package xyz.mpv.rex.cinehub.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
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

data class ActiveDownloadTask(
    val id: Long,
    val title: String,
    val description: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: Int,
    val statusLabel: String,
    val progressPercent: Int,
    val localUri: String?
)

object CineDownloadManager {

    fun downloadStream(
        context: Context,
        title: String,
        link: ExtractorLink
    ): Long {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Toast.makeText(context, "Download Manager service unavailable", Toast.LENGTH_SHORT).show()
                return -1L
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

            val id = downloadManager.enqueue(request)
            Toast.makeText(context, "Started downloading: $sanitizedTitle", Toast.LENGTH_LONG).show()
            return id
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to start download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    fun getActiveDownloads(context: Context): List<ActiveDownloadTask> {
        val list = mutableListOf<ActiveDownloadTask>()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return list
        val query = DownloadManager.Query()
        
        var cursor: Cursor? = null
        try {
            cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val descIdx = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
                val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                do {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "Download" else "Download"
                    val desc = if (descIdx >= 0) cursor.getString(descIdx) ?: "" else ""
                    val downloaded = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L
                    val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L
                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else 0
                    val uri = if (uriIdx >= 0) cursor.getString(uriIdx) else null

                    // Filter only items destined for MAX STREAM or matching our requests
                    if (desc.contains("MAX STREAM") || title.isNotBlank()) {
                        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        val statusLabel = when (status) {
                            DownloadManager.STATUS_RUNNING -> "Downloading"
                            DownloadManager.STATUS_PAUSED -> "Paused"
                            DownloadManager.STATUS_PENDING -> "Pending"
                            DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                            DownloadManager.STATUS_FAILED -> "Failed"
                            else -> "In Progress"
                        }

                        // Only list items that are actively in progress or pending
                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING) {
                            list.add(
                                ActiveDownloadTask(
                                    id = id,
                                    title = title,
                                    description = desc,
                                    bytesDownloaded = downloaded,
                                    totalBytes = total,
                                    status = status,
                                    statusLabel = statusLabel,
                                    progressPercent = progress,
                                    localUri = uri
                                )
                            )
                        }
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return list
    }

    fun cancelDownload(context: Context, downloadId: Long): Boolean {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.remove(downloadId)
            true
        } catch (e: Exception) {
            false
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
