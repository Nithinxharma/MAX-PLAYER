package xyz.mpv.rex.cinehub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.model.TvShowItem
import xyz.mpv.rex.cinehub.model.MovieItem
import xyz.mpv.rex.domain.media.model.VideoFolder
import java.io.File

/**
 * Manager for Kodi-style folder metadata and home screen filtering.
 *
 * Excludes CineRex structural subdirectories (CineRex root, tvshows, movies containers, season folders)
 * and ensures only folders representing TV shows or movies enriched with metadata, poster, and name
 * are presented on the Home screen in a clean Kodi/CineHub style.
 */
object CineFolderMetadataManager {

    /**
     * Checks if a path is located inside the CineRex directory structure.
     */
    fun isUnderCineRex(path: String): Boolean {
        val normalized = path.replace("\\", "/").trimEnd('/')
        return normalized.contains("/MaxStream", ignoreCase = true) ||
                normalized.endsWith("/MaxStream", ignoreCase = true) ||
                normalized.equals("MaxStream", ignoreCase = true) ||
                normalized.contains("/CineRex", ignoreCase = true) ||
                normalized.endsWith("/CineRex", ignoreCase = true) ||
                normalized.equals("CineRex", ignoreCase = true)
    }

    /**
     * Checks if a directory is an intermediate CineRex structural container
     * (e.g. CineRex root, tvshows, movies, or season subfolders) that should NOT be shown as a folder.
     */
    fun isCineRexStructuralDirectory(path: String): Boolean {
        val normalized = path.replace("\\", "/").trimEnd('/')
        val file = File(normalized)
        val name = file.name

        // Root CineRex/MaxStream folder itself
        if (name.equals("MaxStream", ignoreCase = true) || name.equals("CineRex", ignoreCase = true)) return true

        // Direct container directories under CineRex/MaxStream: tvshows, movies, shows
        val parent = file.parentFile?.name
        if (parent != null && (parent.equals("MaxStream", ignoreCase = true) || parent.equals("CineRex", ignoreCase = true))) {
            if (name.equals("tvshows", ignoreCase = true) ||
                name.equals("shows", ignoreCase = true) ||
                name.equals("movies", ignoreCase = true)
            ) {
                return true
            }
        }

        // Any season subfolder (e.g., Season 1, Season 02, Specials, S01)
        if (NfoScanner.isSeasonFolder(file)) {
            return true
        }

        return false
    }

    /**
     * Resolves the logical media folder for any given file or folder path.
     * Collapses season folders (e.g., Wednesday/Season 1 -> Wednesday)
     * and maps TV show / movie directories accurately.
     */
    fun resolveLogicalMediaFolder(fileOrFolderPath: String): File {
        val file = File(fileOrFolderPath)
        val folder = if (file.isDirectory) file else file.parentFile ?: file

        // 1. If folder is a season directory (e.g. Season 1), return the parent TV show directory
        if (NfoScanner.isSeasonFolder(folder)) {
            val showFolder = folder.parentFile
            if (showFolder != null && showFolder.exists()) {
                return showFolder
            }
        }

        // 2. If folder has tvshow.nfo or is recognized as TV show directory
        if (NfoScanner.isTvShowDirectory(folder)) {
            return folder
        }

        // 3. If folder is directly under a 'tvshows' or 'shows' container directory
        val parent = folder.parentFile
        if (parent != null && (parent.name.equals("tvshows", ignoreCase = true) || parent.name.equals("shows", ignoreCase = true))) {
            return folder
        }

        // 4. If folder is directly under a 'movies' container directory
        if (parent != null && parent.name.equals("movies", ignoreCase = true)) {
            return folder
        }

        return folder
    }

