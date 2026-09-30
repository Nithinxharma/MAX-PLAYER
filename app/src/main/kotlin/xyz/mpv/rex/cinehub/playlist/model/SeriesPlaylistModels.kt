package xyz.mpv.rex.cinehub.playlist.model

import kotlinx.serialization.Serializable
import com.lagradost.cloudstream3.utils.ExtractorLink

enum class AutoNextMode {
    Immediate,
    Countdown,
    UpNextCard
}

@Serializable
data class SeriesEpisode(
    val episodeId: String,
    val seriesId: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String = "",
    val runtimeMinutes: Int = 0,
    val stillPath: String? = null,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    val dataUrl: String = "",
    val providerName: String = "",
    val progressSeconds: Long = 0,
    val durationSeconds: Long = 0,
    val progressPercent: Float = 0f,
    val isWatched: Boolean = false,
    val streamLinks: List<String> = emptyList(),
    val directStreamUrl: String? = null
) {
    val formattedEpisodeCode: String
        get() = "S%02dE%02d".format(seasonNumber, episodeNumber)

    val displayTitle: String
        get() = if (title.isNotBlank() && !title.equals("Episode $episodeNumber", ignoreCase = true)) {
            "$formattedEpisodeCode - $title"
        } else {
            "$formattedEpisodeCode Episode $episodeNumber"
        }
}

@Serializable
data class SeriesSeason(
    val seasonNumber: Int,
    val seasonTitle: String = "Season $seasonNumber",
    val episodes: List<SeriesEpisode> = emptyList(),
    val posterPath: String? = null
)

@Serializable
data class SeriesPlaylist(
    val seriesId: String,
    val seriesTitle: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val fanartUrl: String? = null,
    val overview: String = "",
    val year: String = "",
    val rating: Double = 0.0,
    val providerName: String = "",
    val seasons: List<SeriesSeason> = emptyList(),
    val currentSeasonNumber: Int = 1,
    val currentEpisodeNumber: Int = 1
) {
    val allEpisodes: List<SeriesEpisode>
        get() = seasons.flatMap { it.episodes }

    val currentEpisode: SeriesEpisode?
        get() = seasons.firstOrNull { it.seasonNumber == currentSeasonNumber }
            ?.episodes?.firstOrNull { it.episodeNumber == currentEpisodeNumber }
            ?: allEpisodes.firstOrNull { it.seasonNumber == currentSeasonNumber && it.episodeNumber == currentEpisodeNumber }
            ?: allEpisodes.firstOrNull()

    val nextEpisode: SeriesEpisode?
        get() {
            val list = allEpisodes
            val currentIndex = list.indexOfFirst { it.seasonNumber == currentSeasonNumber && it.episodeNumber == currentEpisodeNumber }
            return if (currentIndex != -1 && currentIndex < list.size - 1) list[currentIndex + 1] else null
        }

    val previousEpisode: SeriesEpisode?
        get() {
            val list = allEpisodes
            val currentIndex = list.indexOfFirst { it.seasonNumber == currentSeasonNumber && it.episodeNumber == currentEpisodeNumber }
            return if (currentIndex > 0) list[currentIndex - 1] else null
        }

    fun getSeason(seasonNumber: Int): SeriesSeason? {
        return seasons.firstOrNull { it.seasonNumber == seasonNumber }
    }
}

data class UpNextCardState(
    val isVisible: Boolean = false,
    val nextEpisode: SeriesEpisode? = null,
    val countdownRemainingSeconds: Int = 10,
    val totalCountdownSeconds: Int = 10,
    val autoNextMode: AutoNextMode = AutoNextMode.Countdown
)
