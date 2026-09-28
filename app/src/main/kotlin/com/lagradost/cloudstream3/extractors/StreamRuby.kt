package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked

class RubyStream : StreamRuby() {
    override var name = "RubyStream"
    override var mainUrl = "https://rubystream.com"
}

open class StreamRuby : ExtractorApi() {
    override var name = "StreamRuby"
    override var mainUrl = "https://streamruby.com"
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
            val m3u8Regex = Regex("""(?:"|')(https?://[^"']+\.m3u8[^"']*)(?:"|')""")
            val m3u8 = m3u8Regex.find(unpacked ?: html)?.groupValues?.get(1)
            if (m3u8 != null) {
                M3u8Helper.generateM3u8(
                    name,
                    m3u8,
                    mainUrl,
                    headers = mapOf("Referer" to "$mainUrl/")
                ).forEach(callback)
            }
        }
    }
}
