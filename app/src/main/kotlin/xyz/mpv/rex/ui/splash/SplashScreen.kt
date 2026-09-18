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
 * Premium cinematic MAX STREAM splash screen:
 * 1. Pure black background (#050505)
 * 2. MAX STREAM logo fades in smoothly
 * 3. Soft atmospheric glow emerges behind logo
 * 4. Gradient light sweep beam glides across the logo mark
 * 5. MAX STREAM studio text ascends and illuminates
 * 6. Seamless transition into home / welcome screen
 */
@Serializable
object SplashScreen : Screen {

  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()

    // Cinematic Animation Drivers
    val backgroundGlowAlpha = remember { Animatable(0f) }
    val backgroundGlowScale = remember { Animatable(0.75f) }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.92f) }

    val sweepProgress = remember { Animatable(-0.5f) }
    val sweepAlpha = remember { Animatable(0f) }

    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(14f) }
    val taglineAlpha = remember { Animatable(0f) }

    val exitAlpha = remember { Animatable(1f) }

    val cinematicEase = remember { CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }

    LaunchedEffect(Unit) {
      // 1. Initial Pure Black Silence (150ms)
      delay(150)

      // 2. MAX STREAM Logo Fades in
      launch {
        logoAlpha.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 650, easing = cinematicEase)
        )
      }
      launch {
        logoScale.animateTo(
          targetValue = 1.0f,
          animationSpec = tween(durationMillis = 800, easing = cinematicEase)
        )
      }

      // 3. Soft Glow emerges around logo
      delay(250)
      launch {
        backgroundGlowAlpha.animateTo(
          targetValue = 0.9f,
          animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
      }
      launch {
        backgroundGlowScale.animateTo(
          targetValue = 1.2f,
          animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing)
        )
      }

      // 4. Gradient light sweep passes through logo
      delay(400)
      sweepAlpha.snapTo(1f)
      launch {
        sweepProgress.animateTo(
          targetValue = 1.5f,
          animationSpec = tween(durationMillis = 750, easing = LinearEasing)
        )
        sweepAlpha.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 200)
        )
      }

      // 5. MAX STREAM text appears
      delay(300)
      launch {
        textAlpha.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
        )
      }
      launch {
        textOffsetY.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 550, easing = cinematicEase)
        )
      }
      launch {
        taglineAlpha.animateTo(
          targetValue = 0.7f,
          animationSpec = tween(durationMillis = 600, delayMillis = 100, easing = FastOutSlowInEasing)
        )
      }

      // Hold iconic presence
      delay(800)

      // 6. Smooth transition into home
      exitAlpha.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
      )

      android.util.Log.d("TRANSITION_TRACE", "SplashScreen: Checking appearancePreferences.onboardingCompleted.get()")
      val hasCompletedOnboarding = appearancePreferences.onboardingCompleted.get()
      if (hasCompletedOnboarding) {
        android.util.Log.d("TRANSITION_TRACE", "ABOUT TO NAVIGATE TO MAIN")
      } else {
        android.util.Log.d("TRANSITION_TRACE", "ABOUT TO NAVIGATE TO WELCOME")
      }
      val targetScreen = if (hasCompletedOnboarding) MainScreen else WelcomeScreen

      backstack.clear()
      backstack.add(targetScreen)
      android.util.Log.d("TRANSITION_TRACE", "SplashScreen: backstack updated to targetScreen: " + targetScreen::class.simpleName)
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
          .size(380.dp)
          .scale(backgroundGlowScale.value)
          .alpha(backgroundGlowAlpha.value)
          .blur(72.dp)
          .background(
            Brush.radialGradient(
              colors = listOf(
                Color(0x60A855F7),
                Color(0x35007AFF),
                Color(0x1232D7FF),
                Color.Transparent
              )
            )
          )
      )

      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Logo container with specular light sweep
        Box(
          modifier = Modifier.size(150.dp),
          contentAlignment = Alignment.Center
        ) {
          // Ambient Bloom under logo
          Image(
            painter = painterResource(id = R.drawable.ic_max_stream_mark),
            contentDescription = null,
            modifier = Modifier
              .size(140.dp)
              .scale(logoScale.value * 1.05f)
              .alpha(backgroundGlowAlpha.value * 0.4f)
              .blur(20.dp)
          )

          // Main Crisp Vector Mark with Light Sheen Sweep
          Box(
            modifier = Modifier
              .size(124.dp)
              .scale(logoScale.value)
              .alpha(logoAlpha.value)
              .drawWithContent {
                drawContent()

                // Specular gradient sweep
                if (sweepAlpha.value > 0f) {
                  val sweepX = size.width * sweepProgress.value
                  val sweepBrush = Brush.linearGradient(
                    colors = listOf(
                      Color.Transparent,
                      Color.White.copy(alpha = 0.65f * sweepAlpha.value),
                      Color(0xFF32D7FF).copy(alpha = 0.45f * sweepAlpha.value),
                      Color.Transparent
                    ),
                    start = Offset(sweepX - 60f, 0f),
                    end = Offset(sweepX + 60f, size.height)
                  )
                  drawRect(brush = sweepBrush, blendMode = BlendMode.SrcAtop)
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

        Spacer(modifier = Modifier.height(24.dp))

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
            fontSize = 25.sp,
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