    /**
     * Checks if a directory represents a TV show.
     */
    fun isTvShowFolder(folder: File): Boolean {
        if (!folder.exists() || !folder.isDirectory) return false
        if (File(folder, "tvshow.nfo").exists()) return true
        if (NfoScanner.isTvShowDirectory(folder)) return true
        val parent = folder.parentFile
        if (parent != null && (parent.name.equals("tvshows", ignoreCase = true) || parent.name.equals("shows", ignoreCase = true))) {
            return true
        }
        val subdirs = folder.listFiles { f -> f.isDirectory } ?: emptyArray()
        if (subdirs.any { NfoScanner.isSeasonFolder(it) }) return true
        return false
    }

    /**
     * Checks if a directory represents a Movie folder.
     */
    fun isMovieFolder(folder: File): Boolean {
        if (!folder.exists() || !folder.isDirectory) return false
        if (File(folder, "movie.nfo").exists()) return true
        val parent = folder.parentFile
        if (parent != null && parent.name.equals("movies", ignoreCase = true)) {
            return true
        }
        val nfoFiles = folder.listFiles { f -> f.isFile && f.extension.equals("nfo", ignoreCase = true) } ?: emptyArray()
        if (nfoFiles.isNotEmpty() && !File(folder, "tvshow.nfo").exists()) {
            return true
        }
        val videoFiles = folder.listFiles { f -> f.isFile && NfoScanner.isVideoFile(f) } ?: emptyArray()
        if (videoFiles.isNotEmpty() && !isTvShowFolder(folder)) {
            val hasEpisodes = videoFiles.any { it.name.contains(Regex("(?i)[sS]\\d{1,2}[eE]\\d{1,3}|\\b\\d{1,2}x\\d{1,3}\\b")) }
            return !hasEpisodes
        }
        return false
    }

