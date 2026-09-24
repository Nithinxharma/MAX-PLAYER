package xyz.mpv.rex.ui.preferences

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
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.elevation.AdminSessionManager
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.profile.ProfileScreen
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val isDark = isSystemInDarkTheme()
    val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
    val authManager = koinInject<AuthManager>()
    val adminSessionManager = koinInject<AdminSessionManager>()
    val userProfile by authManager.userProfile.collectAsState()
    val userRole by authManager.userRole.collectAsState()
    val isAdmin by authManager.isAdmin.collectAsState()
    val isElevated by adminSessionManager.isElevated.collectAsState()
    val canAccessAdminDeveloperMenu = isAdmin && isElevated
    val currentUser = authManager.currentUser

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
          // Account & Profile Card in Glass
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
                      .size(48.dp)
                      .clip(CircleShape)
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Person,
                      contentDescription = null,
                      tint = MaxStreamTheme.CrimsonAccent,
                      modifier = Modifier.size(26.dp)
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
                    if (isAdmin) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Box(
                        modifier = Modifier
                          .clip(RoundedCornerShape(6.dp))
                          .background(Color(0xFFFFB800).copy(alpha = 0.2f))
                          .padding(horizontal = 6.dp, vertical = 2.dp)
                      ) {
                        Text(
                          text = "ADMIN",
                          color = Color(0xFFFFB800),
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
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

          // Admin: Extensions & Providers
          if (canAccessAdminDeveloperMenu) {
            item {
              GlassCategoryHeader(title = "Admin: Extensions & Providers", icon = Icons.Outlined.Extension)
              GlassSettingsSection {
                GlassPreferenceItem(
                  title = "Plugin Extensions & Providers",
                  subtitle = "Manage repositories, installed extensions, and diagnostic suite",
                  icon = Icons.Outlined.Extension,
                  onClick = { backstack.add(xyz.mpv.rex.ui.preferences.ExtensionPreferencesScreenRoute) }
                )
              }
            }
          }

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

          // RexShorts Section
          item {
            GlassCategoryHeader(title = stringResource(R.string.pref_category_rexshorts), icon = Icons.Outlined.VideoLibrary)
            GlassSettingsSection {
              GlassPreferenceItem(
                title = stringResource(R.string.pref_category_rexshorts_settings),
                subtitle = stringResource(R.string.pref_category_rexshorts_settings_desc),
                icon = Icons.Outlined.VideoLibrary,
                onClick = { backstack.add(ShortsPreferencesScreen) }
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
        }
      }
    }
  }
}
