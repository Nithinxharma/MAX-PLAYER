package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Extractor for StreamWish and its numerous white-label clones (Filelions, Swdyu, Wishonly, etc.)
 */
class StreamWishExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "StreamWish"
    override val mainUrl: String = "https://streamwish.to"
    override val requiresReferer: Boolean = true

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("streamwish.") ||
                lower.contains("filelions.") ||
                lower.contains("swdyu.") ||
                lower.contains("wishonly.") ||
                lower.contains("asnwish.") ||
                lower.contains("playerwish.") ||
                lower.contains("dwish.") ||
                lower.contains("swhoi.") ||
                lower.contains("multimovies.") ||
                lower.contains("uqloads.")
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
                .addHeader("Referer", referer ?: "$mainUrl/")

            val responseText = defaultClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            val unpacked = JsUnpacker.getAndUnpack(responseText)

            // Extract subtitles if present: file:"...", label:"English"
            val trackRegex = Regex("""\{file:\s*["']([^"']+\.(?:vtt|srt)[^"']*)["'],\s*label:\s*["']([^"']+)["']""")
            trackRegex.findAll(unpacked).forEach { match ->
                val subUrl = match.groupValues[1]
                val label = match.groupValues[2]
                subtitleCallback(
                    SubtitleData(
                        language = label,
                        url = subUrl,
                        isVtt = subUrl.contains(".vtt", ignoreCase = true)
                    )
                )
            }

            // Extract m3u8 sources
            val sourceRegex = Regex("""(?:file|source):\s*["']([^"']+\.m3u8[^"']*)["']""")
            val m3u8Match = sourceRegex.find(unpacked)
            if (m3u8Match != null) {
                val m3u8Url = m3u8Match.groupValues[1]
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl
                )
                val streams = M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = m3u8Url,
                    referer = "$mainUrl/",
                    headers = headers,
                    name = name
                )
                streams.forEach(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "StreamWish extraction failed for $url: ${e.message}")
        }
    }
}
