package xyz.mpv.rex.cinehub.stream

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.ui.browser.networkstreaming.clients.NetworkClientFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Serializable
data class DownloadTask(
    val id: String,
    val title: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val url: String,
    val posterUrl: String? = null,
    val localPath: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "",
    val sourceType: String = "streaming",
    val subtitles: List<String> = emptyList(),
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getDisplayName(): String {
        return if (season != null && episode != null) {
            val s = season.toString().padStart(2, '0')
            val e = episode.toString().padStart(2, '0')
            if (!episodeTitle.isNullOrBlank()) "$title S${s}E${e} - $episodeTitle" else "$title S${s}E${e}"
        } else {
            title
        }
    }
}

/**
 * Unified Download Manager supporting CloudStream sources, direct links,
 * and SMB / FTP / WebDAV network file downloads.
 */
class CloudStreamDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val TAG = "CineHub:DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    private val stateFile: File by lazy {
        File(context.filesDir, "cinehub_downloads.json")
    }

    init {
        loadPersistedState()
    }

    private fun getDownloadDir(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "media")
        val cineDir = File(baseDir, "CineHub_Downloads")
        if (!cineDir.exists()) cineDir.mkdirs()
        return cineDir
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    fun startDownload(
        request: CloudStreamRequest,
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        subtitles: List<String> = emptyList()
    ): String {
        val id = UUID.randomUUID().toString()
        val baseFilename = sanitizeFilename(request.getFormattedDisplayName())
        val extension = if (streamUrl.contains(".mkv", ignoreCase = true)) "mkv" else "mp4"
        val targetFile = File(getDownloadDir(), "$baseFilename.$extension")

        val task = DownloadTask(
            id = id,
            title = request.title,
            episodeTitle = request.episodeTitle,
            season = request.seasonNumber,
            episode = request.episodeNumber,
            url = streamUrl,
            posterUrl = request.posterUrl,
            localPath = targetFile.absolutePath,
            status = DownloadStatus.QUEUED,
            sourceType = "streaming"
        )

        updateTask(task)

        val job = scope.launch {
            executeHttpDownload(task, headers, subtitles)
        }
        activeJobs[id] = job
        return id
    }

    fun startNetworkDownload(
        connection: NetworkConnection,
        networkFile: NetworkFile
    ): String {
        val id = UUID.randomUUID().toString()
        val targetFile = File(getDownloadDir(), sanitizeFilename(networkFile.name))

        val task = DownloadTask(
            id = id,
            title = networkFile.name,
            url = networkFile.path,
            localPath = targetFile.absolutePath,
            status = DownloadStatus.QUEUED,
            sourceType = connection.protocol.name.lowercase(),
            totalBytes = networkFile.size
        )

        updateTask(task)

        val job = scope.launch {
            executeNetworkDownload(task, connection, networkFile)
        }
        activeJobs[id] = job
        return id
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val task = _downloads.value.firstOrNull { it.id == id } ?: return
        if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
            updateTask(task.copy(status = DownloadStatus.PAUSED, speed = ""))
        }
    }

    fun resumeDownload(id: String) {
        val task = _downloads.value.firstOrNull { it.id == id } ?: return
        if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
            updateTask(task.copy(status = DownloadStatus.QUEUED, errorMessage = null))
            val job = scope.launch {
                executeHttpDownload(task, emptyMap(), emptyList())
            }
            activeJobs[id] = job
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val task = _downloads.value.firstOrNull { it.id == id } ?: return
        val file = File(task.localPath)
        if (file.exists()) file.delete()
        _downloads.value = _downloads.value.filterNot { it.id == id }
        persistState()
    }

    fun deleteDownload(id: String) {
        cancelDownload(id)
    }

    private suspend fun executeHttpDownload(
        task: DownloadTask,
        headers: Map<String, String>,
        subtitles: List<String>
    ) = withContext(Dispatchers.IO) {
        val targetFile = File(task.localPath)
        var downloaded = if (targetFile.exists()) targetFile.length() else 0L

        updateTask(task.copy(status = DownloadStatus.DOWNLOADING, downloadedBytes = downloaded))

        try {
            val reqBuilder = Request.Builder().url(task.url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            if (downloaded > 0) {
                reqBuilder.addHeader("Range", "bytes=$downloaded-")
            }

            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                throw IllegalStateException("HTTP Error: ${response.code} ${response.message}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val contentLength = body.contentLength()
            val total = if (response.code == 206) downloaded + contentLength else contentLength

            val downloadedSubPaths = mutableListOf<String>()
            // Download subtitles if any
            subtitles.forEachIndexed { index, subUrl ->
                try {
                    val subExt = if (subUrl.contains(".vtt")) "vtt" else "srt"
                    val subFile = File(targetFile.parentFile, "${targetFile.nameWithoutExtension}_sub_$index.$subExt")
                    val subReq = Request.Builder().url(subUrl).build()
                    val subResp = okHttpClient.newCall(subReq).execute()
                    subResp.body?.bytes()?.let { subBytes ->
                        subFile.writeBytes(subBytes)
                        downloadedSubPaths.add(subFile.absolutePath)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading subtitle: $subUrl")
                }
            }

            val outputStream = RandomAccessFile(targetFile, "rw")
            if (response.code == 206) {
                outputStream.seek(downloaded)
            } else {
                outputStream.setLength(0)
                downloaded = 0L
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            val inputStream: InputStream = body.byteStream()

            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdateTime >= 1000) {
                    val speedBps = (bytesSinceLastUpdate * 1000f) / (now - lastUpdateTime)
                    val speedText = formatSpeed(speedBps)
                    val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f

                    updateTask(
                        task.copy(
                            status = DownloadStatus.DOWNLOADING,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            progress = progress,
                            speed = speedText,
                            subtitles = downloadedSubPaths
                        )
                    )
                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outputStream.close()
            inputStream.close()

            updateTask(
                task.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1.0f,
                    downloadedBytes = downloaded,
                    totalBytes = downloaded,
                    speed = "",
                    subtitles = downloadedSubPaths
                )
            )
            Log.i(TAG, "Download completed: ${task.title} -> ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Download error for ${task.title}: ${e.message}", e)
            updateTask(
                task.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Download failed",
                    speed = ""
                )
            )
        } finally {
            activeJobs.remove(task.id)
            persistState()
        }
    }

    private suspend fun executeNetworkDownload(
        task: DownloadTask,
        connection: NetworkConnection,
        networkFile: NetworkFile
    ) = withContext(Dispatchers.IO) {
        val targetFile = File(task.localPath)
        updateTask(task.copy(status = DownloadStatus.DOWNLOADING))

        try {
            val client = NetworkClientFactory.createClient(connection)
            client.connect().getOrThrow()

            val streamResult = client.getFileStream(networkFile.path)
            val inputStream = streamResult.getOrThrow()
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(16384)
            var bytesRead: Int
            var downloaded = 0L
            val total = networkFile.size

            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdateTime >= 1000) {
                    val speedBps = (bytesSinceLastUpdate * 1000f) / (now - lastUpdateTime)
                    val speedText = formatSpeed(speedBps)
                    val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f

                    updateTask(
                        task.copy(
                            status = DownloadStatus.DOWNLOADING,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            progress = progress,
                            speed = speedText
                        )
                    )
                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outputStream.close()
            inputStream.close()
            client.disconnect()

            updateTask(
                task.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    downloadedBytes = downloaded,
                    totalBytes = downloaded,
                    speed = ""
                )
            )
            Log.i(TAG, "Network download completed: ${task.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Network download failed for ${task.title}: ${e.message}", e)
            updateTask(
                task.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Network transfer failed",
                    speed = ""
                )
            )
        } finally {
            activeJobs.remove(task.id)
            persistState()
        }
    }

    private fun formatSpeed(bytesPerSec: Float): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun updateTask(task: DownloadTask) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.id == task.id }
        if (index != -1) {
            current[index] = task
        } else {
            current.add(0, task)
        }
        _downloads.value = current
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun persistState() {
        try {
            val text = json.encodeToString(_downloads.value)
            stateFile.writeText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist downloads state", e)
        }
    }

    private fun loadPersistedState() {
        try {
            if (!stateFile.exists()) return
            val text = stateFile.readText().trim()
            if (text.isBlank()) return
            val list = json.decodeFromString<List<DownloadTask>>(text).map { t ->
                val finalStatus = if (t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.QUEUED) {
                    DownloadStatus.PAUSED
                } else t.status
                t.copy(status = finalStatus)
            }
            _downloads.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted downloads", e)
        }
    }
}
