package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor

open class VidSrcTo : VidSrcExtractor() {
    override var name = "VidSrcTo"
    override var mainUrl = "https://vidsrc.to"
}

open class VidSrcMe : VidSrcExtractor() {
    override var name = "VidSrcMe"
    override var mainUrl = "https://vidsrc.me"
}

open class VidSrcIn : VidSrcExtractor() {
    override var name = "VidSrcIn"
    override var mainUrl = "https://vidsrc.in"
}

open class VidSrcPm : VidSrcExtractor() {
    override var name = "VidSrcPm"
    override var mainUrl = "https://vidsrc.pm"
}

open class VidSrcNet : VidSrcExtractor() {
    override var name = "VidSrcNet"
    override var mainUrl = "https://vidsrc.net"
}

open class VidSrcXyz : VidSrcExtractor() {
    override var name = "VidSrcXyz"
    override var mainUrl = "https://vidsrc.xyz"
}

open class VidSrcExtractor : ExtractorApi() {
    override var name = "VidSrc"
    override var mainUrl = "https://vidsrc.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Referer" to (referer ?: "$mainUrl/"),
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val response = app.get(url, headers = headers)
        val doc = response.document

        // 1. Look for iframes or direct embed sources
        val iframes = doc.select("iframe").mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
        for (iframeSrc in iframes) {
            val fixedUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else if (iframeSrc.startsWith("/")) "$mainUrl$iframeSrc" else iframeSrc
            loadExtractor(fixedUrl, url, subtitleCallback, callback)
        }

        // 2. Look for source URLs in scripts or data attributes
        val scriptContent = doc.select("script").joinToString("\n") { it.data() }
        val m3u8Regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
        m3u8Regex.findAll(scriptContent).forEach { match ->
            val streamUrl = match.value.replace("\\/", "/")
            M3u8Helper.generateM3u8(
                name,
                streamUrl,
                url,
                headers = headers
            ).forEach(callback)
        }
    }
}
