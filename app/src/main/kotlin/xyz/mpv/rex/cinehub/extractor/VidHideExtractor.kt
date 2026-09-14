package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Extractor for VidHide (vidhide.com, vidhidepro.com, myvidplay.com, etc.).
 */
class VidHideExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "VidHide"
    override val mainUrl: String = "https://vidhidepro.com"
    override val requiresReferer: Boolean = true

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("vidhide.") || lower.contains("vidhidepro.") || lower.contains("myvidplay.")
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
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Referer", referer ?: url)
                .build()

            val body = defaultClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            val unpacked = JsUnpacker.getAndUnpack(body)
            val m3u8Match = Regex("""sources:\s*\[\{file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)
                ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)

            if (m3u8Match != null) {
                val m3u8Url = m3u8Match.groupValues[1]
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
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
            Log.e(TAG, "VidHide extraction failed for $url: ${e.message}")
        }
    }
}
