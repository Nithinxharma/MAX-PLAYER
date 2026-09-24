package xyz.mpv.rex.ui.preferences
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.elevation.AdminSessionManager
import xyz.mpv.rex.ui.profile.ProfileScreen
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.components.GroupedListItem
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val searchShape = RoundedCornerShape(24.dp)
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_preferences),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
      val authManager = koinInject<AuthManager>()
      val adminSessionManager = koinInject<AdminSessionManager>()
      val userProfile by authManager.userProfile.collectAsState()
      val userRole by authManager.userRole.collectAsState()
      val isAdmin by authManager.isAdmin.collectAsState()
      val isElevated by adminSessionManager.isElevated.collectAsState()
      val canAccessAdminDeveloperMenu = isAdmin && isElevated
      val isPremium by authManager.isPremium.collectAsState()
      val currentUser = authManager.currentUser

      val displayName = userProfile?.name?.takeIf { it.isNotBlank() }
          ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
          ?: "Maxstream user"
      val email = userProfile?.email?.takeIf { it.isNotBlank() }
          ?: currentUser?.email?.takeIf { it.isNotBlank() }
          ?: "Tap to sign in or view profile"
      val photoUrl = userProfile?.photo?.takeIf { it.isNotBlank() }
          ?: currentUser?.photoUrl?.toString()

      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarHeight + 24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Account & Profile Card
          item {
            Surface(
              onClick = { backstack.add(ProfileScreen) },
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              border = BorderStroke(1.dp, if (isAdmin) Color(0xFFFFB800).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)),
              tonalElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (!photoUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                  )
                } else {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = displayName,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isAdmin) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFFFB800).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.6f))
                      ) {
                        Text(
                          text = "ADMIN",
                          color = Color(0xFFFFB800),
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }

          // Search bar - full width, prominent placement
          item {
            Surface(
              onClick = { backstack.add(SettingsSearchScreen) },
              shape = searchShape,
              modifier = Modifier
                .fillMaxWidth()
                .clip(searchShape),
              color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
              tonalElevation = 2.dp,
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
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }

          // Extensions Section (Hidden from standard users, accessible only to validated Admins with unlock)
          if (canAccessAdminDeveloperMenu) {
            item {
              PreferenceSection(title = "Admin: Extensions & Providers") {
                GroupedListColumn {
                  PreferenceItem(
                    position = GroupPosition.ONLY,
                    title = "Plugin Extensions & Providers",
                    summary = "Manage repositories, installed extensions, and diagnostic suite",
                    icon = Icons.Outlined.Extension,
                    onClick = { backstack.add(xyz.mpv.rex.ui.preferences.ExtensionPreferencesScreenRoute) },
                  )
                }
              }
            }
          }

          // UI & Appearance Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_ui_appearance)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.ONLY,
                  title = stringResource(id = R.string.pref_appearance_title),
                  summary = stringResource(id = R.string.pref_appearance_summary),
                  icon = Icons.Outlined.Palette,
                  onClick = { backstack.add(AppearancePreferencesScreen) },
                )
              }
            }
          }

          // Playback & Controls Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_playback_controls)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = stringResource(id = R.string.pref_player),
                  summary = stringResource(id = R.string.pref_player_summary),
                  icon = Icons.Outlined.PlayCircle,
                  onClick = { backstack.add(PlayerPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.MIDDLE,
                  title = stringResource(id = R.string.pref_layout_title),
                  summary = stringResource(id = R.string.pref_layout_summary),
                  icon = Icons.AutoMirrored.Outlined.ViewQuilt,
                  onClick = { backstack.add(PlayerControlsPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = stringResource(id = R.string.pref_gesture),
                  summary = stringResource(id = R.string.pref_gesture_summary),
                  icon = Icons.Outlined.Gesture,
                  onClick = { backstack.add(GesturePreferencesScreen) },
                )
              }
            }
          }

          // Media & Library Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_media_library_title)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.ONLY,
                  title = stringResource(R.string.pref_media_library_title),
                  summary = stringResource(R.string.pref_media_library_summary),
                  icon = Icons.Outlined.VideoLibrary,
                  onClick = { backstack.add(MediaLibraryPreferencesScreen) },
                )
              }
            }
          }

          // Media Settings Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_media_settings)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = stringResource(id = R.string.pref_decoder),
                  summary = stringResource(id = R.string.pref_decoder_summary),
                  icon = Icons.Outlined.Memory,
                  onClick = { backstack.add(DecoderPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.MIDDLE,
                  title = stringResource(id = R.string.pref_subtitles),
                  summary = stringResource(id = R.string.pref_subtitles_summary),
                  icon = Icons.Outlined.Subtitles,
                  onClick = { backstack.add(SubtitlesPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = stringResource(id = R.string.pref_audio),
                  summary = stringResource(id = R.string.pref_audio_summary),
                  icon = Icons.Outlined.Audiotrack,
                  onClick = { backstack.add(AudioPreferencesScreen) },
                )
              }
            }
          }

          // RexShorts Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_rexshorts)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.ONLY,
                  title = stringResource(R.string.pref_category_rexshorts_settings),
                  summary = stringResource(R.string.pref_category_rexshorts_settings_desc),
                  icon = Icons.Outlined.VideoLibrary,
                  onClick = { backstack.add(ShortsPreferencesScreen) },
                )
              }
            }
          }

          // Integrations Section
          item {
            PreferenceSection(title = "Integrations") {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = "CineTV Live & Playlist",
                  summary = "Manage IPTV credentials, authentication & stream mappings",
                  icon = Icons.Outlined.Tv,
                  onClick = { backstack.add(xyz.mpv.rex.cinetv.ui.CineTvSettingsScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.MIDDLE,
                  title = "Jellyfin",
                  summary = "External player sync",
                  icon = Icons.Outlined.VideoLibrary,
                  onClick = { backstack.add(xyz.mpv.rex.jellyfin.ui.JellyfinSettingsScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = "yt-dlp",
                  summary = "Manage MAX STREAM Ytdlp & extractor preferences",
                  icon = Icons.Outlined.CloudDownload,
                  onClick = { backstack.add(YtdlSettingsScreen) },
                )
              }
            }
          }

          // Advanced & About Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_advanced_about)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = stringResource(R.string.pref_advanced),
                  summary = stringResource(id = R.string.pref_advanced_summary),
                  icon = Icons.Outlined.Code,
                  onClick = { backstack.add(AdvancedPreferencesScreen) },
                )
                if (isAdmin) {
                  PreferenceItem(
                    position = GroupPosition.MIDDLE,
                    title = stringResource(id = R.string.pref_developer_options_title),
                    summary = stringResource(id = R.string.pref_developer_options_summary),
                    icon = Icons.Outlined.Build,
                    onClick = { backstack.add(DeveloperOptionsScreen) },
                  )
                }
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = stringResource(id = R.string.pref_about_title),
                  summary = stringResource(id = R.string.pref_about_summary),
                  icon = Icons.Outlined.Info,
                  onClick = { backstack.add(AboutScreen) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PreferenceSection(
  title: String,
  content: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
    content()
  }
}

@Composable
private fun PreferenceItem(
  position: GroupPosition,
  title: String,
  summary: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  GroupedListItem(
    position = position,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .background(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (summary.isNotBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
