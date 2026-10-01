package xyz.mpv.rex.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import coil.request.ImageRequest
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

data class FeaturedMedia(
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val streamUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayMedia: (String, String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onOpenTestCenter: () -> Unit
) {
    val context = LocalContext.current

    val featuredList = listOf(
        FeaturedMedia(
            title = "Big Buck Bunny",
            subtitle = "4K Animation • Open Source",
            imageUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        FeaturedMedia(
            title = "Tears of Steel",
            subtitle = "Sci-Fi • CloudStream Core",
            imageUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=800",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        )
    )

    val trendingMovies = listOf(
        FeaturedMedia(
            title = "Elephants Dream",
            subtitle = "Movie • 2024",
            imageUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        FeaturedMedia(
            title = "For Bigger Blazes",
            subtitle = "Action • 1080p",
            imageUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        ),
        FeaturedMedia(
            title = "For Bigger Escape",
            subtitle = "Thriller • 4K",
            imageUrl = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=500",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MAX",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaxStreamTheme.CrimsonAccent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STREAM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaxStreamTheme.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaxStreamTheme.TextPrimary
                        )
                    }
                    IconButton(onClick = onOpenTestCenter) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Test Center",
                            tint = MaxStreamTheme.ElectricCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaxStreamTheme.MidnightSurface
                )
            )
        },
        containerColor = MaxStreamTheme.AbyssBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hero Banner
            item {
                val hero = featuredList.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clickable { onPlayMedia(hero.title, hero.streamUrl) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(hero.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = hero.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaxStreamTheme.AbyssBackground.copy(alpha = 0.5f),
                                        MaxStreamTheme.AbyssBackground
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("FEATURED STREAM", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaxStreamTheme.CrimsonAccent,
                                labelColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hero.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = hero.subtitle,
                            fontSize = 13.sp,
                            color = MaxStreamTheme.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onPlayMedia(hero.title, hero.streamUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watch Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Test Center Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onOpenTestCenter() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaxStreamTheme.ElectricCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CS3 Query Terminal & Inspector",
                                fontWeight = FontWeight.Bold,
                                color = MaxStreamTheme.TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Debug providers, test search queries, and inspect raw Logcat in real time.",
                                fontSize = 12.sp,
                                color = MaxStreamTheme.TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaxStreamTheme.ElectricCyan
                        )
                    }
                }
            }

            // Trending Streams Row
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Trending CloudStream Media",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(trendingMovies) { media ->
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { onPlayMedia(media.title, media.streamUrl) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder)
                            ) {
                                Column {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(media.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = media.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                    )
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = media.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaxStreamTheme.TextPrimary
                                        )
                                        Text(
                                            text = media.subtitle,
                                            fontSize = 11.sp,
                                            color = MaxStreamTheme.TextSecondary
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
