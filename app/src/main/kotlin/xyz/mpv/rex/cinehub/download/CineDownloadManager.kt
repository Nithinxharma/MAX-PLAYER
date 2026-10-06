package xyz.mpv.rex.cinehub.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

private data class ActiveHlsDownload(
    val id: Long,
    val title: String,
    val description: String,
    val targetFile: File,
    @Volatile var bytesDownloaded: Long = 0L,
    @Volatile var totalBytesEstimated: Long = 0L,
    @Volatile var progressPercent: Int = 0,
    @Volatile var status: Int = DownloadManager.STATUS_RUNNING,
    @Volatile var statusLabel: String = "Starting...",
    @Volatile var job: Job? = null
)

object CineDownloadManager {

    private val activeHlsTasks = ConcurrentHashMap<Long, ActiveHlsDownload>()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun downloadStream(
        context: Context,
        title: String,
        link: ExtractorLink
    ): Long {
        val isHlsStream = link.isM3u8 ||
                link.type == ExtractorLinkType.M3U8 ||
                link.url.contains(".m3u8", ignoreCase = true)

        return if (isHlsStream) {
            startHlsDownloadTask(context, title, link)
        } else {
            startDirectDownloadTask(context, title, link)
        }
    }

    private fun startDirectDownloadTask(
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
            val fileName = "$sanitizedTitle - ${link.name.ifBlank { "Stream" }}.mp4"

            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MAX STREAM"
            )
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val request = DownloadManager.Request(Uri.parse(link.url)).apply {
                setTitle(title)
                setDescription("Downloading ${link.name} (Direct MP4)")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MAX STREAM/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

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
            Toast.makeText(context, "Failed to start direct download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    private fun startHlsDownloadTask(
        context: Context,
        title: String,
        link: ExtractorLink
    ): Long {
        val taskId = System.currentTimeMillis()
        val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val fileName = "$sanitizedTitle - ${link.name.ifBlank { "HLS" }}.mp4"

        val targetDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MAX STREAM"
        )
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, fileName)

        val task = ActiveHlsDownload(
            id = taskId,
            title = title,
            description = "Downloading HLS Stream ($fileName)",
            targetFile = targetFile,
            status = DownloadManager.STATUS_RUNNING,
            statusLabel = "Initializing HLS stream..."
        )

        activeHlsTasks[taskId] = task

        val job = scope.launch {
            processHlsDownload(context, task, link)
        }
        task.job = job

