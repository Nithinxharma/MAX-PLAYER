package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Dropload : ExtractorApi() {
    override var name = "Dropload"
    override var mainUrl = "https://dropload.io"
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

        val found = JwPlayerHelper.extractStreamLinks(
            unpacked ?: html,
            name,
            mainUrl,
            callback,
            subtitleCallback,
            mapOf("Referer" to "$mainUrl/")
        )

        if (!found) {
            val directMatch = Regex("""file\s*:\s*["']([^"']+)["']""").find(unpacked ?: html)
            val streamUrl = directMatch?.groupValues?.get(1)
            if (!streamUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name MP4",
                        url = streamUrl,
                        INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }
}
