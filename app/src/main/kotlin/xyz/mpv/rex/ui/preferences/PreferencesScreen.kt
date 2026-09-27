package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.elevation.AdminSessionManager
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.auth.LoginScreen
import xyz.mpv.rex.ui.components.glass.GlassButtonVariant
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton
import xyz.mpv.rex.ui.components.glass.MaxStreamGlassDialog
import xyz.mpv.rex.ui.profile.ProfileScreen
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Modernized Settings Screen enforcing strict Server-Controlled RBAC visibility:
 *
 * Normal User:
 * - Profile, Plan, Logout ONLY.
 * - Hides: Repository Manager, Extension Manager, Developer Settings, Provider Controls.
 *
 * Premium User:
 * - Profile, Current Plan, Active Providers Count, Logout.
 *
 * VIP User:
 * - Profile, VIP Badge, Provider Count, Repository Count, Logout.
 *
 * Admin User:
 * - Shows all above + Repository Management, Provider Diagnostics, Firebase Diagnostics,
 *   Sync Status, Installed Extension Count, Force Provider Sync, Clear Provider Cache.
 */
@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

    val authManager = koinInject<AuthManager>()
    val syncService = koinInject<FirebaseProviderSyncService>()
    val adminSessionManager = koinInject<AdminSessionManager>()

    val userProfile by authManager.userProfile.collectAsState()
    val userRole by authManager.userRole.collectAsState()
    val isAdmin by authManager.isAdmin.collectAsState()
    val isPremium by authManager.isPremium.collectAsState()
    val isElevated by adminSessionManager.isElevated.collectAsState()

    val userPlan by syncService.userPlan.collectAsState()
    val permissions by syncService.userPermissions.collectAsState()
    val isSyncing by syncService.isSyncing.collectAsState()
    val syncStatus by syncService.syncStatus.collectAsState()
    val lastSyncTime by syncService.lastSyncTimeFormatted.collectAsState()
    val loadedProvidersCount by syncService.loadedProvidersCount.collectAsState()
    val installedExtensionsCount by syncService.installedExtensionsCount.collectAsState()
    val syncedRepositoriesCount by syncService.syncedRepositoriesCount.collectAsState()

    val currentUser = authManager.currentUser

    // Determine normalized role tier
    val effectiveRole = when {
      isAdmin -> "admin"
      userRole.equals("vip", ignoreCase = true) || userPlan.equals("vip", ignoreCase = true) -> "vip"
      isPremium || userRole.equals("premium", ignoreCase = true) || userPlan.equals("premium", ignoreCase = true) -> "premium"
      else -> "user"
    }

    val isVip = effectiveRole == "vip"
    val isActualPremium = effectiveRole == "premium"
    val isNormalUser = effectiveRole == "user"

    // Diagnostics dialog states
    var showProviderDiagnosticsDialog by remember { mutableStateOf(false) }
    var showFirebaseDiagnosticsDialog by remember { mutableStateOf(false) }

    val displayName = userProfile?.name?.takeIf { it.isNotBlank() }
        ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: "Max Stream User"
    val email = userProfile?.email?.takeIf { it.isNotBlank() }
        ?: currentUser?.email?.takeIf { it.isNotBlank() }
        ?: "Tap to sign in or view profile"
    val photoUrl = userProfile?.photo?.takeIf { it.isNotBlank() }
        ?: currentUser?.photoUrl?.toString()

    Scaffold(
      topBar = {
        GlassTopBar(
          title = stringResource(R.string.pref_preferences),
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
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // 1. Account & Profile Card in Glass (Shown for all users)
          item {
            GlassCard(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
              shape = RoundedCornerShape(20.dp),
              onClick = { backstack.add(ProfileScreen) }
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (!photoUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                      .size(52.dp)
                      .clip(CircleShape)
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .size(52.dp)
                      .clip(CircleShape)
                      .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Person,
                      contentDescription = null,
                      tint = MaxStreamTheme.CrimsonAccent,
                      modifier = Modifier.size(28.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = displayName,
                      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                      color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Distinct Role/Plan Pill
                    val (badgeLabel, badgeColor) = when (effectiveRole) {
                      "admin" -> "ADMIN" to Color(0xFFFF2D55)
                      "vip" -> "VIP" to Color(0xFFFFB800)
                      "premium" -> "PREMIUM" to Color(0xFF00E676)
                      else -> "FREE" to Color(0xFF00C2FF)
                    }

                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = badgeLabel,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                  contentDescription = null,
                  tint = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline,
                )
              }
            }
          }

          // 2. Role-Specific Plan & Provider Metric Cards
          item {
            val planTitle = when (effectiveRole) {
              "admin" -> "Administrator Authority (Unrestricted)"
              "vip" -> "VIP Gold Membership Tier"
              "premium" -> "Premium Streaming Membership"
              else -> "Free Streaming Plan"
            }

            val planColor = when (effectiveRole) {
              "admin" -> Color(0xFFFF2D55)
              "vip" -> Color(0xFFFFB800)
              "premium" -> Color(0xFF00E676)
              else -> Color(0xFF00C2FF)
            }

            GlassCard(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
              shape = RoundedCornerShape(18.dp)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = when (effectiveRole) {
                        "admin" -> Icons.Default.AdminPanelSettings
                        "vip" -> Icons.Default.WorkspacePremium
                        "premium" -> Icons.Default.Star
                        else -> Icons.Default.CheckCircle
                      },
                      contentDescription = null,
                      tint = planColor,
                      modifier = Modifier.size(20.dp)
                    )
                    Text(
                      text = "Current Plan",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                    )
                  }

                  // Plan Badge
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = planColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, planColor.copy(alpha = 0.6f))
                  ) {
                    Text(
                      text = userPlan.uppercase(),
                      color = planColor,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                }

                Text(
                  text = planTitle,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = planColor
                )

                // Show provider and repository count based on tier
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  if (effectiveRole == "vip" || effectiveRole == "admin") {
                    Surface(
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(12.dp),
                      color = Color.White.copy(alpha = 0.05f)
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                          text = "Active Providers",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaxStreamTheme.TextMuted
                        )
                        Text(
                          text = "$loadedProvidersCount Loaded",
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                          color = planColor
                        )
                      }
                    }

                    Surface(
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(12.dp),
                      color = Color.White.copy(alpha = 0.05f)
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                          text = "Server Repos",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaxStreamTheme.TextMuted
                        )
                        Text(
                          text = "$syncedRepositoriesCount Active",
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                          color = planColor
                        )
                      }
                    }
                  } else if (effectiveRole == "premium") {
                    Surface(
                      modifier = Modifier.fillMaxWidth(),
                      shape = RoundedCornerShape(12.dp),
                      color = Color.White.copy(alpha = 0.05f)
                    ) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = "Active Providers Count",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaxStreamTheme.TextPrimary
                        )
                        Text(
                          text = "$loadedProvidersCount Providers Available",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                          color = planColor
                        )
                      }
                    }
                  } else {
                    Text(
                      text = "Standard access active with server-managed providers.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaxStreamTheme.TextSecondary
                    )
                  }
                }
              }
            }
          }

          // 3. ADMIN USER ONLY: Additional Management & Diagnostic Cards
          if (isAdmin) {
            item {
              GlassCategoryHeader(title = "Admin: Server & Provider Controls", icon = Icons.Outlined.Build)
              GlassSettingsSection {
                // Repository Management
                GlassPreferenceItem(
                  title = "Repository Management",
                  subtitle = "$syncedRepositoriesCount server repositories configured • Manage feeds",
                  icon = Icons.Outlined.CloudQueue,
                  badge = "$syncedRepositoriesCount",
                  showDivider = true,
                  onClick = { backstack.add(xyz.mpv.rex.ui.preferences.ExtensionRepositoriesScreenRoute) }
                )

                // Provider Diagnostics
                GlassPreferenceItem(
                  title = "Provider Diagnostics",
                  subtitle = "Inspect $loadedProvidersCount active runtime providers & bridge state",
                  icon = Icons.Outlined.Assessment,
                  showDivider = true,
                  onClick = { showProviderDiagnosticsDialog = true }
                )

                // Firebase Diagnostics
                GlassPreferenceItem(
                  title = "Firebase Diagnostics",
                  subtitle = "Plan: $userPlan • UID: ${currentUser?.uid?.take(8)}... • State: Active",
                  icon = Icons.Outlined.Storage,
                  showDivider = true,
                  onClick = { showFirebaseDiagnosticsDialog = true }
                )

                // Sync Status
                GlassPreferenceItem(
                  title = "Provider Sync Status",
                  subtitle = "$syncStatus • $loadedProvidersCount Providers Loaded • Last Sync: $lastSyncTime",
                  icon = Icons.Outlined.Sync,
                  badge = if (isSyncing) "SYNCING" else "SYNCED",
                  showDivider = true,
                  onClick = {
                    Toast.makeText(context, "Last Sync: $lastSyncTime ($syncStatus)", Toast.LENGTH_SHORT).show()
                  }
                )

                // Installed Extension Count
                GlassPreferenceItem(
                  title = "Installed Extension Count",
                  subtitle = "$installedExtensionsCount extensions currently installed in local database",
                  icon = Icons.Outlined.Extension,
                  badge = "$installedExtensionsCount",
                  showDivider = true,
                  onClick = { backstack.add(xyz.mpv.rex.ui.preferences.InstalledExtensionsScreenRoute) }
                )

                // Force Provider Sync
                GlassPreferenceItem(
                  title = "Force Provider Sync",
                  subtitle = if (isSyncing) "Syncing with Firebase..." else "Immediately re-sync permissions, repositories, and plugins",
                  icon = Icons.Outlined.Refresh,
                  badge = if (isSyncing) "SYNCING" else null,
                  showDivider = true,
                  onClick = {
                    scope.launch {
                      Toast.makeText(context, "Triggering Firebase Provider Sync...", Toast.LENGTH_SHORT).show()
                      val result = syncService.forceSync()
                      Toast.makeText(
                        context,
                        if (result) "Provider sync completed successfully" else "Provider sync finished with notes",
                        Toast.LENGTH_SHORT
                      ).show()
                    }
                  }
                )

                // Clear Provider Cache
                GlassPreferenceItem(
                  title = "Clear Provider Cache",
                  subtitle = "Wipe manifest memory cache and reload runtime provider registry",
                  icon = Icons.Outlined.CleaningServices,
                  showDivider = false,
                  onClick = {
                    scope.launch {
                      syncService.clearProviderCache()
                      Toast.makeText(context, "Provider cache cleared & registry reloaded", Toast.LENGTH_SHORT).show()
                    }
                  }
                )
              }
            }
          }

          // Search settings bar in Glass
          item {
            GlassCard(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
              shape = MaxStreamTheme.CapsuleShape,
              onClick = { backstack.add(SettingsSearchScreen) }
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaxStreamTheme.CrimsonAccent,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          // 4. General App Preferences (Available for all users, but NEVER show extension/repo manager here)
          // UI & Appearance Section
          item {
            GlassCategoryHeader(title = stringResource(R.string.pref_category_ui_appearance), icon = Icons.Outlined.Palette)
            GlassSettingsSection {
              GlassPreferenceItem(
                title = stringResource(id = R.string.pref_appearance_title),
                subtitle = stringResource(id = R.string.pref_appearance_summary),
                icon = Icons.Outlined.Palette,
                onClick = { backstack.add(AppearancePreferencesScreen) }
              )
            }
          }

          // Playback & Controls Section
          item {
            GlassCategoryHeader(title = stringResource(R.string.pref_category_playback_controls), icon = Icons.Outlined.PlayCircle)
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
                onClick = { backstack.add(GesturePreferencesScreen) }
              )
            }
          }

          // Media & Library Section
          item {
            GlassCategoryHeader(title = stringResource(R.string.pref_media_library_title), icon = Icons.Outlined.VideoLibrary)
            GlassSettingsSection {
              GlassPreferenceItem(
                title = stringResource(R.string.pref_media_library_title),
                subtitle = stringResource(R.string.pref_media_library_summary),
                icon = Icons.Outlined.VideoLibrary,
                onClick = { backstack.add(MediaLibraryPreferencesScreen) }
              )
            }
          }

          // Media Settings Section
          item {
            GlassCategoryHeader(title = stringResource(R.string.pref_category_media_settings), icon = Icons.Outlined.Memory)
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
                onClick = { backstack.add(AudioPreferencesScreen) }
              )
            }
          }

          // Integrations Section
          item {
            GlassCategoryHeader(title = "Integrations", icon = Icons.Outlined.Tv)
            GlassSettingsSection {
              GlassPreferenceItem(
                title = "CineTV Live & Playlist",
                subtitle = "Manage IPTV credentials, authentication & stream mappings",
                icon = Icons.Outlined.Tv,
                showDivider = true,
                onClick = { backstack.add(xyz.mpv.rex.cinetv.ui.CineTvSettingsScreen) }
              )
              GlassPreferenceItem(
                title = "Jellyfin",
                subtitle = "External player sync",
                icon = Icons.Outlined.VideoLibrary,
                showDivider = true,
                onClick = { backstack.add(xyz.mpv.rex.jellyfin.ui.JellyfinSettingsScreen) }
              )
              GlassPreferenceItem(
                title = "yt-dlp",
                subtitle = "Manage MAX STREAM Ytdlp & extractor preferences",
                icon = Icons.Outlined.CloudDownload,
                onClick = { backstack.add(YtdlSettingsScreen) }
              )
            }
          }

          // Advanced & About Section
          item {
            GlassCategoryHeader(title = stringResource(R.string.pref_category_advanced_about), icon = Icons.Outlined.Code)
            GlassSettingsSection {
              GlassPreferenceItem(
                title = stringResource(R.string.pref_advanced),
                subtitle = stringResource(id = R.string.pref_advanced_summary),
                icon = Icons.Outlined.Code,
                showDivider = isAdmin,
                onClick = { backstack.add(AdvancedPreferencesScreen) }
              )
              // Developer Options ONLY for Admins (Strict requirement: Hide for normal users)
              if (isAdmin) {
                GlassPreferenceItem(
                  title = stringResource(id = R.string.pref_developer_options_title),
                  subtitle = stringResource(id = R.string.pref_developer_options_summary),
                  icon = Icons.Outlined.Build,
                  showDivider = true,
                  onClick = { backstack.add(DeveloperOptionsScreen) }
                )
              }
              GlassPreferenceItem(
                title = stringResource(id = R.string.pref_about_title),
                subtitle = stringResource(id = R.string.pref_about_summary),
                icon = Icons.Outlined.Info,
                onClick = { backstack.add(AboutScreen) }
              )
            }
          }

          // 5. Logout Card (Available for all roles)
          item {
            GlassSettingsSection {
              GlassPreferenceItem(
                title = "Logout Session",
                subtitle = "Sign out from Firebase and clear active profile credentials",
                icon = Icons.Default.Logout,
                badge = null,
                showDivider = false,
                onClick = {
                  scope.launch {
                    authManager.signOut(context)
                    backstack.clear()
                    backstack.add(LoginScreen)
                  }
                }
              )
            }
          }
        }
      }
    }

    // Provider Diagnostics Dialog (Admin only)
    if (showProviderDiagnosticsDialog) {
      val diagnostics = syncService.getProviderDiagnostics()
      MaxStreamGlassDialog(
        onDismissRequest = { showProviderDiagnosticsDialog = false },
        title = "Provider Runtime Diagnostics",
        icon = Icons.Outlined.Assessment,
        confirmButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaxStreamGlassButton(
              text = "Full Audit Center",
              variant = GlassButtonVariant.Secondary,
              onClick = {
                showProviderDiagnosticsDialog = false
                backstack.add(xyz.mpv.rex.cinehub.diagnostic.CloudStreamTestCenterScreen)
              }
            )
            MaxStreamGlassButton(
              text = "Close",
              variant = GlassButtonVariant.Primary,
              onClick = { showProviderDiagnosticsDialog = false }
            )
          }
        }
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("• Total Active APIHolder Providers: ${diagnostics["totalProviders"]}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
          Text("• Installed Extensions in DB: ${diagnostics["installedCount"]}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
          Text("• Synced Server Repositories: ${diagnostics["syncedReposCount"]}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
          Text("• Orchestration Status: ${diagnostics["syncStatus"]}", color = MaterialTheme.colorScheme.primary)
          Text("• Last Sync: ${diagnostics["lastSync"]}", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Loaded Providers:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
          val names = diagnostics["providerNames"] as? List<*> ?: emptyList<Any>()
          names.take(15).forEach { name ->
            Text("  - $name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (names.size > 15) {
            Text("  ... and ${names.size - 15} more", style = MaterialTheme.typography.labelSmall, color = MaxStreamTheme.TextMuted)
          }
        }
      }
    }

    // Firebase Diagnostics Dialog (Admin only)
    if (showFirebaseDiagnosticsDialog) {
      val fbDiagnostics = syncService.getFirebaseDiagnostics()
      MaxStreamGlassDialog(
        onDismissRequest = { showFirebaseDiagnosticsDialog = false },
        title = "Firebase RBAC & Sync Diagnostics",
        icon = Icons.Outlined.Storage,
        confirmButton = {
          MaxStreamGlassButton(
            text = "Close",
            variant = GlassButtonVariant.Primary,
            onClick = { showFirebaseDiagnosticsDialog = false }
          )
        }
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("• Authenticated: ${fbDiagnostics["authenticated"]}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
          Text("• Firebase UID: ${fbDiagnostics["uid"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("• Account Email: ${fbDiagnostics["email"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("• Effective Plan Tier: ${fbDiagnostics["plan"]}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          Text("• Permissions Model:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
          Text(
            text = "${fbDiagnostics["permissions"]}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
