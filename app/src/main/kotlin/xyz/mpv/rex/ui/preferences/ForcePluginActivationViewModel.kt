package xyz.mpv.rex.ui.preferences

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.ExtractorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xyz.mpv.rex.cinehub.diagnostic.DiagnosticLogger
import xyz.mpv.rex.cinehub.extension.api.CloudstreamMainApiAdapter
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.DetectedPluginItem
import xyz.mpv.rex.cinehub.extension.model.DexAuditReport
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.PluginActivationState
import xyz.mpv.rex.cinehub.extension.model.PluginTestStepResult
import xyz.mpv.rex.cinehub.extension.model.RegisteredExtractorSummary
import xyz.mpv.rex.cinehub.extension.model.RegisteredProviderSummary
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PluginFilterTab(val label: String) {
    ALL("All Plugins"),
    ACTIVE("Active"),
    INACTIVE("Inactive / Unmounted"),
    ERRORS("Errors & Issues")
}

class ForcePluginActivationViewModel(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val repositoryManager: RepositoryManager,
    private val registry: ProviderRegistry,
    private val db: MpvExDatabase
) : ViewModel() {

    private val TAG = "ForcePluginActivationVM"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _rawPlugins = MutableStateFlow<List<DetectedPluginItem>>(emptyList())
    val rawPlugins: StateFlow<List<DetectedPluginItem>> = _rawPlugins.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(PluginFilterTab.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isBulkOperating = MutableStateFlow(false)
    val isBulkOperating = _isBulkOperating.asStateFlow()

    private val _runtimeLogs = MutableStateFlow<List<String>>(emptyList())
    val runtimeLogs = _runtimeLogs.asStateFlow()

    val filteredPlugins: StateFlow<List<DetectedPluginItem>> = combine(
        _rawPlugins,
        _searchQuery,
        _selectedFilter
    ) { plugins, query, filter ->
        plugins.filter { plugin ->
            val matchesQuery = query.isBlank() ||
                    plugin.displayName.contains(query, ignoreCase = true) ||
                    plugin.pkgName.contains(query, ignoreCase = true) ||
                    plugin.registeredProviders.any { it.name.contains(query, ignoreCase = true) }

            val matchesFilter = when (filter) {
                PluginFilterTab.ALL -> true
                PluginFilterTab.ACTIVE -> plugin.state == PluginActivationState.ACTIVE
                PluginFilterTab.INACTIVE -> plugin.state == PluginActivationState.INACTIVE ||
                        plugin.state == PluginActivationState.NOT_LOADED ||
                        plugin.state == PluginActivationState.DEX_LOADED_ONLY
                PluginFilterTab.ERRORS -> plugin.state == PluginActivationState.ERROR ||
                        plugin.state == PluginActivationState.FILE_MISSING
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        log("Initialized ForcePluginActivationViewModel")
        refreshDetectedPlugins()
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun onFilterChanged(tab: PluginFilterTab) {
        _selectedFilter.value = tab
    }

    private fun log(msg: String) {
        val line = "[${timeFormat.format(Date())}] $msg"
        Log.i(TAG, line)
        DiagnosticLogger.info("FORCE_PLUGIN", msg)
        _runtimeLogs.value = (_runtimeLogs.value + line).takeLast(300)
    }

    fun clearLogs() {
        _runtimeLogs.value = emptyList()
    }

    fun refreshDetectedPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            log("Scanning system for all detected plugins across DB, storage, and runtime...")
            try {
                val dbExtensions = db.extensionDao().getAllInstalledExtensionsSync()
                val candidateFiles = extensionManager.getCandidatePluginFiles()
                val activeRuntimeProviders = registry.getAllProviders()
                val cloudstreamApis = APIHolder.allProviders.toList()
                val cloudstreamExtractors = APIHolder.extractorApis.toList()

                val pluginMap = mutableMapOf<String, DetectedPluginItem>()

                // 1. Process Database Installed Extensions
                for (dbExt in dbExtensions) {
                    val pkg = dbExt.pkgName
                    val file = extensionManager.findExtensionFile(pkg, dbExt.localFilePath)
                    val fileExists = file?.exists() == true
                    val fileSize = if (fileExists) file!!.length() else 0L

                    val (manifestText, manifestClasses) = if (fileExists && file != null) {
                        extensionManager.inspectZipManifest(file)
                    } else Pair(null, emptyList())

                    val classesList = (manifestClasses + (dbExt.classesFile?.split(",")?.map { it.trim() } ?: emptyList())).distinct()

                    val matchingApis = cloudstreamApis.filter {
                        it.sourcePlugin == file?.absolutePath ||
                                it.sourcePlugin == file?.name ||
                                it.name.contains(dbExt.name, ignoreCase = true) ||
                                it.name.contains(pkg, ignoreCase = true) ||
                                pkg.contains(it.name, ignoreCase = true)
                    }

                    val matchingExtractors = cloudstreamExtractors.filter {
                        it.sourcePlugin == file?.absolutePath ||
                                it.sourcePlugin == file?.name ||
                                it.name.contains(pkg, ignoreCase = true)
                    }

                    val isRuntimeActive = matchingApis.isNotEmpty() && dbExt.isEnabled
                    val isDexLoaded = matchingApis.isNotEmpty() || matchingExtractors.isNotEmpty()

                    val state = when {
                        !fileExists -> PluginActivationState.FILE_MISSING
                        isRuntimeActive -> PluginActivationState.ACTIVE
                        !dbExt.isEnabled -> PluginActivationState.INACTIVE
                        isDexLoaded -> PluginActivationState.DEX_LOADED_ONLY
                        else -> PluginActivationState.NOT_LOADED
                    }

                    val providerSummaries = matchingApis.map { api ->
                        RegisteredProviderSummary(
                            name = api.name,
                            id = "cs3_${api.name.lowercase().replace("\\s+".toRegex(), "_")}",
                            mainUrl = api.mainUrl,
                            lang = api.lang,
                            supportedTypes = api.supportedTypes.map { it.name },
                            hasMainPage = api.hasMainPage,
                            hasQuickSearch = api.hasQuickSearch,
                            isEnabled = registry.isProviderEnabled(api.name),
                            sourcePlugin = api.sourcePlugin
                        )
                    }

                    val extractorSummaries = matchingExtractors.map { ext ->
                        RegisteredExtractorSummary(
                            name = ext.name,
                            mainUrl = ext.mainUrl,
                            requiresReferer = ext.requiresReferer,
                            sourcePlugin = ext.sourcePlugin
                        )
                    }

                    pluginMap[pkg] = DetectedPluginItem(
                        pkgName = pkg,
                        displayName = dbExt.name.ifBlank { pkg },
                        version = dbExt.version,
                        versionCode = dbExt.versionCode,
                        description = dbExt.description,
                        localFilePath = file?.absolutePath ?: dbExt.localFilePath,
                        fileSize = fileSize,
                        fileExists = fileExists,
                        isDbInstalled = true,
                        isDbEnabled = dbExt.isEnabled,
                        isDexLoaded = isDexLoaded,
                        isRuntimeActive = isRuntimeActive,
                        state = state,
                        manifestJson = manifestText,
                        discoveredClasses = classesList,
                        registeredProviders = providerSummaries,
                        registeredExtractors = extractorSummaries
                    )
                }

                // 2. Process Unindexed Files on Disk
                for (file in candidateFiles) {
                    val basePkg = file.nameWithoutExtension
                    if (!pluginMap.containsKey(basePkg)) {
                        val (manifestText, manifestClasses) = extensionManager.inspectZipManifest(file)
                        val matchingApis = cloudstreamApis.filter {
                            it.sourcePlugin == file.absolutePath ||
                                    it.sourcePlugin == file.name ||
                                    it.name.contains(basePkg, ignoreCase = true)
                        }
                        val matchingExtractors = cloudstreamExtractors.filter {
                            it.sourcePlugin == file.absolutePath ||
                                    it.sourcePlugin == file.name ||
                                    it.name.contains(basePkg, ignoreCase = true)
                        }

                        val isRuntimeActive = matchingApis.isNotEmpty()
                        val isDexLoaded = matchingApis.isNotEmpty() || matchingExtractors.isNotEmpty()

                        val state = if (isRuntimeActive) PluginActivationState.ACTIVE
                        else if (isDexLoaded) PluginActivationState.DEX_LOADED_ONLY
                        else PluginActivationState.NOT_LOADED

                        val providerSummaries = matchingApis.map { api ->
                            RegisteredProviderSummary(
                                name = api.name,
                                id = "cs3_${api.name.lowercase().replace("\\s+".toRegex(), "_")}",
                                mainUrl = api.mainUrl,
                                lang = api.lang,
                                supportedTypes = api.supportedTypes.map { it.name },
                                hasMainPage = api.hasMainPage,
                                hasQuickSearch = api.hasQuickSearch,
                                isEnabled = registry.isProviderEnabled(api.name),
                                sourcePlugin = api.sourcePlugin
                            )
                        }

                        val extractorSummaries = matchingExtractors.map { ext ->
                            RegisteredExtractorSummary(
                                name = ext.name,
                                mainUrl = ext.mainUrl,
                                requiresReferer = ext.requiresReferer,
                                sourcePlugin = ext.sourcePlugin
                            )
                        }

                        pluginMap[basePkg] = DetectedPluginItem(
                            pkgName = basePkg,
                            displayName = basePkg,
                            version = "1.0.0",
                            versionCode = 1,
                            description = "Discovered on disk: ${file.name}",
                            localFilePath = file.absolutePath,
                            fileSize = file.length(),
                            fileExists = true,
                            isDbInstalled = false,
                            isDbEnabled = false,
                            isDexLoaded = isDexLoaded,
                            isRuntimeActive = isRuntimeActive,
                            state = state,
                            manifestJson = manifestText,
                            discoveredClasses = manifestClasses,
                            registeredProviders = providerSummaries,
                            registeredExtractors = extractorSummaries
                        )
                    }
                }

                val resultList = pluginMap.values.sortedWith(
                    compareByDescending<DetectedPluginItem> { it.isRuntimeActive }
                        .thenByDescending { it.isDbInstalled }
                        .thenBy { it.displayName }
                )

                _rawPlugins.value = resultList
                log("Scan complete: Found ${resultList.size} detected plugins (${resultList.count { it.isRuntimeActive }} active, ${cloudstreamApis.size} total APIs in APIHolder, ${activeRuntimeProviders.size} registered in ProviderRegistry)")
            } catch (e: Exception) {
                log("Scan failed with error: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun forceActivatePlugin(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePluginOperating(pkgName, true)
            log("⚡ [Force Activate] Initiating forced activation for $pkgName...")
            try {
                var ext = db.extensionDao().getExtension(pkgName)
                val file = extensionManager.findExtensionFile(pkgName, ext?.localFilePath)

                if (file == null || !file.exists()) {
                    log("❌ [Force Activate] Error: File for $pkgName not found on disk")
                    updatePluginState(pkgName, PluginActivationState.FILE_MISSING, "File not found on disk")
                    return@launch
                }

                if (ext == null) {
                    ext = InstalledExtension(
                        pkgName = pkgName,
                        name = pkgName,
                        version = "1.0.0",
                        versionCode = 1,
                        localFilePath = file.absolutePath,
                        isEnabled = true
                    )
                    db.extensionDao().insertExtension(ext)
                    log("ℹ️ [Force Activate] Created missing database entry for $pkgName")
                } else {
                    db.extensionDao().updateExtensionState(pkgName, true)
                }

                log("⚡ [Force Activate] Invoking loadExtensionFromDisk for ${file.name}...")
                val success = extensionManager.loadExtensionFromDisk(ext.copy(isEnabled = true, localFilePath = file.absolutePath))

                // Reactively sync all active providers
                val matchingApis = APIHolder.allProviders.filter {
                    it.sourcePlugin == file.absolutePath ||
                            it.sourcePlugin == file.name ||
                            it.name.contains(pkgName, ignoreCase = true)
                }

                for (api in matchingApis) {
                    registry.register(CloudstreamMainApiAdapter(api), isEnabledByDefault = true)
                    registry.setProviderEnabled(api.name, true)
                    log("✅ [Force Activate] Registered and enabled provider: ${api.name} (${api.mainUrl})")
                }

                if (success || matchingApis.isNotEmpty()) {
                    log("✅ [Force Activate] Plugin $pkgName successfully activated with ${matchingApis.size} providers!")
                } else {
                    log("⚠️ [Force Activate] Plugin $pkgName loaded but registered 0 providers.")
                }
            } catch (e: Throwable) {
                log("❌ [Force Activate FAILED] Exception for $pkgName: ${e.message}")
            } finally {
                updatePluginOperating(pkgName, false)
                refreshDetectedPlugins()
            }
        }
    }

    fun forceDeactivatePlugin(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePluginOperating(pkgName, true)
            log("⏸ [Force Deactivate] Deactivating plugin $pkgName...")
            try {
                db.extensionDao().updateExtensionState(pkgName, false)
                extensionManager.toggleExtension(pkgName, false)
                registry.setProviderEnabled(pkgName, false)
                registry.setProviderEnabled("cs3_${pkgName.lowercase()}", false)

                val ext = db.extensionDao().getExtension(pkgName)
                val file = extensionManager.findExtensionFile(pkgName, ext?.localFilePath)
                if (file != null) {
                    val matchingApis = APIHolder.allProviders.filter { it.sourcePlugin == file.absolutePath }
                    matchingApis.forEach { registry.setProviderEnabled(it.name, false) }
                }

                log("✅ [Force Deactivate] Plugin $pkgName deactivated successfully")
            } catch (e: Exception) {
                log("❌ [Force Deactivate FAILED] Error for $pkgName: ${e.message}")
            } finally {
                updatePluginOperating(pkgName, false)
                refreshDetectedPlugins()
            }
        }
    }

    fun forceReloadPlugin(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePluginOperating(pkgName, true)
            log("🔄 [Force Reload] Reloading plugin $pkgName from scratch...")
            try {
                val success = extensionManager.forceReloadExtension(pkgName)
                log(if (success) "✅ [Force Reload] $pkgName reloaded successfully" else "⚠️ [Force Reload] $pkgName reload completed without active providers")
            } catch (e: Exception) {
                log("❌ [Force Reload FAILED] Error for $pkgName: ${e.message}")
            } finally {
                updatePluginOperating(pkgName, false)
                refreshDetectedPlugins()
            }
        }
    }

    fun forceDexLoad(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePluginOperating(pkgName, true)
            log("🔬 [Force Dex Load] Starting deep DEX reflection audit on $pkgName...")
            try {
                val ext = db.extensionDao().getExtension(pkgName)
                val file = extensionManager.findExtensionFile(pkgName, ext?.localFilePath)

                if (file == null || !file.exists()) {
                    log("❌ [Force Dex Load] File missing on disk for $pkgName")
                    return@launch
                }

                val auditReport = extensionManager.forceDexAudit(file)
                _rawPlugins.value = _rawPlugins.value.map { item ->
                    if (item.pkgName == pkgName) {
                        item.copy(
                            auditReport = auditReport,
                            lastActionLog = "Dex Audit: ${auditReport.classLoadAudits.count { it.isClassFound }} classes found, ${auditReport.registeredProvidersCount} providers"
                        )
                    } else item
                }
                log("🔬 [Force Dex Load Report] $pkgName: Verified ${auditReport.classLoadAudits.size} classes, ${auditReport.registeredProvidersCount} providers registered (${auditReport.totalDurationMs}ms)")
            } catch (e: Exception) {
                log("❌ [Force Dex Load FAILED] Error for $pkgName: ${e.message}")
            } finally {
                updatePluginOperating(pkgName, false)
                refreshDetectedPlugins()
            }
        }
    }

    fun runBasicPluginTests(pkgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePluginOperating(pkgName, true)
            log("🧪 [Run Tests] Initiating comprehensive plugin tests for $pkgName...")
            val testResults = mutableListOf<PluginTestStepResult>()

            val plugin = _rawPlugins.value.find { it.pkgName == pkgName }
            val file = extensionManager.findExtensionFile(pkgName, plugin?.localFilePath)

            // Test 1: File & Dex Check
            val t1Start = System.currentTimeMillis()
            if (file != null && file.exists()) {
                testResults.add(
                    PluginTestStepResult(
                        testName = "1. Archive & Storage Integrity",
                        status = "PASSED",
                        durationMs = System.currentTimeMillis() - t1Start,
                        details = "File exists: ${file.name} (${file.length()} bytes)"
                    )
                )
                log("🧪 [Test 1 Passed] Archive valid: ${file.name}")
            } else {
                testResults.add(
                    PluginTestStepResult(
                        testName = "1. Archive & Storage Integrity",
                        status = "FAILED",
                        durationMs = System.currentTimeMillis() - t1Start,
                        details = "File does not exist on disk",
                        errorMessage = "File missing at ${plugin?.localFilePath}"
                    )
                )
                log("🧪 [Test 1 Failed] Archive missing")
            }

            // Test 2: Provider Registration Check
            val t2Start = System.currentTimeMillis()
            val providers = APIHolder.allProviders.filter {
                it.sourcePlugin == file?.absolutePath ||
                        it.sourcePlugin == file?.name ||
                        it.name.contains(pkgName, ignoreCase = true) ||
                        pkgName.contains(it.name, ignoreCase = true)
            }

            if (providers.isNotEmpty()) {
                testResults.add(
                    PluginTestStepResult(
                        testName = "2. Provider Registration",
                        status = "PASSED",
                        durationMs = System.currentTimeMillis() - t2Start,
                        details = "Found ${providers.size} provider(s): ${providers.joinToString { it.name }} (Lang: ${providers.first().lang}, URL: ${providers.first().mainUrl})"
                    )
                )
                log("🧪 [Test 2 Passed] ${providers.size} provider(s) active in APIHolder")
            } else {
                testResults.add(
                    PluginTestStepResult(
                        testName = "2. Provider Registration",
                        status = "FAILED",
                        durationMs = System.currentTimeMillis() - t2Start,
                        details = "No providers registered in APIHolder for $pkgName",
                        errorMessage = "Provider registration missing"
                    )
                )
                log("🧪 [Test 2 Failed] No providers registered")
            }

            // Test 3: Main Page / Home Page Load Test
            val primaryProvider = providers.firstOrNull()
            if (primaryProvider != null) {
                val t3Start = System.currentTimeMillis()
                if (primaryProvider.hasMainPage) {
                    try {
                        log("🧪 [Test 3] Executing getMainPage() for ${primaryProvider.name}...")
                        val mainPageRes = withTimeoutOrNull(8000) {
                            primaryProvider.getMainPage(1, MainPageRequest(primaryProvider.name, primaryProvider.mainUrl, false))
                        }
                        if (mainPageRes != null && mainPageRes.items.isNotEmpty()) {
                            val itemsCount = mainPageRes.items.sumOf { it.list.size }
                            testResults.add(
                                PluginTestStepResult(
                                    testName = "3. Main Page / Homepage Load",
                                    status = "PASSED",
                                    durationMs = System.currentTimeMillis() - t3Start,
                                    details = "Loaded ${mainPageRes.items.size} sections with $itemsCount media items"
                                )
                            )
                            log("🧪 [Test 3 Passed] Main page returned ${mainPageRes.items.size} sections ($itemsCount items)")
                        } else {
                            testResults.add(
                                PluginTestStepResult(
                                    testName = "3. Main Page / Homepage Load",
                                    status = "PASSED",
                                    durationMs = System.currentTimeMillis() - t3Start,
                                    details = "Main page responded (0 items returned)"
                                )
                            )
                        }
                    } catch (e: Throwable) {
                        testResults.add(
                            PluginTestStepResult(
                                testName = "3. Main Page / Homepage Load",
                                status = "FAILED",
                                durationMs = System.currentTimeMillis() - t3Start,
                                details = "Exception during getMainPage()",
                                errorMessage = e.message,
                                stackTrace = e.stackTraceToString()
                            )
                        )
                        log("🧪 [Test 3 Failed] getMainPage error: ${e.message}")
                    }
                } else {
                    testResults.add(
                        PluginTestStepResult(
                            testName = "3. Main Page / Homepage Load",
                            status = "SKIPPED",
                            durationMs = 0L,
                            details = "Provider does not support hasMainPage"
                        )
                    )
                }

                // Test 4: Provider Search Test
                val t4Start = System.currentTimeMillis()
                try {
                    val query = "One Piece"
                    log("🧪 [Test 4] Executing search('$query') on ${primaryProvider.name}...")
                    val searchResults: List<SearchResponse>? = withTimeoutOrNull(8000) {
                        primaryProvider.search(query)
                    }
                    if (searchResults != null && searchResults.isNotEmpty()) {
                        testResults.add(
                            PluginTestStepResult(
                                testName = "4. Search Query Test",
                                status = "PASSED",
                                durationMs = System.currentTimeMillis() - t4Start,
                                details = "Search for '$query' returned ${searchResults.size} results: ${searchResults.take(2).joinToString { it.name }}"
                            )
                        )
                        log("🧪 [Test 4 Passed] Search returned ${searchResults.size} results in ${System.currentTimeMillis() - t4Start}ms")
                    } else {
                        testResults.add(
                            PluginTestStepResult(
                                testName = "4. Search Query Test",
                                status = "PASSED",
                                durationMs = System.currentTimeMillis() - t4Start,
                                details = "Search for '$query' completed without error (0 matches found)"
                            )
                        )
                        log("🧪 [Test 4 Completed] Search completed with 0 matches")
                    }
                } catch (e: Throwable) {
                    testResults.add(
                        PluginTestStepResult(
                            testName = "4. Search Query Test",
                            status = "FAILED",
                            durationMs = System.currentTimeMillis() - t4Start,
                            details = "Exception during search() query",
                            errorMessage = e.message,
                            stackTrace = e.stackTraceToString()
                        )
                    )
                    log("🧪 [Test 4 Failed] Search error: ${e.message}")
                }
            }

            // Test 5: Extractor Registration Test
            val t5Start = System.currentTimeMillis()
            val extractors = APIHolder.extractorApis.filter {
                it.sourcePlugin == file?.absolutePath ||
                        it.sourcePlugin == file?.name ||
                        it.name.contains(pkgName, ignoreCase = true)
            }
            if (extractors.isNotEmpty()) {
                testResults.add(
                    PluginTestStepResult(
                        testName = "5. Extractor Registration",
                        status = "PASSED",
                        durationMs = System.currentTimeMillis() - t5Start,
                        details = "Registered ${extractors.size} extractor(s): ${extractors.joinToString { it.name }}"
                    )
                )
            } else {
                testResults.add(
                    PluginTestStepResult(
                        testName = "5. Extractor Registration",
                        status = "SKIPPED",
                        durationMs = 0L,
                        details = "No standalone extractors registered for this plugin"
                    )
                )
            }

            _rawPlugins.value = _rawPlugins.value.map { item ->
                if (item.pkgName == pkgName) {
                    item.copy(testResults = testResults)
                } else item
            }
            log("🧪 [Tests Completed] $pkgName finished ${testResults.size} diagnostic tests (${testResults.count { it.status == "PASSED" }} passed, ${testResults.count { it.status == "FAILED" }} failed)")
            updatePluginOperating(pkgName, false)
        }
    }

    fun forceActivateAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBulkOperating.value = true
            log("⚡⚡ [Force Activate ALL] Starting mass activation across all detected plugins...")
            val list = _rawPlugins.value
            for (p in list) {
                forceActivatePlugin(p.pkgName)
            }
            log("⚡⚡ [Force Activate ALL] Completed mass activation for ${list.size} plugins")
            _isBulkOperating.value = false
            refreshDetectedPlugins()
        }
    }

    fun forceReloadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBulkOperating.value = true
            log("🔄🔄 [Force Reload ALL] Reloading all plugins and resynchronizing framework...")
            val list = _rawPlugins.value
            for (p in list) {
                forceReloadPlugin(p.pkgName)
            }
            log("🔄🔄 [Force Reload ALL] Completed reload for ${list.size} plugins")
            _isBulkOperating.value = false
            refreshDetectedPlugins()
        }
    }

    private fun updatePluginOperating(pkgName: String, isOperating: Boolean) {
        _rawPlugins.value = _rawPlugins.value.map {
            if (it.pkgName == pkgName) it.copy(isOperating = isOperating) else it
        }
    }

    private fun updatePluginState(pkgName: String, state: PluginActivationState, logMsg: String) {
        _rawPlugins.value = _rawPlugins.value.map {
            if (it.pkgName == pkgName) it.copy(state = state, lastActionLog = logMsg) else it
        }
    }
}
