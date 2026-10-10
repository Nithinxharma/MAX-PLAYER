package xyz.mpv.rex.ui.browser.cinehub

import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.res.painterResource
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import xyz.mpv.rex.cinehub.data.UniversalMetadataSyncService
import xyz.mpv.rex.auth.AuthManager
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.border
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonBanner
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonListItem
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonRow
import xyz.mpv.rex.ui.theme.maxstream.maxStreamShimmer
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.preferences.AccountPreferencesScreen
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.cinehub.extension.api.CineHubMediaDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.cinehub.data.CineFolderMetadataManager
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.data.TMDBMovieNode
import xyz.mpv.rex.cinehub.data.TMDBTvNode
import xyz.mpv.rex.cinehub.data.KodiMediaScraper
import xyz.mpv.rex.cinehub.data.NfoScanner
import xyz.mpv.rex.cinehub.model.EpisodeItem
import xyz.mpv.rex.cinehub.model.MovieItem
import xyz.mpv.rex.cinehub.model.TvShowItem
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.preferences.MediaLibraryPreferencesScreen
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaUtils
import java.io.File

private typealias ProfileScreen = AccountPreferencesScreen

data class ExtensionMediaDetails(
  val loadResponse: LoadResponse,
  val providerId: String = "",
  val providerName: String = "",
)

fun loadExtensionItemDetails(
  providerId: String,
  providerName: String,
  url: String,
  scope: kotlinx.coroutines.CoroutineScope,
  fallbackTitle: String = "",
  fallbackPoster: String? = null,
  fallbackYear: Int? = null,
  fallbackType: TvType = TvType.Movie,
  onLoaded: (ExtensionMediaDetails) -> Unit,
) {
  scope.launch(Dispatchers.IO) {
    val api = APIHolder.apis.firstOrNull { it.name.equals(providerName, true) || it.name.equals(providerId, true) }
      ?: APIHolder.getApi(providerName)
    val loadedResponse: LoadResponse? = try {
      api?.load(url)
    } catch (t: Throwable) {
      Log.e("CineHub", "api.load error for $url on $providerName: ${t.message}", t)
      null
    }

    val finalResponse: LoadResponse? = if (loadedResponse != null) {
      if (loadedResponse is com.lagradost.cloudstream3.AnimeLoadResponse) {
        val flattenedEps = loadedResponse.episodes.values.flatten().distinctBy { it.data }
        TvSeriesLoadResponse(
          name = loadedResponse.name.ifBlank { fallbackTitle.ifBlank { url } },
          url = loadedResponse.url,
          apiName = loadedResponse.apiName.ifBlank { providerName },
          type = TvType.Anime,
          episodes = flattenedEps,
          posterUrl = loadedResponse.posterUrl ?: fallbackPoster,
          year = loadedResponse.year ?: fallbackYear,
          plot = loadedResponse.plot,
          backgroundPosterUrl = loadedResponse.backgroundPosterUrl ?: loadedResponse.posterUrl ?: fallbackPoster
        )
      } else {
        if (loadedResponse.name.isBlank() && fallbackTitle.isNotBlank()) {
          loadedResponse.name = fallbackTitle
        }
        if (loadedResponse.posterUrl.isNullOrBlank() && !fallbackPoster.isNullOrBlank()) {
          loadedResponse.posterUrl = fallbackPoster
        }
        if (loadedResponse.year == null && fallbackYear != null) {
          loadedResponse.year = fallbackYear
        }
        loadedResponse
      }
    } else {
      val registry = org.koin.java.KoinJavaComponent.get<xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry>(xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry::class.java)
      val provider = registry.getProvider(providerId)
        ?: registry.getAllProviders().firstOrNull { it.name.equals(providerName, true) }
      val details = try {
        provider?.loadDetails(url)
      } catch (t: Throwable) {
        Log.e("CineHub", "provider.loadDetails error for $url: ${t.message}", t)
        null
      }
      if (details != null) {
        if (details.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries || details.episodes.isNotEmpty()) {
          TvSeriesLoadResponse(
            name = details.title.ifBlank { fallbackTitle.ifBlank { url } },
            url = details.url,
            apiName = details.providerName.ifBlank { providerName },
            type = TvType.TvSeries,
            episodes = details.episodes.map { ep ->
              Episode(
                data = ep.data,
                name = ep.name,
                season = ep.season,
                episode = ep.episode,
                posterUrl = ep.posterUrl,
                description = ep.description
              )
            },
            posterUrl = details.posterUrl ?: fallbackPoster,
            year = details.year ?: fallbackYear,
            plot = details.overview,
            backgroundPosterUrl = details.backdropUrl ?: details.posterUrl ?: fallbackPoster
          )
        } else {
          MovieLoadResponse(
            name = details.title.ifBlank { fallbackTitle.ifBlank { url } },
            url = details.url,
            apiName = details.providerName.ifBlank { providerName },
            type = TvType.Movie,
            dataUrl = details.url,
            posterUrl = details.posterUrl ?: fallbackPoster,
            year = details.year ?: fallbackYear,
            plot = details.overview,
            backgroundPosterUrl = details.backdropUrl ?: details.posterUrl ?: fallbackPoster
          )
        }
      } else if (fallbackTitle.isNotBlank() || url.isNotBlank()) {
        MovieLoadResponse(
          name = fallbackTitle.ifBlank { url },
          url = url,
          apiName = providerName,
          type = fallbackType,
          dataUrl = url,
          posterUrl = fallbackPoster,
          year = fallbackYear,
          backgroundPosterUrl = fallbackPoster
        )
      } else null
    }

    if (finalResponse != null) {
      withContext(Dispatchers.Main) {
        onLoaded(ExtensionMediaDetails(finalResponse, providerId, providerName.ifBlank { finalResponse.apiName }))
      }
    }
  }
}

data class StreamFailureReason(
  val title: String,
  val description: String,
  val provider: String,
  val url: String
)

fun diagnoseFailure(providerName: String, url: String, exception: Throwable?, linksCount: Int): StreamFailureReason? {
  if (linksCount > 0 && exception == null) return null
  val msg = exception?.message ?: ""
  val cause = exception?.cause?.message ?: ""
  val full = "$msg $cause ${exception?.javaClass?.simpleName ?: ""}"

  val (title, desc) = when {
    exception is java.net.UnknownHostException || full.contains("Unable to resolve host", ignoreCase = true) -> {
      "Network Connection Error" to "Unable to connect to streaming server. Please check your internet connection and try again."
    }
    full.contains("403") || full.contains("Cloudflare", ignoreCase = true) || full.contains("Turnstile", ignoreCase = true) || full.contains("Just a moment", ignoreCase = true) -> {
      "Stream Temporarily Unavailable" to "The stream is temporarily protected or unavailable. Please try again later."
    }
    exception is java.net.SocketTimeoutException || full.contains("timeout", ignoreCase = true) -> {
      "Connection Timeout" to "The streaming server took too long to respond. The server might be temporarily overloaded."
    }
    exception is java.net.ConnectException || full.contains("Connection refused", ignoreCase = true) -> {
      "Stream Offline" to "The streaming server is currently offline. Please try another title."
    }
    exception is javax.net.ssl.SSLException || full.contains("SSL", ignoreCase = true) -> {
      "Security Handshake Error" to "Secure connection to stream failed. Please try again."
    }
    exception != null -> {
      "Playback Error" to "An unexpected error occurred while preparing this media."
    }
    linksCount == 0 -> {
      "No Playable Streams Available" to "No active stream links could be found for this title at this time."
    }
    else -> "No Streams Found" to "No playable stream links were found for this media."
  }

  return StreamFailureReason(title, desc, providerName, url)
}

fun extractAndPlayMovie(
  context: android.content.Context,
  providerName: String,
  dataUrl: String,
  movieTitle: String,
  scope: kotlinx.coroutines.CoroutineScope,
  onDismiss: () -> Unit = {},
  onFailure: ((StreamFailureReason) -> Unit)? = null,
  onLinksLoaded: (List<com.lagradost.cloudstream3.utils.ExtractorLink>, List<com.lagradost.cloudstream3.SubtitleFile>) -> Unit,
) {
  scope.launch(Dispatchers.IO) {
    val api = APIHolder.apis.firstOrNull { it.name.equals(providerName, true) }
      ?: APIHolder.getApi(providerName)
    val links = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
    val subtitles = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()
    var caughtEx: Throwable? = null

    if (api != null) {
      try {
        api.loadLinks(dataUrl, false, subtitleCallback = { sub ->
          synchronized(subtitles) { subtitles.add(sub) }
        }) { link ->
          synchronized(links) { links.add(link) }
        }
      } catch (e: Throwable) {
        caughtEx = e
        android.util.Log.e("CineHub", "Error extracting movie links from $providerName ($dataUrl): ${e.message}", e)
      }
    }

    if (links.isEmpty()) {
      try {
        val registry = org.koin.java.KoinJavaComponent.get<xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry>(xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry::class.java)
        val provider = registry.getProvider(providerName)
          ?: registry.getAllProviders().firstOrNull { it.name.equals(providerName, true) }
        val streams = provider?.loadStreams(dataUrl) ?: emptyList()
        for (st in streams) {
          links.add(
            com.lagradost.cloudstream3.utils.ExtractorLink(
              source = provider?.name ?: "Extension",
              name = st.name,
              url = st.url,
              referer = st.headers["Referer"] ?: "",
              quality = st.quality.filter { it.isDigit() }.toIntOrNull() ?: 1080,
              isM3u8 = st.isM3u8,
              headers = st.headers
            )
          )
        }
      } catch (e: Throwable) {
        if (caughtEx == null) caughtEx = e
        android.util.Log.e("CineHub", "Error in fallback movie stream loading from $providerName: ${e.message}", e)
      }
    }

    val failReason = diagnoseFailure(providerName, dataUrl, caughtEx, links.size)

    withContext(Dispatchers.Main) {
      if (failReason != null) {
        onFailure?.invoke(failReason)
      }
      onLinksLoaded(links, subtitles)
    }
  }
}

fun extractAndPlayEpisode(
  context: android.content.Context,
  providerName: String,
  data: String,
  episodeTitle: String?,
  seriesTitle: String,
  scope: kotlinx.coroutines.CoroutineScope,
  onDismiss: () -> Unit = {},
  onFailure: ((StreamFailureReason) -> Unit)? = null,
  onLinksLoaded: (List<com.lagradost.cloudstream3.utils.ExtractorLink>, List<com.lagradost.cloudstream3.SubtitleFile>) -> Unit,
) {
  scope.launch(Dispatchers.IO) {
    val api = APIHolder.apis.firstOrNull { it.name.equals(providerName, true) }
      ?: APIHolder.getApi(providerName)
    val links = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
    val subtitles = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()
    var caughtEx: Throwable? = null

    if (api != null) {
      try {
        api.loadLinks(data, false, subtitleCallback = { sub ->
          synchronized(subtitles) { subtitles.add(sub) }
        }) { link ->
          synchronized(links) { links.add(link) }
        }
      } catch (e: Throwable) {
        caughtEx = e
        android.util.Log.e("CineHub", "Error extracting episode links from $providerName ($data): ${e.message}", e)
      }
    }

    if (links.isEmpty()) {
      try {
        val registry = org.koin.java.KoinJavaComponent.get<xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry>(xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry::class.java)
        val provider = registry.getProvider(providerName)
          ?: registry.getAllProviders().firstOrNull { it.name.equals(providerName, true) }
        val streams = provider?.loadStreams(data) ?: emptyList()
        for (st in streams) {
          links.add(
            com.lagradost.cloudstream3.utils.ExtractorLink(
              source = provider?.name ?: "Extension",
              name = st.name,
              url = st.url,
              referer = st.headers["Referer"] ?: "",
              quality = st.quality.filter { it.isDigit() }.toIntOrNull() ?: 1080,
              isM3u8 = st.isM3u8,
              headers = st.headers
            )
          )
        }
      } catch (e: Throwable) {
        if (caughtEx == null) caughtEx = e
        android.util.Log.e("CineHub", "Error in fallback episode stream loading from $providerName: ${e.message}", e)
      }
    }

    val failReason = diagnoseFailure(providerName, data, caughtEx, links.size)

    withContext(Dispatchers.Main) {
      if (failReason != null) {
        onFailure?.invoke(failReason)
      }
      onLinksLoaded(links, subtitles)
    }
  }
}

