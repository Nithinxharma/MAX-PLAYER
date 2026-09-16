package xyz.mpv.rex.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.theme.MaxStreamBgDark
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.welcome.WelcomeScreen

/**
 * Premium cinematic MAX STREAM splash screen with a 7-stage sequence:
 * 1. Deep black canvas
 * 2. Soft atmospheric glow appears
 * 3. Official MAX STREAM logo fades in with smooth cinematic scale
 * 4. Gradient light ray / sheen sweep passes through the logo
 * 5. Logo emits a subtle cinematic bloom & glow pulse
 * 6. "MAX STREAM" premium typography ascends and fades in
 * 7. Seamless transition to Home / Welcome screen
 */
@Serializable
object SplashScreen : Screen {

  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()

    // Cinematic Animation Controls
    val backgroundGlowAlpha = remember { Animatable(0f) }
    val backgroundGlowScale = remember { Animatable(0.7f) }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.88f) }

    val sheenProgress = remember { Animatable(-0.6f) }
    val sheenAlpha = remember { Animatable(0f) }

    val bloomAlpha = remember { Animatable(0f) }
    val bloomScale = remember { Animatable(0.95f) }

    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(18f) }
    val taglineAlpha = remember { Animatable(0f) }

    val exitAlpha = remember { Animatable(1f) }

    val cinematicEase = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) }

    LaunchedEffect(Unit) {
      // Stage 1: Initial pure black stillness (200ms)
      delay(200)

      // Stage 2: Soft atmospheric glow appears
      launch {
        backgroundGlowAlpha.animateTo(
          targetValue = 0.85f,
          animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
      }
      launch {
        backgroundGlowScale.animateTo(
          targetValue = 1.25f,
          animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing)
        )
      }

      // Stage 3: Official MAX STREAM logo fades in & settles
      delay(300)
      launch {
        logoAlpha.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 650, easing = cinematicEase)
        )
      }
      launch {
        logoScale.animateTo(
          targetValue = 1.0f,
          animationSpec = tween(durationMillis = 850, easing = cinematicEase)
        )
      }

      // Stage 4: Gradient light / sheen passes through logo
      delay(550)
      sheenAlpha.snapTo(1f)
      launch {
        sheenProgress.animateTo(
          targetValue = 1.6f,
          animationSpec = tween(durationMillis = 850, easing = LinearEasing)
        )
        sheenAlpha.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 200)
        )
      }

      // Stage 5: Logo emits subtle cinematic glow / bloom
      delay(400)
      launch {
        bloomAlpha.animateTo(
          targetValue = 0.75f,
          animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
        bloomAlpha.animateTo(
          targetValue = 0.35f,
          animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
      }
      launch {
        bloomScale.animateTo(
          targetValue = 1.15f,
          animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
      }

      // Stage 6: "MAX STREAM" text appears with graceful vertical float
      delay(300)
      launch {
        textAlpha.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
      }
      launch {
        textOffsetY.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 600, easing = cinematicEase)
        )
      }
      launch {
        taglineAlpha.animateTo(
          targetValue = 0.65f,
          animationSpec = tween(durationMillis = 700, delayMillis = 150, easing = FastOutSlowInEasing)
        )
      }

      // Hold cinematic impact
      delay(900)

      // Stage 7: Smooth transition into Home / Welcome Screen
      exitAlpha.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
      )

      val hasCompletedOnboarding = appearancePreferences.onboardingCompleted.get()
      val targetScreen = if (hasCompletedOnboarding) MainScreen else WelcomeScreen

      backstack.clear()
      backstack.add(targetScreen)
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaxStreamBgDark)
        .graphicsLayer { alpha = exitAlpha.value },
      contentAlignment = Alignment.Center
    ) {
      // Background Radial Atmosphere
      Box(
        modifier = Modifier
          .size(420.dp)
          .scale(backgroundGlowScale.value)
          .alpha(backgroundGlowAlpha.value)
          .blur(80.dp)
          .background(
            Brush.radialGradient(
              colors = listOf(
                Color(0x55A855F7),
                Color(0x33007AFF),
                Color(0x1532D7FF),
                Color.Transparent
              )
            )
          )
      )

      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Logo container with layered bloom and light sweep
        Box(
          modifier = Modifier.size(160.dp),
          contentAlignment = Alignment.Center
        ) {
          // Subtle Bloom Halo behind Logo
          Image(
            painter = painterResource(id = R.drawable.ic_max_stream_mark),
            contentDescription = null,
            modifier = Modifier
              .size(150.dp)
              .scale(logoScale.value * bloomScale.value)
              .alpha(bloomAlpha.value)
              .blur(24.dp)
          )

          // Main Crisp Vector Mark with Light Sheen Sweep
          Box(
            modifier = Modifier
              .size(130.dp)
              .scale(logoScale.value)
              .alpha(logoAlpha.value)
              .drawWithContent {
                drawContent()

                // Draw gradient light sheen passing across the logo
                if (sheenAlpha.value > 0f) {
                  val sweepX = size.width * sheenProgress.value
                  val sheenBrush = Brush.linearGradient(
                    colors = listOf(
                      Color.Transparent,
                      Color.White.copy(alpha = 0.55f * sheenAlpha.value),
                      Color(0xFF32D7FF).copy(alpha = 0.4f * sheenAlpha.value),
                      Color.Transparent
                    ),
                    start = Offset(sweepX - 80f, 0f),
                    end = Offset(sweepX + 80f, size.height)
                  )
                  drawRect(brush = sheenBrush, blendMode = BlendMode.SrcAtop)
                }
              }
          ) {
            Image(
              painter = painterResource(id = R.drawable.ic_max_stream_mark),
              contentDescription = "MAX STREAM Logo",
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Typography: "MAX STREAM"
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .offset { IntOffset(0, textOffsetY.value.dp.roundToPx()) }
            .alpha(textAlpha.value)
        ) {
          Text(
            text = "MAX STREAM",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 5.sp
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "PREMIUM MEDIA UNIVERSE",
            color = Color(0xFFB3B3B3),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 3.sp,
            modifier = Modifier.alpha(taglineAlpha.value)
          )
        }
      }
    }
  }
}
