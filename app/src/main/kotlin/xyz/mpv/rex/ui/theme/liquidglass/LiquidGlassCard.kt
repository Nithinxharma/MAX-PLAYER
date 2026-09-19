package xyz.mpv.rex.ui.theme.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Liquid Glass Card with 28dp corners, frosted translucency, specular borders, and dynamic depth.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tintColor: Color? = null,
    alpha: Float = 0.74f,
    elevation: Dp = 14.dp,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val isGlass = isLiquidGlassActive()

    if (!isGlass) {
        // Standard Material 3 Card fallback when Liquid Glass is disabled
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = tintColor ?: MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box { content() }
            }
        } else {
            Card(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = tintColor ?: MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box { content() }
            }
        }
    } else {
        // Liquid Glass styling
        val surfaceModifier = modifier
            .liquidGlassInteractiveLift(interactionSource, pressedElevation = elevation + 6.dp, defaultElevation = elevation)
            .liquidGlassSurface(
                shape = shape,
                tintColor = tintColor,
                alpha = alpha,
                borderWidth = borderWidth,
                elevation = elevation
            )

        val finalModifier = if (onClick != null) {
            surfaceModifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
        } else {
            surfaceModifier
        }

        Box(
            modifier = finalModifier,
            content = content
        )
    }
}

/**
 * Elevated Liquid Glass Card for featured content and prominent highlights.
 */
@Composable
fun LiquidGlassElevatedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tintColor: Color? = null,
    elevation: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassCard(
        modifier = modifier,
        shape = shape,
        tintColor = tintColor,
        alpha = 0.82f,
        elevation = elevation,
        borderWidth = 1.25.dp,
        onClick = onClick,
        content = content
    )
}
