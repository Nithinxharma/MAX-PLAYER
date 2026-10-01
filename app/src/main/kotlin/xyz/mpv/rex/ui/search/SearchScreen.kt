package xyz.mpv.rex.ui.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPlayMedia: (String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Pair<String, SearchResponse>>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractingTitle by remember { mutableStateOf("") }

    fun doSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        coroutineScope.launch {
            isSearching = true
            val map = ProviderRegistry.searchAll(query)
            val combined = mutableListOf<Pair<String, SearchResponse>>()
            map.forEach { (prov, list) ->
                list.forEach { item ->
                    combined.add(prov to item)
                }
            }
            searchResults = combined
            isSearching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search movies, anime, series...", color = MaxStreamTheme.TextMuted, fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { doSearch() }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaxStreamTheme.ElectricCyan
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaxStreamTheme.ElevatedSurface,
                            unfocusedContainerColor = MaxStreamTheme.ElevatedSurface,
                            focusedBorderColor = MaxStreamTheme.ElectricCyan,
                            unfocusedBorderColor = MaxStreamTheme.GlassBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaxStreamTheme.MidnightSurface
                )
            )
        },
        containerColor = MaxStreamTheme.AbyssBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaxStreamTheme.CrimsonAccent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Querying CloudStream extensions...",
                            color = MaxStreamTheme.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaxStreamTheme.TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Search across all active providers" else "No results found for '$searchQuery'",
                            color = MaxStreamTheme.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults) { (providerName, item) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        isExtracting = true
                                        extractingTitle = item.name
                                        val details = ProviderRegistry.loadDetails(providerName, item.url)
                                        var streamUrlToPlay: String? = null

                                        val playData = when (details) {
                                            is com.lagradost.cloudstream3.MovieLoadResponse -> details.dataUrl
                                            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> details.episodes.firstOrNull()?.data ?: item.url
                                            else -> item.url
                                        }

                                        ProviderRegistry.extractLinks(
                                            providerName = providerName,
                                            data = playData,
                                            onSubtitle = {},
                                            onLink = { link ->
                                                if (streamUrlToPlay == null) {
                                                    streamUrlToPlay = link.url
                                                }
                                            }
                                        )

                                        isExtracting = false
                                        if (streamUrlToPlay != null) {
                                            onPlayMedia(item.name, streamUrlToPlay!!)
                                        } else {
                                            Toast.makeText(context, "No stream links found for ${item.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(MaxStreamTheme.ElevatedSurface)
                                ) {
                                    if (!item.posterUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(item.posterUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = MaxStreamTheme.TextMuted,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }

                                    // Provider badge overlay
                                    Surface(
                                        color = MaxStreamTheme.AbyssBackground.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = providerName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaxStreamTheme.ElectricCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaxStreamTheme.TextPrimary
                                    )
                                    Text(
                                        text = item.type?.name ?: "Media",
                                        fontSize = 11.sp,
                                        color = MaxStreamTheme.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Extracting Stream Dialog
            if (isExtracting) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Extracting Links", color = MaxStreamTheme.TextPrimary, fontSize = 16.sp) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = MaxStreamTheme.CrimsonAccent, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Resolving streams for $extractingTitle...", color = MaxStreamTheme.TextSecondary, fontSize = 13.sp)
                        }
                    },
                    containerColor = MaxStreamTheme.MidnightSurface
                )
            }
        }
    }
}
