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
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AccessibilityPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassSwitchPreference
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Root Category 4: Accessibility Preferences Screen
 *
 * Provides dedicated accessibility controls for visual contrast, subtitle legibility,
 * touch target sizing, haptic feedback, and reduced motion performance optimizations.
 */
@Serializable
object AccessibilityPreferencesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val isDark = isSystemInDarkTheme()
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

        val accessibilityPrefs = koinInject<AccessibilityPreferences>()

        val highContrast by accessibilityPrefs.highContrast.collectAsState()
        val largeTouchTargets by accessibilityPrefs.largeTouchTargets.collectAsState()
        val enhanceSubtitleReadability by accessibilityPrefs.enhanceSubtitleReadability.collectAsState()
        val hapticFeedback by accessibilityPrefs.hapticFeedback.collectAsState()
        val reducedMotion by accessibilityPrefs.reducedMotion.collectAsState()
        val screenReaderAssist by accessibilityPrefs.screenReaderAssist.collectAsState()

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = "Accessibility",
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
                    // Vision & Contrast Section
                    item {
                        GlassCategoryHeader(title = "Vision & Contrast", icon = Icons.Outlined.Visibility)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = "High Contrast Mode",
                                subtitle = "Enhance border outlines, button contrast, and text sharpness",
                                icon = Icons.Outlined.Contrast,
                                checked = highContrast,
                                onCheckedChange = { accessibilityPrefs.highContrast.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = "Enhanced Subtitle Legibility",
                                subtitle = "Enforce high-contrast background shading behind active subtitles",
                                icon = Icons.Outlined.Subtitles,
                                checked = enhanceSubtitleReadability,
                                onCheckedChange = { accessibilityPrefs.enhanceSubtitleReadability.set(it) },
                                showDivider = true
                            )

                            GlassPreferenceItem(
                                title = "Customize Subtitle Typography & Colors",
                                subtitle = "Font sizes, borders, colors, and shadow offset adjustments",
                                icon = Icons.Outlined.Subtitles,
                                showDivider = false,
                                onClick = { backstack.add(SubtitlesPreferencesScreen) }
                            )
                        }
                    }

                    // Touch & Motor Interaction Section
                    item {
                        GlassCategoryHeader(title = "Touch & Motor Interaction", icon = Icons.Outlined.TouchApp)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = "Large Touch Targets",
                                subtitle = "Increase button hit areas to minimum 48dp across the entire player",
                                icon = Icons.Outlined.TouchApp,
                                checked = largeTouchTargets,
                                onCheckedChange = { accessibilityPrefs.largeTouchTargets.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = "Haptic Vibration Feedback",
                                subtitle = "Provide tactile feedback on gesture seek, volume, and playback toggles",
                                icon = Icons.Outlined.Vibration,
                                checked = hapticFeedback,
                                onCheckedChange = { accessibilityPrefs.hapticFeedback.set(it) },
                                showDivider = false
                            )
                        }
                    }

                    // Motion & Performance Optimization Section
                    item {
                        GlassCategoryHeader(title = "Motion & Device Performance", icon = Icons.Outlined.Speed)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = "Reduced Motion & Low-End Mode",
                                subtitle = "Disable complex mesh blur gradients and simplify transitions for smooth 60fps",
                                icon = Icons.Outlined.Speed,
                                checked = reducedMotion,
                                onCheckedChange = { accessibilityPrefs.reducedMotion.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = "Screen Reader & TalkBack Optimization",
                                subtitle = "Add detailed descriptive accessibility labels for TalkBack navigation",
                                icon = Icons.Outlined.Hearing,
                                checked = screenReaderAssist,
                                onCheckedChange = { accessibilityPrefs.screenReaderAssist.set(it) },
                                showDivider = false
                            )
                        }
                    }
                }
            }
        }
    }
}
