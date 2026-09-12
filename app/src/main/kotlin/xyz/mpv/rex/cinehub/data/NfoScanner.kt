package xyz.mpv.rex.cinehub.data

import android.util.Log
import xyz.mpv.rex.cinehub.model.MovieItem
import xyz.mpv.rex.cinehub.model.TvShowItem
import xyz.mpv.rex.cinehub.model.EpisodeItem
import xyz.mpv.rex.cinehub.model.ActorItem
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-performance Kodi / XBMC v17+ / v18+ / v19+ local media scanner and NFO parser.
 * Accurately extracts TMDB, IMDB, TVDB, TVMaze IDs, poster URLs, fanart, actors,
 * and recursively scans TV seasons and episodes across all folder structures.
 */
object NfoScanner {

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "m4v", "wmv", "iso")

    fun isVideoFile(file: File): Boolean {
        return file.isFile && VIDEO_EXTENSIONS.contains(file.extension.lowercase())
    }

    /**
     * Checks if a directory represents a TV show according to Kodi/XBMC conventions:
     * - Contains tvshow.nfo
     * - Or contains Season subdirectories (e.g. "Season 1", "Season 01", "S1", "Specials")
     * - Or contains video files with SxxExx episode patterns
     */
    fun isTvShowDirectory(directory: File): Boolean {
        if (!directory.exists() || !directory.isDirectory) return false
        if (File(directory, "tvshow.nfo").exists()) return true

        val children = directory.listFiles() ?: return false
        val hasSeasonFolder = children.any { child ->
            child.isDirectory && child.name.matches(Regex("(?i)^(season\\s*\\d+|s\\d+|specials|series\\s*\\d+).*"))
        }
        if (hasSeasonFolder) return true

        val hasEpisodeFiles = children.any { child ->
            isVideoFile(child) && child.name.contains(Regex("(?i)[sS]\\d{1,2}[eE]\\d{1,3}|\\b\\d{1,2}x\\d{1,3}\\b"))
        }
        return hasEpisodeFiles
    }

    /**
     * Checks if a directory is a Season subfolder inside a TV show (e.g. "Season 1", "Season 02", "Specials").
     */
    fun isSeasonFolder(directory: File): Boolean {
        return directory.isDirectory && directory.name.matches(Regex("(?i)^(season\\s*\\d+|s\\d+|specials|series\\s*\\d+).*"))
    }

    /**
     * Scans a root directory for all movie files and parses their Kodi NFOs and posters.
     */
    fun scanDirectoryForMovies(directory: File): List<MovieItem> {
        val movies = mutableListOf<MovieItem>()
        if (!directory.exists() || !directory.isDirectory) return movies

        // If this directory is a TV show or Season folder, skip scanning as movies
        if (isTvShowDirectory(directory) || isSeasonFolder(directory)) {
            return movies
        }

        val files = directory.listFiles() ?: return movies

        // Process files in current directory
        for (file in files) {
            if (isVideoFile(file)) {
                // Check if this video is an episode (has SxxExx in filename) - if so, it's TV, not movie
                if (file.name.contains(Regex("(?i)[sS]\\d{1,2}[eE]\\d{1,3}|\\b\\d{1,2}x\\d{1,3}\\b"))) {
                    continue
                }

                val specificNfo = File(directory, "${file.nameWithoutExtension}.nfo")
                val genericNfo = File(directory, "movie.nfo")
                val targetNfo = if (specificNfo.exists()) specificNfo else if (genericNfo.exists()) genericNfo else null

                val parsedMovie = if (targetNfo != null) parseMovieNfo(targetNfo, file) else null

                if (parsedMovie != null) {
                    movies.add(parsedMovie)
                } else {
                    // Fallback to title and local artwork
                    val localPoster = findLocalPosterForVideo(file)
                    val localFanart = resolveArtworkLocalFallback(directory, "fanart.jpg")
                        ?: resolveArtworkLocalFallback(directory, "${file.nameWithoutExtension}-fanart.jpg")
                    movies.add(
                        MovieItem(
                            videoFilePath = file.absolutePath,
                            title = cleanMediaTitle(file.nameWithoutExtension),
                            originalTitle = "",
                            userRating = 0.0,
                            plot = "Local Movie File",
                            mpaa = "",
                            genre = "Movie",
                            director = "Unknown",
                            premiered = "2026",
                            posterPath = localPoster,
                            backdropPath = localFanart,
                            isMetadataCached = false,
                            sourceType = "local"
                        )
                    )
                }
            } else if (file.isDirectory) {
                // Don't recurse into TV show folders as movies
                if (!isTvShowDirectory(file) && !isSeasonFolder(file)) {
                    movies.addAll(scanDirectoryForMovies(file))
                }
            }
        }
        return movies
    }

    /**
     * Scans for TV show folders recursively according to Kodi/XBMC structure.
     */
    fun scanDirectoryForTvShows(directory: File): List<TvShowItem> {
        val tvShows = mutableListOf<TvShowItem>()
        if (!directory.exists() || !directory.isDirectory) return tvShows

        if (isTvShowDirectory(directory)) {
            val tvShowNfo = File(directory, "tvshow.nfo")
            val parsed = if (tvShowNfo.exists()) parseTvShowNfo(tvShowNfo, directory) else null

            if (parsed != null) {
                tvShows.add(parsed)
            } else {
                val localPoster = resolveArtworkLocalFallback(directory, "poster.jpg")
                    ?: resolveArtworkLocalFallback(directory, "folder.jpg")
                    ?: resolveArtworkLocalFallback(directory, "cover.jpg")
                val localFanart = resolveArtworkLocalFallback(directory, "fanart.jpg")
                    ?: resolveArtworkLocalFallback(directory, "backdrop.jpg")

                tvShows.add(
                    TvShowItem(
                        folderPath = directory.absolutePath,
                        title = directory.name,
                        plot = "Local TV Series",
                        userRating = 0.0,
                        genre = "Series",
                        premiered = "2026",
                        studio = "Local",
                        posterPath = localPoster,
                        backdropPath = localFanart,
                        isMetadataCached = false,
                        sourceType = "local"
                    )
                )
            }
            // Do not recurse into Season folders inside this TV show directory as separate shows
            return tvShows
        }

        // Otherwise recurse into subdirectories
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                tvShows.addAll(scanDirectoryForTvShows(file))
            }
        }
        return tvShows
    }

    /**
     * Recursively scans ALL seasons and episodes inside a TV show folder.
     * Accurately parses season folders, SxxExx filenames, and Kodi <episodedetails> NFOs.
     */
    fun scanTvShowEpisodes(showFolder: File): List<EpisodeItem> {
        val episodes = mutableListOf<EpisodeItem>()
        if (!showFolder.exists()) return episodes

        val targetDir = if (showFolder.isDirectory) showFolder else showFolder.parentFile ?: return episodes

        // Collect all video files in show folder and all its subdirectories (Season 1, Season 2, etc.)
        val allVideoFiles = mutableListOf<File>()
        fun collectVideos(dir: File) {
            dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { child ->
                if (isVideoFile(child)) {
                    allVideoFiles.add(child)
                } else if (child.isDirectory) {
                    collectVideos(child)
                }
            }
        }
        collectVideos(targetDir)
        if (allVideoFiles.isEmpty() && isVideoFile(showFolder)) {
            allVideoFiles.add(showFolder)
        }

        val seasonCounters = mutableMapOf<Int, Int>()

        for (videoFile in allVideoFiles) {
            val nfoFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.nfo")
            var parsedEpisode: EpisodeItem? = null

            if (nfoFile.exists()) {
                parsedEpisode = parseEpisodeNfo(nfoFile, videoFile)
            }

            if (parsedEpisode != null) {
                // If NFO had missing/default season or episode, extract from filename or parent folder
                var finalSeason = parsedEpisode.season
                var finalEpisode = parsedEpisode.episode
                if (finalSeason <= 0 || finalEpisode <= 0) {
                    val (sFromFilename, eFromFilename) = parseSeasonAndEpisodeFromFilename(videoFile.name)
                    if (finalSeason <= 0) {
                        finalSeason = sFromFilename ?: extractSeasonFromFolder(videoFile.parentFile?.name ?: "") ?: 1
                    }
                    if (finalEpisode <= 0) {
                        finalEpisode = eFromFilename ?: 1
                    }
                }
                episodes.add(
                    parsedEpisode.copy(
                        season = finalSeason,
                        episode = finalEpisode,
                        stillPath = parsedEpisode.stillPath ?: findLocalStillForEpisode(videoFile)
                    )
                )
            } else {
                // No NFO exists: extract Season and Episode using Kodi regex
                val (extractedSeason, extractedEp) = parseSeasonAndEpisodeFromFilename(videoFile.name)
                val finalSeason = extractedSeason ?: extractSeasonFromFolder(videoFile.parentFile?.name ?: "") ?: 1
                val currentSeasonCount = (seasonCounters[finalSeason] ?: 0) + 1
                val finalEpisode = extractedEp ?: currentSeasonCount
                seasonCounters[finalSeason] = maxOf(currentSeasonCount, finalEpisode)

                val showName = targetDir.name
                val rawClean = cleanEpisodeTitle(videoFile.nameWithoutExtension, showName)
                val localStill = findLocalStillForEpisode(videoFile)

                val episodeTitle = if (rawClean.isNotBlank() && !rawClean.equals(showName, ignoreCase = true)) {
                    if (rawClean.startsWith("Episode ", ignoreCase = true)) rawClean else "Episode $finalEpisode: $rawClean"
                } else {
                    "Episode $finalEpisode"
                }

                episodes.add(
                    EpisodeItem(
                        videoFilePath = videoFile.absolutePath,
                        title = episodeTitle,
                        season = finalSeason,
                        episode = finalEpisode,
                        plot = "Local Media File.",
                        userRating = 0.0,
                        aired = "",
                        stillPath = localStill,
                        sourceType = "local"
                    )
                )
            }
        }

        // Deduplicate by video file path and sort by season ascending, then episode ascending
        return episodes.distinctBy { it.videoFilePath }
            .sortedWith(compareBy({ it.season }, { it.episode }))
    }

    /**
     * Parses a Kodi / XBMC <movie> NFO file into a MovieItem.
     */
    fun parseMovieNfo(nfoFile: File, videoFile: File): MovieItem? {
        return try {
            val doc = getXmlDocument(nfoFile) ?: return null
            if (doc.documentElement.nodeName != "movie") return null

            val root = doc.documentElement
            val title = getTagText(root, "title").ifBlank { cleanMediaTitle(videoFile.nameWithoutExtension) }
            val uniqueIds = extractUniqueIds(root)

            val tmdbId = uniqueIds["tmdb"] ?: getTagText(root, "tmdbid").ifBlank { getTagText(root, "id") }
            val imdbId = uniqueIds["imdb"] ?: getTagText(root, "imdbid")

            val poster = resolvePosterWithFallback(nfoFile, root) ?: findLocalPosterForVideo(videoFile)
            val backdrop = resolveFanartWithFallback(nfoFile, root)
                ?: resolveArtworkLocalFallback(nfoFile.parentFile, "fanart.jpg")

            val allGenres = getAllTagTexts(root, "genre").joinToString(", ").ifBlank {
                getTagText(root, "genre").ifBlank { "Movie" }
            }

            val runtimeStr = getTagText(root, "runtime")
            val runtimeMin = runtimeStr.toIntOrNull() ?: 0

            MovieItem(
                videoFilePath = videoFile.absolutePath,
                title = title,
                originalTitle = getTagText(root, "originaltitle"),
                userRating = getTagText(root, "userrating").toDoubleOrNull() ?: 0.0,
                plot = getTagText(root, "plot").ifBlank { getTagText(root, "outline").ifBlank { "No description available." } },
                tagline = getTagText(root, "tagline"),
                mpaa = getTagText(root, "mpaa"),
                genre = allGenres,
                director = getTagText(root, "director").ifBlank { "Unknown" },
                premiered = getTagText(root, "premiered").ifBlank { getTagText(root, "year").ifBlank { "2026" } },
                runtime = runtimeMin,
                tmdbId = tmdbId,
                imdbId = imdbId,
                posterPath = poster,
                backdropPath = backdrop,
                actors = parseActorsFromNfo(doc),
                isMetadataCached = true,
                sourceType = "local"
            )
        } catch (e: Exception) {
            Log.e("CineHubScanner", "Error processing movie XML: ${nfoFile.name}", e)
            null
        }
    }

    /**
     * Parses a Kodi / XBMC <tvshow> NFO file into a TvShowItem.
     */
    fun parseTvShowNfo(nfoFile: File, folder: File): TvShowItem? {
        return try {
            val doc = getXmlDocument(nfoFile) ?: return null
            if (doc.documentElement.nodeName != "tvshow") return null

            val root = doc.documentElement
            val title = getTagText(root, "title").ifBlank { getTagText(root, "showtitle").ifBlank { folder.name } }
            val uniqueIds = extractUniqueIds(root)

            val tmdbId = uniqueIds["tmdb"] ?: getTagText(root, "tmdbid")
            val tvdbId = uniqueIds["tvdb"] ?: getTagText(root, "tvdbid").ifBlank { getTagText(root, "id") }
            val tvmazeId = uniqueIds["tvmaze"]

            val poster = resolvePosterWithFallback(nfoFile, root)
                ?: resolveArtworkLocalFallback(folder, "poster.jpg")
                ?: resolveArtworkLocalFallback(folder, "folder.jpg")

            val backdrop = resolveFanartWithFallback(nfoFile, root)
                ?: resolveArtworkLocalFallback(folder, "fanart.jpg")
                ?: resolveArtworkLocalFallback(folder, "backdrop.jpg")

            val allGenres = getAllTagTexts(root, "genre").joinToString(", ").ifBlank {
                getTagText(root, "genre").ifBlank { "Series" }
            }

            TvShowItem(
                folderPath = folder.absolutePath,
                title = title,
                plot = getTagText(root, "plot").ifBlank { "No description available." },
                userRating = getTagText(root, "userrating").toDoubleOrNull() ?: 0.0,
                genre = allGenres,
                premiered = getTagText(root, "premiered").ifBlank { getTagText(root, "year").ifBlank { "2026" } },
                studio = getTagText(root, "studio").ifBlank { "Network" },
                tmdbId = tmdbId,
                tvdbId = tvdbId,
                posterPath = poster,
                backdropPath = backdrop,
                actors = parseActorsFromNfo(doc),
                isMetadataCached = true,
                sourceType = "local"
            )
        } catch (e: Exception) {
            Log.e("CineHubScanner", "Error processing tvshow XML: ${nfoFile.name}", e)
            null
        }
    }

    /**
     * Parses a Kodi / XBMC <episodedetails> NFO file into an EpisodeItem.
     */
    fun parseEpisodeNfo(nfoFile: File, videoFile: File): EpisodeItem? {
        return try {
            val doc = getXmlDocument(nfoFile) ?: return null
            if (doc.documentElement.nodeName != "episodedetails") return null

            val root = doc.documentElement
            val title = getTagText(root, "title").ifBlank { cleanEpisodeTitle(videoFile.nameWithoutExtension) }

            val season = getTagText(root, "season").toIntOrNull() ?: 0
            val episode = getTagText(root, "episode").toIntOrNull() ?: 0

            val still = resolveEpisodeStill(nfoFile, root, videoFile)

            EpisodeItem(
                videoFilePath = videoFile.absolutePath,
                title = title,
                season = season,
                episode = episode,
                plot = getTagText(root, "plot").ifBlank { "Local Episode File." },
                userRating = getTagText(root, "userrating").toDoubleOrNull() ?: 0.0,
                aired = getTagText(root, "aired").ifBlank { getTagText(root, "premiered") },
                stillPath = still,
                sourceType = "local"
            )
        } catch (e: Exception) {
            Log.e("CineHubScanner", "Error parsing episode NFO: ${nfoFile.name}", e)
            null
        }
    }

    /**
     * Extracts unique IDs from <uniqueid type="..."> tags or <episodeguide> JSON.
     */
    private fun extractUniqueIds(element: Element): Map<String, String> {
        val ids = mutableMapOf<String, String>()
        val uniqueNodes = element.getElementsByTagName("uniqueid")
        for (i in 0 until uniqueNodes.length) {
            val node = uniqueNodes.item(i)
            if (node != null && node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                val type = elem.getAttribute("type").lowercase().trim()
                val value = elem.textContent?.trim() ?: ""
                if (type.isNotBlank() && value.isNotBlank()) {
                    ids[type] = value
                }
            }
        }

        // Also check <episodeguide> tag (e.g. {"tvmaze": "53647", "tvdb": "397060", "imdb": "tt13443470"})
        val guideText = getTagText(element, "episodeguide")
        if (guideText.contains("{") && guideText.contains("}")) {
            val regex = Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"([^\"]+)\"")
            regex.findAll(guideText).forEach { match ->
                val k = match.groupValues[1].lowercase()
                val v = match.groupValues[2]
                if (v != "None" && v.isNotBlank() && !ids.containsKey(k)) {
                    ids[k] = v
                }
            }
        }

        return ids
    }

    /**
     * Resolves poster URL from XML <thumb aspect="poster"> or local artwork fallbacks.
     */
    private fun resolvePosterWithFallback(nfoFile: File, rootElement: Element): String? {
        val baseName = nfoFile.nameWithoutExtension
        val parentDir = nfoFile.parentFile

        val localCheck = resolveArtworkLocalFallback(parentDir, "$baseName-poster.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "$baseName.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "poster.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "folder.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "cover.jpg")

        if (localCheck != null) return localCheck

        val thumbList = rootElement.getElementsByTagName("thumb")
        for (i in 0 until thumbList.length) {
            val thumbNode = thumbList.item(i)
            if (thumbNode != null && thumbNode.nodeType == Node.ELEMENT_NODE) {
                val thumbElement = thumbNode as Element
                val aspect = thumbElement.getAttribute("aspect").lowercase().trim()
                if (aspect == "poster" || aspect.isBlank()) {
                    val url = thumbElement.textContent?.trim() ?: ""
                    if (url.startsWith("http")) return url
                }
            }
        }
        return null
    }

    /**
     * Resolves backdrop/fanart URL from <fanart><thumb> or local fanart.jpg.
     */
    private fun resolveFanartWithFallback(nfoFile: File, rootElement: Element): String? {
        val parentDir = nfoFile.parentFile
        val localFanart = resolveArtworkLocalFallback(parentDir, "fanart.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "backdrop.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "${nfoFile.nameWithoutExtension}-fanart.jpg")

        if (localFanart != null) return localFanart

        val fanartNodes = rootElement.getElementsByTagName("fanart")
        if (fanartNodes.length > 0) {
            val fanartElement = fanartNodes.item(0) as? Element
            val thumbs = fanartElement?.getElementsByTagName("thumb")
            if (thumbs != null && thumbs.length > 0) {
                val url = thumbs.item(0)?.textContent?.trim() ?: ""
                if (url.startsWith("http")) return url
            }
        }
        return null
    }

    /**
     * Resolves episode still image from XML <thumb aspect="thumb"> or local thumbnail.
     */
    private fun resolveEpisodeStill(nfoFile: File, rootElement: Element, videoFile: File): String? {
        val localStill = findLocalStillForEpisode(videoFile)
        if (localStill != null) return localStill

        val thumbList = rootElement.getElementsByTagName("thumb")
        for (i in 0 until thumbList.length) {
            val thumbNode = thumbList.item(i)
            if (thumbNode != null && thumbNode.nodeType == Node.ELEMENT_NODE) {
                val thumbElement = thumbNode as Element
                val url = thumbElement.textContent?.trim() ?: ""
                if (url.startsWith("http")) return url
            }
        }
        return null
    }

    /**
     * Finds local episode still image file.
     */
    fun findLocalStillForEpisode(videoFile: File): String? {
        val parent = videoFile.parentFile ?: return null
        val base = videoFile.nameWithoutExtension
        return resolveArtworkLocalFallback(parent, "$base-thumb.jpg")
            ?: resolveArtworkLocalFallback(parent, "$base-thumb.png")
            ?: resolveArtworkLocalFallback(parent, "$base.jpg")
            ?: resolveArtworkLocalFallback(parent, "$base.png")
    }

    /**
     * Finds local poster file for a movie or TV show video file.
     */
    fun findLocalPosterForVideo(videoFile: File): String? {
        val parent = videoFile.parentFile ?: return null
        val base = videoFile.nameWithoutExtension

        return resolveArtworkLocalFallback(parent, "$base-poster.jpg")
            ?: resolveArtworkLocalFallback(parent, "$base.jpg")
            ?: resolveArtworkLocalFallback(parent, "poster.jpg")
            ?: resolveArtworkLocalFallback(parent, "folder.jpg")
            ?: resolveArtworkLocalFallback(parent, "cover.jpg")
            // Check grand-parent if video is inside a Season X subfolder
            ?: (if (isSeasonFolder(parent)) {
                parent.parentFile?.let { gp ->
                    resolveArtworkLocalFallback(gp, "poster.jpg")
                        ?: resolveArtworkLocalFallback(gp, "folder.jpg")
                }
            } else null)
    }

    fun parseActorsFromNfo(doc: Document): List<ActorItem> {
        val actorsList = mutableListOf<ActorItem>()
        try {
            val actorNodes = doc.getElementsByTagName("actor")
            for (i in 0 until actorNodes.length) {
                val node = actorNodes.item(i)
                if (node != null && node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val name = getTagText(element, "name")
                    val thumb = getTagText(element, "thumb")
                    val role = getTagText(element, "role")
                    if (name.isNotBlank()) {
                        actorsList.add(ActorItem(name = name, thumbUrl = thumb, character = role))
                    }
                }
            }
        } catch (_: Exception) {}
        return actorsList
    }

    fun parseSeasonAndEpisodeFromFilename(fileName: String): Pair<Int?, Int?> {
        // Pattern 1: Season 1 Episode 2 or Season 01 - Episode 02
        val wordRegex = Regex("(?i)\\b(?:season|series|s)[._\\-\\s]*(\\d{1,2})[._\\-\\s]*(?:episode|ep|e)[._\\-\\s]*(\\d{1,3})\\b")
        val wordMatch = wordRegex.find(fileName)
        if (wordMatch != null) {
            val s = wordMatch.groupValues[1].toIntOrNull()
            val e = wordMatch.groupValues[2].toIntOrNull()
            return Pair(s, e)
        }

        // Pattern 2: S01E02 or s1e2 or S01.E02
        val seRegex = Regex("(?i)[sS](\\d{1,2})[._\\-\\s]*[eE](\\d{1,3})")
        val seMatch = seRegex.find(fileName)
        if (seMatch != null) {
            val s = seMatch.groupValues[1].toIntOrNull()
            val e = seMatch.groupValues[2].toIntOrNull()
            return Pair(s, e)
        }

        // Pattern 3: 1x02 or 01x02
        val xRegex = Regex("(?i)\\b(\\d{1,2})x(\\d{1,3})\\b")
        val xMatch = xRegex.find(fileName)
        if (xMatch != null) {
            val s = xMatch.groupValues[1].toIntOrNull()
            val e = xMatch.groupValues[2].toIntOrNull()
            return Pair(s, e)
        }

        // Pattern 4: Standalone Episode: Episode 02, Ep 02, Ep.02, or E02
        val epRegex = Regex("(?i)\\b(?:ep|episode|e)[._\\-\\s]*(\\d{1,3})\\b")
        val epMatch = epRegex.find(fileName)
        if (epMatch != null) {
            val e = epMatch.groupValues[1].toIntOrNull()
            return Pair(null, e)
        }

        // Pattern 5: Numeric episode pattern like " - 02 ", " - 02.", " 02.", "02 - "
        val numRegex = Regex("(?i)(?:^|[._\\-\\s])0*([1-9]\\d{0,2})(?:[._\\-\\s]|$)(?!\\b(?:1080|720|480|2160|x264|x265|hevc|10bit)\\b)")
        val numMatch = numRegex.find(fileName)
        if (numMatch != null) {
            val e = numMatch.groupValues[1].toIntOrNull()
            if (e != null && e in 1..999) {
                return Pair(null, e)
            }
        }

        return Pair(null, null)
    }

    fun extractSeasonFromFolder(folderName: String): Int? {
        val seasonRegex = Regex("(?i)(?:season|series|s)[._\\-\\s]*(\\d+)")
        val match = seasonRegex.find(folderName)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun cleanMediaTitle(fileName: String): String {
        return fileName
            .replace(Regex("(?i)\\b(1080p|720p|480p|2160p|4k|x264|x265|hevc|10bit|dual|audio|hindi|english|korean|tamil|telugu|web-dl|bluray|hdtv|repack|yify|rarbg)\\b.*"), "")
            .replace(Regex("[\\.\\-_]"), " ")
            .trim()
    }

    fun cleanEpisodeTitle(fileName: String, showTitle: String? = null): String {
        var clean = fileName
            .replace(Regex("(?i)\\b(?:season|series|s)[._\\-\\s]*(\\d{1,2})[._\\-\\s]*(?:episode|ep|e)[._\\-\\s]*(\\d{1,3})\\b"), "")
            .replace(Regex("(?i)[sS]\\d{1,2}[._\\-\\s]*[eE]\\d{1,3}|\\b\\d{1,2}x\\d{1,3}\\b"), "")
            .replace(Regex("(?i)\\b(?:ep|episode|e)[._\\-\\s]*\\d{1,3}\\b"), "")
            .replace(Regex("(?i)\\b(1080p|720p|480p|2160p|4k|x264|x265|hevc|10bit|dual|audio|hindi|english|web-dl|bluray|aac|h264|mp4|mkv)\\b.*"), "")
            .replace(Regex("[\\.\\-_]"), " ")
            .trim()

        if (!showTitle.isNullOrBlank()) {
            clean = clean.replace(Regex("(?i)^\\s*${Regex.escape(showTitle)}\\s*"), "").trim()
        }
        return clean
    }

    fun getXmlDocument(file: File): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            doc.documentElement.normalize()
            doc
        } catch (e: Exception) { null }
    }

    fun getTagText(element: Element, tagName: String): String {
        val nodeList = element.getElementsByTagName(tagName)
        if (nodeList.length > 0) {
            return nodeList.item(0)?.textContent?.trim() ?: ""
        }
        return ""
    }

    fun getAllTagTexts(element: Element, tagName: String): List<String> {
        val list = mutableListOf<String>()
        val nodeList = element.getElementsByTagName(tagName)
        for (i in 0 until nodeList.length) {
            val text = nodeList.item(i)?.textContent?.trim() ?: ""
            if (text.isNotBlank()) list.add(text)
        }
        return list
    }

    fun resolveArtworkLocalFallback(parentDir: File?, targetName: String): String? {
        if (parentDir == null) return null
        return File(parentDir, targetName).takeIf { it.exists() }?.absolutePath
    }

    fun getSharedFilmography(actorName: String, movies: List<MovieItem>, shows: List<TvShowItem>): Pair<List<MovieItem>, List<TvShowItem>> {
        val matchMovies = movies.filter { movie -> movie.actors.any { it.name.equals(actorName, ignoreCase = true) } }
        val matchShows = shows.filter { show -> show.actors.any { it.name.equals(actorName, ignoreCase = true) } }
        return Pair(matchMovies, matchShows)
    }
}
