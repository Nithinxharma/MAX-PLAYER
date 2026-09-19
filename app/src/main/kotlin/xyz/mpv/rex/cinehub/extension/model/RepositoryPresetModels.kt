package xyz.mpv.rex.cinehub.extension.model

data class RepoPresetItem(
    val name: String,
    val url: String,
    val description: String,
    val author: String = "Community",
    val isInstalled: Boolean = false,
    val pluginCount: Int = 0,
    val status: RepoConnectivityStatus = RepoConnectivityStatus.UNKNOWN
)

enum class RepoConnectivityStatus {
    UNKNOWN,
    CHECKING,
    ONLINE,
    OFFLINE
}

data class RepoVerificationResult(
    val url: String,
    val name: String,
    val isOnline: Boolean,
    val httpCode: Int = 0,
    val pluginCount: Int = 0,
    val latencyMs: Long = 0L,
    val error: String? = null
)

data class MegaRepoInstallResult(
    val isSuccess: Boolean,
    val megaRepoUrl: String,
    val discoveredReposCount: Int,
    val installedReposCount: Int,
    val message: String
)
