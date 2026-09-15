package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * CloudStream-style VidHide & FileLions Extractor.
 * Unpacks P.A.C.K.E.R scripts and uses JwPlayerHelper to resolve HLS streams.
 */
class VidHideExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:VidHide"
    }

    override val name: String = "VidHide"
    override val mainUrl: String = "https://vidhidepro.com"
    override val requiresReferer: Boolean = true

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("vidhide") ||
                lower.contains("filelions") ||
                lower.contains("streamhide") ||
                lower.contains("earnvids") ||
                lower.contains("kinoger")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val embedUrl = getEmbedUrl(url)
            val host = java.net.URI(url).host ?: "vidhidepro.com"
            val baseOrigin = "https://$host"

            val headers = mapOf(
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "Origin" to baseOrigin,
                "Referer" to "$baseOrigin/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            )

            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", headers["User-Agent"]!!)
                .addHeader("Referer", referer ?: "$baseOrigin/")
                .build()

            val html = defaultClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            val script = if (!JsUnpacker.getPacked(html).isNullOrEmpty()) {
                JsUnpacker.getAndUnpack(html)
            } else {
                html
            }

            JwPlayerHelper.extractStreamLinks(
                script = script,
                sourceName = name,
                mainUrl = baseOrigin,
                headers = headers,
                callback = callback,
                subtitleCallback = subtitleCallback
            )
        } catch (e: Exception) {
            Log.e(TAG, "VidHide extraction error for $url: ${e.message}")
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            url.contains("/f/") -> url.replace("/f/", "/v/")
            else -> url
        }
    }
}
