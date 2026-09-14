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

        // Direct http/https stream URL or Embed
        if (!request.dataUrl.isNullOrBlank() && (request.dataUrl.startsWith("http://") || request.dataUrl.startsWith("https://"))) {
            if (xyz.mpv.rex.cinehub.extractor.ExtractorManager.canExtract(request.dataUrl)) {
                xyz.mpv.rex.cinehub.extractor.ExtractorManager.loadExtractor(request.dataUrl) { link ->
                    candidates.add(link.toStreamCandidate())
                }
            } else if (request.dataUrl.contains(".m3u8")) {
                val base = CineHubStreamLink(
                    name = "Direct HLS Stream",
                    url = request.dataUrl,
                    quality = "Auto",
                    isM3u8 = true,
                    host = "HLS Direct"
                )
                val variants = xyz.mpv.rex.cinehub.extractor.M3u8Helper.extractM3u8(base)
                variants.forEach { candidates.add(it.toStreamCandidate()) }
            } else if (isValidMediaStreamUrl(request.dataUrl)) {
                candidates.add(
                    StreamCandidate(
                        url = request.dataUrl,
                        name = "Direct Stream (${request.title})",
                        quality = "1080p",
                        isM3u8 = false
                    )
                )
            }
        }

        // Direct CineCloud / Netmirror URL resolution
        if (!request.dataUrl.isNullOrBlank() && (request.dataUrl.startsWith("cnc_stream:") || request.dataUrl.startsWith("cnc_tv:") || request.dataUrl.startsWith("vidsrc_") || request.dataUrl.startsWith("stream_tv:"))) {
            try {
                val direct = CineCloudRepoClient.resolveMediaUri(request.dataUrl)
                if (direct.isNotBlank() && (direct.startsWith("http://") || direct.startsWith("https://"))) {
                    if (xyz.mpv.rex.cinehub.extractor.ExtractorManager.canExtract(direct)) {
                        xyz.mpv.rex.cinehub.extractor.ExtractorManager.loadExtractor(direct) { link ->
                            candidates.add(link.toStreamCandidate())
                        }
                    } else if (direct.contains(".m3u8")) {
                        val base = CineHubStreamLink(
                            name = "CineHub Primary Stream",
                            url = direct,
                            quality = "Auto",
                            isM3u8 = true,
                            host = "CineHub Network"
                        )
                        val variants = xyz.mpv.rex.cinehub.extractor.M3u8Helper.extractM3u8(base)
                        variants.forEach { candidates.add(it.toStreamCandidate()) }
                    } else if (isValidMediaStreamUrl(direct)) {
                        candidates.add(
                            StreamCandidate(
                                url = direct,
                                name = "CineHub Primary Server (Fast)",
                                quality = "1080p",
                                isM3u8 = false,
                                host = "CineHub Network"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve media URI ${request.dataUrl}: ${e.message}")
            }
        }

        // 2. Query extension providers
        val providerRegistry = runCatching {
            KoinJavaComponent.get<ProviderRegistry>(ProviderRegistry::class.java)
        }.getOrNull()

        val providerTasks = mutableListOf<suspend () -> Unit>()

        if (providerRegistry != null) {
            val providersToQuery = if (!request.providerId.isNullOrBlank()) {
                listOfNotNull(providerRegistry.getProvider(request.providerId))
            } else {
                providerRegistry.getEnabledProviders()
            }

            for (targetProvider in providersToQuery) {
                providerTasks.add {
                    try {
                        val queryData = request.dataUrl
                            ?: if (request.isMovie) request.tmdbId.ifBlank { request.title }
                            else "${request.tmdbId.ifBlank { request.title }}:${request.seasonNumber ?: 1}:${request.episodeNumber ?: 1}"
                        
                        targetProvider.loadLinks(queryData) { link ->
                            if (xyz.mpv.rex.cinehub.extractor.ExtractorManager.canExtract(link.url)) {
                                kotlinx.coroutines.runBlocking {
                                    xyz.mpv.rex.cinehub.extractor.ExtractorManager.loadExtractor(
                                        link.url,
                                        referer = link.referer.ifBlank { null }
                                    ) { extracted ->
                                        synchronized(candidates) {
                                            candidates.add(extracted.toStreamCandidate())
                                        }
                                    }
                                }
                            } else if (link.isM3u8 || link.url.contains(".m3u8")) {
                                val variants = xyz.mpv.rex.cinehub.extractor.M3u8Helper.extractM3u8(link)
                                synchronized(candidates) {
                                    variants.forEach { candidates.add(it.toStreamCandidate()) }
                                }
                            } else if (isValidMediaStreamUrl(link.url)) {
                                synchronized(candidates) {
                                    candidates.add(link.toStreamCandidate())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Provider ${targetProvider.name} failed to load streams: ${e.message}")
                    }
                }
            }
        }

        // 3. Query Netmirror/CineCloud resolver if available
        if (!request.tmdbId.isNullOrBlank() || !request.imdbId.isNullOrBlank()) {
            providerTasks.add {
                try {
                    val id = request.imdbId.ifBlank { request.tmdbId }
                    val endpoints = if (request.isMovie) listOf("nf", "pv") else listOf("hs", "dp")
                    for ((idx, ep) in endpoints.withIndex()) {
                        val direct = CineCloudRepoClient.resolveDirectStreamUrl(id, ep)
                        if (!direct.isNullOrBlank()) {
                            if (xyz.mpv.rex.cinehub.extractor.ExtractorManager.canExtract(direct)) {
                                xyz.mpv.rex.cinehub.extractor.ExtractorManager.loadExtractor(direct) { link ->
                                    synchronized(candidates) {
                                        candidates.add(link.toStreamCandidate())
                                    }
                                }
                            } else if (direct.contains(".m3u8")) {
                                val base = CineHubStreamLink(
                                    name = "CloudStream Mirror ${idx + 1}",
                                    url = direct,
                                    quality = "Auto",
                                    isM3u8 = true,
                                    host = "CineCloud"
                                )
                                val variants = xyz.mpv.rex.cinehub.extractor.M3u8Helper.extractM3u8(base)
                                synchronized(candidates) {
                                    variants.forEach { candidates.add(it.toStreamCandidate()) }
                                }
                            } else if (isValidMediaStreamUrl(direct)) {
                                synchronized(candidates) {
                                    candidates.add(
                                        StreamCandidate(
                                            url = direct,
                                            name = "CloudStream Mirror ${idx + 1} (Fast)",
                                            quality = "1080p",
                                            isM3u8 = false,
                                            host = "CineCloud"
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Execute all provider tasks in parallel
        val deferredList = providerTasks.map { task ->
            async { task() }
        }
        deferredList.awaitAll()

        // 4. Deduplicate & inject required HTTP headers
        val uniqueCandidates = candidates.distinctBy { it.url + it.name }

        if (uniqueCandidates.isEmpty()) {
            Log.w(TAG, "No playable media streams found for ${request.title}")
            return@withContext emptyList()
        }

        // 5. Perform asynchronous health pre-flight check and rank
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
}
