package xyz.mpv.rex.ui.browser.shorts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.youtube.data.InvidiousClient
import xyz.mpv.rex.youtube.model.YoutubeVideo
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.cinehub.failover.StreamHealthResolver
import xyz.mpv.rex.cinehub.failover.StreamCandidate
import xyz.mpv.rex.utils.media.MediaUtils
import androidx.compose.ui.platform.LocalContext
import xyz.mpv.rex.youtube.ui.SkeletonLoadingGrid
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

object OnlineShortsGridScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        var videoList by remember { mutableStateOf<List<YoutubeVideo>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            isLoading = true
            videoList = InvidiousClient.fetchShorts()
            isLoading = false
        }

        Scaffold(
            topBar = {
                BrowserTopBar(
                    title = "RexShorts",
                    isInSelectionMode = false,
                    selectedCount = 0,
                    totalCount = 0,
                    onCancelSelection = {},
                    isHomeScreen = true,
                    onSearchClick = {}
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    SkeletonLoadingGrid()
                } else if (videoList.isEmpty()) {
                    Text("No online shorts found.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(videoList, key = { it.videoId }) { video ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val candidates = InvidiousClient.fetchStreamCandidates(video.videoId)
                                            val ranked = StreamHealthResolver.resolveAndRankCandidates(candidates)
                                            val primary = (ranked.firstOrNull() ?: candidates.firstOrNull() ?: StreamCandidate(
                                                url = "https://www.youtube.com/watch?v=${video.videoId}",
                                                name = video.title,
                                                quality = "Auto",
                                                isM3u8 = false,
                                                headers = emptyMap()
                                            )).copy(name = video.title)
                                            val backups = (if (ranked.isNotEmpty()) ranked.drop(1) else candidates.drop(1)).map { it.copy(name = video.title) }
                                            MediaUtils.playStreamWithFailover(
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
                            ) {
                                AsyncImage(
                                    model = video.getBestThumbnailUrl(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(9f / 16f)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
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
