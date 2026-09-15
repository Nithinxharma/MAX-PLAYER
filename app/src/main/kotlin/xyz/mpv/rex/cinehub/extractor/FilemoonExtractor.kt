package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Extractor for Filemoon host (filemoon.sx, filemoon.to, filemoon.in).
 */
class FilemoonExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "Filemoon"
    override val mainUrl: String = "https://filemoon.sx"
    override val requiresReferer: Boolean = true

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("filemoon.") || lower.contains("moonplayer.")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .addHeader("Referer", referer ?: url)

            val body = defaultClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            // Check if there is an iframe embed inside
            val iframeMatch = Regex("""<iframe[^>]+src=["']([^"']+)["']""").find(body)
            val pageToParse = if (iframeMatch != null) {
                val iframeUrl = iframeMatch.groupValues[1]
                val subReq = Request.Builder()
                    .url(iframeUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addHeader("Referer", url)
                    .build()
                defaultClient.newCall(subReq).execute().use { it.body?.string() ?: "" }
            } else {
                body
            }

            val unpacked = JsUnpacker.getAndUnpack(pageToParse)

            // Extract subtitles
            val subRegex = Regex("""\{file:\s*["']([^"']+\.(?:vtt|srt)[^"']*)["'],\s*label:\s*["']([^"']+)["']""")
            subRegex.findAll(unpacked).forEach { match ->
                subtitleCallback(
                    SubtitleData(
                        language = match.groupValues[2],
                        url = match.groupValues[1],
                        isVtt = match.groupValues[1].contains(".vtt")
                    )
                )
            }

            // Find m3u8 source
            val m3u8Regex = Regex("""["']?(https?://[^"']+\.m3u8[^"']*)["']?""")
            val match = m3u8Regex.find(unpacked)
            if (match != null) {
                val m3u8Url = match.groupValues[1]
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    "Referer" to url
                )
                val streams = M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = m3u8Url,
                    referer = url,
                    headers = headers,
                    name = name
                )
                streams.forEach(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Filemoon extraction failed for $url: ${e.message}")
        }
    }
}
