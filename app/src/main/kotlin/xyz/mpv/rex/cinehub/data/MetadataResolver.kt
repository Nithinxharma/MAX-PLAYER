package xyz.mpv.rex.cinehub.data

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import xyz.mpv.rex.cinehub.extension.api.CineHubMediaDetails
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.ui.browser.cinehub.ExtensionMediaDetails
import xyz.mpv.rex.cinehub.extension.api.TvType
import xyz.mpv.rex.cinehub.model.ActorItem
import xyz.mpv.rex.cinehub.model.MovieItem
import xyz.mpv.rex.cinehub.model.TvShowItem
import xyz.mpv.rex.preferences.MetadataSource
import xyz.mpv.rex.ui.browser.cinehub.components.DiscoveryMediaItem

/**
 * Resolved metadata representation for media detail presentation.
 */
data class ResolvedDetailMetadata(
    val title: String,
    val plot: String,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double,
    val year: String,
    val genre: String,
    val actors: List<ActorItem>
)

/**
 * Central Metadata Resolver for MAX STREAM.
 * Enforces unified metadata resolution across search discovery cards and detail view pages
 * adhering to the configured MetadataSource (TMDB enriched vs Original Provider Data).
 */
object MetadataResolver {

    /**
     * Resolves discovery media card for search results.
     * When [useTmdb] is true: Enriched with TMDB Title, Poster, Backdrop, Rating, and Year.
     * When [useTmdb] is false: Displays exact original provider title, poster, year, and metadata without TMDB overrides.
     */
    fun resolveDiscoveryMediaItem(
        ext: CineHubSearchItem,
        matchedMovie: TMDBMovieNode?,
        matchedTv: TMDBTvNode?,
        useTmdb: Boolean
    ): DiscoveryMediaItem {
        val isTv = ext.type == TvType.TvSeries || (useTmdb && matchedTv != null)
        val isAnime = ext.type == TvType.Anime

        val mediaType = if (isTv) "TV" else if (isAnime) "ANIME" else "MOVIE"

        return if (useTmdb) {
            val enrichedPoster = (matchedMovie?.poster_path ?: matchedTv?.poster_path)?.let { "https://image.tmdb.org/t/p/w500$it" }
                ?: ext.posterUrl
            val enrichedBackdrop = (matchedMovie?.backdrop_path ?: matchedTv?.backdrop_path)?.let { "https://image.tmdb.org/t/p/original$it" }
            val enrichedYear = matchedMovie?.release_date?.take(4) ?: matchedTv?.first_air_date?.take(4) ?: ext.year?.toString()
            val enrichedRating = matchedMovie?.vote_average ?: matchedTv?.vote_average ?: 8.0
            val enrichedTitle = matchedMovie?.title ?: matchedTv?.name ?: ext.title

            DiscoveryMediaItem(
                id = "ext_${ext.providerId}_${ext.url}",
                title = enrichedTitle,
                posterUrl = enrichedPoster,
                backdropUrl = enrichedBackdrop,
                year = enrichedYear,
                rating = enrichedRating,
                mediaType = mediaType,
                providerName = ext.providerName,
                rawItem = ext
            )
        } else {
            DiscoveryMediaItem(
                id = "ext_${ext.providerId}_${ext.url}",
                title = ext.title,
                posterUrl = ext.posterUrl,
                backdropUrl = null,
                year = ext.year?.toString(),
                rating = 0.0,
                mediaType = mediaType,
                providerName = ext.providerName,
                rawItem = ext
            )
        }
    }

