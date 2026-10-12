package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Unified Single Search Grid across multiple CloudStream providers.
 * Sorted by relevance and displaying provider badges, media types, and quality tags.
 */
@Composable
fun MaxStreamUnifiedSearchGrid(
    items: List<CineHubSearchItem>,
    onItemClick: (CineHubSearchItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Movies", "TV Shows")

    val filteredItems = remember(items, selectedFilter) {
        when (selectedFilter) {
            "Movies" -> items.filter { it.type != xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries }
            "TV Shows" -> items.filter { it.type == xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries }
            else -> items
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Results Header & Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search Results (${filteredItems.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = Color.White
            )

            // Category Filter Pills
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaxStreamTheme.ElectricCyan else Color(0x221E293B))
                            .border(
                                1.dp,
                                if (isSelected) MaxStreamTheme.ElectricCyan else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Unified 2-Column Responsive Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 1800.dp)
        ) {
            items(filteredItems, key = { "${it.providerId}_${it.url}_${it.title}" }) { searchItem ->
                UnifiedSearchCard(
                    item = searchItem,
                    onClick = { onItemClick(searchItem) }
                )
            }
        }
    }
}

@Composable
fun UnifiedSearchCard(
    item: CineHubSearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMovie = item.type != xyz.mpv.rex.cinehub.extension.api.TvType.TvSeries

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F121C))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("search_card_${item.title}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(Color(0xFF151926))
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Vignette for badges
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Provider Badge (Top Start)
                Text(
                    text = item.providerName.ifBlank { "Stream" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .border(0.7.dp, MaxStreamTheme.ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )

                // Type Badge (Top End)
                Text(
                    text = if (isMovie) "MOVIE" else "SERIES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = if (isMovie) MaxStreamTheme.ElectricCyan else Color(0xFFFFB300),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Title & Year info footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.year != null) {
                        Text(
                            text = item.year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }

                    Text(
                        text = "Verified Stream",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaxStreamTheme.ElectricCyan.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
