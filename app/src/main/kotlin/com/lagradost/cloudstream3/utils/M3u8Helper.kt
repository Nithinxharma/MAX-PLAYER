package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile

class M3u8Helper {
    companion object {
        data class M3u8Stream(
            val streamUrl: String,
            val quality: Int? = null,
            val headers: Map<String, String> = emptyMap()
        )

        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = emptyMap(),
            name: String? = null,
            subtitleCallback: ((SubtitleFile) -> Unit)? = null,
            audioCallback: ((AudioFile) -> Unit)? = null
        ): List<ExtractorLink> {
            val headersWithReferer = buildMap {
                putAll(headers)
                if (referer.isNotBlank() && !containsKey("Referer") && !containsKey("referer")) {
                    put("Referer", referer)
                }
            }
            return PlaylistUtils.extractM3u8ToLinks(
                source = source,
                m3u8Url = streamUrl,
                referer = referer,
                headers = headersWithReferer,
                name = name,
                subtitleCallback = subtitleCallback,
                audioCallback = audioCallback
            )
        }

        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = emptyMap(),
            name: String? = null
        ): List<ExtractorLink> {
            return generateM3u8(
                source = source,
                streamUrl = streamUrl,
                referer = referer,
                quality = quality,
                headers = headers,
                name = name,
                subtitleCallback = null,
                audioCallback = null
            )
        }

        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = emptyMap(),
            name: String? = null,
            isM3u8: Boolean = true
        ): List<ExtractorLink> {
            return if (isM3u8) {
                generateM3u8(source, streamUrl, referer, quality, headers, name)
            } else {
                listOf(
                    ExtractorLink(
                        source = source,
                        name = name ?: source,
                        url = streamUrl,
                        referer = referer,
                        quality = quality ?: Qualities.Unknown.value,
                        type = ExtractorLinkType.VIDEO,
                        headers = headers
                    )
                )
            }
        }
    }

    data class M3u8Stream(
        val streamUrl: String,
        val quality: Int? = null,
        val headers: Map<String, String> = emptyMap()
    )

    suspend fun m3u8Generation(m3u8: M3u8Stream, returnThis: Boolean? = true): List<M3u8Stream> {
        val links = PlaylistUtils.extractM3u8ToLinks(
            source = "M3U8",
            m3u8Url = m3u8.streamUrl,
            headers = m3u8.headers
        )
        val result = links.map {
            M3u8Stream(
                streamUrl = it.url,
                quality = it.quality,
                headers = it.headers
            )
        }
        return if (result.isEmpty() && returnThis == true) {
            listOf(m3u8)
        } else {
            result
        }
    }
}

