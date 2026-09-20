package xyz.mpv.rex.cinehub.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.PluginManager
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import `is`.xyz.mpv.MPVLib
import xyz.mpv.rex.ui.player.PlayerActivity
import java.io.File

object CloudstreamHeadlessRunner {
    private const val TAG = "CSHeadlessRunner"
    private var pluginManager: PluginManager? = null

    /**
     * 1. Initialize Headless Environment
     */
    fun init(context: Context) {
        AcraApplication.init(context)
        pluginManager = PluginManager(context)
        Log.i(TAG, "Cloudstream Headless Engine initialized.")
    }

    /**
     * Load an external .cs3 plugin from local storage into the runtime.
     */
    fun loadPlugin(file: File): List<MainAPI> {
        val manager = pluginManager ?: return emptyList()
        return manager.loadPluginFile(file)
    }

    /**
     * 2. Concurrent Multi-Provider Search
     */
    suspend fun searchAll(query: String): Map<MainAPI, List<SearchResponse>> =
        withContext(Dispatchers.IO) {
            val apis = APIHolder.apis.toList()
            val tasks = apis.map { api ->
                async {
                    try {
                        val results = api.search(query)
                        api to results
                    } catch (t: Throwable) {
                        Log.w(TAG, "Search failed for provider: ${api.name}", t)
                        api to emptyList()
                    }
                }
            }
            tasks.awaitAll().toMap()
        }

    /**
     * 3. Load Details & Episode Data
     */
    suspend fun loadDetails(api: MainAPI, url: String): LoadResponse? =
        withContext(Dispatchers.IO) {
            try {
                api.load(url)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load metadata from ${api.name} for $url", t)
                null
            }
        }

    /**
     * 4. Link & Subtitle Extraction Pipeline
     */
    suspend fun extractStreams(
        api: MainAPI,
        data: String,
        onSubtitleFound: ((SubtitleFile) -> Unit)? = null
    ): List<ExtractorLink> = withContext(Dispatchers.IO) {
        val links = mutableListOf<ExtractorLink>()
        try {
            api.loadLinks(
                data = data,
                isCasting = false,
                subtitleCallback = { sub -> onSubtitleFound?.invoke(sub) },
                callback = { link ->
                    synchronized(links) {
                        links.add(link)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Link extraction failed on ${api.name}", t)
        }
        links
    }

    /**
     * 5. The Handoff: MAX STREAM Player Intent Launcher
     */
    fun buildRexPlayerIntent(
        context: Context,
        link: ExtractorLink,
        title: String? = null,
        posterUrl: String? = null,
        overview: String? = null,
        year: String? = null,
        rating: Double? = null,
        providerName: String? = null,
        allLinks: List<ExtractorLink> = emptyList()
    ): Intent {
        // Sort allLinks descending by quality (highest resolution first)
        val sortedLinks = if (allLinks.isNotEmpty()) {
            allLinks.distinctBy { it.url }.sortedByDescending { it.quality }
        } else {
            listOf(link)
        }
        val bestLink = sortedLinks.firstOrNull() ?: link

        val intent = Intent(context, PlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            this.data = Uri.parse(bestLink.url)
        }

        val headerPairs = mutableListOf<String>()

        // 1. Pack Referer
        if (bestLink.referer.isNotBlank()) {
            headerPairs.add("Referer")
            headerPairs.add(bestLink.referer)
        }

        // 2. Pack custom extractor headers
        bestLink.headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                if (key.equals("Referer", ignoreCase = true) && bestLink.referer.isNotBlank()) {
                    return@forEach
                }
                headerPairs.add(key)
                headerPairs.add(value)
            }
        }

        if (headerPairs.isNotEmpty()) {
            intent.putExtra("headers", headerPairs.toTypedArray())
            Log.d(TAG, "Injected ${headerPairs.size / 2} headers into player intent for ${bestLink.url}")
        }

        if (!title.isNullOrBlank()) {
            intent.putExtra("title", title)
        }
        if (!posterUrl.isNullOrBlank()) {
            intent.putExtra("cinetv_poster", posterUrl)
        }
        if (!overview.isNullOrBlank()) {
            intent.putExtra("cinetv_overview", overview)
        }
        if (!year.isNullOrBlank()) {
            intent.putExtra("cinetv_year", year)
        }
        if (rating != null && rating > 0) {
            intent.putExtra("cinetv_rating", rating.toString())
        }
        if (!providerName.isNullOrBlank()) {
            intent.putExtra("cinetv_provider", providerName)
        }
        intent.putExtra("cinetv_source_type", "cinehub")

        if (sortedLinks.isNotEmpty()) {
            intent.putExtra("cinetv_links_urls", sortedLinks.map { it.url }.toTypedArray())
            intent.putExtra("cinetv_links_names", sortedLinks.map { l ->
                val qual = if (l.quality > 0) "${l.quality}p" else "Auto"
                val source = l.source.ifBlank { providerName ?: "Server" }
                "$qual - $source"
            }.toTypedArray())
            intent.putExtra("cinetv_links_qualities", sortedLinks.map { it.quality }.toIntArray())
            intent.putExtra("cinetv_links_referers", sortedLinks.map { it.referer }.toTypedArray())
        }

        return intent
    }

    fun launchRexPlayer(
        context: Context,
        link: ExtractorLink,
        title: String? = null,
        posterUrl: String? = null,
        overview: String? = null,
        year: String? = null,
        rating: Double? = null,
        providerName: String? = null,
        allLinks: List<ExtractorLink> = emptyList()
    ) {
        val intent = buildRexPlayerIntent(
            context = context,
            link = link,
            title = title,
            posterUrl = posterUrl,
            overview = overview,
            year = year,
            rating = rating,
            providerName = providerName,
            allLinks = allLinks
        )
        context.startActivity(intent)
    }

    /**
     * 6. Embedded MPV Native Header Injection
     */
    fun applyMpvProperties(link: ExtractorLink) {
        val headerMap = HashMap<String, String>()
        if (link.referer.isNotBlank()) {
            headerMap["Referer"] = link.referer
        }
        headerMap.putAll(link.headers)

        // Set User-Agent
        val ua = headerMap.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        if (!ua.isNullOrBlank()) {
            runCatching { MPVLib.setPropertyString("user-agent", ua) }
        }

        // Set escaped comma-separated headers for MPV http-header-fields
        val mpvHeaderFields = headerMap.entries
            .filter { !it.key.equals("User-Agent", ignoreCase = true) && it.value.isNotBlank() }
            .joinToString(",") { "${it.key}: ${it.value.replace(",", "\\,")}" }

        if (mpvHeaderFields.isNotBlank()) {
            runCatching { MPVLib.setPropertyString("http-header-fields", mpvHeaderFields) }
        }
    }
}
