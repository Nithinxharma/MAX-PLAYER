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

class BollyflixProvider(
    private val extension: InstalledExtension,
    private val client: OkHttpClient
) : CineHubProvider {

    companion object {
        private const val TAG = "CineHub:BollyflixProvider"
    }

    override val id: String = extension.pkgName
    override val name: String = extension.name
    override val version: String = extension.version
    override val iconUrl: String? = extension.iconUrl
    override val description: String? = extension.description
    override val hasMainPage: Boolean = true
    
    private val baseUrl = "https://bollyflix.run"

    override suspend fun search(query: String): List<CineHubSearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CineHubSearchItem>()
        try {
            val url = "$baseUrl/?s=${query.replace(" ", "+")}"
            Log.d(TAG, "Searching: $url")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val doc = Jsoup.parse(response.body?.string() ?: return@use)
                val elements = doc.select("article")
                for (el in elements) {
                    val aNode = el.selectFirst("a") ?: continue
                    val imgNode = el.selectFirst("img")
                    val path = aNode.attr("href")
                    val title = aNode.attr("title").ifEmpty { imgNode?.attr("alt") ?: "Unknown" }
                    val poster = imgNode?.attr("src")
                    
                    results.add(
                        CineHubSearchItem(
                            id = path,
                            title = title,
                            url = path, // Full URL is usually in href
                            providerId = id,
                            providerName = name,
                            posterUrl = poster,
                            type = TvType.Movie
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
            val request = Request.Builder().url(baseUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val doc = Jsoup.parse(response.body?.string() ?: return@use)
                val elements = doc.select("article")
                val items = mutableListOf<CineHubSearchItem>()
                for (el in elements) {
                    val aNode = el.selectFirst("a") ?: continue
                    val imgNode = el.selectFirst("img")
                    val path = aNode.attr("href")
                    val title = aNode.attr("title").ifEmpty { imgNode?.attr("alt") ?: "Unknown" }
                    val poster = imgNode?.attr("src")
                    
                    items.add(
                        CineHubSearchItem(
                            id = path,
                            title = title,
                            url = path,
                            providerId = id,
                            providerName = name,
                            posterUrl = poster,
                            type = TvType.Movie
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    lists.add(CineHubHomePageList("$name - Latest Updates", items))
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
                val title = doc.selectFirst("h1")?.text() ?: "Unknown"
                val poster = doc.selectFirst(".entry-content img")?.attr("src")
                val desc = doc.selectFirst(".entry-content p")?.text() ?: ""
                
                // Real CloudStream extractors parse multiple download links. 
                // We'll scrape the href of download buttons
                val episodes = mutableListOf<CineHubEpisode>()
                val buttons = doc.select("a.mb-button")
                for ((index, button) in buttons.withIndex()) {
                    episodes.add(
                        CineHubEpisode(
                            id = button.attr("href"),
                            name = button.text().ifEmpty { "Link ${index + 1}" },
                            season = 1,
                            episode = index + 1,
                            data = button.attr("href")
                        )
                    )
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
                    type = TvType.Movie,
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
            // Here data is the download link. We add it directly as a mirror.
            if (data.startsWith("http")) {
                links.add(
                    CineHubStreamLink(
                        name = "Download Mirror",
                        url = data,
                        quality = "Auto",
                        isM3u8 = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load streams error: ${e.message}")
        }
        links
    }

    override suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> = emptyList()
}
