package xyz.mpv.rex.ui.browser.recentlyplayed

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.cinehub.bridge.RexPlayerBridge
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.extension.model.LibraryItem
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.ConfirmDialog
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamWatchlistSection
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.playlist.PlaylistDetailScreen
import xyz.mpv.rex.ui.browser.selection.SelectionManager
import xyz.mpv.rex.ui.browser.selection.rememberSelectionManager
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaInfoParser
import xyz.mpv.rex.utils.media.MediaUtils
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolved OTT metadata for clean TMDB titles, backdrops, and episode details.
 */
data class ResolvedRecentlyPlayedMetadata(
  val title: String,
  val subtitle: String?,
  val posterUrl: String?,
  val backdropUrl: String?,
  val isTvShow: Boolean,
  val season: Int?,
  val episode: Int?,
  val episodeTitle: String?,
  val year: String?,
)

object RecentlyPlayedMetadataResolver {
  private val memoryCache = ConcurrentHashMap<String, ResolvedRecentlyPlayedMetadata>()

  suspend fun resolve(context: Context, video: Video): ResolvedRecentlyPlayedMetadata {
    val cacheKey = video.path.ifBlank { video.title }
    memoryCache[cacheKey]?.let { return it }

    val rawCandidate = video.displayName.ifBlank { video.title }
    val parsed = MediaInfoParser.parse(rawCandidate)
    val isTv = parsed.type == "tv" || parsed.season != null || parsed.episode != null
    val cleanTitle = parsed.title.ifBlank { MediaUtils.extractCleanMediaTitle(video.title, video.path) }

    var posterUrl: String? = null
    var backdropUrl: String? = null
    var resolvedTitle = cleanTitle
    var resolvedYear = parsed.year

    try {
      if (isTv) {
        val tvDetails = CineOnlineScraper.getOrFetchTvShow(context, cleanTitle, null)
        if (tvDetails != null) {
          resolvedTitle = tvDetails.title
          posterUrl = tvDetails.posterPath
          backdropUrl = tvDetails.backdropPath
          resolvedYear = tvDetails.premiered.take(4).ifBlank { parsed.year }
        }
      } else {
        val movieDetails = CineOnlineScraper.getOrFetchMovie(context, cleanTitle, null)
        if (movieDetails != null) {
          resolvedTitle = movieDetails.title
          posterUrl = movieDetails.posterPath
          backdropUrl = movieDetails.backdropPath
          resolvedYear = movieDetails.premiered.take(4).ifBlank { parsed.year }
        }
      }
    } catch (_: Exception) {
      // Fallback gracefully on parsing
    }

    val subtitle = if (isTv && (parsed.season != null || parsed.episode != null)) {
      val sStr = parsed.season?.let { "S%02d".format(it) } ?: ""
      val eStr = parsed.episode?.let { "E%02d".format(it) } ?: ""
      val epName = parsed.episodeTitle?.let { " • $it" } ?: ""
      "$sStr$eStr$epName".trim()
    } else {
      resolvedYear?.let { "$it" }
    }

    val result = ResolvedRecentlyPlayedMetadata(
      title = resolvedTitle,
      subtitle = subtitle,
      posterUrl = posterUrl,
      backdropUrl = backdropUrl,
      isTvShow = isTv,
      season = parsed.season,
      episode = parsed.episode,
      episodeTitle = parsed.episodeTitle,
      year = resolvedYear
    )
    memoryCache[cacheKey] = result
    return result
  }
}

/**
 * Format relative human-readable timestamp.
 */
fun formatRelativeTimestamp(timestamp: Long): String {
  if (timestamp <= 0) return "Recently played"
  val now = System.currentTimeMillis()
  val diff = (now - timestamp).coerceAtLeast(0)
  val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
  val hours = TimeUnit.MILLISECONDS.toHours(diff)
  val days = TimeUnit.MILLISECONDS.toDays(diff)
  return when {
    minutes < 2 -> "Watched just now"
    minutes < 60 -> "Watched $minutes min ago"
    hours < 24 -> "Watched $hours ${if (hours == 1L) "hour" else "hours"} ago"
    days == 1L -> "Watched yesterday"
    days < 7 -> "Watched $days days ago"
    days < 30 -> "Watched ${days / 7} ${if (days / 7 == 1L) "week" else "weeks"} ago"
    else -> "Watched ${days / 30} ${if (days / 30 == 1L) "month" else "months"} ago"
  }
}

