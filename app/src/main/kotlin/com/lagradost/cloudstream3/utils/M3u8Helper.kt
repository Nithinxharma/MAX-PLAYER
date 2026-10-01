package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.app
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

data class M3u8Stream(
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val quality: Int? = null
)

open class M3u8Helper {
    companion object {
        suspend fun m3u8Generation(
            stream: M3u8Stream,
            returnAll: Boolean = false
        ): List<ExtractorLink> = M3u8Helper().m3u8Generation(stream, returnAll)

        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = emptyMap(),
            name: String? = null
        ): List<ExtractorLink> = M3u8Helper().m3u8Generation(
            M3u8Stream(streamUrl, headers, quality),
            true
        ).map {
            it.copy(
                source = source,
                name = name ?: it.name,
                referer = referer
            )
        }
    }

    suspend fun m3u8Generation(
        stream: M3u8Stream,
        returnAll: Boolean = false
    ): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        val url = stream.streamUrl
        try {
            val response = app.get(url, headers = stream.headers)
            val content = response.text

            if (content.contains("#EXT-X-STREAM-INF")) {
                val lines = content.lines()
                var currentQuality: Int = stream.quality ?: Qualities.Unknown.value
                var currentBandwidth: String? = null

                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        // Extract resolution
                        val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                        if (resMatch != null) {
                            val height = resMatch.groupValues[2].toIntOrNull()
                            if (height != null) {
                                currentQuality = when {
                                    height >= 2160 -> Qualities.P2160.value
                                    height >= 1080 -> Qualities.P1080.value
                                    height >= 720 -> Qualities.P720.value
                                    height >= 480 -> Qualities.P480.value
                                    height >= 360 -> Qualities.P360.value
                                    else -> Qualities.P240.value
                                }
                            }
                        }
                    } else if (line.isNotEmpty() && !line.startsWith("#")) {
                        val subUrl = resolveUrl(url, line)
                        links.add(
                            ExtractorLink(
                                source = "M3U8",
                                name = "${currentQuality}p",
                                url = subUrl,
                                quality = currentQuality,
                                type = ExtractorLinkType.M3U8,
                                headers = stream.headers,
                                isM3u8 = true
                            )
                        )
                        currentQuality = stream.quality ?: Qualities.Unknown.value
                    }
                }
            }

            if (links.isEmpty()) {
                links.add(
                    ExtractorLink(
                        source = "M3U8",
                        name = "Auto (${stream.quality ?: 720}p)",
                        url = url,
                        quality = stream.quality ?: Qualities.P720.value,
                        type = ExtractorLinkType.M3U8,
                        headers = stream.headers,
                        isM3u8 = true
                    )
                )
            }
        } catch (e: Exception) {
            links.add(
                ExtractorLink(
                    source = "M3U8",
                    name = "Auto",
                    url = url,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = stream.headers,
                    isM3u8 = true
                )
            )
        }

        return if (returnAll) links else links.take(1)
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val base = URI(baseUrl)
                base.resolve(relativeUrl).toString()
            }
        } catch (e: Exception) {
            relativeUrl
        }
    }
}
