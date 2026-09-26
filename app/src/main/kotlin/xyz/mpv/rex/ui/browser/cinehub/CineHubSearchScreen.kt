package xyz.mpv.rex.ui.browser.cinehub

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.delay
import xyz.mpv.rex.cinehub.bridge.RexPlayerBridge
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamPosterCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassFilterChip
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassSearchBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonCard
import xyz.mpv.rex.ui.theme.maxstream.maxStreamShimmer
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass

/**
 * Flagship Max Stream Immersive Search Experience.
 * Features:
 * 1. Frosted glass search capsule with instant debounced multi-provider queries.
 * 2. Trending quick-filter chips for instant category browsing.
 * 3. Responsive 4K poster cards grid with smooth hover / D-pad focus scaling.
 * 4. Cinematic media details sheet and seamless handoff to RexPlayerBridge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CineHubSearchScreen(
    viewModel: CineHubViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchInput by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selectedDetails by viewModel.selectedMediaDetails.collectAsState()
    val isLoadingDetails by viewModel.isLoadingDetails.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var activeEpisodeName by remember { mutableStateOf<String?>(null) }

    // Quick suggestion genres
    val suggestions = listOf("Trending", "4K Action", "Sci-Fi", "Anime", "Drama", "Comedy", "Thriller", "Horror")

    // Automatically perform search with debounce as the user types
    LaunchedEffect(searchInput) {
        val query = searchInput.trim()
        if (query.isEmpty()) {
            viewModel.searchContent("")
        } else {
            delay(350)
            viewModel.searchContent(query)
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    val glassSurface = if (isDark) MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val elevatedSurface = if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val glassBorder = if (isDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedDetails != null) selectedDetails!!.name else "Search Max Stream",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (selectedDetails != null) {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("cinehub_details_back_button")
                        ) {
                            Icon(
                                Icons.Rounded.ArrowBack,
                                contentDescription = "Back to search results",
                                tint = primaryTextColor
                            )
                        }
                    } else if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("cinehub_screen_back_button")
                        ) {
                            Icon(
                                Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = primaryTextColor
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor
                )
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedDetails != null) {
                // Detail & Episode Selection View
                MediaDetailView(
                    details = selectedDetails!!,
                    isExtracting = isExtracting,
                    activeEpisode = activeEpisodeName,
                    onPlayClick = { epData, epTitle ->
                        activeEpisodeName = epTitle
                        viewModel.getStreamLinks(
                            providerName = selectedDetails!!.apiName,
                            episodeData = epData
                        ) { links ->
                            if (links.isNotEmpty()) {
                                val bestLink = links.maxByOrNull { it.quality } ?: links.first()
                                Toast.makeText(
                                    context,
                                    "Playing ${bestLink.name} (${bestLink.quality}p)",
                                    Toast.LENGTH_SHORT
                                ).show()
                                RexPlayerBridge.playStream(
                                    context = context,
                                    link = bestLink,
                                    title = epTitle ?: selectedDetails!!.name,
                                    posterUrl = selectedDetails!!.posterUrl,
                                    overview = selectedDetails!!.plot,
                                    year = selectedDetails!!.year?.toString(),
                                    rating = 8.0,
                                    providerName = selectedDetails!!.apiName,
                                    allLinks = links
                                )
                            } else {
                                Toast.makeText(context, "No stream links could be extracted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } else {
                // Search Bar + Filter Chips + Results Grid
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Unified Glass Search Bar
                    MaxStreamGlassSearchBar(
                        query = searchInput,
                        onQueryChange = { searchInput = it },
                        onSearch = { query -> viewModel.searchContent(query) },
                        placeholder = "Search movies, shows, anime & actors…",
                        onVoiceClick = {
                            Toast.makeText(context, "Voice search active", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Suggestion Chips using GlassFilterChip
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestions) { chip ->
                            val cleanTag = chip.replace("4K ", "")
                            MaxStreamGlassFilterChip(
                                text = chip,
                                isSelected = searchInput.equals(cleanTag, ignoreCase = true),
                                onClick = {
                                    searchInput = cleanTag
                                    viewModel.searchContent(searchInput)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isSearching) {
                        // Standard Loading State replacing search skeleton
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 56.dp, bottom = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(42.dp),
                                    color = MaxStreamTheme.NeonViolet,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Searching across streaming providers…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaxStreamTheme.TextMuted
                                )
                            }
                        }
                    } else if (searchResults.isEmpty() && searchInput.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = statusMessage ?: "No results found for \"$searchInput\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaxStreamTheme.TextMuted
                            )
                        }
                    } else {
                        // Poster Grid of Search Results
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("cinehub_search_results_grid")
                        ) {
                            items(searchResults, key = { "${it.apiName}_${it.url}" }) { item ->
                                val mediaType = item.type
                                MaxStreamPosterCard(
                                    title = item.name,
                                    posterUrl = item.posterUrl,
                                    subtitle = mediaType?.name?.uppercase() ?: "STREAM",
                                    qualityBadge = "4K UHD",
                                    cardWidth = 140.dp,
                                    onClick = {
                                        viewModel.loadMediaDetails(item.apiName, item.url, item)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Global Skeleton Pill Indicator for Details
            AnimatedVisibility(
                visible = isLoadingDetails,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                MaxStreamGlassCard(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = MaxStreamTheme.MidnightSurface.copy(alpha = 0.95f),
                    borderColor = Color.White.copy(alpha = 0.25f),
                    elevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaxStreamTheme.NeonViolet,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            "Fetching stream metadata…",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Poster Card for an individual search result (legacy fallback support).
 */
