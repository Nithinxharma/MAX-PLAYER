package xyz.mpv.rex.cinehub.diagnostic

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.api.CineHubEpisode
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object CloudStreamTestCenterScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val clipboardManager = LocalClipboardManager.current

        val repositoryManager = koinInject<RepositoryManager>()
        val extensionManager = koinInject<ExtensionManager>()
        val registry = koinInject<ProviderRegistry>()
        val db = koinInject<MpvExDatabase>()
        val client = koinInject<OkHttpClient>()

        val viewModel = remember {
            CloudStreamDiagnosticViewModel(
                context = context.applicationContext,
                repositoryManager = repositoryManager,
                extensionManager = extensionManager,
                registry = registry,
                db = db,
                client = client
            )
        }

        LaunchedEffect(Unit) {
            android.util.Log.i("TestCenterScreen", "INSTANCE_IDENTITY: CloudStreamTestCenterScreen composed. identityHashCode=${System.identityHashCode(this)}, ViewModel.identityHashCode=${System.identityHashCode(viewModel)}, ProviderRegistry.identityHashCode=${System.identityHashCode(registry)}, ExtensionManager.identityHashCode=${System.identityHashCode(extensionManager)}, APIHolder.identityHashCode=${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf(
            "Overview & Auto",
            "Force Activation",
            "Execution Trace",
            "Repositories",
            "Extensions",
            "Providers",
            "Search",
            "Metadata",
            "Streams & Playback",
            "Live Logs"
        )

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
                                text = "Unified Diagnostic & Testing Suite",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = backstack::removeLastOrNull) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refreshEnvironmentStatus() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    divider = { HorizontalDivider() }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> OverviewAndAutoTestSection(viewModel, onNavigateToTab = { target ->
                            val mapped = when (target) {
                                1 -> 3 // Repositories
                                2 -> 4 // Extensions
                                3 -> 5 // Providers
                                else -> target
                            }
                            selectedTab = mapped
                        })
                        1 -> xyz.mpv.rex.ui.preferences.ForcePluginActivationScreen(showTopBar = false, onNavigateToTrace = { selectedTab = 2 })
                        2 -> xyz.mpv.rex.ui.preferences.PluginExecutionTraceScreen(showTopBar = false)
                        3 -> RepositoriesSection(viewModel)
                        4 -> ExtensionsSection(viewModel)
                        5 -> ProvidersSection(viewModel)
                        6 -> SearchSection(viewModel, onSelectForMetadata = { selectedTab = 7 })
                        7 -> MetadataSection(viewModel, onSelectForExtraction = { selectedTab = 8 })
                        8 -> StreamsAndPlaybackSection(viewModel)
                        9 -> LiveLogsSection(viewModel)
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 1 & 10: OVERVIEW & AUTOMATED TEST
// =========================================================================

