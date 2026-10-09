package xyz.mpv.rex.utils.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.R
import xyz.mpv.rex.utils.media.MediaFormatter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// --- Data Models ---

@Serializable
data class Release(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("assets") val assets: List<Asset> = emptyList(),
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("author") val author: ReleaseAuthor? = null
)

@Serializable
data class ReleaseAuthor(
    @SerialName("login") val login: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class Asset(
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)

enum class UpdateChannel(val displayName: String) {
    STABLE("Stable Releases"),
    PRE_RELEASE("Pre-Releases & Betas"),
    ALL("All Releases")
}

@Serializable
data class GitHubSyncConfig(
    val repo: String = "NithinXharma/MAx-player",
    val token: String = "",
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val autoCheck: Boolean = true,
    val autoInstall: Boolean = true
)

// --- Domain Manager ---

class UpdateManager(
    private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val prefs = context.getSharedPreferences("maxstream_update_prefs", Context.MODE_PRIVATE)

    fun getGitHubConfig(): GitHubSyncConfig {
        val repo = prefs.getString("github_repo", "NithinXharma/MAx-player") ?: "NithinXharma/MAx-player"
        val token = prefs.getString("github_token", "") ?: ""
        val channelStr = prefs.getString("update_channel", UpdateChannel.STABLE.name) ?: UpdateChannel.STABLE.name
        val channel = runCatching { UpdateChannel.valueOf(channelStr) }.getOrDefault(UpdateChannel.STABLE)
        val autoCheck = prefs.getBoolean("auto_check_updates", true)
        val autoInstall = prefs.getBoolean("auto_install_updates", true)

        return GitHubSyncConfig(
            repo = repo,
            token = token,
            channel = channel,
            autoCheck = autoCheck,
            autoInstall = autoInstall
        )
    }

    fun saveGitHubConfig(config: GitHubSyncConfig) {
        prefs.edit()
            .putString("github_repo", config.repo.trim())
            .putString("github_token", config.token.trim())
            .putString("update_channel", config.channel.name)
            .putBoolean("auto_check_updates", config.autoCheck)
            .putBoolean("auto_install_updates", config.autoInstall)
            .apply()
    }

    private fun cleanRepoPath(repoInput: String): String {
        return repoInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .trim('/')
            .ifEmpty { "NithinXharma/MAx-player" }
    }

    suspend fun fetchAllReleases(forceNetwork: Boolean = false): List<Release> = withContext(Dispatchers.IO) {
        val config = getGitHubConfig()
        val cleanRepo = cleanRepoPath(config.repo)
        val cacheKey = "cached_releases_$cleanRepo"

        // Load cache first if available
        val cachedJson = prefs.getString(cacheKey, null)
        val cachedReleases: List<Release> = if (!cachedJson.isNullOrEmpty()) {
            runCatching { json.decodeFromString<List<Release>>(cachedJson) }.getOrDefault(emptyList())
        } else emptyList()

        if (!forceNetwork && cachedReleases.isNotEmpty()) {
            return@withContext filterByChannel(cachedReleases, config.channel)
        }

        val url = "https://api.github.com/repos/$cleanRepo/releases?per_page=30"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "MaxStreamApp-Android")
            .header("Accept", "application/vnd.github.v3+json")

        if (config.token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.token}")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                // If network fails (e.g. rate limit, 404, offline), fallback to cached
                if (cachedReleases.isNotEmpty()) {
                    return@withContext filterByChannel(cachedReleases, config.channel)
                }
                throw IOException("GitHub API returned HTTP ${response.code}: ${response.message}")
            }

            val bodyString = response.body.string()
            val releases = json.decodeFromString<List<Release>>(bodyString)

            // Cache successful fetch
            prefs.edit().putString(cacheKey, bodyString).apply()

            filterByChannel(releases, config.channel)
        } catch (e: Exception) {
            if (cachedReleases.isNotEmpty()) {
                filterByChannel(cachedReleases, config.channel)
            } else {
                throw e
            }
        }
    }

    private fun filterByChannel(releases: List<Release>, channel: UpdateChannel): List<Release> {
        return releases.filter { release ->
            when (channel) {
                UpdateChannel.STABLE -> !release.prerelease && !release.draft
                UpdateChannel.PRE_RELEASE -> !release.draft
                UpdateChannel.ALL -> true
            }
        }
    }

    suspend fun checkForUpdate(forceShow: Boolean = false): Release? {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return null
        }

        val releases = fetchAllReleases(forceNetwork = true)
        if (releases.isEmpty()) return null

        val currentVersion = BuildConfig.VERSION_NAME.replace("-dev", "").trim()
        val ignoredVersion = prefs.getString("ignored_version", null)

        for (release in releases) {
            val remoteVersion = release.tagName.removePrefix("v").trim()
            if (!forceShow && ignoredVersion == remoteVersion) {
                continue
            }
            if (isNewerVersion(remoteVersion, currentVersion)) {
                return release
            }
        }
        return null
    }

    fun ignoreVersion(version: String) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
        prefs.edit().putString("ignored_version", version.trim()).apply()
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rClean = remote.substringBefore('-')
        val cClean = current.substringBefore('-')
        val rParts = rClean.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = cClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun downloadUpdate(release: Release): Flow<Float> {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return flowOf(100f)
        }

        val asset = selectBestApkAsset(release.assets)
            ?: throw Exception("No compatible APK asset found in release ${release.tagName}")

        val destination = File(context.externalCacheDir, asset.name)
        return downloadApk(asset.downloadUrl, destination)
    }

    fun selectBestApkAsset(assets: List<Asset>): Asset? {
        val deviceArch = getDeviceArchitecture()

        // 1. Architecture-specific APK
        val archSpecificApk = assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) && asset.name.contains(deviceArch, ignoreCase = true)
        }
        if (archSpecificApk != null) return archSpecificApk

        // 2. Universal APK
        val universalApk = assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) && asset.name.contains("universal", ignoreCase = true)
        }
        if (universalApk != null) return universalApk

        // 3. Fallback: Any APK
        return assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
    }

    private fun getDeviceArchitecture(): String {
        val primaryAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI ?: "arm64-v8a"
        }

        return when (primaryAbi) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            else -> "universal"
        }
    }

    private fun downloadApk(url: String, destination: File): Flow<Float> = flow {
        val config = getGitHubConfig()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "MaxStreamApp-Android")
            .header("Accept", "application/octet-stream")

        if (config.token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.token}")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) throw IOException("Download failed with HTTP ${response.code}")

        val body = response.body
        val contentLength = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(destination)

        try {
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (contentLength > 0) {
                    (totalBytesRead.toFloat() / contentLength.toFloat()) * 100f
                } else {
                    -1f
                }
                emit(progress)
            }
            outputStream.flush()
            emit(100f)
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }.flowOn(Dispatchers.IO)

    fun getApkFile(release: Release): File? {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return null
        val asset = selectBestApkAsset(release.assets) ?: return null
        val file = File(context.externalCacheDir, asset.name)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun clearCache() {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
        context.externalCacheDir?.listFiles()?.forEach {
            if (it.name.endsWith(".apk", ignoreCase = true)) it.delete()
        }
    }
}

