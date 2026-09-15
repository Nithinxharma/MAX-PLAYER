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
    var selectedTab by remember { mutableIntStateOf(0) }
    val installedList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())
    var availablePlugins by remember { mutableStateOf<List<AvailablePlugin>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isRefreshingCatalog by remember { mutableStateOf(false) }
    var pluginToUninstall by remember { mutableStateOf<InstalledExtension?>(null) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            withContext(Dispatchers.IO) {
                availablePlugins = repositoryManager.getCachedPlugins()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Extensions") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToRepositories) {
                            Icon(Icons.Outlined.Source, contentDescription = "Repositories")
                        }
                    }
                )
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
                        text = { Text("Discover") },
                        icon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(availablePlugins, key = { it.internalName }) { plugin ->
                        val isInstalled = installedList.any { it.pkgName == plugin.internalName }
                        AvailablePluginCard(
                            plugin = plugin,
                            isInstalled = isInstalled,
                            onInstall = {
                                scope.launch(Dispatchers.IO) {
                                    val success = extensionManager.installExtension(plugin)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, if (success) "Installed" else "Install Failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        
        pluginToUninstall?.let { ext ->
            AlertDialog(
                onDismissRequest = { pluginToUninstall = null },
                title = { Text("Uninstall Extension?") },
                text = { Text("Are you sure you want to uninstall ${ext.name}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                extensionManager.uninstallExtension(ext.pkgName)
                                withContext(Dispatchers.Main) {
                                    pluginToUninstall = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Uninstall")
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
}

@Composable
fun InstalledExtensionCard(
    ext: InstalledExtension,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ext.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(ext.version, style = MaterialTheme.typography.bodySmall)
            }
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

@Composable
fun AvailablePluginCard(
    plugin: AvailablePlugin,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    var isInstalling by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!plugin.description.isNullOrBlank()) {
                    Text(plugin.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            if (isInstalled) {
                Text("Installed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            } else {
                Button(
                    onClick = {
                        isInstalling = true
                        onInstall()
                        // isInstalling would ideally be tied to a state flow
                    },
                    enabled = !isInstalling
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Install")
                    }
                }
            }
        }
    }
}