@Composable
fun SearchResultCard(
    item: SearchResponse,
    onClick: () -> Unit
) {
    MaxStreamPosterCard(
        title = item.name,
        posterUrl = item.posterUrl,
        subtitle = item.apiName,
        qualityBadge = "4K HDR",
        onClick = onClick
    )
}

/**
 * Media Details & Episodes View.
 */
@Composable
fun MediaDetailView(
    details: LoadResponse,
    isExtracting: Boolean,
    activeEpisode: String?,
    onPlayClick: (dataUrl: String, title: String?) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val primaryTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    val elevatedSurface = if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val glassBorder = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cinehub_details_view"),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Hero Header Row (Poster + Title + Provider + Plot)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaxStreamTheme.CardShape,
                    color = elevatedSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, glassBorder),
                    modifier = Modifier
                        .width(115.dp)
                        .aspectRatio(2f / 3f)
                ) {
                    if (!details.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = details.posterUrl,
                            contentDescription = details.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = mutedTextColor)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = details.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        ),
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = MaxStreamTheme.BadgeShape,
                            color = if (isDark) MaxStreamTheme.GlassSurfaceActive else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "OTT HD STREAM",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (details.year != null) {
                            Text(
                                text = "• ${details.year}",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryTextColor
                            )
                        }
                    }
                    if (!details.plot.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = details.plot!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = secondaryTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extraction Loading Banner
            if (isExtracting) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) MaxStreamTheme.MidnightSurface else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaxStreamTheme.CrimsonAccent,
                            modifier = Modifier.size(20.dp).testTag("cinehub_extracting_loader")
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Resolving high-speed stream links…",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = primaryTextColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Single Movie "Play" Button
        if (details is MovieLoadResponse) {
            item {
                Button(
                    onClick = { onPlayClick(details.dataUrl, details.name) },
                    shape = MaxStreamTheme.ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxStreamTheme.CrimsonAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cinehub_movie_play_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Watch Movie Now",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                }
            }
        }

        // TV Show Episodes List
        if (details is TvSeriesLoadResponse && details.episodes.isNotEmpty()) {
            item {
                Text(
                    text = "Episodes (${details.episodes.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(details.episodes) { ep ->
                val isCurrent = activeEpisode == ep.name
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) MaxStreamTheme.CrimsonAccent.copy(alpha = 0.20f) else elevatedSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCurrent) MaxStreamTheme.CrimsonAccent else glassBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlayClick(ep.data, ep.name) }
                        .testTag("cinehub_episode_${ep.episode ?: 0}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ep.name ?: "Episode ${ep.episode ?: ""}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isCurrent) MaxStreamTheme.CrimsonAccent else primaryTextColor
                            )
                            if (ep.season != null || ep.episode != null) {
                                Text(
                                    text = "Season ${ep.season ?: 1} • Episode ${ep.episode ?: 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedTextColor
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play Episode",
                            tint = if (isCurrent) MaxStreamTheme.CrimsonAccent else primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