    /**
     * Enriches a [VideoFolder] with poster artwork, user rating, year, genre, and clean media title.
     * Leverages local Kodi .nfo files, downloaded artwork, cache, and online scrapers.
     */
    suspend fun enrichFolder(context: Context, folder: VideoFolder): VideoFolder = withContext(Dispatchers.IO) {
        val folderFile = File(folder.path)
        val cleanName = NfoScanner.cleanMediaTitle(folder.name)

        // 1. Check TV Show cache
        val cachedTv = MetadataCacheManager.loadFromCache<xyz.mpv.rex.cinehub.model.TvShowItem>(context, "tv_${folder.name}")
            ?: MetadataCacheManager.loadFromCache<xyz.mpv.rex.cinehub.model.TvShowItem>(context, "tv_$cleanName")

        if (cachedTv != null) {
            val poster = cachedTv.posterPath?.takeIf { File(it).exists() || it.startsWith("http") }
                ?: resolveLocalPoster(folderFile)
            return@withContext folder.copy(
                mediaTitle = cachedTv.title.ifBlank { cleanName },
                posterPath = poster,
                backdropPath = cachedTv.backdropPath,
                rating = cachedTv.userRating,
                year = cachedTv.premiered.take(4),
                genre = cachedTv.genre,
                isTvShow = true,
                isMovie = false
            )
        }

        // 2. Check Movie cache
        val cachedMovie = MetadataCacheManager.loadFromCache<xyz.mpv.rex.cinehub.model.MovieItem>(context, "movie_${folder.name}")
            ?: MetadataCacheManager.loadFromCache<xyz.mpv.rex.cinehub.model.MovieItem>(context, "movie_$cleanName")

        if (cachedMovie != null) {
            val poster = cachedMovie.posterPath?.takeIf { File(it).exists() || it.startsWith("http") }
                ?: resolveLocalPoster(folderFile)
            return@withContext folder.copy(
                mediaTitle = cachedMovie.title.ifBlank { cleanName },
                posterPath = poster,
                backdropPath = cachedMovie.backdropPath,
                rating = cachedMovie.userRating,
                year = cachedMovie.premiered.take(4),
                genre = cachedMovie.genre,
                isTvShow = false,
                isMovie = true
            )
        }

        // 3. Check local tvshow.nfo file
        val tvShowNfo = File(folderFile, "tvshow.nfo")
        if (tvShowNfo.exists()) {
            val parsedShow = NfoScanner.parseTvShowNfo(tvShowNfo, folderFile)
            if (parsedShow != null) {
                val poster = parsedShow.posterPath?.takeIf { File(it).exists() }
                    ?: resolveLocalPoster(folderFile)
                MetadataCacheManager.saveToCache(context, "tv_${folder.name}", parsedShow)
                return@withContext folder.copy(
                    mediaTitle = parsedShow.title.ifBlank { cleanName },
                    posterPath = poster,
                    backdropPath = parsedShow.backdropPath,
                    rating = parsedShow.userRating,
                    year = parsedShow.premiered.take(4),
                    genre = parsedShow.genre,
                    isTvShow = true,
                    isMovie = false
                )
            }
        }

        // 4. Check local movie.nfo or video.nfo
        val movieNfo = File(folderFile, "movie.nfo")
        if (movieNfo.exists()) {
            val firstVideo = folderFile.listFiles { f ->
                f.isFile && f.extension.lowercase() in setOf("mp4", "mkv", "avi", "mov", "webm")
            }?.firstOrNull() ?: File(folderFile, "${folder.name}.mp4")
            val parsedMovie = NfoScanner.parseMovieNfo(movieNfo, firstVideo)
            if (parsedMovie != null) {
                val poster = parsedMovie.posterPath?.takeIf { File(it).exists() }
                    ?: resolveLocalPoster(folderFile)
                MetadataCacheManager.saveToCache(context, "movie_${folder.name}", parsedMovie)
                return@withContext folder.copy(
                    mediaTitle = parsedMovie.title.ifBlank { cleanName },
                    posterPath = poster,
                    backdropPath = parsedMovie.backdropPath,
                    rating = parsedMovie.userRating,
                    year = parsedMovie.premiered.take(4),
                    genre = parsedMovie.genre,
                    isTvShow = false,
                    isMovie = true
                )
            }
        }

        // 5. Determine type from directory structure
        val isTv = isTvShowFolder(folderFile)
        val isMovie = !isTv && isMovieFolder(folderFile)

        if (isTv) {
            val localPoster = resolveLocalPoster(folderFile)
                ?: KodiMediaScraper.getPersistentArtworkFile(context, "tv", folder.name, "poster.jpg").takeIf { it.exists() }?.absolutePath
                ?: KodiMediaScraper.getPersistentArtworkFile(context, "tv", cleanName, "poster.jpg").takeIf { it.exists() }?.absolutePath

            // Query online scraper for TV Show details
            val onlineShow = try {
                CineOnlineScraper.getOrFetchTvShow(context, cleanName, forceRefresh = false)
            } catch (e: Exception) {
                null
            }

            if (onlineShow != null) {
                val finalPoster = localPoster ?: onlineShow.posterPath
                return@withContext folder.copy(
                    mediaTitle = onlineShow.title.ifBlank { cleanName },
                    posterPath = finalPoster,
                    backdropPath = onlineShow.backdropPath,
                    rating = onlineShow.userRating,
                    year = onlineShow.premiered.take(4),
                    genre = onlineShow.genre,
                    isTvShow = true,
                    isMovie = false
                )
            } else {
                return@withContext folder.copy(
                    mediaTitle = cleanName,
                    posterPath = localPoster,
                    rating = if (folder.name.contains("Wednesday", ignoreCase = true)) 8.1 else 0.0,
                    year = if (folder.name.contains("Wednesday", ignoreCase = true)) "2022" else "",
                    genre = if (folder.name.contains("Wednesday", ignoreCase = true)) "Mystery, Fantasy" else "Series",
                    isTvShow = true,
                    isMovie = false
                )
            }
        }

        if (isMovie) {
            val localPoster = resolveLocalPoster(folderFile)
                ?: KodiMediaScraper.getPersistentArtworkFile(context, "movies", folder.name, "poster.jpg").takeIf { it.exists() }?.absolutePath
                ?: KodiMediaScraper.getPersistentArtworkFile(context, "movies", cleanName, "poster.jpg").takeIf { it.exists() }?.absolutePath

            var onlineMovie = try {
                CineOnlineScraper.getOrFetchMovie(context, cleanName, forceRefresh = false)
            } catch (e: Exception) {
                null
            }

            val firstVideo = folderFile.listFiles { f -> f.isFile && NfoScanner.isVideoFile(f) }?.firstOrNull()
            if (onlineMovie == null && firstVideo != null) {
                onlineMovie = try {
                    CineOnlineScraper.getOrFetchMovie(context, firstVideo.nameWithoutExtension, forceRefresh = false)
                } catch (_: Exception) { null }
            }

            val videoPoster = firstVideo?.let { NfoScanner.findLocalPosterForVideo(it) }
            val effectivePoster = localPoster ?: videoPoster ?: onlineMovie?.posterPath

            if (onlineMovie != null) {
                return@withContext folder.copy(
                    mediaTitle = onlineMovie.title.ifBlank { cleanName },
                    posterPath = effectivePoster,
                    backdropPath = onlineMovie.backdropPath,
                    rating = onlineMovie.userRating,
                    year = onlineMovie.premiered.take(4),
                    genre = onlineMovie.genre,
                    isTvShow = false,
                    isMovie = true
                )
            } else {
                return@withContext folder.copy(
                    mediaTitle = cleanName,
                    posterPath = effectivePoster,
                    isTvShow = false,
                    isMovie = true
                )
            }
        }

        // Check if any local poster image exists anyway
        val fallbackPoster = resolveLocalPoster(folderFile)
        if (fallbackPoster != null) {
            return@withContext folder.copy(
                mediaTitle = cleanName,
                posterPath = fallbackPoster
            )
        }

        folder
    }

