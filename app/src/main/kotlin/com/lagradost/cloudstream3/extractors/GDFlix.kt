package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

open class GDFlix : ExtractorApi() {
    override val name: String = "GDFlix"
    override var mainUrl: String = "https://gdflix.cfd"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseUrl = getBaseUrl(url)
        val document = try {
            app.get(url).document
        } catch (_: Exception) {
            return
        }

        val fileName = document.select("ul > li.list-group-item:contains(Name)").text()
            .substringAfter("Name : ").trim()
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text()
            .substringAfter("Size : ").trim()
        val quality = getIndexQuality(fileName)

        suspend fun emit(link: String, server: String = "") {
            callback.invoke(
                newExtractorLink(
                    "${name}${server}",
                    "${name}${server} ${fileName}[${fileSize}]",
                    link,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = quality
                }
            )
        }

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.text()
            val link = anchor.attr("href")
            when {
                text.contains("FSL V2", ignoreCase = true) -> emit(link, "[FSL V2]")
                text.contains("DIRECT DL", ignoreCase = true) || text.contains("DIRECT SERVER", ignoreCase = true) -> emit(link, "[Direct]")
                text.contains("CLOUD DOWNLOAD [R2]", ignoreCase = true) -> emit(link, "[Cloud]")
                text.contains("GD Index", ignoreCase = true) -> {
                    val cfLink = if (link.startsWith("http")) link else baseUrl.trimEnd('/') + "/" + link.trimStart('/')
                    listOf(1, 2).amap { cfType ->
                        try {
                            app.get("$cfLink?type=$cfType").document.select("a.btn-success").amap {
                                val source = it.attr("href")
                                if (source.isNotBlank()) emit(source, "[CF]")
                            }
                        } catch (_: Exception) {}
                    }
                }
                text.contains("FAST CLOUD", ignoreCase = true) -> {
                    try {
                        val fastLink = if (link.startsWith("http")) link else baseUrl.trimEnd('/') + "/" + link.trimStart('/')
                        val dlink = app.get(fastLink).document.select("div.card-body a").attr("href")
                        if (dlink.isNotBlank()) emit(dlink, "[FAST CLOUD]")
                    } catch (_: Exception) {}
                }
                link.contains("pixeldra", ignoreCase = true) -> {
                    val baseUrlLink = getBaseUrl(link)
                    val finalURL = if (link.contains("download", true)) link
                    else "$baseUrlLink/api/file/${link.substringAfterLast("/")}?download"
                    emit(finalURL, "[Pixeldrain]")
                }
                text.contains("Instant DL", ignoreCase = true) -> {
                    try {
                        val instantResp = app.get(link, allowRedirects = false)
                        val loc = instantResp.headers["location"]
                        if (loc != null) emit(loc, "[Instant]")
                    } catch (_: Exception) {}
                }
                else -> {
                    if (link.startsWith("http")) {
                        loadExtractor(link, "", subtitleCallback, callback)
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
            "https://gdflix.cfd"
        }
    }
}

class GDLink : GDFlix() {
    override val name: String = "GDLink"
    override var mainUrl: String = "https://gdlink.net"
}

class GDFlixApp : GDFlix() {
    override val name: String = "GDFlixApp"
    override var mainUrl: String = "https://new.gdflix.cfd"
}

class GdFlix1 : GDFlix() {
    override val name: String = "GdFlix1"
    override var mainUrl: String = "https://new1.gdflix.cfd"
}

class GdFlix2 : GDFlix() {
    override val name: String = "GdFlix2"
    override var mainUrl: String = "https://gdflix.cfd"
}

class GDFlixNet : GDFlix() {
    override val name: String = "GDFlixNet"
    override var mainUrl: String = "https://new15.gdflix.cfd"
}

open class fastdlserver : ExtractorApi() {
    override val name = "fastdlserver"
    override var mainUrl = "https://fastdlserver.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val location = try {
            app.get(url, allowRedirects = false).headers["location"]
        } catch (_: Exception) {
            null
        }
        if (location != null) {
            loadExtractor(location, "", subtitleCallback, callback)
        }
    }
}