/**
 * Format remaining playback progress info.
 */
fun formatProgressInfo(
  progress: Float?,
  durationMs: Long,
  isTv: Boolean,
  season: Int?,
  episode: Int?,
  episodeTitle: String?
): String {
  if (progress == null || durationMs <= 0) return ""
  val remainingMs = (durationMs * (1f - progress)).toLong().coerceAtLeast(0)
  val remainingMins = (remainingMs / (1000 * 60)).coerceAtLeast(1)
  val percent = (progress * 100).toInt().coerceIn(1, 100)

  return if (isTv) {
    val sStr = season?.let { "S%02d".format(it) } ?: ""
    val eStr = episode?.let { "E%02d".format(it) } ?: ""
    val epCode = if (sStr.isNotBlank() || eStr.isNotBlank()) "$sStr$eStr" else ""
    val epName = episodeTitle?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
    if (epCode.isNotBlank()) {
      "$epCode$epName • $remainingMins min remaining • $percent%"
    } else {
      "$remainingMins min remaining • $percent%"
    }
  } else {
    val watchedMs = (durationMs * progress).toLong()
    val watchedHrs = watchedMs / (1000 * 60 * 60)
    val watchedMins = (watchedMs / (1000 * 60)) % 60
    val watchedStr = if (watchedHrs > 0) "${watchedHrs}h ${watchedMins}m" else "${watchedMins}m"
    "$watchedStr watched • $remainingMins min remaining • $percent%"
  }
}

