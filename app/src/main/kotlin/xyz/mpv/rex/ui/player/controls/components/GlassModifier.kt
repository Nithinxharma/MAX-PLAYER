package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GPU-accelerated Glass Surface Modifier that renders directional specular highlights
 * and smooth alpha layers without runtime Canvas/Paint allocations.
 */
fun Modifier.glassSurface(
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.00f),
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    borderWidth: Dp = 1.dp,
    
    // Outer shadow
    outerShadowColor: Color = Color.Black.copy(alpha = 0.00f),
    outerShadowBlur: Dp = 0.dp,
    outerShadowOffsetX: Dp = 0.dp,
    outerShadowOffsetY: Dp = 0.dp,
    
    // Inner Highlight (Top-left)
    innerHighlightColor: Color = Color.White.copy(alpha = 0.35f),
    innerHighlightBlur: Dp = 5.dp,
    innerHighlightOffsetX: Dp = (-2).dp,
    innerHighlightOffsetY: Dp = (-2).dp,
    
    // Inner Shadow (Bottom-right)
    innerShadowColor: Color = Color.Black.copy(alpha = 0.35f),
    innerShadowBlur: Dp = 5.dp,
    innerShadowOffsetX: Dp = 2.dp,
    innerShadowOffsetY: Dp = 2.dp
) = this.drawWithContent {
    val density = this
    val cornerRadiusPx = with(density) { shape.topStart.toPx(size, density) }
    val radius = CornerRadius(cornerRadiusPx)
    
    // Background Surface
    if (backgroundColor.alpha > 0f) {
        drawRoundRect(
            color = backgroundColor,
            cornerRadius = radius
        )
    }
    
    // Specular Top-Left Highlight using GPU linear gradient shader
    if (innerHighlightColor.alpha > 0f) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    innerHighlightColor,
                    innerHighlightColor.copy(alpha = innerHighlightColor.alpha * 0.4f),
                    Color.Transparent
                ),
                start = Offset.Zero,
                end = Offset(size.width * 0.4f, size.height * 0.4f)
            ),
            cornerRadius = radius
        )
    }
    
    // Bottom-Right Shadow Depth using GPU linear gradient shader
    if (innerShadowColor.alpha > 0f) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    innerShadowColor.copy(alpha = innerShadowColor.alpha * 0.5f),
                    innerShadowColor
                ),
                start = Offset(size.width * 0.6f, size.height * 0.6f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = radius
        )
    }
    
    drawContent()
    
    // Specular Border Stroke
    if (borderWidth > 0.dp && borderColor.alpha > 0f) {
        val borderWidthPx = with(density) { borderWidth.toPx() }
        drawRoundRect(
            color = borderColor,
            cornerRadius = radius,
            style = Stroke(width = borderWidthPx)
        )
    }
}

/**
 * Intelligent Glass Effect that seamlessly adapts across all app UI components
 * (player controls, cards, dialogs, sheets, bottom navigation bars)
 * without breaking layouts, clipping bounds, or touch areas.
 */
fun Modifier.intelligentGlassEffect(
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    backgroundColor: Color = Color(0x3812131D),
    borderColor: Color = Color.White.copy(alpha = 0.18f),
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .glassSurface(
        shape = shape,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        borderWidth = borderWidth,
        innerHighlightColor = Color.White.copy(alpha = 0.22f),
        innerHighlightBlur = 4.dp,
        innerHighlightOffsetX = (-1).dp,
        innerHighlightOffsetY = (-1).dp,
        innerShadowColor = Color.Black.copy(alpha = 0.28f),
        innerShadowBlur = 4.dp,
        innerShadowOffsetX = 1.dp,
        innerShadowOffsetY = 1.dp
    )
