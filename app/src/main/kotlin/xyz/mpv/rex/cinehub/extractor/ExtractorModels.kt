package xyz.mpv.rex.cinehub.extractor

import kotlinx.serialization.Serializable

/**
 * Extracted stream link metadata.
 */
@Serializable
data class ExtractorLinkData(
    val source: String,
    val name: String,
    val url: String,
    val referer: String? = null,
    val quality: String = "Auto",
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Extracted subtitle track metadata.
 */
@Serializable
data class SubtitleData(
    val language: String,
    val url: String,
    val isVtt: Boolean = true
)
