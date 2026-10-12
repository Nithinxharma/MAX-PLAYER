package xyz.mpv.rex.ui.browser.cinehub.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinehub.bridge.CloudStreamBridgeResolver
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Flagship Deep AMOLED Glassmorphism Media Detail Sheet.
 * Displays backdrop, poster, cast carousel, and verified CloudStream live streams.
 * For Movies: resolves single file streams with 1-tap "Watch Now".
 * For TV Shows: displays season selector and episode cards with instant playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassmorphicMediaDetailSheet(
    item: Any,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resolvedDetails by remember(item) { mutableStateOf<CloudStreamBridgeResolver.ResolvedMediaDetails?>(null) }
    var isLoadingDetails by remember(item) { mutableStateOf(true) }

    // Stream extraction states
    var isExtractingStreams by remember { mutableStateOf(false) }
    var extractedLinks by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var extractedSubtitles by remember { mutableStateOf<List<com.lagradost.cloudstream3.SubtitleFile>>(emptyList()) }
    var streamError by remember { mutableStateOf<String?>(null) }

    // TV Series episode & season states
    var selectedSeasonIndex by remember { mutableIntStateOf(1) }
    var activeEpisodeForExtraction by remember { mutableStateOf<Episode?>(null) }

    // Handle back button to dismiss sheet
    BackHandler {
        onDismiss()
    }

    // Load media details on appearance
    LaunchedEffect(item) {
        isLoadingDetails = true
        val details = CloudStreamBridgeResolver.resolveDetails(item)
        resolvedDetails = details
        isLoadingDetails = false

        if (details != null && details.isMovie) {
            // Automatically resolve streams for movies
            isExtractingStreams = true
            streamError = null
            val (links, subs) = CloudStreamBridgeResolver.resolveStreamLinks(details)
            extractedLinks = links
            extractedSubtitles = subs
            isExtractingStreams = false
            if (links.isEmpty()) {
                streamError = "No direct stream links found. Try an alternate provider."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF207080D),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glassmorphic_detail_sheet")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F131F),
                            Color(0xFF07080D),
                            Color.Black
                        )
                    )
                )
        ) {
            if (isLoadingDetails) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaxStreamTheme.ElectricCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Loading media details…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (resolvedDetails != null) {
                val details = resolvedDetails!!

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    // 1. Backdrop Banner Header
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            val backdrop = details.backdropUrl ?: details.posterUrl
                            if (!backdrop.isNullOrBlank()) {
                                AsyncImage(
                                    model = backdrop,
                                    contentDescription = details.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Bottom gradient fade to solid black
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0x9907080D),
                                                Color(0xFF07080D)
                                            )
                                        )
                                    )
                            )

                            // Close Button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .size(38.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 2. Poster, Title & Metadata Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .offset(y = (-40).dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Poster Thumbnail
                            Box(
                                modifier = Modifier
                                    .width(115.dp)
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF151926))
                                    .border(1.5.dp, Color(0x444E7BFF), RoundedCornerShape(16.dp))
                            ) {
                                AsyncImage(
                                    model = details.posterUrl,
                                    contentDescription = details.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Title & Pills
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 44.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = details.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Badges Row: Year, Rating, Type, Provider
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    if (!details.year.isNullOrBlank()) {
                                        Text(
                                            text = details.year,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .background(Color(0x332A334E), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (details.rating != null && details.rating > 0.0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .background(Color(0x33FFB300), RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0x66FFB300), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = String.format("%.1f", details.rating),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFFFD54F)
                                            )
                                        }
                                    }

                                    // Media Type Badge
                                    Text(
                                        text = if (details.isMovie) "MOVIE" else "SERIES",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaxStreamTheme.ElectricCyan,
                                        modifier = Modifier
                                            .background(MaxStreamTheme.ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .border(1.dp, MaxStreamTheme.ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )

                                    // Provider badge
                                    if (details.providerName.isNotBlank()) {
                                        Text(
                                            text = details.providerName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .background(Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Movie Primary "Watch Now" & Streams Section
                    if (details.isMovie) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .offset(y = (-20).dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (isExtractingStreams) {
                                    // Extracting state
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(18.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaxStreamTheme.ElectricCyan,
                                                strokeWidth = 3.dp,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Resolving Live Streams…",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Extracting verified mirrors via DefaultExtractors",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                } else if (extractedLinks.isNotEmpty()) {
                                    val topLink = extractedLinks.first()

                                    // Big Glowing Primary Play Button
                                    Button(
                                        onClick = {
                                            CloudStreamBridgeResolver.launchPlayer(
                                                context = context,
                                                streamUrl = topLink.url,
                                                title = details.title,
                                                posterUrl = details.posterUrl,
                                                overview = details.overview,
                                                year = details.year,
                                                rating = details.rating,
                                                providerName = details.providerName,
                                                headers = topLink.headers,
                                                allLinks = extractedLinks,
                                                subtitles = extractedSubtitles
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaxStreamTheme.ElectricCyan
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("watch_now_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Text(
                                                text = "WATCH NOW (${Qualities.getStringByInt(topLink.quality)})",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                                color = Color.Black
                                            )
                                        }
                                    }

                                    // Alternate Mirrors & Servers List
                                    Text(
                                        text = "Available Streams & Servers (${extractedLinks.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        extractedLinks.forEachIndexed { index, link ->
                                            val isTop = index == 0
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(if (isTop) Color(0x3300E5FF) else Color(0x221E293B))
                                                    .border(
                                                        1.dp,
                                                        if (isTop) Color(0x5500E5FF) else Color.White.copy(alpha = 0.1f),
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .clickable {
                                                        CloudStreamBridgeResolver.launchPlayer(
                                                            context = context,
                                                            streamUrl = link.url,
                                                            title = details.title,
                                                            posterUrl = details.posterUrl,
                                                            overview = details.overview,
                                                            year = details.year,
                                                            rating = details.rating,
                                                            providerName = details.providerName,
                                                            headers = link.headers,
                                                            allLinks = extractedLinks,
                                                            subtitles = extractedSubtitles
                                                        )
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = null,
                                                        tint = if (isTop) MaxStreamTheme.ElectricCyan else Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = link.name.ifBlank { "Server ${index + 1}" },
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = Color.White
                                                        )
                                                        Text(
                                                            text = link.source.ifBlank { details.providerName },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = Qualities.getStringByInt(link.quality),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isTop) MaxStreamTheme.ElectricCyan else Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier
                                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else if (streamError != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "No direct stream links found",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFFF8A80)
                                            )
                                            Text(
                                                text = "The selected provider did not return working streams for this title.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. TV Series Episodes Section
                    if (!details.isMovie) {
                        val lr = details.loadResponse
                        val episodesList = if (lr is TvSeriesLoadResponse) {
                            lr.episodes
                        } else emptyList()

                        // Extract unique seasons
                        val seasons = episodesList.mapNotNull { it.season }.distinct().sorted()

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .offset(y = (-20).dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Season Tabs
                                if (seasons.size > 1) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        seasons.forEach { seasonNum ->
                                            val isSelected = seasonNum == selectedSeasonIndex
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) MaxStreamTheme.ElectricCyan else Color(0x221E293B))
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) MaxStreamTheme.ElectricCyan else Color.White.copy(alpha = 0.1f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable { selectedSeasonIndex = seasonNum }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Season $seasonNum",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isSelected) Color.Black else Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Episodes",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        // Filter episodes for selected season
                        val displayedEpisodes = if (seasons.isNotEmpty()) {
                            episodesList.filter { it.season == selectedSeasonIndex }
                        } else episodesList

                        items(displayedEpisodes) { ep ->
                            val isExtractingThis = isExtractingStreams && activeEpisodeForExtraction == ep

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                                    .clickable {
                                        activeEpisodeForExtraction = ep
                                        isExtractingStreams = true
                                        scope.launch {
                                            val (links, subs) = CloudStreamBridgeResolver.resolveStreamLinks(details, ep)
                                            isExtractingStreams = false
                                            if (links.isNotEmpty()) {
                                                CloudStreamBridgeResolver.launchPlayer(
                                                    context = context,
                                                    streamUrl = links.first().url,
                                                    title = details.title,
                                                    posterUrl = details.posterUrl,
                                                    overview = details.overview,
                                                    year = details.year,
                                                    rating = details.rating,
                                                    providerName = details.providerName,
                                                    headers = links.first().headers,
                                                    allLinks = links,
                                                    subtitles = subs,
                                                    episode = ep
                                                )
                                            } else {
                                                Toast.makeText(context, "No stream links found for Episode ${ep.episode}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0x221E293B)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Thumbnail or Episode Number Box
                                    Box(
                                        modifier = Modifier
                                            .width(80.dp)
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF151926)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!ep.posterUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ep.posterUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isExtractingThis) {
                                                CircularProgressIndicator(
                                                    color = MaxStreamTheme.ElectricCyan,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "Play Episode",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Episode Info
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Episode ${ep.episode}: ${ep.name ?: "Episode ${ep.episode}"}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!ep.description.isNullOrBlank()) {
                                            Text(
                                                text = ep.description ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.6f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Synopsis & Plot
                    if (!details.overview.isNullOrBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Synopsis",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = details.overview ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    // 6. Cast Carousel
                    if (details.cast.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Top Cast",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(details.cast.take(12)) { actor ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.width(80.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF151926))
                                                    .border(1.5.dp, Color(0x334E7BFF), CircleShape)
                                            ) {
                                                AsyncImage(
                                                    model = actor.actor.image,
                                                    contentDescription = actor.actor.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Text(
                                                text = actor.actor.name,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            actor.roleString?.let { role ->
                                                Text(
                                                    text = role,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    maxLines = 1,
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
            }
        }
    }
}
