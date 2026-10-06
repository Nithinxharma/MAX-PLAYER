package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Root Category 2: General Preferences Screen
 *
 * Centralizes all playback engine, control layouts, media decoders, subtitles,
 * audio settings, local media library, OTT integrations, and role-aware advanced configurations.
 */
@Serializable
object GeneralPreferencesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val isDark = isSystemInDarkTheme()
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

        val authManager = koinInject<AuthManager>()
        val isAdmin by authManager.isAdmin.collectAsState()

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = "General Settings",
                    onBackClick = { backstack.removeLastOrNull() }
                )
            },
            containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background
        ) { padding ->
            ProvidePreferenceLocals {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(top = 8.dp, bottom = navBarHeight + 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Playback & Controls Section
                    item {
                        GlassCategoryHeader(title = "Playback & Engine Controls", icon = Icons.Outlined.PlayCircle)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_player),
                                subtitle = stringResource(id = R.string.pref_player_summary),
                                icon = Icons.Outlined.PlayCircle,
                                showDivider = true,
                                onClick = { backstack.add(PlayerPreferencesScreen) }
                            )

                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_layout_title),
                                subtitle = stringResource(id = R.string.pref_layout_summary),
                                icon = Icons.AutoMirrored.Outlined.ViewQuilt,
                                showDivider = true,
                                onClick = { backstack.add(PlayerControlsPreferencesScreen) }
                            )

                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_gesture),
                                subtitle = stringResource(id = R.string.pref_gesture_summary),
                                icon = Icons.Outlined.Gesture,
                                showDivider = false,
                                onClick = { backstack.add(GesturePreferencesScreen) }
                            )
                        }
                    }

                    // Media Decoders & Audio Section
                    item {
                        GlassCategoryHeader(title = "Media Decoders & Audio", icon = Icons.Outlined.Memory)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_decoder),
                                subtitle = stringResource(id = R.string.pref_decoder_summary),
                                icon = Icons.Outlined.Memory,
                                showDivider = true,
                                onClick = { backstack.add(DecoderPreferencesScreen) }
                            )

                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_subtitles),
                                subtitle = stringResource(id = R.string.pref_subtitles_summary),
                                icon = Icons.Outlined.Subtitles,
                                showDivider = true,
                                onClick = { backstack.add(SubtitlesPreferencesScreen) }
                            )

                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_audio),
                                subtitle = stringResource(id = R.string.pref_audio_summary),
                                icon = Icons.Outlined.Audiotrack,
                                showDivider = true,
                                onClick = { backstack.add(AudioPreferencesScreen) }
                            )

                            GlassPreferenceItem(
                                title = stringResource(R.string.pref_decoder_codec_info_title),
                                subtitle = stringResource(R.string.pref_decoder_codec_info_summary),
                                icon = Icons.Outlined.Tune,
                                showDivider = false,
                                onClick = { backstack.add(CodecInformationScreen) }
                            )
                        }
                    }

                    // Media Library & Storage Section
                    item {
                        GlassCategoryHeader(title = "Media Library & Storage", icon = Icons.Outlined.VideoLibrary)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = stringResource(R.string.pref_media_library_title),
                                subtitle = stringResource(R.string.pref_media_library_summary),
                                icon = Icons.Outlined.VideoLibrary,
                                showDivider = true,
                                onClick = { backstack.add(MediaLibraryPreferencesScreen) }
                            )

                            GlassPreferenceItem(
                                title = stringResource(R.string.pref_folders_title),
                                subtitle = stringResource(R.string.pref_folders_summary),
                                icon = Icons.Outlined.Folder,
                                showDivider = false,
                                onClick = { backstack.add(FoldersPreferencesScreen) }
                            )
                        }
                    }

                    // Streaming & Integrations Section
                    item {
                        GlassCategoryHeader(title = "Streaming & Integrations", icon = Icons.Outlined.Tv)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = "CineTV Live & Playlist",
                                subtitle = "Manage IPTV credentials, authentication & stream mappings",
                                icon = Icons.Outlined.Tv,
                                showDivider = true,
                                onClick = { backstack.add(xyz.mpv.rex.cinetv.ui.CineTvSettingsScreen) }
                            )

                            GlassPreferenceItem(
                                title = "Jellyfin Server Sync",
                                subtitle = "Connect to Jellyfin instance for external playback sync",
                                icon = Icons.Outlined.VideoLibrary,
                                showDivider = true,
                                onClick = { backstack.add(xyz.mpv.rex.jellyfin.ui.JellyfinSettingsScreen) }
                            )

                            GlassPreferenceItem(
                                title = "yt-dlp Engine",
                                subtitle = "Configure MAX STREAM ytdl stream resolvers & parameters",
                                icon = Icons.Outlined.CloudDownload,
                                showDivider = true,
                                onClick = { backstack.add(YtdlSettingsScreen) }
                            )

                            GlassPreferenceItem(
                                title = "Shorts & Mini-Player",
                                subtitle = "Manage short-form vertical feeds and compact player behavior",
                                icon = Icons.Outlined.SmartDisplay,
                                showDivider = false,
                                onClick = { backstack.add(ShortsPreferencesScreen) }
                            )
                        }
                    }

                    // Advanced & Developer Options (Role-aware: Shown automatically for Admin / Dev)
                    if (isAdmin) {
                        item {
                            GlassCategoryHeader(title = "Advanced & Developer Utilities", icon = Icons.Outlined.Code)
                            GlassSettingsSection {
                                GlassPreferenceItem(
                                    title = stringResource(R.string.pref_advanced),
                                    subtitle = stringResource(id = R.string.pref_advanced_summary),
                                    icon = Icons.Outlined.Code,
                                    showDivider = true,
                                    onClick = { backstack.add(AdvancedPreferencesScreen) }
                                )

                                GlassPreferenceItem(
                                    title = stringResource(id = R.string.pref_developer_options_title),
                                    subtitle = stringResource(id = R.string.pref_developer_options_summary),
                                    icon = Icons.Outlined.Build,
                                    showDivider = true,
                                    onClick = { backstack.add(DeveloperOptionsScreen) }
                                )

                                GlassPreferenceItem(
                                    title = "mpv Configuration Editor",
                                    subtitle = "Edit mpv.conf and fine-tune playback engine parameters",
                                    icon = Icons.Outlined.Terminal,
                                    showDivider = false,
                                    onClick = { backstack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.MPV_CONF)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
