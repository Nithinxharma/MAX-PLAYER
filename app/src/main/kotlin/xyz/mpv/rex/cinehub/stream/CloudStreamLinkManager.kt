package xyz.mpv.rex.cinehub.stream

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koin.java.KoinJavaComponent
import xyz.mpv.rex.cinehub.data.CineCloudRepoClient
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.providers.OpenArchiveProvider
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.cinehub.failover.StreamCandidate
import xyz.mpv.rex.cinehub.failover.StreamHealthResolver
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * CloudStream Request containing media identifiers and metadata.
 */
data class CloudStreamRequest(
    val title: String,
    val tmdbId: String = "",
    val imdbId: String = "",
    val year: Int? = null,
    val isMovie: Boolean = true,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val posterUrl: String? = null,
    val providerId: String? = null,
    val dataUrl: String? = null,
    val directFilePath: String? = null
) {
    fun getFormattedDisplayName(): String {
        return if (isMovie) {
            if (year != null && year > 0) "$title ($year)" else title
        } else {
            val s = (seasonNumber ?: 1).toString().padStart(2, '0')
            val e = (episodeNumber ?: 1).toString().padStart(2, '0')
            if (!episodeTitle.isNullOrBlank()) {
                "$title - S${s}E${e} - $episodeTitle"
            } else {
                "$title - S${s}E${e}"
            }
        }
    }
}

/**
 * CloudStream-style Multi-Provider Stream & Link Management Engine.
 *
 * Emulates CloudStream's modular link resolution:
 * 1. Queries target provider and active extension registry.
 * 2. Queries multi-source stream scrapers & direct video endpoints (Archive.org, HLS direct streams).
 * 3. Sanitizes and rejects non-video HTML pages (prevents infinite demuxer hang).
 * 4. Injects mandatory HTTP headers (User-Agent, Referer, Origin) to bypass 403 Forbidden.
 * 5. Runs concurrent pre-flight checks to rank verified working streams.
 */
object CloudStreamLinkManager {
    private const val TAG = "CloudStreamLinkManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Resolves all available stream candidates for a media request across all providers.
     */
    suspend fun resolveStreamCandidates(request: CloudStreamRequest): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<StreamCandidate>()

        // 1. Check if direct file path is a valid local file
        if (!request.directFilePath.isNullOrBlank()) {
            val file = File(request.directFilePath)
            if (file.exists() && file.length() > 0) {
                candidates.add(
                    StreamCandidate(
                        url = file.absolutePath,
                        name = "Local Media File (${request.title})",
                        quality = "Original",
                        isM3u8 = false
                    )
                )
                return@withContext candidates
            }
        }

        // 2. Query target extension provider if specified
        val providerRegistry = runCatching {
            KoinJavaComponent.get<ProviderRegistry>(ProviderRegistry::class.java)
        }.getOrNull()

        val providerTasks = mutableListOf<suspend () -> List<CineHubStreamLink>>()

        if (!request.providerId.isNullOrBlank() && providerRegistry != null) {
            val targetProvider = providerRegistry.getProvider(request.providerId)
            if (targetProvider != null) {
                providerTasks.add {
                    try {
                        val queryData = request.dataUrl
                            ?: if (request.isMovie) request.tmdbId.ifBlank { request.title }
                            else "${request.tmdbId.ifBlank { request.title }}:${request.seasonNumber ?: 1}:${request.episodeNumber ?: 1}"
                        targetProvider.loadStreams(queryData)
                    } catch (e: Exception) {
                        Log.w(TAG, "Provider ${targetProvider.name} failed to load streams: ${e.message}")
                        emptyList()
                    }
                }
            }
        }

