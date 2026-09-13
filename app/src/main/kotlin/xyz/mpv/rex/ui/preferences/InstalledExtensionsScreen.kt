package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledExtensionsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRepositories: () -> Unit,
    extensionManager: ExtensionManager = koinInject(),
    repositoryManager: RepositoryManager = koinInject(),
    registry: ProviderRegistry = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Installed, 1 = Discover Catalog
    val installedList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())
    var availablePlugins by remember { mutableStateOf<List<AvailablePlugin>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isRefreshingCatalog by remember { mutableStateOf(false) }
    var pluginToUninstall by remember { mutableStateOf<InstalledExtension?>(null) }
    
    // Testing functionality
    var showTestDialog by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isTesting by remember { mutableStateOf(false) }

    // Load available plugins from repositories cache
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            val cached = repositoryManager.getCachedPlugins()
            if (cached.isEmpty()) {
                isRefreshingCatalog = true
                scope.launch(Dispatchers.IO) {
                    repositoryManager.syncAllRepositories()
                    val fresh = repositoryManager.getCachedPlugins()
                    withContext(Dispatchers.Main) {
                        availablePlugins = fresh
                        isRefreshingCatalog = false
                    }
                }
            } else {
                availablePlugins = cached
            }
        }
    }

    fun testProviders() {
        showTestDialog = true
        isTesting = true
        testResults = emptyMap()
        scope.launch(Dispatchers.IO) {
            val providers = registry.getEnabledProviders()
            val results = mutableMapOf<String, String>()
            for (provider in providers) {
                try {
                    val home = provider.getHomePage()
                    if (home.isNotEmpty() && home.first().items.isNotEmpty()) {
                        results[provider.name] = "OK (${home.first().items.size} items)"
                    } else {
                        results[provider.name] = "Failed (Empty results)"
                    }
                } catch (e: Exception) {
                    results[provider.name] = "Failed (${e.message ?: "Unknown error"})"
                }
            }
            withContext(Dispatchers.Main) {
                testResults = results
                isTesting = false
            }
        }
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { if (!isTesting) showTestDialog = false },
            title = { Text("Provider Test Results") },
            text = {
                if (isTesting) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Testing active providers...")
                    }
                } else if (testResults.isEmpty()) {
                    Text("No active providers to test.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(testResults.entries.toList(), key = { it.key }) { (name, result) ->
                            val isOk = result.startsWith("OK")
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    text = if (isOk) "Working" else "Failed",
                                    color = if (isOk) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (!isOk) {
                                Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestDialog = false }, enabled = !isTesting) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { testProviders() }) {
                            Icon(Icons.Outlined.NetworkCheck, contentDescription = "Test Active Providers")
                        }
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val count = extensionManager.updateAll()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, if (count > 0) "Updated $count extensions" else "All extensions are up to date", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Update, contentDescription = "Update All")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                isRefreshingCatalog = true
                                scope.launch(Dispatchers.IO) {
                                    repositoryManager.syncAllRepositories()
                                    val fresh = repositoryManager.getCachedPlugins()
                                    withContext(Dispatchers.Main) {
                                        availablePlugins = fresh
                                        isRefreshingCatalog = false
                                        Toast.makeText(context, "Catalog refreshed (${fresh.size} providers)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isRefreshingCatalog
                        ) {
                            if (isRefreshingCatalog) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Catalog")
                            }
                        }
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
            // Tab row: Installed vs Discover
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Installed (${installedList.size})") },
                    icon = { Icon(Icons.Outlined.Extension, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Discover & Install") },
                    icon = { Icon(Icons.Outlined.Explore, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                // INSTALLED EXTENSIONS TAB
                if (installedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Widgets,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "No Extensions Installed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Discover provider extensions from your repositories to expand CineHub sources.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { selectedTab = 1 }) {
                                Icon(Icons.Outlined.Explore, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Discover Extensions")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(installedList, key = { it.pkgName }) { ext ->
                            InstalledExtensionCard(
                                ext = ext,
                                onToggle = { enabled ->
                                    scope.launch(Dispatchers.IO) {
                                        extensionManager.toggleExtension(ext.pkgName, enabled)
                                    }
                                },
                                onUninstall = { pluginToUninstall = ext }
                            )
                        }
                    }
                }
            } else {
                // DISCOVER EXTENSIONS TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter extensions by name or tag…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val filteredPlugins = remember(availablePlugins, searchQuery) {
                        if (searchQuery.isBlank()) availablePlugins
                        else availablePlugins.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            (it.description?.contains(searchQuery, ignoreCase = true) == true) ||
                            it.tvTypes.any { t -> t.contains(searchQuery, ignoreCase = true) }
                        }
                    }

                    if (availablePlugins.isEmpty() && !isRefreshingCatalog) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Catalog is empty", fontWeight = FontWeight.Bold)
                                Text("Make sure you have added repositories and synced them.", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = onNavigateToRepositories) {
                                    Text("Manage Repositories")
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredPlugins, key = { "${it.repositoryUrl}_${it.internalName}" }) { plugin ->
                            val isInstalled = installedList.any { it.pkgName == plugin.internalName }
                            DiscoverPluginCard(
                                plugin = plugin,
                                isInstalled = isInstalled,
                                onInstall = {
                                    scope.launch(Dispatchers.IO) {
                                        val success = extensionManager.installExtension(plugin)
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                Toast.makeText(context, "Installed ${plugin.name}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to install ${plugin.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pluginToUninstall?.let { ext ->
        AlertDialog(
            onDismissRequest = { pluginToUninstall = null },
            title = { Text("Uninstall Extension") },
            text = { Text("Are you sure you want to uninstall \"${ext.name}\"? It will be removed from your active search and provider feeds.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pluginToUninstall
                        pluginToUninstall = null
                        if (target != null) {
                            scope.launch(Dispatchers.IO) {
                                extensionManager.uninstallExtension(target.pkgName)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Uninstalled ${target.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pluginToUninstall = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InstalledExtensionCard(
    ext: InstalledExtension,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!ext.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ext.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ext.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("v${ext.version}", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }
                if (!ext.description.isNullOrBlank()) {
                    Text(
                        text = ext.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = ext.isEnabled,
                    onCheckedChange = onToggle
                )
                IconButton(onClick = onUninstall) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DiscoverPluginCard(
    plugin: AvailablePlugin,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!plugin.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = plugin.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Extension, contentDescription = null)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${plugin.version}" + if (plugin.authors.isNotEmpty()) " by ${plugin.authors.joinToString()}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (isInstalled) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Installed") },
                        leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                } else {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install")
                    }
                }
            }

            if (!plugin.description.isNullOrBlank()) {
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (plugin.tvTypes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    plugin.tvTypes.forEach { type ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    }
}
