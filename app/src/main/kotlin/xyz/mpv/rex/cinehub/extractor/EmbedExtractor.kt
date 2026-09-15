package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Universal Embed & Iframe Resolver.
 * Follows nested iframes, script sources, and unpacks stream endpoints
 * from movie/TV embed players (VidSrc, AutoEmbed, SmashyStream, Embed.su, MultiEmbed, 2Embed, VidLink, etc.)
 */
class EmbedExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Embed"
        private val IFRAME_REGEX = Regex("""(?i)<iframe[^>]+src=["']([^"']+)["']""")
        private val VIDEO_SOURCE_REGEX = Regex("""(?i)<(?:video|source)[^>]+src=["']([^"']+)["']""")
        private val DIRECT_M3U8_REGEX = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
    }

    override val name: String = "Universal Embed"
    override val mainUrl: String = ""
    override val requiresReferer: Boolean = false

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("embed") ||
                lower.contains("vidsrc") ||
                lower.contains("smashy") ||
                lower.contains("autoembed") ||
                lower.contains("multiembed") ||
                lower.contains("2embed") ||
                lower.contains("moviesapi") ||
                lower.contains("vidlink") ||
                lower.contains("player.") ||
                lower.contains("/v/") ||
                lower.contains("/e/")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving embed URL: $url (referer: $referer)")
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: ""
            val baseOrigin = if (host.isNotBlank()) "https://$host" else ""

            val headers = mutableMapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Accept" to "*/*"
            )
            if (!referer.isNullOrBlank()) {
                headers["Referer"] = referer
            } else if (baseOrigin.isNotBlank()) {
                headers["Referer"] = "$baseOrigin/"
            }

            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val responseText = defaultClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            // 1. Try JwPlayer extraction
            val unpacked = if (!JsUnpacker.getPacked(responseText).isNullOrEmpty()) {
                JsUnpacker.getAndUnpack(responseText)
            } else {
                responseText
            }

            val parsedJw = JwPlayerHelper.extractStreamLinks(
                script = unpacked,
                sourceName = host.ifBlank { name },
                mainUrl = baseOrigin,
                headers = headers,
                callback = callback,
                subtitleCallback = subtitleCallback
            )

            // 2. Direct M3U8 links in the HTML
            DIRECT_M3U8_REGEX.findAll(unpacked).forEach { match ->
                val m3u8Url = match.groupValues[1]
                val generated = M3u8Helper.generateM3u8(
                    source = host.ifBlank { name },
                    streamUrl = m3u8Url,
                    referer = url,
                    headers = headers,
                    name = "$host HD"
                )
                generated.forEach(callback)
            }

            // 3. Direct video source tags
            VIDEO_SOURCE_REGEX.findAll(unpacked).forEach { match ->
                val src = match.groupValues[1]
                val fullUrl = if (src.startsWith("http")) src else if (src.startsWith("//")) "https:$src" else "$baseOrigin$src"
                if (fullUrl.contains(".m3u8")) {
                    M3u8Helper.generateM3u8(host, fullUrl, url, headers, "$host Stream").forEach(callback)
                } else if (fullUrl.contains(".mp4") || fullUrl.contains(".mkv")) {
                    callback(
                        ExtractorLinkData(
                            source = host,
                            name = "$host Direct Video",
                            url = fullUrl,
                            referer = url,
                            quality = "1080p",
                            isM3u8 = false,
                            headers = headers
                        )
                    )
                }
            }

            // 4. Follow nested iframes recursively
            val iframeMatches = IFRAME_REGEX.findAll(unpacked).toList()
            for (m in iframeMatches) {
                val iframeSrc = m.groupValues[1]
                val fullIframeUrl = when {
                    iframeSrc.startsWith("http") -> iframeSrc
                    iframeSrc.startsWith("//") -> "https:$iframeSrc"
                    iframeSrc.startsWith("/") -> "$baseOrigin$iframeSrc"
                    else -> "$baseOrigin/$iframeSrc"
                }

                // Avoid infinite loop on self
                if (fullIframeUrl.equals(url, ignoreCase = true)) continue

                Log.d(TAG, "Found nested iframe: $fullIframeUrl in $url")
                val childExtractor = ExtractorManager.findExtractor(fullIframeUrl)
                if (childExtractor != null && childExtractor !is EmbedExtractor) {
                    childExtractor.getUrl(fullIframeUrl, url, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving embed for $url: ${e.message}")
        }
    }
}
