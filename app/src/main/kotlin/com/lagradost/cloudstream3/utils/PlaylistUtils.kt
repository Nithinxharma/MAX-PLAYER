package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.app

object PlaylistUtils {
    suspend fun extractM3u8ToLinks(
        source: String,
        m3u8Url: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null
    ): List<ExtractorLink> {
        return try {
            val content = app.get(m3u8Url, headers = headers, referer = referer).text
            parseM3u8Content(source, m3u8Url, content, referer, headers, name)
        } catch (_: Throwable) {
            listOf(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = m3u8Url,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = headers
                )
            )
        }
    }

    fun parseM3u8Content(
        source: String,
        m3u8Url: String,
        content: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null
    ): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        val lines = content.lines()
        var currentResolution: Int? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val resMatch = Regex("""RESOLUTION=\d+x(\d+)""").find(line)
                currentResolution = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val streamUrl = if (line.startsWith("http")) line else {
                    val baseUrl = m3u8Url.substringBeforeLast("/")
                    "$baseUrl/$line"
                }
                links.add(
                    ExtractorLink(
                        source = source,
                        name = name ?: source,
                        url = streamUrl,
                        referer = referer,
                        quality = currentResolution ?: Qualities.Unknown.value,
                        type = ExtractorLinkType.M3U8,
                        headers = headers
                    )
                )
                currentResolution = null
            }
        }

        if (links.isEmpty()) {
            links.add(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = m3u8Url,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = headers
                )
            )
        }

        return links
    }

    suspend fun extractDashToLinks(
        source: String,
        dashUrl: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null
    ): List<ExtractorLink> {
        return listOf(
            ExtractorLink(
                source = source,
                name = name ?: source,
                url = dashUrl,
                referer = referer,
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.DASH,
                headers = headers
            )
        )
    }
}
