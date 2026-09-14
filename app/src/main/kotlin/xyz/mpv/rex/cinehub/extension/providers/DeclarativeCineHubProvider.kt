package xyz.mpv.rex.cinehub.extension.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.data.CineCloudRepoClient
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.extension.api.*
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import java.net.URLEncoder

/**
 * Declarative provider instance representing an installed repository extension.
 * Provides metadata routing, search aggregation, and playback link resolution.
 */
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
            val encoded = URLEncoder.encode(query, "UTF-8")
            val results = mutableListOf<CineHubSearchItem>()
            
            val nodes = CineOnlineScraper.executeManualMovieSearch(query)
            for (node in nodes) {
                results.add(
                    CineHubSearchItem(
                        id = "${id}_${node.id}",
                        title = node.title ?: "Untitled",
                        url = "ext://$id/${node.id}?title=${URLEncoder.encode(node.title ?: "", "UTF-8")}",
                        providerId = id,
                        providerName = name,
                        posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        type = TvType.Movie,
                        year = node.release_date?.take(4)?.toIntOrNull(),
                        rating = node.vote_average
                    )
                )
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val tmdbResults = CineOnlineScraper.executeManualMovieSearch("hindi")
        val movies = tmdbResults.take(15).map { node ->
            CineHubSearchItem(
                id = "${id}_${node.id}",
                title = node.title ?: "Untitled",
                url = "ext://$id/${node.id}?title=${java.net.URLEncoder.encode(node.title ?: "", "UTF-8")}",
                providerId = id,
                providerName = name,
                posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                type = TvType.Movie,
                year = node.release_date?.take(4)?.toIntOrNull(),
                rating = node.vote_average
            )
        }
        
        val tmdbShows = CineOnlineScraper.executeManualTvSearch("love")
        val shows = tmdbShows.take(15).map { node ->
            CineHubSearchItem(
                id = "${id}_${node.id}",
                title = node.name ?: "Untitled",
                url = "ext://$id/${node.id}?title=${java.net.URLEncoder.encode(node.name ?: "", "UTF-8")}",
                providerId = id,
                providerName = name,
                posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                type = TvType.TvSeries,
                year = node.first_air_date?.take(4)?.toIntOrNull(),
                rating = node.vote_average
            )
        }
        
        listOf(
            CineHubHomePageList("$name Movies", movies),
            CineHubHomePageList("$name Series", shows)
        )
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        try {
            // URL format: ext://providerId/tmdbId?title=...
            val clean = url.removePrefix("ext://$id/").substringBefore("?")
            val movie = CineOnlineScraper.getOrFetchMovie(null, "", clean)
            if (movie != null) {
                CineHubMediaDetails(
                    id = url,
                    title = movie.title,
                    url = url,
                    providerId = id,
                    providerName = name,
                    posterUrl = movie.posterPath,
                    backdropUrl = movie.backdropPath,
                    overview = movie.plot,
                    year = movie.premiered.take(4).toIntOrNull(),
                    rating = movie.userRating,
                    genres = listOfNotNull(movie.genre.ifBlank { null }),
                    cast = movie.actors.map { it.name }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {
        val clean = data.removePrefix("ext://$id/").substringBefore("?")
        val streams = mutableListOf<CineHubStreamLink>()

        // 1. Try Netmirror / CineCloud direct streams
        val netStream = CineCloudRepoClient.resolveDirectStreamUrl(clean, "nf") 
            ?: CineCloudRepoClient.resolveDirectStreamUrl(clean, "pv")
        if (!netStream.isNullOrBlank() && !netStream.contains("/embed/")) {
            streams.add(
                CineHubStreamLink(
                    name = "$name Server 1 (HD) (Hindi)",
                    url = netStream,
                    quality = "1080p",
                    isM3u8 = netStream.contains(".m3u8")
                )
            )
        }

        streams
    }
}
