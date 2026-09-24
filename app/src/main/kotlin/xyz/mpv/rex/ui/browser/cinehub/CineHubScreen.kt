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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import xyz.mpv.rex.auth.AuthManager
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.border
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonBanner
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonListItem
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonRow
import xyz.mpv.rex.ui.theme.maxstream.maxStreamShimmer
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.profile.ProfileScreen
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

fun extractAndPlayMovie(
  context: android.content.Context,
  providerName: String,
  dataUrl: String,
  movieTitle: String,
  scope: kotlinx.coroutines.CoroutineScope,
  onDismiss: () -> Unit = {},
  onLinksLoaded: (List<com.lagradost.cloudstream3.utils.ExtractorLink>, List<com.lagradost.cloudstream3.SubtitleFile>) -> Unit,
) {
  scope.launch(Dispatchers.IO) {
    val api = APIHolder.apis.firstOrNull { it.name.equals(providerName, true) }
      ?: APIHolder.getApi(providerName)
    val links = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
    val subtitles = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()

    if (api != null) {
      try {
        api.loadLinks(dataUrl, false, subtitleCallback = { sub ->
          synchronized(subtitles) { subtitles.add(sub) }
        }) { link ->
          synchronized(links) { links.add(link) }
        }
      } catch (e: Exception) {
        android.util.Log.e("CineHub", "Error extracting movie links: ${e.message}")
      }
    }

    if (links.isEmpty()) {
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
    }

    withContext(Dispatchers.Main) {
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
  onLinksLoaded: (List<com.lagradost.cloudstream3.utils.ExtractorLink>, List<com.lagradost.cloudstream3.SubtitleFile>) -> Unit,
) {
  scope.launch(Dispatchers.IO) {
    val api = APIHolder.apis.firstOrNull { it.name.equals(providerName, true) }
      ?: APIHolder.getApi(providerName)
    val links = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
    val subtitles = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()

    if (api != null) {
      try {
        api.loadLinks(data, false, subtitleCallback = { sub ->
          synchronized(subtitles) { subtitles.add(sub) }
        }) { link ->
          synchronized(links) { links.add(link) }
        }
      } catch (e: Exception) {
        android.util.Log.e("CineHub", "Error extracting episode links: ${e.message}")
      }
    }

    if (links.isEmpty()) {
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
    }

    withContext(Dispatchers.Main) {
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

    val activeProvidersList by providerRegistry.activeProviders.collectAsState()
    val registeredProvidersList by providerRegistry.registeredProviders.collectAsState()
    val installedExtensionsList by extensionManager.getAllInstalledExtensions().collectAsState(initial = emptyList())
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      Log.i("CineHubScreen", "INSTANCE_IDENTITY: CineHubScreen composed. identityHashCode=${System.identityHashCode(this)}, ProviderRegistry.identityHashCode=${System.identityHashCode(providerRegistry)}, ExtensionManager.identityHashCode=${System.identityHashCode(extensionManager)}, APIHolder.identityHashCode=${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
    }

    val enableLocalMovies by browserPreferences.enableLocalMovies.collectAsState()
    val enableLocalTvShows by browserPreferences.enableLocalTvShows.collectAsState()
    val enableMetadataScraping by browserPreferences.enableMetadataScraping.collectAsState()
    val enableArtworkDownloads by browserPreferences.enableArtworkDownloads.collectAsState()

    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

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
    var continueWatchingItems by remember { mutableStateOf<List<xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem>>(emptyList()) }

    var extensionSearchResults by remember { mutableStateOf<List<xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var providerHomeRows by remember { mutableStateOf<List<xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList>>(emptyList()) }
    var seeAllSheetData by remember { mutableStateOf<Pair<String, List<xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem>>?>(null) }

    var selectedDetailItem by remember { mutableStateOf<Any?>(null) }

    var pendingStreamTitle by remember { mutableStateOf("") }
    var pendingStreamLinks by remember { mutableStateOf<List<com.lagradost.cloudstream3.utils.ExtractorLink>>(emptyList()) }
    var pendingSubtitles by remember { mutableStateOf<List<com.lagradost.cloudstream3.SubtitleFile>>(emptyList()) }
    var pendingEpisodeMetadataJson by remember { mutableStateOf<String?>(null) }
    var showProviderSelector by remember { mutableStateOf(false) }

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
            val resolvedFanart = localMovieMatch?.backdropPath ?: localMovieMatch?.posterPath
              ?: localTvMatch?.backdropPath ?: localTvMatch?.posterPath ?: path

            cwMap[path] = xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem(
              id = path,
              title = title,
              episodeInfo = epInfo,
              landscapeImageUrl = resolvedFanart,
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
              val resolvedFanart = localMovieMatch?.backdropPath ?: localMovieMatch?.posterPath ?: titleOrPath

              cwMap[titleOrPath] = xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem(
                id = titleOrPath,
                title = titleOrPath,
                episodeInfo = epInfo,
                landscapeImageUrl = resolvedFanart,
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

    LaunchedEffect(activeProvidersList) {
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
            result.add(
              CarouselMovie(
                id = item.id,
                title = item.title,
                subtitle = subtitle,
                posterUrl = item.posterUrl,
                backdropUrl = highResFanart ?: item.posterUrl,
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
      (baseCategories + dynamicCategories + "Library").distinct()
    }

    val authManager = koinInject<AuthManager>()
    val authUser by authManager.firebaseUser.collectAsState()
    val userProfile by authManager.userProfile.collectAsState()
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
            IconButton(
              onClick = {
                backstack.add(xyz.mpv.rex.ui.preferences.ExtensionPreferencesScreenRoute)
              },
              modifier = Modifier.testTag("cinehub_extensions_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = "Extensions",
                tint = MaterialTheme.colorScheme.secondary,
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
                query = searchQuery,
                onQueryChange = { query ->
                  searchQuery = query
                  if (query.length >= 2) {
                    isSearchActive = true
                    isSearchingOnline = true
                    scope.launch(Dispatchers.IO) {
                      val activeProviders = providerRegistry.getEnabledProviders()
                      android.util.Log.i("CineHubSearch", "Searching across ${activeProviders.size} enabled providers for: $query")
                      val extDeferreds = activeProviders.map { provider ->
                        async {
                          try {
                            provider.search(query)
                          } catch (t: Throwable) {
                            android.util.Log.e("CineHubSearch", "Error in provider ${provider.name} search: ${t.message}", t)
                            emptyList()
                          }
                        }
                      }
                      val extRes = extDeferreds.awaitAll().flatten()
                      withContext(Dispatchers.Main) {
                        extensionSearchResults = extRes
                        isSearchingOnline = false
                      }
                    }
                  } else {
                    if (query.isEmpty()) {
                      extensionSearchResults = emptyList()
                    }
                  }
                },
                isExpanded = isSearchActive,
                onExpandedChange = { expanded ->
                  isSearchActive = expanded
                  if (!expanded) {
                    searchQuery = ""
                    extensionSearchResults = emptyList()
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp)
              )
            }

            // If Search is Active, display Search Results
            if (isSearchActive) {
              item {
                Text(
                  text = "Search Results",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
              }

              if (isSearchingOnline) {
                item {
                  MaxStreamSkeletonRow(
                    itemCount = 4,
                    modifier = Modifier.padding(vertical = 12.dp)
                  )
                }
              } else if (extensionSearchResults.isEmpty()) {
                item {
                  Text(
                    text = "No results found for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                  )
                }
              } else {
                item {
                  Text(
                    text = "Extension Results",
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
                    FilterChip(
                      selected = selectedCategory == category,
                      onClick = { selectedCategory = category },
                      label = { Text(category) },
                      shape = RoundedCornerShape(16.dp),
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
                        selectedLabelColor = Color.White
                      )
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
              if (selectedCategory != "Library") {
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
                          imageVector = Icons.Outlined.Extension,
                          contentDescription = null,
                          modifier = Modifier.size(64.dp),
                          tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                          text = "No Extensions Installed",
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = "Install extensions from repositories to browse movies, TV series, anime, and media streams.",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.outline,
                          textAlign = TextAlign.Center,
                        )
                        Button(
                          onClick = {
                            backstack.add(xyz.mpv.rex.ui.preferences.ExtensionPreferencesScreenRoute)
                          },
                          shape = RoundedCornerShape(12.dp),
                        ) {
                          Icon(imageVector = Icons.Outlined.Extension, contentDescription = null)
                          Spacer(modifier = Modifier.width(8.dp))
                          Text("Manage Extensions")
                        }
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
                                  val fetched = CineOnlineScraper.getOrFetchMovie(context, item.title, item.url)
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
                }
              }
            }
          }
        }

        // Navigate to full-screen movie/tv show details
        LaunchedEffect(selectedDetailItem) {
          val item = selectedDetailItem
          if (item != null) {
            selectedDetailItem = null
            CineDetailStateHolder.open(backstack, item)
          }
        }

        // See All Provider Row Sheet
        seeAllSheetData?.let { (title, items) ->
          xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamSeeAllSheet(
            title = title,
            items = items,
            onDismissRequest = { seeAllSheetData = null },
            onItemClick = { item ->
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

        if (showProviderSelector) {
          ProviderSelectorSheet(
            providerRegistry = providerRegistry,
            onDismissRequest = { showProviderSelector = false },
            onProvidersChanged = { loadMedia() }
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
                Toast.makeText(context, "Playing from ${provider?.name ?: "Extension"}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "No stream links found from extension", Toast.LENGTH_SHORT).show()
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

@OptIn(ExperimentalMaterial3Api::class)
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
  
  val tmdbId = when (item) {
    is MovieItem -> item.tmdbId.takeIf { it.isNotBlank() } ?: item.title
    is TvShowItem -> item.tmdbId.takeIf { it.isNotBlank() } ?: item.title
    is ExtensionMediaDetails -> item.loadResponse.url
    is LoadResponse -> item.url
    is SearchResponse -> item.url
    is CineHubSearchItem -> item.url
    is CineHubMediaDetails -> item.id
    else -> ""
  }
  val isMovie = when (item) {
    is MovieItem -> true
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
  val rawPlot = when (item) {
    is MovieItem -> item.plot
    is TvShowItem -> item.plot
    is ExtensionMediaDetails -> item.loadResponse.plot ?: ""
    is LoadResponse -> item.plot ?: ""
    is CineHubMediaDetails -> item.overview ?: ""
    else -> ""
  }
  val rawPosterPath = when (item) {
    is MovieItem -> item.posterPath
    is TvShowItem -> item.posterPath
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
    is ExtensionMediaDetails -> item.loadResponse.backgroundPosterUrl ?: item.loadResponse.posterUrl
    is LoadResponse -> item.backgroundPosterUrl ?: item.posterUrl
    is CineHubMediaDetails -> item.backdropUrl ?: item.posterUrl
    is SearchResponse -> item.posterUrl
    else -> null
  }
  val rawRating = when (item) {
    is MovieItem -> item.userRating
    is TvShowItem -> item.userRating
    is ExtensionMediaDetails -> item.loadResponse.score?.score ?: 0.0
    is LoadResponse -> item.score?.score ?: 0.0
    is SearchResponse -> item.score?.score ?: 0.0
    else -> 0.0
  }
  val rawYear = when (item) {
    is MovieItem -> item.premiered.take(4)
    is TvShowItem -> item.premiered.take(4)
    is ExtensionMediaDetails -> item.loadResponse.year?.toString() ?: ""
    is LoadResponse -> item.year?.toString() ?: ""
    is CineHubMediaDetails -> item.year?.toString() ?: ""
    else -> ""
  }
  val rawGenre = when (item) {
    is MovieItem -> item.genre
    is TvShowItem -> item.genre
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

  val onInstantPlayClick: () -> Unit = {
    when (item) {
      is MovieItem -> {
        if (item.videoFilePath.startsWith("ext_stream:")) {
          val raw = item.videoFilePath.removePrefix("ext_stream:")
          val providerId = raw.substringBefore("::")
          val dataUrl = raw.substringAfter("::")
          isInstantPlayExtracting = true
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
              isInstantPlayExtracting = false
              if (stream != null && stream.url.isNotBlank()) {
                onDismiss()
                Toast.makeText(context, "Playing from ${provider?.name ?: "Extension"}", Toast.LENGTH_SHORT).show()
                MediaUtils.playFile(
                  source = stream.url,
                  context = context,
                  launchSource = "cinehub",
                  headers = stream.headers,
                  title = title,
                  posterUrl = posterPath,
                  overview = plot,
                  year = year,
                  rating = rating.takeIf { it > 0.0 },
                  providerName = provider?.name,
                  allLinks = extractorLinks
                )
              } else {
                Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
              }
            }
          }
        } else if (item.videoFilePath.isNotBlank()) {
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
        } else {
          onDismiss()
          onPlay()
        }
      }
      is TvShowItem -> {
        isInstantPlayExtracting = true
        scope.launch(Dispatchers.IO) {
          val episodes = if (item.folderPath.isNotBlank() && File(item.folderPath).exists()) {
            NfoScanner.scanTvShowEpisodes(File(item.folderPath))
          } else {
            CineOnlineScraper.fetchTvShowEpisodes(context, item.tmdbId.ifBlank { item.title }, 1, item.title)
          }
          val firstEp = episodes.firstOrNull()
          withContext(Dispatchers.Main) {
            isInstantPlayExtracting = false
            if (firstEp != null && firstEp.videoFilePath.isNotBlank()) {
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
            isInstantPlayExtracting = true
            extractAndPlayMovie(
              context = context,
              providerName = item.providerName.ifBlank { resp.apiName },
              dataUrl = resp.dataUrl.ifBlank { resp.url },
              movieTitle = resp.name,
              scope = scope,
              onDismiss = onDismiss,
              onLinksLoaded = { links, subs ->
                isInstantPlayExtracting = false
                if (onLinksLoaded != null) {
                  onLinksLoaded(links, subs, null)
                } else if (links.isNotEmpty()) {
                  val link = links.first()
                  val headersMap = buildMap {
                    if (link.referer.isNotBlank()) put("Referer", link.referer)
                    putAll(link.headers)
                  }
                  val subtitlesJson = if (subs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                  MediaUtils.playFile(
                    source = link.url,
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
                    allLinks = links
                  )
                }
              }
            )
          }
          is TvSeriesLoadResponse -> {
            val firstEp = resp.episodes.firstOrNull()
            if (firstEp != null) {
              isInstantPlayExtracting = true
              extractAndPlayEpisode(
                context = context,
                providerName = item.providerName.ifBlank { resp.apiName },
                data = firstEp.data,
                episodeTitle = firstEp.name,
                seriesTitle = resp.name,
                scope = scope,
                onDismiss = onDismiss,
                onLinksLoaded = { links, subs ->
                  isInstantPlayExtracting = false
                  val epMetadataJson = kotlinx.serialization.json.Json.encodeToString(
                    mapOf(
                      "seriesTitle" to resp.name,
                      "episodeTitle" to (firstEp.name ?: "Episode 1"),
                      "season" to (firstEp.season ?: 1).toString(),
                      "episode" to (firstEp.episode ?: 1).toString()
                    )
                  )
                  if (onLinksLoaded != null) {
                    onLinksLoaded(links, subs, epMetadataJson)
                  } else if (links.isNotEmpty()) {
                    val link = links.first()
                    val headersMap = buildMap {
                      if (link.referer.isNotBlank()) put("Referer", link.referer)
                      putAll(link.headers)
                    }
                    val subtitlesJson = if (subs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                    MediaUtils.playFile(
                      source = link.url,
                      context = context,
                      launchSource = "cinehub",
                      headers = headersMap,
                      subtitlesJson = subtitlesJson,
                      episodeMetadataJson = epMetadataJson,
                      title = "$title - S${firstEp.season ?: 1}E${firstEp.episode ?: 1} ${firstEp.name ?: "Episode 1"}",
                      posterUrl = posterPath,
                      overview = plot,
                      year = year,
                      rating = rating.takeIf { it > 0.0 },
                      providerName = item.providerName.ifBlank { resp.apiName },
                      allLinks = links
                    )
                  }
                }
              )
            } else {
              Toast.makeText(context, "No episodes available", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
      is MovieLoadResponse -> {
        isInstantPlayExtracting = true
        extractAndPlayMovie(
          context = context,
          providerName = item.apiName,
          dataUrl = item.dataUrl.ifBlank { item.url },
          movieTitle = item.name,
          scope = scope,
          onDismiss = onDismiss,
          onLinksLoaded = { links, subs ->
            isInstantPlayExtracting = false
            if (onLinksLoaded != null) {
              onLinksLoaded(links, subs, null)
            } else if (links.isNotEmpty()) {
              val link = links.first()
              val headersMap = buildMap {
                if (link.referer.isNotBlank()) put("Referer", link.referer)
                putAll(link.headers)
              }
              val subtitlesJson = if (subs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
              MediaUtils.playFile(
                source = link.url,
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
                allLinks = links
              )
            }
          }
        )
      }
      else -> {
        onDismiss()
        onPlay()
      }
    }
  }

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
      .background(MaterialTheme.colorScheme.background)
  ) {
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
            .clickable { onInstantPlayClick() }
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

        // Bottom instant play pill banner
        Row(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(14.dp)
            .intelligentGlassEffect(
              shape = RoundedCornerShape(8.dp),
              backgroundColor = Color.Black.copy(alpha = 0.65f),
              borderColor = Color.White.copy(alpha = 0.20f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (isInstantPlayExtracting) Color(0xFFFFB800) else Color(0xFF00E676))
          )
          Text(
            text = if (isInstantPlayExtracting) "Resolving Stream Link..." else "Instant Play • Tap to Watch",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
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
                0 to "Watchlist",
                1 to "Watching",
                2 to "Completed",
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
                    }
                  }
                )
              }
              if (inLibrary) {
                Divider()
                DropdownMenuItem(
                  text = { Text("Remove from Library", color = MaterialTheme.colorScheme.error) },
                  onClick = {
                    showLibraryMenu = false
                    scope.launch(Dispatchers.IO) {
                      libraryEntry?.let { libraryDao.deleteLibraryItem(it) }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                onDismiss()
                onPlay()
              },
              modifier = Modifier
                .weight(1f)
                .height(50.dp),
              shape = RoundedCornerShape(16.dp),
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = "Play Movie", fontWeight = FontWeight.Bold)
            }

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
        } else if (item is TvShowItem) {
          val context = LocalContext.current
          val scope = rememberCoroutineScope()
          var selectedSeason by remember { mutableIntStateOf(1) }
          var allLocalEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
          var availableSeasons by remember { mutableStateOf<List<Int>>(listOf(1)) }
          var seasonEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
          var isLoadingEpisodes by remember { mutableStateOf(true) }
          var resolvedShowFolder by remember { mutableStateOf<File?>(null) }
          var resolvedTmdbId by remember { mutableStateOf<String?>(item.tmdbId.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }) }

          // Initial scan and season detection
          LaunchedEffect(item) {
            isLoadingEpisodes = true
            withContext(Dispatchers.IO) {
              // 1. Resolve local folder for this TV show
              val localFolder: File? = if (item.folderPath.isNotBlank() && File(item.folderPath).exists()) {
                File(item.folderPath)
              } else {
                CineFolderMetadataManager.findLocalShowFolder(context, item.title)
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
              var tmdbId = resolvedTmdbId
              if (tmdbId.isNullOrBlank()) {
                val searched = CineOnlineScraper.getOrFetchTvShow(context, item.title)
                if (searched != null && searched.tmdbId.isNotBlank() && searched.tmdbId.all { it.isDigit() }) {
                  tmdbId = searched.tmdbId
                  resolvedTmdbId = tmdbId
                }
              }

              val onlineDetails = if (!tmdbId.isNullOrBlank()) {
                CineOnlineScraper.fetchTvShowDetails(tmdbId, item.title)
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
          LaunchedEffect(item, selectedSeason, resolvedTmdbId, allLocalEpisodes) {
            isLoadingEpisodes = true
            val loaded = withContext(Dispatchers.IO) {
              val localForSeason = allLocalEpisodes.filter { it.season == selectedSeason }

              // Fetch online episodes for metadata enrichment or fallback
              val tmdbId = resolvedTmdbId ?: item.tmdbId
              val onlineList = CineOnlineScraper.fetchTvShowEpisodes(
                context,
                tmdbId.ifBlank { item.title },
                selectedSeason,
                item.title
              )

              if (localForSeason.isNotEmpty()) {
                // Enrich local episodes with online title, plot, and preview still
                localForSeason.map { localEp ->
                  val match = onlineList.firstOrNull { it.episode == localEp.episode }
                  if (match != null) {
                    localEp.copy(
                      title = if (localEp.title.startsWith("Episode ") || localEp.title.equals(item.title, ignoreCase = true)) {
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
                        Toast.makeText(context, "Playing ${item.title} - ${nextEpisodeToPlay.title}", Toast.LENGTH_SHORT).show()
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
                if (item.folderPath.isNotBlank() && File(item.folderPath).exists()) {
                  isScrapingTv = true
                  scope.launch(Dispatchers.IO) {
                    val enriched = KodiMediaScraper.scrapeTvShow(
                      context = context,
                      showFolder = File(item.folderPath),
                      downloadArtworkAndNfo = true,
                    )
                    val freshEps = NfoScanner.scanTvShowEpisodes(File(item.folderPath))
                    withContext(Dispatchers.Main) {
                      isScrapingTv = false
                      item.title = enriched.title
                      item.plot = enriched.plot
                      item.posterPath = enriched.posterPath
                      item.backdropPath = enriched.backdropPath
                      item.userRating = enriched.userRating
                      item.genre = enriched.genre
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
              FilterChip(
                selected = selectedSeason == s,
                onClick = { selectedSeason = s },
                label = { Text("Season $s", fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
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

          if (loadResp is MovieLoadResponse) {
            var isExtractingMovie by remember { mutableStateOf(false) }
            Button(
              onClick = {
                isExtractingMovie = true
                extractAndPlayMovie(
                  context = context,
                  providerName = provName,
                  dataUrl = loadResp.dataUrl.ifBlank { loadResp.url },
                  movieTitle = loadResp.name,
                  scope = scope,
                  onDismiss = onDismiss,
                  onLinksLoaded = { links, subs ->
                    isExtractingMovie = false
                    if (onLinksLoaded != null) {
                      onLinksLoaded(links, subs, null)
                    } else {
                      if (links.size == 1) {
                        val link = links.first()
                        val headersMap = buildMap {
                          if (link.referer.isNotBlank()) put("Referer", link.referer)
                          putAll(link.headers)
                        }
                        val subtitlesJson = if (subs.isNotEmpty()) kotlinx.serialization.json.Json.encodeToString(subs.map { mapOf("lang" to it.lang, "url" to it.url) }) else null
                        MediaUtils.playFile(
                          source = link.url,
                          context = context,
                          launchSource = "cinehub",
                          headers = headersMap,
                          subtitlesJson = subtitlesJson,
                          episodeMetadataJson = null,
                          title = loadResp.name,
                          posterUrl = loadResp.posterUrl,
                          overview = loadResp.plot,
                          year = loadResp.year?.toString(),
                          rating = loadResp.score?.score,
                          providerName = provName,
                          allLinks = links
                        )
                      } else if (links.size > 1) {
                        detailPendingLinks = links
                        detailPendingSubs = subs
                        detailPendingEpJson = null
                        detailPendingTitle = loadResp.name
                        detailPendingPoster = loadResp.posterUrl
                        detailPendingOverview = loadResp.plot
                        detailPendingYear = loadResp.year?.toString()
                        detailPendingRating = loadResp.score?.score
                        detailPendingProvider = provName
                      } else if (links.isEmpty()) {
                        Toast.makeText(context, "No stream links found", Toast.LENGTH_SHORT).show()
                      }
                    }
                  }
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
              shape = RoundedCornerShape(16.dp),
              enabled = !isExtractingMovie
            ) {
              if (isExtractingMovie) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading Stream...", fontWeight = FontWeight.Bold)
              } else {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play Movie", fontWeight = FontWeight.Bold)
              }
            }
          } else if (loadResp is TvSeriesLoadResponse) {
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
                  .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                items(availableSeasons) { s ->
                  FilterChip(
                    selected = selectedSeason == s,
                    onClick = { selectedSeason = s },
                    label = { Text("Season $s", fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primary,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                  )
                }
              }
            }

            if (seasonEpisodes.isEmpty()) {
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
                seasonEpisodes.forEachIndexed { idx, ep ->
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
                          }
                        ) {
                          Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Episode",
                            tint = MaterialTheme.colorScheme.primary,
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

    if (detailPendingLinks.isNotEmpty()) {
      QualitySelectorBottomSheet(
        title = detailPendingTitle,
        links = detailPendingLinks,
        subtitles = detailPendingSubs,
        episodeMetadataJson = detailPendingEpJson,
        onDismiss = { detailPendingLinks = emptyList() },
        onLinkSelected = { link ->
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
  onDismiss: () -> Unit,
  onLinkSelected: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit,
) {
  val sortedLinks = links.sortedByDescending { it.quality }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp)
        .padding(bottom = 32.dp),
    ) {
      Text(
        text = "Select Stream Quality",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp)
      )
      
      LazyColumn {
        items(sortedLinks) { link ->
          val cleanLabel = remember(link) {
            xyz.mpv.rex.cinehub.utils.StreamLinkFormatter.formatQualityLanguage(link)
          }
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
              .clip(RoundedCornerShape(14.dp))
              .intelligentGlassEffect(
                shape = RoundedCornerShape(14.dp),
                backgroundColor = Color.White.copy(alpha = 0.08f),
                borderColor = Color.White.copy(alpha = 0.15f)
              )
              .clickable { onLinkSelected(link) }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = cleanLabel,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White
                )
                Text(
                  text = if (link.isM3u8) "Fast Direct Stream (HLS)" else "High Speed Direct Link",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.7f)
                )
              }
              if (link.isM3u8) {
                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                  Text("M3U8", color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectorSheet(
    providerRegistry: xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry,
    onDismissRequest: () -> Unit,
    onProvidersChanged: () -> Unit
) {
    val registeredProviders by providerRegistry.registeredProviders.collectAsState()
    val isDark = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.isDark
    val containerBg = if (isDark) Color(0xF210111A) else MaterialTheme.colorScheme.surface
    val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isDark) Color.White.copy(alpha = 0.70f) else MaterialTheme.colorScheme.onSurfaceVariant
    val itemBg = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val itemBorder = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = containerBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = if (isDark) Color(0x66FFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Extension Providers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                    Text(
                        text = "Enable or disable providers to optimize home loading speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = subTextColor
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            registeredProviders.forEach { providerRegistry.setProviderEnabled(it.id, true) }
                            onProvidersChanged()
                        }
                    ) {
                        Text("Enable All", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = {
                            registeredProviders.forEach { providerRegistry.setProviderEnabled(it.id, false) }
                            onProvidersChanged()
                        }
                    ) {
                        Text("Disable All", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (registeredProviders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No providers currently registered. Install or reload extensions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = subTextColor
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(registeredProviders) { provider ->
                        val isEnabled = providerRegistry.isProviderEnabled(provider.id)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else itemBg,
                            border = BorderStroke(
                                1.dp,
                                if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else itemBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Extension,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(8.dp).size(20.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = provider.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                        Text(
                                            text = "ID: ${provider.id} • ${if (isEnabled) "Active" else "Disabled"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = subTextColor
                                        )
                                    }
                                }

                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        providerRegistry.setProviderEnabled(provider.id, checked)
                                        onProvidersChanged()
                                    }
                                )
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
