package xyz.mpv.rex.cinehub.extension.providers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.extension.api.*
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Declarative provider instance representing an installed repository extension.
 * Provides metadata routing, search aggregation, episode discovery, and playback link resolution.
 */
class DeclarativeCineHubProvider(
    private val extension: InstalledExtension,
    private val client: OkHttpClient = OkHttpClient()
) : CineHubProvider {
    companion object {
        private const val TAG = "CineHub:Provider"
    }

    override val id: String = extension.pkgName
    override val name: String = extension.name
    override val version: String = extension.version
    override val author: String? = extension.classesFile
    override val iconUrl: String? = extension.iconUrl
    override val description: String? = extension.description
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override val lang: String = "en"
    override val hasMainPage: Boolean = true

    override suspend fun search(query: String): List<CineHubSearchItem> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<CineHubSearchItem>()
            Log.d(TAG, "[$name] Searching for: $query")

            // 1. Search movies
            val movieNodes = CineOnlineScraper.executeManualMovieSearch(query)
            for (node in movieNodes) {
                val title = node.title ?: "Untitled"
                results.add(
                    CineHubSearchItem(
                        id = "ext://$id/movie_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                        title = title,
                        url = "ext://$id/movie_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                        providerId = id,
                        providerName = name,
                        posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        type = TvType.Movie,
                        year = node.release_date?.take(4)?.toIntOrNull(),
                        rating = node.vote_average
                    )
                )
            }

            // 2. Search TV shows
            val tvNodes = CineOnlineScraper.executeManualTvSearch(query)
            for (node in tvNodes) {
                val title = node.name ?: "Untitled"
                results.add(
                    CineHubSearchItem(
                        id = "ext://$id/tv_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                        title = title,
                        url = "ext://$id/tv_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                        providerId = id,
                        providerName = name,
                        posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        type = TvType.TvSeries,
                        year = node.first_air_date?.take(4)?.toIntOrNull(),
                        rating = node.vote_average
                    )
                )
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "[$name] Search error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[$name] Loading home page sections")
            val trendingQuery = if (name.contains("Hindi", ignoreCase = true) || name.contains("Bollywood", ignoreCase = true)) "hindi" else "action"
            val tmdbResults = CineOnlineScraper.executeManualMovieSearch(trendingQuery)
            val movies = tmdbResults.take(15).map { node ->
                val title = node.title ?: "Untitled"
                CineHubSearchItem(
                    id = "ext://$id/movie_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                    title = title,
                    url = "ext://$id/movie_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                    providerId = id,
                    providerName = name,
                    posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    type = TvType.Movie,
                    year = node.release_date?.take(4)?.toIntOrNull(),
                    rating = node.vote_average
                )
            }

            val seriesQuery = if (name.contains("Anime", ignoreCase = true)) "anime" else "drama"
            val tmdbShows = CineOnlineScraper.executeManualTvSearch(seriesQuery)
            val shows = tmdbShows.take(15).map { node ->
                val title = node.name ?: "Untitled"
                CineHubSearchItem(
                    id = "ext://$id/tv_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                    title = title,
                    url = "ext://$id/tv_${node.id}?title=${URLEncoder.encode(title, "UTF-8")}",
                    providerId = id,
                    providerName = name,
                    posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    type = TvType.TvSeries,
                    year = node.first_air_date?.take(4)?.toIntOrNull(),
                    rating = node.vote_average
                )
            }

            listOf(
                CineHubHomePageList("$name Popular Movies", movies),
                CineHubHomePageList("$name TV Series", shows)
            )
        } catch (e: Exception) {
            Log.e(TAG, "[$name] Failed to load home page: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[$name] Loading details for url: $url")
            val clean = url.removePrefix("ext://$id/").substringBefore("?")
            val queryParams = parseQueryParams(url)
            val titleParam = queryParams["title"]?.let { URLDecoder.decode(it, "UTF-8") }

            val isTv = clean.startsWith("tv_") || url.contains("/tv_") || url.contains("type=tv")
            val rawId = clean.removePrefix("movie_").removePrefix("tv_")

            if (isTv) {
                val tv = CineOnlineScraper.getOrFetchTvShow(null, titleParam ?: rawId, rawId)
                if (tv != null) {
                    val episodes = loadEpisodes(url)
                    return@withContext CineHubMediaDetails(
                        id = url,
                        title = tv.title,
                        url = url,
                        providerId = id,
                        providerName = name,
                        posterUrl = tv.posterPath,
                        backdropUrl = tv.backdropPath,
                        overview = tv.plot,
                        year = tv.premiered.take(4).toIntOrNull(),
                        rating = tv.userRating,
                        genres = listOfNotNull(tv.genre.ifBlank { null }),
                        type = TvType.TvSeries,
                        episodes = episodes
                    )
                }
            }

            // Fallback or Movie details
            val movie = CineOnlineScraper.getOrFetchMovie(null, titleParam ?: "", rawId)
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
                    cast = movie.actors.map { it.name },
                    type = TvType.Movie
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$name] Failed to load details: ${e.message}", e)
            null
        }
    }

    override suspend fun loadEpisodes(url: String): List<CineHubEpisode> = withContext(Dispatchers.IO) {
        try {
            val clean = url.removePrefix("ext://$id/").substringBefore("?")
            val queryParams = parseQueryParams(url)
            val titleParam = queryParams["title"]?.let { URLDecoder.decode(it, "UTF-8") }
            val rawId = clean.removePrefix("movie_").removePrefix("tv_")

            Log.d(TAG, "[$name] Loading episodes for show id=$rawId, title=$titleParam")
            val episodes = mutableListOf<CineHubEpisode>()

            // Load season 1 and 2
            for (season in 1..2) {
                val seasonEps = CineOnlineScraper.fetchTvShowEpisodes(null, rawId, season, titleParam)
                seasonEps.forEach { ep ->
                    episodes.add(
                        CineHubEpisode(
                            id = "$url&season=${ep.season}&episode=${ep.episode}",
                            name = ep.title,
                            season = ep.season,
                            episode = ep.episode,
                            data = "ext://$id/$rawId?s=${ep.season}&e=${ep.episode}&title=${URLEncoder.encode(titleParam ?: "", "UTF-8")}",
                            posterUrl = ep.stillPath,
                            description = ep.plot
                        )
                    )
                }
            }

            episodes
        } catch (e: Exception) {
            Log.e(TAG, "[$name] Error loading episodes: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {
        val clean = data.removePrefix("ext://$id/").substringBefore("?")
        val streams = mutableListOf<CineHubStreamLink>()

        Log.d(TAG, "[$name] Resolving streams for data: $data")

        // If data is already an absolute HTTP/HTTPS URL
        if (data.startsWith("http://") || data.startsWith("https://")) {
            streams.add(
                CineHubStreamLink(
                    name = "$name Stream",
                    url = data,
                    quality = "1080p",
                    isM3u8 = data.contains(".m3u8")
                )
            )
            return@withContext streams
        }

        // Direct embed and HLS endpoints provided by this provider extension
        val rawId = clean.removePrefix("movie_").removePrefix("tv_")
        val params = parseQueryParams(data)
        val season = params["s"] ?: "1"
        val episode = params["e"] ?: "1"
        val isTv = params.containsKey("s") || clean.startsWith("tv_")

        // 1. VidSrc / Pro Stream embed link (can be extracted by Rabbitstream / ExtractorManager)
        val vidsrcUrl = if (isTv) {
            "https://vidsrc.me/embed/tv?tmdb=$rawId&season=$season&episode=$episode"
        } else {
            "https://vidsrc.me/embed/movie?tmdb=$rawId"
        }
        streams.add(
            CineHubStreamLink(
                name = "$name Primary Server (HD)",
                url = vidsrcUrl,
                quality = "1080p",
                isM3u8 = false,
                headers = mapOf("Referer" to "https://vidsrc.me/")
            )
        )

        // 2. Multi-source backup server
        val secondaryUrl = if (isTv) {
            "https://embed.su/embed/tv/$rawId/$season/$episode"
        } else {
            "https://embed.su/embed/movie/$rawId"
        }
        streams.add(
            CineHubStreamLink(
                name = "$name Mirror Server (720p)",
                url = secondaryUrl,
                quality = "720p",
                isM3u8 = false,
                headers = mapOf("Referer" to "https://embed.su/")
            )
        )

        streams
    }

    override suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> = withContext(Dispatchers.IO) {
        emptyList()
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val query = url.substringAfter("?", "")
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { part ->
            val kv = part.split("=", limit = 2)
            if (kv.isNotEmpty()) {
                val key = kv[0]
                val value = if (kv.size > 1) kv[1] else ""
                key to value
            } else null
        }.toMap()
    }
}
