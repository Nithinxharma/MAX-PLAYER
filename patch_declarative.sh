cat << 'INNER_EOF' > app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/providers/DeclarativeCineHubProvider.kt
package xyz.mpv.rex.cinehub.extension.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URLEncoder
import xyz.mpv.rex.cinehub.data.CineCloudRepoClient
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.extension.api.*
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension

class DeclarativeCineHubProvider(
    private val extension: InstalledExtension,
    private val client: OkHttpClient = OkHttpClient()
) : CineHubProvider {
    override val id: String = extension.pkgName
    override val name: String = extension.name
    override val version: String = extension.version
    override val author: String? = null
    override val iconUrl: String? = extension.iconUrl
    override val description: String? = extension.description
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override val lang: String = "en"
    override val hasMainPage: Boolean = true

    override suspend fun search(query: String): List<CineHubSearchItem> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<CineHubSearchItem>()
            val movies = CineOnlineScraper.executeManualMovieSearch(query)
            for (node in movies) {
                results.add(CineHubSearchItem(
                    id = "${id}_m_${node.id}",
                    title = node.title ?: "Untitled",
                    url = "ext://$id/movie/${node.id}",
                    providerId = id,
                    providerName = name,
                    posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    type = TvType.Movie,
                    year = node.release_date?.take(4)?.toIntOrNull(),
                    rating = node.vote_average
                ))
            }
            val shows = CineOnlineScraper.executeManualTvSearch(query)
            for (node in shows) {
                results.add(CineHubSearchItem(
                    id = "${id}_t_${node.id}",
                    title = node.name ?: "Untitled",
                    url = "ext://$id/tv/${node.id}",
                    providerId = id,
                    providerName = name,
                    posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    type = TvType.TvSeries,
                    year = node.first_air_date?.take(4)?.toIntOrNull(),
                    rating = node.vote_average
                ))
            }
            results
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val movies = CineOnlineScraper.executeManualMovieSearch("hindi").take(15).map { node ->
            CineHubSearchItem(
                id = "${id}_m_${node.id}",
                title = node.title ?: "Untitled",
                url = "ext://$id/movie/${node.id}",
                providerId = id, providerName = name,
                posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                type = TvType.Movie, year = node.release_date?.take(4)?.toIntOrNull(), rating = node.vote_average
            )
        }
        val shows = CineOnlineScraper.executeManualTvSearch("love").take(15).map { node ->
            CineHubSearchItem(
                id = "${id}_t_${node.id}",
                title = node.name ?: "Untitled",
                url = "ext://$id/tv/${node.id}",
                providerId = id, providerName = name,
                posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                type = TvType.TvSeries, year = node.first_air_date?.take(4)?.toIntOrNull(), rating = node.vote_average
            )
        }
        listOf(CineHubHomePageList("$name Movies", movies), CineHubHomePageList("$name Series", shows))
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        try {
            val clean = url.removePrefix("ext://$id/")
            val isTv = clean.startsWith("tv/")
            val tmdbId = clean.removePrefix("tv/").removePrefix("movie/").substringBefore("?")
            if (isTv) {
                val show = CineOnlineScraper.getOrFetchTvShow(null, "", tmdbId) ?: return@withContext null
                val episodes = mutableListOf<CineHubEpisode>()
                show.seasons.forEach { season ->
                    season.episodes.forEach { ep ->
                        episodes.add(CineHubEpisode(
                            id = "${url}_S${season.seasonNumber}E${ep.episodeNumber}",
                            name = ep.title,
                            season = season.seasonNumber,
                            episode = ep.episodeNumber,
                            data = "tv:${tmdbId}:${season.seasonNumber}:${ep.episodeNumber}",
                            posterUrl = ep.stillPath
                        ))
                    }
                }
                CineHubMediaDetails(
                    id = url, title = show.title, url = url, providerId = id, providerName = name,
                    posterUrl = show.posterPath, backdropUrl = show.backdropPath, overview = show.plot,
                    year = show.premiered.take(4).toIntOrNull(), rating = show.userRating,
                    genres = listOfNotNull(show.genre.ifBlank { null }), cast = show.actors.map { it.name },
                    type = TvType.TvSeries, episodes = episodes
                )
            } else {
                val movie = CineOnlineScraper.getOrFetchMovie(null, "", tmdbId) ?: return@withContext null
                CineHubMediaDetails(
                    id = url, title = movie.title, url = url, providerId = id, providerName = name,
                    posterUrl = movie.posterPath, backdropUrl = movie.backdropPath, overview = movie.plot,
                    year = movie.premiered.take(4).toIntOrNull(), rating = movie.userRating,
                    genres = listOfNotNull(movie.genre.ifBlank { null }), cast = movie.actors.map { it.name },
                    type = TvType.Movie
                )
            }
        } catch (e: Exception) { null }
    }

    override suspend fun loadEpisodes(url: String): List<CineHubEpisode> = loadDetails(url)?.episodes ?: emptyList()

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<CineHubStreamLink>()
        val isTv = data.startsWith("tv:")
        val clean = data.removePrefix("ext://$id/").removePrefix("movie/").removePrefix("tv:").substringBefore("?")
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
