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
     * Fetches trending videos with automatic instance failover and Piped backup.
     */
    suspend fun fetchTrendingVideos(type: String = "Movies"): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        // Attempt 1: Standard trending
        val endpoints = listOf(
            "api/v1/trending?region=IN",
            "api/v1/popular?region=IN",
            "api/v1/trending?type=$type"
        )
        for (endpoint in endpoints) {
            val responseBody = failoverClient.executeGet(endpoint)
            if (!responseBody.isNullOrBlank()) {
                try {
                    val parsed = jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Parsing failed for $endpoint: ${e.message}")
                }
            }
        }

        // Attempt 2: Piped API Fallback
        val pipedResults = fetchPipedTrending()
        if (pipedResults.isNotEmpty()) {
            return@withContext pipedResults
        }

        emptyList()
    }

    /**
     * Fetches Shorts (short-form videos under 90s or tagged as Shorts).
     */
    suspend fun fetchShorts(): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        // Try popular endpoint first which heavily ranks short-form videos
        val popBody = failoverClient.executeGet("api/v1/popular?region=IN")
        if (!popBody.isNullOrBlank()) {
            try {
                val parsed = jsonParser.decodeFromString<List<YoutubeVideo>>(popBody)
                val shortsOnly = parsed.filter {
                    it.lengthSeconds in 1..90 || it.title.contains("#shorts", ignoreCase = true) || it.title.contains("shorts", ignoreCase = true)
                }
                if (shortsOnly.isNotEmpty()) {
                    return@withContext shortsOnly
                }
            } catch (_: Exception) {}
        }

        // Fallback: Search for #shorts
        fetchSearchVideos("#shorts")
    }

    /**
     * Fetches search results with automatic instance failover and Piped backup.
     */
    suspend fun fetchSearchVideos(query: String): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        val endpoint = "api/v1/search?q=$encodedQuery&type=video"
        val responseBody = failoverClient.executeGet(endpoint)
        if (!responseBody.isNullOrBlank()) {
            try {
                val parsed = jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing search results: ${e.message}")
            }
        }

        // Piped search fallback
        val pipedResults = fetchPipedSearch(encodedQuery)
        if (pipedResults.isNotEmpty()) {
            return@withContext pipedResults
        }

        emptyList()
    }

    private fun fetchPipedTrending(): List<YoutubeVideo> {
        val pipedBases = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.moomoo.me"
        )
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        for (base in pipedBases) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$base/trending?region=IN")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val array = org.json.JSONArray(body)
                        val list = mutableListOf<YoutubeVideo>()
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val url = obj.optString("url", "")
                            val videoId = if (url.contains("v=")) url.substringAfter("v=") else url.trimStart('/')
                            if (videoId.isNotBlank()) {
                                list.add(
                                    YoutubeVideo(
                                        videoId = videoId,
                                        title = obj.optString("title", "Video"),
                                        author = obj.optString("uploaderName", ""),
                                        authorId = obj.optString("uploaderUrl", ""),
                                        viewCount = obj.optLong("views", 0L),
                                        lengthSeconds = obj.optInt("duration", 0),
                                        publishedText = obj.optString("uploadedDate", ""),
                                        videoThumbnails = listOf(
                                            xyz.mpv.rex.youtube.model.YoutubeThumbnail(
                                                url = obj.optString("thumbnail", "https://img.youtube.com/vi/$videoId/hqdefault.jpg"),
                                                width = 480,
                                                height = 360
                                            )
                                        )
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun fetchPipedSearch(encodedQuery: String): List<YoutubeVideo> {
        val pipedBases = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.moomoo.me"
        )
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        for (base in pipedBases) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$base/search?q=$encodedQuery&filter=all")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val root = org.json.JSONObject(body)
                        val items = root.optJSONArray("items") ?: return@use
                        val list = mutableListOf<YoutubeVideo>()
                        for (i in 0 until items.length()) {
                            val obj = items.optJSONObject(i) ?: continue
                            val url = obj.optString("url", "")
                            val videoId = if (url.contains("v=")) url.substringAfter("v=") else url.trimStart('/')
                            if (videoId.isNotBlank()) {
                                list.add(
                                    YoutubeVideo(
                                        videoId = videoId,
                                        title = obj.optString("title", "Video"),
                                        author = obj.optString("uploaderName", ""),
                                        authorId = obj.optString("uploaderUrl", ""),
                                        viewCount = obj.optLong("views", 0L),
                                        lengthSeconds = obj.optInt("duration", 0),
                                        publishedText = obj.optString("uploadedDate", ""),
                                        videoThumbnails = listOf(
                                            xyz.mpv.rex.youtube.model.YoutubeThumbnail(
                                                url = obj.optString("thumbnail", "https://img.youtube.com/vi/$videoId/hqdefault.jpg"),
                                                width = 480,
                                                height = 360
                                            )
                                        )
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
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

        if (candidates.size <= 1) {
            // Piped stream endpoint fallback
            val pipedBases = listOf(
                "https://api.piped.private.coffee",
                "https://pipedapi.tokhmi.xyz"
            )
            for (base in pipedBases) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val req = okhttp3.Request.Builder().url("$base/streams/$videoId").build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: return@use
                            val root = org.json.JSONObject(body)
                            val videoStreams = root.optJSONArray("videoStreams")
                            if (videoStreams != null) {
                                for (i in 0 until videoStreams.length()) {
                                    val streamObj = videoStreams.optJSONObject(i) ?: continue
                                    val streamUrl = streamObj.optString("url", "")
                                    val quality = streamObj.optString("quality", "HD")
                                    if (streamUrl.isNotBlank()) {
                                        candidates.add(
                                            0,
                                            StreamCandidate(
                                                url = streamUrl,
                                                name = "Piped Direct ($quality)",
                                                quality = quality,
                                                isM3u8 = streamUrl.contains(".m3u8"),
                                                headers = defaultHeaders
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (candidates.size > 1) break
                } catch (_: Exception) {}
            }
        }

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