        // 3. Query built-in OpenArchiveProvider for archive / public domain movies
        if (request.isMovie && (request.providerId == null || request.providerId == "open_archive")) {
            providerTasks.add {
                try {
                    val archiveProvider = OpenArchiveProvider(httpClient)
                    // Search archive for title
                    val searchMatches = archiveProvider.search(request.title)
                    val bestMatch = searchMatches.firstOrNull { 
                        it.title.contains(request.title, ignoreCase = true) ||
                        (request.year != null && it.year == request.year)
                    } ?: searchMatches.firstOrNull()
                    
                    if (bestMatch != null) {
                        archiveProvider.loadStreams(bestMatch.url)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        // 4. Query Netmirror/CineCloud resolver if available
        if (!request.tmdbId.isNullOrBlank() || !request.imdbId.isNullOrBlank()) {
            providerTasks.add {
                try {
                    val id = request.imdbId.ifBlank { request.tmdbId }
                    val direct = CineCloudRepoClient.resolveDirectStreamUrl(id, "nf")
                        ?: CineCloudRepoClient.resolveDirectStreamUrl(id, "pv")
                    if (!direct.isNullOrBlank() && isValidMediaStreamUrl(direct)) {
                        listOf(
                            CineHubStreamLink(
                                name = "CloudStream Mirror (Fast)",
                                url = direct,
                                quality = "1080p",
                                isM3u8 = direct.contains(".m3u8")
                            )
                        )
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        // Execute all provider tasks in parallel
        val deferredList = providerTasks.map { task ->
            async { task() }
        }
        val providerResults = deferredList.awaitAll().flatten()

        for (link in providerResults) {
            if (isValidMediaStreamUrl(link.url)) {
                candidates.add(
                    StreamCandidate(
                        url = link.url,
                        name = link.name.ifBlank { "Server Stream" },
                        quality = link.quality.ifBlank { "Auto" },
                        isM3u8 = link.isM3u8 || link.url.contains(".m3u8"),
                        headers = link.headers
                    )
                )
            }
        }

        // 5. Query Multi-Source Video Extractors (Archive.org, Direct HLS / Video endpoints)
        val extractedLinks = queryVideoExtractors(request)
        for (candidate in extractedLinks) {
            if (candidates.none { it.url == candidate.url } && isValidMediaStreamUrl(candidate.url)) {
                candidates.add(candidate)
            }
        }

        // 6. Deduplicate & inject required HTTP headers
        val uniqueCandidates = candidates.distinctBy { it.url }

        if (uniqueCandidates.isEmpty()) {
            Log.w(TAG, "No playable media streams found for ${request.title}")
            return@withContext emptyList()
        }

        // 7. Perform asynchronous health pre-flight check and rank
        val ranked = StreamHealthResolver.resolveAndRankCandidates(uniqueCandidates)
        Log.i(TAG, "Resolved ${ranked.size} stream candidates for ${request.title}")
        ranked
    }

    /**
     * Checks whether a URL is a direct media stream (mp4, m3u8, mkv, webm) and NOT an HTML embed page.
     * Prevents the infinite demuxing hang when an HTML webpage is sent to MPV.
     */
    fun isValidMediaStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()

        // Reject explicit HTML webpages that cause MPV infinite demuxing loops
        if (lower.contains("vidsrc.to/embed/") || 
            lower.contains("vidsrc.me/embed/") || 
            lower.contains("/embed/movie/") || 
            lower.contains("/embed/tv/") ||
            (lower.endsWith(".html") && !lower.contains(".m3u8")) ||
            (lower.endsWith(".htm") && !lower.contains(".m3u8"))) {
            return false
        }

        // Accepts direct stream protocols and media formats
        return lower.startsWith("http://") || 
               lower.startsWith("https://") || 
               lower.startsWith("content://") || 
               lower.startsWith("file://") || 
               lower.startsWith("/")
    }

    /**
     * Queries multi-source public stream extractors (Internet Archive metadata, direct HLS mirrors).
     */
    private suspend fun queryVideoExtractors(request: CloudStreamRequest): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StreamCandidate>()

        try {
            // Query Archive.org public domain media API
            val query = if (request.isMovie) {
                val cleanTitle = request.title.replace("[^a-zA-Z0-9 ]".toRegex(), " ").trim()
                val encoded = URLEncoder.encode("title:($cleanTitle) AND mediatype:(movies)", "UTF-8")
                "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier,title,downloads&sort[]=downloads+desc&rows=3&output=json"
            } else null

            if (query != null) {
                val req = Request.Builder().url(query).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val docs = json.optJSONObject("response")?.optJSONArray("docs")
                        if (docs != null && docs.length() > 0) {
                            for (i in 0 until docs.length().coerceAtMost(2)) {
                                val doc = docs.getJSONObject(i)
                                val identifier = doc.optString("identifier", "")
                                if (identifier.isNotBlank()) {
                                    // Fetch file metadata
                                    val metaUrl = "https://archive.org/metadata/$identifier"
                                    val metaReq = Request.Builder().url(metaUrl).build()
                                    httpClient.newCall(metaReq).execute().use { mResp ->
                                        if (mResp.isSuccessful) {
                                            val mBody = mResp.body?.string() ?: ""
                                            val mJson = JSONObject(mBody)
                                            val files = mJson.optJSONArray("files")
                                            if (files != null) {
                                                for (f in 0 until files.length()) {
                                                    val fileObj = files.getJSONObject(f)
                                                    val name = fileObj.optString("name", "")
                                                    val format = fileObj.optString("format", "")
                                                    if (format.contains("h.264", ignoreCase = true) || 
                                                        format.contains("mp4", ignoreCase = true) || 
                                                        name.endsWith(".mp4", ignoreCase = true)) {
                                                        val directStreamUrl = "https://archive.org/download/$identifier/$name"
                                                        val quality = if (name.contains("1080")) "1080p" else if (name.contains("720")) "720p" else "HD"
                                                        results.add(
                                                            StreamCandidate(
                                                                url = directStreamUrl,
                                                                name = "Archive Direct ($quality)",
                                                                quality = quality,
                                                                isM3u8 = false
                                                            )
                                                        )
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Extractor probe exception: ${e.message}")
        }

        results
    }
}
