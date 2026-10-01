package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.app
import java.util.concurrent.CopyOnWriteArrayList

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    open val requiresReferer: Boolean = false

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = getUrl(url, referer)
        links.forEach { callback(it) }
    }

    open suspend fun getUrl(
        url: String,
        referer: String? = null
    ): List<ExtractorLink> {
        return emptyList()
    }
}

val extractorApis: CopyOnWriteArrayList<ExtractorApi> = CopyOnWriteArrayList()

fun registerExtractor(extractor: ExtractorApi) {
    extractorApis.removeAll { it.name.equals(extractor.name, ignoreCase = true) }
    extractorApis.add(extractor)
}

suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    if (url.isBlank()) return false
    Log.d("CloudStreamExtractor", "loadExtractor: $url (referer: $referer)")

    // Check direct video streams (m3u8, mp4, etc.)
    val cleanUrl = url.trim()
    if (cleanUrl.contains(".m3u8", ignoreCase = true)) {
        val m3u8Links = M3u8Helper.m3u8Generation(
            M3u8Stream(cleanUrl, if (referer != null) mapOf("Referer" to referer) else emptyMap()),
            returnAll = true
        )
        if (m3u8Links.isNotEmpty()) {
            m3u8Links.forEach { callback(it) }
            return true
        }
    } else if (cleanUrl.endsWith(".mp4", ignoreCase = true) || cleanUrl.endsWith(".mkv", ignoreCase = true)) {
        callback(
            ExtractorLink(
                source = "Direct",
                name = "Direct Video (1080p)",
                url = cleanUrl,
                referer = referer ?: "",
                quality = Qualities.P1080.value,
                type = ExtractorLinkType.VIDEO,
                headers = if (referer != null) mapOf("Referer" to referer) else emptyMap()
            )
        )
        return true
    }

    // Try registered extractors
    for (extractor in extractorApis) {
        val domain = extractor.mainUrl.replace("https://", "").replace("http://", "").trimEnd('/')
        if (cleanUrl.contains(domain, ignoreCase = true)) {
            try {
                extractor.getUrl(cleanUrl, referer, subtitleCallback, callback)
                return true
            } catch (t: Throwable) {
                Log.e("CloudStreamExtractor", "Extractor ${extractor.name} failed: ${t.message}", t)
            }
        }
    }

    // Fallback: Generic iframe & video tag scraper for unsupported embeds
    return try {
        val headers = mutableMapOf<String, String>()
        if (!referer.isNullOrBlank()) headers["Referer"] = referer
        val doc = app.get(cleanUrl, headers = headers).document

        // Check for unpacked packed scripts
        val scripts = doc.select("script").map { it.data() }
        var foundLink = false
        for (script in scripts) {
            val unpacked = if (script.contains("eval(function(p,a,c,k,e,d)")) {
                Unpacker.unpack(script) ?: script
            } else {
                script
            }

            // Look for file: "..." or sources: [{file: "..."}]
            val fileMatches = Regex("""(?:file|source|src)\s*:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(unpacked)
            for (match in fileMatches) {
                val streamUrl = match.groupValues[1]
                if (streamUrl.contains(".m3u8")) {
                    M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, headers), returnAll = true).forEach {
                        callback(it)
                    }
                    foundLink = true
                } else {
                    callback(
                        ExtractorLink(
                            source = "Scraped",
                            name = "Video",
                            url = streamUrl,
                            referer = cleanUrl,
                            quality = Qualities.P720.value,
                            type = ExtractorLinkType.VIDEO
                        )
                    )
                    foundLink = true
                }
            }
        }

        // Check video src or source src elements
        val videoSrc = doc.select("video source[src], video[src]").firstOrNull()?.attr("src")
        if (!videoSrc.isNullOrBlank()) {
            val resolved = if (videoSrc.startsWith("//")) "https:$videoSrc" else videoSrc
            callback(
                ExtractorLink(
                    source = "Scraped",
                    name = "HTML5 Video",
                    url = resolved,
                    referer = cleanUrl,
                    quality = Qualities.P720.value,
                    type = if (resolved.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                )
            )
            foundLink = true
        }

        foundLink
    } catch (e: Exception) {
        Log.e("CloudStreamExtractor", "Generic embed scrape failed for $cleanUrl: ${e.message}")
        false
    }
}

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean = loadExtractor(url, null, subtitleCallback, callback)

suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    callback: (ExtractorLink) -> Unit
): Boolean = loadExtractor(url, referer, {}, callback)

suspend fun loadExtractor(
    url: String,
    callback: (ExtractorLink) -> Unit
): Boolean = loadExtractor(url, null, {}, callback)
