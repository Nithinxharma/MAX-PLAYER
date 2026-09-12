package xyz.mpv.rex.ui.preferences

import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.cinehub.data.MetadataCacheManager
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object CineHubPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val browserPreferences = koinInject<BrowserPreferences>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val enableCineHubIntegration by browserPreferences.enableCineHubIntegration.collectAsState()
    val enableTabCineHub by browserPreferences.enableTabCineHub.collectAsState()
    val enableLocalMovies by browserPreferences.enableLocalMovies.collectAsState()
    val enableLocalTvShows by browserPreferences.enableLocalTvShows.collectAsState()
    val enableOnlineCatalog by browserPreferences.enableOnlineCatalog.collectAsState()
    val enableMetadataScraping by browserPreferences.enableMetadataScraping.collectAsState()
    val enableArtworkDownloads by browserPreferences.enableArtworkDownloads.collectAsState()
    val enableAutoRefresh by browserPreferences.enableAutoRefresh.collectAsState()
    val showMetadataOverlay by browserPreferences.showMetadataOverlay.collectAsState()
    val showCastInformation by browserPreferences.showCastInformation.collectAsState()

    ProvidePreferenceLocals {
      Scaffold(
        topBar = {
          TopAppBar(
            title = {
              Text(
                text = "CineHub Settings",
                fontWeight = FontWeight.Bold,
              )
            },
            navigationIcon = {
              IconButton(onClick = { backstack.removeLastOrNull() }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                  contentDescription = stringResource(R.string.generic_cancel),
                )
              }
            },
          )
        },
      ) { innerPadding ->
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
          // General Section
          item {
            PreferenceSectionHeader(title = "General")
          }
          item {
            GroupedListColumn {
              GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                SwitchPreference(
                  value = enableCineHubIntegration,
                  onValueChange = { browserPreferences.enableCineHubIntegration.set(it) },
                  title = { Text(text = "Enable CineHub Engine") },
                  summary = {
                    Text(
                      text = "Master switch for movie and TV show indexing & metadata",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
              GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                SwitchPreference(
                  value = enableTabCineHub,
                  onValueChange = { browserPreferences.enableTabCineHub.set(it) },
                  title = { Text(text = "Show in Bottom Navigation") },
                  summary = {
                    Text(
                      text = "Display CineHub tab in the bottom navigation bar",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }
          }

          // Library Sources Section
          item {
            PreferenceSectionHeader(title = "Library Sources")
          }
          item {
            GroupedListColumn {
              GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                SwitchPreference(
                  value = enableLocalMovies,
                  onValueChange = { browserPreferences.enableLocalMovies.set(it) },
                  title = { Text(text = "Local Movies") },
                  summary = {
                    Text(
                      text = "Index movies from CineRex/movies directory",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
              GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                SwitchPreference(
                  value = enableLocalTvShows,
                  onValueChange = { browserPreferences.enableLocalTvShows.set(it) },
                  title = { Text(text = "Local TV Shows") },
                  summary = {
                    Text(
                      text = "Index TV series from CineRex/tvshows directory",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
              GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                SwitchPreference(
                  value = enableOnlineCatalog,
                  onValueChange = { browserPreferences.enableOnlineCatalog.set(it) },
                  title = { Text(text = "Online Catalog & Discovery") },
                  summary = {
                    Text(
                      text = "Browse trending movies and TV shows from online catalogs",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
              GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                SwitchPreference(
                  value = enableAutoRefresh,
                  onValueChange = { browserPreferences.enableAutoRefresh.set(it) },
                  title = { Text(text = "Auto-Refresh Library") },
                  summary = {
                    Text(
                      text = "Automatically check for library updates",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }
          }

          // Metadata & Artwork Section
          item {
            PreferenceSectionHeader(title = "Metadata & Artwork")
          }
          item {
            GroupedListColumn {
              GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                SwitchPreference(
                  value = enableMetadataScraping,
                  onValueChange = { browserPreferences.enableMetadataScraping.set(it) },
                  title = { Text(text = "Metadata Scraping") },
                  summary = {
                    Text(
                      text = "Fetch poster, plot, rating, and cast information",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
              GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                SwitchPreference(
                  value = enableArtworkDownloads,
                  onValueChange = { browserPreferences.enableArtworkDownloads.set(it) },
                  title = { Text(text = "Download Artwork") },
                  summary = {
                    Text(
                      text = "Download high-resolution posters and backdrops",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
              GroupedPreferenceCard(position = GroupPosition.MIDDLE, highlightKey = null) {
                Preference(
                  title = { Text(text = "Preferred Metadata Source") },
                  summary = {
                    Text(
                      text = browserPreferences.preferredMetadataSource.get(),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = {},
                )
              }
              GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                Preference(
                  title = { Text(text = "Clear Metadata Cache") },
                  summary = {
                    Text(
                      text = "Delete locally cached NFO metadata and posters",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  onClick = {
                    scope.launch(Dispatchers.IO) {
                      MetadataCacheManager.clearCache(context)
                    }
                    Toast.makeText(context, "CineHub metadata cache cleared.", Toast.LENGTH_SHORT).show()
                  },
                )
              }
            }
          }

          // Playback Integration Section
          item {
            PreferenceSectionHeader(title = "Playback Integration")
          }
          item {
            GroupedListColumn {
              GroupedPreferenceCard(position = GroupPosition.FIRST, highlightKey = null) {
                SwitchPreference(
                  value = showMetadataOverlay,
                  onValueChange = { browserPreferences.showMetadataOverlay.set(it) },
                  title = { Text(text = "Show Metadata Overlay") },
                  summary = {
                    Text(
                      text = "Show movie and show details in player controls",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
              GroupedPreferenceCard(position = GroupPosition.LAST, highlightKey = null) {
                SwitchPreference(
                  value = showCastInformation,
                  onValueChange = { browserPreferences.showCastInformation.set(it) },
                  title = { Text(text = "Show Cast Information") },
                  summary = {
                    Text(
                      text = "Display actor details and characters during playback",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }
          }

          item {
            androidx.compose.foundation.layout.Spacer(
              modifier = Modifier.padding(bottom = navBarHeight + 16.dp),
            )
          }
        }
      }
    }
  }
}
