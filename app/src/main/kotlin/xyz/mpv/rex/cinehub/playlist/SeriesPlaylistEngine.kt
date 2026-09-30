package xyz.mpv.rex.cinehub.playlist

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.bridge.RexPlayerBridge
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.model.EpisodeItem
import xyz.mpv.rex.cinehub.model.TvShowItem
import xyz.mpv.rex.cinehub.playlist.model.AutoNextMode
import xyz.mpv.rex.cinehub.playlist.model.SeriesEpisode
import xyz.mpv.rex.cinehub.playlist.model.SeriesPlaylist
import xyz.mpv.rex.cinehub.playlist.model.SeriesSeason
import xyz.mpv.rex.cinehub.playlist.model.UpNextCardState
import xyz.mpv.rex.utils.media.MediaUtils

/**
 * Single source of truth for TV Show series playlists, season navigation,
 * episode progression, Up Next system, and synchronization with Player,
 * Continue Watching, and Watch History.
 */
object SeriesPlaylistEngine {
    private const val TAG = "SeriesPlaylistEngine"
    private const val PREFS_NAME = "maxstream_series_playlist_prefs"

    private val engineScope = CoroutineScope(Dispatchers.IO)

    private val _currentPlaylist = MutableStateFlow<SeriesPlaylist?>(null)
    val currentPlaylist: StateFlow<SeriesPlaylist?> = _currentPlaylist.asStateFlow()

    private val _upNextState = MutableStateFlow(UpNextCardState())
    val upNextState: StateFlow<UpNextCardState> = _upNextState.asStateFlow()

