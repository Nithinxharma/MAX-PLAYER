package xyz.mpv.rex.ui.preferences

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
import xyz.mpv.rex.cinehub.diagnostic.CloudStreamTestCenterScreen
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

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.pref_developer_options_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = backstack::removeLastOrNull) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.secondary
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
                    PreferenceSectionHeader(title = "CloudStream Integration")
                    GroupedListColumn {
                        GroupedPreferenceCard(position = GroupPosition.FIRST) {
                            Preference(
                                title = { Text("CloudStream Diagnostic & Test Center") },
                                summary = {
                                    Text(
                                        "End-to-end integration suite, live 16-stage trace, force DEX activation, and link resolution tests",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Science,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    backstack.add(CloudStreamTestCenterScreen)
                                }
                            )
                        }
                        GroupedPreferenceCard(position = GroupPosition.LAST) {
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
