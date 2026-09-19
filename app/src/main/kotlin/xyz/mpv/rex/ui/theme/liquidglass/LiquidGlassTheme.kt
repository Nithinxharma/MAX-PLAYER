package xyz.mpv.rex.ui.theme.liquidglass

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class LiquidGlassColors(
    val cardBackgroundAlpha: Float = 0.75f,
    val barBackgroundAlpha: Float = 0.80f,
    val dialogBackgroundAlpha: Float = 0.85f,
    val cardElevation: Dp = 14.dp,
    val cardCornerRadius: Dp = 28.dp,
    val specularAlpha: Float = 0.35f,
    val glassBorderWidth: Dp = 1.dp
)

val LocalLiquidGlassColors = staticCompositionLocalOf { LiquidGlassColors() }

/**
 * Creates a translucent glass-adapted Material 3 ColorScheme from the base colorScheme.
 */
fun ColorScheme.toLiquidGlassColorScheme(isDark: Boolean): ColorScheme {
    return this.copy(
        surface = if (isDark) {
            this.surface.copy(alpha = 0.78f).compositeOver(Color(0xFF101014))
        } else {
            this.surface.copy(alpha = 0.82f).compositeOver(Color(0xFFF0F2F6))
        },
        surfaceContainer = if (isDark) {
            this.surfaceContainer.copy(alpha = 0.72f).compositeOver(Color(0xFF16161C))
        } else {
            this.surfaceContainer.copy(alpha = 0.76f).compositeOver(Color(0xFFE8EBF2))
        },
        surfaceContainerHigh = if (isDark) {
            this.surfaceContainerHigh.copy(alpha = 0.76f).compositeOver(Color(0xFF1C1C24))
        } else {
            this.surfaceContainerHigh.copy(alpha = 0.80f).compositeOver(Color(0xFFE2E6EE))
        },
        surfaceContainerHighest = if (isDark) {
            this.surfaceContainerHighest.copy(alpha = 0.80f).compositeOver(Color(0xFF22222E))
        } else {
            this.surfaceContainerHighest.copy(alpha = 0.84f).compositeOver(Color(0xFFDCE1EB))
        },
        surfaceContainerLow = if (isDark) {
            this.surfaceContainerLow.copy(alpha = 0.68f).compositeOver(Color(0xFF0E0E12))
        } else {
            this.surfaceContainerLow.copy(alpha = 0.72f).compositeOver(Color(0xFFEEF1F7))
        },
        surfaceContainerLowest = if (isDark) {
            this.surfaceContainerLowest.copy(alpha = 0.60f).compositeOver(Color(0xFF08080A))
        } else {
            this.surfaceContainerLowest.copy(alpha = 0.65f).compositeOver(Color(0xFFF8FAFC))
        }
    )
}

/**
 * Material 3 Liquid Glass Theme Provider
 */
@Composable
fun Material3LiquidGlassTheme(
    colorScheme: ColorScheme,
    typography: Typography = MaterialTheme.typography,
    shapes: Shapes = MaterialTheme.shapes,
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val glassColorScheme = colorScheme.toLiquidGlassColorScheme(isDark)
    val glassColors = LiquidGlassColors(
        cardBackgroundAlpha = if (isDark) 0.74f else 0.80f,
        barBackgroundAlpha = if (isDark) 0.78f else 0.84f,
        dialogBackgroundAlpha = if (isDark) 0.85f else 0.90f,
        cardElevation = 16.dp,
        cardCornerRadius = 28.dp,
        specularAlpha = if (isDark) 0.38f else 0.55f,
        glassBorderWidth = 1.dp
    )

    CompositionLocalProvider(
        LocalUiStyle provides UiStyle(liquidGlassEnabled = true),
        LocalLiquidGlassColors provides glassColors
    ) {
        MaterialTheme(
            colorScheme = glassColorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
