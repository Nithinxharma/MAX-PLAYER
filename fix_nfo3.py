import re

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'r') as f:
    content = f.read()

# Let's replace the whole scanTvShowEpisodes body
pattern = r"fun scanTvShowEpisodes\(showFolder: File\): List<EpisodeItem> \{.*?\n    \}"
new_body = """fun scanTvShowEpisodes(showFolder: File): List<EpisodeItem> {
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
            val nfoFile = java.io.File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.nfo")
            var parsedEpisode: EpisodeItem? = null
            if (nfoFile.exists()) {
                parsedEpisode = parseKodiEpisodeNfo(nfoFile)
            }

            if (parsedEpisode != null) {
                val finalSeason = parsedEpisode.season
                val currentSeasonCount = (seasonCounters[finalSeason] ?: 0) + 1
                val finalEpisode = if (parsedEpisode.episodeNumber > 0) parsedEpisode.episodeNumber else currentSeasonCount
                seasonCounters[finalSeason] = maxOf(currentSeasonCount, finalEpisode)
                
                episodes.add(parsedEpisode.copy(
                    videoFilePath = videoFile.absolutePath,
                    episodeNumber = finalEpisode
                ))
            } else {
                val extractedSeason = extractSeasonFromFolder(videoFile.parentFile ?: targetDir)
                val extractedEp = extractEpisodeFromFilename(videoFile.nameWithoutExtension)

                val finalSeason = extractedSeason ?: extractSeasonFromFolder(videoFile.parentFile ?: targetDir) ?: 1
                val currentSeasonCount = (seasonCounters[finalSeason] ?: 0) + 1
                val finalEpisode = extractedEp ?: currentSeasonCount
                seasonCounters[finalSeason] = maxOf(currentSeasonCount, finalEpisode)

                val showName = targetDir.name
                val rawClean = cleanEpisodeTitle(videoFile.nameWithoutExtension, showName)
                val localStill = findLocalStillForEpisode(videoFile)
                
                episodes.add(
                    EpisodeItem(
                        title = rawClean,
                        season = finalSeason,
                        episodeNumber = finalEpisode,
                        overview = "S$finalSeason E$finalEpisode - $rawClean",
                        videoFilePath = videoFile.absolutePath,
                        stillUrl = localStill ?: ""
                    )
                )
            }
        }
        
        android.util.Log.d("Series", "[Series] Loaded ${episodes.size} episodes from ${showFolder.name}")
        return episodes
    }"""
content = re.sub(pattern, new_body, content, flags=re.DOTALL)
with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'w') as f:
    f.write(content)
