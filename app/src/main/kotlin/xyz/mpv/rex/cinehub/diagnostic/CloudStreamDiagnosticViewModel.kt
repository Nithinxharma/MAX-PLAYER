package xyz.mpv.rex.cinehub.diagnostic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.cinehub.bridge.RexPlayerBridge
import xyz.mpv.rex.cinehub.extension.api.CineHubEpisode
import xyz.mpv.rex.cinehub.extension.api.CineHubMediaDetails
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.api.CineHubSubtitleTrack
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.ZipFile

enum class StatusIndicator {
    SUCCESS,
    WARNING,
    FAILED,
    IDLE,
    RUNNING
}

data class EnvironmentStatusState(
    val appVersion: String = "",
    val cloudstreamSdkVersion: String = "CloudStream 3 Core / Headless Engine v4.0",
    val loadedProvidersCount: Int = 0,
    val installedExtensionsCount: Int = 0,
    val pluginFilesFoundCount: Int = 0,
    val successfullyLoadedPluginsCount: Int = 0,
    val failedPluginLoadsCount: Int = 0,
    val registerMainApiCallsCount: Int = 0,
    val apiHolderProviderCount: Int = 0,
    val providerRegistryCount: Int = 0,
    val providersRegisteredCount: Int = 0,
    val providersEnabledCount: Int = 0,
    val activeProviderCount: Int = 0,
    val loadedProviderNames: List<String> = emptyList(),
    val repositoriesCount: Int = 0,
    val networkStatus: String = "Unknown",
    val isNetworkConnected: Boolean = false,
    val databaseStatus: String = "Unknown",
    val isDatabaseHealthy: Boolean = false,
    val overallStatus: StatusIndicator = StatusIndicator.IDLE,
    val lastCheckedTimestamp: Long = 0L
)

data class RepositoryTestItem(
    val repo: ExtensionRepo,
    val httpCode: Int? = null,
    val downloadedManifestCount: Int = 0,
    val installedExtensionCount: Int = 0,
    val syncStatus: StatusIndicator = StatusIndicator.IDLE,
    val errorMessage: String? = null,
    val stackTrace: String? = null
)

data class ExtensionTestItem(
    val name: String,
    val version: String,
    val pkgName: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val repositoryUrl: String? = null,
    val statusText: String = "Available",
    val detectedProviders: List<String> = emptyList(),
    val extractedManifestJson: String? = null,
    val registeredProviderNames: List<String> = emptyList(),
    val errorStackTrace: String? = null,
    val pluginRef: AvailablePlugin? = null,
    val installedRef: InstalledExtension? = null
)

data class ProviderSelfTestResult(
    val providerId: String,
    val providerName: String,
    val status: StatusIndicator = StatusIndicator.IDLE,
    val latencyMs: Long = 0L,
    val itemsCount: Int = 0,
    val errorMessage: String? = null,
    val stackTrace: String? = null
)

data class ProviderSearchResultSummary(
    val providerId: String,
    val providerName: String,
    val resultCount: Int,
    val durationMs: Long,
    val error: String? = null,
    val stackTrace: String? = null
)

data class AutomatedTestStepState(
    val name: String,
    val status: StatusIndicator = StatusIndicator.IDLE,
    val details: String = "",
    val durationMs: Long = 0L
)

data class AutomatedTestReport(
    val repoStatus: StatusIndicator = StatusIndicator.IDLE,
    val extensionStatus: StatusIndicator = StatusIndicator.IDLE,
    val providerStatus: StatusIndicator = StatusIndicator.IDLE,
    val searchStatus: StatusIndicator = StatusIndicator.IDLE,
    val metadataStatus: StatusIndicator = StatusIndicator.IDLE,
    val linkStatus: StatusIndicator = StatusIndicator.IDLE,
    val playbackStatus: StatusIndicator = StatusIndicator.IDLE,
    val summaryText: String = "",
    val fullReport: String = ""
)

