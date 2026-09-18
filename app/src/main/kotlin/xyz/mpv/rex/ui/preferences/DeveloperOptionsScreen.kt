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

@Serializable
object DeveloperOptionsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current

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
                        GroupedPreferenceCard(position = GroupPosition.ONLY) {
                            Preference(
                                title = { Text(stringResource(id = R.string.pref_cloudstream_test_center_title)) },
                                summary = {
                                    Text(
                                        stringResource(id = R.string.pref_cloudstream_test_center_summary),
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