    /**
     * Extracts raw provider / item metadata without TMDB enrichment.
     */
    fun extractRawMetadata(item: Any): ResolvedDetailMetadata {
        val rawTitle = when (item) {
            is MovieItem -> item.title
            is TvShowItem -> item.title
            is TMDBMovieNode -> item.title ?: ""
            is TMDBTvNode -> item.name ?: ""
            is ExtensionMediaDetails -> item.loadResponse.name
            is LoadResponse -> item.name
            is SearchResponse -> item.name
            is CineHubSearchItem -> item.title
            is CineHubMediaDetails -> item.title
            else -> ""
        }

        val rawPlot = when (item) {
            is MovieItem -> item.plot
            is TvShowItem -> item.plot
            is TMDBMovieNode -> item.overview ?: ""
            is TMDBTvNode -> item.overview ?: ""
            is ExtensionMediaDetails -> item.loadResponse.plot ?: ""
            is LoadResponse -> item.plot ?: ""
            is CineHubMediaDetails -> item.overview ?: ""
            else -> ""
        }

        val rawPoster = when (item) {
            is MovieItem -> item.posterPath
            is TvShowItem -> item.posterPath
            is TMDBMovieNode -> item.poster_path?.let { "${CineOnlineScraper.THUMB_BASE_URL}$it" }
            is TMDBTvNode -> item.poster_path?.let { "${CineOnlineScraper.THUMB_BASE_URL}$it" }
            is ExtensionMediaDetails -> item.loadResponse.posterUrl
            is LoadResponse -> item.posterUrl
            is SearchResponse -> item.posterUrl
            is CineHubSearchItem -> item.posterUrl
            is CineHubMediaDetails -> item.posterUrl
            else -> null
        }

        val rawBackdrop = when (item) {
            is MovieItem -> item.backdropPath ?: item.posterPath
            is TvShowItem -> item.backdropPath ?: item.posterPath
            is TMDBMovieNode -> (item.backdrop_path ?: item.poster_path)?.let { "${CineOnlineScraper.IMAGE_BASE_URL}$it" }
            is TMDBTvNode -> (item.backdrop_path ?: item.poster_path)?.let { "${CineOnlineScraper.IMAGE_BASE_URL}$it" }
            is ExtensionMediaDetails -> item.loadResponse.backgroundPosterUrl ?: item.loadResponse.posterUrl
            is LoadResponse -> item.backgroundPosterUrl ?: item.posterUrl
            is CineHubMediaDetails -> item.backdropUrl ?: item.posterUrl
            is SearchResponse -> item.posterUrl
            else -> null
        }

        val rawRating = when (item) {
            is MovieItem -> item.userRating
            is TvShowItem -> item.userRating
            is TMDBMovieNode -> item.vote_average
            is TMDBTvNode -> item.vote_average
            is ExtensionMediaDetails -> item.loadResponse.score?.score ?: 0.0
            is LoadResponse -> item.score?.score ?: 0.0
            is SearchResponse -> item.score?.score ?: 0.0
            else -> 0.0
        }

        val rawYear = when (item) {
            is MovieItem -> item.premiered.take(4)
            is TvShowItem -> item.premiered.take(4)
            is TMDBMovieNode -> item.release_date?.take(4) ?: ""
            is TMDBTvNode -> item.first_air_date?.take(4) ?: ""
            is ExtensionMediaDetails -> item.loadResponse.year?.toString() ?: ""
            is LoadResponse -> item.year?.toString() ?: ""
            is CineHubMediaDetails -> item.year?.toString() ?: ""
            else -> ""
        }

        val rawGenre = when (item) {
            is MovieItem -> item.genre
            is TvShowItem -> item.genre
            is TMDBMovieNode -> "Movie"
            is TMDBTvNode -> "Series"
            is ExtensionMediaDetails -> item.loadResponse.tags?.firstOrNull() ?: item.loadResponse.type.name
            is LoadResponse -> item.tags?.firstOrNull() ?: item.type.name
            is SearchResponse -> item.type?.name ?: ""
            is CineHubSearchItem -> item.type.name
            is CineHubMediaDetails -> item.type.name
            else -> ""
        }

        val rawActors = when (item) {
            is MovieItem -> item.actors
            is TvShowItem -> item.actors
            is ExtensionMediaDetails -> item.loadResponse.actors?.map { ActorItem(name = it.actor.name, character = it.role?.toString() ?: "", thumbUrl = it.actor.image ?: "") } ?: emptyList()
            is LoadResponse -> item.actors?.map { ActorItem(name = it.actor.name, character = it.role?.toString() ?: "", thumbUrl = it.actor.image ?: "") } ?: emptyList()
            else -> emptyList()
        }

        return ResolvedDetailMetadata(
            title = rawTitle,
            plot = rawPlot,
            posterPath = rawPoster,
            backdropPath = rawBackdrop,
            rating = rawRating,
            year = rawYear,
            genre = rawGenre,
            actors = rawActors
        )
    }

    /**
     * Resolves full detail metadata combining raw provider data with TMDB enrichment
     * based on the [useTmdb] toggle.
     */
    fun resolveDetailMetadata(
        item: Any,
        tmdbEnrichedMovie: MovieItem?,
        tmdbEnrichedTvShow: TvShowItem?,
        useTmdb: Boolean
    ): ResolvedDetailMetadata {
        val raw = extractRawMetadata(item)

        if (!useTmdb) {
            // When TMDB is disabled: Return provider values exactly as received
            return raw
        }

        // When TMDB is enabled: Prioritize TMDB enriched metadata
        val title = tmdbEnrichedMovie?.title ?: tmdbEnrichedTvShow?.title ?: raw.title
        val plot = tmdbEnrichedMovie?.plot?.takeIf { it.isNotBlank() && it != "No description." && it != "No description available." && it != "Local Media File." }
            ?: tmdbEnrichedTvShow?.plot?.takeIf { it.isNotBlank() && it != "No description." && it != "No description available." && it != "Local Media File." }
            ?: raw.plot
        val posterPath = tmdbEnrichedMovie?.posterPath ?: tmdbEnrichedTvShow?.posterPath ?: raw.posterPath
        val backdropPath = tmdbEnrichedMovie?.backdropPath ?: tmdbEnrichedTvShow?.backdropPath ?: raw.backdropPath
        val rating = tmdbEnrichedMovie?.userRating?.takeIf { it > 0.0 } ?: tmdbEnrichedTvShow?.userRating?.takeIf { it > 0.0 } ?: raw.rating
        val year = tmdbEnrichedMovie?.premiered?.take(4)?.takeIf { it.isNotBlank() && it != "2026" } ?: tmdbEnrichedTvShow?.premiered?.take(4)?.takeIf { it.isNotBlank() && it != "2026" } ?: raw.year
        val genre = tmdbEnrichedMovie?.genre?.takeIf { it.isNotBlank() } ?: tmdbEnrichedTvShow?.genre?.takeIf { it.isNotBlank() } ?: raw.genre
        val actorsList = tmdbEnrichedMovie?.actors?.takeIf { it.isNotEmpty() }
            ?: tmdbEnrichedTvShow?.actors?.takeIf { it.isNotEmpty() }
            ?: raw.actors

        return ResolvedDetailMetadata(
            title = title,
            plot = plot,
            posterPath = posterPath,
            backdropPath = backdropPath,
            rating = rating,
            year = year,
            genre = genre,
            actors = actorsList
        )
    }
}
