package xyz.mpv.rex.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.auth.LoginScreen
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.theme.MaxStreamBgDark
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Premium cinematic MAX STREAM splash screen:
 * 1. Deep OLED dark background (#050505)
 * 2. High-energy pulse & breathing ribbon glow
 * 3. Lottie animation rendered from assets/splash_animation.json
 * 4. Illuminated typography "MAX STREAM" ascending smoothly
 * 5. ~2.5s duration then fluid transition into Auth Check (Authenticated -> Home / Unauthenticated -> Login)
 */
@Serializable
object SplashScreen : Screen {

  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()
    val authManager = koinInject<AuthManager>()

    // Load Lottie Composition from assets/splash_animation.json
    val composition by rememberLottieComposition(
      spec = LottieCompositionSpec.Asset("splash_animation.json")
    )
    val lottieProgress by animateLottieCompositionAsState(
      composition = composition,
      iterations = 1,
      isPlaying = true,
      speed = 1.15f
    )

    // Breathing & Pulse Infinite Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "splash_breathing")
    val energyPulse by infiniteTransition.animateFloat(
      initialValue = 0.92f,
      targetValue = 1.08f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "energyPulse"
    )
    val ribbonGlowAlpha by infiniteTransition.animateFloat(
      initialValue = 0.6f,
      targetValue = 0.95f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 900, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "ribbonGlow"
    )

    // Entrance and Exit Drivers
    val backgroundGlowAlpha = remember { Animatable(0f) }
    val backgroundGlowScale = remember { Animatable(0.85f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(18f) }
    val taglineAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
      // 1. Soft atmospheric glow emerges
      delay(200)
      launch {
        backgroundGlowAlpha.animateTo(
          targetValue = 0.9f,
          animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
      }
      launch {
        backgroundGlowScale.animateTo(
          targetValue = 1.2f,
          animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
        )
      }

      // 2. MAX STREAM typography smoothly illuminates
      delay(600)
      launch {
        textAlpha.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
      }
      launch {
        textOffsetY.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
      }
      launch {
        taglineAlpha.animateTo(
          targetValue = 0.85f,
          animationSpec = tween(durationMillis = 600, delayMillis = 100, easing = FastOutSlowInEasing)
        )
      }
    }

    // Auth Check & Navigation Trigger after ~2.5s
    fun proceedToNextScreen() {
      val isAuthenticated = authManager.isAuthenticated
      val targetScreen: Screen = if (isAuthenticated) {
        MainScreen
      } else {
        LoginScreen
      }
      backstack.clear()
      backstack.add(targetScreen)
    }

    // Navigate when animation reaches completion (~2.4s)
    LaunchedEffect(lottieProgress) {
      if (lottieProgress >= 0.95f) {
        delay(150)
        exitAlpha.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 300, easing = LinearEasing)
        )
        proceedToNextScreen()
      }
    }

    // Safety timer for exactly 2.5s
    LaunchedEffect(Unit) {
      delay(2500)
      if (exitAlpha.value > 0.1f) {
        exitAlpha.animateTo(0f, tween(250))
        proceedToNextScreen()
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaxStreamBgDark)
        .graphicsLayer { alpha = exitAlpha.value },
      contentAlignment = Alignment.Center
    ) {
      // Atmospheric Energy Pulse Glow Backdrop
      Box(
        modifier = Modifier
          .size(360.dp)
          .scale(backgroundGlowScale.value * energyPulse)
          .alpha(backgroundGlowAlpha.value * ribbonGlowAlpha)
          .blur(64.dp)
          .background(
            Brush.radialGradient(
              colors = listOf(
                Color(0xFFFF5F1F),
                Color(0xFFFF2D55),
                Color(0xFF7B61FF),
                Color(0xFF00C2FF),
                Color.Transparent
              )
            )
          )
      )

      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Lottie Animation Container with breathing scale
        Box(
          modifier = Modifier
            .size(240.dp)
            .scale(energyPulse),
          contentAlignment = Alignment.Center
        ) {
          LottieAnimation(
            composition = composition,
            progress = { lottieProgress },
            modifier = Modifier.fillMaxSize()
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Typography: "MAX STREAM" & Tagline
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .offset { IntOffset(0, textOffsetY.value.dp.roundToPx()) }
            .alpha(textAlpha.value)
        ) {
          Text(
            text = "MAX STREAM",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 6.sp
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "PREMIUM MEDIA UNIVERSE",
            color = Color(0xFFB3B3B3),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 3.5.sp,
            modifier = Modifier.alpha(taglineAlpha.value)
          )
        }
      }
    }
  }
}
