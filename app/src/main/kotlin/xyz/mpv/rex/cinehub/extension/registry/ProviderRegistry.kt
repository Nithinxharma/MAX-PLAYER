package xyz.mpv.rex.cinehub.extension.registry

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.extractors.DefaultExtractors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

object ProviderRegistry {
    private val TAG = "ProviderRegistry"
    private val activeProviders = CopyOnWriteArrayList<MainAPI>()

    init {
        DefaultExtractors.registerAll()
    }

    fun register(api: MainAPI) {
        activeProviders.removeAll { it.name.equals(api.name, ignoreCase = true) }
        activeProviders.add(api)
        APIHolder.addProvider(api)
        Log.i(TAG, "Registered provider: ${api.name} (${api.mainUrl})")
    }

    fun unregister(name: String) {
        activeProviders.removeAll { it.name.equals(name, ignoreCase = true) }
        APIHolder.removeProvider(name)
        Log.i(TAG, "Unregistered provider: $name")
    }

    fun getAll(): List<MainAPI> = activeProviders.toList()

    fun get(name: String): MainAPI? =
        activeProviders.find { it.name.equals(name, ignoreCase = true) }

    suspend fun searchAll(
        query: String,
        timeoutMs: Long = 15000L
    ): Map<String, List<SearchResponse>> = coroutineScope {
        val providers = getAll()
        val results = mutableMapOf<String, List<SearchResponse>>()

        val tasks = providers.map { provider ->
            async(Dispatchers.IO) {
                val list = withTimeoutOrNull(timeoutMs) {
                    try {
                        Log.d(TAG, "Searching '${query}' on [${provider.name}]")
                        provider.search(query) ?: provider.quickSearch(query) ?: emptyList()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Search failed on [${provider.name}]: ${t.message}", t)
                        emptyList()
                    }
                } ?: emptyList()
                provider.name to list
            }
        }

        tasks.awaitAll().forEach { (name, list) ->
            if (list.isNotEmpty()) {
                results[name] = list
            }
        }

        results
    }

    suspend fun searchSingle(
        providerName: String,
        query: String,
        timeoutMs: Long = 15000L
    ): List<SearchResponse> = withContext(Dispatchers.IO) {
        val provider = get(providerName) ?: return@withContext emptyList()
        withTimeoutOrNull(timeoutMs) {
            try {
                provider.search(query) ?: provider.quickSearch(query) ?: emptyList()
            } catch (t: Throwable) {
                Log.e(TAG, "Search error for ${provider.name}: ${t.message}", t)
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun loadDetails(
        providerName: String,
        url: String,
        timeoutMs: Long = 20000L
    ): LoadResponse? = withContext(Dispatchers.IO) {
        val provider = get(providerName) ?: return@withContext null
        withTimeoutOrNull(timeoutMs) {
            try {
                provider.load(url)
            } catch (t: Throwable) {
                Log.e(TAG, "Load details failed for [${provider.name}] on $url: ${t.message}", t)
                null
            }
        }
    }

    suspend fun extractLinks(
        providerName: String,
        data: String,
        onSubtitle: (SubtitleFile) -> Unit,
        onLink: (ExtractorLink) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val provider = get(providerName)
        val extractedLinks = mutableListOf<ExtractorLink>()
        val interceptedOnLink: (ExtractorLink) -> Unit = { link ->
            extractedLinks.add(link)
            onLink(link)
        }

        var providerHandled = false
        if (provider != null) {
            try {
                Log.d(TAG, "Invoking loadLinks on [${provider.name}] with data: $data")
                providerHandled = provider.loadLinks(data, false, onSubtitle, interceptedOnLink)
            } catch (t: Throwable) {
                Log.e(TAG, "Provider loadLinks failed: ${t.message}", t)
            }
        }

        // If provider returned false or no links were discovered, run universal fallback extractor
        if (!providerHandled || extractedLinks.isEmpty()) {
            Log.d(TAG, "Running universal loadExtractor fallback on: $data")
            loadExtractor(data, null, onSubtitle, interceptedOnLink)
        }

        extractedLinks.isNotEmpty()
    }
}
