package xyz.mpv.rex.cinehub.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.data.CineFolderMetadataManager
import xyz.mpv.rex.cinehub.extension.model.LibraryItem
import xyz.mpv.rex.cinehub.stream.CloudStreamDownloadManager
import xyz.mpv.rex.cinehub.stream.CloudStreamRequest
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaUtils
import java.io.File

@Serializable
object UnifiedLibraryRoute : Screen {
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        UnifiedLibraryScreen(
            onNavigateBack = { backstack.removeLastOrNull() }
        )
    }
}

enum class LibraryTab(val displayName: String) {
    ALL("All"),
    BOOKMARKS("Bookmarks"),
    LOCAL("Local Media"),
    NETWORK("Network"),
    DOWNLOADS("Downloads"),
    HISTORY("History")
}

data class UnifiedLibraryCardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val posterUrl: String? = null,
    val tab: LibraryTab,
    val directPathOrUrl: String? = null,
    val cloudStreamRequest: CloudStreamRequest? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedLibraryScreen(
    onNavigateBack: () -> Unit,
    onOpenStreamRequest: ((CloudStreamRequest) -> Unit)? = null
) {
    val context = LocalContext.current
    val database = koinInject<MpvExDatabase>()
    val downloadManager = koinInject<CloudStreamDownloadManager>()

    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    var libraryItems by remember { mutableStateOf<List<UnifiedLibraryCardItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val downloads by downloadManager.downloads.collectAsState()
    val bookmarks by database.cineLibraryDao().getAllLibraryItems().collectAsState(initial = emptyList())

    LaunchedEffect(selectedTab, bookmarks, downloads) {
        isLoading = true
        val items = mutableListOf<UnifiedLibraryCardItem>()

        // 1. Bookmarks
        if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.BOOKMARKS) {
            bookmarks.forEach { b ->
                items.add(
                    UnifiedLibraryCardItem(
                        id = "bm_${b.url}",
                        title = b.title,
                        subtitle = "Bookmark • ${b.apiName}",
                        posterUrl = b.posterUrl,
                        tab = LibraryTab.BOOKMARKS,
                        directPathOrUrl = b.url,
                        cloudStreamRequest = CloudStreamRequest(
                            title = b.title,
                            posterUrl = b.posterUrl,
                            dataUrl = b.url,
                            providerId = b.apiName
                        )
                    )
                )
            }
        }

        // 2. Local Media
        if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.LOCAL) {
            runCatching {
                val localMovies = CineFolderMetadataManager.getAllLocalMovies(context)
                localMovies.forEach { m ->
                    items.add(
                        UnifiedLibraryCardItem(
                            id = "local_${m.videoFilePath}",
                            title = m.title,
                            subtitle = "Local • ${if (m.premiered.isNotBlank()) m.premiered else "Movie"}",
                            posterUrl = m.posterPath,
                            tab = LibraryTab.LOCAL,
                            directPathOrUrl = m.videoFilePath
                        )
                    )
                }
                val localTv = CineFolderMetadataManager.getAllLocalTvShows(context)
                localTv.forEach { tv ->
                    items.add(
                        UnifiedLibraryCardItem(
                            id = "local_tv_${tv.folderPath}",
                            title = tv.title,
                            subtitle = "Local Series",
                            posterUrl = tv.posterPath,
                            tab = LibraryTab.LOCAL,
                            directPathOrUrl = tv.folderPath
                        )
                    )
                }
            }
        }

        // 3. Network Connections
        if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.NETWORK) {
            runCatching {
                val conns = database.networkConnectionDao().getAllConnectionsList()
                conns.forEach { conn ->
                    items.add(
                        UnifiedLibraryCardItem(
                            id = "net_${conn.id}",
                            title = conn.name,
                            subtitle = "${conn.protocol.name} • ${conn.host}",
                            tab = LibraryTab.NETWORK,
                            directPathOrUrl = conn.path
                        )
                    )
                }
            }
        }

        // 4. Downloads
        if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.DOWNLOADS) {
            downloads.forEach { dl ->
                items.add(
                    UnifiedLibraryCardItem(
                        id = "dl_${dl.id}",
                        title = dl.getDisplayName(),
                        subtitle = "Downloaded (${dl.status.name})",
                        posterUrl = dl.posterUrl,
                        tab = LibraryTab.DOWNLOADS,
                        directPathOrUrl = dl.localPath
                    )
                )
            }
        }

        // 5. History
        if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.HISTORY) {
            runCatching {
                val recents = database.recentlyPlayedDao().getRecentlyPlayed(30)
                recents.forEach { r ->
                    items.add(
                        UnifiedLibraryCardItem(
                            id = "rec_${r.id}",
                            title = r.videoTitle ?: r.fileName,
                            subtitle = "Watched • ${r.fileName}",
                            tab = LibraryTab.HISTORY,
                            directPathOrUrl = r.filePath
                        )
                    )
                }
            }
        }

        libraryItems = items
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unified Media Library", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("unified_library_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = LibraryTab.values().indexOf(selectedTab),
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                LibraryTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.displayName) }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (libraryItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No items in ${selectedTab.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(libraryItems, key = { it.id }) { item ->
                        UnifiedLibraryGridCard(
                            item = item,
                            onClick = {
                                if (item.cloudStreamRequest != null) {
                                    if (onOpenStreamRequest != null) {
                                        onOpenStreamRequest(item.cloudStreamRequest)
                                    } else {
                                        MediaUtils.playFile(
                                            source = "ext_stream:${item.cloudStreamRequest.providerId}::${item.cloudStreamRequest.dataUrl}",
                                            context = context,
                                            launchSource = "unified_library",
                                            title = item.title,
                                            posterUrl = item.posterUrl,
                                            sourceType = "extension"
                                        )
                                    }
                                } else if (!item.directPathOrUrl.isNullOrBlank()) {
                                    val file = File(item.directPathOrUrl)
                                    if (file.exists()) {
                                        MediaUtils.playFile(
                                            source = file.absolutePath,
                                            context = context,
                                            launchSource = "unified_library",
                                            title = item.title,
                                            posterUrl = item.posterUrl,
                                            sourceType = "local"
                                        )
                                    } else {
                                        Toast.makeText(context, "Opening ${item.title}...", Toast.LENGTH_SHORT).show()
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

@Composable
fun UnifiedLibraryGridCard(
    item: UnifiedLibraryCardItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("lib_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = item.tab.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
