package com.maxstream.bridge

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CineHubEpisodeDetails(
    val data: String,
    val name: String?,
    val season: Int?,
    val episode: Int?,
    val posterUrl: String?,
    val plot: String? = null,
    val rating: Int? = null
)

data class CineHubMediaDetails(
    val title: String,
    val url: String,
    val apiName: String,
    val mediaType: String,
    val posterUrl: String?,
    val backgroundPosterUrl: String?,
    val plot: String?,
    val year: Int?,
    val tags: List<String>,
    val rating: Int?,
    val duration: Int?,
    val episodes: List<CineHubEpisodeDetails>,
    val streamDataUrl: String?
)

/**
 * CineHubDetailsBridge maps CloudStream LoadResponse outputs to CineHub Detail models.
 */
object CineHubDetailsBridge {
    private const val TAG = "CineHubDetailsBridge"

    /**
     * Loads metadata for a given media URL using the specified CloudStream provider.
     */
    suspend fun loadDetails(providerName: String, url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        val provider: MainAPI = APIHolder.getApiFromNameNull(providerName) ?: return@withContext null
        try {
            val response: LoadResponse = provider.load(url) ?: return@withContext null
            mapToCineHubDetails(response)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load details from provider $providerName for URL $url", t)
            null
        }
    }

    /**
     * Transforms a generic CloudStream LoadResponse into a unified CineHubMediaDetails object.
     */
    fun mapToCineHubDetails(response: LoadResponse): CineHubMediaDetails {
        var episodesList = emptyList<CineHubEpisodeDetails>()
        var streamDataUrl: String? = null

        when (response) {
            is MovieLoadResponse -> {
                streamDataUrl = response.dataUrl.ifBlank { response.url }
                episodesList = listOf(
                    CineHubEpisodeDetails(
                        data = streamDataUrl,
                        name = response.name,
                        season = 1,
                        episode = 1,
                        posterUrl = response.posterUrl,
                        plot = response.plot,
                        rating = response.rating
                    )
                )
            }
            is TvSeriesLoadResponse -> {
                episodesList = response.episodes.map { ep -> mapEpisode(ep) }
            }
            is AnimeLoadResponse -> {
                val epMap = mutableListOf<CineHubEpisodeDetails>()
                response.episodes.values.forEach { dubList ->
                    dubList.forEach { ep ->
                        epMap.add(mapEpisode(ep))
                    }
                }
                episodesList = epMap.distinctBy { "${it.season}_${it.episode}_${it.data}" }
            }
            is TorrentLoadResponse -> {
                streamDataUrl = response.torrent ?: response.magnet ?: response.url
                episodesList = listOf(
                    CineHubEpisodeDetails(
                        data = streamDataUrl,
                        name = response.name,
                        season = 1,
                        episode = 1,
                        posterUrl = response.posterUrl,
                        plot = response.plot
                    )
                )
            }
            else -> {
                streamDataUrl = response.url
            }
        }

        return CineHubMediaDetails(
            title = response.name,
            url = response.url,
            apiName = response.apiName,
            mediaType = response.type.name,
            posterUrl = response.posterUrl,
            backgroundPosterUrl = response.backgroundPosterUrl ?: response.posterUrl,
            plot = response.plot,
            year = response.year,
            tags = response.tags ?: emptyList(),
            rating = response.rating,
            duration = response.duration,
            episodes = episodesList,
            streamDataUrl = streamDataUrl
        )
    }

    private fun mapEpisode(ep: Episode): CineHubEpisodeDetails {
        return CineHubEpisodeDetails(
            data = ep.data,
            name = ep.name,
            season = ep.season,
            episode = ep.episode,
            posterUrl = ep.posterUrl,
            plot = ep.description,
            rating = ep.rating
        )
    }
}
