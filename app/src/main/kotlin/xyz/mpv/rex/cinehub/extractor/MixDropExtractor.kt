package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Extractor for MixDrop (mixdrop.co, mixdrop.to, mixdrop.bz, etc.)
 */
class MixDropExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "MixDrop"
    override val mainUrl: String = "https://mixdrop.co"
    override val requiresReferer: Boolean = false

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("mixdrop.") || lower.contains("mxdrop.") || lower.contains("mdy48tn97.com")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val embedUrl = url.replaceFirst("/f/", "/e/")
            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()

            val body = defaultClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            val unpacked = JsUnpacker.getAndUnpack(body)
            val wurlRegex = Regex("""wurl\s*=\s*["']([^"']+)["']""")
            val wurlMatch = wurlRegex.find(unpacked)
            if (wurlMatch != null) {
                var streamUrl = wurlMatch.groupValues[1]
                if (streamUrl.startsWith("//")) {
                    streamUrl = "https:$streamUrl"
                }
                callback(
                    ExtractorLinkData(
                        source = name,
                        name = "$name (720p)",
                        url = streamUrl,
                        referer = embedUrl,
                        quality = "720p",
                        isM3u8 = false,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                            "Referer" to embedUrl
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "MixDrop extraction failed for $url: ${e.message}")
        }
    }
}
