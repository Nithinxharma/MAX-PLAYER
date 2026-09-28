package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONObject

class DailymotionExtractor : DailyMotion() {
    override var name = "DailyMotion"
    override var mainUrl = "https://www.dailymotion.com"
}

class DaiLy : DailyMotion() {
    override var name = "DaiLy"
    override var mainUrl = "https://dai.ly"
}

open class DailyMotion : ExtractorApi() {
    override var name = "DailyMotion"
    override var mainUrl = "https://www.dailymotion.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = when {
            url.contains("/video/") -> url.substringAfter("/video/").substringBefore("?").substringBefore("/")
            url.contains("dai.ly/") -> url.substringAfter("dai.ly/").substringBefore("?").substringBefore("/")
            url.contains("/embed/video/") -> url.substringAfter("/embed/video/").substringBefore("?").substringBefore("/")
            else -> url.trimEnd('/').substringAfterLast('/')
        }

        val metadataUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "https://www.dailymotion.com/"
        )

        try {
            val response = app.get(metadataUrl, headers = headers)
            val json = JSONObject(response.text)
            val qualities = json.optJSONObject("qualities")
            val autoStream = qualities?.optJSONArray("auto")?.optJSONObject(0)?.optString("url")
                ?: json.optJSONObject("stream")?.optString("url")

            if (!autoStream.isNullOrBlank()) {
                M3u8Helper.generateM3u8(
                    name,
                    autoStream,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
            }
        } catch (e: Throwable) {
            // Fallback scraping
            val doc = app.get(url, headers = headers).text
            val m3u8Regex = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""")
            val m3u8 = m3u8Regex.find(doc)?.value
            if (m3u8 != null) {
                M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = headers).forEach(callback)
            }
        }
    }
}
