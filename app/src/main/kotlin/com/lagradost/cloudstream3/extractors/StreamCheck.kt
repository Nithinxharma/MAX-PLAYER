package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class StreamCheck : ExtractorApi() {
    override var name = "StreamCheck"
    override var mainUrl = "https://streamcheck.link"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val doc = response.document

        // Look for iframe links or direct video sources
        val iframeSrc = doc.selectFirst("iframe[src]")?.attr("src")
        if (!iframeSrc.isNullOrBlank()) {
            val resolvedUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
            com.lagradost.cloudstream3.utils.loadExtractor(resolvedUrl, subtitleCallback, callback)
            return
        }

        val videoSrc = doc.selectFirst("video source[src], video[src]")?.attr("src")
        if (!videoSrc.isNullOrBlank()) {
            if (videoSrc.contains(".m3u8")) {
                M3u8Helper.generateM3u8(name, videoSrc, mainUrl, headers = mapOf("Referer" to "$mainUrl/")).forEach(callback)
            } else {
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoSrc,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }
}
