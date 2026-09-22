package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        
        // Find links in page (like downloads or direct streams)
        val downloadLinks = document.select("a.btn, a[href*='/download/'], a[href*='drive.google'], a[href*='workers.dev'], a[href*='gofile.io']").mapNotNull { it.attr("href") }
        for (link in downloadLinks) {
            if (link.contains("gofile.io") || link.contains("driveleech") || link.contains("hubcloud")) {
                loadExtractor(link, url, subtitleCallback, callback)
            } else if (link.endsWith(".mp4") || link.endsWith(".mkv")) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}
