package com.lagradost.cloudstream3.utils

enum class Qualities(val value: Int) {
    Unknown(0), P144(144), P240(240), P360(360), P480(480), P720(720), P1080(1080), P1440(1440), P2160(2160);
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
    VIDEO, M3U8, DASH, TORRENT, MAGNET
}

interface IDownloadableMinimum {
    val url: String
    var referer: String
    var headers: Map<String, String>
}

data class AudioFile(
    val url: String,
    val lang: String,
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

open class ExtractorLink(
    open val source: String,
    open val name: String,
    override val url: String,
    override var referer: String,
    open var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    open var extractorData: String? = null,
    open var type: ExtractorLinkType,
    open var audioTracks: List<AudioFile> = emptyList()
) : IDownloadableMinimum {
    val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
}

data class PlayListItem(
    val url: String,
    val isM3u8: Boolean
)

data class ExtractorLinkPlayList(
    override val source: String,
    override val name: String,
    val playlist: List<PlayListItem>,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
    override var audioTracks: List<AudioFile> = emptyList(),
) : ExtractorLink(
    source = source,
    name = name,
    url = "",
    referer = referer,
    quality = quality,
    headers = headers,
    extractorData = extractorData,
    type = type,
    audioTracks = audioTracks
)
