package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.provider.server.ServerProviderSyncService
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.cinehub.extension.util.LanguageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledExtensionsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRepositories: () -> Unit,
    extensionManager: ExtensionManager = koinInject(),
    repositoryManager: RepositoryManager = koinInject(),
    registry: ProviderRegistry = koinInject(),
    serverProviderSyncService: ServerProviderSyncService = koinInject()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Installed, 1 = Discover Catalog
    val installedList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())
    var availablePlugins by remember { mutableStateOf<List<AvailablePlugin>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguageFilter by remember { mutableStateOf("All") }
    var isRefreshingCatalog by remember { mutableStateOf(false) }
    var pluginToUninstall by remember { mutableStateOf<InstalledExtension?>(null) }

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
                    // Sync Managed Server Providers
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val success = serverProviderSyncService.syncProviders(force = true)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (success) "Server provider manifest synced successfully" else "Provider sync completed with local fallbacks",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.CloudSync, contentDescription = "Sync Server Providers")
                    }

                    if (selectedTab == 0) {
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
                                text = "Discover provider extensions from your repositories to expand MaxStream sources.",
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
                        placeholder = { Text("Filter extensions by name, tag, or language…") },
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

                    // Language Quick Filter Row
                    val availableLanguages = remember(availablePlugins) {
                        val langs = availablePlugins.mapNotNull { it.lang?.trim() }.filter { it.isNotBlank() }
                        val distinctLangs = langs.map { LanguageUtils.formatLanguage(it) }.distinct().sorted()
                        listOf("All") + distinctLangs
                    }

                    if (availableLanguages.size > 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableLanguages.forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguageFilter == lang,
                                    onClick = { selectedLanguageFilter = lang },
                                    label = { Text(lang) },
                                    leadingIcon = if (selectedLanguageFilter == lang) {
                                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    val filteredPlugins = remember(availablePlugins, searchQuery, selectedLanguageFilter) {
                        var list = availablePlugins
                        if (selectedLanguageFilter != "All") {
                            list = list.filter {
                                LanguageUtils.formatLanguage(it.lang).equals(selectedLanguageFilter, ignoreCase = true)
                            }
                        }
                        if (searchQuery.isNotBlank()) {
                            list = list.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                (it.description?.contains(searchQuery, ignoreCase = true) == true) ||
                                it.tvTypes.any { t -> t.contains(searchQuery, ignoreCase = true) } ||
                                (it.lang?.contains(searchQuery, ignoreCase = true) == true) ||
                                LanguageUtils.formatLanguage(it.lang).contains(searchQuery, ignoreCase = true)
                            }
                        }
                        list
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = ext.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("v${ext.version}", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }

                // Extension Language Badge
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Translate,
                                contentDescription = "Language",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${LanguageUtils.formatLanguage(ext.lang)} [${LanguageUtils.getBadge(ext.lang)}]",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (!ext.description.isNullOrBlank()) {
                    Text(
                        text = ext.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
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

            // Language badge & Type tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Extension Language Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Translate,
                            contentDescription = "Language",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "${LanguageUtils.formatLanguage(plugin.lang)} [${LanguageUtils.getBadge(plugin.lang)}]",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                plugin.tvTypes.forEach { type ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            if (!plugin.description.isNullOrBlank()) {
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

