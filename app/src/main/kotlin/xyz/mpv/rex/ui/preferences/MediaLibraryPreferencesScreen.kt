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
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.TextFieldPreference
import androidx.compose.ui.text.AnnotatedString
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

    val customMoviesFolder by browserPreferences.customMoviesFolder.collectAsState()
    val customTvShowsFolder by browserPreferences.customTvShowsFolder.collectAsState()
    val scraperProvider by browserPreferences.scraperProvider.collectAsState()
    val customTmdbApiKey by browserPreferences.customTmdbApiKey.collectAsState()
    val enableMovieScraper by browserPreferences.enableMovieScraper.collectAsState()
    val enableTvScraper by browserPreferences.enableTvScraper.collectAsState()
    val cacheScannedMetadata by browserPreferences.cacheScannedMetadata.collectAsState()

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

              item { PreferenceSectionHeader(title = "Media Folders & Scraper Configuration") }
              item {
                  GroupedListColumn {
                      GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                          TextFieldPreference(
                              value = customMoviesFolder,
                              onValueChange = { browserPreferences.customMoviesFolder.set(it) },
                              textToValue = { it },
                              title = { Text(text = stringResource(R.string.pref_custom_movies_folder)) },
                              summary = {
                                  Text(
                                      text = if (customMoviesFolder.isNotBlank()) customMoviesFolder else "Default (CineRex/movies, Movies)",
                                      color = MaterialTheme.colorScheme.outline
                                  )
                              }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          TextFieldPreference(
                              value = customTvShowsFolder,
                              onValueChange = { browserPreferences.customTvShowsFolder.set(it) },
                              textToValue = { it },
                              title = { Text(text = stringResource(R.string.pref_custom_tv_shows_folder)) },
                              summary = {
                                  Text(
                                      text = if (customTvShowsFolder.isNotBlank()) customTvShowsFolder else "Default (CineRex/tvshows, TV Shows)",
                                      color = MaterialTheme.colorScheme.outline
                                  )
                              }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          ListPreference(
                              value = scraperProvider,
                              onValueChange = { browserPreferences.scraperProvider.set(it) },
                              values = listOf("tmdb_and_tvmaze", "tvmaze", "tmdb"),
                              valueToText = { value ->
                                  AnnotatedString(
                                      when (value) {
                                          "tmdb_and_tvmaze" -> "TMDB & TVMaze (Recommended)"
                                          "tvmaze" -> "TVMaze (No API key needed for TV shows)"
                                          "tmdb" -> "TMDB Only"
                                          else -> value
                                      }
                                  )
                              },
                              title = { Text(text = stringResource(R.string.pref_scraper_provider)) },
                              summary = {
                                  Text(
                                      text = when (scraperProvider) {
                                          "tmdb_and_tvmaze" -> "TMDB & TVMaze (Recommended)"
                                          "tvmaze" -> "TVMaze (No API key needed for TV shows)"
                                          "tmdb" -> "TMDB Only"
                                          else -> scraperProvider
                                      },
                                      color = MaterialTheme.colorScheme.outline
                                  )
                              }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          TextFieldPreference(
                              value = customTmdbApiKey,
                              onValueChange = { browserPreferences.customTmdbApiKey.set(it) },
                              textToValue = { it },
                              title = { Text(text = stringResource(R.string.pref_custom_tmdb_api_key)) },
                              summary = {
                                  Text(
                                      text = if (customTmdbApiKey.isNotBlank()) {
                                          customTmdbApiKey.take(8) + "..."
                                      } else {
                                          "Built-in Key (Tap to edit or customize)"
                                      },
                                      color = MaterialTheme.colorScheme.outline
                                  )
                              }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          SwitchPreference(
                              value = enableMovieScraper,
                              onValueChange = { browserPreferences.enableMovieScraper.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_movie_scraper)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          SwitchPreference(
                              value = enableTvScraper,
                              onValueChange = { browserPreferences.enableTvScraper.set(it) },
                              title = { Text(text = stringResource(R.string.pref_enable_tv_scraper)) }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                          SwitchPreference(
                              value = cacheScannedMetadata,
                              onValueChange = { browserPreferences.cacheScannedMetadata.set(it) },
                              title = { Text(text = stringResource(R.string.pref_cache_scanned_metadata)) },
                              summary = {
                                  Text(
                                      text = stringResource(R.string.pref_cache_scanned_metadata_summary),
                                      color = MaterialTheme.colorScheme.outline
                                  )
                              }
                          )
                      }
                      GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                          Preference(
                              title = { Text(text = stringResource(R.string.pref_force_refresh_metadata)) },
                              summary = {
                                  Text(
                                      text = "Clear cached posters, TV show summaries, and local index",
                                      color = MaterialTheme.colorScheme.outline
                                  )
                              },
                              onClick = {
                                  scope.launch(Dispatchers.IO) {
                                      MetadataCacheManager.clearCache(context)
                                  }
                                  Toast.makeText(context, "Metadata cache cleared.", Toast.LENGTH_SHORT).show()
                              }
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
