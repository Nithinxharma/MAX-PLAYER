package xyz.mpv.rex.cinehub.bridge

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.mpv.rex.cinehub.data.TMDBMovieNode
import xyz.mpv.rex.cinehub.data.TMDBTvNode
import xyz.mpv.rex.cinehub.extension.api.CineHubMediaDetails
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.utils.media.MediaUtils
import java.io.File

/**
 * Universal Bridge between CloudStream Scrapers / Extractors and REX MPV Player.
 * Resolves working media streams across CloudStream providers and launches playback.
 */
object CloudStreamBridgeResolver {

    private const val TAG = "CloudStreamBridge"

    data class ResolvedMediaDetails(
        val title: String,
        val overview: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val rating: Double?,
        val year: String?,
        val runtime: String?,
        val genres: List<String>,
        val cast: List<ActorData>,
        val isMovie: Boolean,
        val providerName: String,
        val dataUrl: String,
        val loadResponse: LoadResponse?,
        val rawItem: Any
    )

    /**
     * Resolves high-level media metadata for any clicked item.
     */
    suspend fun resolveDetails(item: Any): ResolvedMediaDetails? = withContext(Dispatchers.IO) {
        try {
            when (item) {
                is LoadResponse -> {
                    ResolvedMediaDetails(
                        title = item.name,
                        overview = item.plot,
                        posterUrl = item.posterUrl,
                        backdropUrl = item.backgroundPosterUrl ?: item.posterUrl,
                        rating = item.score?.score,
                        year = item.year?.toString(),
                        runtime = item.duration?.let { "$it mins" },
                        genres = item.tags ?: emptyList(),
                        cast = item.actors ?: emptyList(),
                        isMovie = item !is TvSeriesLoadResponse && item.type != TvType.TvSeries && item.type != TvType.Anime,
                        providerName = item.apiName,
                        dataUrl = if (item is MovieLoadResponse) item.dataUrl.ifBlank { item.url } else item.url,
                        loadResponse = item,
                        rawItem = item
                    )
                }

                is SearchResponse -> {
                    // Try to load full details from the provider
                    val provider = APIHolder.getApiFromNameNull(item.apiName)
                    val loaded = if (provider != null) {
                        try {
                            when (val res = APIRepository(provider).load(item.url)) {
                                is Resource.Success -> res.value
                                else -> null
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed loading details for ${item.name}: ${t.message}")
                            null
                        }
                    } else null

                    if (loaded != null) {
                        resolveDetails(loaded)
                    } else {
                        val isMovie = item.type != TvType.TvSeries && item.type != TvType.Anime
                        val itemYear = (item as? com.lagradost.cloudstream3.MovieSearchResponse)?.year?.toString()
                            ?: (item as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year?.toString()
                        ResolvedMediaDetails(
                            title = item.name,
                            overview = null,
                            posterUrl = item.posterUrl,
                            backdropUrl = item.posterUrl,
                            rating = item.score?.score,
                            year = itemYear,
                            runtime = null,
                            genres = item.type?.name?.let { listOf(it) } ?: emptyList(),
                            cast = emptyList(),
                            isMovie = isMovie,
                            providerName = item.apiName,
                            dataUrl = item.url,
                            loadResponse = null,
                            rawItem = item
                        )
                    }
                }

                is CineHubSearchItem -> {
                    val provider = APIHolder.getApiFromNameNull(item.providerName)
                        ?: APIHolder.getApiFromNameNull(item.providerId)
                    val loaded = if (provider != null) {
                        try {
                            when (val res = APIRepository(provider).load(item.url)) {
                                is Resource.Success -> res.value
                                else -> null
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed loading CineHubSearchItem details: ${t.message}")
                            null
                        }
                    } else null

                    if (loaded != null) {
                        resolveDetails(loaded)
                    } else {
                        val isMovie = item.type != xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries &&
                            item.type != xyz.mpv.rex.cinehub.extension.api.TvType.Anime
                        ResolvedMediaDetails(
                            title = item.title,
                            overview = null,
                            posterUrl = item.posterUrl,
                            backdropUrl = item.posterUrl,
                            rating = null,
                            year = item.year?.toString(),
                            runtime = null,
                            genres = listOf(item.type.name),
                            cast = emptyList(),
                            isMovie = isMovie,
                            providerName = item.providerName,
                            dataUrl = item.url,
                            loadResponse = null,
                            rawItem = item
                        )
                    }
                }

                is TMDBMovieNode -> {
                    // Search active CloudStream providers for this movie
                    val searchTitle = item.title ?: ""
                    val matchedItem = findProviderItemForTitle(searchTitle, isMovie = true)
                    if (matchedItem != null) {
                        val details = resolveDetails(matchedItem)
                        if (details != null) {
                            return@withContext details.copy(
                                overview = details.overview ?: item.overview,
                                posterUrl = details.posterUrl ?: item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                                backdropUrl = details.backdropUrl ?: item.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                                rating = details.rating ?: item.vote_average
                            )
                        }
                    }

                    ResolvedMediaDetails(
                        title = item.title ?: "",
                        overview = item.overview,
                        posterUrl = item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = item.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        rating = item.vote_average,
                        year = item.release_date?.take(4),
                        runtime = null,
                        genres = listOf("Movie"),
                        cast = emptyList(),
                        isMovie = true,
                        providerName = "TMDB",
                        dataUrl = item.id.toString(),
                        loadResponse = null,
                        rawItem = item
                    )
                }

                is TMDBTvNode -> {
                    val searchTitle = item.name ?: ""
                    val matchedItem = findProviderItemForTitle(searchTitle, isMovie = false)
                    if (matchedItem != null) {
                        val details = resolveDetails(matchedItem)
                        if (details != null) {
                            return@withContext details.copy(
                                overview = details.overview ?: item.overview,
                                posterUrl = details.posterUrl ?: item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                                backdropUrl = details.backdropUrl ?: item.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                                rating = details.rating ?: item.vote_average
                            )
                        }
                    }

                    ResolvedMediaDetails(
                        title = item.name ?: "",
                        overview = item.overview,
                        posterUrl = item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = item.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        rating = item.vote_average,
                        year = item.first_air_date?.take(4),
                        runtime = null,
                        genres = listOf("TV Series"),
                        cast = emptyList(),
                        isMovie = false,
                        providerName = "TMDB",
                        dataUrl = item.id.toString(),
                        loadResponse = null,
                        rawItem = item
                    )
                }

                else -> null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error resolving details: ${t.message}", t)
            null
        }
    }

    /**
     * Resolves working stream links and subtitles for a movie or TV episode.
     */
    suspend fun resolveStreamLinks(
        details: ResolvedMediaDetails,
        episode: Episode? = null
    ): Pair<List<ExtractorLink>, List<SubtitleFile>> = withContext(Dispatchers.IO) {
        val links = mutableListOf<ExtractorLink>()
        val subtitles = mutableListOf<SubtitleFile>()

        try {
            var targetProvider = APIHolder.getApiFromNameNull(details.providerName)
            var targetDataUrl = if (episode != null) episode.data else details.dataUrl

            // If the item came from TMDB or has no direct provider data URL, search providers
            if (targetProvider == null || targetDataUrl.all { it.isDigit() } || targetDataUrl.isBlank()) {
                val found = findProviderItemForTitle(details.title, details.isMovie)
                if (found != null) {
                    targetProvider = APIHolder.getApiFromNameNull(found.apiName)
                    val loaded = targetProvider?.let {
                        when (val r = APIRepository(it).load(found.url)) {
                            is Resource.Success -> r.value
                            else -> null
                        }
                    }
                    if (loaded is MovieLoadResponse) {
                        targetDataUrl = loaded.dataUrl.ifBlank { loaded.url }
                    } else if (loaded is TvSeriesLoadResponse && episode != null) {
                        val matchedEp = loaded.episodes.find {
                            it.episode == episode.episode && (episode.season == null || it.season == episode.season)
                        } ?: loaded.episodes.firstOrNull()
                        if (matchedEp != null) {
                            targetDataUrl = matchedEp.data
                        }
                    }
                }
            }

            if (targetProvider != null && targetDataUrl.isNotBlank()) {
                val repo = APIRepository(targetProvider)
                try {
                    repo.loadLinks(
                        data = targetDataUrl,
                        isCasting = false,
                        subtitleCallback = { sub ->
                            synchronized(subtitles) { subtitles.add(sub) }
                        },
                        callback = { link ->
                            synchronized(links) { links.add(link) }
                        }
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Error in loadLinks: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error resolving stream links: ${t.message}", t)
        }

        val sortedLinks = links.distinctBy { it.url }.sortedByDescending { it.quality }
        val sortedSubs = subtitles.distinctBy { it.url }
        sortedLinks to sortedSubs
    }

    /**
     * Searches across active CloudStream providers to match a title.
     */
    private suspend fun findProviderItemForTitle(
        title: String,
        isMovie: Boolean
    ): SearchResponse? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val cleanTitle = title.trim()
        val apis = APIHolder.apis.toList()

        val searchJobs = apis.map { provider ->
            async {
                try {
                    val repo = APIRepository(provider)
                    when (val res = repo.search(cleanTitle)) {
                        is Resource.Success -> {
                            res.value.list.firstOrNull { item ->
                                val itemTypeIsMovie = item.type != TvType.TvSeries && item.type != TvType.Anime
                                itemTypeIsMovie == isMovie && item.name.contains(cleanTitle, ignoreCase = true)
                            }
                        }
                        else -> null
                    }
                } catch (_: Throwable) {
                    null
                }
            }
        }

        searchJobs.awaitAll().filterNotNull().firstOrNull()
    }

    /**
     * Launches playback in REX MPV Player with full metadata, stream links, and subtitles.
     */
    fun launchPlayer(
        context: Context,
        streamUrl: String,
        title: String,
        posterUrl: String?,
        overview: String?,
        year: String?,
        rating: Double?,
        providerName: String,
        headers: Map<String, String>? = null,
        allLinks: List<ExtractorLink>? = null,
        subtitles: List<SubtitleFile>? = null,
        episode: Episode? = null
    ) {
        if (streamUrl.isBlank()) {
            Toast.makeText(context, "Stream URL is not available", Toast.LENGTH_SHORT).show()
            return
        }

        val subtitlesJson = if (!subtitles.isNullOrEmpty()) {
            try {
                Json.encodeToString(
                    subtitles.map { mapOf("lang" to it.lang, "url" to it.url) }
                )
            } catch (_: Throwable) {
                null
            }
        } else null

        val episodeMetadataJson = if (episode != null) {
            try {
                buildJsonObject {
                    put("seriesTitle", title)
                    put("episodeTitle", episode.name ?: "Episode ${episode.episode}")
                    put("season", (episode.season ?: 1).toString())
                    put("episode", (episode.episode ?: 1).toString())
                }.toString()
            } catch (_: Throwable) {
                null
            }
        } else null

        val displayTitle = if (episode != null) {
            "$title - S${episode.season ?: 1}E${episode.episode ?: 1} ${episode.name ?: ""}".trim()
        } else {
            title
        }

        MediaUtils.playFile(
            source = streamUrl,
            context = context,
            launchSource = "cinehub",
            headers = headers,
            subtitlesJson = subtitlesJson,
            episodeMetadataJson = episodeMetadataJson,
            title = displayTitle,
            posterUrl = episode?.posterUrl ?: posterUrl,
            overview = episode?.description ?: overview,
            year = year,
            rating = rating,
            providerName = providerName,
            allLinks = allLinks
        )
    }
}
