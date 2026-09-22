package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable
import kotlin.math.absoluteValue

data class HeroFeaturedMedia(
    val id: String,
    val title: String,
    val overview: String? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val rating: Double? = null,
    val matchPercentage: Int? = 98,
    val year: String? = "2024",
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val rawPayload: Any? = null
)

/**
 * Flagship Hero Experience for Max Stream.
 * Features:
 * - Dynamic animated breathing backdrop with subtle parallax drift
 * - Layered depth: Glassmorphism meta deck & badges
 * - D-Pad & TV Remote focus reaction: Smooth scaling, illuminated edge glow
 * - Play & More Info interactive triggers
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaxStreamHeroSection(
    featuredList: List<HeroFeaturedMedia>,
    onPlayClick: (HeroFeaturedMedia) -> Unit,
    onInfoClick: (HeroFeaturedMedia) -> Unit,
    modifier: Modifier = Modifier,
    isTvMode: Boolean = false
) {
    if (featuredList.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { featuredList.size }
    )

    // Auto-advance banner every 8 seconds if not touched
    LaunchedEffect(pagerState.pageCount) {
        if (featuredList.size > 1) {
            while (true) {
                delay(8000)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % featuredList.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    // Infinite breathing backdrop subtle motion
    val infiniteTransition = rememberInfiniteTransition(label = "hero_drift")
    val driftScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_scale"
    )
    val driftAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp)
            .testTag("maxstream_hero_section")
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = if (isTvMode) 32.dp else 16.dp),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTvMode) 380.dp else 300.dp)
        ) { page ->
            val media = featuredList[page]

            // Pager scale offset
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
            val absOffset = pageOffset.absoluteValue
            val baseScale = 1.0f - (absOffset * 0.04f)

            HeroCard(
                media = media,
                baseScale = baseScale,
                driftScale = driftScale,
                driftAlpha = driftAlpha,
                isTvMode = isTvMode,
                onPlayClick = { onPlayClick(media) },
                onInfoClick = { onInfoClick(media) }
            )
        }

        // Dot indicators
        if (featuredList.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayCount = featuredList.size.coerceAtMost(6)
                repeat(displayCount) { index ->
                    val isSelected = pagerState.currentPage % displayCount == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "hero_dot_w"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(4.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaxStreamTheme.CrimsonAccent
                                else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    media: HeroFeaturedMedia,
    baseScale: Float,
    driftScale: Float,
    driftAlpha: Float,
    isTvMode: Boolean,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    val tvScale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "hero_tv_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isHighlighted) 10f else 1f)
            .graphicsLayer {
                scaleX = baseScale * tvScale
                scaleY = baseScale * tvScale
            }
            .shadow(
                elevation = if (isHighlighted) 24.dp else 10.dp,
                shape = MaxStreamTheme.HeroCardShape,
                spotColor = if (isHighlighted) MaxStreamTheme.CrimsonAccent else Color.Black
            )
            .clip(MaxStreamTheme.HeroCardShape)
            .background(MaxStreamTheme.MidnightSurface)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isHighlighted) listOf(
                        Color.White.copy(alpha = 0.9f),
                        MaxStreamTheme.CrimsonAccent,
                        Color.White.copy(alpha = 0.3f)
                    ) else listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = MaxStreamTheme.HeroCardShape
            )
            .testTag("hero_card_${media.id}")
    ) {
        // Animated Backdrop Artwork
        AsyncImage(
            model = media.backdropUrl ?: media.posterUrl,
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = driftScale
                    scaleY = driftScale
                    alpha = driftAlpha
                }
        )

        // Cinematic Multi-Stop Gradient Scrim (Abyss Charcoal to transparent)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.65f),
                            MaxStreamTheme.AbyssBackground.copy(alpha = 0.96f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Side horizontal scrim for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Top Badges (Match %, 4K, HDR)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Match percentage badge
                if (media.matchPercentage != null && media.matchPercentage > 0) {
                    Surface(
                        shape = MaxStreamTheme.BadgeShape,
                        color = MaxStreamTheme.EmeraldLive.copy(alpha = 0.90f)
                    ) {
                        Text(
                            text = "${media.matchPercentage}% Match",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                // Year
                if (!media.year.isNullOrBlank()) {
                    Text(
                        text = media.year,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White.copy(alpha = 0.80f)
                    )
                }

                // 4K Ultra HD Badge
                Surface(
                    shape = MaxStreamTheme.BadgeShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "4K UHD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Star Rating
            if (media.rating != null && media.rating > 0.0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaxStreamTheme.AmberGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = String.format("%.1f", media.rating),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Deck: Title, Genres, Overview & Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (isTvMode) 28.sp else 22.sp,
                    lineHeight = if (isTvMode) 32.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.1.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Genre Tags
            if (media.genres.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    media.genres.take(3).forEach { genre ->
                        Text(
                            text = "• $genre",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaxStreamTheme.TextSecondary
                        )
                    }
                }
            }

            // Overview Synopsis
            if (!media.overview.isNullOrBlank()) {
                Text(
                    text = media.overview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons: Play (Crimson Glow) + More Info (Glass Pill)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                Button(
                    onClick = onPlayClick,
                    shape = MaxStreamTheme.ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxStreamTheme.CrimsonAccent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = MaxStreamTheme.ButtonShape,
                            spotColor = MaxStreamTheme.CrimsonAccent
                        )
                        .testTag("hero_play_${media.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }

                // More Info Button (Glass Surface)
                FilledTonalButton(
                    onClick = onInfoClick,
                    shape = MaxStreamTheme.ButtonShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.25f),
                            shape = MaxStreamTheme.ButtonShape
                        )
                        .testTag("hero_info_${media.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "More Info",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "More Info",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}
