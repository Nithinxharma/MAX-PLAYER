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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.cinehub.data.CineCloudRepoClient
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
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

    val enableOnlineCatalog by browserPreferences.enableOnlineCatalog.collectAsState()
    val enableLocalMovies by browserPreferences.enableLocalMovies.collectAsState()
    val enableLocalTvShows by browserPreferences.enableLocalTvShows.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Movies, 2 = TV Shows
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    var onlineMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var onlineTvShows by remember { mutableStateOf<List<TvShowItem>>(emptyList()) }
    var localMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var localTvShows by remember { mutableStateOf<List<TvShowItem>>(emptyList()) }

    var searchResults by remember { mutableStateOf<List<TMDBMovieNode>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }

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
          val extStorage = Environment.getExternalStorageDirectory()
          if (enableLocalMovies) {
            val cineRexMoviesDir = File(extStorage, "CineRex/movies")
            val altMoviesDir = File(extStorage, "Movies")
            val scanned = mutableListOf<MovieItem>()
            if (cineRexMoviesDir.exists()) {
              scanned.addAll(NfoScanner.scanDirectoryForMovies(cineRexMoviesDir))
            }
            if (altMoviesDir.exists()) {
              scanned.addAll(NfoScanner.scanDirectoryForMovies(altMoviesDir))
            }
            withContext(Dispatchers.Main) {
              localMovies = scanned
            }
          }

          if (enableLocalTvShows) {
            val cineRexTvDir = File(extStorage, "CineRex/tvshows")
            val altTvDir = File(extStorage, "TV Shows")
            val scannedTv = mutableListOf<TvShowItem>()
            if (cineRexTvDir.exists()) {
              scannedTv.addAll(NfoScanner.scanDirectoryForTvShows(cineRexTvDir))
            }
            if (altTvDir.exists()) {
              scannedTv.addAll(NfoScanner.scanDirectoryForTvShows(altTvDir))
            }
            withContext(Dispatchers.Main) {
              localTvShows = scannedTv
            }
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
                backstack.add(MediaLibraryPreferencesScreen)
              },
              modifier = Modifier.testTag("cinehub_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "CineHub Settings",
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
                      val res = runCatching {
                        CineOnlineScraper.executeManualMovieSearch(query)
                      }.getOrDefault(emptyList())
                      withContext(Dispatchers.Main) {
                        searchResults = res
                        isSearchingOnline = false
                      }
                    }
                  } else {
                    isSearchActive = false
                    searchResults = emptyList()
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
              } else if (searchResults.isEmpty()) {
                item {
                  Text(
                    text = "No online results found for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                  )
                }
              } else {
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
        if (item.videoFilePath.startsWith("cnc_stream:") || item.videoFilePath.startsWith("vidsrc:")) {
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
      .width(130.dp)
      .clickable { onClick() },
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .width(130.dp)
        .height(190.dp),
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
) {
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

        if (item is MovieItem) {
          Button(
            onClick = {
              onDismiss()
              onPlay()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp),
            shape = RoundedCornerShape(16.dp),
          ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Play Movie", fontWeight = FontWeight.Bold)
          }
        } else if (item is TvShowItem) {
          val context = LocalContext.current
          val scope = rememberCoroutineScope()
          var selectedSeason by remember { mutableIntStateOf(1) }
          var allLocalEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
          var availableSeasons by remember { mutableStateOf<List<Int>>(listOf(1)) }
          var seasonEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
          var isLoadingEpisodes by remember { mutableStateOf(true) }

          // Initial scan or season detection
          LaunchedEffect(item) {
            isLoadingEpisodes = true
            withContext(Dispatchers.IO) {
              if (item.folderPath.isNotBlank() && File(item.folderPath).exists()) {
                val scanned = NfoScanner.scanTvShowEpisodes(File(item.folderPath))
                allLocalEpisodes = scanned
                val detected = scanned.map { it.season }.filter { it > 0 }.distinct().sorted()
                availableSeasons = if (detected.isNotEmpty()) detected else listOf(1)
                selectedSeason = availableSeasons.firstOrNull() ?: 1
              } else {
                // Online show: check TMDB for season count
                val tmdbId = item.tmdbId
                val seasonsFromTmdb = if (tmdbId.isNotBlank() && tmdbId.all { it.isDigit() }) {
                  val details = CineOnlineScraper.fetchTvShowDetails(tmdbId)
                  details?.seasons?.map { it.season_number }?.filter { it > 0 }?.distinct()?.sorted()
                } else null

                availableSeasons = seasonsFromTmdb?.takeIf { it.isNotEmpty() } ?: (1..3).toList()
                selectedSeason = availableSeasons.firstOrNull() ?: 1
              }
            }
            isLoadingEpisodes = false
          }

          // Fetch or filter episodes whenever selectedSeason changes
          LaunchedEffect(item, selectedSeason, allLocalEpisodes) {
            isLoadingEpisodes = true
            val loaded = withContext(Dispatchers.IO) {
              if (allLocalEpisodes.isNotEmpty()) {
                val filtered = allLocalEpisodes.filter { it.season == selectedSeason }
                if (filtered.isNotEmpty()) filtered else allLocalEpisodes
              } else if (item.folderPath.isNotBlank() && File(item.folderPath).exists()) {
                val scanned = NfoScanner.scanTvShowEpisodes(File(item.folderPath))
                allLocalEpisodes = scanned
                val filtered = scanned.filter { it.season == selectedSeason }
                if (filtered.isNotEmpty()) filtered else scanned
              } else {
                CineOnlineScraper.fetchTvShowEpisodes(
                  context,
                  item.tmdbId.ifBlank { item.title },
                  selectedSeason,
                  item.title
                )
              }
            }
            seasonEpisodes = loaded
            isLoadingEpisodes = false
          }

          val nextEpisodeToPlay = seasonEpisodes.firstOrNull() ?: allLocalEpisodes.firstOrNull()

          // Top Play Next / Quick Play Button
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
              .fillMaxWidth()
              .height(50.dp),
            shape = RoundedCornerShape(16.dp),
          ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (nextEpisodeToPlay != null) "Play ${nextEpisodeToPlay.title}" else "Play Series",
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
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
