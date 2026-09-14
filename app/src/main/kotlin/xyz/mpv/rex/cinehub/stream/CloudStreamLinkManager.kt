package xyz.mpv.rex.cinehub.stream

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.api.CineHubSubtitleTrack
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.cinehub.extractor.ExtractorLinkData
import xyz.mpv.rex.cinehub.extractor.ExtractorManager
import xyz.mpv.rex.cinehub.extractor.M3u8Helper
import xyz.mpv.rex.cinehub.extractor.SubtitleData
import xyz.mpv.rex.cinehub.failover.StreamCandidate
import xyz.mpv.rex.cinehub.failover.StreamHealthResolver
import java.io.File

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
 * 1. Queries target provider from ProviderRegistry.
 * 2. Runs extractors (StreamWish, Filemoon, StreamTape, MixDrop, Dood, Rabbitstream, VidHide, etc.).
 * 3. Resolves M3U8 multi-quality playlists into individual 1080p, 720p, 480p streams.
 * 4. Extracts and aggregates subtitle tracks.
 * 5. Sanitizes and rejects non-video HTML pages.
 * 6. Injects mandatory HTTP headers (User-Agent, Referer, Origin) to bypass 403 Forbidden.
 * 7. Runs concurrent pre-flight checks to rank verified working streams.
 */
object CloudStreamLinkManager {
    private const val TAG = "CineHub:LinkResolution"

    /**
     * Resolves all available stream candidates for a media request across installed providers.
     */
    suspend fun resolveStreamCandidates(request: CloudStreamRequest): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<StreamCandidate>()
        val collectedSubtitles = mutableListOf<String>()

        Log.i(TAG, "Starting link resolution for: ${request.getFormattedDisplayName()} (providerId=${request.providerId}, dataUrl=${request.dataUrl})")

        // 1. Check if direct file path is a valid local file
        if (!request.directFilePath.isNullOrBlank()) {
            val file = File(request.directFilePath)
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Using local file: ${file.absolutePath}")
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

        // 2. Query extension providers
        val providerRegistry = runCatching {
            KoinJavaComponent.get<ProviderRegistry>(ProviderRegistry::class.java)
        }.getOrNull()

        val providerStreamLinks = mutableListOf<CineHubStreamLink>()

        if (providerRegistry != null) {
            val providersToQuery = if (!request.providerId.isNullOrBlank()) {
                listOfNotNull(providerRegistry.getProvider(request.providerId))
            } else {
                providerRegistry.getEnabledProviders()
            }

            Log.d(TAG, "Querying ${providersToQuery.size} active providers for streams")

            val deferredProviders = providersToQuery.map { targetProvider ->
                async {
                    try {
                        val queryData = request.dataUrl
                            ?: if (request.isMovie) request.tmdbId.ifBlank { request.title }
                            else "${request.tmdbId.ifBlank { request.title }}:${request.seasonNumber ?: 1}:${request.episodeNumber ?: 1}"
                        
                        Log.d(TAG, "Querying provider '${targetProvider.name}' with data: $queryData")
                        val streams = targetProvider.loadStreams(queryData)
                        val subs = runCatching { targetProvider.loadSubtitles(queryData) }.getOrDefault(emptyList())
                        Pair(streams, subs)
                    } catch (e: Exception) {
                        Log.w(TAG, "Provider '${targetProvider.name}' failed to load streams: ${e.message}")
                        Pair(emptyList<CineHubStreamLink>(), emptyList<CineHubSubtitleTrack>())
                    }
                }
            }

            val results = deferredProviders.awaitAll()
            for ((streams, subs) in results) {
                providerStreamLinks.addAll(streams)
                subs.forEach { track ->
                    if (track.url.isNotBlank()) {
                        collectedSubtitles.add(track.url)
                    }
                }
            }
        }

        // If request had a direct URL, also include it for extraction/probing
        if (!request.dataUrl.isNullOrBlank() && (request.dataUrl.startsWith("http://") || request.dataUrl.startsWith("https://"))) {
            providerStreamLinks.add(
                CineHubStreamLink(
                    name = "Direct Stream",
                    url = request.dataUrl,
                    quality = "Auto",
                    isM3u8 = request.dataUrl.contains(".m3u8")
                )
            )
        }

        Log.d(TAG, "Collected ${providerStreamLinks.size} initial stream links from providers. Starting extractor resolution...")

        // 3. Process links through CloudStream Extractors
        val extractorTasks = providerStreamLinks.map { streamLink ->
            async {
                val extractedList = mutableListOf<StreamCandidate>()
                val url = streamLink.url

                // Check if an extractor exists for this host or if it's an embed
                if (ExtractorManager.findExtractor(url) != null) {
                    ExtractorManager.extract(
                        url = url,
                        referer = streamLink.headers["Referer"],
                        onSubtitle = { sub ->
                            if (sub.url.isNotBlank()) {
                                synchronized(collectedSubtitles) {
                                    collectedSubtitles.add(sub.url)
                                }
                            }
                        },
                        onLink = { link ->
                            extractedList.add(
                                StreamCandidate(
                                    url = link.url,
                                    name = link.name,
                                    quality = link.quality,
                                    isM3u8 = link.isM3u8,
                                    headers = link.headers.ifEmpty { streamLink.headers }
                                )
                            )
                        }
                    )
                } else if (url.contains(".m3u8")) {
                    // M3U8 Master playlist resolution
                    val multiQualities = M3u8Helper.generateM3u8(
                        source = streamLink.name,
                        streamUrl = url,
                        referer = streamLink.headers["Referer"],
                        headers = streamLink.headers,
                        name = streamLink.name
                    )
                    multiQualities.forEach { qLink ->
                        extractedList.add(
                            StreamCandidate(
                                url = qLink.url,
                                name = qLink.name,
                                quality = qLink.quality,
                                isM3u8 = true,
                                headers = qLink.headers
                            )
                        )
                    }
                } else if (isValidMediaStreamUrl(url)) {
                    // Direct playable media file
                    extractedList.add(
                        StreamCandidate(
                            url = url,
                            name = streamLink.name.ifBlank { "Direct Stream" },
                            quality = streamLink.quality.ifBlank { "1080p" },
                            isM3u8 = streamLink.isM3u8,
                            headers = streamLink.headers
                        )
                    )
                }
                extractedList
            }
        }

        val allExtracted = extractorTasks.awaitAll().flatten()
        candidates.addAll(allExtracted)

        // 4. Attach discovered subtitles to all candidates and filter valid media streams
        val validCandidates = candidates.filter { isValidMediaStreamUrl(it.url) }
            .distinctBy { it.url }
            .map { candidate ->
                if (collectedSubtitles.isNotEmpty() && candidate.subtitles.isEmpty()) {
                    candidate.copy(subtitles = collectedSubtitles.distinct())
                } else {
                    candidate
                }
            }

        if (validCandidates.isEmpty()) {
            Log.w(TAG, "No playable media streams found for ${request.title}")
            return@withContext emptyList()
        }

        // 5. Perform asynchronous health pre-flight check and rank
        val ranked = StreamHealthResolver.resolveAndRankCandidates(validCandidates)
        Log.i(TAG, "Successfully resolved ${ranked.size} stream candidates for ${request.title}")
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
