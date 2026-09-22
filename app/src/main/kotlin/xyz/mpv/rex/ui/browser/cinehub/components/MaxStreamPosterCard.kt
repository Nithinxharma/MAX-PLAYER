package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable

/**
 * Flagship Max Stream Poster Card.
 * Features:
 * - Fluid spring scale & hover / D-pad focus elevation
 * - Dynamic lighting & soft halo glow on active
 * - Multi-stop gradient overlay for high text contrast
 * - Match rating & 4K quality badges
 * - Optional progress bar for continue watching
 */
@Composable
fun MaxStreamPosterCard(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: Double? = null,
    qualityBadge: String? = "4K HDR",
    watchProgress: Float? = null,
    cardWidth: Dp = 150.dp,
    aspectRatio: Float = 2f / 3f,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "poster_card_scale"
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .zIndex(if (isHighlighted) 10f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("maxstream_poster_${title.take(15)}")
    ) {
        // Poster Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .shadow(
                    elevation = if (isHighlighted) 16.dp else 6.dp,
                    shape = MaxStreamTheme.CardShape,
                    spotColor = if (isHighlighted) MaxStreamTheme.CrimsonAccent else Color.Black
                )
                .maxStreamTvFocusable(
                    onClick = onClick,
                    focusedScale = 1.0f, // Already scaled parent
                    shape = MaxStreamTheme.CardShape,
                    interactionSource = interactionSource
                )
                .clip(MaxStreamTheme.CardShape)
                .background(MaxStreamTheme.ElevatedSurface)
        ) {
            // Poster Artwork
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Cinematic Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.90f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Top Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!qualityBadge.isNullOrBlank()) {
                    Surface(
                        shape = MaxStreamTheme.BadgeShape,
                        color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.90f)
                    ) {
                        Text(
                            text = qualityBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                if (rating != null && rating > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.70f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaxStreamTheme.AmberGold,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Highlight Play Icon on Hover/Focus
            if (isHighlighted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaxStreamTheme.CrimsonAccent,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Continue Watching Progress
            if (watchProgress != null && watchProgress > 0f) {
                LinearProgressIndicator(
                    progress = { watchProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaxStreamTheme.CrimsonAccent,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title & Subtitle Below Card
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            color = if (isHighlighted) MaxStreamTheme.CrimsonAccent else MaxStreamTheme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = MaxStreamTheme.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
