package xyz.mpv.rex.ui.theme.liquidglass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class UiStyle(
    val liquidGlassEnabled: Boolean = false
)

val LocalUiStyle = staticCompositionLocalOf { UiStyle(liquidGlassEnabled = false) }

/**
 * Checks if Liquid Glass styling is currently active
 */
@Composable
fun isLiquidGlassActive(): Boolean {
    return LocalUiStyle.current.liquidGlassEnabled
}

/**
 * Dynamic blur modifier with graceful degradation on Android 11 and lower.
 */
fun Modifier.liquidGlassBlur(
    blurRadius: Dp = 20.dp,
    clipShape: Shape? = null
): Modifier = composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
        if (clipShape != null) {
            this.clip(clipShape).blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        } else {
            this.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        }
    } else {
        // Safe fallback on Android 11 and below (zero crash, translucent surface)
        this
    }
}

/**
 * Creates a frosted glass specular border brush with dynamic light reflection
 */
@Composable
fun liquidGlassBorderBrush(
    isDark: Boolean = true,
    alphaTop: Float = if (isDark) 0.35f else 0.55f,
    alphaBottom: Float = if (isDark) 0.08f else 0.12f,
    accentTint: Color = MaterialTheme.colorScheme.primary
): Brush {
    val highlightColor = if (isDark) {
        Color.White.copy(alpha = alphaTop)
    } else {
        Color.White.copy(alpha = alphaTop)
    }
    val subtleColor = if (isDark) {
        accentTint.copy(alpha = alphaBottom)
    } else {
        accentTint.copy(alpha = alphaBottom)
    }
    return Brush.verticalGradient(
        0.0f to highlightColor,
        0.4f to subtleColor,
        1.0f to Color.White.copy(alpha = alphaBottom / 2f)
    )
}

/**
 * Frosted liquid glass surface background modifier.
 * Enhances standard containers with frosted transparency, subtle specular borders, and light diffusion.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape,
    tintColor: Color? = null,
    alpha: Float = 0.72f,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 12.dp,
    blurRadius: Dp = 24.dp
): Modifier = composed {
    val isDark = MaterialTheme.colorScheme.surface.let {
        // Basic luminance estimate
        (0.299 * it.red + 0.587 * it.green + 0.114 * it.blue) < 0.5
    }

    val baseSurface = tintColor ?: if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    }

    val primaryTint = MaterialTheme.colorScheme.primary

    val borderBrush = liquidGlassBorderBrush(
        isDark = isDark,
        alphaTop = if (isDark) 0.38f else 0.65f,
        alphaBottom = if (isDark) 0.10f else 0.18f,
        accentTint = primaryTint
    )

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = primaryTint.copy(alpha = if (isDark) 0.25f else 0.15f),
            spotColor = if (isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.2f)
        )
        .clip(shape)
        .liquidGlassBlur(blurRadius)
        .background(
            brush = Brush.verticalGradient(
                0.0f to baseSurface.copy(alpha = (alpha + 0.08f).coerceAtMost(0.95f)),
                1.0f to baseSurface.copy(alpha = alpha)
            ),
            shape = shape
        )
        .drawWithCache {
            // Specular reflection gradient overlay
            val reflectionBrush = Brush.linearGradient(
                0.0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.28f),
                0.3f to Color.White.copy(alpha = if (isDark) 0.03f else 0.08f),
                1.0f to Color.Transparent,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height * 0.6f)
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = reflectionBrush)
            }
        }
        .border(
            border = BorderStroke(borderWidth, borderBrush),
            shape = shape
        )
}

/**
 * Interactive lift and glow for glass cards and buttons
 */
fun Modifier.liquidGlassInteractiveLift(
    interactionSource: MutableInteractionSource,
    pressedElevation: Dp = 18.dp,
    defaultElevation: Dp = 8.dp
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedElevation by animateFloatAsState(
        targetValue = if (isPressed) pressedElevation.value else defaultElevation.value,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "glass_lift_elevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "glass_lift_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = animatedElevation
        }
}
