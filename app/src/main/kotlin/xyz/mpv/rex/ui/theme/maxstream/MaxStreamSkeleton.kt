package xyz.mpv.rex.ui.theme.maxstream

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern Skeleton Loading System.
 * Replaces old spinners and progress bars with fluid shimmering placeholder geometry.
 */
fun Modifier.maxStreamShimmer(
    shape: Shape = RoundedCornerShape(12.dp),
    baseColor: Color? = null,
    highlightColor: Color? = null
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val defaultBase = if (isDark) Color(0xFF131824) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val defaultHighlight = if (isDark) Color(0xFF263048) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)

    val actualBase = baseColor ?: defaultBase
    val actualHighlight = highlightColor ?: defaultHighlight

    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            actualBase,
            actualHighlight,
            actualBase
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 350f, 350f)
    )

    this
        .clip(shape)
        .background(brush)
}

/**
 * Single Movie/Show Poster Skeleton Card.
 */
@Composable
fun MaxStreamSkeletonCard(
    modifier: Modifier = Modifier,
    cardWidth: Dp = 136.dp,
    aspectRatio: Float = 2f / 3f,
    shape: Shape = MaxStreamTheme.CardShape
) {
    Column(
        modifier = modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .maxStreamShimmer(shape = shape)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .maxStreamShimmer(shape = RoundedCornerShape(6.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(11.dp)
                .maxStreamShimmer(shape = RoundedCornerShape(4.dp))
        )
    }
}

/**
 * Horizontal Carousel Row of Skeleton Cards.
 */
@Composable
fun MaxStreamSkeletonRow(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
    cardWidth: Dp = 136.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Rail Title skeleton
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(160.dp)
                .height(20.dp)
                .maxStreamShimmer(shape = RoundedCornerShape(6.dp))
        )

        LazyRow(
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(itemCount) {
                MaxStreamSkeletonCard(cardWidth = cardWidth)
            }
        }
    }
}

/**
 * Hero Banner Skeleton Header.
 */
@Composable
fun MaxStreamSkeletonBanner(
    modifier: Modifier = Modifier,
    height: Dp = 380.dp,
    shape: Shape = RoundedCornerShape(0.dp)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .maxStreamShimmer(shape = shape)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(22.dp)
                    .maxStreamShimmer(shape = RoundedCornerShape(6.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(32.dp)
                    .maxStreamShimmer(shape = RoundedCornerShape(8.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(16.dp)
                    .maxStreamShimmer(shape = RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(44.dp)
                        .maxStreamShimmer(shape = MaxStreamTheme.ButtonShape)
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .maxStreamShimmer(shape = MaxStreamTheme.ButtonShape)
                )
            }
        }
    }
}

/**
 * Unified Media / File Item List Skeleton.
 */
@Composable
fun MaxStreamSkeletonListItem(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp, 44.dp)
                .maxStreamShimmer(shape = RoundedCornerShape(10.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(16.dp)
                    .maxStreamShimmer(shape = RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .maxStreamShimmer(shape = RoundedCornerShape(4.dp))
            )
        }
    }
}
