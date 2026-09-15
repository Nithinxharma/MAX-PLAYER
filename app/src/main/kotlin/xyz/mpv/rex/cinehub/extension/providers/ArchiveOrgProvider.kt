package xyz.mpv.rex.cinehub.extension.providers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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

class ArchiveOrgProvider(
    private val extension: InstalledExtension,
    private val client: OkHttpClient
) : CineHubProvider {

    companion object {
        private const val TAG = "CineHub:ArchiveProvider"
    }

    override val id: String = extension.pkgName
    override val name: String = extension.name
    override val version: String = extension.version
    override val iconUrl: String? = extension.iconUrl
    override val description: String? = extension.description
    override val hasMainPage: Boolean = true

    override suspend fun search(query: String): List<CineHubSearchItem> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=mediatype:movies+AND+title:${encodedQuery}&output=json&rows=15"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<CineHubSearchItem>()
                val body = response.body?.string() ?: return@use emptyList<CineHubSearchItem>()
                val json = JSONObject(body)
                val docs = json.getJSONObject("response").getJSONArray("docs")
                
                val results = mutableListOf<CineHubSearchItem>()
                for (i in 0 until docs.length()) {
                    val item = docs.getJSONObject(i)
                    val identifier = item.getString("identifier")
                    val title = item.optString("title", identifier)
                    val year = item.optString("year", "").take(4).toIntOrNull()
                    
                    results.add(
                        CineHubSearchItem(
                            id = "archive://$identifier",
                            title = title,
                            url = "archive://$identifier",
                            providerId = id,
                            providerName = name,
                            posterUrl = "https://archive.org/services/img/$identifier",
                            type = TvType.Movie,
                            year = year
                        )
                    )
                }
                return@withContext results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: \${e.message}")
            emptyList()
        }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val lists = mutableListOf<CineHubHomePageList>()
        
        // Define collection based on extension name to create unique home pages
        val collection = when {
            name.lowercase().contains("anime") -> "collection:anime"
            name.lowercase().contains("hindi") || name.lowercase().contains("bolly") -> "collection:hindi_movies"
            else -> "collection:SciFi_Horror"
        }
        
        val url = "https://archive.org/advancedsearch.php?q=mediatype:movies+AND+$collection&sort[]=downloads+desc&output=json&rows=10"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val docs = JSONObject(body).getJSONObject("response").getJSONArray("docs")
                    val items = mutableListOf<CineHubSearchItem>()
                    for (i in 0 until docs.length()) {
                        val item = docs.getJSONObject(i)
                        val identifier = item.getString("identifier")
                        items.add(
                            CineHubSearchItem(
                                id = "archive://$identifier",
                                title = item.optString("title", identifier),
                                url = "archive://$identifier",
                                providerId = id,
                                providerName = name,
                                posterUrl = "https://archive.org/services/img/$identifier",
                                type = TvType.Movie,
                                year = item.optString("year", "").take(4).toIntOrNull()
                            )
                        )
                    }
                    if (items.isNotEmpty()) {
                        lists.add(CineHubHomePageList("$name - Trending", items))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Home page error: \${e.message}")
        }
        return@withContext lists
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        val identifier = url.removePrefix("archive://")
        try {
            val metadataUrl = "https://archive.org/metadata/$identifier"
            val request = Request.Builder().url(metadataUrl).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                val metadata = json.getJSONObject("metadata")
                
                val title = metadata.optJSONArray("title")?.getString(0) ?: metadata.optString("title", identifier)
                val desc = metadata.optJSONArray("description")?.getString(0) ?: metadata.optString("description", "No description available.")
                val year = metadata.optString("year", "").take(4).toIntOrNull()
                
                return@withContext CineHubMediaDetails(
                    id = url,
                    title = title,
                    url = url,
                    providerId = id,
                    providerName = name,
                    posterUrl = "https://archive.org/services/img/$identifier",
                    backdropUrl = "https://archive.org/services/img/$identifier",
                    overview = desc,
                    year = year,
                    type = TvType.Movie,
                    episodes = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load details error: \${e.message}")
            null
        }
    }

    override suspend fun loadEpisodes(url: String): List<CineHubEpisode> = emptyList()

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {
        val identifier = data.removePrefix("archive://")
        val links = mutableListOf<CineHubStreamLink>()
        
        try {
            val metadataUrl = "https://archive.org/metadata/$identifier"
            val request = Request.Builder().url(metadataUrl).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)
                val files = json.getJSONArray("files")
                val server = json.getString("server")
                val dir = json.getString("dir")
                
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val name = file.getString("name")
                    val format = file.optString("format", "")
                    
                    if (format.contains("mp4", ignoreCase = true) || format.contains("mkv", ignoreCase = true) || name.endsWith(".mp4") || name.endsWith(".mkv")) {
                        val fileUrl = "https://$server$dir/$name"
                        links.add(
                            CineHubStreamLink(
                                name = "$name Archive Server",
                                url = fileUrl,
                                quality = "720p", // Just a guess for archive
                                isM3u8 = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load streams error: \${e.message}")
        }
        
        return@withContext links
    }

    override suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> = emptyList()
}
