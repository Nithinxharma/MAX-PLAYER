package xyz.mpv.rex.cinehub.data

import android.content.Context
import xyz.mpv.rex.cinehub.model.*
import xyz.mpv.rex.utils.media.MediaInfoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@kotlinx.serialization.Serializable
data class TMDBMovieNode(
    val id: Int = 0,
    val title: String? = null, 
    val overview: String? = null, 
    val poster_path: String? = null, 
    val backdrop_path: String? = null,
    val vote_average: Double = 0.0, 
    val release_date: String? = null
)

@kotlinx.serialization.Serializable
data class TMDBTvNode(
    val id: Int = 0,
    val name: String? = null, 
    val overview: String? = null, 
    val poster_path: String? = null, 
    val backdrop_path: String? = null,
    val vote_average: Double = 0.0, 
    val first_air_date: String? = null
)

@kotlinx.serialization.Serializable
data class TMDBImage(val file_path: String, val iso_639_1: String? = null, val vote_average: Double = 0.0)

@kotlinx.serialization.Serializable
data class TMDBImagesResponse(val backdrops: List<TMDBImage> = emptyList(), val logos: List<TMDBImage> = emptyList(), val posters: List<TMDBImage> = emptyList())

@kotlinx.serialization.Serializable
data class TMDBCreditsResponse(val cast: List<TMDBCastNode> = emptyList(), val crew: List<TMDBCrewNode> = emptyList())

@kotlinx.serialization.Serializable
data class TMDBCastNode(val id: Int, val name: String, val character: String? = null, val profile_path: String? = null)

@kotlinx.serialization.Serializable
data class TMDBCrewNode(val id: Int, val name: String, val job: String? = null)

@kotlinx.serialization.Serializable
data class TMDBMovieDetails(
    val id: Int,
    val title: String,
    val overview: String?,
    val tagline: String?,
    val runtime: Int?,
    val release_date: String?,
    val vote_average: Double,
    val poster_path: String?,
    val backdrop_path: String?,
    val belongs_to_collection: TMDBCollectionNode?,
    val imdb_id: String?,
    val genres: List<TMDBGenre> = emptyList(),
    val credits: TMDBCreditsResponse? = null,
    val images: TMDBImagesResponse? = null
)

@kotlinx.serialization.Serializable
data class TMDBGenre(val id: Int, val name: String)

@kotlinx.serialization.Serializable
data class TMDBCollectionNode(val id: Int, val name: String, val poster_path: String?, val backdrop_path: String?)

@kotlinx.serialization.Serializable
data class TMDBPersonDetails(
    val id: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val profile_path: String?,
    val known_for_department: String?
)

@kotlinx.serialization.Serializable
data class TMDBMovieSearchWrapper(val results: List<TMDBMovieNode>)

@kotlinx.serialization.Serializable
data class TMDBTvSearchWrapper(val results: List<TMDBTvNode>)

@kotlinx.serialization.Serializable
data class TMDBTvDetails(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val vote_average: Double = 0.0,
    val first_air_date: String? = null,
    val number_of_seasons: Int = 1,
    val number_of_episodes: Int = 1,
    val seasons: List<TMDBSeasonSummary> = emptyList(),
    val genres: List<TMDBGenre> = emptyList(),
    val credits: TMDBCreditsResponse? = null
)

@kotlinx.serialization.Serializable
data class TMDBSeasonSummary(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    val season_number: Int = 1,
    val episode_count: Int = 1,
    val poster_path: String? = null
)

@kotlinx.serialization.Serializable
data class TMDBSeasonResponse(
    val id: Int = 0,
    val name: String = "",
    val season_number: Int = 1,
    val episodes: List<TMDBEpisodeNode> = emptyList()
)

@kotlinx.serialization.Serializable
data class TMDBEpisodeNode(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val episode_number: Int = 1,
    val season_number: Int = 1,
    val air_date: String? = null,
    val still_path: String? = null,
    val vote_average: Double = 0.0,
    val runtime: Int? = null
)

@kotlinx.serialization.Serializable
data class TVMazeEpisodeNode(
    val id: Int = 0,
    val name: String? = null,
    val season: Int = 1,
    val number: Int = 1,
    val summary: String? = null,
    val airdate: String? = null,
    val image: TVMazeImage? = null
)

@kotlinx.serialization.Serializable
data class TVMazeShowNode(
    val id: Int = 0,
    val name: String? = null,
    val summary: String? = null,
    val premiered: String? = null,
    val rating: TVMazeRating? = null,
    val image: TVMazeImage? = null,
    val externals: TVMazeExternals? = null
)

