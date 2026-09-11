package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.FoldersPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.cinehub.data.MetadataCacheManager
import org.koin.compose.koinInject

@Serializable
object MediaLibraryPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val browserPreferences = koinInject<BrowserPreferences>()
    val foldersPreferences = koinInject<FoldersPreferences>()
    val hybridMediaIndex = koinInject<HybridMediaIndexRepository>()
    val metadataCache = koinInject<VideoMetadataCacheRepository>()

    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val includeNoMediaContent by browserPreferences.includeNoMediaContent.collectAsState()
    val showAudioFiles by browserPreferences.showAudioFiles.collectAsState()
    val libraryScanRoots by foldersPreferences.libraryScanRoots.collectAsState()
    
    val enableLocalMovies by browserPreferences.enableLocalMovies.collectAsState()
    val enableLocalTvShows by browserPreferences.enableLocalTvShows.collectAsState()
    val enableOnlineCatalog by browserPreferences.enableOnlineCatalog.collectAsState()
    val enableMetadataScraping by browserPreferences.enableMetadataScraping.collectAsState()
    val enableArtworkDownloads by browserPreferences.enableArtworkDownloads.collectAsState()
    val enableAutoRefresh by browserPreferences.enableAutoRefresh.collectAsState()

    val showMetadataOverlay by browserPreferences.showMetadataOverlay.collectAsState()
    val showCastInformation by browserPreferences.showCastInformation.collectAsState()

    val enableOnlineDiscovery by browserPreferences.enableOnlineDiscovery.collectAsState()
    val trendingContent by browserPreferences.trendingContent.collectAsState()
    
    val enableCineHubIntegration by browserPreferences.enableCineHubIntegration.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_media_library_title),
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
      ProvidePreferenceLocals {
        LazyColumn(
          state = rememberPreferenceLazyListState(),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarHeight + 16.dp),
        ) {

          // CineHub Core Section
          item {
            PreferenceSectionHeader(title = "CineHub Integration")
          }
          item {
            GroupedListColumn {
              GroupedPreferenceCard(position = GroupPosition.ONLY, highlightKey = null) {
                SwitchPreference(
                  value = enableCineHubIntegration,
                  onValueChange = { browserPreferences.enableCineHubIntegration.set(it) },
                  title = { Text(text = "Enable Media Engine") },
                  summary = { Text(text = "Turns on native indexing for movies and TV shows", color = MaterialTheme.colorScheme.outline) }
                )
              }
            }
          }

          if (enableCineHubIntegration) {
              item { PreferenceSectionHeader(title = "Library Sources") }
              item {
                  GroupedListColumn {
                      GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                          SwitchPreference(
                              value = enableLocalMovies,
                              onValueChange = { browserPreferences.enableLocalMovies.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_local_movies)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          SwitchPreference(
                              value = enableLocalTvShows,
                              onValueChange = { browserPreferences.enableLocalTvShows.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_local_tv_shows)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          SwitchPreference(
                              value = enableOnlineCatalog,
                              onValueChange = { browserPreferences.enableOnlineCatalog.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_online_catalog)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          SwitchPreference(
                              value = enableMetadataScraping,
                              onValueChange = { browserPreferences.enableMetadataScraping.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_metadata_scraping)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          SwitchPreference(
                              value = enableArtworkDownloads,
                              onValueChange = { browserPreferences.enableArtworkDownloads.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_artwork_downloads)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                          SwitchPreference(
                              value = enableAutoRefresh,
                              onValueChange = { browserPreferences.enableAutoRefresh.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_auto_refresh)) }
                          )
                      }
                  }
              }

              item { PreferenceSectionHeader(title = "Metadata & Artwork") }
              item {
                  GroupedListColumn {
                      GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                          Preference(
                              title = { Text(text = stringResource(R.string.pref_preferred_metadata_source)) },
                              summary = { Text(text = browserPreferences.preferredMetadataSource.get(), color = MaterialTheme.colorScheme.outline) },
                              onClick = {}
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          Preference(
                              title = { Text(text = stringResource(R.string.pref_force_refresh_metadata)) },
                              onClick = {
                                  scope.launch(Dispatchers.IO) {
                                      MetadataCacheManager.clearCache(context)
                                  }
                                  Toast.makeText(context, "Metadata cache cleared.", Toast.LENGTH_SHORT).show()
                              }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                          Preference(
                              title = { Text(text = stringResource(R.string.pref_manual_mappings)) },
                              onClick = {}
                          )
                      }
                  }
              }

              item { PreferenceSectionHeader(title = "Playback Integration") }
              item {
                  GroupedListColumn {
                      GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                          SwitchPreference(
                              value = showMetadataOverlay,
                              onValueChange = { browserPreferences.showMetadataOverlay.set(it) },
                              title = { Text(text = stringResource(R.string.pref_show_metadata_overlay)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                          SwitchPreference(
                              value = showCastInformation,
                              onValueChange = { browserPreferences.showCastInformation.set(it) },
                              title = { Text(text = stringResource(R.string.pref_show_cast_information)) }
                          )
                      }
                  }
              }
          }

          // Original Content & Discovery Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_content_discovery))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = listOf(R.string.pref_media_library_title, R.string.pref_include_no_media_content_title),
              ) {
                SwitchPreference(
                  value = includeNoMediaContent,
                  onValueChange = { newValue ->
                    browserPreferences.includeNoMediaContent.set(newValue)
                    MediaLibraryEvents.notifyChanged()
                    scope.launch(Dispatchers.IO) {
                      runCatching { hybridMediaIndex.ensureFresh(force = true, userInitiated = true) }
                    }
                  },
                  title = { Text(text = stringResource(R.string.pref_include_no_media_content_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_include_no_media_content_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_show_audio_files_title,
              ) {
                SwitchPreference(
                  value = showAudioFiles,
                  onValueChange = { newValue ->
                    browserPreferences.showAudioFiles.set(newValue)
                    MediaLibraryEvents.notifyChanged()
                  },
                  title = { Text(text = stringResource(R.string.pref_show_audio_files_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_show_audio_files_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }
          }

          // Storage & Exclusions Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_storage_exclusions))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_folders_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_folders_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_folders_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = { backstack.add(FoldersPreferencesScreen) },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_library_roots_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_library_roots_title)) },
                  summary = {
                    Text(
                      text = if (libraryScanRoots.isEmpty()) {
                        stringResource(R.string.pref_library_roots_empty_title)
                      } else {
                        stringResource(R.string.pref_library_root_count, libraryScanRoots.size)
                      },
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = { backstack.add(LibraryRootsPreferencesScreen) },
                )
              }
            }
          }

          // Library Maintenance Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_category_library_maintenance))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_rescan_library_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_rescan_library_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_rescan_library_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      runCatching { hybridMediaIndex.ensureFresh(force = true, userInitiated = true) }
                    }
                    Toast.makeText(context, context.getString(R.string.pref_rescan_started_toast), Toast.LENGTH_SHORT).show()
                  },
                )
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_clear_metadata_cache_title,
              ) {
                Preference(
                  title = { Text(text = stringResource(R.string.pref_clear_metadata_cache_title)) },
                  summary = {
                    Text(
                      text = stringResource(R.string.pref_clear_metadata_cache_summary),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      runCatching { metadataCache.clearAll() }
                    }
                    Toast.makeText(context, context.getString(R.string.pref_cache_cleared_toast), Toast.LENGTH_SHORT).show()
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}
