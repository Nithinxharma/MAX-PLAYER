package xyz.mpv.rex.youtube.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import xyz.mpv.rex.cinehub.failover.StreamCandidate
import xyz.mpv.rex.youtube.invidious.network.InvidiousFailoverClient
import xyz.mpv.rex.youtube.model.VideoDataResponse
import xyz.mpv.rex.youtube.model.YoutubeVideo
import java.net.URLEncoder

/**
 * Production-ready Invidious API Client powered by the InvidiousFailoverClient.
 *
 * Features:
 * - Dynamic instance registry discovery and 24-hour SharedPreferences caching.
 * - Strict instance ranking (>95% health, https, api:true).
 * - Automatic HTTP failover across instances with 5-second per-instance timeout.
 * - Multi-stream candidate extraction with header injection and YouTube web stream fallback.
 */
object InvidiousClient {
    private const val TAG = "InvidiousClient"

    val failoverClient by lazy { InvidiousFailoverClient() }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Fetches trending videos with automatic instance failover.
     */
    suspend fun fetchTrendingVideos(type: String = "Movies"): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val endpoint = "api/v1/trending?type=$type"
        val responseBody = failoverClient.executeGet(endpoint) ?: return@withContext emptyList()
        try {
            jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing trending videos: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches search results with automatic instance failover.
     */
    suspend fun fetchSearchVideos(query: String): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        val endpoint = "api/v1/search?q=$encodedQuery&type=video"
        val responseBody = failoverClient.executeGet(endpoint) ?: return@withContext emptyList()
        try {
            jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing search results: ${e.message}")
            emptyList()
        }
    }

    /**
     * Resolves stream candidates for a videoId.
     * Returns a list of healthy stream candidates (highest quality first) with injected headers,
     * including YouTube web stream fallback.
     */
    suspend fun fetchStreamCandidates(videoId: String): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<StreamCandidate>()
        val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer" to "https://www.youtube.com/",
            "Origin" to "https://www.youtube.com"
        )

        val endpoint = "api/v1/videos/$videoId"
        val responseBody = failoverClient.executeGet(endpoint)

        if (!responseBody.isNullOrBlank()) {
            try {
                val parsed = jsonParser.decodeFromString<VideoDataResponse>(responseBody)

                // 1. Direct Combined format streams (Video + Audio)
                parsed.formatStreams.forEach { format ->
                    if (format.url.isNotBlank()) {
                        val quality = format.qualityLabel.ifBlank { "720p" }
                        candidates.add(
                            StreamCandidate(
                                url = format.url,
                                name = "Invidious Direct ($quality)",
                                quality = quality,
                                isM3u8 = format.url.contains(".m3u8"),
                                headers = defaultHeaders
                            )
                        )
                    }
                }

                // 2. Adaptive format streams
                parsed.adaptiveFormats
                    .filter { it.type.startsWith("video/") && it.container.equals("mp4", ignoreCase = true) }
                    .forEach { format ->
                        if (format.url.isNotBlank()) {
                            val quality = format.qualityLabel.ifBlank { "HD" }
                            candidates.add(
                                StreamCandidate(
                                    url = format.url,
                                    name = "Invidious MP4 ($quality)",
                                    quality = quality,
                                    isM3u8 = format.url.contains(".m3u8"),
                                    headers = defaultHeaders
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing video formats for $videoId: ${e.message}")
            }
        }

        // 3. Fallback: Always append the canonical YouTube web stream URL
        // MPV Lib / YtDlClient can resolve and play YouTube URLs natively if direct links expire
        val webFallbackUrl = "https://www.youtube.com/watch?v=$videoId"
        candidates.add(
            StreamCandidate(
                url = webFallbackUrl,
                name = "YouTube Web Stream",
                quality = "Auto",
                isM3u8 = false,
                headers = defaultHeaders
            )
        )

        return@withContext candidates
    }

    /**
     * Backward-compatible direct stream URL resolver.
     */
    suspend fun fetchDirectStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val candidates = fetchStreamCandidates(videoId)
        return@withContext candidates.firstOrNull()?.url
    }
}
