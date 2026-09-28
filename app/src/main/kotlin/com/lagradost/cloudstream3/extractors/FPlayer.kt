package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

open class FPlayer : ExtractorApi() {
    override var name = "FPlayer"
    override var mainUrl = "https://fplayer.info"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = url.trimEnd('/').substringAfterLast('/')
        val apiUrl = "$mainUrl/api/source/$videoId"

        try {
            val response = app.post(apiUrl, referer = url)
            val json = JSONObject(response.text)
            if (json.optBoolean("success", false)) {
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val file = item.optString("file")
                        val label = item.optString("label", "720p")
                        if (file.isNotBlank()) {
                            val quality = when {
                                label.contains("1080") -> Qualities.P1080.value
                                label.contains("720") -> Qualities.P720.value
                                label.contains("480") -> Qualities.P480.value
                                label.contains("360") -> Qualities.P360.value
                                else -> Qualities.Unknown.value
                            }
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name $label",
                                    url = file,
                                    INFER_TYPE
                                ) {
                                    this.referer = url
                                    this.quality = quality
                                }
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // Fallback scraping
            val doc = app.get(url, referer = referer).document
            val src = doc.selectFirst("video source[src], video[src]")?.attr("src")
            if (!src.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = src,
                        INFER_TYPE
                    ) {
                        this.referer = url
                        this.quality = Qualities.P720.value
                    }
                )
            }
        }
    }
}
