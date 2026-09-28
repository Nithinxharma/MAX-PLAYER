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

open class Embedrise : ExtractorApi() {
    override var name = "Embedrise"
    override var mainUrl = "https://embedrise.com"
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

        val mp4Regex = Regex("""(?:"|')(https?://[^"']+\.mp4[^"']*)(?:"|')""")
        val mp4 = mp4Regex.find(unpacked ?: html)?.groupValues?.get(1)
        if (mp4 != null) {
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name MP4",
                    url = mp4,
                    INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }
}
