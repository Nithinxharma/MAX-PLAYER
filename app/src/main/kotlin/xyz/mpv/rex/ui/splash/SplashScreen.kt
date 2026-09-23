package xyz.mpv.rex.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.theme.MaxStreamBgDark
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.welcome.WelcomeScreen

/**
 * Premium cinematic MAX STREAM splash screen with Lottie Animation:
 * 1. Deep OLED dark background (#050505)
 * 2. Lottie animation rendered seamlessly from assets/splash_animation.json
 * 3. Soft ambient background glow synchronized with animation
 * 4. Illuminated typography "MAX STREAM" ascending smoothly
 * 5. Fluid fade transition into MainScreen or WelcomeScreen
 */
@Serializable
object SplashScreen : Screen {

  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()

    // Load Lottie Composition from assets/splash_animation.json
    val composition by rememberLottieComposition(
      spec = LottieCompositionSpec.Asset("splash_animation.json")
    )
    val lottieProgress by animateLottieCompositionAsState(
      composition = composition,
      iterations = 1,
      isPlaying = true,
      speed = 1.0f
    )

    // Animation Drivers for typography & smooth exit
    val backgroundGlowAlpha = remember { Animatable(0f) }
    val backgroundGlowScale = remember { Animatable(0.85f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(18f) }
    val taglineAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
      // 1. Soft atmospheric glow emerges
      delay(300)
      launch {
        backgroundGlowAlpha.animateTo(
          targetValue = 0.85f,
          animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
      }
      launch {
        backgroundGlowScale.animateTo(
          targetValue = 1.15f,
          animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
        )
      }

      // 2. MAX STREAM typography smoothly illuminates as logo forms
      delay(800)
      launch {
        textAlpha.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
      }
      launch {
        textOffsetY.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
      }
      launch {
        taglineAlpha.animateTo(
          targetValue = 0.75f,
          animationSpec = tween(durationMillis = 700, delayMillis = 150, easing = FastOutSlowInEasing)
        )
      }
    }

    // Navigate when Lottie reaches end (or safety timeout)
    LaunchedEffect(lottieProgress) {
      if (lottieProgress >= 0.98f) {
        delay(250)
        exitAlpha.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 350, easing = LinearEasing)
        )

        val hasCompletedOnboarding = appearancePreferences.onboardingCompleted.get()
        val targetScreen = if (hasCompletedOnboarding) MainScreen else WelcomeScreen

        backstack.clear()
        backstack.add(targetScreen)
      }
    }

    // Fallback safety timer in case composition is delayed
    LaunchedEffect(Unit) {
      delay(4500)
      if (exitAlpha.value > 0.1f) {
        exitAlpha.animateTo(0f, tween(300))
        val hasCompletedOnboarding = appearancePreferences.onboardingCompleted.get()
        val targetScreen = if (hasCompletedOnboarding) MainScreen else WelcomeScreen
        backstack.clear()
        backstack.add(targetScreen)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaxStreamBgDark)
        .graphicsLayer { alpha = exitAlpha.value },
      contentAlignment = Alignment.Center
    ) {
      // Atmospheric Soft Glow Backdrop
      Box(
        modifier = Modifier
          .size(360.dp)
          .scale(backgroundGlowScale.value)
          .alpha(backgroundGlowAlpha.value)
          .blur(64.dp)
          .background(
            Brush.radialGradient(
              colors = listOf(
                Color(0x5500A2FF),
                Color(0x356366F1),
                Color(0x18EC4899),
                Color.Transparent
              )
            )
          )
      )

      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Lottie Animation Container
        Box(
          modifier = Modifier.size(240.dp),
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