@Serializable
object CineHubScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()
    val database = koinInject<xyz.mpv.rex.database.MpvExDatabase>()
    val libraryDao = database.cineLibraryDao()
    val libraryItems by libraryDao.getAllLibraryItems().collectAsState(initial = emptyList())
    val providerRegistry = koinInject<xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry>()
    val extensionManager = koinInject<xyz.mpv.rex.cinehub.extension.manager.ExtensionManager>()
    val recentlyPlayedRepository = koinInject<xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository>()
    val playbackStateRepository = koinInject<xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository>()
    val repositoryManager = koinInject<xyz.mpv.rex.cinehub.extension.manager.RepositoryManager>()

    val activeProvidersList by providerRegistry.activeProviders.collectAsState()
    val registeredProvidersList by providerRegistry.registeredProviders.collectAsState()
    val installedExtensionsList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
      Log.i("CineHubScreen", "INSTANCE_IDENTITY: CineHubScreen composed. identityHashCode=${System.identityHashCode(this)}, ProviderRegistry.identityHashCode=${System.identityHashCode(providerRegistry)}, ExtensionManager.identityHashCode=${System.identityHashCode(extensionManager)}, APIHolder.identityHashCode=${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
    }

    val enableLocalMovies by browserPreferences.enableLocalMovies.collectAsState()
    val enableLocalTvShows by browserPreferences.enableLocalTvShows.collectAsState()
    val enableMetadataScraping by browserPreferences.enableMetadataScraping.collectAsState()
    val enableArtworkDownloads by browserPreferences.enableArtworkDownloads.collectAsState()

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedActiveProvider by remember { mutableStateOf("All Providers") }
    var showRepoInstallerDialog by remember { mutableStateOf(false) }
    val searchQuery = CineHubSearchStateHolder.searchQuery
    val isSearchActive = CineHubSearchStateHolder.isSearchActive

    // Intercept Back button when Search is active to collapse Search and return to Home
    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
      CineHubSearchStateHolder.isSearchActive = false
    }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    var showScraperSheet by remember { mutableStateOf(false) }
    var isScrapingInProgress by remember { mutableStateOf(false) }
    var scrapeProgressCurrent by remember { mutableIntStateOf(0) }
    var scrapeProgressTotal by remember { mutableIntStateOf(0) }
    var scrapeCurrentItemName by remember { mutableStateOf("") }
    var scrapeFinishedResult by remember { mutableStateOf<String?>(null) }

    var localMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var localTvShows by remember { mutableStateOf<List<TvShowItem>>(emptyList()) }
    var downloadedVideos by remember { mutableStateOf<List<xyz.mpv.rex.cinehub.download.DownloadedVideoItem>>(emptyList()) }
    var continueWatchingItems by remember { mutableStateOf<List<xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem>>(emptyList()) }

    LaunchedEffect(selectedCategory) {
      if (selectedCategory == "Library") {
        withContext(Dispatchers.IO) {
          downloadedVideos = xyz.mpv.rex.cinehub.download.CineDownloadManager.getDownloadedVideos(context)
        }
      }
    }

    var extensionSearchResults by remember { mutableStateOf<List<xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var providerHomeRows by remember { mutableStateOf<List<xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList>>(emptyList()) }
    var seeAllSheetData by remember { mutableStateOf<Pair<String, List<xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem>>?>(null) }

    var selectedDetailItem by remember { mutableStateOf<Any?>(null) }
    var activeSheetItem by remember { mutableStateOf<Any?>(null) }

    var pendingStreamTitle by remember { mutableStateOf("") }
    var pendingStreamLinks by remember { mutableStateOf<List<com.lagradost.cloudstream3.utils.ExtractorLink>>(emptyList()) }
    var pendingSubtitles by remember { mutableStateOf<List<com.lagradost.cloudstream3.SubtitleFile>>(emptyList()) }
    var pendingEpisodeMetadataJson by remember { mutableStateOf<String?>(null) }

    val navBarHeight = LocalNavigationBarHeight.current

    // Load media data function
    fun loadMedia() {
      scope.launch(Dispatchers.IO) {
        isLoading = true
        try {
          // Scan local directories if permitted/configured
          if (enableLocalMovies) {
            val scanned = CineFolderMetadataManager.getAllLocalMovies(context).toMutableList()

            withContext(Dispatchers.Main) {
              localMovies = scanned
            }
          }

          if (enableLocalTvShows) {
            val scannedTv = CineFolderMetadataManager.getAllLocalTvShows(context).toMutableList()

            withContext(Dispatchers.Main) {
              localTvShows = scannedTv
            }
          }

          // Query dynamic home rows from all enabled extension providers
          val activeProviders = providerRegistry.getEnabledProviders()
          val extHomeLists = mutableListOf<xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList>()
          for (provider in activeProviders) {
            if (selectedActiveProvider != "All Providers" && !provider.name.equals(selectedActiveProvider, ignoreCase = true) && !provider.id.equals(selectedActiveProvider, ignoreCase = true)) {
              continue
            }
            val rows = runCatching { provider.getHomePage() }.getOrDefault(emptyList())
            extHomeLists.addAll(rows)
          }
          withContext(Dispatchers.Main) {
            providerHomeRows = extHomeLists
          }

          // Load Continue Watching from playback history and state as a prioritized stack
          val recentEntities = runCatching { recentlyPlayedRepository.getRecentlyPlayed(limit = 50) }.getOrDefault(emptyList())
          val playbackStates = runCatching { playbackStateRepository.getAllPlaybackStates() }.getOrDefault(emptyList())
          val cwMap = mutableMapOf<String, xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem>()

          // 1. Process recent playback entities (ordered most recent first)
          for (entity in recentEntities) {
            val path = entity.filePath
            if (path.isBlank() || cwMap.containsKey(path)) continue
            val state = playbackStates.find {
              it.mediaTitle.equals(entity.videoTitle, ignoreCase = true) ||
              it.mediaTitle.equals(path, ignoreCase = true) ||
              it.mediaTitle.equals(File(path).name, ignoreCase = true)
            }
            val lastPos = state?.lastPosition?.toLong() ?: 0L
            val stateTotal = state?.let { (it.lastPosition + it.timeRemaining).toLong() } ?: 0L
            val entityDurSec = if (entity.duration > 0L) {
              if (entity.duration > 100_000L) entity.duration / 1000L else entity.duration
            } else 0L
            val totalDur = if (entityDurSec > 0L) entityDurSec else stateTotal
            val fraction = if (totalDur > 0L) (lastPos.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f) else 0f

            val title = entity.videoTitle?.ifBlank { null } ?: File(path).nameWithoutExtension
            val epMatch = Regex("""\b[sS](\d+)[eE](\d+)\b""").find(title)
            val epInfo = epMatch?.let { "S${it.groupValues[1]} E${it.groupValues[2]}" }

            val localMovieMatch = localMovies.find { it.videoFilePath == path || it.title.equals(title, ignoreCase = true) }
            val localTvMatch = localTvShows.find { it.folderPath == path || it.title.equals(title, ignoreCase = true) }
            val fallbackFanart = localMovieMatch?.backdropPath ?: localMovieMatch?.posterPath
              ?: localTvMatch?.backdropPath ?: localTvMatch?.posterPath ?: path
            val resolvedFanart = UniversalMetadataSyncService.getLandscapeFanartSync(title, fallbackFanart)

            cwMap[path] = xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem(
              id = path,
              title = title,
              episodeInfo = epInfo,
              landscapeImageUrl = resolvedFanart ?: fallbackFanart,
              currentPositionSeconds = lastPos,
              totalDurationSeconds = totalDur,
              watchProgressFraction = fraction,
              rawPayload = entity
            )
          }

          // 2. Process remaining playback states with saved progress
          for (state in playbackStates) {
            val titleOrPath = state.mediaTitle
            if (titleOrPath.isBlank() || cwMap.containsKey(titleOrPath)) continue
            if (state.lastPosition > 0) {
              val stateTotal = (state.lastPosition + state.timeRemaining).toLong()
              val fraction = if (stateTotal > 0L) (state.lastPosition.toFloat() / stateTotal.toFloat()).coerceIn(0f, 1f) else 0f
              val epMatch = Regex("""\b[sS](\d+)[eE](\d+)\b""").find(titleOrPath)
              val epInfo = epMatch?.let { "S${it.groupValues[1]} E${it.groupValues[2]}" }

              val localMovieMatch = localMovies.find { it.videoFilePath == titleOrPath || it.title.equals(titleOrPath, ignoreCase = true) }
              val fallbackFanart = localMovieMatch?.backdropPath ?: localMovieMatch?.posterPath ?: titleOrPath
              val resolvedFanart = UniversalMetadataSyncService.getLandscapeFanartSync(titleOrPath, fallbackFanart)

              cwMap[titleOrPath] = xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem(
                id = titleOrPath,
                title = titleOrPath,
                episodeInfo = epInfo,
                landscapeImageUrl = resolvedFanart ?: fallbackFanart,
                currentPositionSeconds = state.lastPosition.toLong(),
                totalDurationSeconds = stateTotal,
                watchProgressFraction = fraction,
                rawPayload = state
              )
            }
          }

          withContext(Dispatchers.Main) {
            continueWatchingItems = cwMap.values.toList()
          }
        } catch (_: Exception) {
        } finally {
          withContext(Dispatchers.Main) {
            isLoading = false
            isRefreshing = false
          }
        }
      }
    }

    LaunchedEffect(activeProvidersList, selectedActiveProvider) {
      loadMedia()
    }

    // Featured Hero Movie (pick from local movies if available)
    val featuredMovie = remember(localMovies) {
      localMovies.firstOrNull { it.backdropPath != null || it.posterPath != null }
        ?: localMovies.firstOrNull()
    }

    // OpenTune 3D CoverFlow Hero Movies (from Trending/Popular/Featured rows + local movies)
    val heroMovies = remember(localMovies, providerHomeRows) {
      val result = mutableListOf<CarouselMovie>()

      // 1. Trending/Popular/Featured rows from CloudStream extensions
      val candidateRows = providerHomeRows.filter { row ->
        row.title.contains("trend", ignoreCase = true) ||
        row.title.contains("popular", ignoreCase = true) ||
        row.title.contains("featured", ignoreCase = true) ||
        row.title.contains("top", ignoreCase = true) ||
        row.title.contains("latest", ignoreCase = true) ||
        row.title.contains("movie", ignoreCase = true)
      }.ifEmpty { providerHomeRows }

      for (row in candidateRows) {
        for (item in row.items) {
          if (!item.posterUrl.isNullOrBlank()) {
            val subtitle = listOfNotNull(
              item.year?.toString(),
              item.providerName.takeIf { it.isNotBlank() }
            ).joinToString(" • ").ifBlank { "Trending Now" }
            val detectedQuality = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectQuality(item)
            val detectedDubSub = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectDubSub(item)
            val isNew = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectIsNew(item.year?.toString())

            val highResFanart = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.toHighResFanart(item.posterUrl)
            val resolvedBackdrop = UniversalMetadataSyncService.getLandscapeFanartSync(item.title, highResFanart ?: item.posterUrl)
            result.add(
              CarouselMovie(
                id = item.id,
                title = item.title,
                subtitle = subtitle,
                posterUrl = item.posterUrl,
                backdropUrl = resolvedBackdrop ?: highResFanart ?: item.posterUrl,
                rating = item.rating,
                year = item.year?.toString(),
                quality = detectedQuality,
                dubSub = detectedDubSub,
                isNew = isNew,
                genres = listOfNotNull(item.providerName.takeIf { it.isNotBlank() }),
                originalItem = item
              )
            )
          }
        }
      }

      // 2. Local movies scanned from local storage / SMB
      for (movie in localMovies) {
        if (!movie.posterPath.isNullOrBlank() || !movie.backdropPath.isNullOrBlank()) {
          val subtitle = listOfNotNull(
            movie.premiered.takeIf { it.isNotBlank() },
            movie.genre.takeIf { it.isNotBlank() }
          ).joinToString(" • ").ifBlank { "Featured Movie" }
          val detectedQuality = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectQuality(movie)
          val detectedDubSub = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectDubSub(movie)
          val isNew = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectIsNew(movie.premiered)
          val genresList = movie.genre.split(",").map { it.trim() }.filter { it.isNotBlank() }

          result.add(
            CarouselMovie(
              id = movie.videoFilePath,
              title = movie.title,
              subtitle = subtitle,
              posterUrl = movie.posterPath ?: movie.backdropPath,
              backdropUrl = xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.toHighResFanart(movie.backdropPath)
                ?: movie.backdropPath ?: movie.posterPath,
              rating = movie.userRating.takeIf { it > 0.0 },
              year = movie.premiered.take(4).takeIf { it.isNotBlank() },
              quality = detectedQuality,
              dubSub = detectedDubSub,
              isNew = isNew,
              genres = genresList,
              originalItem = movie
            )
          )
        }
      }

      val distinct = result.distinctBy { it.title.lowercase().trim() }
      if (distinct.isNotEmpty()) {
        distinct.take(10)
      } else {
        defaultCuratedHeroMovies
      }
    }

    // Dynamic Category Filter Chips (Part 4)
    val baseCategories = remember {
      listOf("All", "Movies", "TV Shows", "Anime", "Cartoons", "K-Drama", "Asian Drama", "Live TV", "Sports", "Documentary")
    }
    val dynamicCategories = remember(providerHomeRows) {
      providerHomeRows.map { it.title.trim() }.filter { t ->
        t.isNotBlank() && baseCategories.none { it.equals(t, ignoreCase = true) }
      }.distinct().take(6)
    }
    val allCategoryTabs = remember(dynamicCategories) {
      (baseCategories + dynamicCategories).distinct()
    }

    val authManager = koinInject<AuthManager>()
    val syncService = koinInject<xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService>()
    val authUser by authManager.firebaseUser.collectAsState()
    val userProfile by authManager.userProfile.collectAsState()
    val isAdmin by authManager.isAdmin.collectAsState()
    val isSyncing by syncService.isSyncing.collectAsState()
    val syncStatus by syncService.syncStatus.collectAsState()
    val lastSyncTime by syncService.lastSyncTimeFormatted.collectAsState()
    val loadedProvidersCount by syncService.loadedProvidersCount.collectAsState()
    val photoUrl = userProfile?.photoUrl ?: authUser?.photoUrl?.toString()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_max_stream_mark),
                contentDescription = "MaxStream Logo",
                modifier = Modifier.size(28.dp)
              )
              Text(
                text = stringResource(R.string.cinehub),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          },
          actions = {
            xyz.mpv.rex.ui.theme.maxstream.MaxStreamActiveProviderSelector(
              selectedProviderName = selectedActiveProvider,
              availableProviders = activeProvidersList.map { it.name }.filter { it.isNotBlank() }.distinct(),
              onProviderSelect = { providerName ->
                selectedActiveProvider = providerName
              },
              modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(
              onClick = {
                backstack.add(xyz.mpv.rex.ui.preferences.InstalledExtensionsScreenRoute)
              },
              modifier = Modifier.testTag("cinehub_install_repo_button")
            ) {
              Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = "Manage Extensions",
                tint = MaxStreamTheme.CrimsonAccent,
                modifier = Modifier.size(24.dp)
              )
            }
            IconButton(
              onClick = {
                xyz.mpv.rex.ui.browser.MainScreen.requestTab(1)
              },
              modifier = Modifier.testTag("cinehub_my_media_shortcut_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = "My Media Hub",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
              )
            }
            IconButton(
              onClick = {
                backstack.add(ProfileScreen)
              },
              modifier = Modifier.testTag("cinehub_profile_button"),
            ) {
              if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                  model = photoUrl,
                  contentDescription = "Profile",
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaxStreamTheme.CrimsonAccent, CircleShape)
                )
              } else {
                Icon(
                  imageVector = Icons.Filled.AccountCircle,
                  contentDescription = "Profile & Settings",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp)
                )
              }
            }
          },
        )
      },
    ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
        if (isLoading && !isRefreshing) {
          xyz.mpv.rex.ui.theme.maxstream.MaxStreamHomeSkeleton(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState()),
            contentPadding = PaddingValues(bottom = navBarHeight + 32.dp)
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navBarHeight + 32.dp),
          ) {
            // Liquid Glass Search Input Field (Expandable)
            item {
              xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamLiquidGlassSearch(
                query = CineHubSearchStateHolder.searchQuery,
                onQueryChange = { query ->
                  CineHubSearchStateHolder.searchQuery = query
                  if (query.length >= 2) {
                    CineHubSearchStateHolder.isSearchActive = true
                    CineHubSearchStateHolder.isSearching = true
                    isSearchingOnline = true
                    scope.launch(Dispatchers.IO) {
                      CineHubSearchStateHolder.addRecentSearch(query)
                      val activeProviders = providerRegistry.getEnabledProviders()
                      val extDeferreds = activeProviders.map { provider ->
                        async {
                          try {
                            provider.search(query)
                          } catch (t: Throwable) {
                            emptyList()
                          }
                        }
                      }
                      val extRes = extDeferreds.awaitAll().flatten()

                      // TMDB Enrichment: Query TMDB ONLY to enrich existing provider results
                      val (tmdbMovies, tmdbTv) = if (extRes.isNotEmpty()) {
                        val tmdbMoviesDeferred = async {
                          try {
                            xyz.mpv.rex.cinehub.data.CineOnlineScraper.executeManualMovieSearch(query, context)
                          } catch (t: Throwable) {
                            emptyList()
                          }
                        }
                        val tmdbTvDeferred = async {
                          try {
                            xyz.mpv.rex.cinehub.data.CineOnlineScraper.executeManualTvSearch(query, context)
                          } catch (t: Throwable) {
                            emptyList()
                          }
                        }
                        Pair(tmdbMoviesDeferred.await(), tmdbTvDeferred.await())
                      } else {
                        Pair(emptyList(), emptyList())
                      }

                      withContext(Dispatchers.Main) {
                        CineHubSearchStateHolder.providerResults.clear()
                        CineHubSearchStateHolder.providerResults.addAll(extRes)
                        CineHubSearchStateHolder.tmdbMovieResults.clear()
                        CineHubSearchStateHolder.tmdbMovieResults.addAll(tmdbMovies)
                        CineHubSearchStateHolder.tmdbTvResults.clear()
                        CineHubSearchStateHolder.tmdbTvResults.addAll(tmdbTv)
                        extensionSearchResults = extRes
                        isSearchingOnline = false
                        CineHubSearchStateHolder.isSearching = false
                        CineHubSearchStateHolder.hasSearched = true
                      }
                    }
                  } else {
                    if (query.isEmpty()) {
                      extensionSearchResults = emptyList()
                      CineHubSearchStateHolder.providerResults.clear()
                      CineHubSearchStateHolder.tmdbMovieResults.clear()
                      CineHubSearchStateHolder.tmdbTvResults.clear()
                      CineHubSearchStateHolder.hasSearched = false
                    }
                  }
                },
                isExpanded = CineHubSearchStateHolder.isSearchActive,
                onExpandedChange = { expanded ->
                  CineHubSearchStateHolder.isSearchActive = expanded
                  if (!expanded) {
                    CineHubSearchStateHolder.searchQuery = ""
                    extensionSearchResults = emptyList()
                    CineHubSearchStateHolder.providerResults.clear()
                    CineHubSearchStateHolder.tmdbMovieResults.clear()
                    CineHubSearchStateHolder.tmdbTvResults.clear()
                    CineHubSearchStateHolder.hasSearched = false
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp)
              )
            }

            // If Search is Active, display Search Discovery & Results
            if (CineHubSearchStateHolder.isSearchActive) {
              if (CineHubSearchStateHolder.searchQuery.isBlank()) {
                item {
                  xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamSearchSuggestions(
                    selectedCategory = CineHubSearchStateHolder.selectedFilterCategory,
                    onCategorySelect = { cat ->
                      CineHubSearchStateHolder.selectedFilterCategory = cat
                      if (cat != "All") {
                        CineHubSearchStateHolder.searchQuery = cat
                        CineHubSearchStateHolder.isSearching = true
                        isSearchingOnline = true
                        scope.launch(Dispatchers.IO) {
                          val activeProviders = providerRegistry.getEnabledProviders()
                          val extRes = activeProviders.map { provider ->
                            async { runCatching { provider.search(cat) }.getOrDefault(emptyList()) }
                          }.awaitAll().flatten()
                          val tmdbM = if (extRes.isNotEmpty()) {
                            runCatching { xyz.mpv.rex.cinehub.data.CineOnlineScraper.executeManualMovieSearch(cat, context) }.getOrDefault(emptyList())
                          } else emptyList()
                          withContext(Dispatchers.Main) {
                            CineHubSearchStateHolder.providerResults.clear()
                            CineHubSearchStateHolder.providerResults.addAll(extRes)
                            CineHubSearchStateHolder.tmdbMovieResults.clear()
                            CineHubSearchStateHolder.tmdbMovieResults.addAll(tmdbM)
                            extensionSearchResults = extRes
                            isSearchingOnline = false
                            CineHubSearchStateHolder.isSearching = false
                            CineHubSearchStateHolder.hasSearched = true
                          }
                        }
                      }
                    },
                    onQuerySelect = { query ->
                      CineHubSearchStateHolder.searchQuery = query
                      CineHubSearchStateHolder.isSearching = true
                      isSearchingOnline = true
                      scope.launch(Dispatchers.IO) {
                        CineHubSearchStateHolder.addRecentSearch(query)
                        val activeProviders = providerRegistry.getEnabledProviders()
                        val extRes = activeProviders.map { provider ->
                          async { runCatching { provider.search(query) }.getOrDefault(emptyList()) }
                        }.awaitAll().flatten()
                        val (tmdbM, tmdbT) = if (extRes.isNotEmpty()) {
                          Pair(
                            runCatching { xyz.mpv.rex.cinehub.data.CineOnlineScraper.executeManualMovieSearch(query, context) }.getOrDefault(emptyList()),
                            runCatching { xyz.mpv.rex.cinehub.data.CineOnlineScraper.executeManualTvSearch(query, context) }.getOrDefault(emptyList())
                          )
                        } else {
                          Pair(emptyList(), emptyList())
                        }
                        withContext(Dispatchers.Main) {
                          CineHubSearchStateHolder.providerResults.clear()
                          CineHubSearchStateHolder.providerResults.addAll(extRes)
                          CineHubSearchStateHolder.tmdbMovieResults.clear()
                          CineHubSearchStateHolder.tmdbMovieResults.addAll(tmdbM)
                          CineHubSearchStateHolder.tmdbTvResults.clear()
                          CineHubSearchStateHolder.tmdbTvResults.addAll(tmdbT)
                          extensionSearchResults = extRes
                          isSearchingOnline = false
                          CineHubSearchStateHolder.isSearching = false
                          CineHubSearchStateHolder.hasSearched = true
                        }
                      }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                  )
                }
              } else if (CineHubSearchStateHolder.isSearching || isSearchingOnline) {
                item {
                  MaxStreamSkeletonRow(
                    itemCount = 4,
                    modifier = Modifier.padding(vertical = 12.dp)
                  )
                }
              } else {
                // Build Verified Playable Discovery Items:
                // Only items from provider results (extensionSearchResults) are displayed.
                // Each provider result is enriched with TMDB metadata (poster, backdrop, rating, description).
                // TMDB-only results with no playable provider sources are never included.
                val allDiscoveryItems = buildList {
                  if (extensionSearchResults.isNotEmpty()) {
                    val tmdbMovies = CineHubSearchStateHolder.tmdbMovieResults.toList()
                    val tmdbTv = CineHubSearchStateHolder.tmdbTvResults.toList()

                    // Deduplicate provider items by unique provider + URL
                    val uniqueProviderItems = extensionSearchResults.distinctBy { "${it.providerId}_${it.url}" }

                    uniqueProviderItems.forEach { ext ->
                      if (ext.url.isNotBlank()) {
                        val cleanTitle = ext.title.trim()

                        // Match provider result to TMDB metadata
                        val matchedMovie = tmdbMovies.firstOrNull { m ->
                          val mTitle = m.title?.trim().orEmpty()
                          mTitle.equals(cleanTitle, ignoreCase = true) ||
                          (cleanTitle.length >= 3 && mTitle.contains(cleanTitle, ignoreCase = true)) ||
                          (mTitle.length >= 3 && cleanTitle.contains(mTitle, ignoreCase = true))
                        }
                        val matchedTv = if (matchedMovie == null) {
                          tmdbTv.firstOrNull { t ->
                            val tName = t.name?.trim().orEmpty()
                            tName.equals(cleanTitle, ignoreCase = true) ||
                            (cleanTitle.length >= 3 && tName.contains(cleanTitle, ignoreCase = true)) ||
                            (tName.length >= 3 && cleanTitle.contains(tName, ignoreCase = true))
                          }
                        } else null

                        // TMDB Enrichment: Enrich provider result with TMDB artwork and rating
                        val enrichedPoster = (matchedMovie?.poster_path ?: matchedTv?.poster_path)?.let { "https://image.tmdb.org/t/p/w500$it" }
                          ?: ext.posterUrl
                        val enrichedBackdrop = (matchedMovie?.backdrop_path ?: matchedTv?.backdrop_path)?.let { "https://image.tmdb.org/t/p/original$it" }
                        val enrichedYear = matchedMovie?.release_date?.take(4) ?: matchedTv?.first_air_date?.take(4) ?: ext.year?.toString()
                        val enrichedRating = matchedMovie?.vote_average ?: matchedTv?.vote_average ?: 8.0
                        val enrichedTitle = matchedMovie?.title ?: matchedTv?.name ?: ext.title
                        val isTv = ext.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries || matchedTv != null
                        val isAnime = ext.type == xyz.mpv.rex.cinehub.extension.api.TvType.Anime

                        add(
                          xyz.mpv.rex.ui.browser.cinehub.components.DiscoveryMediaItem(
                            id = "ext_${ext.providerId}_${ext.url}",
                            title = enrichedTitle,
                            posterUrl = enrichedPoster,
                            backdropUrl = enrichedBackdrop,
                            year = enrichedYear,
                            rating = enrichedRating,
                            mediaType = if (isTv) "TV" else if (isAnime) "ANIME" else "MOVIE",
                            providerName = null,
                            rawItem = ext
                          )
                        )
                      }
                    }
                  }
                }

                if (allDiscoveryItems.isEmpty()) {
                  item {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Text(
                        text = "No playable results found",
                        style = MaterialTheme.typography.titleMedium.copy(
                          fontWeight = FontWeight.Bold,
                          fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "Try searching for a different title.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                      )
                    }
                  }
                } else {
                  item {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = "Results (${allDiscoveryItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = "Playable Streams",
                        style = MaterialTheme.typography.labelMedium.copy(
                          fontWeight = FontWeight.SemiBold,
                          fontSize = 11.sp
                        ),
                        color = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.ElectricCyan
                      )
                    }
                  }

                  // OTT Discovery Cards Horizontal Scrollable Rail
                  item {
                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(14.dp),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      items(allDiscoveryItems, key = { it.id }) { discoveryItem ->
                        xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamDiscoveryCard(
                          item = discoveryItem,
                          onClick = {
                            val raw = discoveryItem.rawItem
                            if (raw is CineHubSearchItem) {
                              loadExtensionItemDetails(
                                providerId = raw.providerId,
                                providerName = raw.providerName,
                                url = raw.url,
                                scope = scope,
                                fallbackTitle = raw.title,
                                fallbackPoster = discoveryItem.posterUrl ?: raw.posterUrl,
                                fallbackYear = raw.year,
                                fallbackType = if (raw.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries) TvType.TvSeries else TvType.Movie
                              ) { details ->
                                selectedDetailItem = details
                              }
                            }
                          }
                        )
                      }
                    }
                  }

                  // Extension detailed list rows for instant direct playback
                  if (extensionSearchResults.isNotEmpty()) {
                    item {
                      Spacer(modifier = Modifier.height(12.dp))
                      Text(
                        text = "Instant Streams",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                      )
                    }
                    items(extensionSearchResults) { extItem ->
                      ExtensionSearchResultRow(
                        item = extItem,
                        onClick = {
                          loadExtensionItemDetails(
                            providerId = extItem.providerId,
                            providerName = extItem.providerName,
                            url = extItem.url,
                            scope = scope,
                            fallbackTitle = extItem.title,
                            fallbackPoster = extItem.posterUrl,
                            fallbackYear = extItem.year,
                            fallbackType = if (extItem.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries) TvType.TvSeries else TvType.Movie
                          ) { details ->
                            selectedDetailItem = details
                          }
                        }
                      )
                    }
                  }
                }
              }
            } else {
              // Dynamic Category Filter Chips (Part 4)
              item {
                LazyRow(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  contentPadding = PaddingValues(end = 16.dp),
                ) {
                  items(allCategoryTabs) { category ->
                    xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassFilterChip(
                      text = category,
                      isSelected = selectedCategory == category,
                      onClick = { selectedCategory = category }
                    )
                  }
                }
              }

              // Hero Movie Carousel (Part 3)
              if (selectedCategory != "Library" && heroMovies.isNotEmpty()) {
                item {
                  HeroMovieCarousel(
                    movies = heroMovies,
                    onMovieClick = { carouselMovie ->
                      val original = carouselMovie.originalItem
                      if (original is MovieItem) {
                        selectedDetailItem = original
                      } else if (original is CineHubSearchItem) {
                        loadExtensionItemDetails(
                          providerId = original.providerId,
                          providerName = original.providerName,
                          url = original.url,
                          scope = scope,
                          fallbackTitle = original.title,
                          fallbackPoster = original.posterUrl,
                          fallbackYear = original.year,
                          fallbackType = if (original.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries) TvType.TvSeries else TvType.Movie
                        ) { details ->
                          selectedDetailItem = details
                        }
                      }
                    },
                    onPlayClick = { carouselMovie ->
                      val original = carouselMovie.originalItem
                      if (original is MovieItem) {
                        playMediaItem(context, original, scope)
                      } else if (original is CineHubSearchItem) {
                        loadExtensionItemDetails(
                          providerId = original.providerId,
                          providerName = original.providerName,
                          url = original.url,
                          scope = scope,
                          fallbackTitle = original.title,
                          fallbackPoster = original.posterUrl,
                          fallbackYear = original.year,
                          fallbackType = if (original.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries) TvType.TvSeries else TvType.Movie
                        ) { details ->
                          playMediaItem(context, details, scope)
                        }
                      }
                    }
                  )
                }
              }

              // Continue Watching Rail (Part 5)
              if (continueWatchingItems.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "Library")) {
                item {
                  xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamContinueWatchingRail(
                    items = continueWatchingItems,
                    onItemClick = { cwItem ->
                      val entity = cwItem.rawPayload as? xyz.mpv.rex.database.entities.RecentlyPlayedEntity
                      if (entity != null) {
                        MediaUtils.playFile(
                          source = entity.filePath,
                          context = context,
                          launchSource = "cinehub",
                          title = entity.videoTitle ?: File(entity.filePath).nameWithoutExtension
                        )
                      } else {
                        MediaUtils.playFile(
                          source = cwItem.id,
                          context = context,
                          launchSource = "cinehub",
                          title = cwItem.title
                        )
                      }
                    }
                  )
                }
              }

              // Category-Filtered Content Rows (Part 4)
              if (true) {
                if (providerHomeRows.isEmpty() && localMovies.isEmpty() && localTvShows.isEmpty()) {
                  item {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 64.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Movie,
                          contentDescription = null,
                          modifier = Modifier.size(64.dp),
                          tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        )
                        Text(
                          text = "No Titles Available",
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = "No media titles are currently available in this category.",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.outline,
                          textAlign = TextAlign.Center,
                        )
                      }
                    }
                  }
                } else {
                  // Extension Rows filtered by category
                  providerHomeRows.forEach { homeRow ->
                    val filteredItems = when (selectedCategory) {
                      "All" -> homeRow.items
                      "Movies" -> homeRow.items.filter { it.type == xyz.mpv.rex.cinehub.extension.api.TvType.Movie || homeRow.title.contains("movie", ignoreCase = true) }
                      "TV Shows" -> homeRow.items.filter { it.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries || homeRow.title.contains("tv", ignoreCase = true) || homeRow.title.contains("series", ignoreCase = true) }
                      "Anime" -> homeRow.items.filter { it.type == xyz.mpv.rex.cinehub.extension.api.TvType.Anime || homeRow.title.contains("anime", ignoreCase = true) || it.title.contains("anime", ignoreCase = true) }
                      "Cartoons" -> homeRow.items.filter { homeRow.title.contains("cartoon", ignoreCase = true) || homeRow.title.contains("animation", ignoreCase = true) || it.title.contains("cartoon", ignoreCase = true) }
                      "K-Drama" -> homeRow.items.filter { homeRow.title.contains("k-drama", ignoreCase = true) || homeRow.title.contains("kdrama", ignoreCase = true) || homeRow.title.contains("korean", ignoreCase = true) }
                      "Asian Drama" -> homeRow.items.filter { homeRow.title.contains("drama", ignoreCase = true) || homeRow.title.contains("asian", ignoreCase = true) || homeRow.title.contains("cdrama", ignoreCase = true) || homeRow.title.contains("jdrama", ignoreCase = true) }
                      "Live TV" -> homeRow.items.filter { it.type == xyz.mpv.rex.cinehub.extension.api.TvType.LiveTv || homeRow.title.contains("live", ignoreCase = true) || homeRow.title.contains("iptv", ignoreCase = true) }
                      "Sports" -> homeRow.items.filter { homeRow.title.contains("sport", ignoreCase = true) }
                      "Documentary" -> homeRow.items.filter { homeRow.title.contains("doc", ignoreCase = true) }
                      else -> {
                        if (homeRow.title.equals(selectedCategory, ignoreCase = true) || homeRow.title.contains(selectedCategory, ignoreCase = true)) {
                          homeRow.items
                        } else emptyList()
                      }
                    }

                    if (filteredItems.isNotEmpty()) {
                      val displayItems = filteredItems
                      val hasMore = filteredItems.size > 5

                      item {
                        SectionHeader(
                          title = homeRow.title,
                          onSeeAllClick = if (hasMore) { { seeAllSheetData = homeRow.title to filteredItems } } else null
                        )
                      }
                      item {
                        LazyRow(
                          contentPadding = PaddingValues(horizontal = 16.dp),
                          horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                          items(displayItems) { item ->
                            MediaPosterCard(
                              title = item.title,
                              posterUrl = item.posterUrl,
                              rating = item.rating ?: 0.0,
                              year = item.year?.toString() ?: "",
                              rawPayload = item,
                              onClick = {
                                loadExtensionItemDetails(
                                  providerId = item.providerId,
                                  providerName = item.providerName,
                                  url = item.url,
                                  scope = scope,
                                  fallbackTitle = item.title,
                                  fallbackPoster = item.posterUrl,
                                  fallbackYear = item.year,
                                  fallbackType = if (item.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries) TvType.TvSeries else TvType.Movie
                                ) { details ->
                                  selectedDetailItem = details
                                }
                              }
                            )
                          }

                          if (hasMore) {
                            item {
                              xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamSeeAllCard(
                                remainingCount = filteredItems.size,
                                onClick = { seeAllSheetData = homeRow.title to filteredItems }
                              )
                            }
                          }
                        }
                      }
                    }
                  }

                  // Local Movies
                  if (localMovies.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "Movies")) {
                    item {
                      SectionHeader(title = "Local Movies")
                    }
                    item {
                      LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        items(localMovies) { movie ->
                          MediaPosterCard(
                            title = movie.title,
                            posterUrl = movie.posterPath,
                            rating = movie.userRating,
                            year = movie.premiered.take(4),
                            rawPayload = movie,
                            onClick = {
                              selectedDetailItem = movie
                            },
                          )
                        }
                      }
                    }
                  }

                  // Local TV Shows
                  if (localTvShows.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "TV Shows" || selectedCategory == "Anime")) {
                    item {
                      SectionHeader(title = "Local TV Series")
                    }
                    item {
                      LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        items(localTvShows) { show ->
                          MediaPosterCard(
                            title = show.title,
                            posterUrl = show.posterPath,
                            rating = show.userRating,
                            year = show.premiered.take(4),
                            rawPayload = show,
                            onClick = {
                              selectedDetailItem = show
                            },
                          )
                        }
                      }
                    }
                  }
                }
              }

              // Library Section
              if (selectedCategory == "Library") {
                if (libraryItems.isEmpty() && continueWatchingItems.isEmpty() && localMovies.isEmpty() && localTvShows.isEmpty()) {
                  item {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Folder,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.outline,
                          modifier = Modifier.size(64.dp),
                        )
                        Text(
                          text = "Your library is empty.",
                          style = MaterialTheme.typography.titleMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                } else {
                  val statuses = listOf(
                    0 to "Planned / Watchlist",
                    1 to "Continue Watching",
                    2 to "Completed",
                    3 to "Dropped"
                  )
                  
                  statuses.forEach { (statusId, label) ->
                    val itemsForStatus = libraryItems.filter { it.watchStatus == statusId }
                    if (itemsForStatus.isNotEmpty()) {
                      item {
                        SectionHeader(title = label)
                      }
                      item {
                        LazyRow(
                          contentPadding = PaddingValues(horizontal = 16.dp),
                          horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                          items(itemsForStatus) { item ->
                            MediaPosterCard(
                              title = item.title,
                              posterUrl = item.posterUrl,
                              rating = 0.0,
                              year = "",
                              rawPayload = item,
                              onClick = {
                                scope.launch(Dispatchers.IO) {
                                  var fetched: Any? = null
                                  if (item.apiName != "tmdb" && item.apiName.isNotBlank()) {
                                    val api = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(item.apiName)
                                    if (api != null) {
                                      fetched = try { api.load(item.url) } catch (e: Exception) { null }
                                    }
                                  }
                                  if (fetched == null) {
                                    fetched = CineOnlineScraper.getOrFetchMovie(context, item.title, item.url)
                                  }
                                  withContext(Dispatchers.Main) {
                                    if (fetched != null) {
                                      selectedDetailItem = fetched
                                    } else {
                                      Toast.makeText(context, "Could not load details", Toast.LENGTH_SHORT).show()
                                    }
                                  }
                                }
                              },
                            )
                          }
                        }
                      }
                    }
                  }

                  if (localMovies.isNotEmpty()) {
                    item {
                      SectionHeader(title = "Local Movies")
                    }
                    item {
                      LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        items(localMovies) { movie ->
                          MediaPosterCard(
                            title = movie.title,
                            posterUrl = movie.posterPath,
                            rating = movie.userRating,
                            year = movie.premiered.take(4),
                            rawPayload = movie,
                            onClick = {
                              selectedDetailItem = movie
                            },
                          )
                        }
                      }
                    }
                  }

                  if (localTvShows.isNotEmpty()) {
                    item {
                      SectionHeader(title = "Local TV Series")
                    }
                    item {
                      LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        items(localTvShows) { show ->
                          MediaPosterCard(
                            title = show.title,
                            posterUrl = show.posterPath,
                            rating = show.userRating,
                            year = show.premiered.take(4),
                            rawPayload = show,
                            onClick = {
                              selectedDetailItem = show
                            },
                          )
                        }
                      }
                    }
                  }

                  if (downloadedVideos.isNotEmpty()) {
                    item {
                      SectionHeader(title = "Downloaded Videos (${downloadedVideos.size})")
                    }
                    item {
                      LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        items(downloadedVideos) { video ->
                          Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                              .width(200.dp)
                              .clickable {
                                MediaUtils.playFile(
                                  source = video.file.absolutePath,
                                  context = context,
                                  launchSource = "cinehub_download",
                                  title = video.name
                                )
                              }
                          ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                              Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                              ) {
                                Icon(
                                  imageVector = Icons.Default.PlayCircle,
                                  contentDescription = "Play Video",
                                  tint = MaterialTheme.colorScheme.primary,
                                  modifier = Modifier.size(32.dp)
                                )
                                IconButton(
                                  onClick = {
                                    xyz.mpv.rex.cinehub.download.CineDownloadManager.deleteDownloadedVideo(video.file)
                                    downloadedVideos = xyz.mpv.rex.cinehub.download.CineDownloadManager.getDownloadedVideos(context)
                                    Toast.makeText(context, "Deleted ${video.name}", Toast.LENGTH_SHORT).show()
                                  }
                                ) {
                                  Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Video",
                                    tint = MaterialTheme.colorScheme.error
                                  )
                                }
                              }
                              Spacer(modifier = Modifier.height(8.dp))
                              Text(
                                text = video.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                              )
                              Text(
                                text = xyz.mpv.rex.utils.media.MediaFormatter.formatFileSize(video.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // Open movie/tv show details in CineDetailBottomSheet
        LaunchedEffect(selectedDetailItem) {
          val item = selectedDetailItem
          if (item != null) {
            activeSheetItem = item
            selectedDetailItem = null
          }
        }

        activeSheetItem?.let { item ->
          CineDetailBottomSheet(
            item = item,
            onDismiss = { activeSheetItem = null }
          )
        }

        // See All Provider Row Sheet
        seeAllSheetData?.let { (title, items) ->
          ModalBottomSheet(
            onDismissRequest = { seeAllSheetData = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
              LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 450.dp)
              ) {
                items(items) { item ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        seeAllSheetData = null
                        loadExtensionItemDetails(
                          providerId = item.providerId,
                          providerName = item.providerName,
                          url = item.url,
                          scope = scope,
                          fallbackTitle = item.title,
                          fallbackPoster = item.posterUrl,
                          fallbackYear = item.year,
                          fallbackType = if (item.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries) TvType.TvSeries else TvType.Movie
                        ) { details ->
                          selectedDetailItem = details
                        }
                      },
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Column(
                      modifier = Modifier.padding(6.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(130.dp)
                          .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                      )
                      Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // 1-Click Extension Repository Installer Dialog
        if (showRepoInstallerDialog) {
          xyz.mpv.rex.cinehub.ui.RepoInstallerDialog(
            repositoryManager = repositoryManager,
            onDismissRequest = { showRepoInstallerDialog = false },
            onInstalledSuccess = {
              scope.launch {
                loadMedia()
              }
            }
          )
        }

        if (pendingStreamLinks.isNotEmpty()) {
          QualitySelectorBottomSheet(
            title = pendingStreamTitle,
            links = pendingStreamLinks,
            subtitles = pendingSubtitles,
            episodeMetadataJson = pendingEpisodeMetadataJson,
            onDismiss = { pendingStreamLinks = emptyList() },
            onLinkSelected = { link ->
              val headersMap = buildMap {
                if (link.referer.isNotBlank()) put("Referer", link.referer)
                putAll(link.headers)
              }
              val subtitlesJson = if (pendingSubtitles.isNotEmpty()) com.lagradost.cloudstream3.mapper.writeValueAsString(pendingSubtitles.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
              MediaUtils.playFile(
                source = link.url,
                context = context,
                launchSource = "cinehub",
                headers = headersMap,
                subtitlesJson = subtitlesJson,
                episodeMetadataJson = pendingEpisodeMetadataJson
              )
              pendingStreamLinks = emptyList()
            }
          )
        }

        // Kodi Media Scraper Bottom Sheet
        if (showScraperSheet) {
          KodiScraperBottomSheet(
            isScraping = isScrapingInProgress,
            progressCurrent = scrapeProgressCurrent,
            progressTotal = scrapeProgressTotal,
            currentItemName = scrapeCurrentItemName,
            resultSummary = scrapeFinishedResult,
            onStartScrape = { dirOption, customPath, downloadArtworkAndNfo ->
              isScrapingInProgress = true
              scrapeFinishedResult = null
              scope.launch(Dispatchers.IO) {
                val dirToScan = when (dirOption) {
                  "downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                  "custom" -> File(customPath)
                  else -> File(Environment.getExternalStorageDirectory(), "Movies")
                }
                val defaultAltDir = if (dirOption == "default") {
                  File(Environment.getExternalStorageDirectory(), "TV Shows")
                } else null
                val defaultCineRexDir = if (dirOption == "default") {
                  File(Environment.getExternalStorageDirectory(), "CineRex")
                } else null
                val defaultMaxStreamDir = if (dirOption == "default") {
                  File(Environment.getExternalStorageDirectory(), "MaxStream")
                } else null

                var totalMovies = 0
                var totalTv = 0

                val dirsToScrape = listOfNotNull(dirToScan, defaultAltDir, defaultMaxStreamDir, defaultCineRexDir).filter { it.exists() }
                for (dir in dirsToScrape) {
                  val result = KodiMediaScraper.scrapeDirectory(
                    context = context,
                    directory = dir,
                    downloadArtworkAndNfo = downloadArtworkAndNfo,
                    onProgress = { cur, tot, item ->
                      scrapeProgressCurrent = cur
                      scrapeProgressTotal = tot
                      scrapeCurrentItemName = item
                    }
                  )
                  totalMovies += result.scrapedMoviesCount
                  totalTv += result.scrapedTvShowsCount
                }

                withContext(Dispatchers.Main) {
                  isScrapingInProgress = false
                  scrapeFinishedResult = "Scraped $totalMovies movies and $totalTv TV shows with posters and Kodi metadata."
                  loadMedia()
                }
              }
            },
            onDismiss = {
              showScraperSheet = false
              scrapeFinishedResult = null
            }
          )
        }
      }
    }
  }

  private fun playMediaItem(
    context: android.content.Context,
    item: Any,
    scope: kotlinx.coroutines.CoroutineScope,
    onLinksLoaded: ((List<com.lagradost.cloudstream3.utils.ExtractorLink>, List<com.lagradost.cloudstream3.SubtitleFile>, String?) -> Unit)? = null
  ) {
    when (item) {
      is MovieItem -> {
        if (item.videoFilePath.startsWith("ext_stream:")) {
          val raw = item.videoFilePath.removePrefix("ext_stream:")
          val providerId = raw.substringBefore("::")
          val dataUrl = raw.substringAfter("::")
          scope.launch(Dispatchers.IO) {
            val registry = org.koin.java.KoinJavaComponent.get<xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry>(xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry::class.java)
            val provider = registry.getProvider(providerId)
            val streams = provider?.loadStreams(dataUrl) ?: emptyList()
            val stream = streams.firstOrNull()
            val extractorLinks = streams.map { s ->
              com.lagradost.cloudstream3.utils.ExtractorLink(
                source = provider?.name ?: "Extension",
                name = s.name.ifBlank { s.quality ?: "Auto" },
                url = s.url,
                referer = s.headers["Referer"] ?: "",
                quality = com.lagradost.cloudstream3.utils.Qualities.Unknown.value,
                headers = s.headers
              )
            }
            withContext(Dispatchers.Main) {
              if (stream != null && stream.url.isNotBlank()) {
                MediaUtils.playFile(
                  source = stream.url,
                  context = context,
                  launchSource = "cinehub",
                  headers = stream.headers,
                  title = item.title,
                  posterUrl = item.posterPath,
                  overview = item.plot,
                  year = item.premiered.take(4),
                  rating = item.userRating.takeIf { it > 0.0 },
                  providerName = provider?.name,
                  allLinks = extractorLinks
                )
              } else {
                Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
              }
            }
          }
        } else {
          MediaUtils.playFile(
            source = item.videoFilePath,
            context = context,
            launchSource = "cinehub",
            title = item.title,
            posterUrl = item.posterPath,
            overview = item.plot,
            year = item.premiered.take(4),
            rating = item.userRating.takeIf { it > 0.0 },
            providerName = "Local Media"
          )
        }
      }
      is TvShowItem -> {
        scope.launch(Dispatchers.IO) {
          val episodes = if (item.folderPath.isNotBlank() && File(item.folderPath).exists()) {
            NfoScanner.scanTvShowEpisodes(File(item.folderPath))
          } else {
            CineOnlineScraper.fetchTvShowEpisodes(context, item.tmdbId.ifBlank { item.title }, 1, item.title)
          }
          val firstEp = episodes.firstOrNull()
          if (firstEp != null && firstEp.videoFilePath.isNotBlank()) {
            val playUri = firstEp.videoFilePath
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "Playing ${item.title} - ${firstEp.title}", Toast.LENGTH_SHORT).show()
              MediaUtils.playFile(
                source = playUri,
                context = context,
                launchSource = "cinehub",
                title = "${item.title} - ${firstEp.title}",
                posterUrl = firstEp.stillPath ?: item.posterPath,
                overview = firstEp.plot ?: item.plot,
                year = item.premiered.take(4),
                rating = item.userRating.takeIf { it > 0.0 },
                providerName = "Local Media"
              )
            }
          } else {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "No playable episodes found for ${item.title}", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
      is ExtensionMediaDetails -> {
        when (item.loadResponse) {
          is MovieLoadResponse -> {
            extractAndPlayMovie(
              context = context,
              providerName = item.providerName.ifBlank { item.loadResponse.apiName },
              dataUrl = item.loadResponse.dataUrl.ifBlank { item.loadResponse.url },
              movieTitle = item.loadResponse.name,
              scope = scope,
              onDismiss = {},
              onLinksLoaded = { links, subs ->
                if (onLinksLoaded != null) {
                  onLinksLoaded(links, subs, null)
                } else {
                  if (links.size == 1) {
                    val link = links.first()
                    val headersMap = buildMap {
                      if (link.referer.isNotBlank()) put("Referer", link.referer)
                      putAll(link.headers)
                    }
                    val subtitlesJson = if (subs.isNotEmpty()) com.lagradost.cloudstream3.mapper.writeValueAsString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                    MediaUtils.playFile(
                      source = link.url,
                      context = context,
                      launchSource = "cinehub",
                      headers = headersMap,
                      subtitlesJson = subtitlesJson,
                      title = item.loadResponse.name,
                      posterUrl = item.loadResponse.posterUrl,
                      overview = item.loadResponse.plot,
                      year = item.loadResponse.year?.toString(),
                      rating = item.loadResponse.score?.score?.toDouble(),
                      providerName = item.providerName.ifBlank { item.loadResponse.apiName },
                      allLinks = links
                    )
                  } else if (links.isEmpty()) {
                    Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                  }
                }
              }
            )
          }
          is TvSeriesLoadResponse -> {
            Toast.makeText(context, "Please select an episode to play", Toast.LENGTH_SHORT).show()
          }
        }
      }
      is MovieLoadResponse -> {
        extractAndPlayMovie(
          context = context,
          providerName = item.apiName,
          dataUrl = item.dataUrl.ifBlank { item.url },
          movieTitle = item.name,
          scope = scope,
          onDismiss = {},
          onLinksLoaded = { links, subs ->
            if (onLinksLoaded != null) {
              onLinksLoaded(links, subs, null)
            } else {
              if (links.size == 1) {
                val link = links.first()
                val headersMap = buildMap {
                  if (link.referer.isNotBlank()) put("Referer", link.referer)
                  putAll(link.headers)
                }
                val subtitlesJson = if (subs.isNotEmpty()) com.lagradost.cloudstream3.mapper.writeValueAsString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                MediaUtils.playFile(
                  source = link.url,
                  context = context,
                  launchSource = "cinehub",
                  headers = headersMap,
                  subtitlesJson = subtitlesJson,
                  title = item.name,
                  posterUrl = item.posterUrl,
                  overview = item.plot,
                  year = item.year?.toString(),
                  rating = item.score?.score?.toDouble(),
                  providerName = item.apiName,
                  allLinks = links
                )
              } else if (links.isEmpty()) {
                Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
              }
            }
          }
        )
      }
      is TvSeriesLoadResponse -> {
        Toast.makeText(context, "Please select an episode to play", Toast.LENGTH_SHORT).show()
      }
    }
  }
}

@Composable
private fun SectionHeader(
  title: String,
  onSeeAllClick: (() -> Unit)? = null
) {
  val isDark = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.isDark
  val titleColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 10.dp)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.2.sp
      ),
      color = titleColor,
    )

    if (onSeeAllClick != null) {
      TextButton(
        onClick = onSeeAllClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
      ) {
        Text(
          text = "Show All",
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
          ),
          color = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Show All",
          tint = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
          modifier = Modifier.size(13.dp)
        )
      }
    }
  }
}

