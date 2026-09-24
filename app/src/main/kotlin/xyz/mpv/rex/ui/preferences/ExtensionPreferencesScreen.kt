package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.ui.components.glass.*
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamShimmer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPreferencesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRepositories: () -> Unit,
    onNavigateToInstalled: () -> Unit,
    onNavigateToPresets: () -> Unit = {},
    onNavigateToTestCenter: () -> Unit = {},
    onNavigateToForceActivation: () -> Unit = {},
    onNavigateToExecutionTrace: () -> Unit = {},
    extensionManager: ExtensionManager = koinInject(),
    repositoryManager: RepositoryManager = koinInject(),
    registry: ProviderRegistry = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val installedList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())
    val repos by repositoryManager.getAllRepositories().collectAsState(initial = emptyList())
    val activeProviders by registry.activeProviders.collectAsState()
    val isUpdating by extensionManager.isUpdating.collectAsState()
    var showDiagnostics by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background,
        topBar = {
            GlassTopBar(
                title = "Extensions",
                onBackClick = onNavigateBack
            )
        }
    ) { paddingValues ->
        ProvidePreferenceLocals {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // Third-Party Extension Notice Banner in Glass
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
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
                            tint = MaxStreamTheme.CrimsonAccent,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Third-Party Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Extensions are provided by third parties. MaxStream provides the extension framework and does not host, control, or verify third-party media sources.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                GlassCategoryHeader(title = "Extension Management", icon = Icons.Outlined.Extension)

                GlassSettingsSection {
                    val activeCount = installedList.count { it.isEnabled }
                    GlassPreferenceItem(
                        title = "Installed Extensions",
                        subtitle = if (installedList.isEmpty()) "Browse & install extensions from repositories"
                        else "$activeCount active • ${installedList.size} installed",
                        icon = Icons.Outlined.Extension,
                        badge = if (installedList.isNotEmpty()) "${installedList.size}" else null,
                        showDivider = true,
                        onClick = onNavigateToInstalled
                    )

                    GlassPreferenceItem(
                        title = "Repository Presets & MegaRepo",
                        subtitle = "One-tap setup for MegaRepo and 9 curated CloudStream sources",
                        icon = Icons.Outlined.AllInclusive,
                        showDivider = true,
                        onClick = onNavigateToPresets
                    )

                    GlassPreferenceItem(
                        title = "Extension Repositories",
                        subtitle = if (repos.isEmpty()) "Add CloudStream or community repositories"
                        else "${repos.size} repositories configured",
                        icon = Icons.Outlined.CloudQueue,
                        badge = if (repos.isNotEmpty()) "${repos.size}" else null,
                        showDivider = false,
                        onClick = onNavigateToRepositories
                    )
                }

                GlassCategoryHeader(title = "Tools & Diagnostics", icon = Icons.Outlined.Science)

                GlassSettingsSection {
                    GlassPreferenceItem(
                        title = "CloudStream Diagnostic & Test Center",
                        subtitle = "Multi-tool test suite: provider search test, live 16-stage trace & DEX activation",
                        icon = Icons.Outlined.Science,
                        showDivider = true,
                        onClick = onNavigateToTestCenter
                    )

                    GlassPreferenceItem(
                        title = "Update All Extensions",
                        subtitle = if (isUpdating) "Syncing repositories and downloading updates…" else "Sync all repositories and install newer provider versions",
                        icon = Icons.Outlined.Update,
                        enabled = !isUpdating,
                        badge = if (isUpdating) "UPDATING" else null,
                        showDivider = true,
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

                    GlassPreferenceItem(
                        title = "Clear Extension Cache",
                        subtitle = "Clear cached provider manifests and network temporary files",
                        icon = Icons.Outlined.CleaningServices,
                        showDivider = true,
                        onClick = {
                            extensionManager.clearCache()
                            Toast.makeText(context, "Extension cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    )

                    GlassPreferenceItem(
                        title = "Framework Diagnostics",
                        subtitle = "View active runtime providers and framework status",
                        icon = Icons.Outlined.Assessment,
                        showDivider = false,
                        onClick = { showDiagnostics = true }
                    )
                }
            }
        }
    }

    if (showDiagnostics) {
        MaxStreamGlassDialog(
            onDismissRequest = { showDiagnostics = false },
            title = "Extension Framework Diagnostics",
            icon = Icons.Outlined.Assessment,
            confirmButton = {
                xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton(
                    text = "Close",
                    variant = xyz.mpv.rex.ui.components.glass.GlassButtonVariant.Primary,
                    onClick = { showDiagnostics = false }
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("• Active Providers: ${activeProviders.size}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                activeProviders.forEach { p ->
                    Text("  - ${p.name} (v${p.version}) [${p.id}]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Installed Extensions: ${installedList.size}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("• Synced Repositories: ${repos.size}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("• Streaming Engine: Native MPV Integration", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("• Architecture: Declarative / Bridge Architecture", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

