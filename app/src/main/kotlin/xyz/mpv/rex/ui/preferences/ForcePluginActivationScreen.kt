package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.model.DetectedPluginItem
import xyz.mpv.rex.cinehub.extension.model.DexAuditReport
import xyz.mpv.rex.cinehub.extension.model.PluginActivationState
import xyz.mpv.rex.cinehub.extension.model.PluginTestStepResult
import xyz.mpv.rex.cinehub.extension.model.RegisteredExtractorSummary
import xyz.mpv.rex.cinehub.extension.model.RegisteredProviderSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForcePluginActivationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ForcePluginActivationViewModel = koinInject()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val rawPlugins by viewModel.rawPlugins.collectAsState()
    val filteredPlugins by viewModel.filteredPlugins.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isBulkOperating by viewModel.isBulkOperating.collectAsState()
    val runtimeLogs by viewModel.runtimeLogs.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var showLogsBottomSheet by remember { mutableStateOf(false) }

    val activeCount = rawPlugins.count { it.isRuntimeActive }
    val installedCount = rawPlugins.count { it.isDbInstalled }
    val totalProvidersCount = rawPlugins.sumOf { it.registeredProviders.size }
    val totalExtractorsCount = rawPlugins.sumOf { it.registeredExtractors.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search plugins, packages, providers...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text("Force Plugin Activation", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "$activeCount Active • ${rawPlugins.size} Detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) viewModel.onSearchQueryChanged("")
                    }) {
                        Icon(
                            if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshDetectedPlugins() },
                        enabled = !isRefreshing && !isBulkOperating
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("⚡ Force Activate All") },
                            leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.forceActivateAll()
                                Toast.makeText(context, "Activating all plugins...", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🔄 Force Reload All") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.forceReloadAll()
                                Toast.makeText(context, "Reloading all plugins...", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📋 View Live Logs") },
                            leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showLogsBottomSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🗑 Clear Console Logs") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.clearLogs()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.forceActivateAll() },
                            enabled = !isBulkOperating && !isRefreshing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Activate All", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.forceReloadAll() },
                            enabled = !isBulkOperating && !isRefreshing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reload All", fontSize = 13.sp)
                        }
                    }

                    TextButton(onClick = { showLogsBottomSheet = true }) {
                        Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Logs (${runtimeLogs.size})", fontSize = 13.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Summary Health Header
            item {
                SystemSummaryBanner(
                    totalPlugins = rawPlugins.size,
                    activePlugins = activeCount,
                    installedCount = installedCount,
                    totalProviders = totalProvidersCount,
                    totalExtractors = totalExtractorsCount
                )
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PluginFilterTab.values().forEach { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { viewModel.onFilterChanged(tab) },
                            label = { Text(tab.label) },
                            leadingIcon = if (selectedFilter == tab) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            if (filteredPlugins.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ExtensionOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No plugins matching '$searchQuery'" else "No plugins detected",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.refreshDetectedPlugins() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Scan Storage & Database")
                            }
                        }
                    }
                }
            } else {
                items(filteredPlugins, key = { it.pkgName }) { plugin ->
                    PluginItemCard(
                        plugin = plugin,
                        onForceActivate = { viewModel.forceActivatePlugin(plugin.pkgName) },
                        onForceDeactivate = { viewModel.forceDeactivatePlugin(plugin.pkgName) },
                        onForceReload = { viewModel.forceReloadPlugin(plugin.pkgName) },
                        onForceDexLoad = { viewModel.forceDexLoad(plugin.pkgName) },
                        onRunTests = { viewModel.runBasicPluginTests(plugin.pkgName) }
                    )
                }
            }
        }
    }

    if (showLogsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLogsBottomSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Plugin Activation Console Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(runtimeLogs.joinToString("\n")))
                            Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Logs")
                        }
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear Logs")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (runtimeLogs.isEmpty()) {
                                item {
                                    Text("No console logs available yet. Perform an action to see real-time output.", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                items(runtimeLogs) { logLine ->
                                    val color = when {
                                        logLine.contains("FAILED") || logLine.contains("Error") || logLine.contains("❌") -> MaterialTheme.colorScheme.error
                                        logLine.contains("✅") || logLine.contains("Passed") -> MaterialTheme.colorScheme.primary
                                        logLine.contains("⚠️") -> Color(0xFFE65100)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(
                                        text = logLine,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = color
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

@Composable
fun SystemSummaryBanner(
    totalPlugins: Int,
    activePlugins: Int,
    installedCount: Int,
    totalProviders: Int,
    totalExtractors: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.FlashOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Force Activation Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activePlugins > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (activePlugins > 0) "$activePlugins Active" else "0 Active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (activePlugins > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                text = "Bypass automatic heuristics to directly load DEX binaries, instantiate plugin classes, and mount providers & extractors to ProviderRegistry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricChip(label = "Plugins", value = "$totalPlugins")
                MetricChip(label = "DB Installed", value = "$installedCount")
                MetricChip(label = "Providers", value = "$totalProviders")
                MetricChip(label = "Extractors", value = "$totalExtractors")
            }
        }
    }
}

@Composable
fun MetricChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PluginItemCard(
    plugin: DetectedPluginItem,
    onForceActivate: () -> Unit,
    onForceDeactivate: () -> Unit,
    onForceReload: () -> Unit,
    onForceDexLoad: () -> Unit,
    onRunTests: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val stateColor = when (plugin.state) {
        PluginActivationState.ACTIVE -> Color(0xFF2E7D32)
        PluginActivationState.DEX_LOADED_ONLY -> Color(0xFF0277BD)
        PluginActivationState.INACTIVE -> Color(0xFFF57C00)
        PluginActivationState.NOT_LOADED -> MaterialTheme.colorScheme.outline
        PluginActivationState.FILE_MISSING -> MaterialTheme.colorScheme.error
        PluginActivationState.ERROR -> MaterialTheme.colorScheme.error
    }

    val stateText = when (plugin.state) {
        PluginActivationState.ACTIVE -> "ACTIVE"
        PluginActivationState.DEX_LOADED_ONLY -> "DEX MOUNTED"
        PluginActivationState.INACTIVE -> "DISABLED"
        PluginActivationState.NOT_LOADED -> "UNLOADED"
        PluginActivationState.FILE_MISSING -> "FILE MISSING"
        PluginActivationState.ERROR -> "ERROR"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: State Badge + Title + Action Loading indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stateColor)
                    )
                    Column {
                        Text(
                            text = plugin.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = plugin.pkgName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(stateColor.copy(alpha = 0.15f))
                        .border(1.dp, stateColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stateText,
                        color = stateColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BadgeChip(label = "v${plugin.version}")
                if (plugin.isDbInstalled) BadgeChip(label = "DB Installed")
                if (plugin.fileExists) BadgeChip(label = "${plugin.fileSize / 1024} KB")
                BadgeChip(label = "${plugin.registeredProviders.size} Providers")
                if (plugin.registeredExtractors.isNotEmpty()) {
                    BadgeChip(label = "${plugin.registeredExtractors.size} Extractors")
                }
            }

            // Quick Actions Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plugin.isOperating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Processing...", style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(
                        onClick = onForceActivate,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Force Activate", fontSize = 12.sp)
                    }

                    if (plugin.isRuntimeActive || plugin.isDbEnabled) {
                        OutlinedButton(
                            onClick = onForceDeactivate,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Deactivate", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onForceReload,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reload", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = onForceDexLoad,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dex Load", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = onRunTests,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run Tests", fontSize = 12.sp)
                    }
                }
            }

            // Expand / Collapse Details Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Hide Details & Diagnostics" else "View Details, Metadata & Tests",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Details",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Expanded Panel
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sub-navigation tabs
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Metadata", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Providers (${plugin.registeredProviders.size})", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Dex Audit", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Tests (${plugin.testResults.size})", fontSize = 12.sp) }
                        )
                    }

                    when (selectedTab) {
                        0 -> MetadataTabContent(plugin)
                        1 -> ProvidersTabContent(plugin)
                        2 -> DexAuditTabContent(plugin, onRunDexAudit = onForceDexLoad)
                        3 -> TestResultsTabContent(plugin, onRunTests = onRunTests)
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MetadataTabContent(plugin: DetectedPluginItem) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailRow(label = "Package Name", value = plugin.pkgName)
        DetailRow(label = "File Path", value = plugin.localFilePath ?: "Not recorded")
        DetailRow(label = "File Size", value = "${plugin.fileSize} bytes")
        DetailRow(label = "DB Registered", value = if (plugin.isDbInstalled) "Yes (Enabled=${plugin.isDbEnabled})" else "No (Discovered on disk)")
        DetailRow(label = "Classes Discovered", value = if (plugin.discoveredClasses.isEmpty()) "None recorded" else plugin.discoveredClasses.joinToString(", "))

        if (!plugin.manifestJson.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manifest JSON Content:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(plugin.manifestJson))
                    Toast.makeText(context, "Manifest JSON copied", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy JSON", fontSize = 11.sp)
                }
            }
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = plugin.manifestJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ProvidersTabContent(plugin: DetectedPluginItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (plugin.registeredProviders.isEmpty()) {
            Text(
                "No providers registered from this plugin in APIHolder or ProviderRegistry. Click 'Force Activate' to load classes and register providers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            plugin.registeredProviders.forEach { prov ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prov.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (prov.isEnabled) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (prov.isEnabled) "ACTIVE" else "DISABLED",
                                    color = if (prov.isEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text("URL: ${prov.mainUrl}", style = MaterialTheme.typography.bodySmall)
                        Text("Lang: ${prov.lang} • Types: ${prov.supportedTypes.joinToString(", ").ifEmpty { "Default" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Capabilities: MainPage=${prov.hasMainPage}, QuickSearch=${prov.hasQuickSearch}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (plugin.registeredExtractors.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Registered Extractors (${plugin.registeredExtractors.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            plugin.registeredExtractors.forEach { ext ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(ext.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(ext.mainUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DexAuditTabContent(
    plugin: DetectedPluginItem,
    onRunDexAudit: () -> Unit
) {
    val report = plugin.auditReport
    if (report == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("No DEX audit performed yet for this plugin.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRunDexAudit) {
                Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Run Deep DEX Load & Audit")
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audit Result: ${if (report.success) "PASSED (${report.totalDurationMs}ms)" else "FAILED"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (report.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRunDexAudit) {
                    Text("Re-Audit", fontSize = 11.sp)
                }
            }

            DetailRow(label = "Zip Entries", value = "${report.zipEntriesCount}")
            DetailRow(label = "Manifest Plugin Class", value = report.manifestPluginClass ?: "None")
            DetailRow(label = "Classes in DEX", value = "${report.classesFromDex.size}")

            Text("Class Resolution Audit:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            report.classLoadAudits.forEach { audit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (audit.isClassFound) Color(0xFF2E7D32).copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audit.className,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Type: ${audit.resolvedType ?: "Unknown"} • Instantiable: ${audit.isInstantiable} • load(): ${audit.hasLoadMethod}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (audit.isClassFound) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (audit.isClassFound) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TestResultsTabContent(
    plugin: DetectedPluginItem,
    onRunTests: () -> Unit
) {
    val results = plugin.testResults
    if (results.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("No diagnostic tests executed yet for this plugin.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRunTests) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Run Basic Plugin Tests")
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diagnostic Test Pipeline:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRunTests) {
                    Text("Re-Run Tests", fontSize = 11.sp)
                }
            }

            results.forEach { test ->
                val statusColor = when (test.status) {
                    "PASSED" -> Color(0xFF2E7D32)
                    "FAILED" -> MaterialTheme.colorScheme.error
                    "SKIPPED" -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(test.testName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(statusColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${test.status} (${test.durationMs}ms)",
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (test.details.isNotBlank()) {
                            Text(test.details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (!test.errorMessage.isNullOrBlank()) {
                            Text("Error: ${test.errorMessage}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
