package xyz.mpv.rex.ui.preferences

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.cinehub.extension.model.MegaRepoInstallResult
import xyz.mpv.rex.cinehub.extension.model.RepoPresetItem
import xyz.mpv.rex.cinehub.extension.model.RepoVerificationResult
import xyz.mpv.rex.cinehub.extension.model.RepositorySyncResult

class RepositoryPresetsViewModel(
    private val context: Context,
    private val repositoryManager: RepositoryManager
) : ViewModel() {

    val builtInPresets: List<RepoPresetItem> = RepositoryManager.BUILT_IN_PRESETS

    val installedRepositories: StateFlow<List<ExtensionRepo>> = repositoryManager.getAllRepositories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _verificationMap = MutableStateFlow<Map<String, RepoVerificationResult>>(emptyMap())
    val verificationMap: StateFlow<Map<String, RepoVerificationResult>> = _verificationMap.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _isSyncingAll = MutableStateFlow(false)
    val isSyncingAll: StateFlow<Boolean> = _isSyncingAll.asStateFlow()

    private val _syncResults = MutableStateFlow<List<RepositorySyncResult>>(emptyList())
    val syncResults: StateFlow<List<RepositorySyncResult>> = _syncResults.asStateFlow()

    private val _isInstallingMegaRepo = MutableStateFlow(false)
    val isInstallingMegaRepo: StateFlow<Boolean> = _isInstallingMegaRepo.asStateFlow()

    private val _megaRepoResult = MutableStateFlow<MegaRepoInstallResult?>(null)
    val megaRepoResult: StateFlow<MegaRepoInstallResult?> = _megaRepoResult.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun enablePreset(preset: RepoPresetItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = repositoryManager.addRepository(preset.url, preset.name, preset.description)
            if (added) {
                _actionMessage.value = "Enabled repository: ${preset.name}"
            } else {
                _actionMessage.value = "Failed to enable repository: ${preset.name}"
            }
        }
    }

    fun enableAllPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            _actionMessage.value = "Enabling all repository presets..."
            val count = repositoryManager.addAllPresets()
            _actionMessage.value = "Successfully added $count new repository preset(s)."
        }
    }

    fun removeRepository(repo: ExtensionRepo) {
        viewModelScope.launch(Dispatchers.IO) {
            repositoryManager.removeRepository(repo)
            _actionMessage.value = "Removed repository: ${repo.name}"
        }
    }

    fun verifyRepository(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repositoryManager.verifyRepository(url)
            val curr = _verificationMap.value.toMutableMap()
            curr[url] = res
            _verificationMap.value = curr
        }
    }

    fun verifyAllPresets() {
        if (_isVerifying.value) return
        _isVerifying.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val map = _verificationMap.value.toMutableMap()
            for (preset in builtInPresets) {
                val res = repositoryManager.verifyRepository(preset.url)
                map[preset.url] = res
                _verificationMap.value = map.toMap()
            }
            _isVerifying.value = false
            _actionMessage.value = "Completed verification check on ${builtInPresets.size} repositories."
        }
    }

    fun installMegaRepo() {
        if (_isInstallingMegaRepo.value) return
        _isInstallingMegaRepo.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = repositoryManager.installMegaRepo()
            _megaRepoResult.value = result
            _isInstallingMegaRepo.value = false
            _actionMessage.value = result.message
        }
    }

    fun syncAllRepositories() {
        if (_isSyncingAll.value) return
        _isSyncingAll.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val results = repositoryManager.syncAllRepositories()
            _syncResults.value = results
            _isSyncingAll.value = false
            val successCount = results.count { it.error == null }
            _actionMessage.value = "Synced $successCount/${results.size} repositories successfully."
        }
    }

    fun syncRepository(repoUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repositoryManager.syncRepository(repoUrl)
            _actionMessage.value = if (res.error == null) {
                "Synced ${res.repoName} (${res.plugins.size} plugins found)"
            } else {
                "Sync failed for ${res.repoName}: ${res.error}"
            }
        }
    }

    fun addCustomRepository(url: String, name: String?, description: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank()) {
                _actionMessage.value = "Error: Repository URL cannot be empty."
                return@launch
            }
            val added = repositoryManager.addRepository(cleanUrl, name, description)
            if (added) {
                _actionMessage.value = "Added custom repository successfully."
            } else {
                _actionMessage.value = "Failed to add repository."
            }
        }
    }

    suspend fun exportJson(): String {
        return repositoryManager.exportRepositoriesJson()
    }

    suspend fun importJson(json: String): Int {
        val count = repositoryManager.importRepositoriesJson(json)
        if (count > 0) {
            _actionMessage.value = "Imported $count repositories from JSON."
        } else {
            _actionMessage.value = "No valid repositories imported."
        }
        return count
    }
}
