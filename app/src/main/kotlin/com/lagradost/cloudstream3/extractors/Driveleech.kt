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

open class Driveleech : ExtractorApi() {
    override val name: String = "Driveleech"
    override var mainUrl: String = "https://driveleech.org"
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

        // Check download buttons
        document.select("div.text-center a").amap { anchor ->
            val text = anchor.text()
            val link = anchor.attr("href")
            when {
                text.contains("Direct Download", ignoreCase = true) || text.contains("Direct DL", ignoreCase = true) -> {
                    emit(link, "[Direct]")
                }
                text.contains("Cloud Download", ignoreCase = true) -> {
                    emit(link, "[Cloud]")
                }
                text.contains("CF Type", ignoreCase = true) || text.contains("GD Index", ignoreCase = true) -> {
                    listOf("1", "2").amap { t ->
                        try {
                            val subDoc = app.get("$link?type=$t").document
                            subDoc.select("a.btn-success").amap { btn ->
                                val direct = btn.attr("href")
                                if (direct.isNotBlank()) emit(direct, "[CF]")
                            }
                        } catch (_: Exception) {}
                    }
                }
                link.contains("pixeldra", ignoreCase = true) -> {
                    val finalURL = if (link.contains("download", true)) link
                    else "https://pixeldrain.com/api/file/${link.substringAfterLast("/")}?download"
                    emit(finalURL, "[Pixeldrain]")
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
            "https://driveleech.org"
        }
    }
}

class DriveleechNet : Driveleech() {
    override val name: String = "DriveleechNet"
    override var mainUrl: String = "https://driveleech.net"
}

class DriveFire : Driveleech() {
    override val name: String = "DriveFire"
    override var mainUrl: String = "https://drivefire.co"
}

class DriveFireIn : Driveleech() {
    override val name: String = "DriveFireIn"
    override var mainUrl: String = "https://drivefire.in"
}