@kotlinx.serialization.Serializable
data class TVMazeExternals(val thetvdb: Int? = null, val imdb: String? = null)

@kotlinx.serialization.Serializable
data class TVMazeRating(val average: Double? = null)

@kotlinx.serialization.Serializable
data class TVMazeImage(val original: String? = null, val medium: String? = null)

@kotlinx.serialization.Serializable
data class TVMazeSearchWrapper(val show: TVMazeShowNode? = null)

data class OnlineMediaMetadata(
    val title: String, 
    val plot: String, 
    val rating: Double, 
    val posterPath: String?, 
    val premiered: String
)

object ManualMappingManager {
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private const val MAPPING_FILE = "manual_mappings.json"

    private fun getMappingFile(context: Context): File {
        val dir = File(context.filesDir, "cinehub_config")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MAPPING_FILE)
    }

    fun saveMapping(context: Context, filenameOrId: String, tmdbId: String) {
        val file = getMappingFile(context)
        val currentMap = loadAllMappings(context).toMutableMap()
        currentMap[filenameOrId] = tmdbId
        file.writeText(jsonParser.encodeToString(currentMap))
    }

    fun getMapping(context: Context, filenameOrId: String): String? {
        return loadAllMappings(context)[filenameOrId]
    }

    private fun loadAllMappings(context: Context): Map<String, String> {
        val file = getMappingFile(context)
        if (!file.exists()) return emptyMap()
        return try {
            jsonParser.decodeFromString<Map<String, String>>(file.readText())
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

object MetadataCacheManager {
    val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "cinehub_metadata")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    inline fun <reified T> saveToCache(context: Context, id: String, data: T) {
        try {
            val file = File(getCacheDir(context), "${id.hashCode()}.json")
            file.writeText(jsonParser.encodeToString(data))
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "Failed to write cache for $id", e)
        }
    }

    inline fun <reified T> loadFromCache(context: Context, id: String): T? {
        try {
            val file = File(getCacheDir(context), "${id.hashCode()}.json")
            if (file.exists()) {
                return jsonParser.decodeFromString<T>(file.readText())
            }
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "Failed to read cache for $id", e)
        }
        return null
    }

    fun clearCache(context: Context) {
        getCacheDir(context).deleteRecursively()
    }
}

object CineOnlineScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    const val DEFAULT_TMDB_API_KEY = "cf3287a5765254044c0d6a5afae2b1c4"
    const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"
    const val THUMB_BASE_URL = "https://image.tmdb.org/t/p/w500"
    private const val TVMAZE_BASE_URL = "https://api.tvmaze.com"

    fun getEffectiveApiKey(context: Context? = null): String {
        if (context != null) {
            val prefs = org.koin.core.context.GlobalContext.getOrNull()?.get<xyz.mpv.rex.preferences.BrowserPreferences>()
            val custom = prefs?.customTmdbApiKey?.get()
            if (!custom.isNullOrBlank()) {
                return custom.trim()
            }
        }
        return DEFAULT_TMDB_API_KEY
    }

    fun getScraperProvider(context: Context? = null): String {
        if (context != null) {
            val prefs = org.koin.core.context.GlobalContext.getOrNull()?.get<xyz.mpv.rex.preferences.BrowserPreferences>()
            val provider = prefs?.scraperProvider?.get()
            if (!provider.isNullOrBlank()) return provider
        }
        return "tmdb_and_tvmaze"
    }

    fun isMovieScraperEnabled(context: Context? = null): Boolean {
        if (context != null) {
            val prefs = org.koin.core.context.GlobalContext.getOrNull()?.get<xyz.mpv.rex.preferences.BrowserPreferences>()
            return prefs?.enableMovieScraper?.get() ?: true
        }
        return true
    }

    fun isTvScraperEnabled(context: Context? = null): Boolean {
        if (context != null) {
            val prefs = org.koin.core.context.GlobalContext.getOrNull()?.get<xyz.mpv.rex.preferences.BrowserPreferences>()
            return prefs?.enableTvScraper?.get() ?: true
        }
        return true
    }

    fun cleanMediaFileName(fileName: String): Pair<String, String?> {
        var cleanName = fileName.replace(Regex("(?i)\\.(mp4|mkv|avi|mov|webm|flv|ts)\$"), "")

        // Extended deep regex cleanup
        cleanName = cleanName.replace(Regex("(?i)\\b(1080p|2160p|480p|720p|4k|bluray|web-dl|webrip|hdrip|hevc|x264|x265|aac|dts|remux|extended|director's cut|dual|audio|hindi|english|korean|msubs|esubs|moviesmod|org|army|episode\\s*\\d+|season\\s*\\d+|s\\d+e\\d+)\\b.*"), "")

        cleanName = cleanName.replace(Regex("[\\.\\-_]"), " ")

        val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
        val match = yearRegex.find(cleanName)
        val year = match?.value

        if (match != null) {
            cleanName = cleanName.substring(0, match.range.first)
        }
        
        cleanName = cleanName.replace(Regex("[\\[\\]\\(\\)]"), " ").replace(Regex("\\s+"), " ").trim()

        return Pair(cleanName, year)
    }

    suspend fun executeManualMovieSearch(query: String, context: Context? = null): List<TMDBMovieNode> = withContext(Dispatchers.IO) {
        try {
            val key = getEffectiveApiKey(context)
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/movie?api_key=$key&query=$encodedQuery&language=en-US"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use emptyList<TMDBMovieNode>()
                    val parsed = jsonParser.decodeFromString<TMDBMovieSearchWrapper>(body)
                    return@withContext parsed.results
                }
            }
        } catch (e: Exception) {}
        return@withContext emptyList()
    }

    suspend fun executeManualTvSearch(query: String, context: Context? = null): List<TMDBTvNode> = withContext(Dispatchers.IO) {
        try {
            val key = getEffectiveApiKey(context)
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/tv?api_key=$key&query=$encodedQuery&language=en-US"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use emptyList<TMDBTvNode>()
                    val parsed = jsonParser.decodeFromString<TMDBTvSearchWrapper>(body)
                    return@withContext parsed.results
                }
            }
        } catch (e: Exception) {}
        return@withContext emptyList()
    }

    fun searchOnlineMovieMetadata(fileName: String): OnlineMediaMetadata? {
        return runBlocking {
            val movie = getOrFetchMovie(null, fileName, null, false)
            if (movie != null) {
                OnlineMediaMetadata(
                    title = movie.title,
                    plot = movie.plot,
                    rating = movie.userRating,
                    posterPath = movie.posterPath,
                    premiered = movie.premiered
                )
            } else null
        }
    }

    fun searchOnlineTvMetadata(folderName: String): OnlineMediaMetadata? {
        return runBlocking {
            val tv = getOrFetchTvShow(null, folderName, null, false)
            if (tv != null) {
                OnlineMediaMetadata(
                    title = tv.title,
                    plot = tv.plot,
                    rating = tv.userRating,
                    posterPath = tv.posterPath,
                    premiered = tv.premiered
                )
            } else null
        }
    }

    suspend fun getOrFetchMovie(context: Context?, fileName: String, fallbackTmdbId: String? = null, forceRefresh: Boolean = false): MovieItem? = withContext(Dispatchers.IO) {
        if (!isMovieScraperEnabled(context)) return@withContext null
        var tmdbId = fallbackTmdbId
        val apiKey = getEffectiveApiKey(context)
        
        // 1. Check Permanent Manual Mapping
        if (context != null && tmdbId == null) {
            val mappedId = ManualMappingManager.getMapping(context, fileName)
            if (mappedId != null) tmdbId = mappedId
        }

        val cacheId = tmdbId ?: fileName
        
        // 2. Check JSON Cache
        if (!forceRefresh && context != null) {
            val cached = MetadataCacheManager.loadFromCache<MovieItem>(context, "movie_$cacheId")
            if (cached != null) return@withContext cached
        }

        try {
            val (cleanTitle, year) = cleanMediaFileName(fileName)

            if (tmdbId.isNullOrBlank()) {
                val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                var searchUrl = "$TMDB_BASE_URL/search/movie?api_key=$apiKey&query=$encodedTitle&language=en-US"
                if (year != null) searchUrl += "&primary_release_year=$year"

                val searchReq = Request.Builder().url(searchUrl).build()
                client.newCall(searchReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: return@use
                        val parsed = jsonParser.decodeFromString<TMDBMovieSearchWrapper>(body)
                        tmdbId = parsed.results.firstOrNull()?.id?.toString()
                    }
                }
            }

            if (!tmdbId.isNullOrBlank()) {
                val detailUrl = "$TMDB_BASE_URL/movie/$tmdbId?api_key=$apiKey&append_to_response=credits,images&include_image_language=en,null"
                val detailReq = Request.Builder().url(detailUrl).build()
                
                client.newCall(detailReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: return@use
                        val details = jsonParser.decodeFromString<TMDBMovieDetails>(body)
                        
                        val logoPath = details.images?.logos?.maxByOrNull { it.vote_average }?.file_path?.let { "$IMAGE_BASE_URL$it" }
                        val director = details.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: "Unknown"
                        
                        val actors = details.credits?.cast?.take(15)?.map { cast ->
                            ActorItem(
                                id = cast.id.toString(),
                                name = cast.name,
                                character = cast.character ?: "",
                                thumbUrl = cast.profile_path?.let { "$THUMB_BASE_URL$it" } ?: "https://ui-avatars.com/api/?name=${cast.name}&background=random"
                            )
                        } ?: emptyList()

                        val collection = details.belongs_to_collection?.let {
                            MediaCollection(
                                id = it.id,
                                name = it.name,
                                posterPath = it.poster_path?.let { p -> "$IMAGE_BASE_URL$p" },
                                backdropPath = it.backdrop_path?.let { b -> "$IMAGE_BASE_URL$b" }
                            )
                        }

                        val movieItem = MovieItem(
                            videoFilePath = "",
                            title = details.title,
                            originalTitle = details.title,
                            userRating = details.vote_average,
                            plot = details.overview ?: "No description available.",
                            tagline = details.tagline ?: "",
                            mpaa = "",
                            genre = details.genres.joinToString(", ") { it.name },
                            director = director,
                            premiered = details.release_date ?: "2026",
                            runtime = details.runtime ?: 0,
                            posterPath = details.poster_path?.let { "$IMAGE_BASE_URL$it" },
                            backdropPath = details.backdrop_path?.let { "$IMAGE_BASE_URL$it" },
                            logoPath = logoPath,
                            tmdbId = tmdbId.toString(),
                            imdbId = details.imdb_id ?: "",
                            collection = collection,
                            actors = actors,
                            isMetadataCached = true
                        )
                        
                        if (context != null) {
                            MetadataCacheManager.saveToCache(context, "movie_$cacheId", movieItem)
                        }
                        return@withContext movieItem
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CineOnlineScraper", "Online movie scan failed", e)
        }
        return@withContext null
    }

    suspend fun getOrFetchTvShow(context: Context?, folderName: String, fallbackTmdbId: String? = null, forceRefresh: Boolean = false): TvShowItem? = withContext(Dispatchers.IO) {
        if (!isTvScraperEnabled(context)) return@withContext null
        var tmdbId = fallbackTmdbId
        val provider = getScraperProvider(context)
        val apiKey = getEffectiveApiKey(context)
        
        // 1. Check Permanent Manual Mapping
        if (context != null && tmdbId == null) {
            val mappedId = ManualMappingManager.getMapping(context, folderName)
            if (mappedId != null) tmdbId = mappedId
        }

        val cacheId = tmdbId ?: folderName
        
        // 2. Check JSON Cache
        if (!forceRefresh && context != null) {
            val cached = MetadataCacheManager.loadFromCache<TvShowItem>(context, "tv_$cacheId")
            if (cached != null) return@withContext cached
        }

        try {
            val (cleanTitle, year) = cleanMediaFileName(folderName)
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            
            // Try TMDB first unless user specifically selected TVMaze only
            if (provider != "tvmaze") {
                var searchUrl = "$TMDB_BASE_URL/search/tv?api_key=$apiKey&query=$encodedTitle&language=en-US"
                if (year != null) searchUrl += "&first_air_date_year=$year"

                val searchReq = Request.Builder().url(searchUrl).build()
                client.newCall(searchReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: return@use
                        val parsed = jsonParser.decodeFromString<TMDBTvSearchWrapper>(body)
                        
                        val result = if (tmdbId != null) parsed.results.find { it.id.toString() == tmdbId } ?: parsed.results.firstOrNull() else parsed.results.firstOrNull()
                        
                        if (result != null) {
                            val tvShow = TvShowItem(
                                folderPath = "",
                                title = result.name ?: cleanTitle,
                                plot = result.overview ?: "No description.",
                                userRating = result.vote_average,
                                genre = "Series",
                                premiered = result.first_air_date ?: "2026",
                                studio = "Unknown",
                                posterPath = result.poster_path?.let { "$IMAGE_BASE_URL$it" },
                                backdropPath = result.backdrop_path?.let { "$IMAGE_BASE_URL$it" },
                                tmdbId = result.id.toString(),
                                isMetadataCached = true
                            )
                            if (context != null) MetadataCacheManager.saveToCache(context, "tv_$cacheId", tvShow)
                            return@withContext tvShow
                        }
                    }
                }
            }
            
            // Fallback or primary TVMaze (no API key required)
            val tvMazeUrl = "$TVMAZE_BASE_URL/search/shows?q=$encodedTitle"
            val reqMaze = Request.Builder().url(tvMazeUrl).build()
            client.newCall(reqMaze).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val array = jsonParser.decodeFromString<List<TVMazeSearchWrapper>>(body)
                    val node = array.firstOrNull()?.show
                    if (node != null) {
                        val cleanPlot = node.summary?.replace(Regex("<[^>]*>"), "") ?: "No description."
                        val tvShow = TvShowItem(
                            folderPath = "",
                            title = node.name ?: cleanTitle,
                            plot = cleanPlot,
                            userRating = node.rating?.average ?: 0.0,
                            genre = "Series",
                            premiered = node.premiered ?: "2026",
                            studio = "Network",
                            posterPath = node.image?.original ?: node.image?.medium,
                            tvdbId = node.externals?.thetvdb?.toString() ?: "",
                            isMetadataCached = true
                        )
                        if (context != null) MetadataCacheManager.saveToCache(context, "tv_$cacheId", tvShow)
                        return@withContext tvShow
                    }
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }

    suspend fun fetchArtworkOptions(tmdbId: String, type: String = "movie", context: Context? = null): TMDBImagesResponse? = withContext(Dispatchers.IO) {
        try {
            val apiKey = getEffectiveApiKey(context)
            val url = "$TMDB_BASE_URL/$type/$tmdbId/images?api_key=$apiKey&include_image_language=en,null"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@use null
                    return@withContext jsonParser.decodeFromString<TMDBImagesResponse>(body)
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }

    suspend fun fetchActorDetails(context: Context, personId: String): TMDBPersonDetails? = withContext(Dispatchers.IO) {
        val cached = MetadataCacheManager.loadFromCache<TMDBPersonDetails>(context, "actor_$personId")
        if (cached != null) return@withContext cached

        try {
            val apiKey = getEffectiveApiKey(context)
            val url = "$TMDB_BASE_URL/person/$personId?api_key=$apiKey"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@use null
                    val details = jsonParser.decodeFromString<TMDBPersonDetails>(body)
                    MetadataCacheManager.saveToCache(context, "actor_$personId", details)
                    return@withContext details
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }

    suspend fun fetchTvShowDetails(tmdbId: String, showTitle: String? = null, context: Context? = null): TMDBTvDetails? = withContext(Dispatchers.IO) {
        var resolvedId = tmdbId
        if ((resolvedId.isBlank() || !resolvedId.all { it.isDigit() }) && !showTitle.isNullOrBlank()) {
            val tv = getOrFetchTvShow(context, showTitle)
            if (tv != null && tv.tmdbId.isNotBlank() && tv.tmdbId.all { it.isDigit() }) {
                resolvedId = tv.tmdbId
            }
        }
        if (resolvedId.isBlank() || !resolvedId.all { it.isDigit() }) return@withContext null
        try {
            val apiKey = getEffectiveApiKey(context)
            val url = "$TMDB_BASE_URL/tv/$resolvedId?api_key=$apiKey&language=en-US&append_to_response=credits"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@use null
                    return@withContext jsonParser.decodeFromString<TMDBTvDetails>(body)
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }

    suspend fun fetchTvShowEpisodes(
        context: Context?,
        tmdbId: String,
        seasonNumber: Int = 1,
        showTitle: String? = null
    ): List<EpisodeItem> = withContext(Dispatchers.IO) {
        var resolvedId = tmdbId
        if ((resolvedId.isBlank() || !resolvedId.all { it.isDigit() }) && !showTitle.isNullOrBlank()) {
            val tv = getOrFetchTvShow(context, showTitle)
            if (tv != null && tv.tmdbId.isNotBlank() && tv.tmdbId.all { it.isDigit() }) {
                resolvedId = tv.tmdbId
            }
        }

        val cacheKey = "episodes_${resolvedId.ifBlank { showTitle ?: "unknown" }}_$seasonNumber"
        if (context != null) {
            val cached = MetadataCacheManager.loadFromCache<List<EpisodeItem>>(context, cacheKey)
            if (!cached.isNullOrEmpty()) return@withContext cached
        }

        val resultList = mutableListOf<EpisodeItem>()

        // 1. Try TMDB Season API
        if (resolvedId.isNotBlank() && resolvedId.all { it.isDigit() }) {
            try {
                val apiKey = getEffectiveApiKey(context)
                val url = "$TMDB_BASE_URL/tv/$resolvedId/season/$seasonNumber?api_key=$apiKey&language=en-US"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val seasonObj = jsonParser.decodeFromString<TMDBSeasonResponse>(body)
                        seasonObj.episodes.forEach { ep ->
                            resultList.add(
                                EpisodeItem(
                                    videoFilePath = "stream_tv:$resolvedId:$seasonNumber:${ep.episode_number}",
                                    title = ep.name ?: "Episode ${ep.episode_number}",
                                    season = seasonNumber,
                                    episode = ep.episode_number,
                                    plot = ep.overview ?: "No synopsis available.",
                                    userRating = ep.vote_average,
                                    aired = ep.air_date ?: "",
                                    stillPath = ep.still_path?.let { "$IMAGE_BASE_URL$it" },
                                    sourceType = "online"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Try TVMaze fallback if empty
        if (resultList.isEmpty() && !showTitle.isNullOrBlank()) {
            try {
                val cleanTitle = URLEncoder.encode(showTitle, "UTF-8")
                val url = "$TVMAZE_BASE_URL/singlesearch/shows?q=$cleanTitle&embed=episodes"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        if (body.contains("\"_embedded\":{\"episodes\":[")) {
                            val epSub = body.substringAfter("\"_embedded\":{\"episodes\":[").substringBefore("]}")
                            val arrayJson = "[$epSub]"
                            val mazeEpisodes = jsonParser.decodeFromString<List<TVMazeEpisodeNode>>(arrayJson)
                            mazeEpisodes.filter { it.season == seasonNumber }.forEach { ep ->
                                resultList.add(
                                    EpisodeItem(
                                        videoFilePath = "vidsrc_tv:${tmdbId.ifBlank { showTitle }}:$seasonNumber:${ep.number}",
                                        title = ep.name ?: "Episode ${ep.number}",
                                        season = seasonNumber,
                                        episode = ep.number,
                                        plot = ep.summary?.replace(Regex("<[^>]*>"), "") ?: "No description.",
                                        userRating = 7.5,
                                        aired = ep.airdate ?: "",
                                        stillPath = ep.image?.original ?: ep.image?.medium,
                                        sourceType = "online"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback generator so user always has working episode buttons
        if (resultList.isEmpty()) {
            val titlePrefix = showTitle ?: "Series"
            for (epNum in 1..10) {
                resultList.add(
                    EpisodeItem(
                        videoFilePath = "stream_tv:${tmdbId.ifBlank { "tt14674744" }}:$seasonNumber:$epNum",
                        title = "$titlePrefix - Episode $epNum",
                        season = seasonNumber,
                        episode = epNum,
                        plot = "Episode $epNum of Season $seasonNumber.",
                        userRating = 8.0,
                        aired = "Season $seasonNumber",
                        sourceType = "online"
                    )
                )
            }
        }

        if (context != null && resultList.isNotEmpty()) {
            MetadataCacheManager.saveToCache(context, cacheKey, resultList)
        }

        return@withContext resultList
    }

    suspend fun resolveActiveMedia(
        context: Context?,
        filePath: String,
        mediaTitle: String?,
        durationSeconds: Double = 0.0,
        resolution: String = "",
        videoCodec: String = "",
        audioCodec: String = ""
    ): ActiveMediaResolution = withContext(Dispatchers.IO) {
        val targetName = when {
            !mediaTitle.isNullOrBlank() -> mediaTitle
            filePath.isNotBlank() -> File(filePath).nameWithoutExtension
            else -> "Media File"
        }

        val localFile = if (filePath.isNotBlank() && !filePath.startsWith("http") && !filePath.contains("://")) {
            try { File(filePath).takeIf { it.exists() } } catch (_: Exception) { null }
        } else null

        // 1. If local file exists, check Kodi NFOs and local artwork first!
        if (localFile != null) {
            val parentDir = localFile.parentFile
            val baseName = localFile.nameWithoutExtension

            val specificNfo = File(parentDir, "$baseName.nfo")
            val movieNfo = File(parentDir, "movie.nfo")
            val tvShowNfo = File(parentDir, "tvshow.nfo")

            // Check if specific NFO exists
            if (specificNfo.exists()) {
                val doc = NfoScanner.getXmlDocument(specificNfo)
                val rootName = doc?.documentElement?.nodeName
                if (rootName == "episodedetails") {
                    val parsedEp = NfoScanner.parseEpisodeNfo(specificNfo, localFile)
                    if (parsedEp != null) {
                        val showFolder = if (NfoScanner.isSeasonFolder(parentDir ?: localFile)) {
                            parentDir?.parentFile ?: parentDir ?: localFile
                        } else {
                            parentDir ?: localFile
                        }
                        val showNfoFile = File(showFolder, "tvshow.nfo")
                        val showItem = (if (showNfoFile.exists()) {
                            NfoScanner.parseTvShowNfo(showNfoFile, showFolder)
                        } else null) ?: TvShowItem(
                            folderPath = showFolder.absolutePath,
                            title = showFolder.name,
                            plot = parsedEp.plot,
                            userRating = parsedEp.userRating,
                            genre = "Series",
                            premiered = "2026",
                            studio = "Local",
                            posterPath = NfoScanner.findLocalPosterForVideo(localFile),
                            backdropPath = NfoScanner.resolveArtworkLocalFallback(showFolder, "fanart.jpg")
                        )

                        val allLocalEps = NfoScanner.scanTvShowEpisodes(showFolder)
                        val seasonEps = allLocalEps.filter { it.season == parsedEp.season }
                        val foundSeasons = allLocalEps.map { it.season }.filter { it > 0 }.distinct().sorted()
                        val allSeasons = if (foundSeasons.isEmpty()) listOf(parsedEp.season) else foundSeasons

                        return@withContext ActiveMediaResolution.TvShow(
                            show = showItem,
                            season = parsedEp.season,
                            episode = parsedEp.episode,
                            episodeTitle = parsedEp.title,
                            episodePlot = parsedEp.plot,
                            episodeStill = parsedEp.stillPath ?: showItem.posterPath,
                            episodes = if (seasonEps.isNotEmpty()) seasonEps else allLocalEps,
                            totalSeasons = allSeasons.maxOrNull() ?: 1,
                            allSeasons = allSeasons
                        )
                    }
                } else if (rootName == "movie") {
                    val parsedMovie = NfoScanner.parseMovieNfo(specificNfo, localFile)
                    if (parsedMovie != null) {
                        return@withContext ActiveMediaResolution.Movie(parsedMovie)
                    }
                }
            }

            // Check movie.nfo
            if (movieNfo.exists()) {
                val parsedMovie = NfoScanner.parseMovieNfo(movieNfo, localFile)
                if (parsedMovie != null) {
                    return@withContext ActiveMediaResolution.Movie(parsedMovie)
                }
            }

            // Check if TV structure by folder or filename
            val (sFn, eFn) = NfoScanner.parseSeasonAndEpisodeFromFilename(localFile.name)
            val isTv = tvShowNfo.exists() || sFn != null || NfoScanner.isSeasonFolder(parentDir ?: localFile)
            if (isTv) {
                val showFolder = if (NfoScanner.isSeasonFolder(parentDir ?: localFile)) {
                    parentDir?.parentFile ?: parentDir ?: localFile
                } else {
                    parentDir ?: localFile
                }
                val showNfoFile = File(showFolder, "tvshow.nfo")
                val localShow = if (showNfoFile.exists()) NfoScanner.parseTvShowNfo(showNfoFile, showFolder) else null
                val showTitle = localShow?.title ?: showFolder.name
                val seasonNum = sFn ?: NfoScanner.extractSeasonFromFolder(parentDir?.name ?: "") ?: 1
                val epNum = eFn ?: 1

                val allLocalEps = NfoScanner.scanTvShowEpisodes(showFolder)
                val seasonEps = allLocalEps.filter { it.season == seasonNum }
                val currentEp = allLocalEps.find { it.season == seasonNum && it.episode == epNum }
                    ?: seasonEps.firstOrNull() ?: allLocalEps.firstOrNull()

                val foundSeasons = allLocalEps.map { it.season }.filter { it > 0 }.distinct().sorted()
                val allSeasons = if (foundSeasons.isEmpty()) listOf(seasonNum) else foundSeasons

                val showItem = localShow ?: TvShowItem(
                    folderPath = showFolder.absolutePath,
                    title = showTitle,
                    plot = currentEp?.plot ?: "Local Series",
                    userRating = 0.0,
                    genre = "Series",
                    premiered = "2026",
                    studio = "Local",
                    posterPath = NfoScanner.findLocalPosterForVideo(localFile),
                    backdropPath = NfoScanner.resolveArtworkLocalFallback(showFolder, "fanart.jpg")
                )

                return@withContext ActiveMediaResolution.TvShow(
                    show = showItem,
                    season = seasonNum,
                    episode = epNum,
                    episodeTitle = currentEp?.title ?: "Episode $epNum",
                    episodePlot = currentEp?.plot ?: showItem.plot,
                    episodeStill = currentEp?.stillPath ?: showItem.posterPath,
                    episodes = if (seasonEps.isNotEmpty()) seasonEps else allLocalEps,
                    totalSeasons = allSeasons.maxOrNull() ?: 1,
                    allSeasons = allSeasons
                )
            }
        }

        // 2. Parse title using online scrapers
        val parsed = MediaInfoParser.parse(targetName)

        // Check if identified as TV series
        if (parsed.type.equals("tv", ignoreCase = true) || parsed.season != null || parsed.episode != null) {
            val tvShow = getOrFetchTvShow(context, parsed.title)
            val season = parsed.season ?: 1
            val episodeNumber = parsed.episode ?: 1

            if (tvShow != null) {
                val episodes = fetchTvShowEpisodes(context, tvShow.tmdbId.ifBlank { tvShow.title }, season, tvShow.title)
                val currentEp = episodes.find { it.episode == episodeNumber } ?: episodes.firstOrNull()
                val tvDetails = if (tvShow.tmdbId.isNotBlank() && tvShow.tmdbId.all { it.isDigit() }) {
                    fetchTvShowDetails(tvShow.tmdbId)
                } else null
                val seasonsList = tvDetails?.seasons?.map { it.season_number }?.filter { it > 0 }?.distinct()?.sorted()
                    ?: (1..maxOf(season, tvDetails?.number_of_seasons ?: 1)).toList()

                return@withContext ActiveMediaResolution.TvShow(
                    show = tvShow,
                    season = season,
                    episode = episodeNumber,
                    episodeTitle = currentEp?.title ?: (parsed.episodeTitle ?: "Episode $episodeNumber"),
                    episodePlot = currentEp?.plot ?: tvShow.plot,
                    episodeStill = currentEp?.stillPath ?: tvShow.backdropPath ?: tvShow.posterPath,
                    episodes = episodes,
                    totalSeasons = seasonsList.maxOrNull() ?: 1,
                    allSeasons = seasonsList
                )
            }
        }

        // Check if Movie
        val movie = getOrFetchMovie(context, parsed.title)
        if (movie != null) {
            val localPoster = localFile?.let { NfoScanner.findLocalPosterForVideo(it) }
            val finalMovie = if (movie.posterPath.isNullOrBlank() && localPoster != null) {
                movie.copy(posterPath = localPoster)
            } else movie
            return@withContext ActiveMediaResolution.Movie(finalMovie)
        }

        // Normal Media
        val durMins = (durationSeconds / 60).toInt()
        val formattedDuration = if (durMins > 0) {
            val hours = durMins / 60
            val mins = durMins % 60
            if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        } else ""

        val localPoster = localFile?.let { NfoScanner.findLocalPosterForVideo(it) }

        return@withContext ActiveMediaResolution.Normal(
            title = parsed.title.ifBlank { targetName },
            fileName = if (filePath.isNotBlank()) File(filePath).name else targetName,
            durationFormatted = formattedDuration,
            resolution = resolution,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            posterPath = localPoster
        )
    }
}

sealed class ActiveMediaResolution {
    abstract val posterUrl: String?

    data class Movie(val movie: MovieItem) : ActiveMediaResolution() {
        override val posterUrl: String? get() = movie.posterPath
    }

    data class TvShow(
        val show: TvShowItem,
        val season: Int,
        val episode: Int,
        val episodeTitle: String,
        val episodePlot: String,
        val episodeStill: String?,
        val episodes: List<EpisodeItem>,
        val totalSeasons: Int = 1,
        val allSeasons: List<Int> = (1..maxOf(1, totalSeasons)).toList()
    ) : ActiveMediaResolution() {
        override val posterUrl: String? get() = show.posterPath ?: episodeStill
    }

    data class Normal(
        val title: String,
        val fileName: String,
        val durationFormatted: String = "",
        val resolution: String = "",
        val videoCodec: String = "",
        val audioCodec: String = "",
        val posterPath: String? = null
    ) : ActiveMediaResolution() {
        override val posterUrl: String? get() = posterPath
    }
}
