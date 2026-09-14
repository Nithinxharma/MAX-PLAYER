package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * HLS M3U8 Master Playlist parser.
 * Extracts individual resolution streams (4K, 1080p, 720p, 480p, 360p) with full header preservation.
 */
object M3u8Helper {
    private const val TAG = "M3u8Helper"

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val STREAM_INF_REGEX = Regex("""#EXT-X-STREAM-INF:([^\n\r]+)[\r\n]+([^\r\n]+)""")
    private val RESOLUTION_REGEX = Regex("""RESOLUTION=\d+x(\d+)""")

    fun extractM3u8(
        baseLink: CineHubStreamLink,
        httpClient: OkHttpClient = defaultClient
    ): List<CineHubStreamLink> {
        return try {
            val reqBuilder = Request.Builder().url(baseLink.url)
            baseLink.headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (baseLink.referer.isNotBlank() && !baseLink.headers.containsKey("Referer") && !baseLink.headers.containsKey("referer")) {
                reqBuilder.header("Referer", baseLink.referer)
            }
            val response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: return listOf(baseLink)

            if (!body.contains("#EXTM3U")) {
                return listOf(baseLink)
            }

            val matches = STREAM_INF_REGEX.findAll(body).toList()
            if (matches.isEmpty()) {
                // Master playlist has no child variants, return original link
                return listOf(baseLink.copy(isM3u8 = true, streamType = "M3U8"))
            }

            val variants = mutableListOf<CineHubStreamLink>()
            for (match in matches) {
                val inf = match.groupValues[1]
                val streamPath = match.groupValues[2].trim()
                if (streamPath.isBlank() || streamPath.startsWith("#")) continue

                val resolvedUrl = try {
                    URI(baseLink.url).resolve(streamPath).toString()
                } catch (e: Exception) {
                    if (streamPath.startsWith("http")) streamPath
                    else baseLink.url.substringBeforeLast('/') + "/" + streamPath
                }

                val height = RESOLUTION_REGEX.find(inf)?.groupValues?.get(1)?.toIntOrNull() ?: 1080
                val qualityLabel = QualityUtils.formatQuality(height)

                variants.add(
                    CineHubStreamLink(
                        name = "${baseLink.name} ($qualityLabel)",
                        url = resolvedUrl,
                        quality = qualityLabel,
                        isM3u8 = true,
                        headers = baseLink.headers,
                        referer = baseLink.referer,
                        qualityNumeric = height,
                        streamType = "M3U8",
                        host = baseLink.host.ifBlank { "HLS" }
                    )
                )
            }

            if (variants.isNotEmpty()) {
                variants.sortedByDescending { it.qualityNumeric }
            } else {
                listOf(baseLink.copy(isM3u8 = true, streamType = "M3U8"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse M3U8 for ${baseLink.url}: ${e.message}")
            listOf(baseLink)
        }
    }
}
