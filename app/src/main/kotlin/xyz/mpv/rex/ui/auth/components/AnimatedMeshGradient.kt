package xyz.mpv.rex.ui.auth.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Premium Dark Cinematic Mesh Gradient Background for MAX STREAM:
 * - Deep OLED black base (#050508)
 * - Subtle purple & deep magenta ambient ambient lighting (no large cyan/blue glow, no bright halo)
 * - Clean glassmorphism contrast and readability
 */
@Composable
fun AnimatedMeshGradient(
    modifier: Modifier = Modifier,
    blurRadius: Int = 90,
    content: @Composable () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cinematic_mesh_gradient")

    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t1"
    )

    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t2"
    )

    val subtlePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "subtlePulse"
    )

    // Cinematic deep dark palette: subtle purple/magenta accents with zero cyan/blue glow
    val deepPurple = Color(0xFF6B21A8) // Deep violet
    val subtleMagenta = Color(0xFF831843) // Deep rich berry/magenta
    val darkCharcoal = Color(0xFF0F0B18)
    val colorDarkBase = Color(0xFF06060A) // Ultra deep cinematic OLED black

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorDarkBase)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius.dp)
        ) {
            val w = size.width
            val h = size.height

            // Orb 1: Subtle deep magenta accent (top-left, soft and diffused)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(subtleMagenta.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * (0.2f + 0.15f * t1), h * (0.15f + 0.12f * t2)),
                    radius = (w * 0.65f) * subtlePulse
                )
            )

            // Orb 2: Deep atmospheric violet (bottom-right wandering)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(deepPurple.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(w * (0.78f - 0.18f * t2), h * (0.75f - 0.15f * t1)),
                    radius = (w * 0.70f) * subtlePulse
                )
            )

            // Orb 3: Very subtle dark warmth in center-bottom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(darkCharcoal.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(w * 0.5f, h * (0.55f + 0.1f * t1)),
                    radius = w * 0.8f
                )
            )
        }

        // Deep Cinematic Vignette & Contrast Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF06060A).copy(alpha = 0.60f),
                            Color(0xFF06060A).copy(alpha = 0.30f),
                            Color(0xFF06060A).copy(alpha = 0.85f)
                        )
                    )
                )
        )

        content()
    }
}