@Composable
private fun FeaturedHeroCard(
  movie: MovieItem,
  onPlayClick: () -> Unit,
  onDetailClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(240.dp)
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(20.dp))
      .clickable { onDetailClick() },
    shape = RoundedCornerShape(20.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AsyncImage(
        model = movie.backdropPath ?: movie.posterPath,
        contentDescription = movie.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )

      // Gradient overlay for readability
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
              startY = 80f,
            )
          ),
      )

      // Overlay Content
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(16.dp),
      ) {
        if (movie.userRating > 0.0) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), CircleShape)
              .padding(horizontal = 8.dp, vertical = 2.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Star,
              contentDescription = null,
              tint = Color(0xFFFFB800),
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = String.format("%.1f", movie.userRating),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        if (movie.plot.isNotBlank()) {
          Text(
            text = movie.plot,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 4.dp),
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(top = 6.dp),
        ) {
          Button(
            onClick = onPlayClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp),
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Watch", fontSize = 13.sp)
          }

          FilledTonalButton(
            onClick = onDetailClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Details", fontSize = 13.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun MediaPosterCard(
  title: String,
  posterUrl: String?,
  rating: Double,
  year: String,
  qualityBadge: String? = null,
  dubSubBadge: String? = null,
  isNew: Boolean = false,
  rawPayload: Any? = null,
  onClick: () -> Unit,
) {
  val detectedQuality = qualityBadge
    ?: rawPayload?.let { xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectQuality(it) }
    ?: xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectQuality(title)
  val detectedDubSub = dubSubBadge
    ?: rawPayload?.let { xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectDubSub(it) }
    ?: xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectDubSub(title)
  val detectedNew = isNew || xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper.detectIsNew(year)

  xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamPosterCard(
    title = title,
    posterUrl = posterUrl,
    subtitle = if (year.isNotBlank()) year else null,
    rating = if (rating > 0.0) rating else null,
    qualityBadge = detectedQuality,
    dubSubBadge = detectedDubSub,
    isNew = detectedNew,
    cardWidth = 142.dp,
    onClick = onClick
  )
}

@Composable
private fun ExtensionSearchResultRow(
  item: xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Card(
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.size(width = 60.dp, height = 90.dp),
    ) {
      if (!item.posterUrl.isNullOrBlank()) {
        AsyncImage(
          model = item.posterUrl,
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
        ) {
          Icon(imageVector = Icons.Outlined.Movie, contentDescription = null)
        }
      }
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
          border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
          Text(
            text = "HD",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
          )
        }
        if (item.year != null) {
          Text(
            text = item.year.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (item.rating != null && item.rating > 0.0) {
          Text(
            text = "★ ${String.format(java.util.Locale.US, "%.1f", item.rating)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB800),
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CineDetailView(
  item: Any,
  onDismiss: () -> Unit,
  onPlay: () -> Unit = {},
  onRefreshItem: (Any) -> Unit = {},
  onLinksLoaded: ((List<com.lagradost.cloudstream3.utils.ExtractorLink>, List<com.lagradost.cloudstream3.SubtitleFile>, String?) -> Unit)? = null
) {
  val database = koinInject<xyz.mpv.rex.database.MpvExDatabase>()
  val libraryDao = database.cineLibraryDao()
  val libraryItems by libraryDao.getAllLibraryItems().collectAsState(initial = emptyList())
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var currentWatchType by remember { mutableStateOf(xyz.mpv.rex.cinehub.model.WatchType.NONE) }
  
  val tmdbId = when (item) {
    is MovieItem -> item.tmdbId.takeIf { it.isNotBlank() } ?: item.title
    is TvShowItem -> item.tmdbId.takeIf { it.isNotBlank() } ?: item.title
    is TMDBMovieNode -> item.id.toString()
    is TMDBTvNode -> item.id.toString()
    is ExtensionMediaDetails -> item.loadResponse.url
    is LoadResponse -> item.url
    is SearchResponse -> item.url
    is CineHubSearchItem -> item.url
    is CineHubMediaDetails -> item.id
    else -> ""
  }
  val isMovie = when (item) {
    is MovieItem -> true
    is TMDBMovieNode -> true
    is TMDBTvNode -> false
    is ExtensionMediaDetails -> item.loadResponse is MovieLoadResponse
    is MovieLoadResponse -> true
    is TvShowItem -> false
    is TvSeriesLoadResponse -> false
    is SearchResponse -> item.type != TvType.TvSeries && item.type != TvType.Anime
    is CineHubSearchItem -> item.type != xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries
    is CineHubMediaDetails -> item.type != xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries
    else -> true
  }
  val libraryEntry = libraryItems.find { it.url == tmdbId }
  val inLibrary = libraryEntry != null
  var showLibraryMenu by remember { mutableStateOf(false) }

  val rawTitle = when (item) {
    is MovieItem -> item.title
    is TvShowItem -> item.title
    is TMDBMovieNode -> item.title ?: ""
    is TMDBTvNode -> item.name ?: ""
    is ExtensionMediaDetails -> item.loadResponse.name
    is LoadResponse -> item.name
    is SearchResponse -> item.name
    is CineHubSearchItem -> item.title
    is CineHubMediaDetails -> item.title
    else -> ""
  }

  // TMDB Metadata Enrichment States with instant cache check
  val cachedEnrichedMovie = remember(item) {
    if (isMovie && rawTitle.isNotBlank()) {
      val (cleanTitle, _) = CineOnlineScraper.cleanMediaFileName(rawTitle)
      xyz.mpv.rex.cinehub.data.MetadataCacheManager.loadFromCache<MovieItem>(context, "movie_$cleanTitle")
        ?: xyz.mpv.rex.cinehub.data.MetadataCacheManager.loadFromCache<MovieItem>(context, "movie_${cleanTitle.lowercase().replace(" ", "_")}")
    } else null
  }
  val cachedEnrichedTvShow = remember(item) {
    if (!isMovie && rawTitle.isNotBlank()) {
      val (cleanTitle, _) = CineOnlineScraper.cleanMediaFileName(rawTitle)
      xyz.mpv.rex.cinehub.data.MetadataCacheManager.loadFromCache<TvShowItem>(context, "tv_$cleanTitle")
        ?: xyz.mpv.rex.cinehub.data.MetadataCacheManager.loadFromCache<TvShowItem>(context, "tv_${cleanTitle.lowercase().replace(" ", "_")}")
    } else null
  }

  var tmdbEnrichedMovie by remember(item) { mutableStateOf<MovieItem?>(cachedEnrichedMovie) }
  var tmdbEnrichedTvShow by remember(item) { mutableStateOf<TvShowItem?>(cachedEnrichedTvShow) }
  var isTmdbEnriching by remember(item) { mutableStateOf(cachedEnrichedMovie == null && cachedEnrichedTvShow == null) }

  var detailPendingLinks by remember { mutableStateOf<List<com.lagradost.cloudstream3.utils.ExtractorLink>>(emptyList()) }
  var detailPendingSubs by remember { mutableStateOf<List<com.lagradost.cloudstream3.SubtitleFile>>(emptyList()) }
  var detailPendingEpJson by remember { mutableStateOf<String?>(null) }
  var detailPendingTitle by remember { mutableStateOf("") }
  var detailPendingPoster by remember { mutableStateOf<String?>(null) }
  var detailPendingOverview by remember { mutableStateOf<String?>(null) }
  var detailPendingYear by remember { mutableStateOf<String?>(null) }
  var detailPendingRating by remember { mutableStateOf<Double?>(null) }
  var detailPendingProvider by remember { mutableStateOf<String?>(null) }
  var isPendingDownloadMode by remember { mutableStateOf(false) }
  var isInstantDownloadExtracting by remember { mutableStateOf(false) }
  val rawPlot = when (item) {
    is MovieItem -> item.plot
    is TvShowItem -> item.plot
    is TMDBMovieNode -> item.overview ?: ""
    is TMDBTvNode -> item.overview ?: ""
    is ExtensionMediaDetails -> item.loadResponse.plot ?: ""
    is LoadResponse -> item.plot ?: ""
    is CineHubMediaDetails -> item.overview ?: ""
    else -> ""
  }
  val rawPosterPath = when (item) {
    is MovieItem -> item.posterPath
    is TvShowItem -> item.posterPath
    is TMDBMovieNode -> item.poster_path?.let { "${CineOnlineScraper.THUMB_BASE_URL}$it" }
    is TMDBTvNode -> item.poster_path?.let { "${CineOnlineScraper.THUMB_BASE_URL}$it" }
    is ExtensionMediaDetails -> item.loadResponse.posterUrl
    is LoadResponse -> item.posterUrl
    is SearchResponse -> item.posterUrl
    is CineHubSearchItem -> item.posterUrl
    is CineHubMediaDetails -> item.posterUrl
    else -> null
  }
  val rawBackdropPath = when (item) {
    is MovieItem -> item.backdropPath ?: item.posterPath
    is TvShowItem -> item.backdropPath ?: item.posterPath
    is TMDBMovieNode -> (item.backdrop_path ?: item.poster_path)?.let { "${CineOnlineScraper.IMAGE_BASE_URL}$it" }
    is TMDBTvNode -> (item.backdrop_path ?: item.poster_path)?.let { "${CineOnlineScraper.IMAGE_BASE_URL}$it" }
    is ExtensionMediaDetails -> item.loadResponse.backgroundPosterUrl ?: item.loadResponse.posterUrl
    is LoadResponse -> item.backgroundPosterUrl ?: item.posterUrl
    is CineHubMediaDetails -> item.backdropUrl ?: item.posterUrl
    is SearchResponse -> item.posterUrl
    else -> null
  }
  val rawRating = when (item) {
    is MovieItem -> item.userRating
    is TvShowItem -> item.userRating
    is TMDBMovieNode -> item.vote_average
    is TMDBTvNode -> item.vote_average
    is ExtensionMediaDetails -> item.loadResponse.score?.score ?: 0.0
    is LoadResponse -> item.score?.score ?: 0.0
    is SearchResponse -> item.score?.score ?: 0.0
    else -> 0.0
  }
  val rawYear = when (item) {
    is MovieItem -> item.premiered.take(4)
    is TvShowItem -> item.premiered.take(4)
    is TMDBMovieNode -> item.release_date?.take(4) ?: ""
    is TMDBTvNode -> item.first_air_date?.take(4) ?: ""
    is ExtensionMediaDetails -> item.loadResponse.year?.toString() ?: ""
    is LoadResponse -> item.year?.toString() ?: ""
    is CineHubMediaDetails -> item.year?.toString() ?: ""
    else -> ""
  }
  val rawGenre = when (item) {
    is MovieItem -> item.genre
    is TvShowItem -> item.genre
    is TMDBMovieNode -> "Movie"
    is TMDBTvNode -> "Series"
    is ExtensionMediaDetails -> item.loadResponse.tags?.firstOrNull() ?: item.loadResponse.type.name
    is LoadResponse -> item.tags?.firstOrNull() ?: item.type.name
    is SearchResponse -> item.type?.name ?: ""
    is CineHubSearchItem -> item.type.name
    is CineHubMediaDetails -> item.type.name
    else -> ""
  }

  // Effect to automatically match provider or local items with TMDB data
  LaunchedEffect(item) {
    if (rawTitle.isNotBlank()) {
      isTmdbEnriching = true
      withContext(Dispatchers.IO) {
        val tmdbIdDigit = when {
          item is MovieItem && item.tmdbId.isNotBlank() && item.tmdbId.all { it.isDigit() } -> item.tmdbId
          item is TvShowItem && item.tmdbId.isNotBlank() && item.tmdbId.all { it.isDigit() } -> item.tmdbId
          item is TMDBMovieNode -> item.id.toString()
          item is TMDBTvNode -> item.id.toString()
          tmdbId.isNotBlank() && tmdbId.all { it.isDigit() } -> tmdbId
          else -> null
        }
        if (isMovie) {
          val enriched = CineOnlineScraper.getOrFetchMovie(context, rawTitle, tmdbIdDigit)
          if (enriched != null) {
            withContext(Dispatchers.Main) {
              tmdbEnrichedMovie = enriched
            }
          }
        } else {
          val enriched = CineOnlineScraper.getOrFetchTvShow(context, rawTitle, tmdbIdDigit)
          if (enriched != null) {
            withContext(Dispatchers.Main) {
              tmdbEnrichedTvShow = enriched
            }
          }
        }
      }
      isTmdbEnriching = false
    }
  }

  // Resolved metadata prioritizing TMDB matched data
  val title = tmdbEnrichedMovie?.title ?: tmdbEnrichedTvShow?.title ?: rawTitle
  val plot = tmdbEnrichedMovie?.plot?.takeIf { it.isNotBlank() && it != "No description." && it != "No description available." && it != "Local Media File." }
    ?: tmdbEnrichedTvShow?.plot?.takeIf { it.isNotBlank() && it != "No description." && it != "No description available." && it != "Local Media File." }
    ?: rawPlot
  val posterPath = tmdbEnrichedMovie?.posterPath ?: tmdbEnrichedTvShow?.posterPath ?: rawPosterPath
  val backdropPath = tmdbEnrichedMovie?.backdropPath ?: tmdbEnrichedTvShow?.backdropPath ?: rawBackdropPath
  val rating = (tmdbEnrichedMovie?.userRating?.takeIf { it > 0.0 } ?: tmdbEnrichedTvShow?.userRating?.takeIf { it > 0.0 } ?: rawRating)
  val year = (tmdbEnrichedMovie?.premiered?.take(4)?.takeIf { it.isNotBlank() && it != "2026" } ?: tmdbEnrichedTvShow?.premiered?.take(4)?.takeIf { it.isNotBlank() && it != "2026" } ?: rawYear)
  val genre = (tmdbEnrichedMovie?.genre?.takeIf { it.isNotBlank() } ?: tmdbEnrichedTvShow?.genre?.takeIf { it.isNotBlank() } ?: rawGenre)
  val actorsList = (tmdbEnrichedMovie?.actors?.takeIf { it.isNotEmpty() } ?: (item as? MovieItem)?.actors?.takeIf { it.isNotEmpty() } ?: tmdbEnrichedTvShow?.actors?.takeIf { it.isNotEmpty() } ?: (item as? TvShowItem)?.actors?.takeIf { it.isNotEmpty() }).orEmpty()

  var isInstantPlayExtracting by remember { mutableStateOf(false) }
  var streamFailureReason by remember { mutableStateOf<StreamFailureReason?>(null) }

  val onInstantAction: (isDownload: Boolean, forceQualitySheet: Boolean) -> Unit = { isDownload, forceQualitySheet ->
    streamFailureReason = null
    if (isDownload) {
      isInstantDownloadExtracting = true
    } else {
      isInstantPlayExtracting = true
    }

    when (item) {
      is MovieItem -> {
        if (item.videoFilePath.startsWith("ext_stream:")) {
          val raw = item.videoFilePath.removePrefix("ext_stream:")
          val providerId = raw.substringBefore("::")
          val dataUrl = raw.substringAfter("::")
          scope.launch(Dispatchers.IO) {
            val registry = org.koin.java.KoinJavaComponent.get<xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry>(xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry::class.java)
            val provider = registry.getProvider(providerId)
            var caughtEx: Throwable? = null
            val streams = try {
              provider?.loadStreams(dataUrl) ?: emptyList()
            } catch (t: Throwable) {
              caughtEx = t
              emptyList()
            }
            val extractorLinks = streams.map { s ->
              com.lagradost.cloudstream3.utils.ExtractorLink(
                source = provider?.name ?: "Extension",
                name = s.name.ifBlank { s.quality ?: "Auto" },
                url = s.url,
                referer = s.headers["Referer"] ?: "",
                quality = s.quality.filter { it.isDigit() }.toIntOrNull() ?: com.lagradost.cloudstream3.utils.Qualities.Unknown.value,
                headers = s.headers
              )
            }.sortedByDescending { it.quality }
            val fail = diagnoseFailure(provider?.name ?: providerId, dataUrl, caughtEx, streams.size)
            withContext(Dispatchers.Main) {
              isInstantPlayExtracting = false
              isInstantDownloadExtracting = false
              if (extractorLinks.isNotEmpty()) {
                if (forceQualitySheet) {
                  isPendingDownloadMode = isDownload
                  detailPendingLinks = extractorLinks
                  detailPendingSubs = emptyList()
                  detailPendingEpJson = null
                  detailPendingTitle = title
                  detailPendingPoster = posterPath
                  detailPendingOverview = plot
                  detailPendingYear = year
                  detailPendingRating = rating.takeIf { it > 0.0 }
                  detailPendingProvider = provider?.name ?: providerId
                } else if (isDownload) {
                  val topLink = extractorLinks.first()
                  xyz.mpv.rex.cinehub.download.CineDownloadManager.downloadStream(context, title, topLink)
                } else {
                  val topLink = extractorLinks.first()
                  onDismiss()
                  MediaUtils.playFile(
                    source = topLink.url,
                    context = context,
                    launchSource = "cinehub",
                    headers = topLink.headers,
                    title = title,
                    posterUrl = posterPath,
                    overview = plot,
                    year = year,
                    rating = rating.takeIf { it > 0.0 },
                    providerName = provider?.name,
                    allLinks = extractorLinks
                  )
                }
              } else {
                streamFailureReason = fail
              }
            }
          }
        } else if (item.videoFilePath.isNotBlank()) {
          isInstantPlayExtracting = false
          isInstantDownloadExtracting = false
          if (isDownload) {
            Toast.makeText(context, "Media file already on local storage", Toast.LENGTH_SHORT).show()
          } else {
            onDismiss()
            MediaUtils.playFile(
              source = item.videoFilePath,
              context = context,
              launchSource = "cinehub",
              title = title,
              posterUrl = posterPath,
              overview = plot,
              year = year,
              rating = rating.takeIf { it > 0.0 },
              providerName = "Local Media"
            )
          }
        } else {
          isInstantPlayExtracting = false
          isInstantDownloadExtracting = false
          onDismiss()
          onPlay()
        }
      }
      is TvShowItem -> {
        scope.launch(Dispatchers.IO) {
          val episodes = if (item.folderPath.isNotBlank() && File(item.folderPath).exists()) {
            NfoScanner.scanTvShowEpisodes(File(item.folderPath))
          } else {
            CineOnlineScraper.fetchTvShowEpisodes(context, item.tmdbId.ifBlank { item.title }, 1, item.title)
          }
          val firstEp = episodes.firstOrNull()
          withContext(Dispatchers.Main) {
            isInstantPlayExtracting = false
            isInstantDownloadExtracting = false
            if (firstEp != null && firstEp.videoFilePath.isNotBlank()) {
              if (isDownload) {
                Toast.makeText(context, "Episode file already on local storage", Toast.LENGTH_SHORT).show()
              } else {
                onDismiss()
                Toast.makeText(context, "Playing ${item.title} - ${firstEp.title}", Toast.LENGTH_SHORT).show()
                MediaUtils.playFile(
                  source = firstEp.videoFilePath,
                  context = context,
                  launchSource = "cinehub",
                  title = "${item.title} - ${firstEp.title}",
                  posterUrl = firstEp.stillPath ?: posterPath,
                  overview = firstEp.plot ?: plot,
                  year = year,
                  rating = rating.takeIf { it > 0.0 },
                  providerName = "Local Media"
                )
              }
            } else {
              onDismiss()
              onPlay()
            }
          }
        }
      }
      is ExtensionMediaDetails -> {
        when (val resp = item.loadResponse) {
          is MovieLoadResponse -> {
            extractAndPlayMovie(
              context = context,
              providerName = item.providerName.ifBlank { resp.apiName },
              dataUrl = resp.dataUrl.ifBlank { resp.url },
              movieTitle = resp.name,
              scope = scope,
              onDismiss = onDismiss,
              onFailure = { streamFailureReason = it },
              onLinksLoaded = { links, subs ->
                isInstantPlayExtracting = false
                isInstantDownloadExtracting = false
                val sortedLinks = links.sortedByDescending { it.quality }
                if (forceQualitySheet) {
                  isPendingDownloadMode = isDownload
                  detailPendingLinks = sortedLinks
                  detailPendingSubs = subs
                  detailPendingEpJson = null
                  detailPendingTitle = title
                  detailPendingPoster = posterPath
                  detailPendingOverview = plot
                  detailPendingYear = year
                  detailPendingRating = rating.takeIf { it > 0.0 }
                  detailPendingProvider = item.providerName.ifBlank { resp.apiName }
                } else if (isDownload) {
                  if (sortedLinks.isNotEmpty()) {
                    val topLink = sortedLinks.first()
                    xyz.mpv.rex.cinehub.download.CineDownloadManager.downloadStream(context, title, topLink)
                  } else {
                    Toast.makeText(context, "No stream links found to download", Toast.LENGTH_SHORT).show()
                  }
                } else if (onLinksLoaded != null) {
                  onLinksLoaded(sortedLinks, subs, null)
                } else if (sortedLinks.isNotEmpty()) {
                  val topLink = sortedLinks.first()
                  val headersMap = buildMap {
                    if (topLink.referer.isNotBlank()) put("Referer", topLink.referer)
                    putAll(topLink.headers)
                  }
                  val subtitlesJson = if (subs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                  MediaUtils.playFile(
                    source = topLink.url,
                    context = context,
                    launchSource = "cinehub",
                    headers = headersMap,
                    subtitlesJson = subtitlesJson,
                    title = title,
                    posterUrl = posterPath,
                    overview = plot,
                    year = year,
                    rating = rating.takeIf { it > 0.0 },
                    providerName = item.providerName.ifBlank { resp.apiName },
                    allLinks = sortedLinks
                  )
                }
              }
            )
          }
          is TvSeriesLoadResponse -> {
            val firstEp = resp.episodes.firstOrNull()
            if (firstEp != null) {
              extractAndPlayEpisode(
                context = context,
                providerName = item.providerName.ifBlank { resp.apiName },
                data = firstEp.data,
                episodeTitle = firstEp.name,
                seriesTitle = resp.name,
                scope = scope,
                onDismiss = onDismiss,
                onFailure = { streamFailureReason = it },
                onLinksLoaded = { links, subs ->
                  isInstantPlayExtracting = false
                  isInstantDownloadExtracting = false
                  val sortedLinks = links.sortedByDescending { it.quality }
                  val epMetadataJson = kotlinx.serialization.json.Json.encodeToString(
                    mapOf(
                      "seriesTitle" to resp.name,
                      "episodeTitle" to (firstEp.name ?: "Episode 1"),
                      "season" to (firstEp.season ?: 1).toString(),
                      "episode" to (firstEp.episode ?: 1).toString()
                    )
                  )
                  val epFullTitle = "$title - S${firstEp.season ?: 1}E${firstEp.episode ?: 1} ${firstEp.name ?: "Episode 1"}"
                  if (forceQualitySheet) {
                    isPendingDownloadMode = isDownload
                    detailPendingLinks = sortedLinks
                    detailPendingSubs = subs
                    detailPendingEpJson = epMetadataJson
                    detailPendingTitle = epFullTitle
                    detailPendingPoster = posterPath
                    detailPendingOverview = plot
                    detailPendingYear = year
                    detailPendingRating = rating.takeIf { it > 0.0 }
                    detailPendingProvider = item.providerName.ifBlank { resp.apiName }
                  } else if (isDownload) {
                    if (sortedLinks.isNotEmpty()) {
                      val topLink = sortedLinks.first()
                      xyz.mpv.rex.cinehub.download.CineDownloadManager.downloadStream(context, epFullTitle, topLink)
                    } else {
                      Toast.makeText(context, "No stream links found to download", Toast.LENGTH_SHORT).show()
                    }
                  } else if (onLinksLoaded != null) {
                    onLinksLoaded(sortedLinks, subs, epMetadataJson)
                  } else if (sortedLinks.isNotEmpty()) {
                    val topLink = sortedLinks.first()
                    val headersMap = buildMap {
                      if (topLink.referer.isNotBlank()) put("Referer", topLink.referer)
                      putAll(topLink.headers)
                    }
                    val subtitlesJson = if (subs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                    MediaUtils.playFile(
                      source = topLink.url,
                      context = context,
                      launchSource = "cinehub",
                      headers = headersMap,
                      subtitlesJson = subtitlesJson,
                      episodeMetadataJson = epMetadataJson,
                      title = epFullTitle,
                      posterUrl = posterPath,
                      overview = plot,
                      year = year,
                      rating = rating.takeIf { it > 0.0 },
                      providerName = item.providerName.ifBlank { resp.apiName },
                      allLinks = sortedLinks
                    )
                  }
                }
              )
            } else {
              isInstantPlayExtracting = false
              isInstantDownloadExtracting = false
              Toast.makeText(context, "No episodes available", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
      is MovieLoadResponse -> {
        extractAndPlayMovie(
          context = context,
          providerName = item.apiName,
          dataUrl = item.dataUrl.ifBlank { item.url },
          movieTitle = item.name,
          scope = scope,
          onDismiss = onDismiss,
          onFailure = { streamFailureReason = it },
          onLinksLoaded = { links, subs ->
            isInstantPlayExtracting = false
            isInstantDownloadExtracting = false
            val sortedLinks = links.sortedByDescending { it.quality }
            if (forceQualitySheet) {
              isPendingDownloadMode = isDownload
              detailPendingLinks = sortedLinks
              detailPendingSubs = subs
              detailPendingEpJson = null
              detailPendingTitle = title
              detailPendingPoster = posterPath
              detailPendingOverview = plot
              detailPendingYear = year
              detailPendingRating = rating.takeIf { it > 0.0 }
              detailPendingProvider = item.apiName
            } else if (isDownload) {
              if (sortedLinks.isNotEmpty()) {
                val topLink = sortedLinks.first()
                xyz.mpv.rex.cinehub.download.CineDownloadManager.downloadStream(context, title, topLink)
              } else {
                Toast.makeText(context, "No stream links found to download", Toast.LENGTH_SHORT).show()
              }
            } else if (onLinksLoaded != null) {
              onLinksLoaded(sortedLinks, subs, null)
            } else if (sortedLinks.isNotEmpty()) {
              val topLink = sortedLinks.first()
              val headersMap = buildMap {
                if (topLink.referer.isNotBlank()) put("Referer", topLink.referer)
                putAll(topLink.headers)
              }
              val subtitlesJson = if (subs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
              MediaUtils.playFile(
                source = topLink.url,
                context = context,
                launchSource = "cinehub",
                headers = headersMap,
                subtitlesJson = subtitlesJson,
                title = title,
                posterUrl = posterPath,
                overview = plot,
                year = year,
                rating = rating.takeIf { it > 0.0 },
                providerName = item.apiName,
                allLinks = sortedLinks
              )
            }
          }
        )
      }
      else -> {
        isInstantPlayExtracting = false
        isInstantDownloadExtracting = false
        onDismiss()
        onPlay()
      }
    }
  }

  val onInstantPlayClick: () -> Unit = { onInstantAction(false, false) }

  val scrollState = rememberScrollState()
  var dragOffsetY by remember { mutableStateOf(0f) }
  val animatedOffsetY by animateFloatAsState(
    targetValue = dragOffsetY,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
    label = "dragToMinimize"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
      .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFA0B0F1A),
            Color(0xFE070912)
          )
        )
      )
      .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
  ) {
    // Frosted Glass Top Drag Handle Pill
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 8.dp)
        .width(42.dp)
        .height(4.5.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.35f))
    )
    if (isTmdbEnriching && tmdbEnrichedMovie == null && tmdbEnrichedTvShow == null && item !is MovieItem && item !is TvShowItem) {
      // Phase 4: TMDB-style loading state to prevent flashing temporary scraped/provider metadata
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = 56.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .maxStreamShimmer(shape = RoundedCornerShape(0.dp))
        )
        Column(
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
          ) {
            Box(
              modifier = Modifier
                .width(100.dp)
                .height(148.dp)
                .maxStreamShimmer(shape = RoundedCornerShape(14.dp))
            )
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(0.85f)
                  .height(24.dp)
                  .maxStreamShimmer(shape = RoundedCornerShape(6.dp))
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                  modifier = Modifier
                    .width(60.dp)
                    .height(22.dp)
                    .maxStreamShimmer(shape = RoundedCornerShape(8.dp))
                )
                Box(
                  modifier = Modifier
                    .width(70.dp)
                    .height(22.dp)
                    .maxStreamShimmer(shape = RoundedCornerShape(8.dp))
                )
              }
              Box(
                modifier = Modifier
                  .width(50.dp)
                  .height(22.dp)
                  .maxStreamShimmer(shape = RoundedCornerShape(8.dp))
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(16.dp)
              .maxStreamShimmer(shape = RoundedCornerShape(4.dp))
          )
          Box(
            modifier = Modifier
              .fillMaxWidth(0.9f)
              .height(16.dp)
              .maxStreamShimmer(shape = RoundedCornerShape(4.dp))
          )
          Box(
            modifier = Modifier
              .fillMaxWidth(0.7f)
              .height(16.dp)
              .maxStreamShimmer(shape = RoundedCornerShape(4.dp))
          )
        }
      }
    } else {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(bottom = 56.dp),
    ) {
      // YouTube-like 16:9 Instant Play Video Banner Header
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .background(Color.Black)
          .clickable { onInstantPlayClick() }
          .testTag("youtube_instant_play_banner"),
        contentAlignment = Alignment.Center
      ) {
        val heroImg = backdropPath ?: posterPath
        if (!heroImg.isNullOrBlank()) {
          AsyncImage(
            model = heroImg,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }

        // Multi-stop cinematic gradient scrim
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color.Black.copy(alpha = 0.50f),
                  Color.Transparent,
                  Color.Black.copy(alpha = 0.80f)
                )
              )
            )
        )

        // YouTube-style glowing circular Instant Play Button
        Surface(
          shape = CircleShape,
          color = if (isInstantPlayExtracting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
          shadowElevation = 10.dp,
          border = BorderStroke(2.dp, Color.White.copy(alpha = 0.45f)),
          modifier = Modifier
            .size(62.dp)
            .combinedClickable(
              onClick = { onInstantAction(false, false) },
              onLongClick = { onInstantAction(false, true) }
            )
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (isInstantPlayExtracting) {
              CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Instant Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
              )
            }
          }
        }

        // Top right quality badge
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color.Black.copy(alpha = 0.65f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 12.dp, end = 16.dp)
        ) {
          Text(
            text = "1080p HD",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
          )
        }
      }

      Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.Top
        ) {
          if (!posterPath.isNullOrBlank()) {
            Card(
              shape = RoundedCornerShape(14.dp),
              elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
              modifier = Modifier
                .width(100.dp)
                .height(148.dp)
            ) {
              AsyncImage(
                model = posterPath,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface
            )

            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (tmdbEnrichedMovie != null || tmdbEnrichedTvShow != null) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFF01B4E4).copy(alpha = 0.2f)
                ) {
                  Text(
                    text = "TMDB",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF01B4E4),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              if (year.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.primaryContainer
                ) {
                  Text(
                    text = year,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }

              if (genre.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                  Text(
                    text = genre,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
              xyz.mpv.rex.ui.theme.maxstream.MaxStreamWatchTypeChip(
                currentWatchType = currentWatchType,
                onWatchTypeSelected = { selected ->
                  currentWatchType = selected
                }
              )
            }

            if (rating > 0.0) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .background(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    RoundedCornerShape(10.dp)
                  )
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Star,
                  contentDescription = null,
                  tint = Color(0xFFFFB800),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = String.format("%.1f", rating),
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onTertiaryContainer
                )
              }
            }
          }
        }

        if (plot.isNotBlank()) {
          Text(
            text = plot,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
          )
        }

        if (actorsList.isNotEmpty()) {
          Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
              text = "Top Cast",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(actorsList) { actor ->
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.width(72.dp)
                ) {
                  AsyncImage(
                    model = actor.thumbUrl ?: "https://ui-avatars.com/api/?name=${actor.name}&background=random",
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .size(56.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceVariant)
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = actor.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                  )
                  if (actor.character.isNotBlank()) {
                    Text(
                      text = actor.character,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                  }
                }
              }
            }
          }
        }

        // Primary Action Buttons: Play (Top Quality) + Download (Top Quality) with Hold-to-Select
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassButton(
            text = if (isInstantPlayExtracting) "Loading..." else "Play",
            onClick = { onInstantAction(false, false) },
            icon = Icons.Rounded.PlayArrow,
            variant = xyz.mpv.rex.ui.theme.maxstream.GlassButtonVariant.Primary,
            isLoading = isInstantPlayExtracting,
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
          )

          xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassButton(
            text = if (isInstantDownloadExtracting) "Resolving..." else "Download",
            onClick = { onInstantAction(true, false) },
            icon = Icons.Outlined.CloudDownload,
            variant = xyz.mpv.rex.ui.theme.maxstream.GlassButtonVariant.Secondary,
            isLoading = isInstantDownloadExtracting,
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
          )
        }

        // Stream Link Extraction Glass Chips section inside CineDetailView
        if (detailPendingLinks.isNotEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 14.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0x381A2234))
              .border(1.dp, MaxStreamTheme.ElectricCyan.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
              .padding(14.dp),
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
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaxStreamTheme.ElectricCyan)
                )
                Text(
                  text = if (isPendingDownloadMode) "Select Download Source Link" else "Available Stream Mirrors (${detailPendingLinks.size})",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
              }
              IconButton(
                onClick = { detailPendingLinks = emptyList() },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Dismiss Stream Mirrors",
                  tint = Color.White.copy(alpha = 0.8f),
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(detailPendingLinks) { link ->
                val qualityText = when {
                  link.quality >= 2160 -> "4K UHD"
                  link.quality >= 1080 -> "1080p HD"
                  link.quality >= 720 -> "720p HD"
                  link.quality > 0 -> "${link.quality}p"
                  else -> "Auto"
                }
                val sourceText = link.source.ifBlank { "Stream Link" }

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0x60182030),
                  border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                      if (isPendingDownloadMode) {
                        xyz.mpv.rex.cinehub.download.CineDownloadManager.downloadStream(context, detailPendingTitle, link)
                        detailPendingLinks = emptyList()
                      } else {
                        val headersMap = buildMap {
                          if (link.referer.isNotBlank()) put("Referer", link.referer)
                          putAll(link.headers)
                        }
                        val subtitlesJson = if (detailPendingSubs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(detailPendingSubs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                        onDismiss()
                        MediaUtils.playFile(
                          source = link.url,
                          context = context,
                          launchSource = "cinehub",
                          headers = headersMap,
                          subtitlesJson = subtitlesJson,
                          episodeMetadataJson = detailPendingEpJson,
                          title = detailPendingTitle,
                          posterUrl = detailPendingPoster,
                          overview = detailPendingOverview,
                          year = detailPendingYear,
                          rating = detailPendingRating,
                          providerName = detailPendingProvider ?: sourceText,
                          allLinks = detailPendingLinks
                        )
                        detailPendingLinks = emptyList()
                      }
                    }
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaxStreamTheme.CrimsonAccent)
                    )
                    Column {
                      Text(
                        text = qualityText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                      )
                      Text(
                        text = sourceText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.70f)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Library Actions
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box {
            OutlinedButton(
              onClick = { showLibraryMenu = true },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
            ) {
              Icon(
                imageVector = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (inLibrary) {
                  when (libraryEntry?.watchStatus) {
                    0 -> "In Watchlist"
                    1 -> "Watching"
                    2 -> "Completed"
                    3 -> "Dropped"
                    else -> "Saved to Library"
                  }
                } else "Add to Library",
                fontWeight = FontWeight.Bold
              )
            }
            
            DropdownMenu(
              expanded = showLibraryMenu,
              onDismissRequest = { showLibraryMenu = false }
            ) {
              val options = listOf(
                1 to "Watching",
                0 to "Plan to Watch",
                2 to "Completed",
                4 to "On Hold",
                3 to "Dropped"
              )
              options.forEach { (status, label) ->
                DropdownMenuItem(
                  text = { Text(label) },
                  onClick = {
                    showLibraryMenu = false
                    scope.launch(Dispatchers.IO) {
                      libraryDao.insertLibraryItem(
                        xyz.mpv.rex.cinehub.extension.model.LibraryItem(
                          url = tmdbId,
                          apiName = "tmdb",
                          title = title,
                          posterUrl = posterPath,
                          type = if (isMovie) 0 else 1,
                          watchStatus = status
                        )
                      )
                      val watchType = when (status) {
                        1 -> com.lagradost.cloudstream3.ui.WatchType.WATCHING
                        0 -> com.lagradost.cloudstream3.ui.WatchType.PLANTOWATCH
                        2 -> com.lagradost.cloudstream3.ui.WatchType.COMPLETED
                        4 -> com.lagradost.cloudstream3.ui.WatchType.ONHOLD
                        3 -> com.lagradost.cloudstream3.ui.WatchType.DROPPED
                        else -> com.lagradost.cloudstream3.ui.WatchType.NONE
                      }
                      com.lagradost.cloudstream3.utils.DataStoreHelper.setBookmarkedData(
                        com.lagradost.cloudstream3.utils.DataStoreHelper.BookmarkedData(
                          name = title,
                          url = tmdbId,
                          apiName = "tmdb",
                          posterUrl = posterPath,
                          plot = plot
                        ),
                        watchType
                      )
                    }
                  }
                )
              }
              if (inLibrary) {
                HorizontalDivider()
                DropdownMenuItem(
                  text = { Text("Remove from Library", color = MaterialTheme.colorScheme.error) },
                  onClick = {
                    showLibraryMenu = false
                    scope.launch(Dispatchers.IO) {
                      libraryEntry?.let { libraryDao.deleteLibraryItem(it) }
                      com.lagradost.cloudstream3.utils.DataStoreHelper.removeBookmark(tmdbId)
                    }
                  }
                )
              }
            }
          }
        }

        if (item is MovieItem) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            var isScrapingMovie by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            OutlinedButton(
              onClick = {
                if (item.videoFilePath.isNotBlank() && File(item.videoFilePath).exists()) {
                  isScrapingMovie = true
                  scope.launch(Dispatchers.IO) {
                    val enriched = KodiMediaScraper.scrapeMovie(
                      context = context,
                      videoFile = File(item.videoFilePath),
                      downloadArtworkAndNfo = true
                    )
                    withContext(Dispatchers.Main) {
                      isScrapingMovie = false
                      item.title = enriched.title
                      item.plot = enriched.plot
                      item.userRating = enriched.userRating
                      item.posterPath = enriched.posterPath
                      item.backdropPath = enriched.backdropPath
                      item.genre = enriched.genre
                      item.premiered = enriched.premiered
                      item.director = enriched.director
                      item.actors = enriched.actors
                      onRefreshItem(enriched)
                      Toast.makeText(context, "Scraped metadata & downloaded poster for ${enriched.title}", Toast.LENGTH_SHORT).show()
                    }
                  }
                } else {
                  Toast.makeText(context, "Media file not found locally to scrape", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.height(50.dp),
              shape = RoundedCornerShape(16.dp),
              enabled = !isScrapingMovie,
            ) {
              if (isScrapingMovie) {
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .maxStreamShimmer(shape = CircleShape)
                )
              } else {
                Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scrape Online")
              }
            }
          }
        } else if (!isMovie) {
          val context = LocalContext.current
          val scope = rememberCoroutineScope()
          var selectedSeason by remember { mutableIntStateOf(1) }
          var allLocalEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
          var availableSeasons by remember { mutableStateOf<List<Int>>(listOf(1)) }
          var seasonEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
          var isLoadingEpisodes by remember { mutableStateOf(true) }
          var resolvedShowFolder by remember { mutableStateOf<File?>(null) }
          var resolvedTmdbId by remember {
            mutableStateOf<String?>(
              (item as? TvShowItem)?.tmdbId?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
                ?: (item as? TMDBTvNode)?.id?.toString()
                ?: tmdbId.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
            )
          }

          // Initial scan and season detection
          LaunchedEffect(item, title) {
            isLoadingEpisodes = true
            withContext(Dispatchers.IO) {
              // 1. Resolve local folder for this TV show
              val showPath = (item as? TvShowItem)?.folderPath ?: ""
              val localFolder: File? = if (showPath.isNotBlank() && File(showPath).exists()) {
                File(showPath)
              } else {
                CineFolderMetadataManager.findLocalShowFolder(context, title)
              }
              resolvedShowFolder = localFolder

              val localScanned = if (localFolder != null && localFolder.exists()) {
                NfoScanner.scanTvShowEpisodes(localFolder)
              } else {
                emptyList()
              }
              allLocalEpisodes = localScanned
              val localSeasons = localScanned.map { it.season }.filter { it > 0 }.distinct().sorted()

              // 2. Resolve TMDB ID and online season count
              var activeTmdbId = resolvedTmdbId
              if (activeTmdbId.isNullOrBlank()) {
                val searched = CineOnlineScraper.getOrFetchTvShow(context, title)
                if (searched != null && searched.tmdbId.isNotBlank() && searched.tmdbId.all { it.isDigit() }) {
                  activeTmdbId = searched.tmdbId
                  resolvedTmdbId = activeTmdbId
                }
              }

              val onlineDetails = if (!activeTmdbId.isNullOrBlank()) {
                CineOnlineScraper.fetchTvShowDetails(activeTmdbId, title)
              } else null

              val onlineSeasons = onlineDetails?.seasons?.map { it.season_number }?.filter { it > 0 }?.distinct()?.sorted().orEmpty()

              val combinedSeasons = (localSeasons + onlineSeasons).distinct().sorted()
              val finalSeasons = if (combinedSeasons.isNotEmpty()) combinedSeasons else listOf(1)

              withContext(Dispatchers.Main) {
                availableSeasons = finalSeasons
                selectedSeason = finalSeasons.firstOrNull() ?: 1
              }
            }
            isLoadingEpisodes = false
          }

          // Fetch or filter episodes whenever selectedSeason changes
          LaunchedEffect(item, title, selectedSeason, resolvedTmdbId, allLocalEpisodes) {
            isLoadingEpisodes = true
            val loaded = withContext(Dispatchers.IO) {
              val localForSeason = allLocalEpisodes.filter { it.season == selectedSeason }

              // Fetch online episodes for metadata enrichment or fallback
              val activeTmdbId = resolvedTmdbId ?: (item as? TvShowItem)?.tmdbId ?: (item as? TMDBTvNode)?.id?.toString() ?: tmdbId
              val onlineList = CineOnlineScraper.fetchTvShowEpisodes(
                context,
                activeTmdbId.ifBlank { title },
                selectedSeason,
                title
              )

              if (localForSeason.isNotEmpty()) {
                // Enrich local episodes with online title, plot, and preview still
                localForSeason.map { localEp ->
                  val match = onlineList.firstOrNull { it.episode == localEp.episode }
                  if (match != null) {
                    localEp.copy(
                      title = if (localEp.title.startsWith("Episode ") || localEp.title.equals(title, ignoreCase = true)) {
                        match.title
                      } else localEp.title,
                      plot = if (localEp.plot.isBlank() || localEp.plot == "Local Media File.") match.plot else localEp.plot,
                      stillPath = localEp.stillPath ?: match.stillPath,
                      userRating = if (localEp.userRating > 0.0) localEp.userRating else match.userRating,
                      aired = localEp.aired.ifBlank { match.aired }
                    )
                  } else {
                    localEp
                  }
                }
              } else if (onlineList.isNotEmpty()) {
                onlineList
              } else {
                emptyList()
              }
            }
            seasonEpisodes = loaded
            isLoadingEpisodes = false
          }

          val nextEpisodeToPlay = seasonEpisodes.firstOrNull() ?: allLocalEpisodes.firstOrNull()

          // Top Play Next / Quick Play & Scrape Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                if (nextEpisodeToPlay != null) {
                  scope.launch(Dispatchers.IO) {
                    val playUri = nextEpisodeToPlay.videoFilePath
                    withContext(Dispatchers.Main) {
                      onDismiss()
                      if (playUri.isNotBlank()) {
                        Toast.makeText(context, "Playing $title - ${nextEpisodeToPlay.title}", Toast.LENGTH_SHORT).show()
                        MediaUtils.playFile(playUri, context, "cinehub")
                      }
                    }
                  }
                } else {
                  onDismiss()
                  onPlay()
                }
              },
              modifier = Modifier
                .weight(1f)
                .height(50.dp),
              shape = RoundedCornerShape(16.dp),
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (nextEpisodeToPlay != null) "Play ${nextEpisodeToPlay.title}" else "Play Series",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            var isScrapingTv by remember { mutableStateOf(false) }

            OutlinedButton(
              onClick = {
                val showPath = (item as? TvShowItem)?.folderPath ?: ""
                if (showPath.isNotBlank() && File(showPath).exists()) {
                  isScrapingTv = true
                  scope.launch(Dispatchers.IO) {
                    val enriched = KodiMediaScraper.scrapeTvShow(
                      context = context,
                      showFolder = File(showPath),
                      downloadArtworkAndNfo = true,
                    )
                    val freshEps = NfoScanner.scanTvShowEpisodes(File(showPath))
                    withContext(Dispatchers.Main) {
                      isScrapingTv = false
                      allLocalEpisodes = freshEps
                      val detected = freshEps.map { it.season }.filter { it > 0 }.distinct().sorted()
                      availableSeasons = if (detected.isNotEmpty()) detected else listOf(1)
                      val filtered = freshEps.filter { it.season == selectedSeason }
                      seasonEpisodes = if (filtered.isNotEmpty()) filtered else freshEps
                      onRefreshItem(enriched)
                      Toast.makeText(context, "Scraped series & episode artwork for ${enriched.title}", Toast.LENGTH_SHORT).show()
                    }
                  }
                } else {
                  Toast.makeText(context, "Show folder not found locally to scrape", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.height(50.dp),
              shape = RoundedCornerShape(16.dp),
              enabled = !isScrapingTv,
            ) {
              if (isScrapingTv) {
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .maxStreamShimmer(shape = CircleShape)
                )
              } else {
                Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scrape Online")
              }
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          // Seasons selector
          Text(
            text = "Seasons & Episodes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
          )

          LazyRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(availableSeasons) { s ->
              xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassFilterChip(
                text = "Season $s",
                isSelected = selectedSeason == s,
                onClick = { selectedSeason = s }
              )
            }
          }

          if (isLoadingEpisodes) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              MaxStreamSkeletonListItem()
              MaxStreamSkeletonListItem()
              MaxStreamSkeletonListItem()
            }
          } else if (seasonEpisodes.isEmpty()) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "No episodes available for Season $selectedSeason",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            Column(
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              seasonEpisodes.forEach { ep ->
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      scope.launch(Dispatchers.IO) {
                        val playUri = ep.videoFilePath
                        withContext(Dispatchers.Main) {
                          onDismiss()
                          if (playUri.isNotBlank()) {
                            Toast.makeText(context, "Playing ${ep.title}", Toast.LENGTH_SHORT).show()
                            MediaUtils.playFile(playUri, context, "cinehub")
                          }
                        }
                      }
                    },
                  shape = RoundedCornerShape(14.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  )
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(width = 86.dp, height = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                      contentAlignment = Alignment.Center
                    ) {
                      if (!ep.stillPath.isNullOrBlank()) {
                        AsyncImage(
                          model = ep.stillPath,
                          contentDescription = ep.title,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.fillMaxSize()
                        )
                      }
                      Box(
                        modifier = Modifier
                          .size(28.dp)
                          .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          imageVector = Icons.Default.PlayArrow,
                          contentDescription = "Play Episode",
                          tint = Color.White,
                          modifier = Modifier.size(16.dp)
                        )
                      }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "S${ep.season} • E${ep.episode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                      )
                      Text(
                        text = ep.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      if (ep.plot.isNotBlank() && ep.plot != "Local Media File.") {
                        Text(
                          text = ep.plot,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.outline,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.padding(top = 2.dp)
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        } else if (item is ExtensionMediaDetails || item is LoadResponse) {
          val loadResp = if (item is ExtensionMediaDetails) item.loadResponse else item as LoadResponse
          val provName = if (item is ExtensionMediaDetails) item.providerName.ifBlank { loadResp.apiName } else loadResp.apiName
          val context = LocalContext.current

          // Diagnostic Error Banner when Play fails
          streamFailureReason?.let { failure ->
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
              ),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(
                      imageVector = Icons.Default.WarningAmber,
                      contentDescription = "Error",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(22.dp)
                    )
                    Text(
                      text = failure.title,
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onErrorContainer
                    )
                  }
                  IconButton(
                    onClick = { streamFailureReason = null },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Dismiss",
                      tint = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }

                Text(
                  text = failure.description,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.95f)
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = {
                      streamFailureReason = null
                      onInstantPlayClick()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                      .weight(1f)
                      .height(38.dp)
                  ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontWeight = FontWeight.Bold)
                  }

                  FilledTonalButton(
                    onClick = {
                      val clip = android.content.ClipData.newPlainText(
                        "Stream Error",
                        "Title: ${failure.title}\nReason: ${failure.description}"
                      )
                      (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                      Toast.makeText(context, "Error reason copied", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(38.dp)
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Reason")
                  }
                }
              }
            }
          }

          if (loadResp is TvSeriesLoadResponse) {
            val allEpisodes = loadResp.episodes
            val availableSeasons = remember(allEpisodes) {
              val detected = allEpisodes.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted()
              if (detected.isNotEmpty()) detected else listOf(1)
            }
            var selectedSeason by remember { mutableIntStateOf(availableSeasons.firstOrNull() ?: 1) }

            val seasonEpisodes = remember(allEpisodes, selectedSeason, availableSeasons) {
              if (availableSeasons.size <= 1 && allEpisodes.all { it.season == null || it.season == 0 }) {
                allEpisodes
              } else {
                allEpisodes.filter { (it.season ?: 1) == selectedSeason }
              }
            }

            // TMDB Episode scanning & enrichment for scraper episodes (like E1, E2)
            var tmdbSeasonEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
            var isScanningTmdbEpisodes by remember { mutableStateOf(false) }

            LaunchedEffect(title, selectedSeason, tmdbEnrichedTvShow) {
              val showTmdbId = tmdbEnrichedTvShow?.tmdbId?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
                ?: (item as? TvShowItem)?.tmdbId?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
                ?: ""
              isScanningTmdbEpisodes = true
              withContext(Dispatchers.IO) {
                val eps = CineOnlineScraper.fetchTvShowEpisodes(
                  context = context,
                  tmdbId = showTmdbId.ifBlank { title },
                  seasonNumber = selectedSeason,
                  showTitle = title
                )
                withContext(Dispatchers.Main) {
                  tmdbSeasonEpisodes = eps
                  isScanningTmdbEpisodes = false
                }
              }
            }

            Text(
              text = "Seasons & Episodes (${allEpisodes.size} episodes)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 8.dp)
            )

            if (availableSeasons.size > 1) {
              LazyRow(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                items(availableSeasons) { s ->
                  xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassFilterChip(
                    text = "Season $s",
                    isSelected = selectedSeason == s,
                    onClick = { selectedSeason = s }
                  )
                }
              }
            }

            // Episode Range Pagination for long series (> 25 episodes)
            val rangeChunks = remember(seasonEpisodes) {
              if (seasonEpisodes.size > 25) {
                seasonEpisodes.chunked(25).mapIndexed { idx, list ->
                  val start = idx * 25 + 1
                  val end = start + list.size - 1
                  "$start-$end" to list
                }
              } else emptyList()
            }
            var selectedRangeIndex by remember(seasonEpisodes) { mutableIntStateOf(0) }
            val displayedEpisodes = if (rangeChunks.isNotEmpty()) {
              rangeChunks.getOrNull(selectedRangeIndex)?.second ?: seasonEpisodes
            } else seasonEpisodes

            if (rangeChunks.isNotEmpty()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = "Range:",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color.White.copy(alpha = 0.7f)
                )
                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  itemsIndexed(rangeChunks) { idx, (label, _) ->
                    xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassFilterChip(
                      text = label,
                      isSelected = selectedRangeIndex == idx,
                      onClick = { selectedRangeIndex = idx }
                    )
                  }
                }
              }
            }

            if (displayedEpisodes.isEmpty()) {
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 12.dp)
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "No episodes available for Season $selectedSeason",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            } else {
              var extractingEpisodeData by remember { mutableStateOf<String?>(null) }
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                displayedEpisodes.forEachIndexed { idx, ep ->
                  val isExtracting = extractingEpisodeData == ep.data
                  val epNumber = ep.episode ?: (idx + 1)
                  val matchedTmdb = tmdbSeasonEpisodes.firstOrNull { it.episode == epNumber }

                  val epDisplayTitle = matchedTmdb?.title?.takeIf { it.isNotBlank() && !it.matches(Regex("(?i)^Episode\\s*\\d+$")) }
                    ?: ep.name?.takeIf { it.isNotBlank() && !it.matches(Regex("(?i)^E\\d+$")) }
                    ?: "Episode $epNumber"

                  val epDisplayOverview = matchedTmdb?.plot?.takeIf { it.isNotBlank() && it != "No synopsis available." && it != "No description." && it != "Local Media File." }
                    ?: ep.description

                  val epDisplayThumbnail = matchedTmdb?.stillPath?.takeIf { it.isNotBlank() }
                    ?: ep.posterUrl ?: backdropPath ?: posterPath

                  val epDisplayRating = matchedTmdb?.userRating?.takeIf { it > 0.0 }
                  val epDisplayAired = matchedTmdb?.aired?.takeIf { it.isNotBlank() }

                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable(enabled = extractingEpisodeData == null) {
                        streamFailureReason = null
                        extractingEpisodeData = ep.data
                        extractAndPlayEpisode(
                          context = context,
                          providerName = provName,
                          data = ep.data,
                          episodeTitle = epDisplayTitle,
                          seriesTitle = loadResp.name,
                          scope = scope,
                          onDismiss = {
                            extractingEpisodeData = null
                            onDismiss()
                          },
                          onFailure = { streamFailureReason = it },
                          onLinksLoaded = { links, subs ->
                            extractingEpisodeData = null
                            if (onLinksLoaded != null) {
                              onLinksLoaded(links, subs, com.lagradost.cloudstream3.mapper.writeValueAsString(loadResp))
                            } else {
                              if (links.size == 1) {
                                val link = links.first()
                                val headersMap = buildMap {
                                  if (link.referer.isNotBlank()) put("Referer", link.referer)
                                  putAll(link.headers)
                                }
                                val subtitlesJson = if (subs.isNotEmpty()) com.lagradost.cloudstream3.mapper.writeValueAsString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                                val epJson = com.lagradost.cloudstream3.mapper.writeValueAsString(loadResp)
                                val epTitle = "${loadResp.name} - S${ep.season ?: selectedSeason}E${epNumber} $epDisplayTitle"
                                MediaUtils.playFile(
                                  source = link.url,
                                  context = context,
                                  launchSource = "cinehub",
                                  headers = headersMap,
                                  subtitlesJson = subtitlesJson,
                                  episodeMetadataJson = epJson,
                                  title = epTitle,
                                  posterUrl = epDisplayThumbnail,
                                  overview = epDisplayOverview ?: loadResp.plot,
                                  year = loadResp.year?.toString(),
                                  rating = epDisplayRating ?: loadResp.score?.score,
                                  providerName = provName,
                                  allLinks = links
                                )
                              } else if (links.size > 1) {
                                val epTitle = "${loadResp.name} - S${ep.season ?: selectedSeason}E${epNumber} $epDisplayTitle"
                                val epJson = com.lagradost.cloudstream3.mapper.writeValueAsString(loadResp)
                                detailPendingLinks = links
                                detailPendingSubs = subs
                                detailPendingEpJson = epJson
                                detailPendingTitle = epTitle
                                detailPendingPoster = epDisplayThumbnail
                                detailPendingOverview = epDisplayOverview ?: loadResp.plot
                                detailPendingYear = loadResp.year?.toString()
                                detailPendingRating = epDisplayRating ?: loadResp.score?.score
                                detailPendingProvider = provName
                              } else if (links.isEmpty()) {
                                Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                              }
                            }
                          }
                        )
                      },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                  ) {
                    Row(
                      modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      if (!epDisplayThumbnail.isNullOrBlank()) {
                        Box(
                          modifier = Modifier
                            .size(width = 96.dp, height = 58.dp)
                            .clip(RoundedCornerShape(8.dp))
                        ) {
                          AsyncImage(
                            model = epDisplayThumbnail,
                            contentDescription = epDisplayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                          )
                          Box(
                            modifier = Modifier
                              .fillMaxSize()
                              .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                          ) {
                            Icon(
                              imageVector = Icons.Default.PlayArrow,
                              contentDescription = null,
                              tint = Color.White.copy(alpha = 0.85f),
                              modifier = Modifier.size(24.dp)
                            )
                          }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                      }

                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = epDisplayTitle,
                          fontWeight = FontWeight.Bold,
                          style = MaterialTheme.typography.bodyMedium,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        val epSubtitle = buildString {
                          append("Episode $epNumber")
                          if (ep.season != null && ep.season!! > 0) {
                            append(" • Season ${ep.season}")
                          }
                          if (epDisplayRating != null) {
                            append(" • ★ %.1f".format(epDisplayRating))
                          }
                          if (!epDisplayAired.isNullOrBlank()) {
                            append(" • $epDisplayAired")
                          }
                        }
                        Text(
                          text = epSubtitle,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!epDisplayOverview.isNullOrBlank()) {
                          Text(
                            text = epDisplayOverview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                          )
                        }
                      }

                      if (isExtracting) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(24.dp),
                          strokeWidth = 2.dp
                        )
                      } else {
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                          // Episode Download Button (tap = download top quality, hold = choose quality)
                          IconButton(
                            onClick = {
                              extractingEpisodeData = ep.data
                              extractAndPlayEpisode(
                                context = context,
                                providerName = provName,
                                data = ep.data,
                                episodeTitle = epDisplayTitle,
                                seriesTitle = loadResp.name,
                                scope = scope,
                                onDismiss = { extractingEpisodeData = null },
                                onLinksLoaded = { links, subs ->
                                  extractingEpisodeData = null
                                  val sorted = links.sortedByDescending { it.quality }
                                  if (sorted.isNotEmpty()) {
                                    val topLink = sorted.first()
                                    val epTitle = "${loadResp.name} - S${ep.season ?: selectedSeason}E${epNumber} $epDisplayTitle"
                                    xyz.mpv.rex.cinehub.download.CineDownloadManager.downloadStream(context, epTitle, topLink)
                                  } else {
                                    Toast.makeText(context, "No stream links found to download", Toast.LENGTH_SHORT).show()
                                  }
                                }
                              )
                            }
                          ) {
                            Icon(
                              imageVector = Icons.Outlined.CloudDownload,
                              contentDescription = "Download Episode",
                              tint = MaterialTheme.colorScheme.onSurfaceVariant,
                              modifier = Modifier.size(20.dp)
                            )
                          }

                          // Episode Play Button (tap = play top quality, hold = choose quality)
                          IconButton(
                            onClick = {
                              extractingEpisodeData = ep.data
                              extractAndPlayEpisode(
                                context = context,
                                providerName = provName,
                                data = ep.data,
                                episodeTitle = epDisplayTitle,
                                seriesTitle = loadResp.name,
                                scope = scope,
                                onDismiss = {
                                  extractingEpisodeData = null
                                  onDismiss()
                                },
                                onLinksLoaded = { links, subs ->
                                  extractingEpisodeData = null
                                  if (onLinksLoaded != null) {
                                    onLinksLoaded(links, subs, com.lagradost.cloudstream3.mapper.writeValueAsString(loadResp))
                                  } else {
                                    val sorted = links.sortedByDescending { it.quality }
                                    if (sorted.isNotEmpty()) {
                                      val link = sorted.first()
                                      val headersMap = buildMap {
                                        if (link.referer.isNotBlank()) put("Referer", link.referer)
                                        putAll(link.headers)
                                      }
                                      val subtitlesJson = if (subs.isNotEmpty()) com.lagradost.cloudstream3.mapper.writeValueAsString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                                      val epJson = com.lagradost.cloudstream3.mapper.writeValueAsString(loadResp)
                                      val epTitle = "${loadResp.name} - S${ep.season ?: selectedSeason}E${epNumber} $epDisplayTitle"
                                      MediaUtils.playFile(
                                        source = link.url,
                                        context = context,
                                        launchSource = "cinehub",
                                        headers = headersMap,
                                        subtitlesJson = subtitlesJson,
                                        episodeMetadataJson = epJson,
                                        title = epTitle,
                                        posterUrl = epDisplayThumbnail,
                                        overview = epDisplayOverview ?: loadResp.plot,
                                        year = loadResp.year?.toString(),
                                        rating = epDisplayRating ?: loadResp.score?.score,
                                        providerName = provName,
                                        allLinks = sorted
                                      )
                                    } else {
                                      Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                                    }
                                  }
                                }
                              )
                            }
                          ) {
                            Icon(
                              imageVector = Icons.Default.PlayArrow,
                              contentDescription = "Play Episode",
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(24.dp)
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

    if (detailPendingLinks.isNotEmpty()) {
      QualitySelectorBottomSheet(
        title = detailPendingTitle,
        links = detailPendingLinks,
        subtitles = detailPendingSubs,
        episodeMetadataJson = detailPendingEpJson,
        isDownloadMode = isPendingDownloadMode,
        onDismiss = {
          detailPendingLinks = emptyList()
          isPendingDownloadMode = false
        },
        onLinkSelected = { link ->
          if (isPendingDownloadMode) {
            xyz.mpv.rex.cinehub.download.CineDownloadManager.downloadStream(context, detailPendingTitle, link)
            isPendingDownloadMode = false
            detailPendingLinks = emptyList()
          } else {
            val headersMap = buildMap {
              if (link.referer.isNotBlank()) put("Referer", link.referer)
              putAll(link.headers)
            }
            val subtitlesJson = if (detailPendingSubs.isNotEmpty()) com.lagradost.cloudstream3.mapper.writeValueAsString(detailPendingSubs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
            MediaUtils.playFile(
              source = link.url,
              context = context,
              launchSource = "cinehub",
              headers = headersMap,
              subtitlesJson = subtitlesJson,
              episodeMetadataJson = detailPendingEpJson,
              title = detailPendingTitle,
              posterUrl = detailPendingPoster,
              overview = detailPendingOverview,
              year = detailPendingYear,
              rating = detailPendingRating,
              providerName = detailPendingProvider,
              allLinks = detailPendingLinks
            )
            detailPendingLinks = emptyList()
          }
        }
      )
    }

    // Pinned floating top glass bar with YouTube-like Minimize button & swipe-down pill handle
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // YouTube-like Minimize button
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.intelligentGlassEffect(
          shape = CircleShape,
          backgroundColor = Color(0x99101218),
          borderColor = Color.White.copy(alpha = 0.25f)
        )
      ) {
        Icon(
          imageVector = Icons.Rounded.KeyboardArrowDown,
          contentDescription = "Minimize",
          tint = Color.White,
          modifier = Modifier.size(28.dp)
        )
      }

      // YouTube-like Swipe-to-Minimize Pill Handle with drag gestures
      Box(
        modifier = Modifier
          .pointerInput(Unit) {
            detectVerticalDragGestures(
              onVerticalDrag = { _, dragAmount ->
                if (dragAmount > 0 || dragOffsetY > 0) {
                  dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                }
              },
              onDragEnd = {
                if (dragOffsetY > 160f) {
                  onDismiss()
                } else {
                  dragOffsetY = 0f
                }
              },
              onDragCancel = {
                dragOffsetY = 0f
              }
            )
          }
          .intelligentGlassEffect(
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0x99101218),
            borderColor = Color.White.copy(alpha = 0.20f)
          )
          .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .width(36.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.7f))
        )
      }

      // Spacer to balance layout
      Spacer(modifier = Modifier.size(40.dp))
    }
  }
}

