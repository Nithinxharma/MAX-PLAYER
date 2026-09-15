package xyz.mpv.rex.cinehub.extension.providers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import xyz.mpv.rex.cinehub.extension.api.CineHubEpisode
import xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList
import xyz.mpv.rex.cinehub.extension.api.CineHubMediaDetails
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.api.CineHubSubtitleTrack
import xyz.mpv.rex.cinehub.extension.api.TvType
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import java.net.URLEncoder

class SflixProvider(
    private val extension: InstalledExtension,
    private val client: OkHttpClient
) : CineHubProvider {

    companion object {
        private const val TAG = "CineHub:SflixProvider"
    }

    override val id: String = extension.pkgName
    override val name: String = extension.name
    override val version: String = extension.version
    override val iconUrl: String? = extension.iconUrl
    override val description: String? = extension.description
    override val hasMainPage: Boolean = true
    
    private val baseUrl = "https://sflix.to"

    override suspend fun search(query: String): List<CineHubSearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CineHubSearchItem>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/search/${encodedQuery.replace("+", "-")}"
            Log.d(TAG, "Searching: $url")
            
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val doc = Jsoup.parse(response.body?.string() ?: return@use)
                val elements = doc.select(".film_list-wrap .flw-item")
                
                for (el in elements) {
                    val aNode = el.selectFirst(".film-poster a") ?: continue
                    val imgNode = el.selectFirst(".film-poster img")
                    
                    val path = aNode.attr("href")
                    val title = aNode.attr("title").ifEmpty { imgNode?.attr("title") ?: "Unknown" }
                    val poster = imgNode?.attr("data-src")?.ifEmpty { imgNode.attr("src") }
                    val type = if (path.contains("/tv/")) TvType.TvSeries else TvType.Movie
                    
                    results.add(
                        CineHubSearchItem(
                            id = path,
                            title = title,
                            url = "$baseUrl$path",
                            providerId = id,
                            providerName = name,
                            posterUrl = poster,
                            type = type
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}")
        }
        results
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val lists = mutableListOf<CineHubHomePageList>()
        try {
            val request = Request.Builder().url("$baseUrl/home").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val doc = Jsoup.parse(response.body?.string() ?: return@use)
                
                val sections = doc.select(".section-id-02")
                for (section in sections) {
                    val title = section.selectFirst(".heading-name")?.text() ?: "Trending"
                    val elements = section.select(".flw-item")
                    val items = mutableListOf<CineHubSearchItem>()
                    
                    for (el in elements) {
                        val aNode = el.selectFirst(".film-poster a") ?: continue
                        val imgNode = el.selectFirst(".film-poster img")
                        
                        val path = aNode.attr("href")
                        val itemTitle = aNode.attr("title").ifEmpty { imgNode?.attr("title") ?: "Unknown" }
                        val poster = imgNode?.attr("data-src")?.ifEmpty { imgNode.attr("src") }
                        val type = if (path.contains("/tv/")) TvType.TvSeries else TvType.Movie
                        
                        items.add(
                            CineHubSearchItem(
                                id = path,
                                title = itemTitle,
                                url = "$baseUrl$path",
                                providerId = id,
                                providerName = name,
                                posterUrl = poster,
                                type = type
                            )
                        )
                    }
                    if (items.isNotEmpty()) {
                        lists.add(CineHubHomePageList("$name - $title", items))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Home page error: ${e.message}")
        }
        lists
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading details: $url")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val doc = Jsoup.parse(response.body?.string() ?: return@use null)
                
                val title = doc.selectFirst(".heading-name")?.text() ?: "Unknown"
                val desc = doc.selectFirst(".description")?.text() ?: ""
                val poster = doc.selectFirst(".film-poster img")?.attr("src")
                val isTv = url.contains("/tv/")
                
                val episodes = mutableListOf<CineHubEpisode>()
                if (isTv) {
                    val dataId = doc.selectFirst("#watch-id")?.attr("data-id") ?: url.substringAfterLast("-")
                    val seasonsUrl = "$baseUrl/ajax/v2/tv/seasons/$dataId"
                    val seasonsReq = Request.Builder().url(seasonsUrl).build()
                    // If we could execute the ajax request we would parse seasons here.
                    // For the sake of architecture, we'll just parse the episode list if it exists on page
                    val epNodes = doc.select(".eps-item")
                    for ((index, node) in epNodes.withIndex()) {
                        episodes.add(
                            CineHubEpisode(
                                id = node.attr("data-id"),
                                name = node.attr("title").ifEmpty { "Episode ${index + 1}" },
                                season = 1,
                                episode = index + 1,
                                data = "$baseUrl/ajax/v2/episode/servers/${node.attr("data-id")}"
                            )
                        )
                    }
                }
                
                CineHubMediaDetails(
                    id = url,
                    title = title,
                    url = url,
                    providerId = id,
                    providerName = name,
                    posterUrl = poster,
                    backdropUrl = poster,
                    overview = desc,
                    type = if (isTv) TvType.TvSeries else TvType.Movie,
                    episodes = episodes
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load details error: ${e.message}")
            null
        }
    }

    override suspend fun loadEpisodes(url: String): List<CineHubEpisode> {
        return loadDetails(url)?.episodes ?: emptyList()
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {
        val links = mutableListOf<CineHubStreamLink>()
        try {
            Log.d(TAG, "Loading streams for: $data")
            // CloudStream flow: get servers, get iframe embed, extract m3u8
            val serverUrl = if (data.contains("/ajax/")) data else {
                val dataId = data.substringAfterLast("-")
                "$baseUrl/ajax/movie/episodes/$dataId"
            }
            
            val request = Request.Builder().url(serverUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val doc = Jsoup.parse(response.body?.string() ?: return@use)
                val serverNodes = doc.select(".nav-item a")
                
                for (node in serverNodes) {
                    val serverId = node.attr("data-id")
                    if (serverId.isNotEmpty()) {
                        val linkUrl = "$baseUrl/ajax/sources/$serverId"
                        // Here the real CloudStream would GET linkUrl, parse JSON { "link": "https://rabbitstream..." }
                        // and pass it to ExtractorManager. We will just return the raw embed as a placeholder for the extractor
                        links.add(
                            CineHubStreamLink(
                                name = "${node.text()} Server",
                                url = linkUrl,
                                quality = "Auto",
                                isM3u8 = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load streams error: ${e.message}")
        }
        links
    }

    override suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> = emptyList()
}
