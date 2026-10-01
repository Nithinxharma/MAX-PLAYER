package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Standard CloudStream API Repository.
 * Encapsulates communication between MAX STREAM UI and CloudStream MainAPI providers,
 * implementing upstream timeout protection, response caching, link extraction, and safe invocation.
 */
class APIRepository(val api: MainAPI) {

    companion object {
        private const val DEFAULT_TIMEOUT = 120_000L
        private const val MAX_TIMEOUT = 4 * DEFAULT_TIMEOUT
        private const val MIN_TIMEOUT = 5_000L

        var dubStatusActive = HashSet<DubStatus>()

        val noneApi = object : MainAPI() {
            override var name = "None"
            override val supportedTypes = emptySet<TvType>()
            override var lang = ""
        }

        val randomApi = object : MainAPI() {
            override var name = "Random"
            override val supportedTypes = emptySet<TvType>()
            override var lang = ""
        }

        fun isInvalidData(data: String): Boolean {
            return data.isEmpty() || data == "[]" || data == "about:blank"
        }

        data class SavedLoadResponse(
            val unixTime: Long,
            val response: LoadResponse,
            val hash: Pair<String, String>
        )

        private val cache = CopyOnWriteArrayList<SavedLoadResponse>()
        private var cacheIndex: Int = 0
        const val CACHE_SIZE = 20

        fun getTimeout(desired: Long?): Long {
            return (desired ?: DEFAULT_TIMEOUT).coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)
        }

        fun clearCache() {
            cache.clear()
        }
    }

    val hasMainPage: Boolean get() = api.hasMainPage
    val providerType get() = api.providerType
    val name: String get() = api.name
    val mainUrl: String get() = api.mainUrl
    val mainPage get() = api.mainPage
    val hasQuickSearch: Boolean get() = api.hasQuickSearch
    val vpnStatus get() = api.vpnStatus

    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            withTimeout(getTimeout(api.loadTimeoutMs)) {
                if (isInvalidData(url)) throw ErrorLoadingException("Invalid URL: $url")
                val fixedUrl = api.fixUrl(url)
                val lookingForHash = Pair(api.name, fixedUrl)
                val now = System.currentTimeMillis() / 1000

                val cached = synchronized(cache) {
                    cache.firstOrNull { it.hash == lookingForHash && (now - it.unixTime) < 60 * 10 }?.response
                }
                if (cached != null) return@withTimeout cached

                val response = api.load(fixedUrl) ?: throw ErrorLoadingException("Provider returned null for $fixedUrl")
                response.tags = response.tags?.filter { it.isNotBlank() }

                val add = SavedLoadResponse(now, response, lookingForHash)
                synchronized(cache) {
                    if (cache.size >= CACHE_SIZE) {
                        if (cache.isNotEmpty()) {
                            cache[cacheIndex % cache.size] = add
                            cacheIndex = (cacheIndex + 1) % CACHE_SIZE
                        } else {
                            cache.add(add)
                        }
                    } else {
                        cache.add(add)
                    }
                }
                response
            }
        }
    }

    suspend fun search(query: String, page: Int = 1): Resource<SearchResponseList> {
        if (query.isEmpty()) {
            return Resource.Success(SearchResponseList(emptyList(), false))
        }
        return safeApiCall {
            withTimeout(getTimeout(api.searchTimeoutMs)) {
                val results = api.search(query) ?: throw ErrorLoadingException("No search results from ${api.name}")
                SearchResponseList(results, false)
            }
        }
    }

    suspend fun quickSearch(query: String): Resource<SearchResponseList> {
        if (query.isEmpty()) {
            return Resource.Success(SearchResponseList(emptyList(), false))
        }
        return safeApiCall {
            withTimeout(getTimeout(api.quickSearchTimeoutMs)) {
                val results = api.quickSearch(query) ?: throw ErrorLoadingException("No quickSearch results from ${api.name}")
                SearchResponseList(results, false)
            }
        }
    }

    suspend fun getMainPage(page: Int = 1, nameIndex: Int? = null): Resource<List<HomePageResponse?>> {
        return safeApiCall {
            withTimeout(getTimeout(api.getMainPageTimeoutMs)) {
                if (nameIndex != null) {
                    val data = api.mainPage.getOrNull(nameIndex)
                    if (data != null) {
                        listOf(api.getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages)))
                    } else {
                        emptyList()
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        api.mainPage.map { data ->
                            async {
                                try {
                                    api.getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages))
                                } catch (e: Throwable) {
                                    logError(e)
                                    null
                                }
                            }
                        }.map { it.await() }
                    }
                }
            }
        }
    }

    suspend fun extractorVerifierJob(extractorData: String?) {
        safeApiCall {
            api.extractorVerifierJob(extractorData)
        }
    }

    suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (isInvalidData(data)) return false
        var success = false
        val extractedLinks = mutableListOf<ExtractorLink>()
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            extractedLinks.add(link)
            callback(link)
        }

        try {
            withTimeout(getTimeout(api.loadLinksTimeoutMs)) {
                success = api.loadLinks(data, isCasting, subtitleCallback, safeCallback)
            }
        } catch (throwable: Throwable) {
            logError(throwable)
        }

        // If provider loadLinks returned false or 0 links, attempt fallback universal extractor
        if ((!success || extractedLinks.isEmpty()) && (data.startsWith("http://") || data.startsWith("https://") || data.startsWith("//"))) {
            val fixedUrl = com.lagradost.cloudstream3.utils.httpsify(data)
            try {
                val fallbackExtracted = com.lagradost.cloudstream3.utils.loadExtractor(
                    url = fixedUrl,
                    referer = api.mainUrl,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
                if (fallbackExtracted) success = true
            } catch (e: Throwable) {
                logError(e)
            }
        }

        return success || extractedLinks.isNotEmpty()
    }
}
