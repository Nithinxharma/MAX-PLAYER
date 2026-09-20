package xyz.mpv.rex.ui.player.controls.components.sheets

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.data.ActiveMediaResolution
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.data.NfoScanner
import xyz.mpv.rex.cinehub.model.EpisodeItem
import xyz.mpv.rex.cinehub.model.MovieItem
import xyz.mpv.rex.ui.player.PlayerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSheet(
    viewModel: PlayerViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentFilePath = `is`.xyz.mpv.MPVLib.getPropertyString("path") ?: ""
    val mediaTitle = viewModel.mediaTitle.collectAsState().value ?: ""
    val durationSec = try { `is`.xyz.mpv.MPVLib.getPropertyDouble("duration") ?: 0.0 } catch (_: Exception) { 0.0 }
    val width = `is`.xyz.mpv.MPVLib.getPropertyString("width") ?: ""
    val height = `is`.xyz.mpv.MPVLib.getPropertyString("height") ?: ""
    val resolution = if (width.isNotBlank() && height.isNotBlank()) "${width}x${height}" else ""
    val videoCodec = `is`.xyz.mpv.MPVLib.getPropertyString("video-format") ?: ""
    val audioCodec = `is`.xyz.mpv.MPVLib.getPropertyString("audio-codec-name") ?: ""

    val customPoster by viewModel.customMediaPosterUrl.collectAsState()
    val customOverview by viewModel.customMediaOverview.collectAsState()
    val customYear by viewModel.customMediaYear.collectAsState()
    val customRating by viewModel.customMediaRating.collectAsState()
    val customProvider by viewModel.customMediaProvider.collectAsState()
    val currentQuality by viewModel.currentQualityName.collectAsState()

    var resolutionData by remember { mutableStateOf<ActiveMediaResolution?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val cleanTitle = remember(mediaTitle) {
        val t = mediaTitle
        if (t.isBlank() || t.startsWith("http://") || t.startsWith("https://") || t.endsWith(".m3u8") || t.contains(".m3u8") || t == "index.m3u8" || t == "master.m3u8") {
            "Max Stream"
        } else {
            t
        }
    }

    val effectiveMovie = remember(resolutionData, customPoster, customOverview, customYear, customRating, customProvider, cleanTitle, durationSec) {
        val fromResolution = (resolutionData as? ActiveMediaResolution.Movie)?.movie
        if (fromResolution != null) {
            fromResolution.copy(title = cleanTitle.ifBlank { fromResolution.title })
        } else if (!customPoster.isNullOrBlank() || !customOverview.isNullOrBlank() || !customYear.isNullOrBlank() || !customProvider.isNullOrBlank() || cleanTitle != "Max Stream") {
            val runtimeMins = if (durationSec > 0) (durationSec / 60).toInt() else 0
            MovieItem(
                videoFilePath = "",
                title = cleanTitle,
                originalTitle = "",
                userRating = customRating?.toDoubleOrNull() ?: 8.0,
                plot = customOverview ?: "Streamed directly via ${customProvider ?: "Max Stream"}.",
                mpaa = "",
                genre = customProvider ?: "Max Stream",
                director = "",
                premiered = customYear ?: "",
                posterPath = customPoster,
                runtime = runtimeMins
            )
        } else null
    }

    // For TV Show Season/Episodes in Sheet
    var selectedSeason by remember { mutableIntStateOf(1) }
    var tvEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var isLoadingTvEpisodes by remember { mutableStateOf(false) }

    LaunchedEffect(currentFilePath, mediaTitle) {
        isLoading = true
        val resolved = CineOnlineScraper.resolveActiveMedia(
            context = context,
            filePath = currentFilePath,
            mediaTitle = mediaTitle,
            durationSeconds = durationSec,
            resolution = resolution,
            videoCodec = videoCodec,
            audioCodec = audioCodec
        )
        resolutionData = resolved
        if (resolved is ActiveMediaResolution.TvShow) {
            selectedSeason = resolved.season
            tvEpisodes = resolved.episodes
        }
        isLoading = false
    }

    LaunchedEffect(selectedSeason) {
        val current = resolutionData
        if (current is ActiveMediaResolution.TvShow && selectedSeason != current.season) {
            isLoadingTvEpisodes = true
            val eps = withContext(Dispatchers.IO) {
                if (current.show.folderPath.isNotBlank() && File(current.show.folderPath).exists()) {
                    val allLocal = NfoScanner.scanTvShowEpisodes(File(current.show.folderPath))
                    val seasonLocal = allLocal.filter { it.season == selectedSeason }
                    if (seasonLocal.isNotEmpty()) seasonLocal else allLocal
                } else {
                    CineOnlineScraper.fetchTvShowEpisodes(
                        context,
                        current.show.tmdbId.ifBlank { current.show.title },
                        selectedSeason,
                        current.show.title
                    )
                }
            }
            tvEpisodes = eps
            isLoadingTvEpisodes = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xF210111A), // Glassmorphic frosted dark container
        tonalElevation = 8.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0x66FFFFFF))
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                when (val data = resolutionData) {
                    is ActiveMediaResolution.TvShow -> {
                        TvShowGlassmorphismContent(
                            data = data,
                            selectedSeason = selectedSeason,
                            onSeasonSelected = { selectedSeason = it },
                            episodes = tvEpisodes,
                            isLoadingEpisodes = isLoadingTvEpisodes,
                            onPlayEpisode = { ep ->
                                scope.launch(Dispatchers.IO) {
                                    val resolvedUri = ep.videoFilePath
                                    withContext(Dispatchers.Main) {
                                        onDismissRequest()
                                        Toast.makeText(context, "Playing ${ep.title}", Toast.LENGTH_SHORT).show()
                                        `is`.xyz.mpv.MPVLib.command("loadfile", resolvedUri)
                                    }
                                }
                            }
                        )
                    }
                    is ActiveMediaResolution.Movie -> {
                        MovieGlassmorphismContent(
                            movie = effectiveMovie ?: data.movie,
                            quality = currentQuality.ifBlank { resolution.ifBlank { "HD" } },
                            provider = customProvider ?: "Max Stream",
                            onRefresh = {
                                isLoading = true
                                scope.launch {
                                    val refreshed = CineOnlineScraper.getOrFetchMovie(
                                        context,
                                        cleanTitle,
                                        data.movie.tmdbId,
                                        forceRefresh = true
                                    )
                                    if (refreshed != null) {
                                        resolutionData = ActiveMediaResolution.Movie(refreshed)
                                    }
                                    isLoading = false
                                }
                            }
                        )
                    }
                    is ActiveMediaResolution.Normal, null -> {
                        if (effectiveMovie != null) {
                            MovieGlassmorphismContent(
                                movie = effectiveMovie,
                                quality = currentQuality.ifBlank { resolution.ifBlank { "HD" } },
                                provider = customProvider ?: "Max Stream",
                                onRefresh = {}
                            )
                        } else {
                            val normalData = resolutionData as? ActiveMediaResolution.Normal
                            NormalMediaGlassmorphismContent(
                                title = cleanTitle,
                                year = customYear ?: "",
                                duration = normalData?.durationFormatted ?: if (durationSec > 0) "${(durationSec / 60).toInt()}m" else "Live Stream",
                                quality = currentQuality.ifBlank { resolution.ifBlank { "HD" } },
                                provider = customProvider ?: "Max Stream",
                                videoCodec = videoCodec,
                                audioCodec = audioCodec
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvShowGlassmorphismContent(
    data: ActiveMediaResolution.TvShow,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    episodes: List<EpisodeItem>,
    isLoadingEpisodes: Boolean,
    onPlayEpisode: (EpisodeItem) -> Unit
) {
    val show = data.show
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        // Hero Backdrop
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                AsyncImage(
                    model = data.episodeStill ?: show.backdropPath ?: show.posterPath,
                    contentDescription = show.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x8010111A),
                                    Color(0xF210111A)
                                )
                            )
                        )
                )

                // Episode badge pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TV Series • S${data.season}:E${data.episode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Title and Poster Row
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-30).dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Dynamic Series Poster Rectangle
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.5.dp, Color(0x55FFFFFF)), RoundedCornerShape(14.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        AsyncImage(
                            model = show.posterPath,
                            contentDescription = show.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = show.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = data.episodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (show.userRating > 0.0) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", show.userRating),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = " • ",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = show.premiered.take(4),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            if (show.genre.isNotBlank()) {
                                Text(
                                    text = " • ${show.genre.take(20)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Episode Plot Card in Glassmorphism Style
                if (data.episodePlot.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 18.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF)),
                        border = BorderStroke(1.dp, Color(0x20FFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Episode Synopsis",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = data.episodePlot,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.88f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Season Selector
                Text(
                    text = "Episode Guide",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(data.allSeasons) { s ->
                        FilterChip(
                            selected = selectedSeason == s,
                            onClick = { onSeasonSelected(s) },
                            label = { Text("Season $s", fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = Color(0x1FFFFFFF),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedSeason == s,
                                borderColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                }
            }
        }

        // Episode Cards List
        if (isLoadingEpisodes) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        } else {
            items(episodes) { ep ->
                val isCurrentPlaying = ep.season == data.season && ep.episode == data.episode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .clickable { onPlayEpisode(ep) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentPlaying) Color(0x356366F1) else Color(0x15FFFFFF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isCurrentPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color(0x22FFFFFF)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Episode Still Image
                        Box(
                            modifier = Modifier
                                .size(width = 86.dp, height = 58.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x22FFFFFF)),
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
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "E${ep.episode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isCurrentPlaying) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            text = "PLAYING",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = ep.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (ep.plot.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ep.plot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f),
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
}

@Composable
private fun MovieGlassmorphismContent(
    movie: MovieItem,
    quality: String = "HD",
    provider: String = "Max Stream",
    onRefresh: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        // Hero Backdrop
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                AsyncImage(
                    model = movie.backdropPath ?: movie.posterPath,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x8010111A),
                                    Color(0xF210111A)
                                )
                            )
                        )
                )

                // Movie Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Feature Film",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Title and Poster Row
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-30).dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Movie Poster in Dynamic Rectangle
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.5.dp, Color(0x55FFFFFF)), RoundedCornerShape(14.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        AsyncImage(
                            model = movie.posterPath,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (movie.tagline.isNotBlank()) {
                            Text(
                                text = "\"${movie.tagline}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (movie.userRating > 0.0) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", movie.userRating),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = " • ",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = movie.premiered.take(4),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            if (movie.runtime > 0) {
                                Text(
                                    text = " • ${movie.runtime}m",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }

                        // Quality and Provider pills
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = quality.ifBlank { "HD" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            if (provider.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x28FFFFFF),
                                    border = BorderStroke(1.dp, Color(0x35FFFFFF))
                                ) {
                                    Text(
                                        text = provider,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (movie.genre.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = movie.genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Plot Overview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF)),
                    border = BorderStroke(1.dp, Color(0x20FFFFFF))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Plot Overview",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = movie.plot.ifEmpty { "No description available." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            lineHeight = 20.sp
                        )
                    }
                }

                // Cast row
                if (movie.actors.isNotEmpty()) {
                    Text(
                        text = "Cast & Characters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                    ) {
                        items(movie.actors) { actor ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(76.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .border(BorderStroke(1.dp, Color(0x44FFFFFF)), CircleShape)
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    AsyncImage(
                                        model = actor.thumbUrl,
                                        contentDescription = actor.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = actor.name,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                if (actor.character.isNotBlank()) {
                                    Text(
                                        text = actor.character,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Refresh Button
                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Scraped Metadata")
                }
            }
        }
    }
}

@Composable
private fun NormalMediaGlassmorphismContent(
    title: String,
    year: String,
    duration: String,
    quality: String,
    provider: String,
    videoCodec: String,
    audioCodec: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (year.isNotBlank()) {
                        Text(
                            text = year,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = quality.ifBlank { "HD" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (provider.isNotBlank()) {
                        Text(
                            text = "• $provider",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Technical Specs Cards in Glassmorphism style
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF)),
            border = BorderStroke(1.dp, Color(0x25FFFFFF))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Stream Details",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TechSpecPill(
                        label = "Quality",
                        value = quality.ifBlank { "HD" },
                        modifier = Modifier.weight(1f)
                    )
                    TechSpecPill(
                        label = "Runtime",
                        value = duration.ifBlank { "Live Stream" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TechSpecPill(
                        label = "Source",
                        value = provider.ifBlank { "Max Stream" },
                        modifier = Modifier.weight(1f)
                    )
                    TechSpecPill(
                        label = "Year",
                        value = year.ifBlank { "N/A" },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (videoCodec.isNotBlank() || audioCodec.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TechSpecPill(
                            label = "Video Codec",
                            value = videoCodec.ifBlank { "Auto" },
                            modifier = Modifier.weight(1f)
                        )
                        TechSpecPill(
                            label = "Audio Codec",
                            value = audioCodec.ifBlank { "Auto" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechSpecPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x22000000),
        border = BorderStroke(1.dp, Color(0x20FFFFFF)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