        Toast.makeText(context, "Started downloading media: $sanitizedTitle", Toast.LENGTH_LONG).show()
        return taskId
    }

    private suspend fun processHlsDownload(
        context: Context,
        task: ActiveHlsDownload,
        link: ExtractorLink
    ) {
        var outputStream: FileOutputStream? = null
        try {
            task.statusLabel = "Parsing M3U8 manifest..."

            // 1. Fetch M3U8 manifest content
            val manifestUrl = link.url
            val manifestText = fetchTextWithHeaders(manifestUrl, link)

            if (manifestText.isBlank()) {
                task.status = DownloadManager.STATUS_FAILED
                task.statusLabel = "Failed: Empty manifest"
                return
            }

            // 2. Resolve Master Playlist if needed
            val mediaPlaylistUrl = resolveMediaPlaylistUrl(manifestUrl, manifestText, link)
            val mediaPlaylistText = if (mediaPlaylistUrl != manifestUrl) {
                fetchTextWithHeaders(mediaPlaylistUrl, link)
            } else {
                manifestText
            }

            // 3. Extract Segment URLs
            val segmentUrls = parseSegmentUrls(mediaPlaylistUrl, mediaPlaylistText)
            if (segmentUrls.isEmpty()) {
                task.status = DownloadManager.STATUS_FAILED
                task.statusLabel = "Failed: No stream segments found"
                return
            }

            task.statusLabel = "Downloading segments (0/${segmentUrls.size})..."
            outputStream = FileOutputStream(task.targetFile)

            val totalSegments = segmentUrls.size
            for ((index, segmentUrl) in segmentUrls.withIndex()) {
                if (task.status == DownloadManager.STATUS_FAILED || task.job?.isCancelled == true) {
                    break
                }

                // Download segment bytes
                val segmentBytes = fetchBytesWithHeaders(segmentUrl, link)
                if (segmentBytes != null && segmentBytes.isNotEmpty()) {
                    outputStream.write(segmentBytes)
                    outputStream.flush()
                }

                val currentFileLength = task.targetFile.length()
                val progress = (((index + 1).toDouble() / totalSegments) * 100).toInt()

                task.bytesDownloaded = currentFileLength
                task.progressPercent = progress
                task.statusLabel = "Segment ${index + 1}/$totalSegments ($progress%)"
            }

            if (task.job?.isCancelled == true) {
                task.status = DownloadManager.STATUS_FAILED
                task.statusLabel = "Cancelled"
                if (task.targetFile.exists()) task.targetFile.delete()
                return
            }

            task.status = DownloadManager.STATUS_SUCCESSFUL
            task.statusLabel = "Completed"
            task.progressPercent = 100

            // Register with system media scanner so media libraries recognize the video file
            MediaScannerConnection.scanFile(
                context,
                arrayOf(task.targetFile.absolutePath),
                arrayOf("video/mp4", "video/mp2t")
            ) { _, _ -> }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download completed: ${task.title}", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            task.status = DownloadManager.STATUS_FAILED
            task.statusLabel = "Error: ${e.localizedMessage ?: "Download failed"}"
        } finally {
            try {
                outputStream?.close()
            } catch (_: Throwable) {}
        }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(relativeUrl).toString()
        } catch (_: Exception) {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else if (relativeUrl.startsWith("/")) {
                val protoEnd = baseUrl.indexOf("://")
                if (protoEnd != -1) {
                    val hostEnd = baseUrl.indexOf('/', protoEnd + 3)
                    val origin = if (hostEnd != -1) baseUrl.substring(0, hostEnd) else baseUrl
                    "$origin$relativeUrl"
                } else relativeUrl
            } else {
                val parent = baseUrl.substringBeforeLast('/')
                "$parent/$relativeUrl"
            }
        }
    }

    private fun fetchTextWithHeaders(url: String, link: ExtractorLink): String {
        val reqBuilder = Request.Builder().url(url)
        if (link.referer.isNotBlank()) {
            reqBuilder.header("Referer", link.referer)
        }
        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        link.headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                reqBuilder.header(key, value)
            }
        }

        return try {
            val response = httpClient.newCall(reqBuilder.build()).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun fetchBytesWithHeaders(url: String, link: ExtractorLink): ByteArray? {
        val reqBuilder = Request.Builder().url(url)
        if (link.referer.isNotBlank()) {
            reqBuilder.header("Referer", link.referer)
        }
        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        link.headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                reqBuilder.header(key, value)
            }
        }

        return try {
            val response = httpClient.newCall(reqBuilder.build()).execute()
            response.body?.bytes()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun serveNull(): ByteArray? = null

    private fun resolveMediaPlaylistUrl(baseUrl: String, content: String, link: ExtractorLink): String {
        val lines = content.lines()
        var bestUrl: String? = null
        var maxBandwidth = 0L

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(line)
                val bw = bwMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""
                if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                    if (bw >= maxBandwidth) {
                        maxBandwidth = bw
                        bestUrl = resolveUrl(baseUrl, nextLine)
                    }
                }
            }
        }
        return bestUrl ?: baseUrl
    }

    private fun parseSegmentUrls(baseUrl: String, content: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val fullUrl = resolveUrl(baseUrl, trimmed)
                segments.add(fullUrl)
            }
        }
        return segments
    }

    fun getActiveDownloads(context: Context): List<ActiveDownloadTask> {
        val list = mutableListOf<ActiveDownloadTask>()

        // 1. Add active HLS tasks
        activeHlsTasks.values.forEach { hls ->
            if (hls.status == DownloadManager.STATUS_RUNNING || hls.status == DownloadManager.STATUS_PAUSED || hls.status == DownloadManager.STATUS_PENDING) {
                list.add(
                    ActiveDownloadTask(
                        id = hls.id,
                        title = hls.title,
                        description = hls.description,
                        bytesDownloaded = hls.bytesDownloaded,
                        totalBytes = hls.totalBytesEstimated,
                        status = hls.status,
                        statusLabel = hls.statusLabel,
                        progressPercent = hls.progressPercent,
                        localUri = Uri.fromFile(hls.targetFile).toString()
                    )
                )
            }
        }

        // 2. Add System DownloadManager tasks
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager != null) {
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
        }
        return list
    }

    fun cancelDownload(context: Context, downloadId: Long): Boolean {
        var cancelled = false
        val hlsTask = activeHlsTasks[downloadId]
        if (hlsTask != null) {
            hlsTask.status = DownloadManager.STATUS_FAILED
            hlsTask.statusLabel = "Cancelled"
            hlsTask.job?.cancel()
            activeHlsTasks.remove(downloadId)
            if (hlsTask.targetFile.exists()) {
                hlsTask.targetFile.delete()
            }
            cancelled = true
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.remove(downloadId)
            cancelled = true
        } catch (_: Throwable) {}

        return cancelled
    }

    fun getDownloadedVideos(context: Context): List<DownloadedVideoItem> {
        val list = mutableListOf<DownloadedVideoItem>()
        try {
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MAX STREAM"
            )
            if (targetDir.exists() && targetDir.isDirectory) {
                // EXCLUDE .m3u8 text files - only include playable video files (.mp4, .ts, .mkv, .webm, .avi, .mov)
                val playableVideoExtensions = setOf("mp4", "mkv", "webm", "ts", "avi", "mov", "m4v")
                targetDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() in playableVideoExtensions) {
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
