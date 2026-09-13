import re

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'r') as f:
    content = f.read()

content = content.replace("parseKodiEpisodeNfo(nfoFile)", "parseEpisodeNfo(nfoFile, videoFile)")
content = content.replace("val extractedSeason = extractSeasonFromFolder(videoFile.parentFile ?: targetDir)", "val extractedSeason = extractSeasonFromFolder((videoFile.parentFile ?: targetDir).name)")
content = content.replace("val finalSeason = extractedSeason ?: extractSeasonFromFolder(videoFile.parentFile ?: targetDir) ?: 1", "val finalSeason = extractedSeason ?: extractSeasonFromFolder((videoFile.parentFile ?: targetDir).name) ?: 1")
content = content.replace("val extractedEp = extractEpisodeFromFilename(videoFile.nameWithoutExtension)", "val extractedEp = parseSeasonAndEpisodeFromFilename(videoFile.nameWithoutExtension).second")
content = content.replace("episodeNumber = finalEpisode", "episode = finalEpisode")
content = content.replace("overview = ", "plot = ")
content = content.replace("stillUrl = ", "stillPath = ")
content = content.replace("userRating = 0.0", "userRating = 0.0") # not needed
content = content.replace("aired = \"\"", "aired = \"\"") # not needed, we can just supply default values. Wait! We need to add them!

# Add aired and userRating to EpisodeItem constructor if not present
content = re.sub(
    r'EpisodeItem\(\s*title = rawClean,\s*season = finalSeason,\s*episode = finalEpisode,\s*plot = "S\$finalSeason E\$finalEpisode - \$rawClean",\s*videoFilePath = videoFile.absolutePath,\s*stillPath = localStill \?: ""\s*\)',
    'EpisodeItem(title = rawClean, season = finalSeason, episode = finalEpisode, plot = "S$finalSeason E$finalEpisode - $rawClean", videoFilePath = videoFile.absolutePath, stillPath = localStill ?: "", userRating = 0.0, aired = "")',
    content
)

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'w') as f:
    f.write(content)
