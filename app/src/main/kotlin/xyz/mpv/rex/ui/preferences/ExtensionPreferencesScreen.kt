package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPreferencesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRepositories: () -> Unit,
    onNavigateToInstalled: () -> Unit,
    onNavigateToTestCenter: () -> Unit = {},
    onNavigateToForceActivation: () -> Unit = {},
    extensionManager: ExtensionManager = koinInject(),
    repositoryManager: RepositoryManager = koinInject(),
    registry: ProviderRegistry = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val installedList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())
    val repos by repositoryManager.getAllRepositories().collectAsState(initial = emptyList())
    val activeProviders by registry.activeProviders.collectAsState()
    val isUpdating by extensionManager.isUpdating.collectAsState()
    var showDiagnostics by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Extensions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        ProvidePreferenceLocals {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Third-Party Extension Notice Banner (Mandatory)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Third-Party Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Extensions are provided by third parties. CineHub only provides the extension framework and does not control or verify the content, availability, or reliability of third-party sources.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Extension Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Preference(
                    title = { Text("Installed Extensions") },
                    summary = {
                        val activeCount = installedList.count { it.isEnabled }
                        Text(
                            if (installedList.isEmpty()) "Browse & install extensions from repositories"
                            else "$activeCount active • ${installedList.size} installed"
                        )
                    },
                    icon = { Icon(Icons.Outlined.Extension, contentDescription = null) },
                    onClick = onNavigateToInstalled
                )

                Preference(
                    title = { Text("Extension Repositories") },
                    summary = {
                        Text(
                            if (repos.isEmpty()) "Add CloudStream or community repositories"
                            else "${repos.size} repositories configured"
                        )
                    },
                    icon = { Icon(Icons.Outlined.CloudQueue, contentDescription = null) },
                    onClick = onNavigateToRepositories
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tools & Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Preference(
                    title = { Text("Force Plugin Activation") },
                    summary = { Text("Force load DEX binaries, activate plugins, reload & run tests") },
                    icon = { Icon(Icons.Outlined.FlashOn, contentDescription = null) },
                    onClick = onNavigateToForceActivation
                )

                Preference(
                    title = { Text("Update All Extensions") },
                    summary = { Text("Sync all repositories and install newer provider versions") },
                    icon = {
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Update, contentDescription = null)
                        }
                    },
                    enabled = !isUpdating,
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val updated = extensionManager.updateAll()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (updated > 0) "Updated $updated extensions successfully" else "All extensions are up-to-date",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )

                Preference(
                    title = { Text("Clear Extension Cache") },
                    summary = { Text("Clear cached provider manifests and network temporary files") },
                    icon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                    onClick = {
                        extensionManager.clearCache()
                        Toast.makeText(context, "Extension cache cleared", Toast.LENGTH_SHORT).show()
                    }
                )

                Preference(
                    title = { Text("CloudStream Test Center") },
                    summary = { Text("End-to-end integration and provider diagnostic suite") },
                    icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
                    onClick = onNavigateToTestCenter
                )

                Preference(
                    title = { Text("Framework Diagnostics") },
                    summary = { Text("View active runtime providers and framework status") },
                    icon = { Icon(Icons.Outlined.Assessment, contentDescription = null) },
                    onClick = { showDiagnostics = true }
                )
            }
        }
    }

    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Extension Framework Diagnostics") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("• Active Providers: ${activeProviders.size}", fontWeight = FontWeight.SemiBold)
                    activeProviders.forEach { p ->
                        Text("  - ${p.name} (v${p.version}) [${p.id}]", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Installed Extensions: ${installedList.size}", fontWeight = FontWeight.SemiBold)
                    Text("• Synced Repositories: ${repos.size}", fontWeight = FontWeight.SemiBold)
                    Text("• Streaming Engine: Native MPV Integration", fontWeight = FontWeight.SemiBold)
                    Text("• Architecture: Declarative / Bridge Architecture", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text("Close")
                }
            }
        )
    }
}
