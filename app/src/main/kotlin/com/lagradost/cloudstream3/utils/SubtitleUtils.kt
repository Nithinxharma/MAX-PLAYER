package com.lagradost.cloudstream3.utils

import android.content.Context
import android.util.Log
import java.io.File

object SubtitleUtils {

    private const val TAG = "SubtitleUtils"

    // Only these files are allowed, so no videos as subtitles
    val allowedExtensions = listOf(
        ".vtt", ".srt", ".txt", ".ass",
        ".ttml", ".sbv", ".dfxp"
    )

    /**
     * Deletes subtitles matching the target video item or filename from disk.
     */
    fun deleteMatchingSubtitles(context: Context, info: Any) {
        val displayName = when (info) {
            is String -> info
            is File -> info.name
            else -> {
                // Reflective or string property fallback for DownloadedFileInfo
                try {
                    val field = info.javaClass.getDeclaredField("displayName")
                    field.isAccessible = true
                    field.get(info) as? String
                } catch (_: Throwable) {
                    info.toString()
                }
            }
        } ?: return

        // 1. Search in cache dir
        deleteMatchingSubtitles(context.cacheDir, displayName)

        // 2. Search in files dir
        deleteMatchingSubtitles(context.filesDir, displayName)

        // 3. Search in external files dir if available
        context.getExternalFilesDir(null)?.let { externalDir ->
            deleteMatchingSubtitles(externalDir, displayName)
        }
    }

    /**
     * Recursively deletes subtitles matching displayName within a given directory.
     */
    fun deleteMatchingSubtitles(folder: File?, displayName: String) {
        if (folder == null || !folder.exists() || !folder.isDirectory) return
        val cleanDisplay = cleanDisplayName(displayName)

        try {
            folder.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteMatchingSubtitles(file, displayName)
                } else {
                    if (isMatchingSubtitle(file.name, displayName, cleanDisplay)) {
                        try {
                            if (file.delete()) {
                                Log.i(TAG, "Deleted matching subtitle: ${file.absolutePath}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete subtitle ${file.name}: ${e.message}")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error traversing folder for subtitle deletion: ${t.message}")
        }
    }

    /**
     * Purges all cached subtitle temporary files from app cache and files storage.
     */
    fun clearSubtitleCache(context: Context) {
        val targetDirs = listOfNotNull(
            context.cacheDir,
            File(context.cacheDir, "subtitles"),
            File(context.filesDir, "subtitles"),
            context.getExternalFilesDir("subtitles")
        )

        for (dir in targetDirs) {
            if (!dir.exists()) continue
            dir.listFiles()?.forEach { file ->
                val name = file.name.lowercase()
                val isSub = allowedExtensions.any { name.endsWith(it) } ||
                        name.startsWith("temp-subtitle") ||
                        name.startsWith("unzipped-subtitle")
                if (isSub && file.isFile) {
                    try {
                        file.delete()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    /**
     * Cleans up orphaned subtitle files that do not correspond to any active video files.
     */
    fun cleanupOrphanSubtitles(folder: File?, activeVideoNames: Set<String> = emptySet()) {
        if (folder == null || !folder.exists() || !folder.isDirectory) return
        val cleanActiveNames = activeVideoNames.map { cleanDisplayName(it).lowercase() }.toSet()

        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                cleanupOrphanSubtitles(file, activeVideoNames)
            } else {
                val name = file.name.lowercase()
                val isSub = allowedExtensions.any { name.endsWith(it) }
                if (isSub) {
                    val cleanSub = cleanDisplayName(file.name).lowercase()
                    val hasParentVideo = cleanActiveNames.any { cleanSub.startsWith(it) }
                    if (!hasParentVideo && cleanActiveNames.isNotEmpty()) {
                        try {
                            file.delete()
                            Log.i(TAG, "Removed orphan subtitle: ${file.name}")
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }
    }

    /**
     * @param name the file name of the subtitle
     * @param display the file name of the video
     * @param cleanDisplay the cleanDisplayName of the video file name
     */
    fun isMatchingSubtitle(
        name: String,
        display: String,
        cleanDisplay: String
    ): Boolean {
        // Check if the file has a valid subtitle extension
        val hasValidExtension = allowedExtensions.any { name.endsWith(it, ignoreCase = true) }

        // We can't have the exact same file as a subtitle
        val isNotDisplayName = !name.equals(display, ignoreCase = true)

        // Check if the file name starts with a cleaned version of the display name
        val startsWithCleanDisplay = cleanDisplayName(name).startsWith(cleanDisplay, ignoreCase = true)

        return hasValidExtension && isNotDisplayName && startsWithCleanDisplay
    }

    fun cleanDisplayName(name: String): String {
        return name.substringBeforeLast('.').trim()
    }
}