@Composable
fun CineDetailBottomSheet(
  item: Any,
  onDismiss: () -> Unit,
  onPlay: () -> Unit = {},
  onRefreshItem: (Any) -> Unit = {},
  onLinksLoaded: ((List<com.lagradost.cloudstream3.utils.ExtractorLink>, List<com.lagradost.cloudstream3.SubtitleFile>, String?) -> Unit)? = null
) {
  CineDetailView(item, onDismiss, onPlay, onRefreshItem, onLinksLoaded)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KodiScraperBottomSheet(
  isScraping: Boolean,
  progressCurrent: Int,
  progressTotal: Int,
  currentItemName: String,
  resultSummary: String?,
  onStartScrape: (dirOption: String, customPath: String, downloadArtworkAndNfo: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedDirOption by remember { mutableStateOf("default") }
  var customPath by remember {
    mutableStateOf(
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.absolutePath
        ?: "/storage/emulated/0/Movies"
    )
  }
  var downloadArtworkAndNfo by remember { mutableStateOf(true) }

  ModalBottomSheet(
    onDismissRequest = {
      if (!isScraping) onDismiss()
    },
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp)
        .padding(bottom = 32.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier = Modifier
            .size(42.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        Column {
          Text(
            text = "Kodi Media Scraper",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "TMDB v4/v3 & TVMaze Online Library Engine",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (isScraping) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ),
          shape = RoundedCornerShape(16.dp),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Scraping in progress…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = if (progressTotal > 0) "$progressCurrent / $progressTotal" else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val progressFraction = if (progressTotal > 0) progressCurrent.toFloat() / progressTotal.toFloat() else 0f
            LinearProgressIndicator(
              progress = { progressFraction.coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = currentItemName.ifBlank { "Querying TMDB and downloading posters…" },
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "Matching titles, season episodes, downloading high-res posters, and writing Kodi .nfo files.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
        }
      } else if (resultSummary != null) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
          ),
          shape = RoundedCornerShape(16.dp),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Scraping Complete",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = resultSummary,
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = onDismiss,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text(text = "View Scraped Library", fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        Text(
          text = "Select Media Location:",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = selectedDirOption == "default",
            onClick = { selectedDirOption = "default" },
            label = { Text("Default (Movies/TV)") },
            leadingIcon = { Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) },
          )
          FilterChip(
            selected = selectedDirOption == "downloads",
            onClick = { selectedDirOption = "downloads" },
            label = { Text("Downloads") },
            leadingIcon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)) },
          )
          FilterChip(
            selected = selectedDirOption == "custom",
            onClick = { selectedDirOption = "custom" },
            label = { Text("Custom") },
            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
          )
        }

        if (selectedDirOption == "custom") {
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = customPath,
            onValueChange = { customPath = it },
            label = { Text("Custom Directory Path") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { downloadArtworkAndNfo = !downloadArtworkAndNfo },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = downloadArtworkAndNfo,
            onCheckedChange = { downloadArtworkAndNfo = it },
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Download Posters & Save Kodi .nfo",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Saves posters, backdrops, and XML metadata locally for offline access.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = {
            onStartScrape(selectedDirOption, customPath, downloadArtworkAndNfo)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Start Kodi Online Scraper",
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelectorBottomSheet(
  title: String,
  links: List<com.lagradost.cloudstream3.utils.ExtractorLink>,
  subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
  episodeMetadataJson: String?,
  isDownloadMode: Boolean = false,
  onDismiss: () -> Unit,
  onLinkSelected: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit,
) {
  val context = LocalContext.current
  val sortedLinks = remember(links) { links.sortedByDescending { it.quality } }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0D111A) else MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .padding(bottom = 32.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = if (isDownloadMode) "Select Download Quality" else "Select Stream Quality",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = if (isDownloadMode) "Choose source server & resolution to download" else "Tap quality or server to play instantly",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (sortedLinks.isNotEmpty()) {
        val topLink = sortedLinks.first()
        val topQualityText = remember(topLink) {
          when {
            topLink.quality >= 2160 -> "4K UHD"
            topLink.quality >= 1080 -> "1080p FHD"
            topLink.quality >= 720 -> "720p HD"
            topLink.quality > 0 -> "${topLink.quality}p"
            else -> "Auto (Highest)"
          }
        }
        val topServerName = topLink.source.ifBlank { "Best Available" }

        // Hero Auto-Select Card
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color.Transparent,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  Color(0xFF6366F1).copy(alpha = 0.85f),
                  Color(0xFFA855F7).copy(alpha = 0.85f)
                )
              )
            )
            .clickable { onLinkSelected(topLink) }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color.White.copy(alpha = 0.25f)
                ) {
                  Text(
                    text = "AUTO SELECT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color.Black.copy(alpha = 0.3f)
                ) {
                  Text(
                    text = topQualityText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Auto-Play Highest Quality",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "Server: $topServerName • ${if (topLink.isM3u8) "HLS Stream" else "Direct Video"}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
              )
            }
            IconButton(
              onClick = { onLinkSelected(topLink) },
              modifier = Modifier
                .size(44.dp)
                .background(Color.White, CircleShape)
            ) {
              Icon(
                imageVector = if (isDownloadMode) Icons.Outlined.CloudDownload else Icons.Rounded.PlayArrow,
                contentDescription = "Auto Select",
                tint = Color(0xFF6366F1)
              )
            }
          }
        }
      }
      
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(sortedLinks) { link ->
          val cleanLabel = remember(link) {
            xyz.mpv.rex.cinehub.utils.StreamLinkFormatter.formatQualityLanguage(link)
          }
          val qualityTag = remember(link) {
            when {
              link.quality >= 2160 -> "4K"
              link.quality >= 1080 -> "1080p"
              link.quality >= 720 -> "720p"
              link.quality >= 480 -> "480p"
              link.quality >= 360 -> "360p"
              else -> "HD"
            }
          }
          val serverName = link.source.ifBlank {
            try {
              java.net.URI(link.url).host?.removePrefix("www.") ?: "Direct Server"
            } catch (e: Exception) {
              "Direct Server"
            }
          }

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .intelligentGlassEffect(
                shape = RoundedCornerShape(14.dp),
                backgroundColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                borderColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
              )
              .clickable { onLinkSelected(link) }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                  ) {
                    Text(
                      text = qualityTag,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                  Text(
                    text = cleanLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Server: $serverName • ${if (link.isM3u8) "Direct Stream (HLS)" else "Direct Video"}",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                if (link.isM3u8) {
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                  ) {
                    Text(
                      text = "HLS",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
                IconButton(
                  onClick = { onLinkSelected(link) },
                  modifier = Modifier.size(36.dp)
                ) {
                  Icon(
                    imageVector = if (isDownloadMode) Icons.Outlined.CloudDownload else Icons.Rounded.PlayArrow,
                    contentDescription = if (isDownloadMode) "Download" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun createCuratedMovie(
  title: String,
  genre: String,
  year: String,
  posterUrl: String,
  backdropUrl: String,
  rating: Double
): MovieItem {
  return MovieItem(
    videoFilePath = posterUrl,
    title = title,
    originalTitle = title,
    userRating = rating,
    plot = "$title ($year)",
    mpaa = "PG-13",
    genre = genre,
    director = "",
    premiered = year,
    posterPath = posterUrl,
    backdropPath = backdropUrl
  )
}

private val defaultCuratedHeroMovies = listOf(
  CarouselMovie(
    id = "curated_1",
    title = "Dune: Part Two",
    subtitle = "Sci-Fi • Adventure • 2024",
    posterUrl = "https://image.tmdb.org/t/p/w780/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
    backdropUrl = "https://image.tmdb.org/t/p/w1280/xOMo8BRK7PfcJv9JCnx7s520b4.jpg",
    rating = 8.6,
    originalItem = createCuratedMovie("Dune: Part Two", "Sci-Fi, Adventure", "2024", "https://image.tmdb.org/t/p/w780/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", "https://image.tmdb.org/t/p/w1280/xOMo8BRK7PfcJv9JCnx7s520b4.jpg", 8.6)
  ),
  CarouselMovie(
    id = "curated_2",
    title = "Oppenheimer",
    subtitle = "Biography • Drama • 2023",
    posterUrl = "https://image.tmdb.org/t/p/w780/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
    backdropUrl = "https://image.tmdb.org/t/p/w1280/rLb2cwF3Pazuxaj0sRXQ037tGI1.jpg",
    rating = 8.9,
    originalItem = createCuratedMovie("Oppenheimer", "Biography, Drama", "2023", "https://image.tmdb.org/t/p/w780/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg", "https://image.tmdb.org/t/p/w1280/rLb2cwF3Pazuxaj0sRXQ037tGI1.jpg", 8.9)
  ),
  CarouselMovie(
    id = "curated_3",
    title = "Interstellar",
    subtitle = "Sci-Fi • Drama • 2014",
    posterUrl = "https://image.tmdb.org/t/p/w780/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
    backdropUrl = "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
    rating = 8.7,
    originalItem = createCuratedMovie("Interstellar", "Sci-Fi, Drama", "2014", "https://image.tmdb.org/t/p/w780/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg", 8.7)
  ),
  CarouselMovie(
    id = "curated_4",
    title = "Across the Spider-Verse",
    subtitle = "Animation • Action • 2023",
    posterUrl = "https://image.tmdb.org/t/p/w780/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
    backdropUrl = "https://image.tmdb.org/t/p/w1280/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
    rating = 8.8,
    originalItem = createCuratedMovie("Across the Spider-Verse", "Animation, Action", "2023", "https://image.tmdb.org/t/p/w780/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg", "https://image.tmdb.org/t/p/w1280/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg", 8.8)
  ),
  CarouselMovie(
    id = "curated_5",
    title = "Deadpool & Wolverine",
    subtitle = "Action • Comedy • 2024",
    posterUrl = "https://image.tmdb.org/t/p/w780/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
    backdropUrl = "https://image.tmdb.org/t/p/w1280/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
    rating = 8.0,
    originalItem = createCuratedMovie("Deadpool & Wolverine", "Action, Comedy", "2024", "https://image.tmdb.org/t/p/w780/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", "https://image.tmdb.org/t/p/w1280/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg", 8.0)
  )
)
