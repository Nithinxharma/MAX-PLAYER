package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Extractor for StreamTape (streamtape.com, streamtape.net, shavetape.cash).
 */
class StreamTapeExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "StreamTape"
    override val mainUrl: String = "https://streamtape.com"
    override val requiresReferer: Boolean = false

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("streamtape.") || lower.contains("shavetape.") || lower.contains("watchadsontape.")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()

            val body = defaultClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            // StreamTape pattern: document.getElementById('botlink').innerHTML = '...' + ('...').substring(1)
            val botRegex = Regex("""id=["']botlink["'][^>]*>.*?</div>\s*<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val scriptMatch = botRegex.find(body)
            val script = scriptMatch?.groupValues?.get(1) ?: body

            val targetTokenRegex = Regex("""innerHTML\s*=\s*["']([^"']+)["']\s*\+\s*\(?["']([^"']+)["']\.substring\((\d+)\)""")
            val tokenMatch = targetTokenRegex.find(script)
            val streamUrl = if (tokenMatch != null) {
                val part1 = tokenMatch.groupValues[1]
                val part2 = tokenMatch.groupValues[2]
                val substringIndex = tokenMatch.groupValues[3].toIntOrNull() ?: 0
                val part2Sub = if (substringIndex < part2.length) part2.substring(substringIndex) else ""
                "https:$part1$part2Sub&stream=1"
            } else {
                // Secondary pattern: directly find norobot link
                val norobotRegex = Regex("""["'](//[^"']+/get_video\?[^"']+)["']""")
                norobotRegex.find(body)?.groupValues?.get(1)?.let { "https:$it&stream=1" }
            }

            if (!streamUrl.isNullOrBlank()) {
                callback(
                    ExtractorLinkData(
                        source = name,
                        name = "$name (720p)",
                        url = streamUrl,
                        referer = url,
                        quality = "720p",
                        isM3u8 = false,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                            "Referer" to url
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "StreamTape extraction failed for $url: ${e.message}")
        }
    }
}
