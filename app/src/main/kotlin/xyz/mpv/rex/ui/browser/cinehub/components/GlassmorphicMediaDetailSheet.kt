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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinehub.bridge.CloudStreamBridgeResolver
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Flagship Liquid Glass Media Detail Sheet.
 * Displays backdrop, poster, provider selector, categorized quality stream table, cast carousel,
 * and robust multi-provider search fallbacks to ensure no screen is ever blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassmorphicMediaDetailSheet(
    item: Any,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeItem by remember(item) { mutableStateOf<Any>(item) }
    var resolvedDetails by remember(activeItem) { mutableStateOf<CloudStreamBridgeResolver.ResolvedMediaDetails?>(null) }
    var isLoadingDetails by remember(activeItem) { mutableStateOf(true) }

    // Multi-Provider & Stream extraction states
    var availableProviders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedProviderName by remember { mutableStateOf<String?>(null) }
    var isExtractingStreams by remember { mutableStateOf(false) }
    var extractedLinks by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var extractedSubtitles by remember { mutableStateOf<List<com.lagradost.cloudstream3.SubtitleFile>>(emptyList()) }
    var streamError by remember { mutableStateOf<String?>(null) }

    // Smart Scraper Search & Fallback state
    var isScraperSearchExpanded by remember { mutableStateOf(false) }
    var searchQueryText by remember(activeItem) { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var isSearchingScrapers by remember { mutableStateOf(false) }

    // TV Series episode & season states
    var selectedSeasonIndex by remember { mutableIntStateOf(1) }
    var activeEpisodeForExtraction by remember { mutableStateOf<Episode?>(null) }

    // Handle back button to dismiss sheet
    BackHandler {
        onDismiss()
    }

    // Load available providers once
    LaunchedEffect(Unit) {
        availableProviders = CloudStreamBridgeResolver.getAvailableProviders()
    }

    // Function to trigger stream extraction for current details
    fun extractStreamsForDetails(
        details: CloudStreamBridgeResolver.ResolvedMediaDetails,
        episode: Episode? = null,
        provider: String? = selectedProviderName
    ) {
        scope.launch {
            isExtractingStreams = true
            streamError = null
            val (links, subs) = CloudStreamBridgeResolver.resolveStreamLinks(details, episode, provider)
            extractedLinks = links
            extractedSubtitles = subs
            isExtractingStreams = false
            if (links.isEmpty()) {
                streamError = "No direct stream links found on ${provider ?: details.providerName}. Try switching providers or refining title search."
            }
        }
    }

    // Load media details on item change
    LaunchedEffect(activeItem) {
        isLoadingDetails = true
        val details = CloudStreamBridgeResolver.resolveDetails(activeItem)
        resolvedDetails = details
        isLoadingDetails = false

        if (details != null) {
            searchQueryText = details.title
            if (details.isMovie) {
                extractStreamsForDetails(details, provider = selectedProviderName)
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
                            text = "Loading liquid media details…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (resolvedDetails == null) {
                // Fallback UI when details resolution fails
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Media Details Unresolved",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .intelligentGlassEffect(
                                    shape = RoundedCornerShape(20.dp),
                                    backgroundColor = Color(0x441E293B),
                                    borderColor = Color(0x44FFB300)
                                ),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "Online Metadata Unreachable",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "Unable to fetch online TMDB / provider metadata automatically for this media item. Search providers manually below:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                OutlinedTextField(
                                    value = searchQueryText,
                                    onValueChange = { searchQueryText = it },
                                    label = { Text("Search Title / Scraper Query") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            if (searchQueryText.isNotBlank()) {
                                                isSearchingScrapers = true
                                                scope.launch {
                                                    searchResults = CloudStreamBridgeResolver.searchProvidersWithQuery(searchQueryText)
                                                    isSearchingScrapers = false
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaxStreamTheme.ElectricCyan)
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaxStreamTheme.ElectricCyan,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedLabelColor = MaxStreamTheme.ElectricCyan,
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Button(
                                    onClick = {
                                        if (searchQueryText.isNotBlank()) {
                                            isSearchingScrapers = true
                                            scope.launch {
                                                searchResults = CloudStreamBridgeResolver.searchProvidersWithQuery(searchQueryText)
                                                isSearchingScrapers = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.ElectricCyan),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isSearchingScrapers) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Search Active Scrapers", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Search Matches (${searchResults.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        items(searchResults) { result ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeItem = result
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0x221E293B)),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp, 70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray)
                                    ) {
                                        AsyncImage(
                                            model = result.posterUrl,
                                            contentDescription = result.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = result.name, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(text = "Provider: ${result.apiName}", style = MaterialTheme.typography.labelSmall, color = MaxStreamTheme.ElectricCyan)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Select", tint = MaxStreamTheme.ElectricCyan)
                                }
                            }
                        }
                    }
                }
            } else {
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
                                .height(250.dp)
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
                            // Bottom gradient fade
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

                    // 2. Poster, Title & Badges
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

                            // Title & Info
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

                                // Badges Row
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

                                    Text(
                                        text = if (details.isMovie) "MOVIE" else "SERIES",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaxStreamTheme.ElectricCyan,
                                        modifier = Modifier
                                            .background(MaxStreamTheme.ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .border(1.dp, MaxStreamTheme.ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )

                                    if (details.providerName.isNotBlank()) {
                                        Text(
                                            text = selectedProviderName ?: details.providerName,
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

                    // 3. Provider Selector & Refine Scraper Query Row
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .offset(y = (-20).dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Select Provider / Server Source",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            // Provider Selector Chips
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Default Provider Chip
                                val isDefaultSelected = selectedProviderName == null
                                FilterChip(
                                    selected = isDefaultSelected,
                                    onClick = {
                                        selectedProviderName = null
                                        extractStreamsForDetails(details, provider = null)
                                    },
                                    label = { Text("Auto (${details.providerName})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaxStreamTheme.ElectricCyan,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0x221E293B),
                                        labelColor = Color.White
                                    )
                                )

                                availableProviders.forEach { provName ->
                                    val isSelected = selectedProviderName == provName
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedProviderName = provName
                                            extractStreamsForDetails(details, provider = provName)
                                        },
                                        label = { Text(provName) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaxStreamTheme.ElectricCyan,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0x221E293B),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Toggle Scraper Search Bar
                            TextButton(
                                onClick = { isScraperSearchExpanded = !isScraperSearchExpanded },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaxStreamTheme.ElectricCyan)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isScraperSearchExpanded) "Hide Manual Title Search" else "Title Search / Refine Scraper Query")
                            }

                            if (isScraperSearchExpanded) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQueryText,
                                        onValueChange = { searchQueryText = it },
                                        placeholder = { Text("Movie/Series Search Title") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaxStreamTheme.ElectricCyan,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            if (searchQueryText.isNotBlank()) {
                                                isSearchingScrapers = true
                                                scope.launch {
                                                    searchResults = CloudStreamBridgeResolver.searchProvidersWithQuery(
                                                        query = searchQueryText,
                                                        providerFilter = selectedProviderName
                                                    )
                                                    isSearchingScrapers = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.background(MaxStreamTheme.ElectricCyan, RoundedCornerShape(12.dp))
                                    ) {
                                        if (isSearchingScrapers) {
                                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Black)
                                        }
                                    }
                                }

                                if (searchResults.isNotEmpty()) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text("Search Results (${searchResults.size}):", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                        searchResults.take(5).forEach { res ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0x331E293B))
                                                    .clickable { activeItem = res }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(res.name, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                                                Text(res.apiName, style = MaterialTheme.typography.labelSmall, color = MaxStreamTheme.ElectricCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Movie Primary "Watch Now" & Categorized Stream Links Table
                    if (details.isMovie) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .offset(y = (-10).dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (isExtractingStreams) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .intelligentGlassEffect(
                                                shape = RoundedCornerShape(18.dp),
                                                backgroundColor = Color(0x331E293B),
                                                borderColor = MaxStreamTheme.ElectricCyan.copy(alpha = 0.3f)
                                            ),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
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
                                                    text = "Extracting Live Streams…",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Resolving mirrors on ${selectedProviderName ?: details.providerName}",
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
                                                providerName = selectedProviderName ?: details.providerName,
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

                                    // Categorized Stream Links List
                                    Text(
                                        text = "Available Streams & Server Mirrors (${extractedLinks.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        extractedLinks.forEachIndexed { index, link ->
                                            val isTop = index == 0
                                            val qualityLabel = Qualities.getStringByInt(link.quality)

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
                                                            providerName = selectedProviderName ?: details.providerName,
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

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = qualityLabel,
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isTop) MaxStreamTheme.ElectricCyan else Color.White.copy(alpha = 0.8f),
                                                        modifier = Modifier
                                                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (streamError != null) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .intelligentGlassEffect(
                                                shape = RoundedCornerShape(16.dp),
                                                backgroundColor = Color(0x33EF4444),
                                                borderColor = Color(0x66EF4444)
                                            ),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF8A80))
                                                Text(
                                                    text = "No direct stream links found",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFFF8A80)
                                                )
                                            }
                                            Text(
                                                text = streamError ?: "The selected provider returned no working streams.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )

                                            // Fallback Direct Play or Retry Button
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        extractStreamsForDetails(details, provider = selectedProviderName)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x44FFFFFF)),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Retry Extract")
                                                }

                                                if (details.dataUrl.isNotBlank() && !details.dataUrl.all { it.isDigit() }) {
                                                    Button(
                                                        onClick = {
                                                            CloudStreamBridgeResolver.launchPlayer(
                                                                context = context,
                                                                streamUrl = details.dataUrl,
                                                                title = details.title,
                                                                posterUrl = details.posterUrl,
                                                                overview = details.overview,
                                                                year = details.year,
                                                                rating = details.rating,
                                                                providerName = details.providerName
                                                            )
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.ElectricCyan),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Text("Play Direct Link", color = Color.Black, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. TV Series Episodes Section
                    if (!details.isMovie) {
                        val lr = details.loadResponse
                        val episodesList = if (lr is TvSeriesLoadResponse) {
                            lr.episodes
                        } else emptyList()

                        val seasons = episodesList.mapNotNull { it.season }.distinct().sorted()

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .offset(y = (-10).dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
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
                                    text = "Episodes (${episodesList.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

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
                                            val (links, subs) = CloudStreamBridgeResolver.resolveStreamLinks(details, ep, selectedProviderName)
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
                                                    providerName = selectedProviderName ?: details.providerName,
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

                    // 6. Synopsis & Plot
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

                    // 7. Cast Carousel
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
