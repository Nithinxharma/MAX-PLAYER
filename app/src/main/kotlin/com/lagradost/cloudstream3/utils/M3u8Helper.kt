package com.lagradost.cloudstream3.utils

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
            name: String? = null
        ): List<ExtractorLink> {
            return listOf(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = streamUrl,
                    referer = referer,
                    quality = quality ?: Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = headers
                )
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
            return listOf(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = streamUrl,
                    referer = referer,
                    quality = quality ?: Qualities.Unknown.value,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                    headers = headers
                )
            )
        }
    }
}