    // Callback for player to request playing an episode
    var onPlayEpisodeRequested: ((SeriesEpisode) -> Unit)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Smart Series Playlist Builder:
     * Combines CloudStream provider episodes with TMDB episode metadata.
     */
    fun buildAndSetPlaylist(
        context: Context,
        seriesId: String,
        seriesTitle: String,
        providerEpisodes: List<Episode> = emptyList(),
        localEpisodes: List<EpisodeItem> = emptyList(),
        tmdbTvShow: TvShowItem? = null,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        fanartUrl: String? = null,
        overview: String = "",
        year: String = "",
        rating: Double = 0.0,
        providerName: String = "",
        initialSeason: Int? = null,
        initialEpisode: Int? = null
    ): SeriesPlaylist {
        val prefs = getPrefs(context)
        val cleanSeriesTitle = CineOnlineScraper.cleanMediaFileName(seriesTitle).first.ifBlank { seriesTitle }

        // Choose best artwork priority: TMDB Fanart -> Provider Fanart -> TMDB Backdrop -> Provider Poster -> TMDB Poster
        val effectiveFanart = fanartUrl?.takeIf { it.isNotBlank() }
            ?: tmdbTvShow?.backdropPath?.takeIf { it.isNotBlank() }
            ?: backdropUrl?.takeIf { it.isNotBlank() }
            ?: posterUrl

        val effectiveBackdrop = backdropUrl?.takeIf { it.isNotBlank() }
            ?: tmdbTvShow?.backdropPath?.takeIf { it.isNotBlank() }
            ?: effectiveFanart

        val effectivePoster = posterUrl?.takeIf { it.isNotBlank() }
            ?: tmdbTvShow?.posterPath?.takeIf { it.isNotBlank() }

        val allSeriesEpisodes = mutableListOf<SeriesEpisode>()

        // 1. Build from Provider Episodes
        if (providerEpisodes.isNotEmpty()) {
            providerEpisodes.forEach { ep ->
                val sNum = ep.season ?: 1
                val eNum = ep.episode ?: 1
                val epTitle = ep.name?.takeIf { it.isNotBlank() } ?: "Episode $eNum"
                val epDataUrl = ep.data
                val epThumb = ep.posterUrl

                // Fetch saved progress for this episode
                val progressKey = "progress_${seriesId}_s${sNum}_e${eNum}"
                val savedPos = prefs.getLong("${progressKey}_pos", 0L)
                val savedDur = prefs.getLong("${progressKey}_dur", 0L)
                val isWatched = prefs.getBoolean("${progressKey}_watched", false) || (savedDur > 0 && savedPos.toFloat() / savedDur >= 0.90f)
                val progressPercent = if (savedDur > 0) (savedPos.toFloat() / savedDur).coerceIn(0f, 1f) else 0f

                allSeriesEpisodes.add(
                    SeriesEpisode(
                        episodeId = "$seriesId-S${sNum}E${eNum}",
                        seriesId = seriesId,
                        seriesTitle = cleanSeriesTitle,
                        seasonNumber = sNum,
                        episodeNumber = eNum,
                        title = epTitle,
                        overview = ep.description ?: "",
                        runtimeMinutes = 0,
                        stillPath = epThumb,
                        backdropPath = effectiveBackdrop,
                        posterPath = effectivePoster,
                        dataUrl = epDataUrl,
                        providerName = providerName,
                        progressSeconds = savedPos,
                        durationSeconds = savedDur,
                        progressPercent = progressPercent,
                        isWatched = isWatched
                    )
                )
            }
        } else if (localEpisodes.isNotEmpty()) {
            // 2. Build from local/NFO or TMDB scraped episodes
            localEpisodes.forEach { ep ->
                val sNum = ep.season
                val eNum = ep.episode
                val epTitle = ep.title.takeIf { it.isNotBlank() } ?: "Episode $eNum"
                val epDataUrl = ep.videoFilePath

                val progressKey = "progress_${seriesId}_s${sNum}_e${eNum}"
                val savedPos = prefs.getLong("${progressKey}_pos", 0L)
                val savedDur = prefs.getLong("${progressKey}_dur", 0L)
                val isWatched = prefs.getBoolean("${progressKey}_watched", false) || (savedDur > 0 && savedPos.toFloat() / savedDur >= 0.90f)
                val progressPercent = if (savedDur > 0) (savedPos.toFloat() / savedDur).coerceIn(0f, 1f) else 0f

                allSeriesEpisodes.add(
                    SeriesEpisode(
                        episodeId = "$seriesId-S${sNum}E${eNum}",
                        seriesId = seriesId,
                        seriesTitle = cleanSeriesTitle,
                        seasonNumber = sNum,
                        episodeNumber = eNum,
                        title = epTitle,
                        overview = ep.plot,
                        runtimeMinutes = 0,
                        stillPath = ep.stillPath,
                        backdropPath = effectiveBackdrop,
                        posterPath = effectivePoster,
                        dataUrl = epDataUrl,
                        providerName = providerName,
                        progressSeconds = savedPos,
                        durationSeconds = savedDur,
                        progressPercent = progressPercent,
                        isWatched = isWatched
                    )
                )
            }
        }

        // Group into seasons
        val seasonsMap = allSeriesEpisodes.groupBy { it.seasonNumber }
        val seasonsList = seasonsMap.entries.sortedBy { it.key }.map { (sNum, eps) ->
            SeriesSeason(
                seasonNumber = sNum,
                seasonTitle = if (sNum == 0) "Specials / OVAs" else "Season $sNum",
                episodes = eps.sortedBy { it.episodeNumber },
                posterPath = effectivePoster
            )
        }

        val rememberedSeason = prefs.getInt("last_season_$seriesId", 1)
        val rememberedEpisode = prefs.getInt("last_episode_$seriesId", 1)

        val targetSeason = initialSeason ?: rememberedSeason
        val targetEpisode = initialEpisode ?: rememberedEpisode

        val playlist = SeriesPlaylist(
            seriesId = seriesId,
            seriesTitle = cleanSeriesTitle,
            posterUrl = effectivePoster,
            backdropUrl = effectiveBackdrop,
            fanartUrl = effectiveFanart,
            overview = overview.ifBlank { tmdbTvShow?.plot ?: "" },
            year = year.ifBlank { tmdbTvShow?.premiered ?: "" },
            rating = if (rating > 0.0) rating else tmdbTvShow?.userRating ?: 0.0,
            providerName = providerName,
            seasons = seasonsList,
            currentSeasonNumber = targetSeason,
            currentEpisodeNumber = targetEpisode
        )

        _currentPlaylist.value = playlist
        Log.d(TAG, "Generated SeriesPlaylist for '$cleanSeriesTitle': ${seasonsList.size} seasons, ${allSeriesEpisodes.size} episodes")
        return playlist
    }

    /**
     * Enrich existing series playlist with TMDB metadata without overriding provider URLs.
     */
    fun enrichWithTmdb(tmdbEpisodes: List<EpisodeItem>) {
        val current = _currentPlaylist.value ?: return
        if (tmdbEpisodes.isEmpty()) return

        val tmdbMap = tmdbEpisodes.associateBy { "s${it.season}_e${it.episode}" }

        val updatedSeasons = current.seasons.map { season ->
            val updatedEps = season.episodes.map { ep ->
                val match = tmdbMap["s${ep.seasonNumber}_e${ep.episodeNumber}"]
                if (match != null) {
                    ep.copy(
                        title = if (ep.title.isBlank() || ep.title.startsWith("Episode ")) match.title else ep.title,
                        overview = ep.overview.ifBlank { match.plot },
                        runtimeMinutes = ep.runtimeMinutes,
                        stillPath = ep.stillPath ?: match.stillPath
                    )
                } else ep
            }
            season.copy(episodes = updatedEps)
        }

        _currentPlaylist.value = current.copy(seasons = updatedSeasons)
    }

