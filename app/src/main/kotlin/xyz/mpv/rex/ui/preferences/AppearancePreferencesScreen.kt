package xyz.mpv.rex.ui.preferences

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassSwitchPreference
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.preferences.components.ThemePicker
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.locale.LocaleHelper

/**
 * Root Category 3: Dedicated Appearance Preferences Screen
 *
 * Provides comprehensive visual customization: Theme presets, AMOLED pure black mode,
 * dynamic colors, glassmorphic UI depth, animations, typography, and media thumbnail settings.
 */
@Serializable
object AppearancePreferencesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val isDark = isSystemInDarkTheme()
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

        val preferences = koinInject<AppearancePreferences>()

        val appTheme by preferences.appTheme.collectAsState()
        val amoledMode by preferences.amoledMode.collectAsState()
        val materialYou by preferences.materialYou.collectAsState()
        val enableModernGlassUI by preferences.enableModernGlassUI.collectAsState()
        val enableGlassPlayerControls by preferences.enableGlassPlayerControls.collectAsState()
        val enableGlassSeekbarBackground by preferences.enableGlassSeekbarBackground.collectAsState()
        val enableBounceAnimation by preferences.enableBounceAnimation.collectAsState()
        val useSystemFont by preferences.useSystemFont.collectAsState()
        val unlimitedNameLines by preferences.unlimitedNameLines.collectAsState()
        val showUnplayedOldVideoLabel by preferences.showUnplayedOldVideoLabel.collectAsState()
        val showNetworkThumbnails by preferences.showNetworkThumbnails.collectAsState()
        val useTmdbMetadata by preferences.useTmdbMetadata.collectAsState()
        val matchPlayerControlsToTheme by preferences.matchPlayerControlsToTheme.collectAsState()
        val hidePlayerButtonsBackground by preferences.hidePlayerButtonsBackground.collectAsState()

        val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
        var showLanguageDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = stringResource(R.string.pref_appearance_title),
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
                    // Theme & Color Palette Section
                    item {
                        GlassCategoryHeader(title = "Theme & Color Palette", icon = Icons.Outlined.Palette)
                        GlassSettingsSection {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "MaxStream Dark Theme",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Default Dark",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaxStreamTheme.CrimsonAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                ThemePicker(
                                    currentTheme = appTheme,
                                    isDarkMode = true,
                                    onThemeSelected = { preferences.appTheme.set(it) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_amoled_mode_title),
                                subtitle = stringResource(id = R.string.pref_appearance_amoled_mode_summary),
                                icon = Icons.Outlined.DarkMode,
                                checked = amoledMode,
                                onCheckedChange = { preferences.amoledMode.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = "Material You Dynamic Complement",
                                subtitle = "Complement MaxStream branding with system accent colors",
                                icon = Icons.Outlined.ColorLens,
                                checked = materialYou,
                                onCheckedChange = { preferences.materialYou.set(it) },
                                showDivider = false
                            )
                        }
                    }

                    // Glassmorphism & Visual Depth Section
                    item {
                        GlassCategoryHeader(title = "Glassmorphism & Depth", icon = Icons.Outlined.AutoAwesome)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_enable_modern_glass_ui_title),
                                subtitle = stringResource(id = R.string.pref_appearance_enable_modern_glass_ui_summary),
                                icon = Icons.Outlined.AutoAwesome,
                                checked = enableModernGlassUI,
                                onCheckedChange = { preferences.enableModernGlassUI.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = "Glass Player Controls",
                                subtitle = "Render video player panels and badges with blurred frosted glass",
                                icon = Icons.Outlined.ViewQuilt,
                                checked = enableGlassPlayerControls,
                                onCheckedChange = { preferences.enableGlassPlayerControls.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = "Glass Seekbar Background",
                                subtitle = "Show intelligent translucent glass behind video playback scrubber",
                                icon = Icons.Outlined.AutoAwesome,
                                checked = enableGlassSeekbarBackground,
                                onCheckedChange = { preferences.enableGlassSeekbarBackground.set(it) },
                                showDivider = false
                            )
                        }
                    }

                    // Motion & Animation Section
                    item {
                        GlassCategoryHeader(title = "Motion & Interactions", icon = Icons.Outlined.Animation)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = "Premium Micro-Interactions",
                                subtitle = "Subtle spring bounce feedback on interactive cards and buttons",
                                icon = Icons.Outlined.Animation,
                                checked = enableBounceAnimation,
                                onCheckedChange = { preferences.enableBounceAnimation.set(it) },
                                showDivider = false
                            )
                        }
                    }

                    // UI Density & Display Section
                    item {
                        GlassCategoryHeader(title = "Layout Density & Typography", icon = Icons.Outlined.TextFields)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_use_system_font_title),
                                subtitle = stringResource(id = R.string.pref_appearance_use_system_font_summary),
                                icon = Icons.Outlined.TextFields,
                                checked = useSystemFont,
                                onCheckedChange = { preferences.useSystemFont.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_unlimited_name_lines_title),
                                subtitle = stringResource(id = R.string.pref_appearance_unlimited_name_lines_summary),
                                icon = Icons.Outlined.TextFields,
                                checked = unlimitedNameLines,
                                onCheckedChange = { preferences.unlimitedNameLines.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_match_player_controls_to_theme_title),
                                subtitle = stringResource(id = R.string.pref_appearance_match_player_controls_to_theme_summary),
                                icon = Icons.Outlined.Palette,
                                checked = matchPlayerControlsToTheme,
                                onCheckedChange = { preferences.matchPlayerControlsToTheme.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_hide_player_buttons_background_title),
                                subtitle = stringResource(id = R.string.pref_appearance_hide_player_buttons_background_summary),
                                icon = Icons.Outlined.ViewQuilt,
                                checked = hidePlayerButtonsBackground,
                                onCheckedChange = { preferences.hidePlayerButtonsBackground.set(it) },
                                showDivider = false
                            )
                        }
                    }

                    // Media Presentation Section
                    item {
                        GlassCategoryHeader(title = "Media Presentation & Badges", icon = Icons.Outlined.Image)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_title),
                                subtitle = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_summary),
                                icon = Icons.Outlined.Image,
                                checked = showUnplayedOldVideoLabel,
                                onCheckedChange = { preferences.showUnplayedOldVideoLabel.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_show_network_thumbnails_title),
                                subtitle = stringResource(id = R.string.pref_appearance_show_network_thumbnails_summary),
                                icon = Icons.Outlined.Image,
                                checked = showNetworkThumbnails,
                                onCheckedChange = { preferences.showNetworkThumbnails.set(it) },
                                showDivider = false
                            )
                        }
                    }

                    // Metadata Source Section
                    item {
                        GlassCategoryHeader(
                            title = stringResource(id = R.string.pref_appearance_metadata_source_category),
                            icon = Icons.Outlined.AutoAwesome
                        )
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = stringResource(id = R.string.pref_appearance_use_tmdb_metadata_title),
                                subtitle = stringResource(id = R.string.pref_appearance_use_tmdb_metadata_summary),
                                icon = Icons.Outlined.AutoAwesome,
                                checked = useTmdbMetadata,
                                onCheckedChange = {
                                    preferences.useTmdbMetadata.set(it)
                                    preferences.metadataSource.set(if (it) xyz.mpv.rex.preferences.MetadataSource.TMDB else xyz.mpv.rex.preferences.MetadataSource.PROVIDER)
                                },
                                showDivider = false
                            )
                        }
                    }

                    // Language Selection Section
                    item {
                        GlassCategoryHeader(title = stringResource(id = R.string.pref_appearance_language_title), icon = Icons.Outlined.Language)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_appearance_language_title),
                                subtitle = if (currentLanguage.code.isEmpty()) {
                                    stringResource(R.string.system_default)
                                } else {
                                    "${currentLanguage.nativeName} (${currentLanguage.localizedName})"
                                },
                                icon = Icons.Outlined.Language,
                                showDivider = false,
                                onClick = { showLanguageDialog = true }
                            )
                        }
                    }
                }
            }
        }

        // Language Selection Dialog
        if (showLanguageDialog) {
            val languages = remember { LocaleHelper.getSupportedLanguages(context) }
            val currentLangCode = currentLanguage.code
            var selectedCode by remember { mutableStateOf(currentLangCode) }

            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.pref_appearance_language_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                    ) {
                        languages.forEach { lang ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedCode == lang.code,
                                        onClick = { selectedCode = lang.code },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCode == lang.code,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.padding(start = 12.dp))
                                Column {
                                    Text(
                                        text = if (lang.code.isEmpty()) stringResource(R.string.system_default) else lang.nativeName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selectedCode == lang.code) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (lang.code.isNotEmpty()) {
                                        Text(
                                            text = lang.localizedName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            preferences.appLanguage.set(selectedCode)
                            LocaleHelper.setAppLanguage(context, selectedCode)
                            showLanguageDialog = false
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
