package xyz.mpv.rex.ui.theme.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass Button with translucent surface, specular border, and elevation glow.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    tintColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val isGlass = isLiquidGlassActive()

    if (!isGlass) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .liquidGlassInteractiveLift(interactionSource, pressedElevation = 14.dp, defaultElevation = 6.dp)
                .liquidGlassSurface(
                    shape = shape,
                    tintColor = tintColor.copy(alpha = if (enabled) 0.32f else 0.12f),
                    alpha = 0.80f,
                    elevation = 8.dp,
                    borderWidth = 1.dp
                )
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Liquid Glass Icon Button with glass ring and specular reflection
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    val isGlass = isLiquidGlassActive()

    if (!isGlass) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .size(44.dp)
                .liquidGlassInteractiveLift(interactionSource, pressedElevation = 10.dp, defaultElevation = 4.dp)
                .liquidGlassSurface(
                    shape = shape,
                    alpha = 0.70f,
                    elevation = 6.dp,
                    borderWidth = 1.dp
                )
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Liquid Glass Chip for filters, tags, and status labels
 */
@Composable
fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val isGlass = isLiquidGlassActive()
    val interactionSource = remember { MutableInteractionSource() }

    val tint = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = if (isGlass) 0.35f else 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isGlass) 0.65f else 1.0f)
    }

    Box(
        modifier = modifier
            .liquidGlassInteractiveLift(interactionSource, pressedElevation = 8.dp, defaultElevation = if (selected) 6.dp else 2.dp)
            .liquidGlassSurface(
                shape = shape,
                tintColor = tint,
                alpha = if (selected) 0.85f else 0.70f,
                elevation = if (selected) 8.dp else 3.dp,
                borderWidth = if (selected) 1.2.dp else 0.8.dp
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.padding(end = 6.dp)) {
                    leadingIcon()
                }
            }
            ProvideTextStyle(
                value = MaterialTheme.typography.labelMedium.copy(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                label()
            }
        }
    }
}
