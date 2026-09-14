package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.api.CineHubSubtitleTrack
import java.util.Random

/**
 * StreamWish / FlashWish / EmbedWish host extractor.
 */
class StreamWishExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "streamwish.to"
    override val requiresReferer = true

    private val domains = listOf(
        "streamwish.", "wishembed.", "dwish.", "embedwish.", "mwish.",
        "wishfast.", "strwish.", "sfastwish.", "kswplayer.", "hgcloud.to",
        "asnwish.", "flaswish.", "swish."
    )

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return domains.any { lower.contains(it) }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", referer ?: url)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return
            val unpacked = JsUnpacker(html).unpack() ?: html

            val m3u8Regex = Regex("""(?:file|source|sources\s*:\s*\[\{file)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""")
            val match = m3u8Regex.find(unpacked) ?: m3u8Regex.find(html)
            val m3u8Url = match?.groupValues?.get(1)

            if (!m3u8Url.isNullOrBlank()) {
                val origin = url.substringBeforeLast('/')
                val baseLink = CineHubStreamLink(
                    name = name,
                    url = m3u8Url,
                    quality = "Auto",
                    isM3u8 = true,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        "Referer" to url,
                        "Origin" to origin
                    ),
                    referer = url,
                    qualityNumeric = 1080,
                    streamType = "M3U8",
                    host = name
                )
                val variants = M3u8Helper.extractM3u8(baseLink, client)
                variants.forEach(callback)
            }
        } catch (e: Exception) {
            Log.w("StreamWishExtractor", "Extraction failed for $url: ${e.message}")
        }
    }
}

/**
 * Filemoon / MoonPlayer host extractor.
 */
class FilemoonExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "Filemoon"
    override val mainUrl = "filemoon.sx"
    override val requiresReferer = true

    private val domains = listOf("filemoon.", "filemoon.sx", "filemoon.in", "filemoon.to", "filemoon.top", "moonplayer.")

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return domains.any { lower.contains(it) }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", referer ?: url)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return
            val unpacked = JsUnpacker(html).unpack() ?: html

            val m3u8Regex = Regex("""(?:file|sources\s*:\s*\[\{file)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""")
            val match = m3u8Regex.find(unpacked) ?: m3u8Regex.find(html)
            val m3u8Url = match?.groupValues?.get(1)

            if (!m3u8Url.isNullOrBlank()) {
                val baseLink = CineHubStreamLink(
                    name = name,
                    url = m3u8Url,
                    quality = "Auto",
                    isM3u8 = true,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        "Referer" to url
                    ),
                    referer = url,
                    qualityNumeric = 1080,
                    streamType = "M3U8",
                    host = name
                )
                val variants = M3u8Helper.extractM3u8(baseLink, client)
                variants.forEach(callback)
            }
        } catch (e: Exception) {
            Log.w("FilemoonExtractor", "Extraction failed for $url: ${e.message}")
        }
    }
}

/**
 * DoodStream / Dood host extractor.
 */
class DoodStreamExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "DoodStream"
    override val mainUrl = "doodstream.com"
    override val requiresReferer = true

    private val domains = listOf(
        "dood.", "doodstream.", "ds2play.", "d0000d.", "dooood.", "dood.to",
        "dood.watch", "dood.so", "dood.cx", "dood.la", "dood.ws", "dood.sh",
        "dood.pm", "dood.wf", "dood.re"
    )

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return domains.any { lower.contains(it) }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val embedUrl = if (url.contains("/d/")) url.replace("/d/", "/e/") else url
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", referer ?: "https://dood.to/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return

            val passRegex = Regex("""/pass_md5/[^"'\s]+""")
            val passMatch = passRegex.find(html)?.value ?: return
            val baseUrl = if (passMatch.startsWith("http")) passMatch else {
                val domain = embedUrl.substringBefore("/e/").substringBefore("/d/")
                domain + passMatch
            }

            val passReq = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", embedUrl)
                .build()

            val passResp = client.newCall(passReq).execute()
            val prefix = passResp.body?.string() ?: return
            if (prefix.isBlank()) return

            val randomChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val randomString = (1..10).map { randomChars[Random().nextInt(randomChars.length)] }.joinToString("")
            val token = passMatch.substringAfterLast('/')
            val directUrl = "$prefix$randomString?token=$token&expiry=${System.currentTimeMillis()}"

            callback(
                CineHubStreamLink(
                    name = name,
                    url = directUrl,
                    quality = "1080p",
                    isM3u8 = false,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        "Referer" to embedUrl
                    ),
                    referer = embedUrl,
                    qualityNumeric = 1080,
                    streamType = "VIDEO",
                    host = name
                )
            )
        } catch (e: Exception) {
            Log.w("DoodStreamExtractor", "Extraction failed for $url: ${e.message}")
        }
    }
}

