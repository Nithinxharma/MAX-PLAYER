package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

data class MediaInfoDetails(
    val id: String,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val year: String?,
    val runtime: String?,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val providerBadge: String? = null,
    val rawPayload: Any? = null
)

/**
 * Flagship Media Info Sheet.
 * Displays high-impact imagery, title, genres, rating, cast and actions.
 * Never displays ugly raw URLs or technical logs to users.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaxStreamMediaInfoSheet(
    details: MediaInfoDetails,
    onDismissRequest: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isBookmarked by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaxStreamTheme.MidnightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
        modifier = modifier.testTag("media_info_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
        ) {
            // Backdrop Header with gradient & close button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    AsyncImage(
                        model = details.backdropUrl ?: details.posterUrl,
                        contentDescription = details.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f),
                                        MaxStreamTheme.MidnightSurface
                                    )
                                )
                            )
                    )

                    // Top Action: Close Button
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            // Main Details Body
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title & Star Rating
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = details.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    lineHeight = 28.sp
                                ),
                                color = Color.White
                            )

                            // Year & Runtime & Provider Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                if (!details.year.isNullOrBlank()) {
                                    Text(
                                        text = details.year,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaxStreamTheme.TextSecondary
                                    )
                                }
                                if (!details.runtime.isNullOrBlank()) {
                                    Text(
                                        text = "• ${details.runtime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaxStreamTheme.TextSecondary
                                    )
                                }
                                if (!details.providerBadge.isNullOrBlank()) {
                                    Surface(
                                        shape = MaxStreamTheme.BadgeShape,
                                        color = MaxStreamTheme.GlassSurfaceActive
                                    ) {
                                        Text(
                                            text = details.providerBadge,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaxStreamTheme.ElectricCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (details.rating != null && details.rating > 0.0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = MaxStreamTheme.AmberGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = String.format("%.1f", details.rating),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons: Play + My List
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Button(
                            onClick = {
                                onPlayClick()
                                onDismissRequest()
                            },
                            shape = MaxStreamTheme.ButtonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaxStreamTheme.CrimsonAccent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .shadow(8.dp, MaxStreamTheme.ButtonShape, spotColor = MaxStreamTheme.CrimsonAccent)
                                .testTag("sheet_play_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Watch Stream",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        FilledTonalButton(
                            onClick = { isBookmarked = !isBookmarked },
                            shape = MaxStreamTheme.ButtonShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(46.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), MaxStreamTheme.ButtonShape)
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) MaxStreamTheme.CrimsonAccent else Color.White
                            )
                        }
                    }

                    // Genres Pills
                    if (details.genres.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            details.genres.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaxStreamTheme.ElevatedSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaxStreamTheme.TextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Overview
                    if (!details.overview.isNullOrBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Overview",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = details.overview,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Cast list
                    if (details.cast.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Cast & Crew",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = details.cast.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = MaxStreamTheme.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
