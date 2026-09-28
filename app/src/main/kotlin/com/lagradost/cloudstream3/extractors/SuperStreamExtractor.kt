package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.CryptoJSHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class SuperStream : SuperStreamExtractor() {
    override var name = "SuperStream"
    override var mainUrl = "https://superstream.bio"
}

open class SuperStreamExtractor : ExtractorApi() {
    override var name = "SuperStream"
    override var mainUrl = "https://superstream.bio"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "$mainUrl/"
        )

        try {
            val response = app.get(url, headers = headers)
            val text = response.text

            // If direct m3u8 in content
            if (url.contains(".m3u8") || text.contains("#EXTM3U")) {
                M3u8Helper.generateM3u8(
                    name,
                    url,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
                return
            }

            // Check if encrypted CryptoJS payload exists in script tags
            val encryptedMatch = Regex("""encryptedData\s*[:=]\s*["']([^"']+)["']""").find(text)
            val keyMatch = Regex("""decryptionKey\s*[:=]\s*["']([^"']+)["']""").find(text)

            if (encryptedMatch != null && keyMatch != null) {
                val decrypted = CryptoJSHelper.decrypt(keyMatch.groupValues[1], encryptedMatch.groupValues[1])
                if (decrypted.isNotBlank()) {
                    val m3u8 = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(decrypted)?.value
                    if (m3u8 != null) {
                        M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = headers).forEach(callback)
                        return
                    }
                }
            }

            // JSON / API source parsing
            if (text.startsWith("{") || text.contains("sources\":")) {
                val json = runCatching { JSONObject(text) }.getOrNull()
                val streamUrl = json?.optString("url") ?: json?.optJSONArray("sources")?.optJSONObject(0)?.optString("file")
                if (!streamUrl.isNullOrBlank()) {
                    if (streamUrl.contains(".m3u8")) {
                        M3u8Helper.generateM3u8(name, streamUrl, mainUrl, headers = headers).forEach(callback)
                    } else {
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name Direct",
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
        } catch (e: Throwable) {
            // Safe fallback
        }
    }
}
