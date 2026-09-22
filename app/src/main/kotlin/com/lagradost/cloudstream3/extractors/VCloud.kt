package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

open class VCloud : ExtractorApi() {
    override val name: String = "V-Cloud"
    override val mainUrl: String = "https://vcloud.lol"
    override val requiresReferer = false

    private fun extractDoubleAtob(html: String): String? {
        val regex = Regex("""var\s+url\s*=\s*atob\s*\(\s*atob\s*\(\s*['"]([^'"]+)['"]\s*\)\s*\)""")
        return regex.find(html)?.groupValues?.get(1)?.let {
            try {
                base64Decode(base64Decode(it))
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseUrl = getBaseUrl(url)
        val doc = try {
            app.get(url).document
        } catch (_: Exception) {
            return
        }

        var link = if (url.contains("/video/")) {
            doc.selectFirst("div.vd > center > a")?.attr("href") ?: ""
        } else {
            val scriptTag = doc.selectFirst("script:containsData(url)")?.toString() ?: ""
            if (url.contains("vcloud")) {
                extractDoubleAtob(scriptTag) ?: ""
            } else {
                Regex("var url = '([^']*)'").find(scriptTag)?.groupValues?.get(1) ?: ""
            }
        }

        if (link.isNotBlank() && !link.startsWith("https://") && !link.startsWith("http://")) {
            link = baseUrl.trimEnd('/') + "/" + link.trimStart('/')
        }

        if (link.isBlank()) return

        val document = try {
            app.get(link).document
        } catch (_: Exception) {
            return
        }

        val header = document.select("div.card-header").text()
        val size = document.select("i#size").text()
        val quality = getIndexQuality(header)

        suspend fun emit(finalLink: String, server: String = "") {
            callback.invoke(
                newExtractorLink(
                    "${name}${server}",
                    "${name}${server} ${header}[${size}]",
                    finalLink,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = quality
                }
            )
        }

        document.select("h2 a.btn").amap {
            val anchorLink = it.attr("href")
            val text = it.text()
            when {
                text.contains("FSL Server", ignoreCase = true) -> emit(anchorLink, "[FSL Server]")
                text.contains("FSLv2", ignoreCase = true) -> emit(anchorLink, "[FSLv2 Server]")
                text.contains("Mega Server", ignoreCase = true) -> emit(anchorLink, "[Mega Server]")
                text.contains("Download File", ignoreCase = true) -> emit(anchorLink)
                text.contains("pixeldra", ignoreCase = true) -> {
                    val finalURL = if (anchorLink.contains("download", true)) anchorLink
                    else "https://pixeldrain.com/api/file/${anchorLink.substringAfterLast("/")}?download"
                    emit(finalURL, "[Pixeldrain]")
                }
                text.contains("Gofile", ignoreCase = true) -> {
                    loadExtractor(anchorLink, "", subtitleCallback, callback)
                }
                else -> {
                    if (anchorLink.startsWith("http")) {
                        loadExtractor(anchorLink, "", subtitleCallback, callback)
                    }
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P1080.value
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            "https://vcloud.lol"
        }
    }
}
