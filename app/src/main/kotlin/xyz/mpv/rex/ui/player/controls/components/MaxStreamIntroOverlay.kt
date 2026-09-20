package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun MaxStreamIntroOverlay(
    isLoading: Boolean,
    loadingPercent: Int? = null,
    mediaTitle: String? = null,
    modifier: Modifier = Modifier
) {
    var minTimeElapsed by remember { mutableStateOf(false) }
    var initialStart by remember { mutableStateOf(true) }

    // Ensure intro displays for at least 2.2 seconds on video startup
    LaunchedEffect(isLoading) {
        if (isLoading) {
            minTimeElapsed = false
            delay(2200L)
            minTimeElapsed = true
        } else {
            if (!minTimeElapsed) {
                delay(1800L)
                minTimeElapsed = true
            }
        }
        initialStart = false
    }

    val showOverlay = isLoading || !minTimeElapsed

    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(650, easing = FastOutSlowInEasing)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090A10)),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "max_stream_intro")
            
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.75f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow_alpha"
            )

            val rotateAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotate_angle"
            )

            // Background ambient radial glow
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .scale(pulseScale)
                    .alpha(glowAlpha)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE50914).copy(alpha = 0.45f),
                                Color(0xFF8A2BE2).copy(alpha = 0.25f),
                                Color(0xFF00D2FF).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // 3D-styled 'M' Logo Canvas
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Path for left leg & diagonal of 'M'
                        val leftPath = Path().apply {
                            moveTo(w * 0.12f, h * 0.90f)
                            lineTo(w * 0.12f, h * 0.15f)
                            lineTo(w * 0.38f, h * 0.15f)
                            lineTo(w * 0.50f, h * 0.55f)
                            lineTo(w * 0.36f, h * 0.90f)
                            close()
                        }

                        // Path for right leg & diagonal of 'M'
                        val rightPath = Path().apply {
                            moveTo(w * 0.88f, h * 0.90f)
                            lineTo(w * 0.88f, h * 0.15f)
                            lineTo(w * 0.62f, h * 0.15f)
                            lineTo(w * 0.50f, h * 0.55f)
                            lineTo(w * 0.64f, h * 0.90f)
                            close()
                        }

                        val redVioletBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF2A55),
                                Color(0xFFC70039),
                                Color(0xFF8B00FF)
                            )
                        )

                        val cyanVioletBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF3F51B5),
                                Color(0xFF8B00FF)
                            )
                        )

                        drawPath(leftPath, brush = redVioletBrush)
                        drawPath(rightPath, brush = cyanVioletBrush)

                        // Center ribbon overlay to give 3D depth
                        val centerRibbon = Path().apply {
                            moveTo(w * 0.38f, h * 0.15f)
                            lineTo(w * 0.50f, h * 0.58f)
                            lineTo(w * 0.62f, h * 0.15f)
                            lineTo(w * 0.50f, h * 0.72f)
                            close()
                        }
                        
                        drawPath(
                            centerRibbon,
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF5252), Color(0xFF7C4DFF), Color(0xFF448AFF))
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Brand Title Text "MAX STREAM"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "MAX ",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "STREAM",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Media title or Subtitle
                Text(
                    text = if (!mediaTitle.isNullOrBlank() && !mediaTitle.contains("index.m3u8") && !mediaTitle.startsWith("http")) mediaTitle else "PREMIUM STREAMING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Glowing progress line / buffer status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(200.dp)
                ) {
                    val progressAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "progress_alpha"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        if (loadingPercent != null && loadingPercent > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(loadingPercent / 100f)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFFFF2A55), Color(0xFF00E5FF))
                                        )
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                                    .align(Alignment.Center)
                                    .alpha(progressAlpha)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, Color(0xFFFF2A55), Color(0xFF00E5FF), Color.Transparent)
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (loadingPercent != null && loadingPercent in 1..99) "BUFFERING $loadingPercent%" else "OPTIMIZING STREAM...",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
