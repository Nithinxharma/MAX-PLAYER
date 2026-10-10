package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.MegaRepoInstallResult
import xyz.mpv.rex.cinehub.extension.model.RepoPresetItem
import xyz.mpv.rex.cinehub.extension.model.RepoVerificationResult
import xyz.mpv.rex.cinehub.provider.server.ServerProviderSyncService
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.cinehub.extension.util.LanguageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledExtensionsScreen(
    onNavigateBack: () -> Unit,
    extensionManager: ExtensionManager = koinInject(),
    repositoryManager: RepositoryManager = koinInject(),
    registry: ProviderRegistry = koinInject(),
    serverProviderSyncService: ServerProviderSyncService = koinInject()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Installed, 1 = Discover Catalog, 2 = Repositories & Presets

    val installedList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())
    val reposList by repositoryManager.getAllRepositories().collectAsState(initial = emptyList())

    var availablePlugins by remember { mutableStateOf<List<AvailablePlugin>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguageFilter by remember { mutableStateOf("All") }
    var isRefreshingCatalog by remember { mutableStateOf(false) }
    var pluginToUninstall by remember { mutableStateOf<InstalledExtension?>(null) }

    // Repositories state
    var isSyncingAllRepos by remember { mutableStateOf(false) }
    var isInstallingMegaRepo by remember { mutableStateOf(false) }
    var megaRepoResult by remember { mutableStateOf<MegaRepoInstallResult?>(null) }
    var verificationMap by remember { mutableStateOf<Map<String, RepoVerificationResult>>(emptyMap()) }
    var isVerifyingAll by remember { mutableStateOf(false) }

    // Dialogs
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<ExtensionRepo?>(null) }
    var showImportExportDialog by remember { mutableStateOf(false) }
    var importExportJsonText by remember { mutableStateOf("") }

    // Sync & Catalog Loader
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
                title = {
                    Column {
                        Text("Extensions & Providers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${installedList.size} Active • ${reposList.size} Repositories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://github.com/NithinXharma/MAx-player".toUri())
                            )
                        }
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = "GitHub Repository")
                    }

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
                    } else if (selectedTab == 1) {
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
                    } else if (selectedTab == 2) {
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    importExportJsonText = repositoryManager.exportRepositoriesJson()
                                    withContext(Dispatchers.Main) {
                                        showImportExportDialog = true
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Code, contentDescription = "Import/Export Repos")
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
            // Tab Row: Installed (0) | Discover Catalog (1) | Repositories & Presets (2)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Installed (${installedList.size})", fontSize = 12.sp) },
                    icon = { Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Discover", fontSize = 12.sp) },
                    icon = { Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Repositories (${reposList.size})", fontSize = 12.sp) },
                    icon = { Icon(Icons.Outlined.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: INSTALLED EXTENSIONS
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
                                    text = "Discover provider extensions from your repositories or enable MegaRepo to expand MaxStream sources.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { selectedTab = 1 }) {
                                        Icon(Icons.Outlined.Explore, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Discover")
                                    }
                                    OutlinedButton(onClick = { selectedTab = 2 }) {
                                        Icon(Icons.Outlined.CloudQueue, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Repositories")
                                    }
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
                }
                1 -> {
                    // TAB 1: DISCOVER CATALOG
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search catalog by name, tag, or language…") },
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
                                    Text("Make sure you have added repositories or enabled MegaRepo in the Repositories tab.", style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = { selectedTab = 2 }) {
                                        Text("Open Repositories & Presets")
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
                2 -> {
                    // TAB 2: REPOSITORIES & PRESETS HUB
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // MegaRepo Master Hub Hero Banner
                        item {
                            MegaRepoBannerCard(
                                isInstalling = isInstallingMegaRepo,
                                onInstallClick = {
                                    isInstallingMegaRepo = true
                                    scope.launch(Dispatchers.IO) {
                                        val res = repositoryManager.installMegaRepo()
                                        repositoryManager.syncAllRepositories()
                                        val fresh = repositoryManager.getCachedPlugins()
                                        withContext(Dispatchers.Main) {
                                            megaRepoResult = res
                                            availablePlugins = fresh
                                            isInstallingMegaRepo = false
                                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                megaRepoResult = megaRepoResult
                            )
                        }

                        // Repositories Action Bar
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val count = repositoryManager.addAllPresets()
                                            repositoryManager.syncAllRepositories()
                                            val fresh = repositoryManager.getCachedPlugins()
                                            withContext(Dispatchers.Main) {
                                                availablePlugins = fresh
                                                Toast.makeText(context, "Added & synced $count preset repositories", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                ) {
                                    Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add All Presets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { showAddRepoDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Custom Repo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Presets Header & Catalog
                        item {
                            Text(
                                text = "Popular Repository Presets",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(RepositoryManager.BUILT_IN_PRESETS, key = { it.url }) { preset ->
                            val isInstalled = reposList.any { it.url.equals(preset.url, ignoreCase = true) }
                            val verification = verificationMap[preset.url]

                            PresetCardItem(
                                preset = preset,
                                isInstalled = isInstalled,
                                verification = verification,
                                onEnable = {
                                    scope.launch(Dispatchers.IO) {
                                        repositoryManager.addRepository(preset.url, preset.name, preset.description)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Enabled ${preset.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onVerify = {
                                    scope.launch(Dispatchers.IO) {
                                        val res = repositoryManager.verifyRepository(preset.url)
                                        withContext(Dispatchers.Main) {
                                            verificationMap = verificationMap + (preset.url to res)
                                        }
                                    }
                                },
                                onCopyUrl = {
                                    clipboardManager.setText(AnnotatedString(preset.url))
                                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Active Installed Repositories Header & List
                        item {
                            Text(
                                text = "Active Repositories (${reposList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        if (reposList.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.CloudQueue, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("No active repositories added", fontWeight = FontWeight.Bold)
                                        Text("Select 'Add All Presets' above or install MegaRepo to start.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            items(reposList, key = { it.url }) { repo ->
                                RepositoryCard(
                                    repo = repo,
                                    onSync = {
                                        scope.launch(Dispatchers.IO) {
                                            val result = repositoryManager.syncRepository(repo.url)
                                            val fresh = repositoryManager.getCachedPlugins()
                                            withContext(Dispatchers.Main) {
                                                availablePlugins = fresh
                                                if (result.error == null) {
                                                    Toast.makeText(context, "Synced ${result.plugins.size} plugins from ${repo.name}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Sync error: ${result.error}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    onDelete = { repoToDelete = repo }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // UNINSTALL DIALOG
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

    // ADD CUSTOM REPOSITORY DIALOG
    if (showAddRepoDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddRepoDialog = false },
            onAdd = { url, name ->
                showAddRepoDialog = false
                scope.launch(Dispatchers.IO) {
                    repositoryManager.addRepository(url, name)
                    val fresh = repositoryManager.getCachedPlugins()
                    withContext(Dispatchers.Main) {
                        availablePlugins = fresh
                        Toast.makeText(context, "Repository added & syncing…", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // DELETE REPOSITORY CONFIRMATION DIALOG
    repoToDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text("Remove Repository") },
            text = { Text("Are you sure you want to remove \"${repo.name}\"? Installed extensions from this repository will remain installed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = repoToDelete
                        repoToDelete = null
                        if (target != null) {
                            scope.launch(Dispatchers.IO) {
                                repositoryManager.removeRepository(target)
                            }
                        }
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // IMPORT / EXPORT JSON DIALOG
    if (showImportExportDialog) {
        AlertDialog(
            onDismissRequest = { showImportExportDialog = false },
            title = { Text("Import / Export Repositories", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Copy JSON below to backup, or paste a JSON array of repositories to import.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        OutlinedTextField(
                            value = importExportJsonText,
                            onValueChange = { importExportJsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val count = repositoryManager.importRepositoriesJson(importExportJsonText)
                            repositoryManager.syncAllRepositories()
                            val fresh = repositoryManager.getCachedPlugins()
                            withContext(Dispatchers.Main) {
                                availablePlugins = fresh
                                showImportExportDialog = false
                                Toast.makeText(context, "Imported $count repositories", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Import JSON")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(importExportJsonText))
                            Toast.makeText(context, "Copied JSON to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copy")
                    }
                    TextButton(onClick = { showImportExportDialog = false }) {
                        Text("Close")
                    }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun RepositoryCard(
    repo: ExtensionRepo,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(repo.lastSync) {
        if (repo.lastSync > 0) {
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(repo.lastSync))
        } else "Never"
    }

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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = repo.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Last synced: $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Outlined.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun MegaRepoBannerCard(
    isInstalling: Boolean,
    onInstallClick: () -> Unit,
    megaRepoResult: MegaRepoInstallResult?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "MegaRepo Master Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Automated CloudStream Multi-Repo Index",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("Recommended", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
                }
            }

            Text(
                text = "Import and sync all top verified CloudStream extension feeds in one tap, including Hindi, Anime, English, IPTV, and high-speed streaming sources.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Button(
                onClick = onInstallClick,
                enabled = !isInstalling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Installing MegaRepo & Community Feeds...")
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install MegaRepo (One-Tap Setup)", fontWeight = FontWeight.Bold)
                }
            }

            if (megaRepoResult != null) {
                Text(
                    text = "Status: ${megaRepoResult.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (megaRepoResult.isSuccess) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PresetCardItem(
    preset: RepoPresetItem,
    isInstalled: Boolean,
    verification: RepoVerificationResult?,
    onEnable: () -> Unit,
    onVerify: () -> Unit,
    onCopyUrl: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Author: ${preset.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isInstalled) {
                    Badge(containerColor = Color(0xFF2E7D32)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Installed", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = preset.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCopyUrl,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (verification != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (verification.isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                    )
                    Text(
                        text = if (verification.isOnline) {
                            "Online • HTTP ${verification.httpCode} • ${verification.latencyMs}ms (${verification.pluginCount} plugins listed)"
                        } else {
                            "Offline: ${verification.error ?: "HTTP ${verification.httpCode}"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = if (verification.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onVerify,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check Connection", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onEnable,
                    enabled = !isInstalled,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInstalled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isInstalled) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isInstalled) "Enabled" else "Enable", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, name: String?) -> Unit
) {
    var repoUrl by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Extension Repository") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter a repository URL (repo.json or plugins.json) or pick a popular preset:",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://.../repo.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = { Text("Repository Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoUrl.isNotBlank()) {
                        onAdd(repoUrl, repoName.ifBlank { null })
                    }
                },
                enabled = repoUrl.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
