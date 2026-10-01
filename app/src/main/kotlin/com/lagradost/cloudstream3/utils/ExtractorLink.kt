package com.lagradost.cloudstream3.utils

import androidx.annotation.Keep

@Keep
enum class ExtractorLinkType {
    VIDEO, TORRENT, M3U8, DASH, MAGNET
}

val INFER_TYPE = ExtractorLinkType.VIDEO

fun httpsify(url: String): String = if (url.startsWith("//")) "https:$url" else url

@Keep
enum class Qualities(val value: Int) {
    Unknown(0),
    P144(144),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P2160(2160)
}

fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null) return Qualities.Unknown.value
    return when {
        qualityName.contains("4k", ignoreCase = true) || qualityName.contains("2160", ignoreCase = true) -> Qualities.P2160.value
        qualityName.contains("1080", ignoreCase = true) -> Qualities.P1080.value
        qualityName.contains("720", ignoreCase = true) -> Qualities.P720.value
        qualityName.contains("480", ignoreCase = true) -> Qualities.P480.value
        qualityName.contains("360", ignoreCase = true) -> Qualities.P360.value
        qualityName.contains("240", ignoreCase = true) -> Qualities.P240.value
        else -> Qualities.Unknown.value
    }
}

@Keep
data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String = "",
    val quality: Int = Qualities.Unknown.value,
    val type: ExtractorLinkType = INFER_TYPE,
    val headers: Map<String, String> = emptyMap(),
    val extractorData: String? = null,
    val isM3u8: Boolean = url.contains(".m3u8", ignoreCase = true) || type == ExtractorLinkType.M3U8
)

@Keep
data class SubtitleFile(
    val lang: String,
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

fun newSubtitleFile(lang: String, url: String): SubtitleFile = SubtitleFile(lang, url)
