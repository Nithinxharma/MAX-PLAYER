package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
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
import xyz.mpv.rex.cinehub.extension.model.LibraryItem
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable

/**
 * Premium OTT 'My Watchlist' Section:
 * Displays user's bookmarked & planned media in an adaptive mosaic / glassmorphism presentation
 * placed directly underneath the Continue Watching area.
 */
@Composable
fun MaxStreamWatchlistSection(
    items: List<LibraryItem>,
    modifier: Modifier = Modifier,
    onItemClick: (LibraryItem) -> Unit,
    onExploreClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("maxstream_watchlist_section")
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "Watchlist",
                            tint = MaxStreamTheme.CrimsonAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "My Watchlist",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = primaryTextColor
                )
                if (items.isNotEmpty()) {
                    Surface(
                        shape = MaxStreamTheme.BadgeShape,
                        color = if (isDark) Color(0x33FFFFFF) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${items.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (items.isNotEmpty()) {
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = MaxStreamTheme.ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (items.isEmpty()) {
            // Premium Glass Empty State Card
            MaxStreamGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = if (isDark) Color(0x221E2436) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x3328324E) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your Watchlist is empty",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Save movies and TV shows from details to track what to watch next.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 15.sp
                            ),
                            color = mutedTextColor
                        )
                    }
                }
            }
        } else {
            // Adaptive Horizontal Mosaic Rail
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watchlist_mosaic_rail")
            ) {
                items(
                    items = items,
                    key = { "${it.apiName}_${it.url}" }
                ) { libraryItem ->
                    MaxStreamWatchlistCard(
                        item = libraryItem,
                        onClick = { onItemClick(libraryItem) }
                    )
                }
            }
        }
    }
}

/**
 * Glassmorphic OTT Watchlist Poster Card:
 * - Frosted glass card surface
 * - TMDB High-Res Poster artwork
 * - Media type badge (MOVIE, TV SERIES, ANIME)
 * - Watch status indicator
 * - Dynamic interactive hover & touch spring scale
 */
@Composable
fun MaxStreamWatchlistCard(
    item: LibraryItem,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 135.dp,
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
        label = "watchlist_card_scale"
    )

    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    val elevatedSurface = if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val glassBorder = if (isHighlighted) {
        MaxStreamTheme.CrimsonAccent
    } else if (isDark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    }

    val typeLabel = when (item.type) {
        1 -> "TV"
        2 -> "ANIME"
        else -> "MOVIE"
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .zIndex(if (isHighlighted) 10f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("watchlist_card_${item.url.take(15)}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
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
                .background(elevatedSurface)
        ) {
            // Poster Artwork
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
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
                        tint = mutedTextColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Cinematic Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Top Badges: Media Type
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.70f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // Top Right: Bookmark badge
            Surface(
                shape = CircleShape,
                color = MaxStreamTheme.CrimsonAccent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Saved",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Center Play Icon on Hover/Focus
            if (isHighlighted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaxStreamTheme.CrimsonAccent,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.5.sp,
                lineHeight = 15.sp
            ),
            color = if (isHighlighted) MaxStreamTheme.CrimsonAccent else primaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Subtitle (Provider / Source)
        Text(
            text = item.apiName.uppercase(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = mutedTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
