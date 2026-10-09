package xyz.mpv.rex.ui.preferences

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.elevation.AdminSessionManager
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

@Serializable
object DeveloperOptionsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val authManager = koinInject<AuthManager>()
        val adminSessionManager = koinInject<AdminSessionManager>()
        val isAdmin by authManager.isAdmin.collectAsState()
        val isElevated by adminSessionManager.isElevated.collectAsState()

        if (!isAdmin || !isElevated) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Access Restricted: Administrator privileges and an active administrative elevation session are required.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return
        }

        val isDark = androidx.compose.foundation.isSystemInDarkTheme()

        val context = LocalContext.current

        Scaffold(
            containerColor = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background,
            topBar = {
                xyz.mpv.rex.ui.components.glass.GlassTopBar(
                    title = stringResource(id = R.string.pref_developer_options_title),
                    onBackClick = { backstack.removeLastOrNull() },
                    actions = {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://github.com/NithinXharma/MAx-player".toUri())
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = "GitHub Repository",
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                    PreferenceSectionHeader(title = "Firebase Extension Access Control (RBAC)")
                    GroupedListColumn {
                        GroupedPreferenceCard(position = GroupPosition.FIRST) {
                            Preference(
                                title = { Text("Plans Management") },
                                summary = {
                                    Text(
                                        "Configure tier plans (Free, Premium, VIP, Admin) & assign allowed extensions",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Layers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    backstack.add(xyz.mpv.rex.ui.preferences.admin.PlansManagementScreenRoute)
                                }
                            )
                        }
                        GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
                            Preference(
                                title = { Text("User Permissions & Overrides") },
                                summary = {
                                    Text(
                                        "Assign user plans, grant custom extensions, and configure user blacklists",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.ManageAccounts,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    backstack.add(xyz.mpv.rex.ui.preferences.admin.UserPermissionsScreenRoute)
                                }
                            )
                        }
                        GroupedPreferenceCard(position = GroupPosition.LAST) {
                            Preference(
                                title = { Text("Extension Assignment Matrix") },
                                summary = {
                                    Text(
                                        "Assign or unassign individual extensions across all plans in real time",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.ChecklistRtl,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    backstack.add(xyz.mpv.rex.ui.preferences.admin.ExtensionAssignmentScreenRoute)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PreferenceSectionHeader(title = "Firebase Provider Synchronization & Discovery")
                    GroupedListColumn {
                        GroupedPreferenceCard(position = GroupPosition.FIRST) {
                            Preference(
                                title = { Text("Trigger Firebase Provider Sync") },
                                summary = {
                                    Text(
                                        "Resolve user plan/permissions and install assigned extensions silently",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    val syncService = org.koin.core.context.GlobalContext.get().get<xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService>()
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        syncService.syncUserProviders(force = true)
                                    }
                                }
                            )
                        }
                        GroupedPreferenceCard(position = GroupPosition.LAST) {
                            Preference(
                                title = { Text("Run Firebase Auto-Discovery Database Sync") },
                                summary = {
                                    Text(
                                        "Scan repositories, parse manifests, and automatically mirror all metadata into Firestore",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    val discoveryService = org.koin.core.context.GlobalContext.get().get<xyz.mpv.rex.cinehub.provider.server.FirebaseAutoDiscoveryService>()
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        discoveryService.discoverAndSyncAll()
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PreferenceSectionHeader(title = "CloudStream Integration")
                    GroupedListColumn {
                        GroupedPreferenceCard(position = GroupPosition.ONLY) {
                            Preference(
                                title = { Text("Repository Presets & MegaRepo") },
                                summary = {
                                    Text(
                                        "MegaRepo one-tap install and 9 verified CloudStream community feeds",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AllInclusive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    backstack.add(RepositoryPresetsScreenRoute)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PreferenceSectionHeader(title = "System & Hardware Diagnostics")
                    GroupedListColumn {
                        GroupedPreferenceCard(position = GroupPosition.FIRST) {
                            Preference(
                                title = { Text("Decoder & Codec Information") },
                                summary = {
                                    Text(
                                        "Inspect Android MediaCodec hardware decoders and profiles",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    backstack.add(CodecInformationScreen)
                                }
                            )
                        }

                        GroupedPreferenceCard(position = GroupPosition.LAST) {
                            Preference(
                                title = { Text("Open Source Libraries") },
                                summary = {
                                    Text(
                                        "View third-party open source licenses and credits",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    backstack.add(LibrariesScreen)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current + 16.dp))
                }
            }
        }
    }
}
