package xyz.mpv.rex.cinehub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.model.EpisodeItem
import xyz.mpv.rex.cinehub.model.MovieItem
import xyz.mpv.rex.cinehub.model.TvShowItem
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ScrapedLibraryResult(
    val movies: List<MovieItem>,
    val tvShows: List<TvShowItem>,
    val scrapedMoviesCount: Int = 0,
    val scrapedTvShowsCount: Int = 0
)

/**
 * Kodi/XBMC-Style Media Scraper Engine for Movies and TV Shows.
 *
 * Scans local storage, identifies video files, queries online databases (TMDB v3/v4 & TVMaze),
 * matches metadata, downloads high-resolution posters and backdrops,
 * writes standardized Kodi .nfo metadata files, and arranges media into a polished library.
 */
object KodiMediaScraper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Download artwork image from URL to target local file.
     */
    suspend fun downloadArtwork(imageUrl: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        if (imageUrl.isBlank() || (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://"))) {
            return@withContext false
        }
        try {
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val request = Request.Builder().url(imageUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("KodiMediaScraper", "Failed to download artwork: $imageUrl to ${targetFile.name}", e)
        }
        return@withContext false
    }

    /**
     * Get a persistent cache file for media artwork (fallback if media directory is read-only).
     */
    fun getPersistentArtworkFile(context: Context, type: String, id: String, suffix: String): File {
        val dir = File(context.filesDir, "cinehub_artwork/$type")
        if (!dir.exists()) dir.mkdirs()
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "${safeId}_$suffix")
    }

    /**
     * Writes standardized Kodi <movie> .nfo file.
     */
    fun writeKodiMovieNfo(targetNfo: File, movie: MovieItem) {
        try {
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>")
                appendLine("<movie>")
                appendLine("    <title>${escapeXml(movie.title)}</title>")
                appendLine("    <originaltitle>${escapeXml(movie.originalTitle)}</originaltitle>")
                appendLine("    <userrating>${movie.userRating}</userrating>")
                appendLine("    <plot>${escapeXml(movie.plot)}</plot>")
                if (movie.tagline.isNotBlank()) {
                    appendLine("    <tagline>${escapeXml(movie.tagline)}</tagline>")
                }
                if (movie.runtime > 0) {
                    appendLine("    <runtime>${movie.runtime}</runtime>")
                }
                if (movie.premiered.isNotBlank()) {
                    appendLine("    <premiered>${escapeXml(movie.premiered)}</premiered>")
                    appendLine("    <year>${movie.premiered.take(4)}</year>")
                }
                if (movie.genre.isNotBlank()) {
                    appendLine("    <genre>${escapeXml(movie.genre)}</genre>")
                }
                if (movie.director.isNotBlank()) {
                    appendLine("    <director>${escapeXml(movie.director)}</director>")
                }
                if (movie.tmdbId.isNotBlank()) {
                    appendLine("    <uniqueid type=\"tmdb\" default=\"true\">${movie.tmdbId}</uniqueid>")
                }
                if (movie.imdbId.isNotBlank()) {
                    appendLine("    <uniqueid type=\"imdb\">${movie.imdbId}</uniqueid>")
                }
                if (!movie.posterPath.isNullOrBlank()) {
                    appendLine("    <thumb aspect=\"poster\">${escapeXml(movie.posterPath ?: "")}</thumb>")
                }
                if (!movie.backdropPath.isNullOrBlank()) {
                    appendLine("    <fanart>")
                    appendLine("        <thumb>${escapeXml(movie.backdropPath ?: "")}</thumb>")
                    appendLine("    </fanart>")
                }
                appendLine("</movie>")
            }
            targetNfo.writeText(xml)
        } catch (e: Exception) {
            android.util.Log.w("KodiMediaScraper", "Could not write movie NFO to ${targetNfo.name}", e)
        }
    }

    /**
     * Writes standardized Kodi <tvshow> .nfo file.
     */
    fun writeKodiTvShowNfo(targetNfo: File, show: TvShowItem) {
        try {
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>")
                appendLine("<tvshow>")
                appendLine("    <title>${escapeXml(show.title)}</title>")
                appendLine("    <plot>${escapeXml(show.plot)}</plot>")
                appendLine("    <userrating>${show.userRating}</userrating>")
                if (show.premiered.isNotBlank()) {
                    appendLine("    <premiered>${escapeXml(show.premiered)}</premiered>")
                    appendLine("    <year>${show.premiered.take(4)}</year>")
                }
                if (show.genre.isNotBlank()) {
                    appendLine("    <genre>${escapeXml(show.genre)}</genre>")
                }
                if (show.studio.isNotBlank()) {
                    appendLine("    <studio>${escapeXml(show.studio)}</studio>")
                }
                if (show.tmdbId.isNotBlank()) {
                    appendLine("    <uniqueid type=\"tmdb\" default=\"true\">${show.tmdbId}</uniqueid>")
                }
                if (!show.posterPath.isNullOrBlank()) {
                    appendLine("    <thumb aspect=\"poster\">${escapeXml(show.posterPath ?: "")}</thumb>")
                }
                if (!show.backdropPath.isNullOrBlank()) {
                    appendLine("    <fanart>")
                    appendLine("        <thumb>${escapeXml(show.backdropPath ?: "")}</thumb>")
                    appendLine("    </fanart>")
                }
                appendLine("</tvshow>")
            }
            targetNfo.writeText(xml)
        } catch (e: Exception) {
            android.util.Log.w("KodiMediaScraper", "Could not write TV show NFO to ${targetNfo.name}", e)
        }
    }

    /**
     * Writes standardized Kodi <episodedetails> .nfo file.
     */
    fun writeKodiEpisodeNfo(targetNfo: File, ep: EpisodeItem) {
        try {
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>")
                appendLine("<episodedetails>")
                appendLine("    <title>${escapeXml(ep.title)}</title>")
                appendLine("    <season>${ep.season}</season>")
                appendLine("    <episode>${ep.episode}</episode>")
                appendLine("    <plot>${escapeXml(ep.plot)}</plot>")
                if (ep.userRating > 0.0) {
                    appendLine("    <rating>${ep.userRating}</rating>")
                }
                if (ep.aired.isNotBlank()) {
                    appendLine("    <aired>${escapeXml(ep.aired)}</aired>")
                }
                if (!ep.stillPath.isNullOrBlank()) {
                    appendLine("    <thumb>${escapeXml(ep.stillPath ?: "")}</thumb>")
                }
                appendLine("</episodedetails>")
            }
            targetNfo.writeText(xml)
        } catch (e: Exception) {
            android.util.Log.w("KodiMediaScraper", "Could not write episode NFO to ${targetNfo.name}", e)
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Scrapes a single local movie file using Kodi TMDB scraper.
     * Searches TMDB online, downloads poster & backdrop, writes .nfo, and caches metadata.
     */
    suspend fun scrapeMovie(
        context: Context,
        videoFile: File,
        downloadArtworkAndNfo: Boolean = true
    ): MovieItem = withContext(Dispatchers.IO) {
        val parent = videoFile.parentFile ?: videoFile
        val baseName = videoFile.nameWithoutExtension

        // 1. Check if Kodi .nfo already exists locally
        val specificNfo = File(parent, "$baseName.nfo")
        val genericNfo = File(parent, "movie.nfo")
        val existingNfo = if (specificNfo.exists()) specificNfo else if (genericNfo.exists()) genericNfo else null
        val localMovie = if (existingNfo != null) NfoScanner.parseMovieNfo(existingNfo, videoFile) else null

        // 2. Query TMDB online scraper
        val onlineMovie = CineOnlineScraper.getOrFetchMovie(context, videoFile.name, forceRefresh = false)

        val finalTitle = onlineMovie?.title ?: localMovie?.title ?: NfoScanner.cleanMediaTitle(baseName)
        val finalPlot = onlineMovie?.plot ?: localMovie?.plot ?: "Local Movie File"
        val finalRating = onlineMovie?.userRating ?: localMovie?.userRating ?: 0.0
        val finalGenre = onlineMovie?.genre ?: localMovie?.genre ?: "Movie"
        val finalPremiered = onlineMovie?.premiered ?: localMovie?.premiered ?: "2026"
        val finalDirector = onlineMovie?.director ?: localMovie?.director ?: "Unknown"
        val finalRuntime = onlineMovie?.runtime ?: localMovie?.runtime ?: 0
        val finalTmdbId = onlineMovie?.tmdbId ?: localMovie?.tmdbId ?: ""
        val finalImdbId = onlineMovie?.imdbId ?: localMovie?.imdbId ?: ""

        // Local artwork resolution
        var resolvedPoster = NfoScanner.findLocalPosterForVideo(videoFile) ?: onlineMovie?.posterPath ?: localMovie?.posterPath
        var resolvedBackdrop = NfoScanner.resolveArtworkLocalFallback(parent, "fanart.jpg") ?: onlineMovie?.backdropPath ?: localMovie?.backdropPath

        // 3. Download appropriate poster and backdrop if enabled
        if (downloadArtworkAndNfo && onlineMovie != null) {
            if (!onlineMovie.posterPath.isNullOrBlank()) {
                val targetLocalPoster = if (parent.canWrite()) {
                    File(parent, "$baseName-poster.jpg")
                } else {
                    getPersistentArtworkFile(context, "movies", onlineMovie.tmdbId.ifBlank { baseName }, "poster.jpg")
                }
                if (!targetLocalPoster.exists()) {
                    val downloaded = downloadArtwork(onlineMovie.posterPath!!, targetLocalPoster)
                    if (downloaded) {
                        resolvedPoster = targetLocalPoster.absolutePath
                    }
                } else {
                    resolvedPoster = targetLocalPoster.absolutePath
                }
            }

            if (!onlineMovie.backdropPath.isNullOrBlank()) {
                val targetLocalFanart = if (parent.canWrite()) {
                    File(parent, "$baseName-fanart.jpg")
                } else {
                    getPersistentArtworkFile(context, "movies", onlineMovie.tmdbId.ifBlank { baseName }, "fanart.jpg")
                }
                if (!targetLocalFanart.exists()) {
                    val downloaded = downloadArtwork(onlineMovie.backdropPath!!, targetLocalFanart)
                    if (downloaded) {
                        resolvedBackdrop = targetLocalFanart.absolutePath
                    }
                } else {
                    resolvedBackdrop = targetLocalFanart.absolutePath
                }
            }
        }

        val resultItem = MovieItem(
            videoFilePath = videoFile.absolutePath,
            title = finalTitle,
            originalTitle = onlineMovie?.originalTitle ?: finalTitle,
            userRating = finalRating,
            plot = finalPlot,
            tagline = onlineMovie?.tagline ?: localMovie?.tagline ?: "",
            mpaa = onlineMovie?.mpaa ?: localMovie?.mpaa ?: "",
            genre = finalGenre,
            director = finalDirector,
            premiered = finalPremiered,
            runtime = finalRuntime,
            posterPath = resolvedPoster,
            backdropPath = resolvedBackdrop,
            logoPath = onlineMovie?.logoPath ?: localMovie?.logoPath,
            tmdbId = finalTmdbId,
            imdbId = finalImdbId,
            collection = onlineMovie?.collection ?: localMovie?.collection,
            actors = if (!onlineMovie?.actors.isNullOrEmpty()) onlineMovie!!.actors else (localMovie?.actors ?: emptyList()),
            isMetadataCached = true,
            sourceType = "local"
        )

        // 4. Save Kodi .nfo locally if enabled and directory writable
        if (downloadArtworkAndNfo && parent.canWrite() && !specificNfo.exists()) {
            writeKodiMovieNfo(specificNfo, resultItem)
        }

        // Cache metadata in app's cache manager
        MetadataCacheManager.saveToCache(context, "movie_${videoFile.name}", resultItem)

        return@withContext resultItem
    }

    /**
     * Scrapes a local TV show directory using Kodi TMDB/TVMaze scraper.
     * Matches show details, downloads posters and fanarts, discovers all seasons/episodes,
     * scrapes episode titles and still thumbnails from TMDB, and writes .nfo files.
     */
    suspend fun scrapeTvShow(
        context: Context,
        showFolder: File,
        downloadArtworkAndNfo: Boolean = true
    ): TvShowItem = withContext(Dispatchers.IO) {
        val tvShowNfo = File(showFolder, "tvshow.nfo")
        val localShow = if (tvShowNfo.exists()) NfoScanner.parseTvShowNfo(tvShowNfo, showFolder) else null

        // Query TMDB online scraper
        val onlineShow = CineOnlineScraper.getOrFetchTvShow(context, showFolder.name, forceRefresh = false)

        val finalTitle = onlineShow?.title ?: localShow?.title ?: showFolder.name
        val finalPlot = onlineShow?.plot ?: localShow?.plot ?: "Local TV Series"
        val finalRating = onlineShow?.userRating ?: localShow?.userRating ?: 0.0
        val finalGenre = onlineShow?.genre ?: localShow?.genre ?: "Series"
        val finalPremiered = onlineShow?.premiered ?: localShow?.premiered ?: "2026"
        val finalStudio = onlineShow?.studio ?: localShow?.studio ?: "Local"
        val finalTmdbId = onlineShow?.tmdbId ?: localShow?.tmdbId ?: ""

        // Local artwork resolution
        var resolvedPoster = NfoScanner.resolveArtworkLocalFallback(showFolder, "poster.jpg")
            ?: NfoScanner.resolveArtworkLocalFallback(showFolder, "folder.jpg")
            ?: onlineShow?.posterPath ?: localShow?.posterPath
        var resolvedBackdrop = NfoScanner.resolveArtworkLocalFallback(showFolder, "fanart.jpg")
            ?: onlineShow?.backdropPath ?: localShow?.backdropPath

        // Download artwork if enabled
        if (downloadArtworkAndNfo && onlineShow != null) {
            if (!onlineShow.posterPath.isNullOrBlank()) {
                val targetLocalPoster = if (showFolder.canWrite()) {
                    File(showFolder, "poster.jpg")
                } else {
                    getPersistentArtworkFile(context, "tv", onlineShow.tmdbId.ifBlank { showFolder.name }, "poster.jpg")
                }
                if (!targetLocalPoster.exists()) {
                    val downloaded = downloadArtwork(onlineShow.posterPath!!, targetLocalPoster)
                    if (downloaded) {
                        resolvedPoster = targetLocalPoster.absolutePath
                    }
                } else {
                    resolvedPoster = targetLocalPoster.absolutePath
                }
            }

            if (!onlineShow.backdropPath.isNullOrBlank()) {
                val targetLocalFanart = if (showFolder.canWrite()) {
                    File(showFolder, "fanart.jpg")
                } else {
                    getPersistentArtworkFile(context, "tv", onlineShow.tmdbId.ifBlank { showFolder.name }, "fanart.jpg")
                }
                if (!targetLocalFanart.exists()) {
                    val downloaded = downloadArtwork(onlineShow.backdropPath!!, targetLocalFanart)
                    if (downloaded) {
                        resolvedBackdrop = targetLocalFanart.absolutePath
                    }
                } else {
                    resolvedBackdrop = targetLocalFanart.absolutePath
                }
            }
        }

        val tvShowItem = TvShowItem(
            folderPath = showFolder.absolutePath,
            title = finalTitle,
            plot = finalPlot,
            userRating = finalRating,
            genre = finalGenre,
            premiered = finalPremiered,
            studio = finalStudio,
            posterPath = resolvedPoster,
            backdropPath = resolvedBackdrop,
            logoPath = onlineShow?.logoPath ?: localShow?.logoPath,
            tmdbId = finalTmdbId,
            actors = if (!onlineShow?.actors.isNullOrEmpty()) onlineShow!!.actors else (localShow?.actors ?: emptyList()),
            isMetadataCached = true,
            sourceType = "local"
        )

        // Save Kodi tvshow.nfo if enabled
        if (downloadArtworkAndNfo && showFolder.canWrite() && !tvShowNfo.exists()) {
            writeKodiTvShowNfo(tvShowNfo, tvShowItem)
        }

        // Cache TV show metadata
        MetadataCacheManager.saveToCache(context, "tv_${showFolder.name}", tvShowItem)

        // Now arrange local episodes & scrape episode metadata online
        val localEpisodes = NfoScanner.scanTvShowEpisodes(showFolder)
        val seasonsPresent = localEpisodes.map { it.season }.filter { it > 0 }.distinct()

        for (s in seasonsPresent) {
            val onlineEpisodes = if (finalTmdbId.isNotBlank()) {
                CineOnlineScraper.fetchTvShowEpisodes(context, finalTmdbId, s, finalTitle)
            } else emptyList()

            val seasonLocalEps = localEpisodes.filter { it.season == s }
            for (localEp in seasonLocalEps) {
                val matched = onlineEpisodes.find { it.episode == localEp.episode }
                if (matched != null) {
                    val epFile = File(localEp.videoFilePath)
                    val epParent = epFile.parentFile ?: showFolder
                    val epBase = epFile.nameWithoutExtension

                    // Update episode title, plot, still
                    localEp.title = matched.title
                    localEp.plot = matched.plot
                    localEp.userRating = matched.userRating
                    localEp.aired = matched.aired
                    localEp.stillPath = matched.stillPath ?: localEp.stillPath

                    // Download episode still if enabled
                    if (downloadArtworkAndNfo && !matched.stillPath.isNullOrBlank()) {
                        val stillTarget = if (epParent.canWrite()) {
                            File(epParent, "$epBase-thumb.jpg")
                        } else {
                            getPersistentArtworkFile(context, "episodes", "${finalTmdbId}_s${s}e${localEp.episode}", "thumb.jpg")
                        }
                        if (!stillTarget.exists()) {
                            downloadArtwork(matched.stillPath!!, stillTarget)
                        }
                    }

                    // Save episode .nfo if enabled
                    val epNfo = File(epParent, "$epBase.nfo")
                    if (downloadArtworkAndNfo && epParent.canWrite() && !epNfo.exists()) {
                        writeKodiEpisodeNfo(epNfo, localEp)
                    }
                }
            }
        }

        return@withContext tvShowItem
    }

    /**
     * Scans and scrapes a whole directory for both Movies and TV Shows.
     * Arranges files, queries online metadata from TMDB, downloads posters, and generates NFOs.
     */
    suspend fun scrapeDirectory(
        context: Context,
        directory: File,
        downloadArtworkAndNfo: Boolean = true,
        onProgress: ((current: Int, total: Int, currentItemName: String) -> Unit)? = null
    ): ScrapedLibraryResult = withContext(Dispatchers.IO) {
        val moviesResult = mutableListOf<MovieItem>()
        val tvShowsResult = mutableListOf<TvShowItem>()

        if (!directory.exists() || !directory.isDirectory) {
            return@withContext ScrapedLibraryResult(emptyList(), emptyList())
        }

        // 1. Discover all candidate video files and TV show directories
        val tvShowFolders = mutableListOf<File>()
        val movieFiles = mutableListOf<File>()

        fun inspect(dir: File) {
            if (NfoScanner.isTvShowDirectory(dir)) {
                tvShowFolders.add(dir)
                return
            }
            if (NfoScanner.isSeasonFolder(dir)) {
                return
            }
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    inspect(child)
                } else if (NfoScanner.isVideoFile(child)) {
                    // Check if episode
                    val (s, e) = NfoScanner.parseSeasonAndEpisodeFromFilename(child.name)
                    if (s == null && e == null) {
                        movieFiles.add(child)
                    }
                }
            }
        }

        inspect(directory)

        val totalItems = movieFiles.size + tvShowFolders.size
        var currentIndex = 0

        // 2. Scrape movies
        for (movieFile in movieFiles) {
            currentIndex++
            onProgress?.invoke(currentIndex, totalItems, movieFile.nameWithoutExtension)
            val scrapedMovie = scrapeMovie(context, movieFile, downloadArtworkAndNfo)
            moviesResult.add(scrapedMovie)
        }

        // 3. Scrape TV shows
        for (showFolder in tvShowFolders) {
            currentIndex++
            onProgress?.invoke(currentIndex, totalItems, showFolder.name)
            val scrapedTv = scrapeTvShow(context, showFolder, downloadArtworkAndNfo)
            tvShowsResult.add(scrapedTv)
        }

        return@withContext ScrapedLibraryResult(
            movies = moviesResult,
            tvShows = tvShowsResult,
            scrapedMoviesCount = moviesResult.size,
            scrapedTvShowsCount = tvShowsResult.size
        )
    }
}