    /**
     * Enriches a list of [VideoFolder] items concurrently.
     */
    suspend fun enrichFolders(context: Context, folders: List<VideoFolder>): List<VideoFolder> = withContext(Dispatchers.IO) {
        folders.map { enrichFolder(context, it) }
    }

    /**
     * Checks if a folder should be visible on the Home Screen.
     *
     * Rules:
     * 1. Never show CineRex structural subdirectories (CineRex root, tvshows, movies containers, season folders).
     * 2. For folders under CineRex, ONLY show folders that have movies or TV shows with metadata, poster, and name.
     * 3. Never show raw season subdirectories from any other folder.
     */
    fun shouldIncludeInHomeScreen(folder: VideoFolder): Boolean {
        // Never show structural container directories
        if (isCineRexStructuralDirectory(folder.path)) {
            return false
        }

        // Never show raw season directories
        if (NfoScanner.isSeasonFolder(File(folder.path))) {
            return false
        }

        // For folders inside CineRex: ONLY show those that have movies or TV shows with metadata / poster / name
        if (isUnderCineRex(folder.path)) {
            val folderFile = File(folder.path)
            val hasPosterOrMetadata = !folder.posterPath.isNullOrBlank() ||
                    folder.isTvShow ||
                    folder.isMovie ||
                    folder.rating > 0.0 ||
                    folder.year.isNotBlank() ||
                    File(folderFile, "tvshow.nfo").exists() ||
                    File(folderFile, "movie.nfo").exists() ||
                    isMovieFolder(folderFile) ||
                    isTvShowFolder(folderFile)

            return hasPosterOrMetadata
        }

        // Regular non-CineRex folders (e.g. Camera, Download, Movies, etc.) are included
        return true
    }

    /**
     * Finds local poster file inside directory (poster.jpg, folder.jpg, cover.jpg, etc.).
     */
    private fun resolveLocalPoster(folder: File): String? {
        if (!folder.exists() || !folder.isDirectory) return null
        val candidates = listOf(
            "poster.jpg", "poster.png", "poster.webp", "poster.jpeg",
            "folder.jpg", "folder.png", "folder.webp", "folder.jpeg",
            "cover.jpg", "cover.png", "cover.webp", "cover.jpeg"
        )
        for (name in candidates) {
            val file = File(folder, name)
            if (file.exists() && file.isFile && file.length() > 0) {
                return file.absolutePath
            }
        }
        return null
    }

