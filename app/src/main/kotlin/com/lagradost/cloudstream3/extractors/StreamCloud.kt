package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink

open class StreamCloud : ExtractorApi() {
    override var name = "StreamCloud"
    override var mainUrl = "https://streamcloud.club"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer)
        val html = res.text

        val unpacked = if (!getPacked(html).isNullOrEmpty()) {
            getAndUnpack(html)
        } else {
            html
        }

        val m3u8Regex = Regex("""(?:"|')(https?://[^"']+\.m3u8[^"']*)(?:"|')""")
        val m3u8 = m3u8Regex.find(unpacked ?: html)?.groupValues?.get(1)
        if (m3u8 != null) {
            M3u8Helper.generateM3u8(
                name,
                m3u8,
                mainUrl,
                headers = mapOf("Referer" to "$mainUrl/", "Origin" to mainUrl)
            ).forEach(callback)
            return
        }

        val videoSrc = Regex("""file\s*:\s*["']([^"']+\.mp4[^"']*)["']""").find(unpacked ?: html)?.groupValues?.get(1)
            ?: res.document.selectFirst("video source[src], video[src]")?.attr("src")

        if (!videoSrc.isNullOrBlank()) {
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name MP4",
                    url = videoSrc,
                    INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }
}