/**
 * StreamTape host extractor.
 */
class StreamTapeExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "StreamTape"
    override val mainUrl = "streamtape.com"
    override val requiresReferer = true

    private val domains = listOf("streamtape.", "shavetape.", "streamta.pe")

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return domains.any { lower.contains(it) }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", referer ?: "https://streamtape.com/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return

            val linkRegex = Regex("""getElementById\(['"](?:robotlink|videolink)['"]\)\.innerHTML\s*=\s*['"]([^'"]+)['"]\s*\+\s*['"]([^'"]+)['"]""")
            val match = linkRegex.find(html)
            val linkPart = if (match != null) {
                match.groupValues[1] + match.groupValues[2]
            } else {
                val singleRegex = Regex("""(?:robotlink|videolink)['"]\)\.innerHTML\s*=\s*['"]([^'"]+)['"]""")
                singleRegex.find(html)?.groupValues?.get(1)
            }

            if (!linkPart.isNullOrBlank()) {
                val finalUrl = if (linkPart.startsWith("//")) "https:$linkPart" else linkPart
                callback(
                    CineHubStreamLink(
                        name = name,
                        url = finalUrl,
                        quality = "1080p",
                        isM3u8 = false,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                            "Referer" to "https://streamtape.com/"
                        ),
                        referer = "https://streamtape.com/",
                        qualityNumeric = 1080,
                        streamType = "VIDEO",
                        host = name
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("StreamTapeExtractor", "Extraction failed for $url: ${e.message}")
        }
    }
}

/**
 * Mp4Upload host extractor.
 */
class Mp4UploadExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "mp4upload.com"
    override val requiresReferer = true

    override fun canExtract(url: String): Boolean = url.lowercase().contains("mp4upload.com")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", referer ?: "https://www.mp4upload.com/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return

            val srcRegex = Regex("""player\.src\(\s*\{\s*type\s*:\s*["'][^"']+["'],\s*src\s*:\s*["']([^"']+)["']""")
            val directUrl = srcRegex.find(html)?.groupValues?.get(1)
                ?: Regex("""<video[^>]+src=["']([^"']+)["']""").find(html)?.groupValues?.get(1)

            if (!directUrl.isNullOrBlank()) {
                callback(
                    CineHubStreamLink(
                        name = name,
                        url = directUrl,
                        quality = "1080p",
                        isM3u8 = directUrl.contains(".m3u8"),
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                            "Referer" to "https://www.mp4upload.com/"
                        ),
                        referer = "https://www.mp4upload.com/",
                        qualityNumeric = 1080,
                        streamType = if (directUrl.contains(".m3u8")) "M3U8" else "VIDEO",
                        host = name
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("Mp4UploadExtractor", "Extraction failed for $url: ${e.message}")
        }
    }
}

/**
 * MixDrop host extractor.
 */
class MixDropExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "MixDrop"
    override val mainUrl = "mixdrop.co"
    override val requiresReferer = true

    private val domains = listOf("mixdrop.", "mixdrop.co", "mixdrop.to", "mixdrop.sx", "mixdrop.bz", "mixdrop.ch", "mixdrop.ag")

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return domains.any { lower.contains(it) }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val embedUrl = if (url.contains("/f/")) url.replace("/f/", "/e/") else url
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", referer ?: "https://mixdrop.co/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return
            val unpacked = JsUnpacker(html).unpack() ?: html

            val wurlRegex = Regex("""(?:wurl|surl)\s*=\s*["']([^"']+)["']""")
            val rawUrl = wurlRegex.find(unpacked)?.groupValues?.get(1)

            if (!rawUrl.isNullOrBlank()) {
                val streamUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                callback(
                    CineHubStreamLink(
                        name = name,
                        url = streamUrl,
                        quality = "1080p",
                        isM3u8 = streamUrl.contains(".m3u8"),
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                            "Referer" to "https://mixdrop.co/"
                        ),
                        referer = "https://mixdrop.co/",
                        qualityNumeric = 1080,
                        streamType = if (streamUrl.contains(".m3u8")) "M3U8" else "VIDEO",
                        host = name
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("MixDropExtractor", "Extraction failed for $url: ${e.message}")
        }
    }
}

/**
 * JWPlayer script source parser.
 */
class JWPlayerExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "JWPlayer"
    override val mainUrl = ""

    override fun canExtract(url: String): Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return
            parseJwPlayerScript(html, url, callback)
        } catch (e: Exception) {
            Log.w("JWPlayerExtractor", "Extraction failed for $url: ${e.message}")
        }
    }

    fun parseJwPlayerScript(html: String, pageUrl: String, callback: (CineHubStreamLink) -> Unit) {
        val unpacked = JsUnpacker(html).unpack() ?: html
        val fileRegex = Regex("""["']?file["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4|mkv)[^"']*)["'](?:\s*,\s*["']?label["']?\s*:\s*["']([^"']+)["'])?""")
        for (match in fileRegex.findAll(unpacked)) {
            val streamUrl = match.groupValues[1]
            val label = match.groupValues.getOrNull(2)?.ifBlank { null } ?: "1080p"
            val qualityNumeric = QualityUtils.parseQuality(label)

            val baseLink = CineHubStreamLink(
                name = "JWPlayer ($label)",
                url = streamUrl,
                quality = label,
                isM3u8 = streamUrl.contains(".m3u8"),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                    "Referer" to pageUrl
                ),
                referer = pageUrl,
                qualityNumeric = qualityNumeric,
                streamType = if (streamUrl.contains(".m3u8")) "M3U8" else "VIDEO",
                host = "JWPlayer"
            )

            if (baseLink.isM3u8) {
                val variants = M3u8Helper.extractM3u8(baseLink, client)
                variants.forEach(callback)
            } else {
                callback(baseLink)
            }
        }
    }
}

/**
 * Generic HLS M3U8 Master playlist extractor.
 */
class GenericM3u8Extractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "HLS Stream"
    override val mainUrl = ""

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        val baseLink = CineHubStreamLink(
            name = "HLS Stream",
            url = url,
            quality = "Auto",
            isM3u8 = true,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "Referer" to (referer ?: url)
            ),
            referer = referer ?: url,
            qualityNumeric = 1080,
            streamType = "M3U8",
            host = "HLS"
        )
        val variants = M3u8Helper.extractM3u8(baseLink, client)
        variants.forEach(callback)
    }
}

/**
 * Generic embed / iframe crawler.
 */
class GenericEmbedExtractor(private val client: OkHttpClient) : ExtractorApi() {
    override val name = "Generic Embed"
    override val mainUrl = ""

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/embed/") || lower.contains("/e/") || lower.contains("/v/")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return

            JWPlayerExtractor(client).parseJwPlayerScript(html, url, callback)

            val iframeRegex = Regex("""<iframe[^>]+src=["']([^"']+)["']""")
            for (match in iframeRegex.findAll(html)) {
                var embedUrl = match.groupValues[1]
                if (embedUrl.startsWith("//")) embedUrl = "https:$embedUrl"
                if (embedUrl.startsWith("http")) {
                    ExtractorManager.loadExtractor(embedUrl, referer = url, subtitleCallback = subtitleCallback, callback = callback)
                }
            }
        } catch (e: Exception) {
            Log.w("GenericEmbedExtractor", "Embed scan failed for $url: ${e.message}")
        }
    }
}
