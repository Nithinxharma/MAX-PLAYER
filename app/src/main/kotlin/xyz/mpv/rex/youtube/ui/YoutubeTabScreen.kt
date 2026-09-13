package xyz.mpv.rex.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.youtube.data.InvidiousClient
import xyz.mpv.rex.youtube.model.YoutubeVideo
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YoutubeTabScreen(
    onPlayRequested: (String, String, String) -> Unit,
    onUpdateMediaInfo: (
        thumbnail: String,
        title: String,
        author: String,
        description: String,
        metadata: Map<String, String>
    ) -> Unit = { _, _, _, _, _ -> }
) {
    var videoList by remember { mutableStateOf<List<YoutubeVideo>>(emptyList()) }
    var localShortsList by remember { mutableStateOf<List<LocalShortVideo>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("All") } // "All", "Trending", "Online Shorts", "Local Shorts"
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSearchBarVisible by remember { mutableStateOf(false) } 
    var refreshTrigger by remember { mutableIntStateOf(0) }
    
    // Bottom Sheet States
    var longPressedVideo by remember { mutableStateOf<YoutubeVideo?>(null) }
    var clickedChannelVideo by remember { mutableStateOf<YoutubeVideo?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(refreshTrigger, isSearching, selectedCategory) {
        isLoading = true
        if (selectedCategory == "Local Shorts") {
            localShortsList = queryLocalShorts(context)
            videoList = emptyList()
        } else if (selectedCategory == "Online Shorts") {
            videoList = InvidiousClient.fetchShorts()
            localShortsList = emptyList()
        } else if (isSearching && searchQuery.isNotBlank()) {
            videoList = InvidiousClient.fetchSearchVideos(searchQuery)
            localShortsList = emptyList()
        } else if (selectedCategory == "Trending") {
            videoList = InvidiousClient.fetchTrendingVideos("Movies")
            localShortsList = emptyList()
        } else {
            videoList = InvidiousClient.fetchTrendingVideos()
            localShortsList = emptyList()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserTopBar(
                    title = if (isSearching) "Search Results" else "CineTube & Shorts",
                    isInSelectionMode = false,
                    selectedCount = 0,
                    totalCount = if (selectedCategory == "Local Shorts") localShortsList.size else videoList.size,
                    onCancelSelection = {},
                    isHomeScreen = true, 
                    onSearchClick = {
                        isSearchBarVisible = !isSearchBarVisible
                    }
                )

                // Category Switcher (All, Trending, Online Shorts, Local Shorts)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf("All", "Trending", "Online Shorts", "Local Shorts")
                    categories.forEach { cat ->
                        val isSel = selectedCategory == cat
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedCategory = cat
                                isSearching = false
                            },
                            label = {
                                Text(
                                    text = when (cat) {
                                        "All" -> "🎬 All Videos"
                                        "Trending" -> "🔥 Trending"
                                        "Online Shorts" -> "⚡ Online Shorts"
                                        "Local Shorts" -> "📱 Local Shorts"
                                        else -> cat
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isSearchBarVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            placeholder = { Text("Search CineTube library...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty() || isSearching) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearching = false
                                        isSearchBarVisible = false
                                        keyboardController?.hide()
                                        refreshTrigger++
                                    }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    isSearching = true
                                    refreshTrigger++ 
                                }
                                keyboardController?.hide()
                            })
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(targetState = isLoading, label = "LoadingTransition") { loading ->
                if (loading) {
                    SkeletonLoadingGrid()
                } else if (selectedCategory == "Local Shorts") {
                    if (localShortsList.isEmpty()) {
                        EmptyShortsStateUi(
                            title = "No Local Shorts Found",
                            subtitle = "Short videos (≤90s) saved to your device will automatically appear here.",
                            onRetry = { refreshTrigger++ }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(localShortsList, key = { it.id }) { shortItem ->
                                LocalShortCard(
                                    item = shortItem,
                                    onClick = {
                                        onPlayRequested(shortItem.path, shortItem.title, "")
                                    }
                                )
                            }
                        }
                    }
                } else if (selectedCategory == "Online Shorts") {
                    if (videoList.isEmpty()) {
                        ErrorStateUi(
                            isSearching = isSearching,
                            onRetry = { refreshTrigger++ }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(videoList, key = { it.videoId }) { video ->
                                OnlineShortCard(
                                    video = video,
                                    onClick = {
                                        scope.launch {
                                            val candidates = InvidiousClient.fetchStreamCandidates(video.videoId)
                                            val ranked = xyz.mpv.rex.cinehub.failover.StreamHealthResolver.resolveAndRankCandidates(candidates)
                                            val primary = (ranked.firstOrNull() ?: candidates.firstOrNull() ?: xyz.mpv.rex.cinehub.failover.StreamCandidate(
                                                url = "https://www.youtube.com/watch?v=${video.videoId}",
                                                name = video.title,
                                                quality = "Auto",
                                                isM3u8 = false,
                                                headers = emptyMap()
                                            )).copy(name = video.title)
                                            val backups = (if (ranked.isNotEmpty()) ranked.drop(1) else candidates.drop(1)).map { it.copy(name = video.title) }
                                            xyz.mpv.rex.utils.media.MediaUtils.playStreamWithFailover(
                                                primaryCandidate = primary,
                                                backupCandidates = backups,
                                                context = context,
                                                title = video.title,
                                                launchSource = "cinetube_shorts",
                                                posterUrl = video.getBestThumbnailUrl(),
                                                sourceType = "cinetube"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else if (videoList.isEmpty()) {
                    ErrorStateUi(
                        isSearching = isSearching,
                        onRetry = { refreshTrigger++ }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!isSearching && selectedCategory == "All") {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                ShortsSpotlightRow(
                                    onSelectOnlineShorts = { selectedCategory = "Online Shorts" },
                                    onSelectLocalShorts = { selectedCategory = "Local Shorts" }
                                )
                            }
                        }

                        items(
                            items = videoList,
                            key = { it.videoId }
                        ) { video ->
                            VideoCardPremium(
                                video = video,
                                onClick = {
                                    scope.launch {
                                        onUpdateMediaInfo(
                                            video.getBestThumbnailUrl(),
                                            video.title,
                                            video.author,
                                            video.description,
                                            mapOf(
                                                "Views" to video.formatViewCount(),
                                                "Published" to video.publishedText,
                                                "Duration" to "${video.lengthSeconds}s",
                                                "Video ID" to video.videoId,
                                                "SourceType" to "youtube",
                                                "AuthorThumbnail" to (video.getBestAuthorThumbnailUrl() ?: "")
                                            )
                                        )
                                        
                                        val candidates = InvidiousClient.fetchStreamCandidates(video.videoId)
                                        val ranked = xyz.mpv.rex.cinehub.failover.StreamHealthResolver.resolveAndRankCandidates(candidates)
                                        val primary = (ranked.firstOrNull() ?: candidates.firstOrNull() ?: xyz.mpv.rex.cinehub.failover.StreamCandidate(
                                            url = "https://www.youtube.com/watch?v=${video.videoId}",
                                            name = video.title,
                                            quality = "Auto",
                                            isM3u8 = false,
                                            headers = emptyMap()
                                        )).copy(name = video.title)
                                        val backups = (if (ranked.isNotEmpty()) ranked.drop(1) else candidates.drop(1)).map { it.copy(name = video.title) }
                                        xyz.mpv.rex.utils.media.MediaUtils.playStreamWithFailover(
                                            primaryCandidate = primary,
                                            backupCandidates = backups,
                                            context = context,
                                            title = video.title,
                                            launchSource = "cinetube",
                                            posterUrl = video.getBestAuthorThumbnailUrl() ?: "",
                                            sourceType = "cinetube"
                                        )
                                    }
                                },
                                onLongClick = { longPressedVideo = video },
                                onChannelClick = { clickedChannelVideo = video }
                            )
                        }
                    }
                }
            }

            if (longPressedVideo != null) {
                ContextMenuBottomSheet(
                    video = longPressedVideo!!,
                    onDismiss = { longPressedVideo = null }
                )
            }

            if (clickedChannelVideo != null) {
                ChannelInfoBottomSheet(
                    video = clickedChannelVideo!!,
                    onDismiss = { clickedChannelVideo = null },
                    onPlayRequested = onPlayRequested,
                    onUpdateMediaInfo = onUpdateMediaInfo
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCardPremium(
    video: YoutubeVideo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onChannelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = video.getBestThumbnailUrl(),
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (video.isLiveNow) {
                    BadgeChip(text = "LIVE", color = Color.Red)
                }
                BadgeChip(text = "HD", color = MaterialTheme.colorScheme.primary)
            }

            if (video.lengthSeconds > 0) {
                val minutes = video.lengthSeconds / 60
                val seconds = video.lengthSeconds % 60
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = video.getBestAuthorThumbnailUrl() ?: "https://ui-avatars.com/api/?name=${video.author}&background=random",
                contentDescription = video.author,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChannelClick() },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = video.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${video.formatViewCount()} • ${video.publishedText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onLongClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BadgeChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.9f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuBottomSheet(video: YoutubeVideo, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = video.getBestThumbnailUrl(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            val menuItems = listOf(
                Triple(Icons.Default.PlayArrow, "Play Next", {}),
                Triple(Icons.Default.WatchLater, "Save to Watch Later", {}),
                Triple(Icons.Default.Download, "Download Video", {}),
                Triple(Icons.Default.Share, "Share", {}),
                Triple(Icons.Default.ContentCopy, "Copy Link", {}),
                Triple(Icons.Default.Block, "Not Interested", {})
            )

            menuItems.forEach { (icon, text, action) ->
                BottomSheetMenuItem(icon = icon, text = text, onClick = {
                    action()
                    onDismiss()
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelInfoBottomSheet(
    video: YoutubeVideo, 
    onDismiss: () -> Unit,
    onPlayRequested: (String, String, String) -> Unit,
    onUpdateMediaInfo: (String, String, String, String, Map<String, String>) -> Unit
) {
    var channelVideos by remember { mutableStateOf<List<YoutubeVideo>>(emptyList()) }
    var isFetchingVideos by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(video.author) {
        isFetchingVideos = true
        val results = InvidiousClient.fetchSearchVideos(video.author)
        channelVideos = results.filter { it.author.contains(video.author, ignoreCase = true) }
        isFetchingVideos = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.9f) 
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                            )
                        )
                ) {
                    AsyncImage(
                        model = video.getBestAuthorThumbnailUrl() ?: "https://ui-avatars.com/api/?name=${video.author}&background=random",
                        contentDescription = video.author,
                        modifier = Modifier
                            .size(80.dp)
                            .align(Alignment.BottomStart)
                            .offset(x = 24.dp, y = 40.dp) 
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp)) 
                
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = video.author,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = if(video.subCountText.isNotEmpty()) video.subCountText else "Official Channel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {  },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Subscribe", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Recent Uploads",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (isFetchingVideos) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (channelVideos.isEmpty()) {
                item {
                    Text(
                        text = "No recent videos found.",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(channelVideos) { channelVideo ->
                    ChannelVideoItem(
                        video = channelVideo,
                        onClick = {
                            scope.launch {
                                onUpdateMediaInfo(
                                    channelVideo.getBestThumbnailUrl(),
                                    channelVideo.title,
                                    channelVideo.author,
                                    channelVideo.description,
                                    mapOf(
                                        "Views" to channelVideo.formatViewCount(), 
                                        "Published" to channelVideo.publishedText,
                                        "SourceType" to "youtube",
                                        "AuthorThumbnail" to (channelVideo.getBestAuthorThumbnailUrl() ?: "")
                                    )
                                )
                                val candidates = InvidiousClient.fetchStreamCandidates(channelVideo.videoId)
                                val ranked = xyz.mpv.rex.cinehub.failover.StreamHealthResolver.resolveAndRankCandidates(candidates)
                                val primary = (ranked.firstOrNull() ?: candidates.first()).copy(name = channelVideo.title)
                                val backups = (if (ranked.isNotEmpty()) ranked.drop(1) else candidates.drop(1)).map { it.copy(name = channelVideo.title) }
                                xyz.mpv.rex.utils.media.MediaUtils.playStreamWithFailover(
                                    primaryCandidate = primary,
                                    backupCandidates = backups,
                                    context = context,
                                    title = channelVideo.title,
                                    launchSource = "cinetube",
                                    posterUrl = channelVideo.getBestAuthorThumbnailUrl() ?: "",
                                    sourceType = "cinetube"
                                )
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelVideoItem(video: YoutubeVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = video.getBestThumbnailUrl(),
            contentDescription = null,
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${video.formatViewCount()} • ${video.publishedText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2
            )
        }
    }
}

@Composable
fun BottomSheetMenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(24.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SkeletonLoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    }
                }
            }
        }
    }
}

fun Modifier.shimmerEffect(): Modifier {
    return this.background(Color.Gray.copy(alpha = 0.2f))
}

@Composable
fun ErrorStateUi(isSearching: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = "Error",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearching) "No cinematic results found." else "Network timeout. Node offline.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please check your connection or switch nodes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Retry Connection", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

data class LocalShortVideo(
    val id: Long,
    val title: String,
    val path: String,
    val durationSeconds: Int,
    val width: Int,
    val height: Int
)

suspend fun queryLocalShorts(context: android.content.Context): List<LocalShortVideo> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val results = mutableListOf<LocalShortVideo>()
    val projection = arrayOf(
        android.provider.MediaStore.Video.Media._ID,
        android.provider.MediaStore.Video.Media.DISPLAY_NAME,
        android.provider.MediaStore.Video.Media.DATA,
        android.provider.MediaStore.Video.Media.DURATION,
        android.provider.MediaStore.Video.Media.WIDTH,
        android.provider.MediaStore.Video.Media.HEIGHT
    )
    val selection = "${android.provider.MediaStore.Video.Media.DURATION} > 0 AND ${android.provider.MediaStore.Video.Media.DURATION} <= 95000"
    val sortOrder = "${android.provider.MediaStore.Video.Media.DATE_MODIFIED} DESC"
    try {
        context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
            val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
            val widthCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.HEIGHT)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Short Video"
                val path = cursor.getString(dataCol) ?: ""
                val duration = (cursor.getLong(durCol) / 1000).toInt()
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                if (path.isNotBlank()) {
                    results.add(LocalShortVideo(id, name, path, duration, width, height))
                }
            }
        }
    } catch (_: Exception) {}
    results
}

@Composable
fun ShortsSpotlightRow(
    onSelectOnlineShorts: () -> Unit,
    onSelectLocalShorts: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Online Shorts Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onSelectOnlineShorts() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Online Shorts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trending YouTube clips",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Local Shorts Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onSelectLocalShorts() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Local Shorts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Device vertical videos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineShortCard(
    video: YoutubeVideo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.getBestThumbnailUrl(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 180f
                        )
                    )
            )

            // Shorts badge
            Surface(
                color = Color(0xFFE50914),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "SHORTS",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.author,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LocalShortCard(
    item: LocalShortVideo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
            }

            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 140f
                        )
                    )
            )

            // Duration badge
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${item.durationSeconds}s",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Local Clip • ${item.width}x${item.height}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun EmptyShortsStateUi(
    title: String,
    subtitle: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MovieFilter,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Refresh")
        }
    }
}
