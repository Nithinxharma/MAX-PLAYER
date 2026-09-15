package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * Parses HLS M3U8 master playlists to resolve multi-quality stream links
 * (1080p, 720p, 480p, 360p).
 */
object M3u8Helper {
    private const val TAG = "CineHub:Extractor"
    private val client = OkHttpClient()

    private val STREAM_INF_REGEX = Regex("""#EXT-X-STREAM-INF:(?:.*?RESOLUTION=\d+x(\d+).*?\s+(.+)|.*?BANDWIDTH=(\d+).*?\s+(.+)|.*?\s+(.+))""")
    private val RESOLUTION_REGEX = Regex("""RESOLUTION=\d+x(\d+)""")

    suspend fun generateM3u8(
        source: String,
        streamUrl: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        name: String = source
    ): List<ExtractorLinkData> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExtractorLinkData>()
        try {
            val reqBuilder = Request.Builder().url(streamUrl)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            if (referer != null && !headers.containsKey("Referer")) {
                reqBuilder.addHeader("Referer", referer)
            }
            if (!headers.containsKey("User-Agent")) {
                reqBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            }

            val body = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            if (!body.contains("#EXTM3U")) {
                return@withContext listOf(
                    ExtractorLinkData(
                        source = source,
                        name = "$name Auto",
                        url = streamUrl,
                        referer = referer,
                        quality = "Auto",
                        isM3u8 = true,
                        headers = headers
                    )
                )
            }

            val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var currentQuality: String? = null

            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    val resMatch = RESOLUTION_REGEX.find(line)
                    currentQuality = if (resMatch != null) {
                        "${resMatch.groupValues[1]}p"
                    } else if (line.contains("1080")) {
                        "1080p"
                    } else if (line.contains("720")) {
                        "720p"
                    } else if (line.contains("480")) {
                        "480p"
                    } else {
                        "Auto"
                    }
                } else if (!line.startsWith("#") && currentQuality != null) {
                    val resolvedUrl = resolveRelativeUrl(streamUrl, line)
                    results.add(
                        ExtractorLinkData(
                            source = source,
                            name = "$name $currentQuality",
                            url = resolvedUrl,
                            referer = referer,
                            quality = currentQuality,
                            isM3u8 = true,
                            headers = headers
                        )
                    )
                    currentQuality = null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "M3U8 parsing failed for $streamUrl: ${e.message}")
        }

        if (results.isEmpty()) {
            results.add(
                ExtractorLinkData(
                    source = source,
                    name = "$name Auto",
                    url = streamUrl,
                    referer = referer,
                    quality = "Auto",
                    isM3u8 = true,
                    headers = headers
                )
            )
        }

        results
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                relativeOrAbsolute
            } else {
                val baseUri = URI(baseUrl)
                baseUri.resolve(relativeOrAbsolute).toString()
            }
        } catch (e: Exception) {
            relativeOrAbsolute
        }
    }
}
