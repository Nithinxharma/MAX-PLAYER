package xyz.mpv.rex.tv.model

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * Represents a media item (local phone video or online CineHub item) prepared for TV streaming.
 */
@Serializable
data class TvStreamItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val durationMs: Long = 0L,
    val uriString: String,
    val posterUrl: String? = null,
    val mimeType: String = "video/mp4",
    val isLocalFile: Boolean = true,
    val fileSize: Long = 0L,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val overview: String = "",
    val releaseYear: String = "",
    val rating: Double? = null
) {
    val uri: Uri
        get() = Uri.parse(uriString)

    val formattedDuration: String
        get() {
            if (durationMs <= 0L) return ""
            val totalSec = durationMs / 1000
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
}

/**
 * Real-time playback synchronization state between phone and TV Web Player.
 */
data class TvPlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isFullscreen: Boolean = false,
    val activeItem: TvStreamItem? = null,
    val lastCommand: String? = null,
    val lastCommandTimestamp: Long = 0L,
    val connectedClientsCount: Int = 0
)
