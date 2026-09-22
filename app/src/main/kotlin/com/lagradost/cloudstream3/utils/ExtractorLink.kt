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
    open var type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    override var headers: Map<String, String> = mapOf(),
    open var extractorData: String? = null,
    open var errorMessage: String? = null,
    open var audioTracks: List<AudioFile> = emptyList()
) : IDownloadableMinimum {

    // Overloaded legacy constructor using Boolean isM3u8
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        extractorData: String? = null,
        errorMessage: String? = null
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        headers = headers,
        extractorData = extractorData,
        errorMessage = errorMessage
    )

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
    override var type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    override var headers: Map<String, String> = mapOf(),
    override var extractorData: String? = null,
    override var audioTracks: List<AudioFile> = emptyList(),
) : ExtractorLink(
    source = source,
    name = name,
    url = "",
    referer = referer,
    quality = quality,
    type = type,
    headers = headers,
    extractorData = extractorData,
    audioTracks = audioTracks
)

data class DrmExtractorLink(
    override val source: String,
    override val name: String,
    override val url: String,
    override var referer: String = "",
    override var quality: Int = Qualities.Unknown.value,
    override var type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    override var headers: Map<String, String> = mapOf(),
    override var extractorData: String? = null,
    val kid: String? = null,
    val key: String? = null,
    val kType: String? = null
) : ExtractorLink(
    source = source,
    name = name,
    url = url,
    referer = referer,
    quality = quality,
    type = type,
    headers = headers,
    extractorData = extractorData
)

suspend fun newDrmExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    initializer: suspend DrmExtractorLink.() -> Unit = {}
): DrmExtractorLink {
    val link = DrmExtractorLink(
        source = source,
        name = name,
        url = url,
        type = type
    )
    initializer(link)
    return link
}

