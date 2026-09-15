package xyz.mpv.rex.cinehub.failover

import kotlinx.serialization.Serializable

/**
 * Represents a single playable media stream candidate with associated metadata and headers.
 *
 * @property url Direct video stream or playlist URL (.mp4, .m3u8, etc.)
 * @property name Human-readable label (e.g. "Server 1 - 1080p", "Invidious Direct")
 * @property quality Resolution label (e.g. "1080p", "720p", "480p")
 * @property isM3u8 Whether the stream is an HLS playlist
 * @property headers HTTP headers required by the hoster (e.g. Referer, Origin, User-Agent)
 */
@Serializable
data class StreamCandidate(
    val url: String,
    val name: String = "Stream",
    val quality: String = "1080p",
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)