// --- ViewModel ---

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = UpdateManager(application)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _releases = MutableStateFlow<List<Release>>(emptyList())
    val releases: StateFlow<List<Release>> = _releases.asStateFlow()

    private val _isSyncingReleases = MutableStateFlow(false)
    val isSyncingReleases: StateFlow<Boolean> = _isSyncingReleases.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _config = MutableStateFlow(updateManager.getGitHubConfig())
    val config: StateFlow<GitHubSyncConfig> = _config.asStateFlow()

    val isAutoUpdateEnabled: StateFlow<Boolean> = MutableStateFlow(_config.value.autoCheck)

    init {
        if (BuildConfig.ENABLE_UPDATE_FEATURE) {
            syncReleases(forceNetwork = false)
            if (_config.value.autoCheck) {
                checkForUpdate(manual = false)
            }
        }
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        data class Available(val release: Release) : UpdateState()
        object NoUpdate : UpdateState()
        data class Error(val message: String? = null) : UpdateState()
        data class ReadyToInstall(val release: Release) : UpdateState()
    }

    fun dismissNoUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun toggleAutoUpdate(enabled: Boolean) {
        val newConfig = _config.value.copy(autoCheck = enabled)
        saveConfig(newConfig)
        if (enabled) {
            checkForUpdate(manual = false)
        }
    }

    fun saveConfig(newConfig: GitHubSyncConfig) {
        updateManager.saveGitHubConfig(newConfig)
        _config.value = newConfig
        syncReleases(forceNetwork = true)
    }

    fun syncReleases(forceNetwork: Boolean = true) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
        viewModelScope.launch {
            _isSyncingReleases.value = true
            try {
                val fetched = updateManager.fetchAllReleases(forceNetwork = forceNetwork)
                _releases.value = fetched
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncingReleases.value = false
            }
        }
    }

    fun checkForUpdate(manual: Boolean = false) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return

        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            try {
                val release = updateManager.checkForUpdate(forceShow = manual)
                if (release != null) {
                    val existingFile = updateManager.getApkFile(release)
                    if (existingFile != null) {
                        _updateState.value = UpdateState.ReadyToInstall(release)
                    } else {
                        _updateState.value = UpdateState.Available(release)
                    }
                } else {
                    if (manual) _updateState.value = UpdateState.NoUpdate
                    else _updateState.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (manual) _updateState.value = UpdateState.Error(e.message)
                else _updateState.value = UpdateState.Idle
            }
        }
    }

    fun downloadUpdate(release: Release) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            try {
                updateManager.downloadUpdate(release).collect { progress ->
                    _downloadProgress.value = progress
                }
                _isDownloading.value = false
                _updateState.value = UpdateState.ReadyToInstall(release)

                // If auto-install is enabled, automatically trigger install dialog
                if (_config.value.autoInstall) {
                    installUpdate(release)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isDownloading.value = false
                _updateState.value = UpdateState.Error(e.message)
            }
        }
    }

    fun installUpdate(release: Release, activity: Activity? = null) {
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return

        val file = updateManager.getApkFile(release) ?: return
        val context = getApplication<Application>()

        // Check Unknown App Sources permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dismiss() {
        updateManager.clearCache()
        _updateState.value = UpdateState.Idle
    }

    fun ignoreVersion(version: String) {
        updateManager.ignoreVersion(version)
        _updateState.value = UpdateState.Idle
    }
}

