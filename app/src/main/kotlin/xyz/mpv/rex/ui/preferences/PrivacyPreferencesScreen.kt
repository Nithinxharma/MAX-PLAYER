package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import xyz.mpv.rex.preferences.PrivacyPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassSwitchPreference
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Root Category 5: Privacy & Data Protection Screen
 *
 * Provides granular controls for watch history, search caching, crash telemetry,
 * provider cache clearing, and direct access to legal privacy policies.
 */
@Serializable
object PrivacyPreferencesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val scope = rememberCoroutineScope()
        val isDark = isSystemInDarkTheme()
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

        val privacyPrefs = koinInject<PrivacyPreferences>()
        val historyRepo = koinInject<RecentlyPlayedRepository>()
        val syncService = koinInject<FirebaseProviderSyncService>()

        val pauseWatchHistory by privacyPrefs.pauseWatchHistory.collectAsState()
        val pauseSearchHistory by privacyPrefs.pauseSearchHistory.collectAsState()
        val sendCrashReports by privacyPrefs.sendCrashReports.collectAsState()
        val anonymousAnalytics by privacyPrefs.anonymousAnalytics.collectAsState()

        var showClearHistoryDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = "Privacy & Security",
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
                    // Watch History & Media Tracking Section
                    item {
                        GlassCategoryHeader(title = "Watch History & Media Tracking", icon = Icons.Outlined.History)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = "Pause Watch History",
                                subtitle = "Temporarily stop recording playback progress and recently played media",
                                icon = Icons.Outlined.History,
                                checked = pauseWatchHistory,
                                onCheckedChange = { privacyPrefs.pauseWatchHistory.set(it) },
                                showDivider = true
                            )

                            GlassPreferenceItem(
                                title = "View & Manage Watch History",
                                subtitle = "Inspect active playback entries across video files and streams",
                                icon = Icons.Outlined.History,
                                showDivider = true,
                                onClick = { backstack.add(RecentlyPlayedScreen) }
                            )

                            GlassPreferenceItem(
                                title = "Clear Watch History",
                                subtitle = "Delete all stored playback timestamps, resumes, and history logs",
                                icon = Icons.Outlined.DeleteOutline,
                                showDivider = false,
                                onClick = { showClearHistoryDialog = true }
                            )
                        }
                    }

                    // Search & Cache Management Section
                    item {
                        GlassCategoryHeader(title = "Search & Cache Control", icon = Icons.Outlined.CleaningServices)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = "Pause Search History",
                                subtitle = "Do not save search queries in local cache or recent suggestions",
                                icon = Icons.Outlined.Search,
                                checked = pauseSearchHistory,
                                onCheckedChange = { privacyPrefs.pauseSearchHistory.set(it) },
                                showDivider = true
                            )

                            GlassPreferenceItem(
                                title = "Clear Provider Manifest & Cache",
                                subtitle = "Flush local provider cache, compiled dex files, and reload registry",
                                icon = Icons.Outlined.CleaningServices,
                                showDivider = true,
                                onClick = {
                                    scope.launch {
                                        syncService.clearProviderCache()
                                        Toast.makeText(context, "Provider cache cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            GlassPreferenceItem(
                                title = "Clear Media Thumbnail Cache",
                                subtitle = "Free up device storage by clearing cached cover art and frame previews",
                                icon = Icons.Outlined.Storage,
                                showDivider = false,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            val cacheDir = context.cacheDir
                                            cacheDir.deleteRecursively()
                                        }
                                        Toast.makeText(context, "Cache storage cleaned", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    // Telemetry & Diagnostics Section
                    item {
                        GlassCategoryHeader(title = "Telemetry & Diagnostics", icon = Icons.Outlined.Security)
                        GlassSettingsSection {
                            GlassSwitchPreference(
                                title = "Send Anonymous Crash Reports",
                                subtitle = "Automatically transmit fatal stack traces to assist bug fixes",
                                icon = Icons.Outlined.BugReport,
                                checked = sendCrashReports,
                                onCheckedChange = { privacyPrefs.sendCrashReports.set(it) },
                                showDivider = true
                            )

                            GlassSwitchPreference(
                                title = "Anonymous Performance Metrics",
                                subtitle = "Share aggregated player latency and frame drop statistics",
                                icon = Icons.Outlined.Analytics,
                                checked = anonymousAnalytics,
                                onCheckedChange = { privacyPrefs.anonymousAnalytics.set(it) },
                                showDivider = false
                            )
                        }
                    }

                    // Legal & Privacy Policy Section
                    item {
                        GlassCategoryHeader(title = "Legal & Compliance", icon = Icons.Outlined.Policy)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = "Privacy Policy",
                                subtitle = "Read our data retention and protection policies",
                                icon = Icons.Outlined.Policy,
                                showDivider = true,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "https://github.com/NithinXharma/MAx-player".toUri())
                                    )
                                }
                            )

                            GlassPreferenceItem(
                                title = "Terms of Service",
                                subtitle = "Review end-user license agreements and streaming guidelines",
                                icon = Icons.Outlined.Description,
                                showDivider = false,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "https://github.com/NithinXharma/MAx-player".toUri())
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Clear History Confirmation Dialog
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear All Watch History?") },
                text = {
                    Text("This action will permanently delete all playback history and progress checkpoints. This cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                historyRepo.clearAll()
                                Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
                                showClearHistoryDialog = false
                            }
                        }
                    ) {
                        Text("Clear History", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