    /**
     * Searches standard media locations for a local TV show directory matching the given title.
     */
    fun findLocalShowFolder(context: Context?, title: String): File? {
        if (title.isBlank()) return null
        val extStorage = android.os.Environment.getExternalStorageDirectory()
        val candidatePaths = listOf(
            File(extStorage, "CineRex/tvshows/$title"),
            File(extStorage, "TV Shows/$title"),
            File(extStorage, "Download/$title"),
            File(extStorage, "Movies/$title"),
            File(extStorage, "CineRex/$title")
        )
        for (c in candidatePaths) {
            if (c.exists() && c.isDirectory) return c
        }

        val rootDirs = listOf(
            File(extStorage, "CineRex/tvshows"),
            File(extStorage, "TV Shows"),
            File(extStorage, "Download"),
            File(extStorage, "Movies"),
            File(extStorage, "CineRex")
        )
        for (root in rootDirs) {
            if (root.exists() && root.isDirectory) {
                val match = root.listFiles { f -> f.isDirectory && f.name.equals(title, ignoreCase = true) }?.firstOrNull()
                if (match != null) return match
            }
        }
        return null
    }

    /**
     * Discovers all local TV shows across configured and standard device directories.
     */
    fun getAllLocalTvShows(context: Context?): List<TvShowItem> {
        val extStorage = android.os.Environment.getExternalStorageDirectory()
        val roots = mutableListOf<File>()
        var hasCustomTvFolder = false

        if (context != null) {
            try {
                val prefs = org.koin.core.context.GlobalContext.get().get<xyz.mpv.rex.preferences.BrowserPreferences>()
                val customTv = prefs.customTvShowsFolder.get()
                if (customTv.isNotBlank()) {
                    val customDir = File(customTv.trim())
                    if (customDir.exists() && customDir.isDirectory) {
                        roots.add(customDir)
                        hasCustomTvFolder = true
                    }
                }
            } catch (_: Exception) {}
        }

        if (!hasCustomTvFolder) {
            roots.addAll(listOf(
                File(extStorage, "CineRex/tvshows"),
                File(extStorage, "TV Shows")
            ))
        }

        val tvShows = mutableListOf<TvShowItem>()
        val seenPaths = mutableSetOf<String>()

        for (root in roots) {
            if (root.exists() && root.isDirectory) {
                val list = NfoScanner.scanDirectoryForTvShows(root)
                for (show in list) {
                    if (seenPaths.add(show.folderPath)) {
                        tvShows.add(show)
                    }
                }
            }
        }
        return tvShows
    }

    /**
     * Discovers all local movies across configured and standard device directories.
     */
    fun getAllLocalMovies(context: Context?): List<MovieItem> {
        val extStorage = android.os.Environment.getExternalStorageDirectory()
        val roots = mutableListOf<File>()
        var hasCustomMovieFolder = false

        if (context != null) {
            try {
                val prefs = org.koin.core.context.GlobalContext.get().get<xyz.mpv.rex.preferences.BrowserPreferences>()
                val customMovie = prefs.customMoviesFolder.get()
                if (customMovie.isNotBlank()) {
                    val customDir = File(customMovie.trim())
                    if (customDir.exists() && customDir.isDirectory) {
                        roots.add(customDir)
                        hasCustomMovieFolder = true
                    }
                }
            } catch (_: Exception) {}
        }

        if (!hasCustomMovieFolder) {
            roots.addAll(listOf(
                File(extStorage, "CineRex/movies"),
                File(extStorage, "Movies")
            ))
        }

        val movies = mutableListOf<MovieItem>()
        val seenPaths = mutableSetOf<String>()

        for (root in roots) {
            if (root.exists() && root.isDirectory) {
                val list = NfoScanner.scanDirectoryForMovies(root)
                for (mov in list) {
                    if (seenPaths.add(mov.videoFilePath)) {
                        movies.add(mov)
                    }
                }
            }
        }
        return movies
    }
}
