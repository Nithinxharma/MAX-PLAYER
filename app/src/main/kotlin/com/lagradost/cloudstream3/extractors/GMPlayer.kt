package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Element

open class GMPlayer : Gdriveplayer() {
    override val name = "GMPlayer"
}

open class Gdriveplayer : ExtractorApi() {
    override val name = "Gdrive"
    override val mainUrl = "https://gdriveplayer.to"
    override val requiresReferer = false

    private fun unpackJs(script: Element): String? {
        return script.select("script").find { it.data().contains("eval(function(p,a,c,k,e,d)") }
            ?.data()?.let { getAndUnpack(it) }
    }

    private fun Regex.first(str: String): String? {
        return find(str)?.groupValues?.getOrNull(1)
    }

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val document = app.get(url).document
        val eval = unpackJs(document)?.replace("\\", "") ?: return
        val data = Regex("data='(\\S+?)'").first(eval) ?: return
        val password = Regex("null,['|\"](\\w+)['|\"]").first(eval)
            ?.split(Regex("\\D+"))
            ?.joinToString("") {
                it.toInt().toChar().toString()
            }.let { Regex("var pass = \"(\\S+?)\"").first(it ?: return)?.toByteArray(Charsets.UTF_8) }
            ?: throw ErrorLoadingException("can't find password")
        val decryptedData = cryptoAESHandler(data, password, false, false)?.let { getAndUnpack(it) }?.replace("\\", "")
        val sourceData = decryptedData?.substringAfter("sources:[")?.substringBefore("],")
        val subData = decryptedData?.substringAfter("tracks:[")?.substringBefore("],")

        Regex("\"file\":\"(\\S+?)\".*?res=(\\d+)").findAll(sourceData ?: return).map {
            it.groupValues[1] to it.groupValues[2]
        }.toList().distinctBy { it.second }.forEach { (link, quality) ->
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = "${httpsify(link)}&res=$quality",
                ) {
                    this.referer = mainUrl
                    this.quality = quality.toIntOrNull() ?: Qualities.Unknown.value
                    this.headers = mapOf("Range" to "bytes=0-")
                }
            )
        }

        subData?.addMarks("file")?.addMarks("kind")?.addMarks("label")?.let { dataSub ->
            tryParseJson<List<Tracks>>("[$dataSub]")?.forEach { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        sub.label,
                        httpsify(sub.file),
                    )
                )
            }
        }
    }

    @Serializable
    data class Tracks(
        @JsonProperty("file") @SerialName("file") val file: String,
        @JsonProperty("kind") @SerialName("kind") val kind: String,
        @JsonProperty("label") @SerialName("label") val label: String,
    )
}
