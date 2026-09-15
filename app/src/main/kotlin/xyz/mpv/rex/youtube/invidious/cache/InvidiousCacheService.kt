package xyz.mpv.rex.youtube.invidious.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.App
import xyz.mpv.rex.youtube.invidious.engine.InvidiousFilterRankEngine
import xyz.mpv.rex.youtube.invidious.model.InvidiousInstance
import java.util.concurrent.TimeUnit

/**
 * Fetch & Local Cache Service for Invidious Instances.
 *
 * Implements local caching with SharedPreferences.
 * Reads from the cache on startup and only fetches a fresh list from the network
 * if the cache is older than 24 hours.
 */
class InvidiousCacheService(
    private val context: Context = App.instance,
) {
    companion object {
        private const val TAG = "InvidiousCacheService"
        private const val PREFS_NAME = "invidious_instance_cache"
        private const val KEY_CACHED_JSON = "cached_instances_json"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_timestamp"
        private const val KEY_ACTIVE_PRIMARY_URL = "active_primary_url"

        private const val API_INSTANCES_URL = "https://api.invidious.io/instances.json?sort_by=type,health"
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L // 24 hours

        // Verified fallback seed instances if offline or api.invidious.io is blocked
        val SEED_INSTANCES = listOf(
            InvidiousInstance("yewtu.be", "https://yewtu.be", "https", true, 99.1),
            InvidiousInstance("invidious.projectsegfau.lt", "https://invidious.projectsegfau.lt", "https", true, 98.8),
            InvidiousInstance("invidious.privacydev.net", "https://invidious.privacydev.net", "https", true, 98.5),
            InvidiousInstance("iv.melmac.space", "https://iv.melmac.space", "https", true, 98.0),
            InvidiousInstance("inv.nadeko.net", "https://inv.nadeko.net", "https", true, 97.5),
            InvidiousInstance("invidious.nerdvpn.de", "https://invidious.nerdvpn.de", "https", true, 97.0),
            InvidiousInstance("invidious.drgns.space", "https://invidious.drgns.space", "https", true, 96.5),
        )
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val fetchHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var inMemoryInstances: List<InvidiousInstance>? = null

    /**
     * Retrieves the ranked instances.
     * Uses in-memory or SharedPreferences cache if valid (<24h old).
     * If expired or missing, triggers a network refresh.
     */
    suspend fun getRankedInstances(forceRefresh: Boolean = false): List<InvidiousInstance> = withContext(Dispatchers.IO) {
        val cached = inMemoryInstances
        if (!forceRefresh && cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        val lastFetch = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        val cachedJson = prefs.getString(KEY_CACHED_JSON, null)
        val isCacheValid = (System.currentTimeMillis() - lastFetch) < CACHE_TTL_MS

        if (!forceRefresh && isCacheValid && !cachedJson.isNullOrBlank()) {
            val instances = InvidiousFilterRankEngine.parseAndRank(cachedJson)
            if (instances.isNotEmpty()) {
                Log.d(TAG, "Loaded ${instances.size} healthy Invidious instances from local cache (<24h)")
                inMemoryInstances = instances
                return@withContext instances
            }
        }

        // Cache missing or expired: fetch fresh list from network
        Log.d(TAG, "Fetching fresh Invidious instances from: $API_INSTANCES_URL")
        val networkInstances = fetchFromNetwork()
        if (networkInstances.isNotEmpty()) {
            inMemoryInstances = networkInstances
            return@withContext networkInstances
        }

        // Network failed: fallback to expired cache if available
        if (!cachedJson.isNullOrBlank()) {
            val fallback = InvidiousFilterRankEngine.parseAndRank(cachedJson)
            if (fallback.isNotEmpty()) {
                Log.w(TAG, "Network fetch failed; falling back to stale cache with ${fallback.size} instances")
                inMemoryInstances = fallback
                return@withContext fallback
            }
        }

        // Ultimate fallback to curated seed instances
        Log.w(TAG, "Using curated seed instances as fallback")
        inMemoryInstances = SEED_INSTANCES
        return@withContext SEED_INSTANCES
    }

    /**
     * Executes the HTTP request to fetch the live instances registry.
     */
    private fun fetchFromNetwork(): List<InvidiousInstance> {
        return try {
            val request = Request.Builder()
                .url(API_INSTANCES_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()

            fetchHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val ranked = InvidiousFilterRankEngine.parseAndRank(body)
                    if (ranked.isNotEmpty()) {
                        // Persist to SharedPreferences
                        prefs.edit()
                            .putString(KEY_CACHED_JSON, body)
                            .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                            .apply()
                        Log.i(TAG, "Successfully cached ${ranked.size} filtered & ranked Invidious instances")
                        return ranked
                    }
                }
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch live Invidious instances: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets the currently saved active primary base URL.
     */
    fun getSavedPrimaryUrl(): String? {
        return prefs.getString(KEY_ACTIVE_PRIMARY_URL, null)
    }

    /**
     * Persists the successfully connected base URL as the default primary URL.
     */
    fun savePrimaryUrl(url: String) {
        prefs.edit().putString(KEY_ACTIVE_PRIMARY_URL, url.trimEnd('/')).apply()
        Log.d(TAG, "Saved active primary Invidious URL: $url")
    }
}