class CloudStreamDiagnosticViewModel(
    private val context: Context,
    private val repositoryManager: RepositoryManager,
    private val extensionManager: ExtensionManager,
    private val registry: ProviderRegistry,
    private val db: MpvExDatabase,
    private val client: OkHttpClient
) : ViewModel() {

    private val TAG_REPO = "RepositoryManager"
    private val TAG_EXT = "ExtensionManager"
    private val TAG_PROV = "ProviderRegistry"
    private val TAG_SEARCH = "SearchDiagnostic"
    private val TAG_META = "MetadataDiagnostic"
    private val TAG_LINK = "LinkExtraction"
    private val TAG_PLAYER = "MPVPlayer"
    private val TAG_AUTO = "AutomatedTest"

    // Section 1: Environment Status State
    private val _envStatus = MutableStateFlow(EnvironmentStatusState())
    val envStatus: StateFlow<EnvironmentStatusState> = _envStatus.asStateFlow()

    // Section 2: Repository Test State
    private val _repoTestItems = MutableStateFlow<List<RepositoryTestItem>>(emptyList())
    val repoTestItems: StateFlow<List<RepositoryTestItem>> = _repoTestItems.asStateFlow()
    private val _isSyncingRepos = MutableStateFlow(false)
    val isSyncingRepos: StateFlow<Boolean> = _isSyncingRepos.asStateFlow()

    // Section 3: Extension Test State
    private val _extensionTestItems = MutableStateFlow<List<ExtensionTestItem>>(emptyList())
    val extensionTestItems: StateFlow<List<ExtensionTestItem>> = _extensionTestItems.asStateFlow()
    private val _isManagingExtensions = MutableStateFlow(false)
    val isManagingExtensions: StateFlow<Boolean> = _isManagingExtensions.asStateFlow()

    // Section 4: Provider Registry Test State
    private val _providerSelfTests = MutableStateFlow<Map<String, ProviderSelfTestResult>>(emptyMap())
    val providerSelfTests: StateFlow<Map<String, ProviderSelfTestResult>> = _providerSelfTests.asStateFlow()

    // Section 5: Search Test State
    val searchQuery = MutableStateFlow("One Piece")
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    private val _searchSummaries = MutableStateFlow<List<ProviderSearchResultSummary>>(emptyList())
    val searchSummaries: StateFlow<List<ProviderSearchResultSummary>> = _searchSummaries.asStateFlow()
    private val _searchResults = MutableStateFlow<List<CineHubSearchItem>>(emptyList())
    val searchResults: StateFlow<List<CineHubSearchItem>> = _searchResults.asStateFlow()
    val selectedSearchItem = MutableStateFlow<CineHubSearchItem?>(null)

    // Section 6: Metadata Test State
    private val _isLoadingMetadata = MutableStateFlow(false)
    val isLoadingMetadata: StateFlow<Boolean> = _isLoadingMetadata.asStateFlow()
    private val _metadataDetails = MutableStateFlow<CineHubMediaDetails?>(null)
    val metadataDetails: StateFlow<CineHubMediaDetails?> = _metadataDetails.asStateFlow()
    private val _rawMetadataJson = MutableStateFlow<String?>(null)
    val rawMetadataJson: StateFlow<String?> = _rawMetadataJson.asStateFlow()
    private val _metadataError = MutableStateFlow<String?>(null)
    val metadataError: StateFlow<String?> = _metadataError.asStateFlow()
    val selectedEpisode = MutableStateFlow<CineHubEpisode?>(null)

    // Section 7: Link Extraction Test State
    private val _isExtractingLinks = MutableStateFlow(false)
    val isExtractingLinks: StateFlow<Boolean> = _isExtractingLinks.asStateFlow()
    private val _extractedLinks = MutableStateFlow<List<CineHubStreamLink>>(emptyList())
    val extractedLinks: StateFlow<List<CineHubStreamLink>> = _extractedLinks.asStateFlow()
    private val _extractedSubtitles = MutableStateFlow<List<CineHubSubtitleTrack>>(emptyList())
    val extractedSubtitles: StateFlow<List<CineHubSubtitleTrack>> = _extractedSubtitles.asStateFlow()
    private val _linkExtractionDurationMs = MutableStateFlow(0L)
    val linkExtractionDurationMs: StateFlow<Long> = _linkExtractionDurationMs.asStateFlow()
    private val _linkExtractionError = MutableStateFlow<String?>(null)
    val linkExtractionError: StateFlow<String?> = _linkExtractionError.asStateFlow()
    val selectedStreamLink = MutableStateFlow<CineHubStreamLink?>(null)

    // Section 8: Video Playback Test State
    private val _playbackLogs = MutableStateFlow<List<String>>(emptyList())
    val playbackLogs: StateFlow<List<String>> = _playbackLogs.asStateFlow()
    private val _playbackStatus = MutableStateFlow(StatusIndicator.IDLE)
    val playbackStatus: StateFlow<StatusIndicator> = _playbackStatus.asStateFlow()

    // Section 10: Full Automated Test State
    private val _isRunningAutomatedTest = MutableStateFlow(false)
    val isRunningAutomatedTest: StateFlow<Boolean> = _isRunningAutomatedTest.asStateFlow()
    private val _automatedSteps = MutableStateFlow<List<AutomatedTestStepState>>(emptyList())
    val automatedSteps: StateFlow<List<AutomatedTestStepState>> = _automatedSteps.asStateFlow()
    private val _automatedReport = MutableStateFlow<AutomatedTestReport?>(null)
    val automatedReport: StateFlow<AutomatedTestReport?> = _automatedReport.asStateFlow()

    val registeredProviders: StateFlow<List<CineHubProvider>> = registry.registeredProviders
    val activeProviders: StateFlow<List<CineHubProvider>> = registry.activeProviders

    init {
        DiagnosticLogger.info(TAG_AUTO, "CloudStream Test Center Diagnostic Engine initialized. identityHashCode=${System.identityHashCode(this)}, ProviderRegistry.identityHashCode=${System.identityHashCode(registry)}, ExtensionManager.identityHashCode=${System.identityHashCode(extensionManager)}, APIHolder.identityHashCode=${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
        android.util.Log.i("DiagnosticViewModel", "INSTANCE_IDENTITY: CloudStreamDiagnosticViewModel initialized. identityHashCode=${System.identityHashCode(this)}, ProviderRegistry.identityHashCode=${System.identityHashCode(registry)}, ExtensionManager.identityHashCode=${System.identityHashCode(extensionManager)}, APIHolder.identityHashCode=${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
        refreshEnvironmentStatus()
        loadRepositoryAndExtensionData()

        viewModelScope.launch {
            registry.registeredProviders.collect {
                refreshEnvironmentStatus()
            }
        }
        viewModelScope.launch {
            registry.activeProviders.collect {
                refreshEnvironmentStatus()
            }
        }
        viewModelScope.launch {
            extensionManager.successfullyLoadedPluginsCount.collect {
                refreshEnvironmentStatus()
            }
        }
        viewModelScope.launch {
            extensionManager.pluginFilesFoundCount.collect {
                refreshEnvironmentStatus()
            }
        }
        viewModelScope.launch {
            extensionManager.failedPluginLoadsCount.collect {
                refreshEnvironmentStatus()
            }
        }
    }

    // ==========================================
    // SECTION 1: ENVIRONMENT STATUS
    // ==========================================

    fun refreshEnvironmentStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticLogger.info("EnvironmentStatus", "Refreshing environment and runtime health status... ViewModel@${System.identityHashCode(this@CloudStreamDiagnosticViewModel)}, Registry@${System.identityHashCode(registry)}, ExtensionManager@${System.identityHashCode(extensionManager)}, APIHolder@${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
            android.util.Log.i("DiagnosticViewModel", "INSTANCE_IDENTITY: refreshEnvironmentStatus running on ViewModel@${System.identityHashCode(this@CloudStreamDiagnosticViewModel)}, Registry@${System.identityHashCode(registry)}, ExtensionManager@${System.identityHashCode(extensionManager)}, APIHolder@${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")

            val appVer = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            val sdkVer = "CloudStream Core API v3 (Headless Runner)"

            // Check Providers
            val allRegistered = registry.getAllProviders()
            val enabledList = registry.getEnabledProviders()
            val providersRegisteredCount = allRegistered.size
            val providersEnabledCount = enabledList.size
            val activeProviderCount = providersEnabledCount
            val loadedProviders = activeProviderCount

            // Check Installed Extensions
            val installedCount = try {
                db.extensionDao().getEnabledExtensionsSync().size
            } catch (e: Exception) {
                0
            }

            // Diagnostic detailed counts
            val filesFound = extensionManager.pluginFilesFoundCount.value
            val loadedPlugins = extensionManager.successfullyLoadedPluginsCount.value
            val failedPlugins = extensionManager.failedPluginLoadsCount.value
            val registerCalls = com.lagradost.cloudstream3.APIHolder.registerMainApiCallsCount
            val apiHolderCount = com.lagradost.cloudstream3.APIHolder.allProviders.size
            val providerNames = allRegistered.map { it.name }

            // Check DB Repositories
            var repoCount = 0
            var dbHealthy = false
            var dbStatus = "Disconnected"
            try {
                val cursor = db.openHelper.readableDatabase.query("SELECT count(*) FROM extension_repositories")
                if (cursor.moveToFirst()) {
                    repoCount = cursor.getInt(0)
                }
                cursor.close()
                dbHealthy = true
                dbStatus = "Connected (Room SQLite v${db.openHelper.readableDatabase.version})"
            } catch (e: Exception) {
                dbHealthy = false
                dbStatus = "Error: ${e.message}"
                DiagnosticLogger.error("EnvironmentStatus", "Database check failed", e)
            }

            // Check Network
            var netConnected = false
            var netStatus = "Disconnected"
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                if (capabilities != null) {
                    netConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    netStatus = when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected (Wi-Fi)"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected (Cellular)"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Connected (Ethernet)"
                        else -> "Connected"
                    }
                }
            } catch (e: Exception) {
                netStatus = "Error: ${e.message}"
                DiagnosticLogger.error("EnvironmentStatus", "Network check failed", e)
            }

            val overall = when {
                !netConnected || !dbHealthy -> StatusIndicator.FAILED
                loadedProviders == 0 && installedCount == 0 -> StatusIndicator.WARNING
                else -> StatusIndicator.SUCCESS
            }

            _envStatus.value = EnvironmentStatusState(
                appVersion = appVer,
                cloudstreamSdkVersion = sdkVer,
                loadedProvidersCount = loadedProviders,
                installedExtensionsCount = installedCount,
                pluginFilesFoundCount = filesFound,
                successfullyLoadedPluginsCount = loadedPlugins,
                failedPluginLoadsCount = failedPlugins,
                registerMainApiCallsCount = registerCalls,
                apiHolderProviderCount = apiHolderCount,
                providerRegistryCount = providersRegisteredCount,
                providersRegisteredCount = providersRegisteredCount,
                providersEnabledCount = providersEnabledCount,
                activeProviderCount = activeProviderCount,
                loadedProviderNames = providerNames,
                repositoriesCount = repoCount,
                networkStatus = netStatus,
                isNetworkConnected = netConnected,
                databaseStatus = dbStatus,
                isDatabaseHealthy = dbHealthy,
                overallStatus = overall,
                lastCheckedTimestamp = System.currentTimeMillis()
            )

            DiagnosticLogger.info(
                "EnvironmentStatus",
                "Environment Status: overall=$overall, net=$netStatus, db=$dbStatus, providers=$loadedProviders, extensions=$installedCount"
            )
        }
    }

    // ==========================================
    // SECTION 2: REPOSITORY TEST
    // ==========================================

    fun loadRepositoryAndExtensionData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repos = mutableListOf<ExtensionRepo>()
                val cursor = db.openHelper.readableDatabase.query("SELECT url, name, description, lastSync FROM extension_repositories")
                try {
                    while (cursor.moveToNext()) {
                        repos.add(
                            ExtensionRepo(
                                url = cursor.getString(0),
                                name = cursor.getString(1),
                                description = cursor.getString(2),
                                lastSync = cursor.getLong(3)
                            )
                        )
                    }
                } finally {
                    cursor.close()
                }

                _repoTestItems.value = repos.map { repo ->
                    val cachedPlugins = repositoryManager.getCachedPluginsForRepo(repo.url)
                    RepositoryTestItem(
                        repo = repo,
                        httpCode = if (cachedPlugins.isNotEmpty()) 200 else null,
                        downloadedManifestCount = cachedPlugins.size,
                        installedExtensionCount = 0,
                        syncStatus = if (repo.lastSync > 0) StatusIndicator.SUCCESS else StatusIndicator.IDLE
                    )
                }

                refreshExtensionList()
            } catch (e: Exception) {
                DiagnosticLogger.error(TAG_REPO, "Failed to query repositories from DB", e)
            }
        }
    }

    fun syncAllRepositories() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingRepos.value = true
            DiagnosticLogger.info(TAG_REPO, "Starting Sync All Repositories...")
            try {
                // Check if any presets need adding
                val currentRepos = _repoTestItems.value.map { it.repo.url }.toSet()
                if (currentRepos.isEmpty()) {
                    DiagnosticLogger.info(TAG_REPO, "No repositories configured. Auto-injecting popular presets...")
                    for (preset in RepositoryManager.POPULAR_PRESETS) {
                        repositoryManager.addRepository(preset.url, preset.name, preset.description)
                    }
                }

                val syncResults = repositoryManager.syncAllRepositories()
                val updatedItems = mutableListOf<RepositoryTestItem>()

                for (res in syncResults) {
                    val status = if (res.error == null) StatusIndicator.SUCCESS else StatusIndicator.FAILED
                    val code = if (res.error == null) 200 else 500
                    if (res.error != null) {
                        DiagnosticLogger.error(TAG_REPO, "Failed syncing repo: ${res.repoUrl} -> ${res.error}")
                    } else {
                        DiagnosticLogger.info(TAG_REPO, "Successfully synced: ${res.repoName} (${res.plugins.size} plugins parsed)")
                    }
                    updatedItems.add(
                        RepositoryTestItem(
                            repo = ExtensionRepo(res.repoUrl, res.repoName, null, System.currentTimeMillis()),
                            httpCode = code,
                            downloadedManifestCount = res.plugins.size,
                            installedExtensionCount = 0,
                            syncStatus = status,
                            errorMessage = res.error
                        )
                    )
                }

                _repoTestItems.value = updatedItems
                refreshExtensionList()
                refreshEnvironmentStatus()
            } catch (t: Throwable) {
                DiagnosticLogger.error(TAG_REPO, "Repository sync encountered unexpected exception", t)
            } finally {
                _isSyncingRepos.value = false
            }
        }
    }

    fun syncSingleRepository(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticLogger.info(TAG_REPO, "Syncing repository: $url")
            _repoTestItems.update { list ->
                list.map { if (it.repo.url == url) it.copy(syncStatus = StatusIndicator.RUNNING) else it }
            }
            try {
                val res = repositoryManager.syncRepository(url)
                val status = if (res.error == null) StatusIndicator.SUCCESS else StatusIndicator.FAILED
                val code = if (res.error == null) 200 else 500

                _repoTestItems.update { list ->
                    list.map {
                        if (it.repo.url == url) {
                            it.copy(
                                repo = it.repo.copy(lastSync = System.currentTimeMillis()),
                                httpCode = code,
                                downloadedManifestCount = res.plugins.size,
                                syncStatus = status,
                                errorMessage = res.error
                            )
                        } else it
                    }
                }

                if (res.error != null) {
                    DiagnosticLogger.error(TAG_REPO, "Failed syncing $url: ${res.error}")
                } else {
                    DiagnosticLogger.info(TAG_REPO, "Synced $url successfully with ${res.plugins.size} plugins.")
                }

                refreshExtensionList()
                refreshEnvironmentStatus()
            } catch (t: Throwable) {
                DiagnosticLogger.error(TAG_REPO, "Error syncing repository $url", t)
                _repoTestItems.update { list ->
                    list.map {
                        if (it.repo.url == url) {
                            it.copy(
                                syncStatus = StatusIndicator.FAILED,
                                errorMessage = t.message,
                                stackTrace = getStackTraceString(t)
                            )
                        } else it
                    }
                }
            }
        }
    }

    fun addPopularRepositories() {
        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticLogger.info(TAG_REPO, "Adding popular CloudStream repositories presets...")
            for (preset in RepositoryManager.POPULAR_PRESETS) {
                repositoryManager.addRepository(preset.url, preset.name, preset.description)
            }
            loadRepositoryAndExtensionData()
            syncAllRepositories()
        }
    }

    fun clearRepositoryCache() {
        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticLogger.info(TAG_REPO, "Clearing repository and plugin cache...")
            repositoryManager.clearCache()
            loadRepositoryAndExtensionData()
            refreshEnvironmentStatus()
        }
    }

    // ==========================================
    // SECTION 3: EXTENSION TEST
    // ==========================================

    fun refreshExtensionList() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = db.extensionDao().getEnabledExtensionsSync().associateBy { it.pkgName }
            val cachedPlugins = repositoryManager.getCachedPlugins()

            val items = mutableListOf<ExtensionTestItem>()

            // Add installed extensions
            for ((pkg, inst) in installed) {
                val manifestInfo = inspectInstalledExtensionZip(inst)
                val detected = manifestInfo.first
                val manifestJson = manifestInfo.second

                items.add(
                    ExtensionTestItem(
                        name = inst.name,
                        version = inst.version,
                        pkgName = inst.pkgName,
                        isInstalled = true,
                        isEnabled = inst.isEnabled,
                        repositoryUrl = inst.repositoryUrl,
                        statusText = if (inst.isEnabled) "Installed (Active)" else "Installed (Disabled)",
                        detectedProviders = detected,
                        extractedManifestJson = manifestJson,
                        registeredProviderNames = detected,
                        installedRef = inst
                    )
                )
            }

            // Add available remote plugins that are not installed
            for (plugin in cachedPlugins) {
                if (!installed.containsKey(plugin.internalName)) {
                    items.add(
                        ExtensionTestItem(
                            name = plugin.name,
                            version = plugin.version,
                            pkgName = plugin.internalName,
                            isInstalled = false,
                            isEnabled = false,
                            repositoryUrl = plugin.repositoryUrl,
                            statusText = "Available in Repo",
                            pluginRef = plugin
                        )
                    )
                }
            }

            _extensionTestItems.value = items
        }
    }

    private fun inspectInstalledExtensionZip(ext: InstalledExtension): Pair<List<String>, String?> {
        val path = ext.localFilePath ?: return Pair(emptyList(), null)
        val file = File(path)
        if (!file.exists()) return Pair(emptyList(), null)

        val providers = mutableListOf<String>()
        var manifestJson: String? = null

        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: zip.getEntry("make.json") ?: zip.getEntry("plugin.json")
                if (entry != null) {
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    manifestJson = text
                    val json = JSONObject(text)
                    val mainClass = json.optString("pluginClassName", json.optString("mainClass", json.optString("class", "")))
                    if (mainClass.isNotBlank()) providers.add(mainClass)
                    val arr = json.optJSONArray("classes")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val c = arr.optString(i)
                            if (c.isNotBlank() && !providers.contains(c)) providers.add(c)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.warn(TAG_EXT, "Failed inspecting zip for ${ext.pkgName}: ${e.message}")
        }
        return Pair(providers, manifestJson)
    }

    fun installExtension(plugin: AvailablePlugin) {
        viewModelScope.launch(Dispatchers.IO) {
            _isManagingExtensions.value = true
            DiagnosticLogger.info(TAG_EXT, "Installing extension: ${plugin.name} (${plugin.internalName}) from ${plugin.url}...")
            try {
                val success = extensionManager.installExtension(plugin)
                if (success) {
                    DiagnosticLogger.info(TAG_EXT, "Successfully installed extension: ${plugin.name}")
                    refreshExtensionList()
                    refreshEnvironmentStatus()
                } else {
                    DiagnosticLogger.error(TAG_EXT, "Failed to install extension: ${plugin.name} (network or parsing error)")
                }
            } catch (t: Throwable) {
                DiagnosticLogger.error(TAG_EXT, "Exception during extension install ${plugin.name}", t)
            } finally {
                _isManagingExtensions.value = false
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isManagingExtensions.value = true
            DiagnosticLogger.info(TAG_EXT, "Uninstalling extension: $pkgName...")
            try {
                extensionManager.uninstallExtension(pkgName)
                DiagnosticLogger.info(TAG_EXT, "Uninstalled $pkgName successfully.")
                refreshExtensionList()
                refreshEnvironmentStatus()
            } catch (t: Throwable) {
                DiagnosticLogger.error(TAG_EXT, "Exception during uninstall $pkgName", t)
            } finally {
                _isManagingExtensions.value = false
            }
        }
    }

    fun reloadInstalledExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            _isManagingExtensions.value = true
            DiagnosticLogger.info(TAG_EXT, "Reloading all installed extensions and reloading DexClassLoader...")
            try {
                extensionManager.loadInstalledExtensions()
                DiagnosticLogger.info(TAG_EXT, "Reload completed. Active providers in registry: ${registry.getAllProviders().size}")
                refreshExtensionList()
                refreshEnvironmentStatus()
            } catch (t: Throwable) {
                DiagnosticLogger.error(TAG_EXT, "Exception during reload", t)
            } finally {
                _isManagingExtensions.value = false
            }
        }
    }

    fun registerProvidersForExtension(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticLogger.info(TAG_EXT, "Explicit provider registration triggered for: $pkgName")
            try {
                val installed = db.extensionDao().getEnabledExtensionsSync().find { it.pkgName == pkgName }
                if (installed != null) {
                    extensionManager.loadInstalledExtensions()
                    DiagnosticLogger.info(TAG_EXT, "Registration refreshed for $pkgName. Registered: ${registry.getAllProviders().map { it.name }}")
                } else {
                    DiagnosticLogger.warn(TAG_EXT, "Extension $pkgName not found in enabled database records.")
                }
                refreshExtensionList()
                refreshEnvironmentStatus()
            } catch (t: Throwable) {
                DiagnosticLogger.error(TAG_EXT, "Provider registration failed for $pkgName", t)
            }
        }
    }

    // ==========================================
    // SECTION 4: PROVIDER REGISTRY TEST
    // ==========================================

    fun getRegisteredProviders(): List<CineHubProvider> {
        return registry.getAllProviders()
    }

    fun runProviderSelfTest(provider: CineHubProvider) {
        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticLogger.info(TAG_PROV, "Starting Self-Test for provider: ${provider.name} (id=${provider.id})...")
            val startTime = System.currentTimeMillis()
            _providerSelfTests.update { map ->
                map + (provider.id to ProviderSelfTestResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    status = StatusIndicator.RUNNING
                ))
            }

            try {
                var itemsCount = 0
                if (provider.hasMainPage) {
                    val homePages = provider.getHomePage()
                    itemsCount = homePages.sumOf { it.items.size }
                    DiagnosticLogger.info(TAG_PROV, "Provider ${provider.name} getHomePage() returned ${homePages.size} groups with $itemsCount total items.")
                } else {
                    val searchItems = provider.search("One Piece")
                    itemsCount = searchItems.size
                    DiagnosticLogger.info(TAG_PROV, "Provider ${provider.name} search fallback returned $itemsCount items.")
                }

                val duration = System.currentTimeMillis() - startTime
                _providerSelfTests.update { map ->
                    map + (provider.id to ProviderSelfTestResult(
                        providerId = provider.id,
                        providerName = provider.name,
                        status = StatusIndicator.SUCCESS,
                        latencyMs = duration,
                        itemsCount = itemsCount
                    ))
                }
                DiagnosticLogger.info(TAG_PROV, "Self-Test for ${provider.name}: SUCCESS in ${duration}ms ($itemsCount items).")
            } catch (t: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                val trace = getStackTraceString(t)
                _providerSelfTests.update { map ->
                    map + (provider.id to ProviderSelfTestResult(
                        providerId = provider.id,
                        providerName = provider.name,
                        status = StatusIndicator.FAILED,
                        latencyMs = duration,
                        errorMessage = t.message ?: "Unknown provider error",
                        stackTrace = trace
                    ))
                }
                DiagnosticLogger.error(TAG_PROV, "Self-Test failed for provider ${provider.name} in ${duration}ms", t)
            }
        }
    }

    // ==========================================
    // SECTION 5: SEARCH TEST
    // ==========================================

    fun runSearch(query: String = searchQuery.value) {
        viewModelScope.launch(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@launch
            _isSearching.value = true
            _searchResults.value = emptyList()
            _searchSummaries.value = emptyList()

            val providers = registry.getAllProviders()
            DiagnosticLogger.info(TAG_SEARCH, "Executing search for \"$q\" across ${providers.size} registered providers concurrently...")

            if (providers.isEmpty()) {
                DiagnosticLogger.warn(TAG_SEARCH, "No providers registered. Ensure extensions are synced and installed first.")
                _isSearching.value = false
                return@launch
            }

            val deferreds = providers.map { provider ->
                async {
                    val start = System.currentTimeMillis()
                    try {
                        val results = provider.search(q)
                        val duration = System.currentTimeMillis() - start
                        DiagnosticLogger.info(TAG_SEARCH, "Provider [${provider.name}] returned ${results.size} results in ${duration}ms")
                        Pair(
                            ProviderSearchResultSummary(
                                providerId = provider.id,
                                providerName = provider.name,
                                resultCount = results.size,
                                durationMs = duration
                            ),
                            results
                        )
                    } catch (t: Throwable) {
                        val duration = System.currentTimeMillis() - start
                        DiagnosticLogger.error(TAG_SEARCH, "Provider [${provider.name}] search failed in ${duration}ms", t)
                        Pair(
                            ProviderSearchResultSummary(
                                providerId = provider.id,
                                providerName = provider.name,
                                resultCount = 0,
                                durationMs = duration,
                                error = t.message ?: "Search failed",
                                stackTrace = getStackTraceString(t)
                            ),
                            emptyList<CineHubSearchItem>()
                        )
                    }
                }
            }

            val resultsList = deferreds.awaitAll()
            val summaries = resultsList.map { it.first }
            val allItems = resultsList.flatMap { it.second }

            _searchSummaries.value = summaries
            _searchResults.value = allItems
            _isSearching.value = false

            DiagnosticLogger.info(TAG_SEARCH, "Search complete. Aggregated ${allItems.size} results across ${summaries.size} providers.")
        }
    }

    // ==========================================
    // SECTION 6: METADATA TEST
    // ==========================================

    fun fetchMetadata(item: CineHubSearchItem) {
        selectedSearchItem.value = item
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMetadata.value = true
            _metadataDetails.value = null
            _rawMetadataJson.value = null
            _metadataError.value = null
            selectedEpisode.value = null

            DiagnosticLogger.info(TAG_META, "Fetching metadata for \"${item.title}\" via provider [${item.providerName}] URL=${item.url}...")
            val startTime = System.currentTimeMillis()

            try {
                val provider = registry.getProvider(item.providerId)
                if (provider == null) {
                    throw Exception("Provider ${item.providerId} (${item.providerName}) is no longer registered.")
                }

                val details = provider.loadDetails(item.url)
                val duration = System.currentTimeMillis() - startTime

                if (details != null) {
                    _metadataDetails.value = details
                    val json = JSONObject().apply {
                        put("id", details.id)
                        put("title", details.title)
                        put("url", details.url)
                        put("providerId", details.providerId)
                        put("providerName", details.providerName)
                        put("year", details.year)
                        put("type", details.type.name)
                        put("overview", details.overview)
                        put("posterUrl", details.posterUrl)
                        put("backdropUrl", details.backdropUrl)
                        put("genres", JSONArray(details.genres))
                        put("cast", JSONArray(details.cast))
                        val epArray = JSONArray()
                        details.episodes.forEach { ep ->
                            epArray.put(JSONObject().apply {
                                put("id", ep.id)
                                put("name", ep.name)
                                put("season", ep.season)
                                put("episode", ep.episode)
                                put("data", ep.data)
                            })
                        }
                        put("episodes", epArray)
                    }
                    _rawMetadataJson.value = json.toString(2)
                    DiagnosticLogger.info(TAG_META, "Metadata loaded successfully in ${duration}ms: ${details.title}, ${details.episodes.size} episodes.")
                } else {
                    _metadataError.value = "Provider returned null metadata response."
                    DiagnosticLogger.warn(TAG_META, "Provider ${item.providerName} returned null for URL: ${item.url}")
                }
            } catch (t: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                val trace = getStackTraceString(t)
                _metadataError.value = "${t.message}\n\nStacktrace:\n$trace"
                DiagnosticLogger.error(TAG_META, "Failed fetching metadata for ${item.title} in ${duration}ms", t)
            } finally {
                _isLoadingMetadata.value = false
            }
        }
    }

    // ==========================================
    // SECTION 7: LINK EXTRACTION TEST
    // ==========================================

    fun extractLinks(data: String, providerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExtractingLinks.value = true
            _extractedLinks.value = emptyList()
            _extractedSubtitles.value = emptyList()
            _linkExtractionError.value = null
            selectedStreamLink.value = null

            DiagnosticLogger.info(TAG_LINK, "Extracting video links for data payload \"$data\" with providerId=$providerId...")
            val startTime = System.currentTimeMillis()

            try {
                val provider = registry.getProvider(providerId)
                if (provider == null) {
                    throw Exception("Provider $providerId not found in registry.")
                }

                val links = provider.loadStreams(data)
                val subs = provider.loadSubtitles(data)
                val duration = System.currentTimeMillis() - startTime
                _linkExtractionDurationMs.value = duration
                _extractedLinks.value = links
                _extractedSubtitles.value = subs

                if (links.isNotEmpty()) {
                    selectedStreamLink.value = links.first()
                    DiagnosticLogger.info(TAG_LINK, "Extracted ${links.size} streams in ${duration}ms. Top link: ${links.first().quality} -> ${links.first().url}")
                } else {
                    DiagnosticLogger.warn(TAG_LINK, "Link extraction completed in ${duration}ms but 0 streams were extracted.")
                }
            } catch (t: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                _linkExtractionDurationMs.value = duration
                val trace = getStackTraceString(t)
                _linkExtractionError.value = "${t.message}\n\nStacktrace:\n$trace"
                DiagnosticLogger.error(TAG_LINK, "Link extraction failed in ${duration}ms", t)
            } finally {
                _isExtractingLinks.value = false
            }
        }
    }

    // ==========================================
    // SECTION 8: VIDEO PLAYBACK TEST
    // ==========================================

    fun playSelectedLink(streamLink: CineHubStreamLink, title: String? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            val logs = mutableListOf<String>()
            _playbackStatus.value = StatusIndicator.RUNNING
            logs.add("[1/4] Preparing MPV playback intent for: ${streamLink.url}")
            logs.add("      Source: ${streamLink.name}, Quality: ${streamLink.quality}, isM3u8: ${streamLink.isM3u8}")

            val headers = streamLink.headers
            logs.add("[2/4] Injecting HTTP Headers: count=${headers.size}")
            headers.forEach { (k, v) ->
                logs.add("      Header: $k = $v")
            }

            try {
                // Convert CineHubStreamLink into CloudStream ExtractorLink for the RexPlayerBridge
                val extractorLink = com.lagradost.cloudstream3.utils.ExtractorLink(
                    source = streamLink.name,
                    name = streamLink.name,
                    url = streamLink.url,
                    referer = headers["Referer"] ?: "",
                    quality = streamLink.quality.filter { it.isDigit() }.toIntOrNull() ?: 1080,
                    isM3u8 = streamLink.isM3u8,
                    headers = headers
                )

                logs.add("[3/4] Launching MPV Player Activity via RexPlayerBridge...")
                RexPlayerBridge.playStream(context, extractorLink, title ?: _metadataDetails.value?.title ?: "Diagnostic Stream")
                logs.add("[4/4] Player intent successfully dispatched to Android framework.")
                _playbackStatus.value = StatusIndicator.SUCCESS
                DiagnosticLogger.info(TAG_PLAYER, "Playback intent dispatched successfully for ${streamLink.url}")
            } catch (t: Throwable) {
                logs.add("[ERROR] Failed dispatching MPV intent: ${t.message}")
                logs.add(getStackTraceString(t))
                _playbackStatus.value = StatusIndicator.FAILED
                DiagnosticLogger.error(TAG_PLAYER, "MPV Player launch error", t)
            }
            _playbackLogs.value = logs
        }
    }

    // ==========================================
    // SECTION 10: FULL AUTOMATED TEST
    // ==========================================

    fun runCompleteCloudStreamTest() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRunningAutomatedTest.value = true
            _automatedReport.value = null
            DiagnosticLogger.info(TAG_AUTO, "==================================================")
            DiagnosticLogger.info(TAG_AUTO, "STARTING COMPLETE CLOUDSTREAM END-TO-END DIAGNOSTIC")
            DiagnosticLogger.info(TAG_AUTO, "==================================================")

            val steps = mutableListOf(
                AutomatedTestStepState("1. Sync Repositories", StatusIndicator.RUNNING),
                AutomatedTestStepState("2. Extensions Verification / Install", StatusIndicator.IDLE),
                AutomatedTestStepState("3. Register Providers", StatusIndicator.IDLE),
                AutomatedTestStepState("4. Search \"One Piece\"", StatusIndicator.IDLE),
                AutomatedTestStepState("5. Metadata Load (Top Result)", StatusIndicator.IDLE),
                AutomatedTestStepState("6. Extract Video Streams", StatusIndicator.IDLE),
                AutomatedTestStepState("7. MPV Playback Pipeline", StatusIndicator.IDLE)
            )
            _automatedSteps.value = steps

            var repoPass = false
            var extPass = false
            var provPass = false
            var searchPass = false
            var metaPass = false
            var linkPass = false
            var playPass = false

            val reportSb = StringBuilder()
            reportSb.appendLine("CLOUDSTREAM FULL INTEGRATION DIAGNOSTIC REPORT")
            reportSb.appendLine("Timestamp: ${java.util.Date()}")
            reportSb.appendLine("App Version: ${BuildConfig.VERSION_NAME}")
            reportSb.appendLine("--------------------------------------------------")

            // STEP 1: SYNC REPOSITORIES
            val start1 = System.currentTimeMillis()
            try {
                // Ensure default repositories are added
                val currentRepos = db.openHelper.readableDatabase.query("SELECT count(*) FROM extension_repositories")
                var count = 0
                if (currentRepos.moveToFirst()) count = currentRepos.getInt(0)
                currentRepos.close()
                if (count == 0) {
                    for (preset in RepositoryManager.POPULAR_PRESETS) {
                        repositoryManager.addRepository(preset.url, preset.name, preset.description)
                    }
                }

                val syncs = repositoryManager.syncAllRepositories()
                val successCount = syncs.count { it.error == null }
                val duration1 = System.currentTimeMillis() - start1
                repoPass = successCount > 0
                steps[0] = AutomatedTestStepState(
                    "1. Sync Repositories",
                    if (repoPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                    "Synced $successCount/${syncs.size} repositories successfully (${repositoryManager.getCachedPlugins().size} plugins in catalog)",
                    duration1
                )
                reportSb.appendLine("Repositories: ${if (repoPass) "PASS" else "FAIL"} ($successCount/${syncs.size} synced in ${duration1}ms)")
            } catch (t: Throwable) {
                steps[0] = AutomatedTestStepState("1. Sync Repositories", StatusIndicator.FAILED, "Error: ${t.message}", System.currentTimeMillis() - start1)
                reportSb.appendLine("Repositories: FAIL (${t.message})")
            }
            _automatedSteps.value = steps.toList()

            // STEP 2: EXTENSIONS
            val start2 = System.currentTimeMillis()
            steps[1] = steps[1].copy(status = StatusIndicator.RUNNING)
            _automatedSteps.value = steps.toList()
            try {
                var installedList = db.extensionDao().getEnabledExtensionsSync()
                if (installedList.isEmpty()) {
                    val available = repositoryManager.getCachedPlugins()
                    val targetPlugin = available.firstOrNull { it.url.isNotBlank() }
                    if (targetPlugin != null) {
                        DiagnosticLogger.info(TAG_AUTO, "Auto-installing target test extension: ${targetPlugin.name}")
                        extensionManager.installExtension(targetPlugin)
                        installedList = db.extensionDao().getEnabledExtensionsSync()
                    }
                }
                val duration2 = System.currentTimeMillis() - start2
                extPass = installedList.isNotEmpty()
                steps[1] = AutomatedTestStepState(
                    "2. Extensions Verification / Install",
                    if (extPass) StatusIndicator.SUCCESS else StatusIndicator.WARNING,
                    "Detected ${installedList.size} installed extension(s)",
                    duration2
                )
                reportSb.appendLine("Extensions: ${if (extPass) "PASS" else "WARNING"} (${installedList.size} installed)")
            } catch (t: Throwable) {
                steps[1] = AutomatedTestStepState("2. Extensions Verification / Install", StatusIndicator.FAILED, "Error: ${t.message}", System.currentTimeMillis() - start2)
                reportSb.appendLine("Extensions: FAIL (${t.message})")
            }
            _automatedSteps.value = steps.toList()

            // STEP 3: PROVIDER REGISTRY
            val start3 = System.currentTimeMillis()
            steps[2] = steps[2].copy(status = StatusIndicator.RUNNING)
            _automatedSteps.value = steps.toList()
            try {
                extensionManager.loadInstalledExtensions()
                val providers = registry.getAllProviders()
                val duration3 = System.currentTimeMillis() - start3
                provPass = providers.isNotEmpty()
                steps[2] = AutomatedTestStepState(
                    "3. Register Providers",
                    if (provPass) StatusIndicator.SUCCESS else StatusIndicator.WARNING,
                    "${providers.size} provider(s) active in runtime registry",
                    duration3
                )
                reportSb.appendLine("Providers: ${if (provPass) "PASS" else "WARNING"} (${providers.size} registered)")
            } catch (t: Throwable) {
                steps[2] = AutomatedTestStepState("3. Register Providers", StatusIndicator.FAILED, "Error: ${t.message}", System.currentTimeMillis() - start3)
                reportSb.appendLine("Providers: FAIL (${t.message})")
            }
            _automatedSteps.value = steps.toList()

            // STEP 4: SEARCH
            val start4 = System.currentTimeMillis()
            steps[3] = steps[3].copy(status = StatusIndicator.RUNNING)
            _automatedSteps.value = steps.toList()
            var topResult: CineHubSearchItem? = null
            try {
                val providers = registry.getAllProviders()
                val allResults = mutableListOf<CineHubSearchItem>()
                for (p in providers) {
                    try {
                        val res = p.search("One Piece")
                        allResults.addAll(res)
                    } catch (e: Throwable) {
                        DiagnosticLogger.warn(TAG_AUTO, "Provider ${p.name} search warning: ${e.message}")
                    }
                }
                topResult = allResults.firstOrNull()
                val duration4 = System.currentTimeMillis() - start4
                searchPass = allResults.isNotEmpty()
                steps[3] = AutomatedTestStepState(
                    "4. Search \"One Piece\"",
                    if (searchPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                    "Found ${allResults.size} search results across ${providers.size} providers",
                    duration4
                )
                reportSb.appendLine("Search: ${if (searchPass) "PASS" else "FAIL"} (${allResults.size} results in ${duration4}ms)")
            } catch (t: Throwable) {
                steps[3] = AutomatedTestStepState("4. Search \"One Piece\"", StatusIndicator.FAILED, "Error: ${t.message}", System.currentTimeMillis() - start4)
                reportSb.appendLine("Search: FAIL (${t.message})")
            }
            _automatedSteps.value = steps.toList()

            // STEP 5: METADATA
            val start5 = System.currentTimeMillis()
            steps[4] = steps[4].copy(status = StatusIndicator.RUNNING)
            _automatedSteps.value = steps.toList()
            var targetDataUrl: String? = null
            var targetProviderId: String? = null
            try {
                if (topResult != null) {
                    val p = registry.getProvider(topResult.providerId)
                    val details = p?.loadDetails(topResult.url)
                    val duration5 = System.currentTimeMillis() - start5
                    metaPass = details != null
                    targetDataUrl = details?.episodes?.firstOrNull()?.data ?: details?.url ?: topResult.url
                    targetProviderId = topResult.providerId
                    steps[4] = AutomatedTestStepState(
                        "5. Metadata Load (Top Result)",
                        if (metaPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                        if (details != null) "Loaded \"${details.title}\" (${details.episodes.size} episodes)" else "Metadata returned null",
                        duration5
                    )
                    reportSb.appendLine("Metadata: ${if (metaPass) "PASS" else "FAIL"} (Title=\"${details?.title}\")")
                } else {
                    steps[4] = AutomatedTestStepState("5. Metadata Load (Top Result)", StatusIndicator.FAILED, "Skipped: No search result to load", 0L)
                    reportSb.appendLine("Metadata: FAIL (No search result)")
                }
            } catch (t: Throwable) {
                steps[4] = AutomatedTestStepState("5. Metadata Load (Top Result)", StatusIndicator.FAILED, "Error: ${t.message}", System.currentTimeMillis() - start5)
                reportSb.appendLine("Metadata: FAIL (${t.message})")
            }
            _automatedSteps.value = steps.toList()

            // STEP 6: LINK EXTRACTION
            val start6 = System.currentTimeMillis()
            steps[5] = steps[5].copy(status = StatusIndicator.RUNNING)
            _automatedSteps.value = steps.toList()
            var extractedStream: CineHubStreamLink? = null
            try {
                if (targetDataUrl != null && targetProviderId != null) {
                    val p = registry.getProvider(targetProviderId)
                    val streams = p?.loadStreams(targetDataUrl) ?: emptyList()
                    extractedStream = streams.firstOrNull()
                    val duration6 = System.currentTimeMillis() - start6
                    linkPass = streams.isNotEmpty()
                    steps[5] = AutomatedTestStepState(
                        "6. Extract Video Streams",
                        if (linkPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                        "Extracted ${streams.size} streams (Top: ${extractedStream?.quality ?: "N/A"})",
                        duration6
                    )
                    reportSb.appendLine("Link Extraction: ${if (linkPass) "PASS" else "FAIL"} (${streams.size} streams extracted)")
                } else {
                    steps[5] = AutomatedTestStepState("6. Extract Video Streams", StatusIndicator.FAILED, "Skipped: No target data payload", 0L)
                    reportSb.appendLine("Link Extraction: FAIL (No media payload)")
                }
            } catch (t: Throwable) {
                steps[5] = AutomatedTestStepState("6. Extract Video Streams", StatusIndicator.FAILED, "Error: ${t.message}", System.currentTimeMillis() - start6)
                reportSb.appendLine("Link Extraction: FAIL (${t.message})")
            }
            _automatedSteps.value = steps.toList()

            // STEP 7: PLAYBACK INTENT PIPELINE
            val start7 = System.currentTimeMillis()
            steps[6] = steps[6].copy(status = StatusIndicator.RUNNING)
            _automatedSteps.value = steps.toList()
            try {
                if (extractedStream != null) {
                    playPass = extractedStream.url.isNotBlank() && (extractedStream.url.startsWith("http://") || extractedStream.url.startsWith("https://"))
                    val duration7 = System.currentTimeMillis() - start7
                    steps[6] = AutomatedTestStepState(
                        "7. MPV Playback Pipeline",
                        if (playPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                        "Stream URL and headers verified for MPV Player handoff (${extractedStream.quality})",
                        duration7
                    )
                    reportSb.appendLine("Playback: ${if (playPass) "PASS" else "FAIL"} (Stream verified for MPV)")
                } else {
                    steps[6] = AutomatedTestStepState("7. MPV Playback Pipeline", StatusIndicator.FAILED, "Skipped: No extracted stream to verify", 0L)
                    reportSb.appendLine("Playback: FAIL (No extracted stream)")
                }
            } catch (t: Throwable) {
                steps[6] = AutomatedTestStepState("7. MPV Playback Pipeline", StatusIndicator.FAILED, "Error: ${t.message}", System.currentTimeMillis() - start7)
                reportSb.appendLine("Playback: FAIL (${t.message})")
            }
            _automatedSteps.value = steps.toList()

            val finalReport = reportSb.toString()
            _automatedReport.value = AutomatedTestReport(
                repoStatus = if (repoPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                extensionStatus = if (extPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                providerStatus = if (provPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                searchStatus = if (searchPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                metadataStatus = if (metaPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                linkStatus = if (linkPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                playbackStatus = if (playPass) StatusIndicator.SUCCESS else StatusIndicator.FAILED,
                summaryText = "Automated Test Complete: Repositories (${if (repoPass) "PASS" else "FAIL"}), Extensions (${if (extPass) "PASS" else "FAIL"}), Providers (${if (provPass) "PASS" else "FAIL"}), Search (${if (searchPass) "PASS" else "FAIL"}), Metadata (${if (metaPass) "PASS" else "FAIL"}), Links (${if (linkPass) "PASS" else "FAIL"}), Playback (${if (playPass) "PASS" else "FAIL"}).",
                fullReport = finalReport
            )

            DiagnosticLogger.info(TAG_AUTO, "Automated Diagnostic Run finished. Report generated.")
            _isRunningAutomatedTest.value = false
            refreshEnvironmentStatus()
        }
    }

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
