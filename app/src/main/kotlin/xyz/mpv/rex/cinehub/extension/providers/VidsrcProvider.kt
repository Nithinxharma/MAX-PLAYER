package xyz.mpv.rex.cinehub.extension.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.extension.api.CineHubEpisode
import xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList
import xyz.mpv.rex.cinehub.extension.api.CineHubMediaDetails
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.api.CineHubSubtitleTrack
import xyz.mpv.rex.cinehub.extension.api.TvType

class VidsrcProvider : CineHubProvider {

    companion object {
        private const val TAG = "CineHub:VidsrcProvider"
    }

    override val id: String = "vidsrc_provider"
    override val name: String = "VidSrc (TMDB)"
    override val version: String = "1.0.0"
    override val description: String = "Reliable TMDB metadata + VidSrc Streams"
    override val hasMainPage: Boolean = true

    override suspend fun search(query: String): List<CineHubSearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CineHubSearchItem>()
        try {
            val movies = CineOnlineScraper.executeManualMovieSearch(query)
            movies.forEach { m ->
                results.add(
                    CineHubSearchItem(
                        id = "tmdb:movie:${m.id}",
                        title = m.title ?: "Unknown",
                        url = "tmdb:movie:${m.id}",
                        providerId = id,
                        providerName = name,
                        posterUrl = m.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        type = TvType.Movie,
                        year = m.release_date?.take(4)?.toIntOrNull()
                    )
                )
            }
            
            val shows = CineOnlineScraper.executeManualTvSearch(query)
            shows.forEach { s ->
                results.add(
                    CineHubSearchItem(
                        id = "tmdb:tv:${s.id}",
                        title = s.name ?: "Unknown",
                        url = "tmdb:tv:${s.id}",
                        providerId = id,
                        providerName = name,
                        posterUrl = s.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        type = TvType.TvSeries,
                        year = s.first_air_date?.take(4)?.toIntOrNull()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}")
        }
        results
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val lists = mutableListOf<CineHubHomePageList>()
        try {
            val trendingMovies = CineOnlineScraper.executeDiscoverMovies().map { m ->
                CineHubSearchItem(
                    id = "tmdb:movie:${m.id}",
                    title = m.title ?: "Unknown",
                    url = "tmdb:movie:${m.id}",
                    providerId = id,
                    providerName = name,
                    posterUrl = m.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    type = TvType.Movie
                )
            }
            if (trendingMovies.isNotEmpty()) {
                lists.add(CineHubHomePageList("Trending Movies", trendingMovies))
            }
            
            val trendingShows = CineOnlineScraper.executeDiscoverTv().map { s ->
                CineHubSearchItem(
                    id = "tmdb:tv:${s.id}",
                    title = s.name ?: "Unknown",
                    url = "tmdb:tv:${s.id}",
                    providerId = id,
                    providerName = name,
                    posterUrl = s.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    type = TvType.TvSeries
                )
            }
            if (trendingShows.isNotEmpty()) {
                lists.add(CineHubHomePageList("Trending TV Shows", trendingShows))
            }
        } catch (e: Exception) {
            Log.e(TAG, "HomePage error: ${e.message}")
        }
        lists
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        try {
            val parts = url.split(":")
            if (parts.size < 3) return@withContext null
            
            val typeStr = parts[1]
            val tmdbId = parts[2]
            
            if (typeStr == "movie") {
                val details = CineOnlineScraper.getOrFetchMovie(null, "fake_name_$tmdbId", tmdbId, true)
                return@withContext CineHubMediaDetails(
                    id = url,
                    title = details?.title ?: "Movie",
                    url = url,
                    providerId = id,
                    providerName = name,
                    posterUrl = details?.posterPath,
                    backdropUrl = details?.backdropPath,
                    overview = details?.plot,
                    type = TvType.Movie,
                    episodes = listOf(
                        CineHubEpisode(
                            id = "vidsrc:movie:$tmdbId",
                            name = "Movie",
                            season = 1,
                            episode = 1,
                            data = "https://vidsrc.me/embed/movie?tmdb=$tmdbId"
                        )
                    )
                )
            } else if (typeStr == "tv") {
                val showDetails = CineOnlineScraper.fetchTvShowDetails(tmdbId, null, null)
                val episodes = mutableListOf<CineHubEpisode>()
                
                showDetails?.seasons?.forEach { season ->
                    val sNum = season.season_number
                    if (sNum == 0) return@forEach // Skip specials
                    
                    val sEpisodes = CineOnlineScraper.fetchTvShowEpisodes(null, tmdbId, sNum, showDetails.name)
                    sEpisodes.forEach { ep ->
                        episodes.add(
                            CineHubEpisode(
                                id = "vidsrc:tv:$tmdbId:$sNum:${ep.episode}",
                                name = ep.title.ifBlank { "Episode ${ep.episode}" },
                                season = sNum,
                                episode = ep.episode,
                                data = "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$sNum&episode=${ep.episode}",
                                description = ep.plot
                            )
                        )
                    }
                }
                
                return@withContext CineHubMediaDetails(
                    id = url,
                    title = showDetails?.name ?: "TV Show",
                    url = url,
                    providerId = id,
                    providerName = name,
                    posterUrl = showDetails?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = showDetails?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    overview = showDetails?.overview,
                    type = TvType.TvSeries,
                    episodes = episodes
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Details error: ${e.message}")
        }
        null
    }

    override suspend fun loadEpisodes(url: String): List<CineHubEpisode> {
        return loadDetails(url)?.episodes ?: emptyList()
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> {
        return listOf(
            CineHubStreamLink(
                name = "VidSrc AutoEmbed",
                url = data,
                quality = "Auto",
                isM3u8 = false
            ),
            CineHubStreamLink(
                name = "VidSrc.to",
                url = data.replace(".me/", ".to/"),
                quality = "Auto",
                isM3u8 = false
            ),
            CineHubStreamLink(
                name = "Superembed",
                url = "https://multiembed.mov/?video_id=${data.substringAfter("tmdb=").substringBefore("&")}&tmdb=1",
                quality = "Auto",
                isM3u8 = false
            )
        )
    }

    override suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> = emptyList()
}
