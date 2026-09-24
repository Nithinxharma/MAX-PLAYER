package xyz.mpv.rex.cinehub.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.io.File

data class DownloadedVideoItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

object CineDownloadManager {

    fun downloadStream(
        context: Context,
        title: String,
        link: ExtractorLink
    ) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Toast.makeText(context, "Download Manager service unavailable", Toast.LENGTH_SHORT).show()
                return
            }

            val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val extension = if (link.isM3u8) ".m3u8" else ".mp4"
            val fileName = "$sanitizedTitle - ${link.name.ifBlank { "Stream" }}$extension"

            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MAX STREAM"
            )
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val request = DownloadManager.Request(Uri.parse(link.url)).apply {
                setTitle(title)
                setDescription("Downloading ${link.name} ($extension)")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MAX STREAM/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                // Add CloudStream stream headers for stream sources requiring headers
                if (link.referer.isNotBlank()) {
                    addRequestHeader("Referer", link.referer)
                }
                addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                
                link.headers.forEach { (key, value) ->
                    if (key.isNotBlank() && value.isNotBlank()) {
                        addRequestHeader(key, value)
                    }
                }
            }

            downloadManager.enqueue(request)
            Toast.makeText(context, "Started downloading: $sanitizedTitle", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to start download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun getDownloadedVideos(context: Context): List<DownloadedVideoItem> {
        val list = mutableListOf<DownloadedVideoItem>()
        try {
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MAX STREAM"
            )
            if (targetDir.exists() && targetDir.isDirectory) {
                val videoExtensions = setOf("mp4", "mkv", "webm", "ts", "m3u8", "avi", "mov")
                targetDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() in videoExtensions) {
                        list.add(
                            DownloadedVideoItem(
                                file = file,
                                name = file.nameWithoutExtension,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.lastModified }
    }

    fun deleteDownloadedVideo(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
