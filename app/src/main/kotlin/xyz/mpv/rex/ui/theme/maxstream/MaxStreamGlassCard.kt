package xyz.mpv.rex.ui.theme.maxstream

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Backward compatibility alias redirecting to centralized xyz.mpv.rex.core.ui.MaxStreamGlassCard
 */
@Composable
fun MaxStreamGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 1.dp,
    tint: Color = Color.White.copy(alpha = 0.08f),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    xyz.mpv.rex.core.ui.MaxStreamGlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        borderWidth = borderWidth,
        tint = tint,
        onClick = onClick,
        content = content
    )
}
