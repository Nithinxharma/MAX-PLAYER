package xyz.mpv.rex.ui.browser.cinehub

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.model.LibraryItem
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

object CineHubLibraryScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val database = koinInject<MpvExDatabase>()
        val libraryDao = database.cineLibraryDao()
        val libraryItems by libraryDao.getAllLibraryItems().collectAsState(initial = emptyList())

        var selectedWatchStatus by remember { mutableStateOf("All") } // "All", "Watching", "Plan to Watch", "Completed", "Dropped"
        var searchQuery by remember { mutableStateOf("") }
        var selectedDetailItem by remember { mutableStateOf<Any?>(null) }

        val isDark = isSystemInDarkTheme()
        val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
        val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

        val watchStatusCategories = listOf(
            "All",
            "Watching",
            "Plan to Watch",
            "Completed",
            "Dropped"
        )

        val filteredItems = remember(libraryItems, selectedWatchStatus, searchQuery) {
            libraryItems.filter { item ->
                val matchesStatus = when (selectedWatchStatus) {
                    "All" -> true
                    "Watching" -> item.watchStatus == 1
                    "Plan to Watch" -> item.watchStatus == 0
                    "Completed" -> item.watchStatus == 2
                    "Dropped" -> item.watchStatus == 3
                    else -> true
                }
                val matchesQuery = if (searchQuery.isBlank()) true else {
                    item.title.contains(searchQuery, ignoreCase = true)
                }
                matchesStatus && matchesQuery
            }
        }

        if (selectedDetailItem != null) {
            CineDetailView(
                item = selectedDetailItem!!,
                onDismiss = { selectedDetailItem = null }
            )
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Color(0xFF0D0E12) else MaterialTheme.colorScheme.background)
                .padding(top = 16.dp)
                .testTag("cinehub_library_screen")
        ) {
            // Header Bar
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
                        shape = RoundedCornerShape(12.dp),
                        color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = "Movie Library",
                                tint = MaxStreamTheme.CrimsonAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Movie Library",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = primaryTextColor
                        )
                        Text(
                            text = "${libraryItems.size} items in collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter saved movies & series...", color = mutedTextColor) },
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Watch Status Filter Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(watchStatusCategories) { status ->
                    val isSelected = selectedWatchStatus == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedWatchStatus = status },
                        label = { Text(status, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                            selectedLabelColor = Color.White,
                            containerColor = if (isDark) Color(0x1FFFFFFF) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Grid
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching library items" else "Your Library is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Text(
                            text = "Bookmark movies or TV shows from details to add them to your collection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedTextColor,
                            textAlign = TextAlign.Center
                        )
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
                    items(filteredItems) { item ->
                        LibraryPosterCard(
                            item = item,
                            onClick = {
                                selectedDetailItem = item
                            },
                            onRemove = {
                                scope.launch(Dispatchers.IO) {
                                    libraryDao.deleteLibraryItem(item)
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
private fun LibraryPosterCard(
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
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant)
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

            // Watch status badge
            Surface(
                shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                color = MaxStreamTheme.CrimsonAccent,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
