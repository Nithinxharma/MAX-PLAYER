package xyz.mpv.rex.cinehub.extension.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.data.CineCloudRepoClient
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.extension.api.*

/**
 * Bridges CineHub's native online catalogue and scraper engine into the ProviderRegistry.
 */
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
        val tmdbResults = CineOnlineScraper.executeManualMovieSearch(query)
        tmdbResults.map { node ->
            CineHubSearchItem(
                id = node.id.toString(),
                title = node.title ?: "Untitled",
                url = "tmdb://${node.id}",
                providerId = id,
                providerName = name,
                posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                type = TvType.Movie,
                year = node.release_date?.take(4)?.toIntOrNull(),
                rating = node.vote_average
            )
        }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val movies = CineCloudRepoClient.fetchOnlineMovies(context)
        val items = movies.take(15).map { movie ->
            CineHubSearchItem(
                id = movie.tmdbId.ifBlank { movie.title },
                title = movie.title,
                url = "tmdb://${movie.tmdbId.ifBlank { movie.title }}",
                providerId = id,
                providerName = name,
                posterUrl = movie.posterPath,
                type = TvType.Movie,
                year = movie.premiered.take(4).toIntOrNull(),
                rating = movie.userRating
            )
        }
        listOf(
            CineHubHomePageList(
                title = "Trending on CineHub",
                items = items
            )
        )
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? = withContext(Dispatchers.IO) {
        val idStr = url.removePrefix("tmdb://")
        val movie = CineOnlineScraper.getOrFetchMovie(context, "", idStr) ?: return@withContext null
        CineHubMediaDetails(
            id = movie.tmdbId,
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
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CineHubStreamLink>()
        loadLinks(data) { list.add(it) }
        list
    }

    override suspend fun loadLinks(
        data: String,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var foundAny = false
        val endpoints = if (data.contains(":")) listOf("uri") else listOf("nf", "pv", "hs", "dp")

        for ((idx, ep) in endpoints.withIndex()) {
            val direct = if (ep == "uri") {
                CineCloudRepoClient.resolveMediaUri(data)
            } else {
                CineCloudRepoClient.resolveDirectStreamUrl(data, ep)
            }

            if (!direct.isNullOrBlank() && (direct.startsWith("http://") || direct.startsWith("https://"))) {
                if (xyz.mpv.rex.cinehub.extractor.ExtractorManager.canExtract(direct)) {
                    val extracted = xyz.mpv.rex.cinehub.extractor.ExtractorManager.loadExtractor(
                        direct,
                        subtitleCallback = subtitleCallback,
                        callback = { link ->
                            foundAny = true
                            callback(link.copy(name = "CineHub Mirror - ${link.name}"))
                        }
                    )
                    if (extracted) foundAny = true
                } else if (!direct.contains("/embed/")) {
                    foundAny = true
                    val baseLink = CineHubStreamLink(
                        name = "CineHub Server ${idx + 1} (HD)",
                        url = direct,
                        quality = "1080p",
                        isM3u8 = direct.contains(".m3u8"),
                        host = "CineHub Network"
                    )
                    if (baseLink.isM3u8) {
                        val variants = xyz.mpv.rex.cinehub.extractor.M3u8Helper.extractM3u8(baseLink)
                        variants.forEach(callback)
                    } else {
                        callback(baseLink)
                    }
                }
            }
        }
        foundAny
    }
}
