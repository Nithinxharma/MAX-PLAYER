package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.LocalUpdateViewModel
import xyz.mpv.rex.R
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.elevation.AdminSessionManager
import xyz.mpv.rex.auth.elevation.ElevationResult
import xyz.mpv.rex.auth.util.CertificateHelper
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.crash.CrashActivity.Companion.collectDeviceInfo
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassSwitchPreference
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.CommunityIcon
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.utils.TelegramIcon
import xyz.mpv.rex.utils.update.UpdateViewModel

/**
 * Root Category 6: About MaxStream Screen
 *
 * Displays application branding, version, open source credits, community links,
 * and role-aware admin diagnostic inspection data (MPV, FFmpeg, CloudStream engine, device hardware).
 */
@Serializable
object AboutScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val isDark = isSystemInDarkTheme()
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

        val updateViewModel = LocalUpdateViewModel.current
        val updateState by (updateViewModel?.updateState ?: MutableStateFlow(UpdateViewModel.UpdateState.Idle)).collectAsState()
        val isAutoUpdateEnabled by (updateViewModel?.isAutoUpdateEnabled ?: MutableStateFlow(false)).collectAsState()

        val authManager = koinInject<AuthManager>()
        val adminSessionManager = koinInject<AdminSessionManager>()
        val syncService = koinInject<FirebaseProviderSyncService>()

        val isAdmin by authManager.isAdmin.collectAsState()
        val isElevated by adminSessionManager.isElevated.collectAsState()
        val sessionInfo by adminSessionManager.sessionInfo.collectAsState()
        val loadedProvidersCount by syncService.loadedProvidersCount.collectAsState()
        val installedExtensionsCount by syncService.installedExtensionsCount.collectAsState()

        var tapCount by remember { mutableIntStateOf(0) }
        var showElevationDialog by remember { mutableStateOf(false) }

        val packageManager: PackageManager = context.packageManager
        val packageInfo = runCatching { packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = packageInfo?.versionName?.substringBefore('-') ?: "2.8.0"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 280
        } else {
            packageInfo?.versionCode?.toLong() ?: 280
        }
        val buildType = BuildConfig.BUILD_TYPE

        LaunchedEffect(updateState) {
            when (updateState) {
                is UpdateViewModel.UpdateState.NoUpdate -> {
                    Toast.makeText(context, context.getString(R.string.pref_about_up_to_date), Toast.LENGTH_SHORT).show()
                    updateViewModel?.dismissNoUpdate()
                }
                is UpdateViewModel.UpdateState.Error -> {
                    Toast.makeText(context, context.getString(R.string.pref_about_check_updates_failed), Toast.LENGTH_SHORT).show()
                    updateViewModel?.dismissNoUpdate()
                }
                else -> {}
            }
        }

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = stringResource(id = R.string.pref_about_title),
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
                    // Header Brand Card
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_max_stream_mark),
                                        contentDescription = "MAX STREAM Logo",
                                        modifier = Modifier.size(54.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        if (!isAdmin) {
                                            Toast.makeText(context, "Admin authorization required", Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
                                        if (isElevated) {
                                            val remainingMinutes = (sessionInfo.remainingTimeMs / 60000).coerceAtLeast(1)
                                            Toast.makeText(
                                                context,
                                                "Active Admin Session ($remainingMinutes min remaining)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@clickable
                                        }
                                        tapCount++
                                        if (tapCount in 1..6) {
                                            val remaining = 7 - tapCount
                                            Toast.makeText(
                                                context,
                                                "Security Verification: tap $remaining more times to request elevation",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (tapCount >= 7) {
                                            tapCount = 0
                                            showElevationDialog = true
                                        }
                                    }
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isElevated) "v$versionName ($versionCode) • [ADMIN ELEVATED]" else "v$versionName ($versionCode) $buildType",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isElevated) MaxStreamTheme.CrimsonAccent else (if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                        }
                    }

                    // Standard User: Community & Licenses Section
                    item {
                        GlassCategoryHeader(title = "Community & Open Source", icon = Icons.Outlined.Group)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_about_oss_libraries),
                                subtitle = "View open source third-party software licenses and credits",
                                icon = Icons.Outlined.Code,
                                showDivider = true,
                                onClick = { backstack.add(LibrariesScreen) }
                            )

                            GlassPreferenceItem(
                                title = "GitHub Repository",
                                subtitle = "Explore open source project repository and issues",
                                icon = Icons.Outlined.Code,
                                showDivider = true,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, context.getString(R.string.github_repo_url).toUri())
                                    )
                                }
                            )

                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_about_telegram_channel),
                                subtitle = stringResource(id = R.string.pref_about_telegram_channel_summary),
                                icon = TelegramIcon,
                                showDivider = true,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, context.getString(R.string.pref_about_telegram_url).toUri())
                                    )
                                }
                            )

                            GlassPreferenceItem(
                                title = stringResource(id = R.string.pref_about_telegram_group),
                                subtitle = stringResource(id = R.string.pref_about_telegram_group_summary),
                                icon = TelegramIcon,
                                showDivider = false,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, context.getString(R.string.pref_about_telegram_chat_url).toUri())
                                    )
                                }
                            )
                        }
                    }

                    // Software Updates (if enabled)
                    if (BuildConfig.ENABLE_UPDATE_FEATURE) {
                        item {
                            GlassCategoryHeader(title = "Software Updates", icon = Icons.Default.SystemUpdate)
                            GlassSettingsSection {
                                GlassSwitchPreference(
                                    title = stringResource(R.string.pref_about_auto_check_updates),
                                    subtitle = stringResource(R.string.pref_about_auto_check_updates_summary),
                                    icon = Icons.Default.SystemUpdate,
                                    checked = isAutoUpdateEnabled,
                                    onCheckedChange = { updateViewModel?.toggleAutoUpdate(it) },
                                    showDivider = true
                                )

                                GlassPreferenceItem(
                                    title = stringResource(R.string.pref_about_check_updates),
                                    subtitle = if (updateState is UpdateViewModel.UpdateState.Loading) "Checking for updates..." else stringResource(R.string.pref_about_check_updates_summary),
                                    icon = Icons.Outlined.Refresh,
                                    showDivider = false,
                                    onClick = { updateViewModel?.checkForUpdate(manual = true) }
                                )
                            }
                        }
                    }

                    // ADMIN / DEVELOPER ONLY: Deep Diagnostic Information
                    if (isAdmin) {
                        item {
                            GlassCategoryHeader(title = "Engine & Platform Diagnostics", icon = Icons.Outlined.Terminal)
                            GlassSettingsSection {
                                GlassPreferenceItem(
                                    title = "MPV Playback Core",
                                    subtitle = "Engine: libmpv 0.38+ • Hardware Decoders: Mediastream / MediaCodec",
                                    icon = Icons.Outlined.Memory,
                                    showDivider = true,
                                    onClick = { backstack.add(CodecInformationScreen) }
                                )

                                GlassPreferenceItem(
                                    title = "FFmpeg Core Version",
                                    subtitle = "FFmpeg Core: n7.0+ with AV1, HEVC, VP9, and EAC3 passthrough",
                                    icon = Icons.Outlined.Memory,
                                    showDivider = true,
                                    onClick = {
                                        Toast.makeText(context, "FFmpeg 7.0+ Core Active", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                GlassPreferenceItem(
                                    title = "Device Hardware & OS Diagnostics",
                                    subtitle = "Tap to view and copy hardware ABI, display metrics, and memory status",
                                    icon = Icons.Outlined.PhoneAndroid,
                                    showDivider = true,
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(collectDeviceInfo()))
                                        Toast.makeText(context, "Device info copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                val certInfo = remember(context) { CertificateHelper.getCertificateFingerprints(context) }
                                GlassPreferenceItem(
                                    title = "Build Certificate Fingerprints",
                                    subtitle = "SHA-1: ${certInfo.sha1.take(16)}... • Tap to copy for Firebase",
                                    icon = Icons.Outlined.Security,
                                    showDivider = false,
                                    onClick = {
                                        val copyText = "Package: ${certInfo.packageName}\nSHA-1: ${certInfo.sha1}\nSHA-256: ${certInfo.sha256}"
                                        clipboardManager.setText(AnnotatedString(copyText))
                                        Toast.makeText(context, "Certificate fingerprints copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // Legal & Terms
                    item {
                        GlassCategoryHeader(title = "Legal & Policies", icon = Icons.Outlined.Policy)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = "Privacy Policy",
                                subtitle = "Learn how MAX STREAM protects user security and telemetry",
                                icon = Icons.Outlined.Policy,
                                showDivider = true,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "https://github.com".toUri())
                                    )
                                }
                            )

                            GlassPreferenceItem(
                                title = "Terms of Service",
                                subtitle = "End user licensing and community guidelines",
                                icon = Icons.Outlined.Description,
                                showDivider = false,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "https://github.com".toUri())
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Elevation Dialog
        if (showElevationDialog) {
            AlertDialog(
                onDismissRequest = { showElevationDialog = false },
                title = { Text("Request Administrative Elevation") },
                text = {
                    Column {
                        Text(
                            "Elevate your session to access developer configurations, diagnostics, and server provider controls. Elevation is strictly time-bounded (15 minutes).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Authenticated UID: ${authManager.currentUser?.uid ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val uid = authManager.currentUser?.uid
                            if (uid != null) {
                                scope.launch {
                                    val result = adminSessionManager.requestElevation(uid)
                                    when (result) {
                                        is ElevationResult.Granted -> {
                                            showElevationDialog = false
                                            val minutes = result.durationMs / 60000
                                            Toast.makeText(context, "Administrative Elevation Active ($minutes min TTL)", Toast.LENGTH_LONG).show()
                                        }
                                        is ElevationResult.Denied -> {
                                            Toast.makeText(context, "Elevation Denied: ${result.reason}", Toast.LENGTH_LONG).show()
                                        }
                                        is ElevationResult.Error -> {
                                            Toast.makeText(context, "Elevation Error: ${result.throwable.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Authorize Elevation")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showElevationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Serializable
object LibrariesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val isDark = isSystemInDarkTheme()
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = stringResource(id = R.string.pref_about_oss_libraries),
                    onBackClick = { backstack.removeLastOrNull() }
                )
            },
            containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Open Source Licenses & Dependencies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "MAX STREAM is built with open source software including libmpv, FFmpeg, Jetpack Compose, Koin, OkHttp, Kotlin Coroutines, and CloudStream compatibility extensions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
