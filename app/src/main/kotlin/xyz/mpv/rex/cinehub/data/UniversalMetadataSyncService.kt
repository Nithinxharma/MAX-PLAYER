package xyz.mpv.rex.cinehub.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.database.entities.PlaybackStateEntity
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import java.util.concurrent.ConcurrentHashMap

data class UniversalMediaMetadata(
    val id: String,                         // File path, stream URL, or TMDB ID
    val title: String,                      // Clean title
    val episodeInfo: String? = null,        // e.g. "S01 E03"
    val landscapeFanartUrl: String? = null, // 16:9 Landscape backdrop image URL
    val posterUrl: String? = null,          // Poster image URL
    val currentPositionMs: Long = 0L,       // Playback position in milliseconds
    val totalDurationMs: Long = 0L,         // Total duration in milliseconds
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val rawPayload: Any? = null
) {
    val currentPositionSeconds: Long get() = (currentPositionMs / 1000L).coerceAtLeast(0L)
    val totalDurationSeconds: Long get() = (totalDurationMs / 1000L).coerceAtLeast(0L)
    val progressFraction: Float get() = if (totalDurationMs > 0L) (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val timestampFormatted: String get() {
        if (totalDurationMs <= 0L) return ""
        return "${formatTime(currentPositionSeconds)} / ${formatTime(totalDurationSeconds)}"
    }

    val remainingTimeFormatted: String get() {
        if (totalDurationMs <= 0L) return ""
        val remainingSec = (totalDurationSeconds - currentPositionSeconds).coerceAtLeast(0L)
        val minutes = remainingSec / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m left"
            minutes > 0 -> "${minutes}m left"
            else -> "${remainingSec}s left"
        }
    }

    private fun formatTime(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}

object UniversalMetadataSyncService {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val landscapeCache = ConcurrentHashMap<String, String>()
    private val _syncStateMap = MutableStateFlow<Map<String, UniversalMediaMetadata>>(emptyMap())
    val syncStateMap: StateFlow<Map<String, UniversalMediaMetadata>> = _syncStateMap.asStateFlow()

    /**
     * Fast non-suspend lookup for cached or TMDB converted landscape backdrop URLs.
     */
    fun getLandscapeFanartSync(title: String?, fallbackUrl: String?): String? {
        if (title.isNullOrBlank() && fallbackUrl.isNullOrBlank()) return null
        val cacheKey = title?.lowercase()?.trim() ?: ""
        if (cacheKey.isNotBlank()) {
            landscapeCache[cacheKey]?.let { return it }
        }
        if (!fallbackUrl.isNullOrBlank() && fallbackUrl.contains("image.tmdb.org")) {
            return fallbackUrl.replace(Regex("""/w\d{3,4}/"""), "/original/")
        }
        return fallbackUrl
    }

    /**
     * Updates or records playback progress & metadata synchronously across all app components.
     */
    fun updatePlaybackProgress(
        context: Context,
        id: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        episodeInfo: String? = null,
        posterUrl: String? = null,
        landscapeUrl: String? = null,
        recentlyPlayedRepo: RecentlyPlayedRepository? = null,
        playbackStateRepo: PlaybackStateRepository? = null
    ) {
        scope.launch {
            val resolvedLandscape = landscapeUrl ?: getOrFetchLandscapeFanart(context, title, posterUrl)

            val metadata = UniversalMediaMetadata(
                id = id,
                title = title,
                episodeInfo = episodeInfo,
                landscapeFanartUrl = resolvedLandscape ?: posterUrl,
                posterUrl = posterUrl,
                currentPositionMs = positionMs,
                totalDurationMs = durationMs,
                lastWatchedTimestamp = System.currentTimeMillis()
            )

            val currentMap = _syncStateMap.value.toMutableMap()
            currentMap[id] = metadata
            _syncStateMap.value = currentMap

            try {
                recentlyPlayedRepo?.addRecentlyPlayed(
                    filePath = id,
                    fileName = title,
                    videoTitle = title,
                    duration = durationMs,
                    fileSize = 0L
                )
                val posSec = (positionMs / 1000L).toInt()
                val remSec = ((durationMs - positionMs) / 1000L).coerceAtLeast(0L).toInt()
                playbackStateRepo?.upsert(
                    PlaybackStateEntity(
                        mediaTitle = title,
                        lastPosition = posSec,
                        playbackSpeed = 1.0,
                        sid = -1,
                        subDelay = 0,
                        subSpeed = 1.0,
                        aid = -1,
                        audioDelay = 0,
                        timeRemaining = remSec
                    )
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Resolves a high-resolution 16:9 Landscape Fanart/Backdrop image for any title or poster URL.
     * Uses cache first, then TMDB search query.
     */
    suspend fun getOrFetchLandscapeFanart(
        context: Context,
        title: String,
        fallbackPosterUrl: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext fallbackPosterUrl

        val cacheKey = title.lowercase().trim()
        landscapeCache[cacheKey]?.let { return@withContext it }

        if (!fallbackPosterUrl.isNullOrBlank() && fallbackPosterUrl.contains("image.tmdb.org")) {
            if (fallbackPosterUrl.contains("/backdrop") || fallbackPosterUrl.contains("/fanart")) {
                val highRes = fallbackPosterUrl.replace(Regex("""/w\d{3,4}/"""), "/original/")
                landscapeCache[cacheKey] = highRes
                return@withContext highRes
            }
        }

        try {
            val movies = CineOnlineScraper.executeManualMovieSearch(title, context)
            val firstMovie = movies.firstOrNull()
            if (firstMovie != null && !firstMovie.backdrop_path.isNullOrBlank()) {
                val backdropUrl = "https://image.tmdb.org/t/p/original" + firstMovie.backdrop_path
                landscapeCache[cacheKey] = backdropUrl
                return@withContext backdropUrl
            }

            val tvShows = CineOnlineScraper.executeManualTvSearch(title, context)
            val firstTv = tvShows.firstOrNull()
            if (firstTv != null && !firstTv.backdrop_path.isNullOrBlank()) {
                val backdropUrl = "https://image.tmdb.org/t/p/original" + firstTv.backdrop_path
                landscapeCache[cacheKey] = backdropUrl
                return@withContext backdropUrl
            }
        } catch (_: Exception) {}

        val highResFallback = if (!fallbackPosterUrl.isNullOrBlank() && fallbackPosterUrl.contains("image.tmdb.org")) {
            fallbackPosterUrl.replace(Regex("""/w\d{3,4}/"""), "/original/")
        } else fallbackPosterUrl

        if (!highResFallback.isNullOrBlank()) {
            landscapeCache[cacheKey] = highResFallback
        }
        return@withContext highResFallback
    }
}
