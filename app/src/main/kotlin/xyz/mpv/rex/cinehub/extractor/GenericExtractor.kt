package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Fallback extractor for direct media URLs (m3u8, mp4, mkv, webm) or generic HTML5 pages.
 */
class GenericExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "Direct Stream"
    override val mainUrl: String = ""
    override val requiresReferer: Boolean = false

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
                lower.contains(".mp4") ||
                lower.contains(".mkv") ||
                lower.contains(".webm")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        val lower = url.lowercase()
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        if (referer != null) {
            headers["Referer"] = referer
        }

        if (lower.contains(".m3u8")) {
            val streams = M3u8Helper.generateM3u8(
                source = name,
                streamUrl = url,
                referer = referer,
                headers = headers,
                name = "Direct HLS"
            )
            streams.forEach(callback)
        } else {
            val quality = if (lower.contains("1080")) "1080p" else if (lower.contains("720")) "720p" else if (lower.contains("480")) "480p" else "HD"
            callback(
                ExtractorLinkData(
                    source = name,
                    name = "Direct Video ($quality)",
                    url = url,
                    referer = referer,
                    quality = quality,
                    isM3u8 = false,
                    headers = headers
                )
            )
        }
    }
}
