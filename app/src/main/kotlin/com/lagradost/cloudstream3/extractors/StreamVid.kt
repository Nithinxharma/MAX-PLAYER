package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker

open class StreamVid : ExtractorApi() {
    override var mainUrl = "https://streamvid.net"
    override var name = "StreamVid"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).text
        val script = res.substringAfter("<script type='text/javascript'>eval(function(p,a,c,k,e,d)")
        if (script.isNotBlank()) {
            val evalCode = "eval(function(p,a,c,k,e,d)" + script.substringBefore("</script>")
            val unpacked = JsUnpacker(evalCode).unpack()
            if (!unpacked.isNullOrBlank()) {
                JwPlayerHelper.extractStreamLinks(unpacked, name, mainUrl, callback, subtitleCallback)
            }
        }
    }
}
