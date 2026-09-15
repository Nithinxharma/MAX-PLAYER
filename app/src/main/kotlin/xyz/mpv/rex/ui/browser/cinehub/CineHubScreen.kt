package xyz.mpv.rex.ui.browser.cinehub

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.cinehub.data.CineCloudRepoClient
import xyz.mpv.rex.cinehub.data.CineFolderMetadataManager
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.data.KodiMediaScraper
import xyz.mpv.rex.cinehub.data.NfoScanner
import xyz.mpv.rex.cinehub.data.TMDBMovieNode
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

    val enableOnlineCatalog by browserPreferences.enableOnlineCatalog.collectAsState()
    val enableLocalMovies by browserPreferences.enableLocalMovies.collectAsState()
    val enableLocalTvShows by browserPreferences.enableLocalTvShows.collectAsState()
    val enableMetadataScraping by browserPreferences.enableMetadataScraping.collectAsState()
    val enableArtworkDownloads by browserPreferences.enableArtworkDownloads.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Movies, 2 = TV Shows, 3 = Library
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

    var onlineMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var onlineTvShows by remember { mutableStateOf<List<TvShowItem>>(emptyList()) }
    var localMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var localTvShows by remember { mutableStateOf<List<TvShowItem>>(emptyList()) }

    var searchResults by remember { mutableStateOf<List<TMDBMovieNode>>(emptyList()) }
    var extensionSearchResults by remember { mutableStateOf<List<xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var providerHomeRows by remember { mutableStateOf<List<xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList>>(emptyList()) }

    var selectedDetailItem by remember { mutableStateOf<Any?>(null) }

    val navBarHeight = LocalNavigationBarHeight.current

    // Load media data function
    fun loadMedia() {
      scope.launch(Dispatchers.IO) {
        isLoading = true
        try {
          if (enableOnlineCatalog) {
            val movies = runCatching { CineCloudRepoClient.fetchOnlineMovies(context) }.getOrDefault(emptyList())
            val tvShows = runCatching { CineCloudRepoClient.fetchOnlineTvShows(context) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
              onlineMovies = movies
              onlineTvShows = tvShows
            }
          }

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
        } catch (_: Exception) {
        } finally {
          withContext(Dispatchers.Main) {
            isLoading = false
            isRefreshing = false
          }
        }
      }
    }

    LaunchedEffect(Unit) {
      loadMedia()
    }

    // Featured Hero Movie (pick first online or local movie with backdrop)
    val featuredMovie = remember(onlineMovies, localMovies) {
      onlineMovies.firstOrNull { it.backdropPath != null || it.posterPath != null }
        ?: localMovies.firstOrNull { it.backdropPath != null || it.posterPath != null }
        ?: onlineMovies.firstOrNull()
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.cinehub),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
              )
            }
          },
          actions = {
            IconButton(
              onClick = {
                showScraperSheet = true
              },
              modifier = Modifier.testTag("cinehub_kodi_scraper_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = "Kodi Media Scraper",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            IconButton(
              onClick = {
                isRefreshing = true
                loadMedia()
                Toast.makeText(context, "Refreshing catalog…", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.testTag("cinehub_refresh_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
              )
            }
            IconButton(
              onClick = {
                backstack.add(xyz.mpv.rex.ui.preferences.ExtensionPreferencesScreenRoute)
              },
              modifier = Modifier.testTag("cinehub_extensions_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = "Extensions",
              )
            }
            IconButton(
              onClick = {
                backstack.add(PreferencesScreen)
              },
              modifier = Modifier.testTag("cinehub_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
              )
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
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navBarHeight + 32.dp),
          ) {
            // Search Input Field
            item {
              OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                  searchQuery = query
                  if (query.length >= 2) {
                    isSearchActive = true
                    isSearchingOnline = true
                    scope.launch(Dispatchers.IO) {
                      val tmdbDeferred = async {
                        runCatching {
                          CineOnlineScraper.executeManualMovieSearch(query)
                        }.getOrDefault(emptyList())
                      }
                      val activeProviders = providerRegistry.getEnabledProviders()
                      val extDeferreds = activeProviders.map { provider ->
                        async {
                          runCatching { provider.search(query) }.getOrDefault(emptyList())
                        }
                      }
                      val res = tmdbDeferred.await()
                      val extRes = extDeferreds.awaitAll().flatten()
                      withContext(Dispatchers.Main) {
                        searchResults = res
                        extensionSearchResults = extRes
                        isSearchingOnline = false
                      }
                    }
                  } else {
                    isSearchActive = false
                    searchResults = emptyList()
                    extensionSearchResults = emptyList()
                  }
                },
                placeholder = { Text("Search movies, TV shows, actors…") },
                leadingIcon = {
                  Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                  if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                      searchQuery = ""
                      isSearchActive = false
                      searchResults = emptyList()
                      extensionSearchResults = emptyList()
                    }) {
                      Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                    }
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp)
                  .testTag("cinehub_search_input"),
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
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(120.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                  }
                }
              } else if (searchResults.isEmpty() && extensionSearchResults.isEmpty()) {
                item {
                  Text(
                    text = "No results found for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                  )
                }
              } else {
                if (extensionSearchResults.isNotEmpty()) {
                  item {
                    Text(
                      text = "Extension Providers",
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
                        scope.launch(Dispatchers.IO) {
                          val provider = providerRegistry.getProvider(extItem.providerId)
                          val details = provider?.loadDetails(extItem.url)
                          withContext(Dispatchers.Main) {
                            selectedDetailItem = MovieItem(
                              videoFilePath = "ext_stream:${extItem.providerId}::${extItem.url}",
                              title = details?.title ?: extItem.title,
                              originalTitle = details?.title ?: extItem.title,
                              userRating = details?.rating ?: extItem.rating ?: 0.0,
                              plot = details?.overview ?: "Content provided by ${extItem.providerName}",
                              tagline = "Source: ${extItem.providerName}",
                              mpaa = "NR",
                              genre = details?.genres?.firstOrNull() ?: extItem.type.name,
                              director = extItem.providerName,
                              premiered = (details?.year ?: extItem.year)?.toString() ?: "",
                              runtime = 120,
                              posterPath = details?.posterUrl ?: extItem.posterUrl,
                              backdropPath = details?.backdropUrl ?: details?.posterUrl ?: extItem.posterUrl,
                              tmdbId = details?.id ?: extItem.id,
                              sourceType = "extension"
                            )
                          }
                        }
                      }
                    )
                  }
                }
                if (searchResults.isNotEmpty()) {
                  item {
                    Text(
                      text = "CineHub Catalog",
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                  }
                  items(searchResults) { node ->
                    SearchResultRow(
                      node = node,
                      onClick = {
                        scope.launch(Dispatchers.IO) {
                          val queryTitle = node.title ?: ""
                          val fetched = CineOnlineScraper.getOrFetchMovie(context, queryTitle, node.id.toString())
                          withContext(Dispatchers.Main) {
                            if (fetched != null) {
                              selectedDetailItem = fetched
                            } else {
                              Toast.makeText(context, queryTitle, Toast.LENGTH_SHORT).show()
                            }
                          }
                        }
                      },
                    )
                  }
                }
              }
            } else {
              // Category Filter Chips
              item {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("All") },
                    shape = RoundedCornerShape(16.dp),
                  )
                  FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Movies") },
                    shape = RoundedCornerShape(16.dp),
                  )
                  FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("TV Shows") },
                    shape = RoundedCornerShape(16.dp),
                  )
                  FilterChip(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("Library") },
                    shape = RoundedCornerShape(16.dp),
                  )
                }
              }

              // Featured Hero Banner (if available and tab == 0 or 1)
              if ((selectedTab == 0 || selectedTab == 1) && featuredMovie != null) {
                item {
                  FeaturedHeroCard(
                    movie = featuredMovie,
                    onPlayClick = {
                      playMediaItem(context, featuredMovie, scope)
                    },
                    onDetailClick = {
                      selectedDetailItem = featuredMovie
                    },
                  )
                }
              }

              // Trending Movies Section
              if (selectedTab == 0 || selectedTab == 1) {
                if (onlineMovies.isNotEmpty()) {
                  item {
                    SectionHeader(title = "Trending Movies")
                  }
                  item {
                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(onlineMovies) { movie ->
                        MediaPosterCard(
                          title = movie.title,
                          posterUrl = movie.posterPath,
                          rating = movie.userRating,
                          year = movie.premiered.take(4),
                          onClick = {
                            selectedDetailItem = movie
                          },
                        )
                      }
                    }
                  }
                }

                // Local Movies Section
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
                          onClick = {
                            selectedDetailItem = movie
                          },
                        )
                      }
                    }
                  }
                }
              }

              // TV Series Section
              if (selectedTab == 0 || selectedTab == 2) {
                if (onlineTvShows.isNotEmpty()) {
                  item {
                    SectionHeader(title = "Popular TV Shows")
                  }
                  item {
                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(onlineTvShows) { show ->
                        MediaPosterCard(
                          title = show.title,
                          posterUrl = show.posterPath,
                          rating = show.userRating,
                          year = show.premiered.take(4),
                          onClick = {
                            selectedDetailItem = show
                          },
                        )
                      }
                    }
                  }
                }

                // Local TV Shows Section
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
                          onClick = {
                            selectedDetailItem = show
                          },
                        )
                      }
                    }
                  }
                }
              }

              // Extension Provider Sections
              if (selectedTab == 0 && providerHomeRows.isNotEmpty()) {
                providerHomeRows.forEach { homeRow ->
                  if (homeRow.items.isNotEmpty()) {
                    item {
                      SectionHeader(title = homeRow.title)
                    }
                    item {
                      LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        items(homeRow.items) { item ->
                          MediaPosterCard(
                            title = item.title,
                            posterUrl = item.posterUrl,
                            rating = item.rating ?: 0.0,
                            year = item.year?.toString() ?: "",
                            onClick = {
                              scope.launch(Dispatchers.IO) {
                                val provider = providerRegistry.getProvider(item.providerId)
                                val details = provider?.loadDetails(item.url)
                                withContext(Dispatchers.Main) {
                                  selectedDetailItem = MovieItem(
                                    videoFilePath = "ext_stream:${item.providerId}::${item.url}",
                                    title = details?.title ?: item.title,
                                    originalTitle = details?.title ?: item.title,
                                    userRating = details?.rating ?: item.rating ?: 0.0,
                                    plot = details?.overview ?: "Content provided by ${item.providerName}",
                                    tagline = "Source: ${item.providerName}",
                                    mpaa = "NR",
                                    genre = details?.genres?.firstOrNull() ?: item.type.name,
                                    director = item.providerName,
                                    premiered = (details?.year ?: item.year)?.toString() ?: "",
                                    runtime = 120,
                                    posterPath = details?.posterUrl ?: item.posterUrl,
                                    backdropPath = details?.backdropUrl ?: details?.posterUrl ?: item.posterUrl,
                                    tmdbId = details?.id ?: item.id,
                                    sourceType = "extension"
                                  )
                                }
                              }
                            }
                          )
                        }
                      }
                    }
                  }
                }
              }

              // Library Section
              if (selectedTab == 3) {
                if (libraryItems.isEmpty()) {
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
                              onClick = {
                                // For now, we will attempt to fetch online if possible,
                                // or launch appropriate handler
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
                }
              }

              // Empty state when no media items available
              if (onlineMovies.isEmpty() && onlineTvShows.isEmpty() && localMovies.isEmpty() && localTvShows.isEmpty()) {
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
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp),
                      )
                      Text(
                        text = "Your CineHub collection is ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                      )
                      Text(
                        text = "Place movies in the CineRex/movies folder or enable online catalogs to browse titles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                      )
                      FilledTonalButton(
                        onClick = {
                          isRefreshing = true
                          loadMedia()
                        },
                        modifier = Modifier.padding(top = 8.dp),
                      ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Library")
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // Details Bottom Sheet
        selectedDetailItem?.let { item ->
          CineDetailBottomSheet(
            item = item,
            onDismiss = { selectedDetailItem = null },
            onPlay = {
              playMediaItem(context, item, scope)
            },
            onRefreshItem = { updated ->
              selectedDetailItem = updated
              loadMedia()
            },
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

                var totalMovies = 0
                var totalTv = 0

                val dirsToScrape = listOfNotNull(dirToScan, defaultAltDir, defaultCineRexDir).filter { it.exists() }
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
            withContext(Dispatchers.Main) {
              if (stream != null && stream.url.isNotBlank()) {
                Toast.makeText(context, "Playing from ${provider?.name ?: "Extension"}", Toast.LENGTH_SHORT).show()
                MediaUtils.playFile(stream.url, context, "cinehub")
              } else {
                Toast.makeText(context, "No stream found from extension", Toast.LENGTH_SHORT).show()
              }
            }
          }
        } else if (item.videoFilePath.startsWith("cnc_stream:") || item.videoFilePath.startsWith("vidsrc:")) {
          val parts = item.videoFilePath.split(":")
          val postId = parts.getOrNull(1) ?: ""
          val platform = parts.getOrNull(2) ?: "vidsrc"
          scope.launch(Dispatchers.IO) {
            val directStream = CineCloudRepoClient.resolveDirectStreamUrl(postId, platform)
            withContext(Dispatchers.Main) {
              if (!directStream.isNullOrBlank()) {
                MediaUtils.playFile(directStream, context, "cinehub")
              } else {
                Toast.makeText(context, "Resolving stream… Playing ${item.title}", Toast.LENGTH_SHORT).show()
                MediaUtils.playFile(item.videoFilePath, context, "cinehub")
              }
            }
          }
        } else {
          MediaUtils.playFile(item.videoFilePath, context, "cinehub")
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
          if (firstEp != null) {
            val playUri = CineCloudRepoClient.resolveMediaUri(firstEp.videoFilePath)
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "Playing ${item.title} - ${firstEp.title}", Toast.LENGTH_SHORT).show()
              MediaUtils.playFile(playUri, context, "cinehub")
            }
          } else {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "No episodes found for ${item.title}", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
  )
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
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .width(140.dp)
      .clickable { onClick() },
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .width(140.dp)
        .height(205.dp),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (!posterUrl.isNullOrBlank()) {
          AsyncImage(
            model = posterUrl,
            contentDescription = title,
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
            Icon(
              imageVector = Icons.Outlined.Movie,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(36.dp),
            )
          }
        }

        if (rating > 0.0) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(11.dp),
              )
              Spacer(modifier = Modifier.width(2.dp))
              Text(
                text = String.format("%.1f", rating),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 10.sp,
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    if (year.isNotBlank()) {
      Text(
        text = year,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
      )
    }
  }
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
        SuggestionChip(
          onClick = {},
          label = { Text(item.providerName, style = MaterialTheme.typography.labelSmall) },
          modifier = Modifier.height(24.dp)
        )
        if (item.year != null) {
          Text(
            text = item.year.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
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

@Composable
private fun SearchResultRow(
  node: TMDBMovieNode,
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
      val posterUrl = node.poster_path?.let { "https://image.tmdb.org/t/p/w200$it" }
      if (!posterUrl.isNullOrBlank()) {
        AsyncImage(
          model = posterUrl,
          contentDescription = node.title,
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
        text = node.title ?: "",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!node.release_date.isNullOrBlank()) {
        Text(
          text = node.release_date.take(4),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
      if (node.vote_average > 0.0) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 2.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFFFB800),
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = String.format("%.1f", node.vote_average),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CineDetailBottomSheet(
  item: Any,
  onDismiss: () -> Unit,
  onPlay: () -> Unit,
  onRefreshItem: (Any) -> Unit = {},
) {
  val database = koinInject<xyz.mpv.rex.database.MpvExDatabase>()
  val libraryDao = database.cineLibraryDao()
  val libraryItems by libraryDao.getAllLibraryItems().collectAsState(initial = emptyList())
  val scope = rememberCoroutineScope()
  
  val tmdbId = when (item) {
    is MovieItem -> item.tmdbId.takeIf { it.isNotBlank() } ?: item.title
    is TvShowItem -> item.tmdbId.takeIf { it.isNotBlank() } ?: item.title
    else -> ""
  }
  val isMovie = item is MovieItem
  val libraryEntry = libraryItems.find { it.url == tmdbId }
  val inLibrary = libraryEntry != null
  var showLibraryMenu by remember { mutableStateOf(false) }

  val title = when (item) {
    is MovieItem -> item.title
    is TvShowItem -> item.title
    else -> ""
  }
  val plot = when (item) {
    is MovieItem -> item.plot
    is TvShowItem -> item.plot
    else -> ""
  }
  val posterPath = when (item) {
    is MovieItem -> item.posterPath
    is TvShowItem -> item.posterPath
    else -> null
  }
  val backdropPath = when (item) {
    is MovieItem -> item.backdropPath ?: item.posterPath
    is TvShowItem -> item.backdropPath ?: item.posterPath
    else -> null
  }
  val rating = when (item) {
    is MovieItem -> item.userRating
    is TvShowItem -> item.userRating
    else -> 0.0
  }
  val year = when (item) {
    is MovieItem -> item.premiered.take(4)
    is TvShowItem -> item.premiered.take(4)
    else -> ""
  }
  val genre = when (item) {
    is MovieItem -> item.genre
    is TvShowItem -> item.genre
    else -> ""
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 36.dp),
    ) {
      if (!backdropPath.isNullOrBlank()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        ) {
          AsyncImage(
            model = backdropPath,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = title,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
            )
            if (genre.isNotBlank() || year.isNotBlank()) {
              Text(
                text = listOf(year, genre).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp),
              )
            }
          }

          if (rating > 0.0) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .background(
                  MaterialTheme.colorScheme.primaryContainer,
                  RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = String.format("%.1f", rating),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
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
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                    val playUri = CineCloudRepoClient.resolveMediaUri(nextEpisodeToPlay.videoFilePath)
                    withContext(Dispatchers.Main) {
                      onDismiss()
                      Toast.makeText(context, "Playing ${item.title} - ${nextEpisodeToPlay.title}", Toast.LENGTH_SHORT).show()
                      MediaUtils.playFile(playUri, context, "cinehub")
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
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(modifier = Modifier.size(32.dp))
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
                        val playUri = CineCloudRepoClient.resolveMediaUri(ep.videoFilePath)
                        withContext(Dispatchers.Main) {
                          onDismiss()
                          Toast.makeText(context, "Playing ${ep.title}", Toast.LENGTH_SHORT).show()
                          MediaUtils.playFile(playUri, context, "cinehub")
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
        }
      }
    }
  }
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
