package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import xyz.mpv.rex.cinehub.model.MovieItem
import xyz.mpv.rex.cinehub.model.TvShowItem
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
    
    // We can infer metadata from the playing file path
    val currentFilePath = `is`.xyz.mpv.MPVLib.getPropertyString("path") ?: ""
    val title = viewModel.mediaTitle.collectAsState().value ?: File(currentFilePath).nameWithoutExtension

    var movieData by remember { mutableStateOf<MovieItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentFilePath) {
        isLoading = true
        // Basic resolution logic: use online scraper based on the file name.
        val fetched = CineOnlineScraper.getOrFetchMovie(context, File(currentFilePath).name)
        if (fetched != null) {
            movieData = fetched
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (movieData != null) {
            val movie = movieData!!
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        AsyncImage(
                            model = movie.backdropPath ?: movie.posterPath,
                            contentDescription = "Backdrop",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
                        
                        if (movie.logoPath != null) {
                            AsyncImage(
                                model = movie.logoPath,
                                contentDescription = "Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 16.dp).width(160.dp).height(80.dp)
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            AsyncImage(
                                model = movie.posterPath ?: android.R.drawable.ic_menu_gallery,
                                contentDescription = movie.title,
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Gray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(movie.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                if (movie.tagline.isNotBlank()) {
                                    Text("\"${movie.tagline}\"", style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("★ ${movie.userRating} | ${movie.premiered} | ${movie.runtime} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(movie.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        isLoading = true
                                        scope.launch {
                                            val refreshed = CineOnlineScraper.getOrFetchMovie(context, File(currentFilePath).name, movie.tmdbId, forceRefresh = true)
                                            if (refreshed != null) {
                                                movieData = refreshed
                                            }
                                            isLoading = false
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Refresh Metadata", fontSize = 12.sp)
                                }
                            }
                        }

                        if (movie.actors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Cast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                items(movie.actors) { actor ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                                        AsyncImage(
                                            model = actor.thumbUrl, contentDescription = actor.name, contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(actor.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                        if (actor.character.isNotBlank()) {
                                            Text(actor.character, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                        
                        movie.collection?.let { collection ->
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp))) {
                                AsyncImage(model = collection.backdropPath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Part of the", color = Color.White, fontSize = 12.sp)
                                    Text(collection.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Plot Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(movie.plot.ifEmpty { "No description available." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No metadata found for this media.", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
