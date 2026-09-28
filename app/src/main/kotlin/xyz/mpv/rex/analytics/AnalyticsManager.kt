package xyz.mpv.rex.analytics

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.provider.server.model.DailyAnalyticsSummary
import xyz.mpv.rex.cinehub.provider.server.model.ProviderHealthMetric
import xyz.mpv.rex.cinehub.provider.server.model.SearchAnalyticsEvent
import xyz.mpv.rex.cinehub.provider.server.model.StreamAnalyticsEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Batched Analytics & Health Monitoring Engine for Max Stream OTT.
 *
 * Buffer all search events, watch events, and provider telemetry locally first.
 * Never spam Firestore on individual actions.
 * Uploads in batches:
 * - Periodic timer (every 15 minutes)
 * - Application background lifecycle
 * - User logout
 */
class AnalyticsManager(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "AnalyticsManager"
        const val DAILY_COLLECTION = "analytics_daily"
        const val SEARCHES_COLLECTION = "analytics_searches"
        const val STREAMS_COLLECTION = "analytics_streams"
        const val PROVIDERS_COLLECTION = "analytics_providers"
        private const val FLUSH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Local in-memory event queues
    private val searchEventQueue = ConcurrentLinkedQueue<SearchAnalyticsEvent>()
    private val streamEventQueue = ConcurrentLinkedQueue<StreamAnalyticsEvent>()
    private val providerHealthMap = ConcurrentHashMap<String, ProviderHealthMetric>()
    private val extensionUsageMap = ConcurrentHashMap<String, Int>()

    private val _dailySummary = MutableStateFlow<DailyAnalyticsSummary?>(null)
    val dailySummary: StateFlow<DailyAnalyticsSummary?> = _dailySummary.asStateFlow()

    private val _allProviderHealth = MutableStateFlow<List<ProviderHealthMetric>>(emptyList())
    val allProviderHealth: StateFlow<List<ProviderHealthMetric>> = _allProviderHealth.asStateFlow()

    private val _isFlushing = MutableStateFlow(false)
    val isFlushing: StateFlow<Boolean> = _isFlushing.asStateFlow()

    init {
        // Start 15-minute background flush loop
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushBatch()
            }
        }
    }

    /**
     * Records a search query event locally without calling Firestore.
     */
    fun trackSearch(query: String, resultCount: Int, selectedProvider: String? = null) {
        if (query.isBlank()) return
        searchEventQueue.add(
            SearchAnalyticsEvent(
                query = query.trim(),
                timestamp = System.currentTimeMillis(),
                resultCount = resultCount,
                selectedProvider = selectedProvider
            )
        )
    }

    /**
     * Records a stream playback event locally without calling Firestore.
     */
    fun trackStream(
        mediaTitle: String,
        provider: String,
        extractor: String? = null,
        isSuccess: Boolean = true,
        latencyMs: Long = 0L,
        error: String? = null
    ) {
        streamEventQueue.add(
            StreamAnalyticsEvent(
                mediaTitle = mediaTitle,
                provider = provider,
                extractor = extractor,
                isSuccess = isSuccess,
                latencyMs = latencyMs,
                timestamp = System.currentTimeMillis(),
                error = error
            )
        )
        // Increment provider usage counter
        extensionUsageMap.compute(provider) { _, current -> (current ?: 0) + 1 }
    }

    /**
     * Records health metrics for a provider (Search, Home, Load, Stream success).
     */
    fun recordProviderHealth(
        providerName: String,
        searchPassed: Boolean,
        homePassed: Boolean,
        loadPassed: Boolean,
        streamPassed: Boolean
    ) {
        val score = (
            (if (searchPassed) 25 else 0) +
            (if (homePassed) 25 else 0) +
            (if (loadPassed) 25 else 0) +
            (if (streamPassed) 25 else 0)
        )

        val metric = ProviderHealthMetric(
            providerName = providerName,
            searchPassed = searchPassed,
            homePassed = homePassed,
            loadPassed = loadPassed,
            streamPassed = streamPassed,
            healthScore = score,
            lastTested = System.currentTimeMillis()
        )
        providerHealthMap[providerName] = metric
        _allProviderHealth.value = providerHealthMap.values.sortedByDescending { it.healthScore }
    }

    /**
     * Flushes all buffered events to Firestore in a single atomic/batched operation.
     */
    suspend fun flushBatch(): Boolean = withContext(Dispatchers.IO) {
        if (_isFlushing.value) return@withContext false
        _isFlushing.value = true

        val searchesToFlush = mutableListOf<SearchAnalyticsEvent>()
        while (true) {
            val item = searchEventQueue.poll() ?: break
            searchesToFlush.add(item)
        }

        val streamsToFlush = mutableListOf<StreamAnalyticsEvent>()
        while (true) {
            val item = streamEventQueue.poll() ?: break
            streamsToFlush.add(item)
        }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        try {
            val batch = firestore.batch()

            // 1. Write aggregated daily summary
            val dailyRef = firestore.collection(DAILY_COLLECTION).document(todayDate)
            val successfulStreams = streamsToFlush.count { it.isSuccess }
            val totalStreams = streamsToFlush.size
            val streamRate = if (totalStreams > 0) ((successfulStreams.toDouble() / totalStreams) * 100).toInt() else 100

            val topProvider = extensionUsageMap.maxByOrNull { it.value }?.key ?: "Default"

            val dailyUpdate = hashMapOf<String, Any>(
                "date" to todayDate,
                "totalSearches" to FieldValue.increment(searchesToFlush.size.toLong()),
                "totalStreams" to FieldValue.increment(totalStreams.toLong()),
                "lastUpdated" to FieldValue.serverTimestamp(),
                "topExtension" to topProvider,
                "providerSuccessRate" to streamRate
            )
            batch.set(dailyRef, dailyUpdate, SetOptions.merge())

            // 2. Write provider health summaries to analytics_providers
            for ((providerName, metric) in providerHealthMap) {
                val provRef = firestore.collection(PROVIDERS_COLLECTION).document(providerName)
                val provData = hashMapOf<String, Any>(
                    "providerName" to metric.providerName,
                    "searchPassed" to metric.searchPassed,
                    "homePassed" to metric.homePassed,
                    "loadPassed" to metric.loadPassed,
                    "streamPassed" to metric.streamPassed,
                    "healthScore" to metric.healthScore,
                    "lastTested" to metric.lastTested
                )
                batch.set(provRef, provData, SetOptions.merge())
            }

            // Commit batch to Firestore
            batch.commit().await()
            Log.i(TAG, "ANALYTICS_FLUSH: Successfully flushed ${searchesToFlush.size} searches and ${streamsToFlush.size} streams to Firestore.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "ANALYTICS_FLUSH_ERROR: Failed to commit analytics batch: ${e.message}")
            // Re-queue uncommitted events
            searchEventQueue.addAll(searchesToFlush)
            streamEventQueue.addAll(streamsToFlush)
            false
        } finally {
            _isFlushing.value = false
        }
    }

    /**
     * Fetches the latest daily analytics overview for the Admin Dashboard.
     */
    suspend fun fetchDailyAnalytics(): DailyAnalyticsSummary = withContext(Dispatchers.IO) {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        try {
            val doc = firestore.collection(DAILY_COLLECTION).document(todayDate).get().await()
            if (doc != null && doc.exists()) {
                val totalSearches = (doc.get("totalSearches") as? Number)?.toInt() ?: searchEventQueue.size
                val totalStreams = (doc.get("totalStreams") as? Number)?.toInt() ?: streamEventQueue.size
                val successRate = (doc.get("providerSuccessRate") as? Number)?.toInt() ?: 98
                val topExt = doc.getString("topExtension") ?: "SuperStream"
                DailyAnalyticsSummary(
                    date = todayDate,
                    totalActiveUsers = 1,
                    totalSearches = totalSearches,
                    totalStreams = totalStreams,
                    providerSuccessRate = successRate,
                    topExtension = topExt
                )
            } else {
                DailyAnalyticsSummary(
                    date = todayDate,
                    totalActiveUsers = 1,
                    totalSearches = searchEventQueue.size,
                    totalStreams = streamEventQueue.size,
                    providerSuccessRate = 100,
                    topExtension = extensionUsageMap.maxByOrNull { it.value }?.key ?: "Bollyflix"
                )
            }
        } catch (e: Exception) {
            DailyAnalyticsSummary(
                date = todayDate,
                totalActiveUsers = 1,
                totalSearches = searchEventQueue.size,
                totalStreams = streamEventQueue.size,
                providerSuccessRate = 100,
                topExtension = "SuperStream"
            )
        }
    }
}
