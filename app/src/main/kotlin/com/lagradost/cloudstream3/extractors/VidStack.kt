package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class VidStack : ExtractorApi() {
    override var name = "VidStack"
    override var mainUrl = "https://vidstack.io"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer)
        val text = res.text

        val unpacked = if (text.contains("eval(function(p,a,c,k,e,d)")) {
            getAndUnpack(text)
        } else {
            text
        }

        val m3u8 = Regex("""(?:file|source|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)?.groupValues?.get(1)
            ?: Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(unpacked)?.value

        if (m3u8 != null) {
            M3u8Helper.generateM3u8(
                source = this.name,
                streamUrl = m3u8,
                referer = url
            ).forEach(callback)
        } else {
            val mp4 = Regex("""(?:file|source|src)\s*:\s*["']([^"']+\.mp4[^"']*)["']""").find(unpacked)?.groupValues?.get(1)
            if (mp4 != null) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = mp4
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}
