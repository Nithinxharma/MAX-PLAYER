package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked

class Luluvdo : LuluStream() {
    override var name = "Luluvdo"
    override var mainUrl = "https://luluvdo.com"
}

class Ponplayer : LuluStream() {
    override var name = "Ponplayer"
    override var mainUrl = "https://ponplayer.org"
}

open class LuluStream : ExtractorApi() {
    override var name = "LuluStream"
    override var mainUrl = "https://lulustream.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val pageResponse = app.get(url, referer = referer)
        val html = pageResponse.text

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
            val m3u8Url = m3u8Regex.find(unpacked ?: html)?.groupValues?.get(1)
            if (m3u8Url != null) {
                M3u8Helper.generateM3u8(
                    name,
                    m3u8Url,
                    mainUrl,
                    headers = mapOf("Referer" to "$mainUrl/")
                ).forEach(callback)
            }
        }
    }
}
