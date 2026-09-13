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
        // Query extension source or fallback to global media index filtered for this provider
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val results = mutableListOf<CineHubSearchItem>()
            
            // TMDB media index query tagged for this provider
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
        emptyList()
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
                    name = "$name Server 1 (HD)",
                    url = netStream,
                    quality = "1080p",
                    isM3u8 = netStream.contains(".m3u8")
                )
            )
        }

        // 2. Query Archive.org for public domain / open media matches
        try {
            val titleParam = runCatching {
                android.net.Uri.parse("http://dummy/$data").getQueryParameter("title")
            }.getOrNull() ?: clean
            val archiveProvider = xyz.mpv.rex.cinehub.extension.providers.OpenArchiveProvider()
            val archiveResults = archiveProvider.search(titleParam)
            val bestArchive = archiveResults.firstOrNull()
            if (bestArchive != null) {
                val archiveStreams = archiveProvider.loadStreams(bestArchive.url)
                streams.addAll(archiveStreams.map { it.copy(name = "$name (Archive ${it.quality})") })
            }
        } catch (e: Exception) {
            // Ignore archive lookup failure
        }

        streams
    }
}
