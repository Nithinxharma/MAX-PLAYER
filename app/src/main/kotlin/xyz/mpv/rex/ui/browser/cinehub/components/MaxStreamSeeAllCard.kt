package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable

/**
 * Liquid Glass "Show All" Card displayed as the 16th item in provider content rows.
 */
@Composable
fun MaxStreamSeeAllCard(
    remainingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 145.dp,
    aspectRatio: Float = 2f / 3f
) {
    val isDark = isSystemInDarkTheme()
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
        label = "see_all_card_scale"
    )

    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val cardBg = if (isDark) {
        Color(0x331E293B)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    }
    val borderColor = if (isHighlighted) {
        MaxStreamTheme.CrimsonAccent
    } else if (isDark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .zIndex(if (isHighlighted) 10f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("maxstream_see_all_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
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
                .background(cardBg)
                .border(BorderStroke(1.2.dp, borderColor), MaxStreamTheme.CardShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // Radial Glass Glow Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaxStreamTheme.CrimsonAccent.copy(alpha = if (isHighlighted) 0.25f else 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                // Glass Circle Icon
                Surface(
                    shape = CircleShape,
                    color = if (isHighlighted) MaxStreamTheme.CrimsonAccent else MaxStreamTheme.CrimsonAccent.copy(alpha = 0.20f),
                    shadowElevation = if (isHighlighted) 8.dp else 2.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Show All",
                            tint = if (isHighlighted) Color.White else MaxStreamTheme.CrimsonAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Show All",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = if (isHighlighted) MaxStreamTheme.CrimsonAccent else primaryTextColor,
                    textAlign = TextAlign.Center
                )

                if (remainingCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "+$remainingCount more",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaxStreamTheme.CrimsonAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "View Catalog",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
