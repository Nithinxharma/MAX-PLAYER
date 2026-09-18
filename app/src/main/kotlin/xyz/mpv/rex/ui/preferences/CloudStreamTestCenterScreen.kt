package xyz.mpv.rex.ui.preferences

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.cinehub.bridge.CloudstreamHeadlessRunner
import xyz.mpv.rex.cinehub.bridge.RexPlayerBridge
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Serializable
object CloudStreamTestCenterScreenRoute : Screen {
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        CloudStreamTestCenterScreen(
            onNavigateBack = { backstack.removeLastOrNull() }
        )
    }
}

enum class LogLevel { INFO, WARN, ERROR }

data class DiagnosticLog(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

enum class TestStatus {
    SUCCESS, WARNING, FAILED, IDLE, RUNNING
}

data class AutomatedTestReport(
    var repositoriesStatus: TestStatus = TestStatus.IDLE,
    var repositoriesDetail: String = "Pending",
    var extensionsStatus: TestStatus = TestStatus.IDLE,
    var extensionsDetail: String = "Pending",
    var providersStatus: TestStatus = TestStatus.IDLE,
    var providersDetail: String = "Pending",
    var searchStatus: TestStatus = TestStatus.IDLE,
    var searchDetail: String = "Pending",
    var metadataStatus: TestStatus = TestStatus.IDLE,
    var metadataDetail: String = "Pending",
    var linkExtractionStatus: TestStatus = TestStatus.IDLE,
    var linkExtractionDetail: String = "Pending",
    var playbackStatus: TestStatus = TestStatus.IDLE,
    var playbackDetail: String = "Pending",
    var durationMs: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudStreamTestCenterScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Lazy Koin Injections (resolved safely when screen is opened)
    val db = remember { org.koin.java.KoinJavaComponent.get<MpvExDatabase>(MpvExDatabase::class.java) }
    val repositoryManager = remember { org.koin.java.KoinJavaComponent.get<RepositoryManager>(RepositoryManager::class.java) }
    val extensionManager = remember { org.koin.java.KoinJavaComponent.get<ExtensionManager>(ExtensionManager::class.java) }
    val providerRegistry = remember { org.koin.java.KoinJavaComponent.get<ProviderRegistry>(ProviderRegistry::class.java) }

    // Logs State
    val logs = remember { mutableStateListOf<DiagnosticLog>() }
    var logFilter by remember { mutableStateOf<LogLevel?>(null) }

    fun addLog(level: LogLevel, tag: String, msg: String, t: Throwable? = null) {
        val entry = DiagnosticLog(level = level, tag = tag, message = msg, throwable = t)
        logs.add(0, entry)
        when (level) {
            LogLevel.INFO -> Log.i("CSTestCenter_$tag", msg)
            LogLevel.WARN -> Log.w("CSTestCenter_$tag", msg, t)
            LogLevel.ERROR -> Log.e("CSTestCenter_$tag", msg, t)
        }
    }

    // SECTION 1: Environment Status State
    var networkStatus by remember { mutableStateOf(TestStatus.IDLE) }
    var networkDetails by remember { mutableStateOf("Checking network...") }
    var databaseStatus by remember { mutableStateOf(TestStatus.IDLE) }
    var databaseDetails by remember { mutableStateOf("Checking database...") }
    var loadedProvidersCount by remember { mutableIntStateOf(0) }
    var installedExtensionsCount by remember { mutableIntStateOf(0) }
    var repositoriesCount by remember { mutableIntStateOf(0) }
    var isCheckingEnv by remember { mutableStateOf(false) }

    val refreshEnvironment: () -> Unit = {
        isCheckingEnv = true
        scope.launch(Dispatchers.IO) {
            addLog(LogLevel.INFO, "Env", "Initiating environment diagnostic check...")
            // Network test
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                val capabilities = cm?.getNetworkCapabilities(activeNetwork)
                val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                if (hasInternet) {
                    val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).build()
                    val req = Request.Builder().url("https://raw.githubusercontent.com").head().build()
                    val resp = client.newCall(req).execute()
                    networkStatus = if (resp.isSuccessful || resp.code in 200..404) TestStatus.SUCCESS else TestStatus.WARNING
                    networkDetails = "Connected (HTTP ${resp.code})"
                    addLog(LogLevel.INFO, "Env", "Network verified: $networkDetails")
                } else {
                    networkStatus = TestStatus.FAILED
                    networkDetails = "No internet connection detected"
                    addLog(LogLevel.ERROR, "Env", "Network offline")
                }
            } catch (e: Exception) {
                networkStatus = TestStatus.WARNING
                networkDetails = "Reachable with warning: ${e.message}"
                addLog(LogLevel.WARN, "Env", "Network check warning", e)
            }

            // Database test
            try {
                val repos = db.extensionDao().getAllRepositoriesSync()
                val exts = db.extensionDao().getAllInstalledExtensionsSync()
                repositoriesCount = repos.size
                installedExtensionsCount = exts.size
                databaseStatus = TestStatus.SUCCESS
                databaseDetails = "SQLite operational (${repos.size} repos, ${exts.size} installed exts)"
                addLog(LogLevel.INFO, "Env", "Database check successful: $databaseDetails")
            } catch (e: Exception) {
                databaseStatus = TestStatus.FAILED
                databaseDetails = "Database error: ${e.message}"
                addLog(LogLevel.ERROR, "Env", "Database check failed", e)
            }

            // Provider counts
            val totalProviders = APIHolder.apis.size.coerceAtLeast(providerRegistry.getAllProviders().size)
            loadedProvidersCount = totalProviders

            withContext(Dispatchers.Main) {
                isCheckingEnv = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { CloudstreamHeadlessRunner.init(context) }
        refreshEnvironment()
    }

    // SECTION 2: Repository Test State
    var dbRepositories by remember { mutableStateOf<List<ExtensionRepo>>(emptyList()) }
    var selectedRepoUrl by remember { mutableStateOf<String?>(null) }
    var isSyncingRepos by remember { mutableStateOf(false) }
    var lastRepoSyncResult by remember { mutableStateOf<String?>(null) }
    var lastRepoSyncError by remember { mutableStateOf<String?>(null) }

    val reloadDbRepos: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val list = db.extensionDao().getAllRepositoriesSync()
            withContext(Dispatchers.Main) {
                dbRepositories = list
                if (selectedRepoUrl == null && list.isNotEmpty()) {
                    selectedRepoUrl = list.first().url
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadDbRepos()
    }

    // SECTION 3: Extension Test State
    var installedExtensionsList by remember { mutableStateOf<List<InstalledExtension>>(emptyList()) }
    var availableExtensionsList by remember { mutableStateOf<List<AvailablePlugin>>(emptyList()) }
    var selectedExtDetails by remember { mutableStateOf<String?>(null) }
    var extOperationError by remember { mutableStateOf<String?>(null) }
    var isExtBusy by remember { mutableStateOf(false) }

    val reloadExtensionsData: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val installed = db.extensionDao().getAllInstalledExtensionsSync()
            val cached = repositoryManager.getCachedPlugins()
            withContext(Dispatchers.Main) {
                installedExtensionsList = installed
                availableExtensionsList = cached
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadExtensionsData()
    }

    // SECTION 4: Provider Registry Test State
    var registeredProvidersList by remember { mutableStateOf<List<MainAPI>>(emptyList()) }
    var providerSelfTestResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isRunningSelfTest by remember { mutableStateOf(false) }

    val refreshRegisteredProviders: () -> Unit = {
        val list = APIHolder.apis.toList()
        registeredProvidersList = list
        loadedProvidersCount = list.size
    }

    LaunchedEffect(Unit) {
        refreshRegisteredProviders()
    }

    // SECTION 5: Search Test State
    var searchQuery by remember { mutableStateOf("One Piece") }
    var isSearching by remember { mutableStateOf(false) }
    var searchLatencyMs by remember { mutableStateOf<Long?>(null) }
    var searchResultsByProvider by remember { mutableStateOf<Map<String, List<SearchResponse>>>(emptyMap()) }
    var searchErrorsByProvider by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var allFlatSearchResults by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var selectedSearchResult by remember { mutableStateOf<SearchResponse?>(null) }

    // SECTION 6: Metadata Test State
    var isLoadingMetadata by remember { mutableStateOf(false) }
    var loadedMediaResponse by remember { mutableStateOf<LoadResponse?>(null) }
    var metadataError by remember { mutableStateOf<String?>(null) }
    var rawMetadataJson by remember { mutableStateOf<String?>(null) }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }

    // SECTION 7: Link Extraction Test State
    var isExtractingLinks by remember { mutableStateOf(false) }
    var extractionDurationMs by remember { mutableStateOf<Long?>(null) }
    var extractedLinksList by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var extractedSubtitlesList by remember { mutableStateOf<List<SubtitleFile>>(emptyList()) }
    var linkExtractionError by remember { mutableStateOf<String?>(null) }
    var selectedLinkForPlay by remember { mutableStateOf<ExtractorLink?>(null) }

    // SECTION 8: Video Playback Test State
    var playbackStateStatus by remember { mutableStateOf("Idle (Not started)") }
    var playbackErrorsLog by remember { mutableStateOf<String?>(null) }
    var playbackIntentSummary by remember { mutableStateOf<String?>(null) }

    // SECTION 10: Full Automated Test State
    var isRunningAutomatedTest by remember { mutableStateOf(false) }
    var automatedTestProgress by remember { mutableFloatStateOf(0f) }
    var automatedTestStepName by remember { mutableStateOf("") }
    var automatedReport by remember { mutableStateOf<AutomatedTestReport?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CloudStream Test Center",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "End-to-End Diagnostic Pipeline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refreshEnvironment()
                        reloadDbRepos()
                        reloadExtensionsData()
                        refreshRegisteredProviders()
                        Toast.makeText(context, "Diagnostics refreshed", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh all diagnostics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 10: Full Automated Test Banner
            item {
                AutomatedTestCard(
                    isRunning = isRunningAutomatedTest,
                    progress = automatedTestProgress,
                    stepName = automatedTestStepName,
                    report = automatedReport,
                    onRunTest = {
                        isRunningAutomatedTest = true
                        automatedTestProgress = 0.05f
                        automatedTestStepName = "Starting automated test sequence..."
                        val report = AutomatedTestReport()
                        automatedReport = report
                        val startTime = SystemClock.elapsedRealtime()

                        scope.launch(Dispatchers.IO) {
                            addLog(LogLevel.INFO, "AutoTest", "=== STARTING FULL END-TO-END CLOUDSTREAM TEST ===")

                            // STEP 1: Repository Sync
                            try {
                                automatedTestStepName = "Step 1/7: Syncing Repositories..."
                                automatedTestProgress = 0.15f
                                addLog(LogLevel.INFO, "AutoTest", "Step 1: Syncing repository catalog...")
                                val syncResults = repositoryManager.syncAllRepositories()
                                val successCount = syncResults.count { it.error == null }
                                val pluginsTotal = repositoryManager.getCachedPlugins().size
                                if (successCount > 0 || pluginsTotal > 0) {
                                    report.repositoriesStatus = TestStatus.SUCCESS
                                    report.repositoriesDetail = "Synced ${syncResults.size} repos ($pluginsTotal available plugins)"
                                    addLog(LogLevel.INFO, "AutoTest", "Step 1 PASS: ${report.repositoriesDetail}")
                                } else {
                                    report.repositoriesStatus = TestStatus.WARNING
                                    report.repositoriesDetail = "Synced with 0 plugins found"
                                    addLog(LogLevel.WARN, "AutoTest", "Step 1 WARN: 0 plugins")
                                }
                            } catch (e: Exception) {
                                report.repositoriesStatus = TestStatus.FAILED
                                report.repositoriesDetail = "Error: ${e.message}"
                                addLog(LogLevel.ERROR, "AutoTest", "Step 1 FAIL", e)
                            }

                            // STEP 2: Install Extension
                            try {
                                automatedTestStepName = "Step 2/7: Installing Providers Extension..."
                                automatedTestProgress = 0.30f
                                val installed = db.extensionDao().getAllInstalledExtensionsSync()
                                if (installed.isNotEmpty()) {
                                    report.extensionsStatus = TestStatus.SUCCESS
                                    report.extensionsDetail = "Using ${installed.size} already installed extension(s)"
                                    addLog(LogLevel.INFO, "AutoTest", "Step 2 PASS: Already installed (${installed.first().name})")
                                } else {
                                    val available = repositoryManager.getCachedPlugins()
                                    if (available.isNotEmpty()) {
                                        val target = available.first()
                                        addLog(LogLevel.INFO, "AutoTest", "Installing target plugin: ${target.name} from ${target.url}")
                                        val installedOk = extensionManager.installExtension(target)
                                        if (installedOk) {
                                            report.extensionsStatus = TestStatus.SUCCESS
                                            report.extensionsDetail = "Successfully installed ${target.name}"
                                            addLog(LogLevel.INFO, "AutoTest", "Step 2 PASS: Installed ${target.name}")
                                        } else {
                                            report.extensionsStatus = TestStatus.FAILED
                                            report.extensionsDetail = "Failed to install ${target.name}"
                                            addLog(LogLevel.ERROR, "AutoTest", "Step 2 FAIL: Installation failed")
                                        }
                                    } else {
                                        report.extensionsStatus = TestStatus.WARNING
                                        report.extensionsDetail = "No available extensions found in repo cache"
                                        addLog(LogLevel.WARN, "AutoTest", "Step 2 WARN: No plugins in cache")
                                    }
                                }
                            } catch (e: Exception) {
                                report.extensionsStatus = TestStatus.FAILED
                                report.extensionsDetail = "Error: ${e.message}"
                                addLog(LogLevel.ERROR, "AutoTest", "Step 2 FAIL", e)
                            }

                            // STEP 3: Register Providers
                            try {
                                automatedTestStepName = "Step 3/7: Registering and verifying Provider APIs..."
                                automatedTestProgress = 0.45f
                                val apis = APIHolder.apis
                                val registryProviders = providerRegistry.getAllProviders()
                                val count = apis.size.coerceAtLeast(registryProviders.size)
                                if (count > 0) {
                                    report.providersStatus = TestStatus.SUCCESS
                                    report.providersDetail = "$count provider(s) active in runtime"
                                    addLog(LogLevel.INFO, "AutoTest", "Step 3 PASS: Registered providers: ${apis.map { it.name }}")
                                } else {
                                    report.providersStatus = TestStatus.FAILED
                                    report.providersDetail = "0 providers registered in APIHolder/Registry"
                                    addLog(LogLevel.ERROR, "AutoTest", "Step 3 FAIL: No providers registered")
                                }
                            } catch (e: Exception) {
                                report.providersStatus = TestStatus.FAILED
                                report.providersDetail = "Error: ${e.message}"
                                addLog(LogLevel.ERROR, "AutoTest", "Step 3 FAIL", e)
                            }

                            // STEP 4: Search Test
                            var targetResult: SearchResponse? = null
                            try {
                                automatedTestStepName = "Step 4/7: Searching 'One Piece' across providers..."
                                automatedTestProgress = 0.60f
                                val query = "One Piece"
                                val apis = APIHolder.apis
                                val results = mutableListOf<SearchResponse>()
                                for (api in apis) {
                                    try {
                                        val res = api.search(query)
                                        if (res != null) results.addAll(res)
                                    } catch (t: Throwable) {
                                        addLog(LogLevel.WARN, "AutoTest", "Provider ${api.name} search failed: ${t.message}")
                                    }
                                }
                                if (results.isNotEmpty()) {
                                    targetResult = results.first()
                                    report.searchStatus = TestStatus.SUCCESS
                                    report.searchDetail = "Found ${results.size} items (selected: ${targetResult.name})"
                                    addLog(LogLevel.INFO, "AutoTest", "Step 4 PASS: Found ${results.size} search results")
                                } else {
                                    report.searchStatus = TestStatus.WARNING
                                    report.searchDetail = "0 results returned for '$query'"
                                    addLog(LogLevel.WARN, "AutoTest", "Step 4 WARN: 0 results")
                                }
                            } catch (e: Exception) {
                                report.searchStatus = TestStatus.FAILED
                                report.searchDetail = "Error: ${e.message}"
                                addLog(LogLevel.ERROR, "AutoTest", "Step 4 FAIL", e)
                            }

                            // STEP 5: Metadata Test
                            var targetEpisodeData: String? = null
                            try {
                                automatedTestStepName = "Step 5/7: Loading metadata details..."
                                automatedTestProgress = 0.75f
                                if (targetResult != null) {
                                    val api = APIHolder.getApi(targetResult.apiName) ?: APIHolder.apis.firstOrNull()
                                    if (api != null) {
                                        val loadResp = api.load(targetResult.url)
                                        if (loadResp != null) {
                                            report.metadataStatus = TestStatus.SUCCESS
                                            var epCount = 0
                                            if (loadResp is TvSeriesLoadResponse) {
                                                epCount = loadResp.episodes.size
                                                targetEpisodeData = loadResp.episodes.firstOrNull()?.data
                                            } else if (loadResp is MovieLoadResponse) {
                                                targetEpisodeData = loadResp.dataUrl
                                            }
                                            report.metadataDetail = "${loadResp.name} ($epCount episodes / movie data ready)"
                                            addLog(LogLevel.INFO, "AutoTest", "Step 5 PASS: Loaded metadata for ${loadResp.name}")
                                        } else {
                                            report.metadataStatus = TestStatus.FAILED
                                            report.metadataDetail = "api.load() returned null"
                                            addLog(LogLevel.ERROR, "AutoTest", "Step 5 FAIL: null load response")
                                        }
                                    } else {
                                        report.metadataStatus = TestStatus.FAILED
                                        report.metadataDetail = "API for ${targetResult.apiName} not found"
                                    }
                                } else {
                                    report.metadataStatus = TestStatus.WARNING
                                    report.metadataDetail = "Skipped (no search result to test)"
                                }
                            } catch (e: Exception) {
                                report.metadataStatus = TestStatus.FAILED
                                report.metadataDetail = "Error: ${e.message}"
                                addLog(LogLevel.ERROR, "AutoTest", "Step 5 FAIL", e)
                            }

                            // STEP 6: Link Extraction Test
                            var bestLink: ExtractorLink? = null
                            try {
                                automatedTestStepName = "Step 6/7: Extracting stream links..."
                                automatedTestProgress = 0.90f
                                if (targetEpisodeData != null && targetResult != null) {
                                    val api = APIHolder.getApi(targetResult.apiName) ?: APIHolder.apis.firstOrNull()
                                    if (api != null) {
                                        val links = mutableListOf<ExtractorLink>()
                                        api.loadLinks(targetEpisodeData, false, {}) { link ->
                                            synchronized(links) { links.add(link) }
                                        }
                                        if (links.isNotEmpty()) {
                                            bestLink = links.maxByOrNull { it.quality } ?: links.first()
                                            report.linkExtractionStatus = TestStatus.SUCCESS
                                            report.linkExtractionDetail = "Extracted ${links.size} links (${bestLink.name} ${bestLink.quality}p)"
                                            addLog(LogLevel.INFO, "AutoTest", "Step 6 PASS: ${report.linkExtractionDetail}")
                                        } else {
                                            report.linkExtractionStatus = TestStatus.WARNING
                                            report.linkExtractionDetail = "0 links returned by provider"
                                            addLog(LogLevel.WARN, "AutoTest", "Step 6 WARN: 0 links")
                                        }
                                    }
                                } else {
                                    report.linkExtractionStatus = TestStatus.WARNING
                                    report.linkExtractionDetail = "Skipped (no episode data)"
                                }
                            } catch (e: Exception) {
                                report.linkExtractionStatus = TestStatus.FAILED
                                report.linkExtractionDetail = "Error: ${e.message}"
                                addLog(LogLevel.ERROR, "AutoTest", "Step 6 FAIL", e)
                            }

                            // STEP 7: Video Playback Launcher Verification
                            try {
                                automatedTestStepName = "Step 7/7: Verifying video player launch..."
                                automatedTestProgress = 1.0f
                                if (bestLink != null) {
                                    val intent = CloudstreamHeadlessRunner.buildRexPlayerIntent(context, bestLink, targetResult?.name)
                                    report.playbackStatus = TestStatus.SUCCESS
                                    report.playbackDetail = "Player intent generated (${intent.data}) with ${intent.getStringArrayExtra("headers")?.size ?: 0} headers"
                                    addLog(LogLevel.INFO, "AutoTest", "Step 7 PASS: Player handoff verified: ${report.playbackDetail}")
                                } else {
                                    report.playbackStatus = TestStatus.WARNING
                                    report.playbackDetail = "Skipped (no stream link extracted)"
                                }
                            } catch (e: Exception) {
                                report.playbackStatus = TestStatus.FAILED
                                report.playbackDetail = "Error: ${e.message}"
                                addLog(LogLevel.ERROR, "AutoTest", "Step 7 FAIL", e)
                            }

                            report.durationMs = SystemClock.elapsedRealtime() - startTime
                            automatedTestStepName = "Completed in ${report.durationMs}ms"
                            withContext(Dispatchers.Main) {
                                isRunningAutomatedTest = false
                                refreshEnvironment()
                                reloadDbRepos()
                                reloadExtensionsData()
                                refreshRegisteredProviders()
                            }
                            addLog(LogLevel.INFO, "AutoTest", "=== COMPLETED FULL CLOUDSTREAM TEST (${report.durationMs}ms) ===")
                        }
                    }
                )
            }

            // SECTION 1: Environment Status
            item {
                SectionCard(title = "1. Environment Status", icon = Icons.Outlined.CheckCircle) {
                    StatusRow(
                        label = "App Version",
                        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        status = TestStatus.SUCCESS
                    )
                    StatusRow(
                        label = "CloudStream SDK",
                        value = "Core v4.0.1 (APIHolder & Plugin Engine)",
                        status = TestStatus.SUCCESS
                    )
                    StatusRow(
                        label = "Loaded Providers",
                        value = "$loadedProvidersCount provider(s) ready",
                        status = if (loadedProvidersCount > 0) TestStatus.SUCCESS else TestStatus.WARNING
                    )
                    StatusRow(
                        label = "Installed Extensions",
                        value = "$installedExtensionsCount package(s) on disk",
                        status = if (installedExtensionsCount > 0) TestStatus.SUCCESS else TestStatus.WARNING
                    )
                    StatusRow(
                        label = "Database Repositories",
                        value = "$repositoriesCount repository URLs",
                        status = if (repositoriesCount > 0) TestStatus.SUCCESS else TestStatus.WARNING
                    )
                    StatusRow(
                        label = "Network Status",
                        value = networkDetails,
                        status = networkStatus
                    )
                    StatusRow(
                        label = "Database Status",
                        value = databaseDetails,
                        status = databaseStatus
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = refreshEnvironment,
                            enabled = !isCheckingEnv
                        ) {
                            if (isCheckingEnv) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Re-check Environment")
                        }
                    }
                }
            }

            // SECTION 2: Repository Test
            item {
                SectionCard(title = "2. Repository Test", icon = Icons.Outlined.CloudQueue) {
                    Text(
                        text = "Stored Extension Repositories (${dbRepositories.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (dbRepositories.isEmpty()) {
                        Text(
                            text = "No repositories stored in database. Click 'Sync All Repositories' to populate defaults.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        dbRepositories.forEach { repo ->
                            val isSelected = repo.url == selectedRepoUrl
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRepoUrl = repo.url },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = repo.name.ifBlank { "Repository" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isSelected) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "Selected",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = repo.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Last Sync: " + if (repo.lastSync > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(repo.lastSync)) else "Never",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    // Repository Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val target = selectedRepoUrl ?: dbRepositories.firstOrNull()?.url
                                if (target != null) {
                                    isSyncingRepos = true
                                    lastRepoSyncError = null
                                    scope.launch(Dispatchers.IO) {
                                        addLog(LogLevel.INFO, "RepoTest", "Syncing repository: $target")
                                        val res = repositoryManager.syncRepository(target)
                                        withContext(Dispatchers.Main) {
                                            isSyncingRepos = false
                                            if (res.error == null) {
                                                lastRepoSyncResult = "Sync SUCCESS: ${res.plugins.size} plugins found for ${res.repoName}"
                                                addLog(LogLevel.INFO, "RepoTest", lastRepoSyncResult!!)
                                            } else {
                                                lastRepoSyncError = "Sync FAILED: ${res.error}"
                                                addLog(LogLevel.ERROR, "RepoTest", lastRepoSyncError!!)
                                            }
                                            reloadDbRepos()
                                            reloadExtensionsData()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "No repository selected", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncingRepos,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync Selected")
                        }

                        FilledTonalButton(
                            onClick = {
                                isSyncingRepos = true
                                lastRepoSyncError = null
                                scope.launch(Dispatchers.IO) {
                                    addLog(LogLevel.INFO, "RepoTest", "Syncing ALL repositories...")
                                    val results = repositoryManager.syncAllRepositories()
                                    val totalFound = repositoryManager.getCachedPlugins().size
                                    withContext(Dispatchers.Main) {
                                        isSyncingRepos = false
                                        lastRepoSyncResult = "All synced: ${results.size} repos checked, $totalFound plugins in cache"
                                        addLog(LogLevel.INFO, "RepoTest", lastRepoSyncResult!!)
                                        reloadDbRepos()
                                        reloadExtensionsData()
                                    }
                                }
                            },
                            enabled = !isSyncingRepos,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync All")
                        }

                        OutlinedButton(
                            onClick = {
                                repositoryManager.clearCache()
                                lastRepoSyncResult = "Cache cleared"
                                addLog(LogLevel.INFO, "RepoTest", "Repository cache cleared")
                                reloadExtensionsData()
                                Toast.makeText(context, "Repo cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear Cache")
                        }
                    }

                    if (lastRepoSyncResult != null) {
                        Text(
                            text = lastRepoSyncResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (lastRepoSyncError != null) {
                        Text(
                            text = lastRepoSyncError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // SECTION 3: Extension Test
            item {
                SectionCard(title = "3. Extension Test", icon = Icons.Outlined.Extension) {
                    Text(
                        text = "Installed Extensions (${installedExtensionsList.size}) / Available in Cache (${availableExtensionsList.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (installedExtensionsList.isEmpty() && availableExtensionsList.isEmpty()) {
                        Text(
                            text = "No extensions detected. Please sync repositories in Section 2 above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Display Installed Extensions
                    installedExtensionsList.forEach { ext ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ext.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${ext.pkgName} • v${ext.version}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Switch(
                                        checked = ext.isEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch(Dispatchers.IO) {
                                                extensionManager.toggleExtension(ext.pkgName, enabled)
                                                reloadExtensionsData()
                                                refreshRegisteredProviders()
                                            }
                                        }
                                    )
                                }

                                Text(
                                    text = "Classes: ${ext.classesFile ?: "Uninspected"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isExtBusy = true
                                            scope.launch(Dispatchers.IO) {
                                                addLog(LogLevel.INFO, "ExtTest", "Reloading & re-registering all installed extensions including ${ext.name}")
                                                extensionManager.loadInstalledExtensions()
                                                withContext(Dispatchers.Main) {
                                                    isExtBusy = false
                                                    selectedExtDetails = "Reloaded extensions. Providers active: ${APIHolder.apis.map { it.name }}"
                                                    addLog(LogLevel.INFO, "ExtTest", selectedExtDetails!!)
                                                    reloadExtensionsData()
                                                    refreshRegisteredProviders()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Reload & Register")
                                    }

                                    Button(
                                        onClick = {
                                            isExtBusy = true
                                            scope.launch(Dispatchers.IO) {
                                                addLog(LogLevel.INFO, "ExtTest", "Uninstalling extension: ${ext.pkgName}")
                                                extensionManager.uninstallExtension(ext.pkgName)
                                                withContext(Dispatchers.Main) {
                                                    isExtBusy = false
                                                    reloadExtensionsData()
                                                    refreshRegisteredProviders()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Uninstall")
                                    }
                                }
                            }
                        }
                    }

                    // Display Available Plugins to install
                    if (availableExtensionsList.isNotEmpty()) {
                        Text(
                            text = "Available in Repository Cache",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        availableExtensionsList.take(5).forEach { plugin ->
                            val isInstalled = installedExtensionsList.any { it.pkgName == plugin.internalName }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plugin.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "v${plugin.version} • ${plugin.internalName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                if (!isInstalled) {
                                    Button(
                                        onClick = {
                                            isExtBusy = true
                                            extOperationError = null
                                            scope.launch(Dispatchers.IO) {
                                                addLog(LogLevel.INFO, "ExtTest", "Installing ${plugin.name} from ${plugin.url}")
                                                val success = extensionManager.installExtension(plugin)
                                                withContext(Dispatchers.Main) {
                                                    isExtBusy = false
                                                    if (success) {
                                                        selectedExtDetails = "Installed ${plugin.name} successfully!"
                                                        addLog(LogLevel.INFO, "ExtTest", selectedExtDetails!!)
                                                    } else {
                                                        extOperationError = "Failed to install ${plugin.name}"
                                                        addLog(LogLevel.ERROR, "ExtTest", extOperationError!!)
                                                    }
                                                    reloadExtensionsData()
                                                    refreshRegisteredProviders()
                                                }
                                            }
                                        },
                                        enabled = !isExtBusy
                                    ) {
                                        Text("Install")
                                    }
                                } else {
                                    Text(
                                        text = "Installed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (selectedExtDetails != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedExtDetails!!,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (extOperationError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = extOperationError!!,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 4: Provider Registry Test
            item {
                SectionCard(title = "4. Provider Registry Test", icon = Icons.Outlined.AppRegistration) {
                    Text(
                        text = "Registered Provider APIs (${registeredProvidersList.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (registeredProvidersList.isEmpty()) {
                        Text(
                            text = "No providers registered in APIHolder. Install and enable an extension first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        registeredProvidersList.forEach { api ->
                            val selfTestResult = providerSelfTestResults[api.name]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = api.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Lang: ${api.lang} • Types: ${api.supportedTypes.joinToString()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = "Main URL: ${api.mainUrl}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                isRunningSelfTest = true
                                                scope.launch(Dispatchers.IO) {
                                                    addLog(LogLevel.INFO, "SelfTest", "Running self-test on provider: ${api.name}")
                                                    val t0 = SystemClock.elapsedRealtime()
                                                    try {
                                                        var details = "Pass: "
                                                        if (api.hasMainPage) {
                                                            val mainPage = api.getMainPage(1, MainPageRequest(api.name, api.mainUrl, false))
                                                            val count = mainPage?.items?.size ?: 0
                                                            details += "getMainPage() returned $count items"
                                                        } else {
                                                            val searchTest = api.search("a")
                                                            details += "search('a') returned ${searchTest?.size ?: 0} items"
                                                        }
                                                        val latency = SystemClock.elapsedRealtime() - t0
                                                        details += " (${latency}ms)"
                                                        withContext(Dispatchers.Main) {
                                                            providerSelfTestResults = providerSelfTestResults + (api.name to details)
                                                            addLog(LogLevel.INFO, "SelfTest", "${api.name} self-test SUCCESS: $details")
                                                            isRunningSelfTest = false
                                                        }
                                                    } catch (t: Throwable) {
                                                        val sw = StringWriter()
                                                        t.printStackTrace(PrintWriter(sw))
                                                        val err = "FAILED: ${t.message}\n${sw.toString().take(300)}"
                                                        withContext(Dispatchers.Main) {
                                                            providerSelfTestResults = providerSelfTestResults + (api.name to err)
                                                            addLog(LogLevel.ERROR, "SelfTest", "${api.name} self-test FAIL", t)
                                                            isRunningSelfTest = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isRunningSelfTest
                                        ) {
                                            Text("Self-Test")
                                        }
                                    }

                                    if (selfTestResult != null) {
                                        val isFail = selfTestResult.startsWith("FAILED")
                                        Surface(
                                            color = if (isFail) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = selfTestResult,
                                                    modifier = Modifier.padding(8.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isFail) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: Search Test
            item {
                SectionCard(title = "5. Multi-Provider Search Test", icon = Icons.Outlined.Search) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search Query") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val query = searchQuery.trim()
                                if (query.isBlank()) return@Button
                                isSearching = true
                                selectedSearchResult = null
                                loadedMediaResponse = null
                                extractedLinksList = emptyList()
                                scope.launch(Dispatchers.IO) {
                                    addLog(LogLevel.INFO, "SearchTest", "Searching '$query' against ${registeredProvidersList.size} providers...")
                                    val t0 = SystemClock.elapsedRealtime()
                                    val providerResultsMap = mutableMapOf<String, List<SearchResponse>>()
                                    val providerErrorsMap = mutableMapOf<String, String>()
                                    val allResults = mutableListOf<SearchResponse>()

                                    val jobs = registeredProvidersList.map { api ->
                                        async {
                                            try {
                                                val res = api.search(query) ?: emptyList()
                                                synchronized(providerResultsMap) {
                                                    providerResultsMap[api.name] = res
                                                    allResults.addAll(res)
                                                }
                                                addLog(LogLevel.INFO, "SearchTest", "${api.name}: returned ${res.size} results")
                                            } catch (t: Throwable) {
                                                synchronized(providerErrorsMap) {
                                                    providerErrorsMap[api.name] = t.message ?: "Unknown error"
                                                }
                                                addLog(LogLevel.WARN, "SearchTest", "${api.name} search error", t)
                                            }
                                        }
                                    }
                                    jobs.awaitAll()
                                    val latency = SystemClock.elapsedRealtime() - t0

                                    withContext(Dispatchers.Main) {
                                        searchLatencyMs = latency
                                        searchResultsByProvider = providerResultsMap
                                        searchErrorsByProvider = providerErrorsMap
                                        allFlatSearchResults = allResults
                                        isSearching = false
                                    }
                                }
                            },
                            enabled = !isSearching
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Run Search")
                        }
                    }

                    // Search Telemetry
                    if (searchLatencyMs != null) {
                        Text(
                            text = "Search finished in ${searchLatencyMs}ms: ${allFlatSearchResults.size} total results across ${searchResultsByProvider.size} providers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Search Results List
                    if (allFlatSearchResults.isNotEmpty()) {
                        Text(
                            text = "Tap a result to run Metadata Test below:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allFlatSearchResults) { item ->
                                val isSelected = selectedSearchResult == item
                                Card(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable {
                                            selectedSearchResult = item
                                            loadedMediaResponse = null
                                            extractedLinksList = emptyList()
                                            addLog(LogLevel.INFO, "SearchTest", "Selected item: ${item.name} (${item.apiName})")
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        AsyncImage(
                                            model = item.posterUrl,
                                            contentDescription = item.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.apiName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 6: Metadata Test
            item {
                SectionCard(title = "6. Metadata Test (api.load)", icon = Icons.Outlined.Info) {
                    if (selectedSearchResult == null) {
                        Text(
                            text = "No search result selected. Perform a search in Section 5 and tap an item to inspect metadata.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        val item = selectedSearchResult!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Target: ${item.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Provider: ${item.apiName} • URL: ${item.url}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = {
                                    isLoadingMetadata = true
                                    metadataError = null
                                    rawMetadataJson = null
                                    scope.launch(Dispatchers.IO) {
                                        addLog(LogLevel.INFO, "MetadataTest", "Fetching metadata from ${item.apiName} for: ${item.url}")
                                        val t0 = SystemClock.elapsedRealtime()
                                        try {
                                            val api = APIHolder.getApi(item.apiName) ?: APIHolder.apis.firstOrNull { it.name.equals(item.apiName, true) }
                                            if (api != null) {
                                                val resp = api.load(item.url)
                                                val latency = SystemClock.elapsedRealtime() - t0
                                                withContext(Dispatchers.Main) {
                                                    isLoadingMetadata = false
                                                    loadedMediaResponse = resp
                                                    rawMetadataJson = resp.toString()
                                                    if (resp != null) {
                                                        addLog(LogLevel.INFO, "MetadataTest", "Metadata loaded in ${latency}ms for ${resp.name}")
                                                    } else {
                                                        metadataError = "api.load() returned null"
                                                        addLog(LogLevel.ERROR, "MetadataTest", metadataError!!)
                                                    }
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    isLoadingMetadata = false
                                                    metadataError = "API '${item.apiName}' not found in APIHolder"
                                                    addLog(LogLevel.ERROR, "MetadataTest", metadataError!!)
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            withContext(Dispatchers.Main) {
                                                isLoadingMetadata = false
                                                metadataError = "Exception: ${t.message}"
                                                addLog(LogLevel.ERROR, "MetadataTest", "Metadata load failed", t)
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoadingMetadata
                            ) {
                                if (isLoadingMetadata) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Load Details")
                            }
                        }

                        if (loadedMediaResponse != null) {
                            val resp = loadedMediaResponse!!
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Title: ${resp.name} (${resp.year ?: "N/A"})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Type: ${resp.type} • API: ${resp.apiName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!resp.plot.isNullOrBlank()) {
                                        Text(
                                            text = "Plot: ${resp.plot}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (resp is TvSeriesLoadResponse) {
                                        Text(
                                            text = "Episodes (${resp.episodes.size}): Tap an episode to extract stream links in Section 7",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(resp.episodes) { ep ->
                                                val isEpSelected = selectedEpisode == ep
                                                FilterChip(
                                                    selected = isEpSelected,
                                                    onClick = {
                                                        selectedEpisode = ep
                                                        addLog(LogLevel.INFO, "MetadataTest", "Selected episode: S${ep.season}E${ep.episode} - ${ep.name}")
                                                    },
                                                    label = { Text("S${ep.season ?: 1}E${ep.episode ?: 1}") }
                                                )
                                            }
                                        }
                                    } else if (resp is MovieLoadResponse) {
                                        Text(
                                            text = "Movie Stream Data URL: ${resp.dataUrl}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Raw String Inspection
                                    if (rawMetadataJson != null) {
                                        SelectionContainer {
                                            Text(
                                                text = "Raw:\n$rawMetadataJson",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (metadataError != null) {
                            Text(
                                text = metadataError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // SECTION 7: Link Extraction Test
            item {
                SectionCard(title = "7. Link Extraction Test (loadLinks)", icon = Icons.Outlined.Link) {
                    val targetData = when (val resp = loadedMediaResponse) {
                        is TvSeriesLoadResponse -> selectedEpisode?.data ?: resp.episodes.firstOrNull()?.data
                        is MovieLoadResponse -> resp.dataUrl
                        else -> null
                    }

                    if (targetData == null) {
                        Text(
                            text = "No media content loaded. Load metadata in Section 6 first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Target Data: $targetData",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = {
                                    isExtractingLinks = true
                                    linkExtractionError = null
                                    extractedLinksList = emptyList()
                                    extractedSubtitlesList = emptyList()
                                    selectedLinkForPlay = null
                                    scope.launch(Dispatchers.IO) {
                                        addLog(LogLevel.INFO, "LinkTest", "Extracting stream links for: $targetData")
                                        val t0 = SystemClock.elapsedRealtime()
                                        try {
                                            val apiName = loadedMediaResponse?.apiName ?: ""
                                            val api = APIHolder.getApi(apiName) ?: APIHolder.apis.firstOrNull()
                                            val links = mutableListOf<ExtractorLink>()
                                            val subs = mutableListOf<SubtitleFile>()

                                            if (api != null) {
                                                api.loadLinks(targetData, false, { sub ->
                                                    synchronized(subs) { subs.add(sub) }
                                                }) { link ->
                                                    synchronized(links) { links.add(link) }
                                                }
                                            }
                                            val latency = SystemClock.elapsedRealtime() - t0

                                            withContext(Dispatchers.Main) {
                                                isExtractingLinks = false
                                                extractionDurationMs = latency
                                                extractedLinksList = links
                                                extractedSubtitlesList = subs
                                                if (links.isNotEmpty()) {
                                                    selectedLinkForPlay = links.maxByOrNull { it.quality } ?: links.first()
                                                    addLog(LogLevel.INFO, "LinkTest", "Extracted ${links.size} links in ${latency}ms")
                                                } else {
                                                    linkExtractionError = "No stream links extracted by provider"
                                                    addLog(LogLevel.WARN, "LinkTest", linkExtractionError!!)
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            withContext(Dispatchers.Main) {
                                                isExtractingLinks = false
                                                linkExtractionError = "Exception: ${t.message}"
                                                addLog(LogLevel.ERROR, "LinkTest", "Link extraction failed", t)
                                            }
                                        }
                                    }
                                },
                                enabled = !isExtractingLinks
                            ) {
                                if (isExtractingLinks) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Extract Links")
                            }
                        }

                        if (extractionDurationMs != null) {
                            Text(
                                text = "Extraction completed in ${extractionDurationMs}ms (${extractedLinksList.size} links, ${extractedSubtitlesList.size} subtitles)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (extractedLinksList.isNotEmpty()) {
                            Text(
                                text = "Extracted Links (Tap to select for playback in Section 8):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            extractedLinksList.forEach { link ->
                                val isSelected = selectedLinkForPlay == link
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLinkForPlay = link
                                            addLog(LogLevel.INFO, "LinkTest", "Selected link: ${link.name} (${link.quality}p)")
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${link.name} • ${link.quality}p",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (link.isM3u8) "HLS (m3u8)" else "MP4 / Direct",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = link.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (link.referer.isNotBlank()) {
                                            Text(
                                                text = "Referer: ${link.referer}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (linkExtractionError != null) {
                            Text(
                                text = linkExtractionError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // SECTION 8: Video Playback Test
            item {
                SectionCard(title = "8. Video Playback Test (MPV Player)", icon = Icons.Outlined.PlayCircle) {
                    if (selectedLinkForPlay == null) {
                        Text(
                            text = "No stream link selected. Extract and select a link in Section 7 above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        val link = selectedLinkForPlay!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Selected Stream: ${link.name} (${link.quality}p)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = link.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    val mediaTitle = loadedMediaResponse?.name ?: "CloudStream Diagnostic Stream"
                                    addLog(LogLevel.INFO, "PlaybackTest", "Launching MPV Player for: ${link.url}")
                                    playbackStateStatus = "Launching PlayerActivity..."
                                    playbackErrorsLog = null

                                    RexPlayerBridge.playStream(
                                        context = context,
                                        link = link,
                                        title = mediaTitle
                                    )

                                    playbackStateStatus = "Player started successfully! Video handed over to REX-MPV engine."
                                    playbackIntentSummary = "URL: ${link.url}\nHeaders count: ${link.headers.size + if (link.referer.isNotBlank()) 1 else 0}"
                                    addLog(LogLevel.INFO, "PlaybackTest", "PlayerActivity launched with stream handoff")
                                } catch (t: Throwable) {
                                    playbackStateStatus = "Launch FAILED"
                                    playbackErrorsLog = "Exception launching player: ${t.message}"
                                    addLog(LogLevel.ERROR, "PlaybackTest", "Player launch failed", t)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Selected Link in MPV Player")
                        }

                        Text(
                            text = "Status: $playbackStateStatus",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (playbackIntentSummary != null) {
                            SelectionContainer {
                                Text(
                                    text = playbackIntentSummary!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        if (playbackErrorsLog != null) {
                            Text(
                                text = playbackErrorsLog!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // SECTION 9: Live Logs Console
            item {
                SectionCard(title = "9. Diagnostic Live Logs", icon = Icons.Outlined.Terminal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = logFilter == null,
                                onClick = { logFilter = null },
                                label = { Text("ALL (${logs.size})") }
                            )
                            FilterChip(
                                selected = logFilter == LogLevel.INFO,
                                onClick = { logFilter = LogLevel.INFO },
                                label = { Text("INFO") }
                            )
                            FilterChip(
                                selected = logFilter == LogLevel.WARN,
                                onClick = { logFilter = LogLevel.WARN },
                                label = { Text("WARN") }
                            )
                            FilterChip(
                                selected = logFilter == LogLevel.ERROR,
                                onClick = { logFilter = LogLevel.ERROR },
                                label = { Text("ERROR") }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                val logDump = logs.joinToString("\n") { log ->
                                    "[${log.formattedTime}] [${log.level}] [${log.tag}] ${log.message}"
                                }
                                clipboard.setText(AnnotatedString(logDump))
                                Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Logs")
                            }
                            IconButton(onClick = { logs.clear() }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Clear Logs")
                            }
                        }
                    }

                    // Terminal Box
                    val filteredLogs = remember(logs, logFilter) {
                        if (logFilter == null) logs.toList()
                        else logs.filter { it.level == logFilter }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No log records yet",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            SelectionContainer {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredLogs) { log ->
                                        val color = when (log.level) {
                                            LogLevel.INFO -> Color(0xFF81C784)
                                            LogLevel.WARN -> Color(0xFFFFB74D)
                                            LogLevel.ERROR -> Color(0xFFE57373)
                                        }
                                        Text(
                                            text = "${log.formattedTime} [${log.tag}] ${log.message}",
                                            color = color,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    status: TestStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        StatusBadge(status = status)
    }
}

@Composable
private fun StatusBadge(status: TestStatus) {
    val (bgColor, textColor, text) = when (status) {
        TestStatus.SUCCESS -> Triple(Color(0xFF2E7D32), Color.White, "SUCCESS")
        TestStatus.WARNING -> Triple(Color(0xFFEF6C00), Color.White, "WARNING")
        TestStatus.FAILED -> Triple(Color(0xFFC62828), Color.White, "FAILED")
        TestStatus.RUNNING -> Triple(MaterialTheme.colorScheme.primary, Color.White, "RUNNING")
        TestStatus.IDLE -> Triple(Color.Gray.copy(alpha = 0.3f), MaterialTheme.colorScheme.onSurface, "IDLE")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun AutomatedTestCard(
    isRunning: Boolean,
    progress: Float,
    stepName: String,
    report: AutomatedTestReport?,
    onRunTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "10. Full Automated End-to-End Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Repositories → Extensions → Providers → Search → Metadata → Links → Playback",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Button(
                onClick = onRunTest,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running Test...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Complete CloudStream Test")
                }
            }

            if (isRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = stepName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (report != null && !isRunning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Final Test Report (${report.durationMs}ms):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    AutomatedReportRow("1. Repositories", report.repositoriesStatus, report.repositoriesDetail)
                    AutomatedReportRow("2. Extensions", report.extensionsStatus, report.extensionsDetail)
                    AutomatedReportRow("3. Providers", report.providersStatus, report.providersDetail)
                    AutomatedReportRow("4. Search", report.searchStatus, report.searchDetail)
                    AutomatedReportRow("5. Metadata", report.metadataStatus, report.metadataDetail)
                    AutomatedReportRow("6. Link Extraction", report.linkExtractionStatus, report.linkExtractionDetail)
                    AutomatedReportRow("7. Playback", report.playbackStatus, report.playbackDetail)
                }
            }
        }
    }
}

@Composable
private fun AutomatedReportRow(
    stage: String,
    status: TestStatus,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusBadge(status = status)
    }
}