@Serializable
object RecentlyPlayedScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val playlistRepository = koinInject<PlaylistRepository>()
    val viewModel: RecentlyPlayedViewModel =
      viewModel(factory = RecentlyPlayedViewModel.factory(context.applicationContext as android.app.Application))

    val recentItems by viewModel.recentItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val database = koinInject<MpvExDatabase>()
    val libraryDao = remember { database.cineLibraryDao() }
    val libraryItems by libraryDao.getAllLibraryItems().collectAsState(initial = emptyList())
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteFilesCheckbox = rememberSaveable { mutableStateOf(false) }
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val navigationBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

    val isFabVisible = remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Selection manager for all items (videos and playlists)
    val selectionManager =
      rememberSelectionManager(
        items = recentItems,
        getId = { item ->
          when (item) {
            is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}"
            is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}"
          }
        },
        onDeleteItems = { items, _ ->
          viewModel.deleteRecentItems(items)
        },
        onRenameItem = null,
        onOperationComplete = { },
      )

    BackHandler(enabled = selectionManager.isInSelectionMode) {
      if (selectionManager.isInSelectionMode) {
        selectionManager.clear()
      }
    }

    val listState = rememberLazyListState()

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = "Continue Watching",
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = recentItems.size,
          onBackClick = null,
          onCancelSelection = { selectionManager.clear() },
          onSortClick = null,
          onSettingsClick = {
            backStack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen)
          },
          isSingleSelection = selectionManager.isSingleSelection,
          onInfoClick = null,
          onPlayClick = null,
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
          onDeleteClick = { deleteDialogOpen.value = true },
        )
      },
      floatingActionButton = {
        if (!selectionManager.isInSelectionMode && isFabVisible.value && recentItems.isNotEmpty()) {
          TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(stringResource(R.string.play_recently_played_or_first)) } },
            state = rememberTooltipState(),
          ) {
            FloatingActionButton(
              modifier = Modifier
                .padding(bottom = navigationBarHeight + 8.dp)
                .animateFloatingActionButton(
                  visible = true,
                  alignment = Alignment.BottomEnd,
                ),
              onClick = {
                coroutineScope.launch {
                  val recentlyPlayedVideos = xyz.mpv.rex.utils.history.RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                  val lastPlayed = recentlyPlayedVideos.firstOrNull()
                  if (lastPlayed != null) {
                    MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                  } else {
                    android.widget.Toast.makeText(context, context.getString(R.string.no_recently_played_videos), android.widget.Toast.LENGTH_SHORT).show()
                  }
                }
              },
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play_recently_played_or_first))
            }
          }
        }
      },
    ) { padding ->
      when {
        !enableRecentlyPlayed -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            OttEmptyState(
              icon = Icons.Filled.History,
              title = stringResource(R.string.recently_played_disabled_title),
              message = stringResource(R.string.recently_played_disabled_message),
            )
          }
        }

        isLoading && recentItems.isEmpty() -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(48.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        recentItems.isEmpty() && libraryItems.isEmpty() && !isLoading -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            OttEmptyState(
              icon = Icons.Outlined.PlayCircle,
              title = "Nothing watched yet",
              message = "Start watching movies and shows to build your continue watching history.",
            )
          }
        }

        else -> {
          OttContinueWatchingContent(
            recentItems = recentItems,
            libraryItems = libraryItems,
            selectionManager = selectionManager,
            onVideoClick = { video ->
              MediaUtils.playFile(video, context, "recently_played")
            },
            onPlaylistClick = { playlistItem ->
              backStack.add(PlaylistDetailScreen(playlistItem.playlist.id))
            },
            onWatchlistItemClick = { libraryItem ->
              coroutineScope.launch(Dispatchers.IO) {
                val api = APIHolder.apis.firstOrNull { it.name.equals(libraryItem.apiName, true) }
                  ?: APIHolder.getApi(libraryItem.apiName)
                if (api != null) {
                  val response = runCatching { api.load(libraryItem.url) }.getOrNull()
                  if (response != null) {
                    val (dataUrl, epTitle) = when (response) {
                      is MovieLoadResponse -> response.dataUrl to response.name
                      is TvSeriesLoadResponse -> {
                        val firstEp = response.episodes.firstOrNull()
                        (firstEp?.data ?: response.url) to (firstEp?.name ?: response.name)
                      }
                      else -> response.url to response.name
                    }
                    val links = mutableListOf<ExtractorLink>()
                    runCatching {
                      api.loadLinks(dataUrl, isCasting = false, subtitleCallback = {}) { link ->
                        links.add(link)
                      }
                    }
                    withContext(Dispatchers.Main) {
                      if (links.isNotEmpty()) {
                        val bestLink = links.maxByOrNull { it.quality } ?: links.first()
                        RexPlayerBridge.playStream(
                          context = context,
                          link = bestLink,
                          title = epTitle,
                          posterUrl = response.posterUrl ?: libraryItem.posterUrl,
                          overview = response.plot,
                          year = response.year?.toString(),
                          providerName = libraryItem.apiName,
                          allLinks = links
                        )
                      } else {
                        android.widget.Toast.makeText(context, "No stream links found for ${libraryItem.title}", android.widget.Toast.LENGTH_SHORT).show()
                      }
                    }
                  } else {
                    withContext(Dispatchers.Main) {
                      android.widget.Toast.makeText(context, "Could not load stream details for ${libraryItem.title}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                  }
                } else {
                  withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Provider '${libraryItem.apiName}' not active", android.widget.Toast.LENGTH_SHORT).show()
                  }
                }
              }
            },
            listState = listState,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
          )
        }
      }

      // Delete confirmation dialog
      if (deleteDialogOpen.value && selectionManager.isInSelectionMode) {
        val itemCount = selectionManager.selectedCount
        val itemText = pluralStringResource(R.plurals.item_type_item_plural, itemCount)
        val deleteFiles = deleteFilesCheckbox.value

        val title = if (deleteFiles) {
          stringResource(R.string.delete_files_title, itemCount, itemText)
        } else {
          stringResource(R.string.remove_from_history_title, itemCount, itemText)
        }

        val subtitle = if (deleteFiles) {
          stringResource(R.string.delete_files_msg)
        } else {
          stringResource(R.string.remove_from_history_msg, itemText)
        }

        ConfirmDialog(
          title = title,
          subtitle = subtitle,
          customContent = {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              androidx.compose.material3.Checkbox(
                checked = deleteFilesCheckbox.value,
                onCheckedChange = {
                  deleteFilesCheckbox.value = it
                },
              )
              Text(
                text = stringResource(R.string.also_delete_files),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          },
          onConfirm = {
            selectionManager.deleteSelected(deleteFilesCheckbox.value)
            deleteDialogOpen.value = false
            deleteFilesCheckbox.value = false
          },
          onCancel = {
            deleteDialogOpen.value = false
            deleteFilesCheckbox.value = false
          },
        )
      }
    }
  }
}

