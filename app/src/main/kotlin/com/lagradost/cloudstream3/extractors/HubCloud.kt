package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.one"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val tag = "HubCloud"
        val realUrl = url.trim()
        val baseUrl = getBaseUrl(realUrl)

        val href = try {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val rawHref = app.get(realUrl).document.select("#download").attr("href")
                if (rawHref.startsWith("http", ignoreCase = true)) {
                    rawHref
                } else {
                    baseUrl.trimEnd('/') + "/" + rawHref.trimStart('/')
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to extract href: ${e.message}")
            ""
        }

        if (href.isBlank()) {
            return
        }

        val document = try {
            app.get(href).document
        } catch (e: Exception) {
            return
        }

        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val headerDetails = cleanTitle(header)
        val labelExtras = buildString {
            if (headerDetails.isNotEmpty()) append("[$headerDetails]")
            if (size.isNotEmpty()) append("[$size]")
        }
        val quality = getIndexQuality(header)

        document.select("div.card-body h2 a.btn").amap { element ->
            val link = element.attr("href")
            val text = element.text()
            when {
                text.contains("FSL Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$referer [FSL Server]",
                            "$referer [FSL Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }
                text.contains("Download File", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$referer",
                            "$referer $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }
                text.contains("BuzzServer", ignoreCase = true) -> {
                    try {
                        val buzzResp = app.get("$link/download", referer = link, allowRedirects = false)
                        val dlink = buzzResp.headers["hx-redirect"].orEmpty()
                        if (dlink.isNotBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    "$referer [BuzzServer]",
                                    "$referer [BuzzServer] $labelExtras",
                                    dlink,
                                ) { this.quality = quality }
                            )
                        }
                    } catch (_: Exception) {}
                }
                text.contains("pixeldra", ignoreCase = true) || text.contains("pixel", ignoreCase = true) -> {
                    val baseUrlLink = getBaseUrl(link)
                    val finalURL = if (link.contains("download", true)) link
                    else "$baseUrlLink/api/file/${link.substringAfterLast("/")}?download"
                    callback(
                        newExtractorLink(
                            "Pixeldrain",
                            "Pixeldrain $labelExtras",
                            finalURL
                        ) { this.quality = quality }
                    )
                }
                text.contains("S3 Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$referer S3 Server",
                            "$referer S3 Server $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }
                text.contains("FSLv2", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$referer FSLv2",
                            "$referer FSLv2 $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }
                text.contains("Mega Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$referer [Mega Server]",
                            "$referer [Mega Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }
                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
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
            ""
        }
    }

    private fun cleanTitle(title: String): String {
        val parts = title.split(".", "-", "_")
        val qualityTags = listOf("WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV", "CAM", "TS")
        val audioTags = listOf("AAC", "AC3", "DTS", "MP3", "FLAC", "DD5", "EAC3", "Atmos")
        val subTags = listOf("ESub", "ESubs", "Subs", "MultiSub", "NoSub")
        val codecTags = listOf("x264", "x265", "H264", "HEVC", "AVC")

        val startIndex = parts.indexOfFirst { part -> qualityTags.any { tag -> part.contains(tag, ignoreCase = true) } }
        val endIndex = parts.indexOfLast { part ->
            subTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                    audioTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                    codecTags.any { tag -> part.contains(tag, ignoreCase = true) }
        }

        return if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
            parts.subList(startIndex, endIndex + 1).joinToString(".")
        } else if (startIndex != -1) {
            parts.subList(startIndex, parts.size).joinToString(".")
        } else {
            parts.takeLast(3).joinToString(".")
        }
    }
}

open class HubuCloud : ExtractorApi() {
    override val name: String = "Hubu"
    override val mainUrl: String = "https://hubu.cloud"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        val streamUrl = doc.select("source").attr("src")
        if (streamUrl.isNotBlank()) {
            callback.invoke(newExtractorLink(source = name, name = name, url = streamUrl))
        }
    }
}
