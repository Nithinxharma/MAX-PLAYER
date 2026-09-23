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
 * Animated Mesh Gradient Background featuring OTT dark aesthetic with vibrant energy:
 * Colors: #FF5F1F (Orange), #FF2D55 (Neon Pink), #7B61FF (Vibrant Purple), #00C2FF (Electric Cyan)
 */
@Composable
fun AnimatedMeshGradient(
    modifier: Modifier = Modifier,
    blurRadius: Int = 80,
    content: @Composable () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_gradient")

    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t1"
    )

    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t2"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val colorOrange = Color(0xFFFF5F1F)
    val colorCrimson = Color(0xFFFF2D55)
    val colorPurple = Color(0xFF7B61FF)
    val colorCyan = Color(0xFF00C2FF)
    val colorDarkBase = Color(0xFF07070A)

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

            // Orb 1: Neon Pink / Crimson (Top Left wandering)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorCrimson.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(w * (0.2f + 0.3f * t1), h * (0.15f + 0.25f * t2)),
                    radius = (w * 0.55f) * pulse
                )
            )

            // Orb 2: Electric Cyan (Top Right wandering)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorCyan.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(w * (0.85f - 0.35f * t2), h * (0.25f + 0.3f * t1)),
                    radius = (w * 0.60f) * pulse
                )
            )

            // Orb 3: Sunset Orange (Bottom Left wandering)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorOrange.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(w * (0.25f + 0.25f * t2), h * (0.8f - 0.25f * t1)),
                    radius = (w * 0.58f) * (2f - pulse)
                )
            )

            // Orb 4: Vibrant Purple (Bottom Right / Center wandering)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorPurple.copy(alpha = 0.48f), Color.Transparent),
                    center = Offset(w * (0.75f - 0.25f * t1), h * (0.75f - 0.2f * t2)),
                    radius = (w * 0.65f) * pulse
                )
            )
        }

        // Dark Vignette / Contrast overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        content()
    }
}