/**
 * Modern OTT Continue Watching & Recently Played 2.0 Content Container
 */
@Composable
private fun OttContinueWatchingContent(
  recentItems: List<RecentlyPlayedItem>,
  libraryItems: List<LibraryItem>,
  selectionManager: SelectionManager<RecentlyPlayedItem, String>,
  onVideoClick: (Video) -> Unit,
  onPlaylistClick: (RecentlyPlayedItem.PlaylistItem) -> Unit,
  onWatchlistItemClick: (LibraryItem) -> Unit,
  listState: LazyListState,
  onRefresh: suspend () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isRefreshing = remember { mutableStateOf(false) }

  // Partition in-progress continue watching items vs full watch history
  val continueWatchingItems = remember(recentItems) {
    recentItems.filter { item ->
      item is RecentlyPlayedItem.VideoItem && item.progress != null && item.progress > 0.02f && item.progress < 0.95f
    }
  }

  val otherItems = remember(recentItems, continueWatchingItems) {
    if (continueWatchingItems.isNotEmpty()) {
      recentItems.filterNot { it in continueWatchingItems }
    } else {
      recentItems
    }
  }

  val watchlistItems = remember(libraryItems) {
    val filtered = libraryItems.filter { it.watchStatus == 0 }
    if (filtered.isNotEmpty()) filtered else libraryItems
  }

  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxSize(),
  ) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // 1. TOP SECTION: Featured Continue Watching OTT Carousel / Row
      if (continueWatchingItems.isNotEmpty() && !selectionManager.isInSelectionMode) {
        item(key = "continue_watching_section_header") {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
              ) {
                Text(
                  text = "${continueWatchingItems.size} in progress",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(
                items = continueWatchingItems,
                key = { item -> "cw_${(item as RecentlyPlayedItem.VideoItem).video.id}" }
              ) { item ->
                val videoItem = item as RecentlyPlayedItem.VideoItem
                OttHeroContinueWatchingCard(
                  item = videoItem,
                  onClick = { onVideoClick(videoItem.video) },
                  onLongClick = { selectionManager.handleLongClick(videoItem) }
                )
              }
            }
          }
        }
      }

      // 2. MIDDLE SECTION: Watchlist (Saved movies & Saved TV shows)
      if (!selectionManager.isInSelectionMode) {
        item(key = "watchlist_middle_section") {
          MaxStreamWatchlistSection(
            items = watchlistItems,
            onItemClick = onWatchlistItemClick
          )
        }
      }

      // 3. BOTTOM SECTION: All Watch History / Mosaic Grid Header
      item(key = "all_history_section_header") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (continueWatchingItems.isNotEmpty()) "Watch History" else "Recently Played",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "${recentItems.size} ${if (recentItems.size == 1) "item" else "items"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
          )
        }
      }

      // 3. OTT Glass Mosaic Cards
      val displayItems = if (continueWatchingItems.isNotEmpty() && !selectionManager.isInSelectionMode) otherItems else recentItems
      items(
        items = displayItems,
        key = { item ->
          when (item) {
            is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}_${item.timestamp}"
            is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}_${item.timestamp}"
          }
        }
      ) { item ->
        val isSelected = selectionManager.isSelected(item)
        when (item) {
          is RecentlyPlayedItem.VideoItem -> {
            OttHistoryMosaicCard(
              item = item,
              isSelected = isSelected,
              isInSelectionMode = selectionManager.isInSelectionMode,
              onClick = {
                if (selectionManager.isInSelectionMode) {
                  selectionManager.toggle(item)
                } else {
                  onVideoClick(item.video)
                }
              },
              onLongClick = { selectionManager.handleLongClick(item) },
              modifier = Modifier.padding(horizontal = 16.dp)
            )
          }
          is RecentlyPlayedItem.PlaylistItem -> {
            OttPlaylistMosaicCard(
              item = item,
              isSelected = isSelected,
              isInSelectionMode = selectionManager.isInSelectionMode,
              onClick = {
                if (selectionManager.isInSelectionMode) {
                  selectionManager.toggle(item)
                } else {
                  onPlaylistClick(item)
                }
              },
              onLongClick = { selectionManager.handleLongClick(item) },
              modifier = Modifier.padding(horizontal = 16.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * 16:9 Premium Hero Continue Watching Card for OTT Top Carousel
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OttHeroContinueWatchingCard(
  item: RecentlyPlayedItem.VideoItem,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var metadata by remember(item.video.id) {
    mutableStateOf<ResolvedRecentlyPlayedMetadata?>(null)
  }

  LaunchedEffect(item.video.id) {
    withContext(Dispatchers.IO) {
      metadata = RecentlyPlayedMetadataResolver.resolve(context, item.video)
    }
  }

  val displayTitle = metadata?.title ?: MediaUtils.extractCleanMediaTitle(item.video.title, item.video.path)
  val heroImage = metadata?.backdropUrl ?: metadata?.posterUrl ?: item.video.path
  val relativeTime = formatRelativeTimestamp(item.timestamp)
  val progressVal = item.progress ?: 0f

  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val cardScale by animateFloatAsState(
    targetValue = if (isPressed) 0.96f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "heroCardScale"
  )

  Surface(
    modifier = modifier
      .width(280.dp)
      .scale(cardScale)
      .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
      .clip(RoundedCornerShape(20.dp))
      .combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true),
        onClick = onClick,
        onLongClick = onLongClick
      ),
    shape = RoundedCornerShape(20.dp),
    color = Color(0x3812131D),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // 16:9 Backdrop with Gradient & Play Button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(148.dp)
          .background(Color.Black)
      ) {
        AsyncImage(
          model = heroImage,
          contentDescription = displayTitle,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color.Black.copy(alpha = 0.1f),
                  Color.Transparent,
                  Color.Black.copy(alpha = 0.85f)
                )
              )
            )
        )

        // OTT Play / Resume Button in Center
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
          border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
          shadowElevation = 8.dp,
          modifier = Modifier
            .size(44.dp)
            .align(Alignment.Center)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Resume",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        // Relative time tag at top right
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color.Black.copy(alpha = 0.65f),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
        ) {
          Text(
            text = relativeTime,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }

        // Progress bar pinned to bottom of banner
        if (progressVal > 0f) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .align(Alignment.BottomCenter)
              .background(Color.White.copy(alpha = 0.2f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressVal)
                .background(
                  Brush.horizontalGradient(
                    listOf(
                      MaterialTheme.colorScheme.primary,
                      Color(0xFFFF0055)
                    )
                  )
                )
            )
          }
        }
      }

      // Title and episode info below image
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = displayTitle,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurface
        )

        val progressText = formatProgressInfo(
          progress = item.progress,
          durationMs = item.video.duration,
          isTv = metadata?.isTvShow ?: false,
          season = metadata?.season,
          episode = metadata?.episode,
          episodeTitle = metadata?.episodeTitle
        )

        if (progressText.isNotBlank()) {
          Text(
            text = progressText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        } else if (!metadata?.subtitle.isNullOrBlank()) {
          Text(
            text = metadata?.subtitle ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

/**
 * OTT Glass Mosaic Card for Watch History List
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OttHistoryMosaicCard(
  item: RecentlyPlayedItem.VideoItem,
  isSelected: Boolean,
  isInSelectionMode: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var metadata by remember(item.video.id) {
    mutableStateOf<ResolvedRecentlyPlayedMetadata?>(null)
  }

  LaunchedEffect(item.video.id) {
    withContext(Dispatchers.IO) {
      metadata = RecentlyPlayedMetadataResolver.resolve(context, item.video)
    }
  }

  val displayTitle = metadata?.title ?: MediaUtils.extractCleanMediaTitle(item.video.title, item.video.path)
  val posterImage = metadata?.posterUrl ?: metadata?.backdropUrl ?: item.video.path
  val relativeTime = formatRelativeTimestamp(item.timestamp)
  val progressVal = item.progress ?: 0f

  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.98f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "historyCardScale"
  )

  val borderStroke = if (isSelected) {
    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
  } else {
    BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
  }

  val cardBg = if (isSelected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
  } else {
    Color(0x33141624)
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .scale(scale)
      .clip(RoundedCornerShape(16.dp))
      .combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true),
        onClick = onClick,
        onLongClick = onLongClick
      ),
    shape = RoundedCornerShape(16.dp),
    color = cardBg,
    border = borderStroke,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Poster / Thumbnail Container with Progress Bar
      Box(
        modifier = Modifier
          .width(84.dp)
          .height(112.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color.Black)
      ) {
        AsyncImage(
          model = posterImage,
          contentDescription = displayTitle,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
              )
            )
        )

        // Play icon badge
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.65f),
          modifier = Modifier
            .size(28.dp)
            .align(Alignment.Center)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        // Progress bar at bottom of poster
        if (progressVal > 0f) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(3.5.dp)
              .align(Alignment.BottomCenter)
              .background(Color.White.copy(alpha = 0.3f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressVal)
                .background(
                  Brush.horizontalGradient(
                    listOf(
                      MaterialTheme.colorScheme.primary,
                      Color(0xFFFF0055)
                    )
                  )
                )
            )
          }
        }
      }

      // Metadata Info
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        // Tag badge (Series / Movie / Watched)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (metadata?.isTvShow == true) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ) {
              Text(
                text = "SERIES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          } else {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color.White.copy(alpha = 0.08f)
            ) {
              Text(
                text = "MOVIE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Text(
            text = relativeTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.sp
          )
        }

        // Clean TMDB Title
        Text(
          text = displayTitle,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        // TV Episode / Movie detail line
        val progressText = formatProgressInfo(
          progress = item.progress,
          durationMs = item.video.duration,
          isTv = metadata?.isTvShow ?: false,
          season = metadata?.season,
          episode = metadata?.episode,
          episodeTitle = metadata?.episodeTitle
        )

        if (progressText.isNotBlank()) {
          Text(
            text = progressText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        } else if (!metadata?.subtitle.isNullOrBlank()) {
          Text(
            text = metadata?.subtitle ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Checkbox / Selection state
      if (isInSelectionMode) {
        Icon(
          imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
          contentDescription = null,
          tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.size(24.dp)
        )
      }
    }
  }
}

/**
 * OTT Glass Mosaic Card for Playlist items
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OttPlaylistMosaicCard(
  item: RecentlyPlayedItem.PlaylistItem,
  isSelected: Boolean,
  isInSelectionMode: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val relativeTime = formatRelativeTimestamp(item.timestamp)
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.98f else 1f,
    label = "playlistCardScale"
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .scale(scale)
      .clip(RoundedCornerShape(16.dp))
      .combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true),
        onClick = onClick,
        onLongClick = onLongClick
      ),
    shape = RoundedCornerShape(16.dp),
    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color(0x33141624),
    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Surface(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = item.playlist.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${item.videoCount} videos • $relativeTime",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (isInSelectionMode) {
        Icon(
          imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
          contentDescription = null,
          tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.size(24.dp)
        )
      }
    }
  }
}

/**
 * Premium OTT Glass Empty State
 */
@Composable
fun OttEmptyState(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .padding(32.dp)
      .fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = Color(0x3812131D),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.size(72.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
          )
        }
      }

      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.9f)
      )
    }
  }
}
