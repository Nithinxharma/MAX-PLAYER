package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.CryptoJSHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked

class ChillxTop : Chillx() {
    override var name = "Chillx"
    override var mainUrl = "https://chillx.top"
}

open class Chillx : ExtractorApi() {
    override var name = "Chillx"
    override var mainUrl = "https://chillx.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val res = app.get(url, headers = headers, referer = referer)
        val html = res.text

        val unpacked = if (!getPacked(html).isNullOrEmpty()) {
            getAndUnpack(html)
        } else {
            html
        }

        // Try direct m3u8 in unpacked or raw html
        val m3u8Regex = Regex("""(?:"|')(https?://[^"']+\.m3u8[^"']*)(?:"|')""")
        val m3u8 = m3u8Regex.find(unpacked ?: html)?.groupValues?.get(1)
        if (m3u8 != null) {
            M3u8Helper.generateM3u8(
                name,
                m3u8,
                mainUrl,
                headers = headers
            ).forEach(callback)
            return
        }

        // Check for AES / CryptoJS encrypted source
        val encMatch = Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']""").find(unpacked ?: html)
        if (encMatch != null) {
            val fileStr = encMatch.groupValues[1]
            if (fileStr.startsWith("http")) {
                if (fileStr.contains(".m3u8")) {
                    M3u8Helper.generateM3u8(name, fileStr, mainUrl, headers = headers).forEach(callback)
                }
            } else {
                // Key search
                val key = Regex("""key\s*[:=]\s*["']([^"']+)["']""").find(unpacked ?: html)?.groupValues?.get(1) ?: "chillx"
                val decrypted = runCatching { CryptoJSHelper.decrypt(key, fileStr) }.getOrNull()
                if (!decrypted.isNullOrBlank() && decrypted.contains(".m3u8")) {
                    M3u8Helper.generateM3u8(name, decrypted, mainUrl, headers = headers).forEach(callback)
                }
            }
        }
    }
}
