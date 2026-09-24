package xyz.mpv.rex.tv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.tv.MaxStreamTvManager
import xyz.mpv.rex.tv.model.TvStreamItem
import xyz.mpv.rex.tv.ui.components.TvContentRail
import xyz.mpv.rex.tv.ui.components.TvEpisodeRail
import xyz.mpv.rex.tv.ui.components.TvHeroBanner
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.profile.ProfileScreen
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Dedicated MaxStream TV UI Screen.
 * 10-foot TV & Leanback optimized interface with rich glassmorphism, accessible large play buttons,
 * episode shelves, and direct mobile-hotspot-to-TV streaming integration.
 */
@Serializable
object MaxStreamTvScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val scope = rememberCoroutineScope()

        var phoneVideos by remember { mutableStateOf<List<TvStreamItem>>(emptyList()) }
        var trendingMovies by remember { mutableStateOf<List<TvStreamItem>>(emptyList()) }
        var sampleEpisodes by remember { mutableStateOf<List<TvStreamItem>>(emptyList()) }
        var featuredHeroItem by remember { mutableStateOf<TvStreamItem?>(null) }
        var selectedEpisodeIdx by remember { mutableIntStateOf(0) }

        // Initialize TV Manager
        LaunchedEffect(Unit) {
            MaxStreamTvManager.initialize(context)

            // Load phone videos in background
            scope.launch(Dispatchers.IO) {
                try {
                    val folders = MediaFileRepository.getAllVideoFolders(context)
                    val allVideos = folders.flatMap { folder ->
                        MediaFileRepository.getVideosInFolder(context, folder.bucketId)
                    }
                    val streamItems = allVideos.map { v ->
                        TvStreamItem(
                            id = v.id.toString(),
                            title = if (v.title.isNotBlank()) v.title else v.displayName,
                            subtitle = "${v.resolution} • ${v.durationFormatted}",
                            durationMs = v.duration,
                            uriString = v.uri.toString(),
                            mimeType = if (v.mimeType.isNotBlank()) v.mimeType else "video/mp4",
                            fileSize = v.size,
                            isLocalFile = true
                        )
                    }
                    withContext(Dispatchers.Main) {
                        phoneVideos = streamItems
                        MaxStreamTvManager.getServer()?.setLibrary(streamItems)

                        if (featuredHeroItem == null && streamItems.isNotEmpty()) {
                            featuredHeroItem = streamItems.first()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Create initial curated trending & show data
            val defaultShowEpisodes = listOf(
                TvStreamItem(
                    id = "ep_1",
                    title = "The Awakening",
                    subtitle = "Episode 1",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    durationMs = 3200000L,
                    uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    posterUrl = "https://image.tmdb.org/t/p/w780/9cqNxx0GxF0bflZmeSMuL5tnGzr.jpg",
                    overview = "An unexpected event changes everything in the kingdom.",
                    isLocalFile = false
                ),
                TvStreamItem(
                    id = "ep_2",
                    title = "Into the Shadows",
                    subtitle = "Episode 2",
                    seasonNumber = 1,
                    episodeNumber = 2,
                    durationMs = 2950000L,
                    uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    posterUrl = "https://image.tmdb.org/t/p/w780/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                    overview = "A secret journey begins through uncharted territories.",
                    isLocalFile = false
                ),
                TvStreamItem(
                    id = "ep_3",
                    title = "The Convergence",
                    subtitle = "Episode 3",
                    seasonNumber = 1,
                    episodeNumber = 3,
                    durationMs = 3420000L,
                    uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    posterUrl = "https://image.tmdb.org/t/p/w780/393vhJJa4v4w69t5p9pL0g6xW8V.jpg",
                    overview = "Allies reunite for the ultimate confrontation.",
                    isLocalFile = false
                )
            )
            sampleEpisodes = defaultShowEpisodes

            val sampleTrending = listOf(
                TvStreamItem(
                    id = "m_1",
                    title = "Interstellar Odyssey",
                    subtitle = "4K HDR • Sci-Fi",
                    durationMs = 7200000L,
                    uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    posterUrl = "https://image.tmdb.org/t/p/w780/gKkl37BQuKTanygYQG1pyYgLVgf.jpg",
                    overview = "A breathtaking journey across the stars to find humanity's new home.",
                    releaseYear = "2024",
                    rating = 8.9,
                    isLocalFile = false
                ),
                TvStreamItem(
                    id = "m_2",
                    title = "Cyber Horizon",
                    subtitle = "Action / Thriller",
                    durationMs = 6400000L,
                    uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    posterUrl = "https://image.tmdb.org/t/p/w780/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg",
                    overview = "In a neon-drenched metropolis, an agent uncovers a global conspiracy.",
                    releaseYear = "2025",
                    rating = 8.6,
                    isLocalFile = false
                )
            )
            trendingMovies = sampleTrending

            if (featuredHeroItem == null) {
                featuredHeroItem = defaultShowEpisodes.first()
            }
        }

        fun launchLocalPlayer(item: TvStreamItem) {
            val uri = Uri.parse(item.uriString)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setClass(context, PlayerActivity::class.java)
                putExtra("internal_launch", true)
                putExtra("title", item.title)
                putExtra("filename", item.title)
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        BackHandler {
            backstack.removeLastOrNull()
        }

        Scaffold(
            containerColor = MaxStreamTheme.AbyssBackground,
            topBar = {
                // TV TopBar with Glass Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .maxStreamGlass(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = MaxStreamTheme.GlassSurface,
                            borderColor = MaxStreamTheme.GlassBorder
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { backstack.removeLastOrNull() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaxStreamTheme.GlassSurfaceActive)
                                    .maxStreamTvFocusable(onClick = { backstack.removeLastOrNull() })
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "MAX",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = (-0.5).sp
                                        ),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "STREAM",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = (-0.5).sp
                                        ),
                                        color = MaxStreamTheme.CrimsonAccent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaxStreamTheme.ElectricCyan)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "TV MODE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }

                        // Right actions (Stream on TV Hotspot trigger + Profile)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Hotspot Stream Status Button
                            val netInfo by MaxStreamTvManager.networkInfo.collectAsState()
                            Button(
                                onClick = {
                                    featuredHeroItem?.let { item ->
                                        MaxStreamTvManager.playOnTv(context, item)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (netInfo?.isHotspot == true) MaxStreamTheme.ElectricCyan else MaxStreamTheme.GlassSurfaceActive,
                                    contentColor = if (netInfo?.isHotspot == true) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (netInfo?.isHotspot == true) Icons.Default.WifiTethering else Icons.Default.Tv,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (netInfo?.isHotspot == true) "HOTSPOT TV READY" else "STREAM TO TV",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }

                            // Profile Avatar
                            IconButton(
                                onClick = { backstack.add(ProfileScreen) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaxStreamTheme.CrimsonAccent)
                            ) {
                                Text(
                                    text = "M",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
            ) {
                // 1. Prominent TV Hero Banner (Immediate Play Movie / Play Episode)
                item {
                    featuredHeroItem?.let { hero ->
                        TvHeroBanner(
                            item = hero,
                            onPlayLocal = { item -> launchLocalPlayer(item) },
                            onPlayOnTv = { item -> MaxStreamTvManager.playOnTv(context, item) }
                        )
                    }
                }

                // 2. TV Show Episode Shelf (Easy accessible episodes & instant launch)
                if (sampleEpisodes.isNotEmpty()) {
                    item {
                        TvEpisodeRail(
                            title = "SEASON 1 EPISODES",
                            episodes = sampleEpisodes,
                            selectedEpisodeIndex = selectedEpisodeIdx,
                            onEpisodePlay = { ep ->
                                selectedEpisodeIdx = sampleEpisodes.indexOf(ep).coerceAtLeast(0)
                                featuredHeroItem = ep
                                launchLocalPlayer(ep)
                            },
                            onEpisodePlayOnTv = { ep ->
                                selectedEpisodeIdx = sampleEpisodes.indexOf(ep).coerceAtLeast(0)
                                featuredHeroItem = ep
                                MaxStreamTvManager.playOnTv(context, ep)
                            }
                        )
                    }
                }

                // 3. Local Phone Videos (Ready to Stream directly to TV over Hotspot with ZERO LAG)
                if (phoneVideos.isNotEmpty()) {
                    item {
                        TvContentRail(
                            title = "📱 MY PHONE VIDEOS (STREAM TO TV WITHOUT LAG)",
                            items = phoneVideos,
                            accentColor = MaxStreamTheme.ElectricCyan,
                            onItemSelect = { video ->
                                featuredHeroItem = video
                                launchLocalPlayer(video)
                            },
                            onItemPlayOnTv = { video ->
                                featuredHeroItem = video
                                MaxStreamTvManager.playOnTv(context, video)
                            }
                        )
                    }
                }

                // 4. Featured & Trending Movies Rail
                if (trendingMovies.isNotEmpty()) {
                    item {
                        TvContentRail(
                            title = "🔥 TRENDING MOVIES",
                            items = trendingMovies,
                            accentColor = MaxStreamTheme.CrimsonAccent,
                            onItemSelect = { movie ->
                                featuredHeroItem = movie
                                launchLocalPlayer(movie)
                            },
                            onItemPlayOnTv = { movie ->
                                featuredHeroItem = movie
                                MaxStreamTvManager.playOnTv(context, movie)
                            }
                        )
                    }
                }
            }
        }

        // TV Bottom Sheet (Hotspot Zero-Lag Streamer & Remote Control)
        if (MaxStreamTvManager.isBottomSheetVisible) {
            MaxStreamTvBottomSheet(
                onDismissRequest = { MaxStreamTvManager.hideBottomSheet() }
            )
        }
    }
}
