package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.NiceResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

open class Vixeo : Vidsonic() {
    override val name: String = "Vixeo"
    override val mainUrl: String = "https://vixeo.io"

    override fun extractEncodedVideoUrl(response: NiceResponse): String {
        val dataConfigBase64 = response.document.select("#streamsonic-player-root").attr("data-config")
        val dataConfigRaw = base64Decode(dataConfigBase64)
        val dataConfig = AppUtils.parseJson<JsonObject>(dataConfigRaw)
        return dataConfig["source"]!!.jsonPrimitive.content
    }
}

open class Vidsonic : ExtractorApi() {
    override val name: String = "Vidsonic"
    override val mainUrl: String = "https://vidsonic.net"
    override val requiresReferer: Boolean = false

    open fun extractEncodedVideoUrl(response: NiceResponse): String {
        return response.text
            .substringAfter("const _0x1 = ")
            .substringBefore(";")
            .replace("'", "")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url)
        val encodedStreamUrl = extractEncodedVideoUrl(response)

        val streamUrl = encodedStreamUrl
            .replace("|", "")
            .chunked(2)
            .map {
                it.toInt(16).toChar()
            }
            .joinToString("")
            .reversed()

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            )
        )
    }
}