// --- UI Components ---

@Composable
fun UpdateDialog(
    release: Release,
    isDownloading: Boolean,
    progress: Float,
    actionLabel: String,
    currentVersion: String,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onIgnore: () -> Unit,
    onViewArticles: (() -> Unit)? = null
) {
    val downloadSize = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.size ?: 0L
    val formattedDate = formatDate(release.publishedAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (actionLabel == "Install" || actionLabel == "Install Now") {
                    Icons.Filled.SystemUpdate
                } else {
                    Icons.Filled.CloudDownload
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (actionLabel == "Install" || actionLabel == "Install Now") {
                        stringResource(R.string.update_ready_to_install)
                    } else {
                        stringResource(R.string.update_available_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = release.name ?: release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                InfoRow(label = stringResource(R.string.update_current_version), value = currentVersion)
                InfoRow(label = stringResource(R.string.update_latest_version), value = release.tagName.removePrefix("v"))
                InfoRow(label = stringResource(R.string.update_release_date), value = formattedDate)
                if (downloadSize > 0) {
                    InfoRow(label = stringResource(R.string.update_size), value = MediaFormatter.formatFileSize(downloadSize))
                }

                if (!release.body.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.update_release_notes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        MarkdownText(
                            markdown = release.body,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.update_downloading),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (progress >= 0) "${progress.toInt()}%" else "...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (progress >= 0) progress / 100f else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = onAction) {
                    Text(
                        if (actionLabel == "Install" || actionLabel == "Install Now") {
                            stringResource(R.string.update_install)
                        } else {
                            stringResource(R.string.update_download)
                        }
                    )
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    if (actionLabel != "Install" && actionLabel != "Install Now") {
                        TextButton(onClick = onIgnore) {
                            Text(stringResource(R.string.update_ignore))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_cancel))
                    }
                }
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatDate(dateString: String?): String {
    if (dateString.isNullOrBlank()) return "Unknown"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString) ?: return dateString

        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        outputFormat.format(date)
    } catch (e: Exception) {
        dateString
    }
}
