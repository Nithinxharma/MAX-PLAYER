package com.lagradost.cloudstream3.utils

enum class Qualities(val value: Int) {
    Unknown(0),
    P144(144),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P1440(1440),
    P2160(2160);

    companion object {
        fun getStringByInt(quality: Int?): String {
            return when (quality) {
                null, 0 -> "Auto"
                2160 -> "4K"
                else -> "${quality}p"
            }
        }
    }
}

enum class ExtractorLinkType {
    M3U8,
    DASH,
    VIDEO,
    TORRENT,
    MAGNET
}

open class ExtractorLink(
    open val source: String,
    open val name: String,
    open val url: String,
    open val referer: String,
    open val quality: Int,
    open val isM3u8: Boolean = false,
    open val headers: Map<String, String> = emptyMap(),
    open val extractorData: String? = null,
    open val type: ExtractorLinkType = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
)