    fun selectEpisode(seasonNumber: Int, episodeNumber: Int, context: Context? = null) {
        val current = _currentPlaylist.value ?: return
        _currentPlaylist.value = current.copy(
            currentSeasonNumber = seasonNumber,
            currentEpisodeNumber = episodeNumber
        )

        if (context != null) {
            val prefs = getPrefs(context)
            prefs.edit()
                .putInt("last_season_${current.seriesId}", seasonNumber)
                .putInt("last_episode_${current.seriesId}", episodeNumber)
                .apply()
        }
    }

    fun getNextEpisode(): SeriesEpisode? {
        return _currentPlaylist.value?.nextEpisode
    }

    fun getPreviousEpisode(): SeriesEpisode? {
        return _currentPlaylist.value?.previousEpisode
    }

    fun advanceToNextEpisode(context: Context? = null): SeriesEpisode? {
        val next = getNextEpisode() ?: return null
        selectEpisode(next.seasonNumber, next.episodeNumber, context)
        return next
    }

    fun goToPreviousEpisode(context: Context? = null): SeriesEpisode? {
        val prev = getPreviousEpisode() ?: return null
        selectEpisode(prev.seasonNumber, prev.episodeNumber, context)
        return prev
    }

    fun updatePlaybackProgress(
        context: Context,
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        positionSeconds: Long,
        durationSeconds: Long
    ) {
        if (seriesId.isBlank() || positionSeconds < 0) return

        val prefs = getPrefs(context)
        val progressKey = "progress_${seriesId}_s${seasonNumber}_e${episodeNumber}"
        val isWatched = durationSeconds > 0 && (positionSeconds.toFloat() / durationSeconds) >= 0.90f
        val progressPercent = if (durationSeconds > 0) (positionSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f) else 0f

        prefs.edit()
            .putLong("${progressKey}_pos", positionSeconds)
            .putLong("${progressKey}_dur", durationSeconds)
            .putBoolean("${progressKey}_watched", isWatched)
            .putInt("last_season_$seriesId", seasonNumber)
            .putInt("last_episode_$seriesId", episodeNumber)
            .apply()

        // Also update in-memory current playlist state
        val current = _currentPlaylist.value
        if (current != null && current.seriesId == seriesId) {
            val updatedSeasons = current.seasons.map { season ->
                if (season.seasonNumber == seasonNumber) {
                    val updatedEps = season.episodes.map { ep ->
                        if (ep.episodeNumber == episodeNumber) {
                            ep.copy(
                                progressSeconds = positionSeconds,
                                durationSeconds = durationSeconds,
                                progressPercent = progressPercent,
                                isWatched = isWatched
                            )
                        } else ep
                    }
                    season.copy(episodes = updatedEps)
                } else season
            }
            _currentPlaylist.value = current.copy(
                seasons = updatedSeasons,
                currentSeasonNumber = seasonNumber,
                currentEpisodeNumber = episodeNumber
            )
        }
    }

    fun markEpisodeWatched(
        context: Context,
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        isWatched: Boolean
    ) {
        val prefs = getPrefs(context)
        val progressKey = "progress_${seriesId}_s${seasonNumber}_e${episodeNumber}"
        prefs.edit()
            .putBoolean("${progressKey}_watched", isWatched)
            .apply()

        val current = _currentPlaylist.value
        if (current != null && current.seriesId == seriesId) {
            val updatedSeasons = current.seasons.map { season ->
                if (season.seasonNumber == seasonNumber) {
                    val updatedEps = season.episodes.map { ep ->
                        if (ep.episodeNumber == episodeNumber) {
                            ep.copy(isWatched = isWatched)
                        } else ep
                    }
                    season.copy(episodes = updatedEps)
                } else season
            }
            _currentPlaylist.value = current.copy(seasons = updatedSeasons)
        }
    }

    fun showUpNextCard(
        nextEpisode: SeriesEpisode,
        countdownSeconds: Int = 10,
        mode: AutoNextMode = AutoNextMode.Countdown
    ) {
        _upNextState.value = UpNextCardState(
            isVisible = true,
            nextEpisode = nextEpisode,
            countdownRemainingSeconds = countdownSeconds,
            totalCountdownSeconds = countdownSeconds,
            autoNextMode = mode
        )
    }

    fun updateUpNextCountdown(secondsRemaining: Int) {
        val current = _upNextState.value
        if (current.isVisible) {
            _upNextState.value = current.copy(countdownRemainingSeconds = secondsRemaining)
        }
    }

    fun dismissUpNextCard() {
        _upNextState.value = _upNextState.value.copy(isVisible = false)
    }

    fun clear() {
        _currentPlaylist.value = null
        _upNextState.value = UpNextCardState(isVisible = false)
    }
}
