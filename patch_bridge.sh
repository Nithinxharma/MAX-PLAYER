cat << 'INNER_EOF' > app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/providers/CineOnlineBridgeProvider.kt
package xyz.mpv.rex.cinehub.extension.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.data.CineCloudRepoClient
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.extension.api.*

class CineOnlineBridgeProvider(private val context: Context) : CineHubProvider {
    override val id: String = "cinehub_core"
    override val name: String = "CineHub Network"
    override val version: String = "2.5.0"
    override val author: String = "CineHub Core Team"
    override val iconUrl: String? = null
    override val description: String = "Integrated TMDB global index and fast streaming mirrors."
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    override val lang: String = "en"
    override val hasMainPage: Boolean = true

    override suspend fun search(query: String): List<CineHubSearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CineHubSearchItem>()
        val movies = CineOnlineScraper.executeManualMovieSearch(query)
        for (node in movies) {
            results.add(CineHubSearchItem(
                id = node.id.toString(), title = node.title ?: "Untitled", url = "tmdb://movie/${node.id}",
                providerId = id, providerName = name, posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                type = TvType.Movie, year = node.release_date?.take(4)?.toIntOrNull(), rating = node.vote_average
            ))
        }
        val shows = CineOnlineScraper.executeManualTvSearch(query)
        for (node in shows) {
            results.add(CineHubSearchItem(
                id = node.id.toString(), title = node.name ?: "Untitled", url = "tmdb://tv/${node.id}",
                providerId = id, providerName = name, posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                type = TvType.TvSeries, year = node.first_air_date?.take(4)?.toIntOrNull(), rating = node.vote_average
            ))
        }
        results
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val movies = CineCloudRepoClient.fetchOnlineMovies(context).take(15).map { movie ->
            CineHubSearchItem(
                id = movie.tmdbId.ifBlank { movie.title }, title = movie.title, url = "tmdb://movie/${movie.tmdbId.ifBlank { movie.title }}",
                providerId = id, providerName = name, posterUrl = movie.posterPath, type = TvType.Movie,
                year = movie.premiered.take(4).toIntOrNull(), rating = movie.userRating
            )
        }
        listOf(CineHubHomePageList("Trending on CineHub", movies))
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        val isTv = url.contains("tmdb://tv/")
        val idStr = url.removePrefix("tmdb://tv/").removePrefix("tmdb://movie/")
        if (isTv) {
            val show = CineOnlineScraper.getOrFetchTvShow(context, "", idStr) ?: return@withContext null
            val episodes = mutableListOf<CineHubEpisode>()
            show.seasons.forEach { season ->
                season.episodes.forEach { ep ->
                    episodes.add(CineHubEpisode(
                        id = "${url}_S${season.seasonNumber}E${ep.episodeNumber}", name = ep.title,
                        season = season.seasonNumber, episode = ep.episodeNumber, data = "tv:${idStr}:${season.seasonNumber}:${ep.episodeNumber}",
                        posterUrl = ep.stillPath
                    ))
                }
            }
            CineHubMediaDetails(
                id = show.tmdbId, title = show.title, url = url, providerId = id, providerName = name,
                posterUrl = show.posterPath, backdropUrl = show.backdropPath, overview = show.plot,
                year = show.premiered.take(4).toIntOrNull(), rating = show.userRating, genres = listOfNotNull(show.genre.ifBlank { null }),
                cast = show.actors.map { it.name }, type = TvType.TvSeries, episodes = episodes
            )
        } else {
            val movie = CineOnlineScraper.getOrFetchMovie(context, "", idStr) ?: return@withContext null
            CineHubMediaDetails(
                id = movie.tmdbId, title = movie.title, url = url, providerId = id, providerName = name,
                posterUrl = movie.posterPath, backdropUrl = movie.backdropPath, overview = movie.plot,
                year = movie.premiered.take(4).toIntOrNull(), rating = movie.userRating, genres = listOfNotNull(movie.genre.ifBlank { null }),
                cast = movie.actors.map { it.name }, type = TvType.Movie
            )
        }
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<CineHubStreamLink>()
        val clean = data.removePrefix("tmdb://movie/").removePrefix("tmdb://tv/").removePrefix("tv:").substringBefore("?")
        val nf = CineCloudRepoClient.resolveDirectStreamUrl(clean, "nf")
        val pv = CineCloudRepoClient.resolveDirectStreamUrl(clean, "pv")
        val hs = CineCloudRepoClient.resolveDirectStreamUrl(clean, "hs")
        val dp = CineCloudRepoClient.resolveDirectStreamUrl(clean, "dp")
        val best = nf ?: pv ?: hs ?: dp
        if (!best.isNullOrBlank() && !best.contains("/embed/")) {
            streams.add(CineHubStreamLink(name = "[$name] Vidstream", url = nf ?: best, quality = "1080p", isM3u8 = (nf ?: best).contains(".m3u8")))
            streams.add(CineHubStreamLink(name = "[$name] Filemoon", url = pv ?: best, quality = "1080p", isM3u8 = (pv ?: best).contains(".m3u8")))
            streams.add(CineHubStreamLink(name = "[$name] StreamTape", url = hs ?: best, quality = "720p", isM3u8 = (hs ?: best).contains(".m3u8")))
            streams.add(CineHubStreamLink(name = "[$name] Dood", url = dp ?: best, quality = "480p", isM3u8 = (dp ?: best).contains(".m3u8")))
        }
        streams
    }
}
INNER_EOF
