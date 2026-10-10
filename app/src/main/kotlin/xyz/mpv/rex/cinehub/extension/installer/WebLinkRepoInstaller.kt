package xyz.mpv.rex.cinehub.extension.installer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.RepoVerificationResult

/**
 * Result data for a 1-click web repository installation attempt.
 */
data class WebRepoInstallResult(
    val isSuccess: Boolean,
    val repositoryUrl: String,
    val repositoryName: String,
    val pluginCount: Int,
    val message: String
)

/**
 * WebLinkRepoInstaller bridges deep-links, web URLs, and pasted repository endpoints
 * directly into MaxStream's RepositoryManager.
 */
class WebLinkRepoInstaller(
    private val repositoryManager: RepositoryManager
) {
    companion object {
        private const val TAG = "WebLinkRepoInstaller"

        /**
         * Normalizes raw URLs, custom schemes (cloudstreamrepo://, csrepo://), or pastebin URLs.
         */
        fun sanitizeRepositoryUrl(rawInput: String): String {
            var url = rawInput.trim()
            if (url.startsWith("cloudstreamrepo://", ignoreCase = true)) {
                url = "https://" + url.removePrefix("cloudstreamrepo://").removePrefix("http://").removePrefix("https://")
            } else if (url.startsWith("csrepo://", ignoreCase = true)) {
                url = "https://" + url.removePrefix("csrepo://").removePrefix("http://").removePrefix("https://")
            } else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                if (url.contains("raw.githubusercontent.com") || url.contains("pastebin.com") || url.endsWith(".json")) {
                    url = "https://$url"
                }
            }
            return url
        }
    }

    /**
     * Inspects a web link and verifies repository compatibility before installation.
     */
    suspend fun inspectRepositoryUrl(rawUrl: String): RepoVerificationResult = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeRepositoryUrl(rawUrl)
        Log.d(TAG, "Inspecting repository URL: $cleanUrl")
        repositoryManager.verifyRepository(cleanUrl)
    }

    /**
     * Executes 1-click repository installation into RepositoryManager.
     */
    suspend fun installRepositoryFromUrl(
        rawUrl: String,
        customName: String? = null,
        customDescription: String? = null
    ): WebRepoInstallResult = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeRepositoryUrl(rawUrl)
        if (cleanUrl.isBlank()) {
            return@withContext WebRepoInstallResult(
                isSuccess = false,
                repositoryUrl = rawUrl,
                repositoryName = "Unknown",
                pluginCount = 0,
                message = "Invalid or empty repository URL provided."
            )
        }

        val verification = repositoryManager.verifyRepository(cleanUrl)
        if (!verification.isOnline && verification.pluginCount == 0) {
            return@withContext WebRepoInstallResult(
                isSuccess = false,
                repositoryUrl = cleanUrl,
                repositoryName = verification.name,
                pluginCount = 0,
                message = verification.error ?: "Failed to reach repository manifest."
            )
        }

        val repoName = customName?.takeIf { it.isNotBlank() } ?: verification.name
        val success = repositoryManager.addRepository(cleanUrl, repoName, customDescription)

        if (success) {
            WebRepoInstallResult(
                isSuccess = true,
                repositoryUrl = cleanUrl,
                repositoryName = repoName,
                pluginCount = verification.pluginCount,
                message = "Successfully installed $repoName with ${verification.pluginCount} available extensions!"
            )
        } else {
            WebRepoInstallResult(
                isSuccess = false,
                repositoryUrl = cleanUrl,
                repositoryName = repoName,
                pluginCount = 0,
                message = "Repository is already installed or could not be saved."
            )
        }
    }
}