@Composable
fun OverviewAndAutoTestSection(
    viewModel: CloudStreamDiagnosticViewModel,
    onNavigateToTab: (Int) -> Unit
) {
    val envState by viewModel.envStatus.collectAsState()
    val isRunningAuto by viewModel.isRunningAutomatedTest.collectAsState()
    val automatedSteps by viewModel.automatedSteps.collectAsState()
    val report by viewModel.automatedReport.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Full Automated Test Card (Prominent Hero)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Section 10: Full Automated Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    StatusBadge(status = if (isRunningAuto) StatusIndicator.RUNNING else report?.repoStatus ?: StatusIndicator.IDLE)
                }

                Text(
                    text = "Runs complete end-to-end verification pipeline: Sync Repos → Install Extension → Register Providers → Search \"One Piece\" → Load Metadata → Extract Links → Playback Pipeline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )

                Button(
                    onClick = { viewModel.runCompleteCloudStreamTest() },
                    enabled = !isRunningAuto,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRunningAuto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running Pipeline...")
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Complete CloudStream Test")
                    }
                }

                if (automatedSteps.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Pipeline Progress:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    automatedSteps.forEach { step ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (step.status) {
                                StatusIndicator.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                StatusIndicator.WARNING -> Color(0xFFFFF3E0)
                                StatusIndicator.SUCCESS -> Color(0xFFE8F5E9)
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (step.status) {
                                    StatusIndicator.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    StatusIndicator.WARNING -> Color(0xFFFFB74D)
                                    StatusIndicator.SUCCESS -> Color(0xFFA5D6A7)
                                    else -> Color.Transparent
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        when (step.status) {
                                            StatusIndicator.SUCCESS -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                            StatusIndicator.FAILED -> Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            StatusIndicator.WARNING -> Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                                            StatusIndicator.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            else -> Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                        }
                                        Text(text = step.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    if (step.durationMs > 0) {
                                        Text(text = "${step.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }

                                if (step.details.isNotBlank()) {
                                    Text(text = step.details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (!step.failureReason.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Reason: ${step.failureReason}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            if (!step.troubleshootingTip.isNullOrBlank()) {
                                                Text(
                                                    text = "💡 Tip: ${step.troubleshootingTip}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!step.failureStackTrace.isNullOrBlank()) {
                                    var showStack by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { showStack = !showStack }, contentPadding = PaddingValues(0.dp)) {
                                            Text(if (showStack) "Hide Stacktrace" else "Show Full Stacktrace", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(step.failureStackTrace))
                                                Toast.makeText(context, "Stacktrace copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Stacktrace", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    if (showStack) {
                                        SelectionContainer {
                                            Text(
                                                text = step.failureStackTrace,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                                                    .padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (report != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "Final Diagnostic Summary:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(text = report!!.summaryText, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(report!!.fullReport))
                                    Toast.makeText(context, "Full report copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Report", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Environment Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Section 1: Environment Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    StatusBadge(status = envState.overallStatus)
                }

                HorizontalDivider()

                StatusGridItem("App Version", envState.appVersion, Icons.Default.Info)
                StatusGridItem("CloudStream SDK", envState.cloudstreamSdkVersion, Icons.Default.Code)
                StatusGridItem("Active Providers", "${envState.activeProviderCount} active", Icons.Default.Widgets, onClick = { onNavigateToTab(3) })
                StatusGridItem("Providers Registered", "${envState.providersRegisteredCount} in registry", Icons.Default.Layers)
                StatusGridItem("Providers Enabled", "${envState.providersEnabledCount} enabled", Icons.Default.Check)
                StatusGridItem("Installed Extensions", "${envState.installedExtensionsCount} in DB", Icons.Default.Extension, onClick = { onNavigateToTab(2) })
                StatusGridItem("Plugin Files Found", "${envState.pluginFilesFoundCount} files on disk", Icons.Default.FolderOpen)
                StatusGridItem("Plugins Succeeded", "${envState.successfullyLoadedPluginsCount} loaded", Icons.Default.CheckCircleOutline)
                StatusGridItem("Plugins Failed", "${envState.failedPluginLoadsCount} failed", Icons.Default.ErrorOutline)
                StatusGridItem("registerMainAPI Calls", "${envState.registerMainApiCallsCount} invoked", Icons.Default.Send)
                StatusGridItem("APIHolder Providers", "${envState.apiHolderProviderCount} registered", Icons.Default.AccountTree)
                StatusGridItem("Repositories", "${envState.repositoriesCount} configured", Icons.Default.CloudQueue, onClick = { onNavigateToTab(1) })
                StatusGridItem("Network Status", envState.networkStatus, if (envState.isNetworkConnected) Icons.Default.Wifi else Icons.Default.WifiOff)
                StatusGridItem("Database Status", envState.databaseStatus, Icons.Default.Storage)
            }
        }
    }
}

@Composable
fun StatusGridItem(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =========================================================================
// SECTION 2: REPOSITORY TEST
// =========================================================================

@Composable
fun RepositoriesSection(viewModel: CloudStreamDiagnosticViewModel) {
    val items by viewModel.repoTestItems.collectAsState()
    val isSyncing by viewModel.isSyncingRepos.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Section 2: Repository Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Test fetching remote repository manifests, parsing plugin catalogs, and database persistence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncAllRepositories() },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync All")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.addPopularRepositories() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Presets")
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearRepositoryCache() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No repositories configured. Click \"Add Presets\" to inject CloudStream repositories.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.repo.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            StatusBadge(status = item.syncStatus)
                        }

                        Text(
                            text = item.repo.url,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Plugins: ${item.downloadedManifestCount} manifest items",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "HTTP Code: ${item.httpCode ?: "N/A"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.httpCode == 200) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                            )
                        }

                        if (!item.errorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = "Error: ${item.errorMessage}\n${item.stackTrace ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.syncSingleRepository(item.repo.url) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Sync Selected Repository", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 3: EXTENSION TEST
// =========================================================================

@Composable
fun ExtensionsSection(viewModel: CloudStreamDiagnosticViewModel) {
    val items by viewModel.extensionTestItems.collectAsState()
    val isManaging by viewModel.isManagingExtensions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Section 3: Extension Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Test extension download, zip extraction, classes inspection, and DexClassLoader provider registration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.reloadInstalledExtensions() },
                        enabled = !isManaging,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reload from Disk")
                    }

                    OutlinedButton(
                        onClick = { viewModel.refreshExtensionList() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Refresh List")
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No extensions found in catalog. Please sync repositories first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items.forEach { ext ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = ext.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${ext.pkgName} (v${ext.version})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            StatusBadge(
                                status = if (ext.isInstalled) StatusIndicator.SUCCESS else StatusIndicator.IDLE,
                                customText = if (ext.isInstalled) "INSTALLED" else "AVAILABLE"
                            )
                        }

                        if (ext.detectedProviders.isNotEmpty()) {
                            Text(
                                text = "Detected Providers (${ext.detectedProviders.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            ext.detectedProviders.forEach { prov ->
                                Text(
                                    text = "• $prov",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (!ext.extractedManifestJson.isNullOrBlank()) {
                            var showJson by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = { showJson = !showJson },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (showJson) "Hide Extracted Manifest JSON" else "View Extracted Manifest JSON",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (showJson) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = ext.extractedManifestJson,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ext.isInstalled) {
                                OutlinedButton(
                                    onClick = { viewModel.registerProvidersForExtension(ext.pkgName) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Register Providers", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { viewModel.uninstallExtension(ext.pkgName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Uninstall", style = MaterialTheme.typography.labelSmall)
                                }
                            } else if (ext.pluginRef != null) {
                                Button(
                                    onClick = { viewModel.installExtension(ext.pluginRef) },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Install Extension", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 4: PROVIDER REGISTRY TEST
// =========================================================================

@Composable
fun ProvidersSection(viewModel: CloudStreamDiagnosticViewModel) {
    val providers by viewModel.registeredProviders.collectAsState()
    val selfTests by viewModel.providerSelfTests.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Section 4: Provider Registry Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Verify registered provider instances, capabilities, languages, and execute provider self-test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Total Active Providers: ${providers.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (providers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No providers currently registered. Install an extension or reload from disk.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            providers.forEach { provider ->
                val testResult = selfTests[provider.id]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            StatusBadge(status = testResult?.status ?: StatusIndicator.IDLE)
                        }

                        Text(
                            text = "ID: ${provider.id} | Lang: ${provider.lang} | Has MainPage: ${provider.hasMainPage}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Text(
                            text = "Supported Types: ${provider.supportedTypes.joinToString { it.name }}",
                            style = MaterialTheme.typography.labelSmall
                        )

                        if (testResult != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (testResult.status == StatusIndicator.SUCCESS) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = if (testResult.status == StatusIndicator.SUCCESS)
                                            "Self-Test Passed: ${testResult.itemsCount} items returned in ${testResult.latencyMs}ms"
                                        else if (testResult.status == StatusIndicator.RUNNING)
                                            "Testing provider main page / search..."
                                        else
                                            "Self-Test Failed (${testResult.latencyMs}ms): ${testResult.errorMessage}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (testResult.status == StatusIndicator.SUCCESS) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    if (!testResult.stackTrace.isNullOrBlank()) {
                                        SelectionContainer {
                                            Text(
                                                text = testResult.stackTrace,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.runProviderSelfTest(provider) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Run Provider Self-Test", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 5: SEARCH TEST
// =========================================================================

@Composable
fun SearchSection(
    viewModel: CloudStreamDiagnosticViewModel,
    onSelectForMetadata: () -> Unit
) {
    var query by remember { mutableStateOf(viewModel.searchQuery.value) }
    val isSearching by viewModel.isSearching.collectAsState()
    val summaries by viewModel.searchSummaries.collectAsState()
    val results by viewModel.searchResults.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Section 5: Search Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Query all registered providers concurrently, record search latency, item counts, and inspect search result models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.searchQuery.value = it
                        },
                        label = { Text("Search Query") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = { viewModel.runSearch(query) },
                        enabled = !isSearching,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Search")
                        }
                    }
                }
            }
        }

        if (summaries.isNotEmpty()) {
            Text(
                text = "Provider Search Metrics:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            summaries.forEach { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = summary.providerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            if (summary.error != null) {
                                Text(text = "Error: ${summary.error}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "${summary.resultCount} items", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(text = "${summary.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            Text(
                text = "Results (${results.size} found):",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            results.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!item.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .size(56.dp, 80.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp, 80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null)
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Provider: ${item.providerName} (${item.type.name})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.url,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Button(
                                onClick = {
                                    viewModel.fetchMetadata(item)
                                    onSelectForMetadata()
                                },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Select for Metadata Test", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 6: METADATA TEST
// =========================================================================

@Composable
fun MetadataSection(
    viewModel: CloudStreamDiagnosticViewModel,
    onSelectForExtraction: () -> Unit
) {
    val selectedItem by viewModel.selectedSearchItem.collectAsState()
    val isLoading by viewModel.isLoadingMetadata.collectAsState()
    val details by viewModel.metadataDetails.collectAsState()
    val rawJson by viewModel.rawMetadataJson.collectAsState()
    val error by viewModel.metadataError.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Section 6: Metadata Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Verify provider.loadDetails(url), title, year, episodes list, cast, genres, and inspect raw JSON payload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (selectedItem != null) {
                    Text(
                        text = "Selected: ${selectedItem!!.title} [${selectedItem!!.providerName}]",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { viewModel.fetchMetadata(selectedItem!!) },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetch Metadata (loadDetails)")
                        }
                    }
                } else {
                    Text(
                        text = "No item selected. Go to Section 5: Search and select a result card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (details != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!details!!.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = details!!.posterUrl,
                                contentDescription = details!!.title,
                                modifier = Modifier
                                    .size(70.dp, 100.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = details!!.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Year: ${details!!.year ?: "N/A"} | Type: ${details!!.type.name}", style = MaterialTheme.typography.labelSmall)
                            Text(text = "Provider: ${details!!.providerName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (details!!.genres.isNotEmpty()) {
                                Text(text = "Genres: ${details!!.genres.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    if (!details!!.overview.isNullOrBlank()) {
                        Text(text = details!!.overview!!, style = MaterialTheme.typography.bodySmall)
                    }

                    if (details!!.episodes.isNotEmpty()) {
                        Text(
                            text = "Episodes (${details!!.episodes.size}):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        details!!.episodes.take(10).forEach { ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "S${ep.season ?: 1} E${ep.episode ?: 1}: ${ep.name ?: "Episode"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectedEpisode.value = ep
                                        viewModel.extractLinks(ep.data, details!!.providerId)
                                        onSelectForExtraction()
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Extract Streams", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (details!!.episodes.size > 10) {
                            Text(
                                text = "+ ${details!!.episodes.size - 10} more episodes...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            if (!rawJson.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Raw Response JSON", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(rawJson!!))
                                Toast.makeText(context, "JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", modifier = Modifier.size(16.dp))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = rawJson!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 7 & 8: STREAMS & PLAYBACK
// =========================================================================

@Composable
fun StreamsAndPlaybackSection(viewModel: CloudStreamDiagnosticViewModel) {
    val isExtracting by viewModel.isExtractingLinks.collectAsState()
    val links by viewModel.extractedLinks.collectAsState()
    val subtitles by viewModel.extractedSubtitles.collectAsState()
    val duration by viewModel.linkExtractionDurationMs.collectAsState()
    val error by viewModel.linkExtractionError.collectAsState()
    val selectedStream by viewModel.selectedStreamLink.collectAsState()
    val playbackLogs by viewModel.playbackLogs.collectAsState()
    val playbackStatus by viewModel.playbackStatus.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 7: Link Extraction Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Section 7: Link Extraction Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Test provider.loadStreams(data), video URL resolution, HTTP referer/user-agent headers, and subtitle streams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (duration > 0) {
                    Text(
                        text = "Extraction Duration: ${duration}ms | Streams: ${links.size} | Subtitles: ${subtitles.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (links.isNotEmpty()) {
            Text(
                text = "Extracted Video Streams (${links.size}):",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            links.forEach { stream ->
                val isSelected = selectedStream == stream
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectedStreamLink.value = stream },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stream.name} - ${stream.quality}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (stream.isM3u8) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "HLS / M3U8",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = stream.url,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (stream.headers.isNotEmpty()) {
                            Text(
                                text = "Headers: ${stream.headers.map { "${it.key}: ${it.value}" }.joinToString(" | ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Section 8: Video Playback Test Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Section 8: Video Playback Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    StatusBadge(status = playbackStatus)
                }

                Text(
                    text = "Launch native MPV player engine with resolved stream link and injected HTTP request headers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (selectedStream != null) {
                    Button(
                        onClick = { viewModel.playSelectedLink(selectedStream!!) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Selected Link in MPV Player")
                    }
                } else {
                    Text(
                        text = "Select a stream above to test MPV playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (playbackLogs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            playbackLogs.forEach { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (log.contains("[ERROR]")) Color(0xFFFF5252) else Color(0xFF81C784)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 9: LIVE LOGS
// =========================================================================

@Composable
fun LiveLogsSection(viewModel: CloudStreamDiagnosticViewModel) {
    val allLogs by DiagnosticLogger.logs.collectAsState()
    var selectedLevel by remember { mutableStateOf(DiagnosticLogLevel.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val filteredLogs = remember(allLogs, selectedLevel, searchQuery) {
        allLogs.filter { log ->
            val matchLevel = (selectedLevel == DiagnosticLogLevel.ALL || log.level == selectedLevel)
            val matchQuery = (searchQuery.isBlank() || log.message.contains(searchQuery, ignoreCase = true) || log.tag.contains(searchQuery, ignoreCase = true) || (log.stackTrace?.contains(searchQuery, ignoreCase = true) == true))
            matchLevel && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Section 9: Live Logs (${filteredLogs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val formatted = DiagnosticLogger.getFormattedLogs(selectedLevel, null, searchQuery)
                        clipboardManager.setText(AnnotatedString(formatted))
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Logs", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { DiagnosticLogger.clear() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DiagnosticLogLevel.values().forEach { lvl ->
                FilterChip(
                    selected = selectedLevel == lvl,
                    onClick = { selectedLevel = lvl },
                    label = { Text(lvl.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search logs by tag or text...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        // Monospace Console Window
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF121212),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(filteredLogs.size) {
                if (filteredLogs.isNotEmpty()) {
                    listState.animateScrollToItem(filteredLogs.size - 1)
                }
            }

            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val logColor = when (log.level) {
                            DiagnosticLogLevel.ERROR -> Color(0xFFFF5252)
                            DiagnosticLogLevel.WARNING -> Color(0xFFFFB74D)
                            DiagnosticLogLevel.INFO -> Color(0xFF81C784)
                            DiagnosticLogLevel.DEBUG -> Color(0xFF4FC3F7)
                            else -> Color(0xFFE0E0E0)
                        }

                        Column {
                            Text(
                                text = "[${log.formattedTime}] [${log.level.name}] [${log.tag}] ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = logColor
                            )
                            if (!log.stackTrace.isNullOrBlank()) {
                                Text(
                                    text = log.stackTrace,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFF8A80),
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// HELPER COMPONENTS
// =========================================================================

@Composable
fun StatusBadge(
    status: StatusIndicator,
    customText: String? = null
) {
    val (color, text) = when (status) {
        StatusIndicator.SUCCESS -> Pair(Color(0xFF2E7D32), customText ?: "SUCCESS")
        StatusIndicator.WARNING -> Pair(Color(0xFFF57C00), customText ?: "WARNING")
        StatusIndicator.FAILED -> Pair(MaterialTheme.colorScheme.error, customText ?: "FAILED")
        StatusIndicator.RUNNING -> Pair(MaterialTheme.colorScheme.primary, "RUNNING")
        StatusIndicator.IDLE -> Pair(Color.Gray, customText ?: "IDLE")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
