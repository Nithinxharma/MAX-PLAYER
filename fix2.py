import re

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'r') as f:
    content = f.read()

bad_block = """        if (!showFolder.exists()) android.util.Log.d("Series", "[Series] Loaded ${episodes.size} episodes from ${showFolder.name}")
        return episodes return episodes

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
        collectVideos(targetDir)"""

good_block = """        if (!showFolder.exists()) return episodes
        
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
        collectVideos(targetDir)"""

content = content.replace(bad_block, good_block)

# And fix the end of the function:
end_bad = """        android.util.Log.d("Series", "[Series] Loaded ${episodes.size} episodes from ${showFolder.name}")
        return episodes"""
if end_bad not in content:
    content = content.replace("        return episodes", end_bad)
# Wait, I might double replace.
with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'w') as f:
    f.write(content)
