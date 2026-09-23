package xyz.mpv.rex.ui.browser.cinehub.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable
import xyz.mpv.rex.utils.media.MediaThumbnailUtils
import java.io.File

data class ContinueWatchingMediaItem(
    val id: String,
    val title: String,
    val episodeInfo: String? = null,
    val landscapeImageUrl: String?,
    val currentPositionSeconds: Long = 0,
    val totalDurationSeconds: Long = 0,
    val watchProgressFraction: Float = 0f,
    val rawPayload: Any? = null
) {
    val remainingTimeFormatted: String
        get() {
            if (totalDurationSeconds <= 0) return ""
            val remainingSec = (totalDurationSeconds - currentPositionSeconds).coerceAtLeast(0)
            val minutes = remainingSec / 60
            val hours = minutes / 60
            return when {
                hours > 0 -> "${hours}h ${minutes % 60}m left"
                minutes > 0 -> "${minutes}m left"
                else -> "${remainingSec}s left"
            }
        }

    val timestampFormatted: String
        get() {
            if (totalDurationSeconds <= 0) return ""
            return "${formatTime(currentPositionSeconds)} / ${formatTime(totalDurationSeconds)}"
        }

    private fun formatTime(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}

/**
 * Redesigned Continue Watching Landscape Card:
 * - 16:9 Landscape artwork with real frame thumbnail extraction for local video files
 * - Full TMDB fanart support for online streams
 * - Movie/Episode title and subtitle with light/dark adaptive text colors
 * - Current position, total duration, remaining time, watch %
 * - Slim Crimson progress bar
 * - Interactive spring scale and play icon on hover/focus
 */
@Composable
fun MaxStreamContinueWatchingCard(
    item: ContinueWatchingMediaItem,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 230.dp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cw_card_scale"
    )

    // Dynamically extract thumbnail for local videos if needed
    val localThumbnailBitmap by produceState<Bitmap?>(initialValue = null, item.landscapeImageUrl, item.id) {
        val imgUrl = item.landscapeImageUrl
        if (imgUrl != null && !imgUrl.startsWith("http://") && !imgUrl.startsWith("https://")) {
            val uri = if (imgUrl.startsWith("content://") || imgUrl.startsWith("file://")) {
                Uri.parse(imgUrl)
            } else {
                Uri.fromFile(File(imgUrl))
            }
            value = MediaThumbnailUtils.extractThumbnailOrCoverArt(context, uri)
        } else {
            value = null
        }
    }

    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .width(cardWidth)
            .zIndex(if (isHighlighted) 10f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("cw_card_${item.id.take(15)}")
    ) {
        // Landscape Card Surface (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(
                    elevation = if (isHighlighted) 14.dp else 4.dp,
                    shape = MaxStreamTheme.CardShape,
                    spotColor = if (isHighlighted) MaxStreamTheme.CrimsonAccent else Color.Black
                )
                .maxStreamTvFocusable(
                    onClick = onClick,
                    focusedScale = 1.0f,
                    shape = MaxStreamTheme.CardShape,
                    interactionSource = interactionSource
                )
                .clip(MaxStreamTheme.CardShape)
                .background(if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // Landscape Backdrop Artwork / Video Frame Thumbnail
            if (localThumbnailBitmap != null) {
                Image(
                    bitmap = localThumbnailBitmap!!.asImageBitmap(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val highResUrl = MaxStreamMetadataHelper.toHighResFanart(item.landscapeImageUrl) ?: item.landscapeImageUrl
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(highResUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Cinematic Multi-stop Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.20f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.50f),
                                Color.Black.copy(alpha = 0.92f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Top Badges: Remaining Time
            if (item.remainingTimeFormatted.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.70f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = item.remainingTimeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Center Play Icon on Hover/Focus
            if (isHighlighted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaxStreamTheme.CrimsonAccent,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Resume",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Progress Bar
            val progress = item.watchProgressFraction.coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaxStreamTheme.CrimsonAccent,
                trackColor = Color.White.copy(alpha = 0.20f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title and Episode Details
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            color = if (isHighlighted) MaxStreamTheme.CrimsonAccent else primaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!item.episodeInfo.isNullOrBlank()) {
                Text(
                    text = item.episodeInfo,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else if (item.timestampFormatted.isNotBlank()) {
                Text(
                    text = item.timestampFormatted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = mutedTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            val percent = (item.watchProgressFraction * 100).toInt().coerceIn(0, 100)
            if (percent > 0) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaxStreamTheme.CrimsonAccent
                )
            }
        }
    }
}

/**
 * Continue Watching Horizontal Scrollable Rail (Part 5).
 */
@Composable
fun MaxStreamContinueWatchingRail(
    items: List<ContinueWatchingMediaItem>,
    modifier: Modifier = Modifier,
    onItemClick: (ContinueWatchingMediaItem) -> Unit
) {
    if (items.isEmpty()) return
    val isDark = isSystemInDarkTheme()
    val headerColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("continue_watching_rail")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                ),
                color = headerColor
            )
            Surface(
                shape = MaxStreamTheme.BadgeShape,
                color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.20f)
            ) {
                Text(
                    text = "${items.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaxStreamTheme.CrimsonAccent,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                MaxStreamContinueWatchingCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

