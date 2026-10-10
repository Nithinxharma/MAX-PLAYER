package xyz.mpv.rex.ui.browser.cinehub

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.download.ActiveDownloadTask
import xyz.mpv.rex.cinehub.download.CineDownloadManager
import xyz.mpv.rex.cinehub.download.DownloadedVideoItem
import xyz.mpv.rex.cinehub.extension.model.LibraryItem
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.database.entities.PlaylistEntity
import xyz.mpv.rex.database.entities.RecentlyPlayedEntity
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamDownloadsSection
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.utils.media.MediaUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CineHubUnifiedMediaScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val isDark = isSystemInDarkTheme()

        val database = koinInject<MpvExDatabase>()
        val libraryDao = database.cineLibraryDao()
        val libraryItems by libraryDao.getAllLibraryItems().collectAsState(initial = emptyList())

        val recentlyPlayedRepo = koinInject<RecentlyPlayedRepository>()
        val playlistRepo = koinInject<PlaylistRepository>()
        val playlists by playlistRepo.observeAllPlaylists().collectAsState(initial = emptyList())

        var selectedTab by remember { mutableIntStateOf(0) } // 0 = Watchlist, 1 = History, 2 = Downloads, 3 = Playlists
        var searchQuery by remember { mutableStateOf("") }
        var selectedDetailItem by remember { mutableStateOf<Any?>(null) }
        var showCreatePlaylistDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        var recentHistoryItems by remember { mutableStateOf<List<RecentlyPlayedEntity>>(emptyList()) }
        var activeDownloads by remember { mutableStateOf<List<ActiveDownloadTask>>(emptyList()) }
        var completedDownloads by remember { mutableStateOf<List<DownloadedVideoItem>>(emptyList()) }

        fun refreshData() {
            scope.launch(Dispatchers.IO) {
                val recents = runCatching { recentlyPlayedRepo.getRecentlyPlayed(limit = 100) }.getOrDefault(emptyList())
                val active = CineDownloadManager.getActiveDownloads(context)
                val completed = CineDownloadManager.getDownloadedVideos(context)
                withContext(Dispatchers.Main) {
                    recentHistoryItems = recents
                    activeDownloads = active
                    completedDownloads = completed
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshData()
        }

        if (selectedDetailItem != null) {
            CineDetailView(
                item = selectedDetailItem!!,
                onDismiss = { selectedDetailItem = null }
            )
            return
        }

        val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
        val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Color(0xFF0B0C10) else MaterialTheme.colorScheme.background)
                .padding(top = 16.dp)
                .testTag("cine_unified_media_screen")
        ) {
            // High-End Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f),
                        border = BorderStroke(1.5.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = "My Media Hub",
                                tint = MaxStreamTheme.CrimsonAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "My Media Hub",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                letterSpacing = 0.3.sp
                            ),
                            color = primaryTextColor
                        )
                        Text(
                            text = "Unified Watchlist, History, Downloads & Playlists",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor
                        )
                    }
                }
            }

            // Glassmorphism Sub-Tab Selector
            val tabs = listOf(
                "Watchlist (${libraryItems.size})" to Icons.Rounded.Bookmark,
                "History (${recentHistoryItems.size})" to Icons.Rounded.History,
                "Downloads (${activeDownloads.size + completedDownloads.size})" to Icons.Rounded.CloudDownload,
                "Playlists (${playlists.size})" to Icons.AutoMirrored.Rounded.PlaylistPlay
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tabs.size) { index ->
                    val (label, icon) = tabs[index]
                    val isSelected = selectedTab == index

                    MaxStreamGlassCard(
                        onClick = { selectedTab = index },
                        shape = RoundedCornerShape(16.dp),
                        borderColor = if (isSelected) MaxStreamTheme.CrimsonAccent else Color(0x22FFFFFF),
                        backgroundColor = if (isSelected) MaxStreamTheme.CrimsonAccent.copy(alpha = 0.25f) else Color(0x0DFFFFFF),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) MaxStreamTheme.CrimsonAccent else mutedTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = if (isSelected) primaryTextColor else mutedTextColor
                            )
                        }
                    }
                }
            }

            // Unified Filter Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search items in My Media...", color = mutedTextColor) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = mutedTextColor) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaxStreamTheme.CrimsonAccent,
                    unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = if (isDark) Color(0x0DFFFFFF) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Content with Animated Transition
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)))
                },
                label = "media_tab_transition",
                modifier = Modifier.fillMaxSize()
            ) { target ->
                when (target) {
                    0 -> UnifiedWatchlistTab(
                        libraryItems = libraryItems,
                        searchQuery = searchQuery,
                        onItemClick = { item -> selectedDetailItem = item },
                        onRemove = { item ->
                            scope.launch(Dispatchers.IO) {
                                libraryDao.deleteLibraryItem(item)
                            }
                        }
                    )

                    1 -> UnifiedHistoryTab(
                        historyItems = recentHistoryItems,
                        searchQuery = searchQuery,
                        onPlayItem = { entity ->
                            MediaUtils.playFile(
                                source = entity.filePath,
                                context = context,
                                launchSource = "history",
                                title = entity.videoTitle ?: File(entity.filePath).nameWithoutExtension
                            )
                        },
                        onClearHistory = {
                            scope.launch(Dispatchers.IO) {
                                recentlyPlayedRepo.clearAll()
                                refreshData()
                            }
                        }
                    )

                    2 -> MaxStreamDownloadsSection(
                        activeTasks = activeDownloads,
                        completedVideos = completedDownloads,
                        modifier = Modifier.fillMaxSize(),
                        onPlayVideo = { video ->
                            MediaUtils.playFile(
                                source = video.file.absolutePath,
                                context = context,
                                launchSource = "downloads",
                                title = video.name
                            )
                        },
                        onDeleteVideo = { video ->
                            scope.launch(Dispatchers.IO) {
                                CineDownloadManager.deleteDownloadedVideo(video.file)
                                refreshData()
                            }
                        },
                        onCancelTask = { task ->
                            CineDownloadManager.cancelDownload(context, task.id)
                            refreshData()
                        }
                    )

                    3 -> UnifiedPlaylistsTab(
                        playlists = playlists,
                        searchQuery = searchQuery,
                        onCreatePlaylist = { showCreatePlaylistDialog = true }
                    )
                }
            }
        }

        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text("Create New Playlist", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    playlistRepo.createPlaylist(newPlaylistName.trim())
                                    newPlaylistName = ""
                                    withContext(Dispatchers.Main) {
                                        showCreatePlaylistDialog = false
                                        refreshData()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold, color = MaxStreamTheme.CrimsonAccent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun UnifiedWatchlistTab(
    libraryItems: List<LibraryItem>,
    searchQuery: String,
    onItemClick: (LibraryItem) -> Unit,
    onRemove: (LibraryItem) -> Unit
) {
    var selectedWatchStatus by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("Recently Added") }
    var showSortMenu by remember { mutableStateOf(false) }
    var randomPickItem by remember { mutableStateOf<LibraryItem?>(null) }
    var showRandomDialog by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    val statuses = listOf("All", "Watching", "Plan to Watch", "Completed", "Favorites", "Subscriptions", "Dropped")

    val filtered = remember(libraryItems, selectedWatchStatus, searchQuery, sortBy) {
        val list = libraryItems.filter { item ->
            val matchesStatus = when (selectedWatchStatus) {
                "All" -> true
                "Watching" -> item.watchStatus == 1
                "Plan to Watch" -> item.watchStatus == 0
                "Completed" -> item.watchStatus == 2
                "Favorites" -> item.watchStatus == 1 || item.watchStatus == 2
                "Subscriptions" -> item.watchStatus == 0 || item.watchStatus == 1
                "Dropped" -> item.watchStatus == 3
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) true else item.title.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesQuery
        }

        when (sortBy) {
            "Alphabetical" -> list.sortedBy { it.title }
            "Recently Added" -> list.reversed()
            else -> list
        }
    }

    if (showRandomDialog && randomPickItem != null) {
        val picked = randomPickItem!!
        AlertDialog(
            onDismissRequest = { showRandomDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaxStreamTheme.ElectricCyan
                    )
                    Text("Random Pick", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!picked.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = picked.posterUrl,
                            contentDescription = picked.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(0.68f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text(
                        text = picked.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRandomDialog = false
                        onItemClick(picked)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                ) {
                    Text("Watch Now")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val pool = filtered.ifEmpty { libraryItems }
                    if (pool.isNotEmpty()) {
                        randomPickItem = pool.random()
                    }
                }) {
                    Text("Pick Another")
                }
            },
            containerColor = Color(0xFF121622),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Controls Header Row: Filter Chips + Sort Button + Random Title Picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(statuses) { status ->
                    val isSelected = selectedWatchStatus == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedWatchStatus = status },
                        label = { Text(status, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                            selectedLabelColor = Color.White,
                            containerColor = if (isDark) Color(0x1FFFFFFF) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Pick Random Title Button
            IconButton(
                onClick = {
                    val pool = filtered.ifEmpty { libraryItems }
                    if (pool.isNotEmpty()) {
                        randomPickItem = pool.random()
                        showRandomDialog = true
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaxStreamTheme.ElectricCyan.copy(alpha = 0.20f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Pick Random Title",
                    tint = MaxStreamTheme.ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.5f)
                    )
                    Text("No Watchlist Items Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                    Text("Bookmark titles from search or detail view to save them here.", style = MaterialTheme.typography.bodyMedium, color = mutedTextColor, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { item ->
                    UnifiedWatchlistPosterCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onRemove = { onRemove(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedWatchlistPosterCard(
    item: LibraryItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val statusLabel = when (item.watchStatus) {
        0 -> "PLANNED"
        1 -> "WATCHING"
        2 -> "COMPLETED"
        3 -> "DROPPED"
        else -> "SAVED"
    }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDark) Color(0xFF18181E) else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                color = MaxStreamTheme.CrimsonAccent,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UnifiedHistoryTab(
    historyItems: List<RecentlyPlayedEntity>,
    searchQuery: String,
    onPlayItem: (RecentlyPlayedEntity) -> Unit,
    onClearHistory: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    val filtered = remember(historyItems, searchQuery) {
        if (searchQuery.isBlank()) historyItems else historyItems.filter {
            (it.videoTitle ?: "").contains(searchQuery, ignoreCase = true) || it.filePath.contains(searchQuery, ignoreCase = true)
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (historyItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF3B30), contentColor = Color(0xFFFF3B30)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Watch History", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.5f))
                    Text("No Watch History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                    Text("Videos and streams you watch will automatically appear here with progress tracking.", style = MaterialTheme.typography.bodyMedium, color = mutedTextColor, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { entity ->
                    MaxStreamGlassCard(
                        onClick = { onPlayItem(entity) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaxStreamTheme.CrimsonAccent, modifier = Modifier.size(24.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entity.videoTitle ?: File(entity.filePath).nameWithoutExtension,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormat.format(Date(entity.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedTextColor
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
private fun UnifiedPlaylistsTab(
    playlists: List<PlaylistEntity>,
    searchQuery: String,
    onCreatePlaylist: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    val filtered = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Custom Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
            Button(
                onClick = onCreatePlaylist,
                colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Playlist", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.5f))
                    Text("No Playlists Created", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                    Text("Create custom playlists to group and organize your local media and stream URLs.", style = MaterialTheme.typography.bodyMedium, color = mutedTextColor, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { playlist ->
                    MaxStreamGlassCard(
                        onClick = { },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = MaxStreamTheme.CrimsonAccent, modifier = Modifier.size(26.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = primaryTextColor)
                                Text("Custom Playlist", style = MaterialTheme.typography.bodySmall, color = mutedTextColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
