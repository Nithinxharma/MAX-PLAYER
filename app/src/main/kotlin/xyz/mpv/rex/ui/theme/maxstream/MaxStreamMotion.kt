package xyz.mpv.rex.ui.theme.maxstream

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Centralized Max Stream Motion Engine.
 * Consistent physics-based spring curves and cinematic screen transitions.
 */
object MaxStreamMotion {

    // Spring Physics Specs
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SmoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val SubtleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    val DpSpring = SpringSpec<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Standard Animation Durations
    const val DURATION_FAST = 180
    const val DURATION_NORMAL = 280
    const val DURATION_SMOOTH = 360
    const val DURATION_CINEMATIC = 480

    // Standard Tween Specs
    val FastFadeSpec = tween<Float>(durationMillis = DURATION_FAST, easing = FastOutSlowInEasing)
    val NormalFadeSpec = tween<Float>(durationMillis = DURATION_NORMAL, easing = LinearOutSlowInEasing)
    val SmoothFadeSpec = tween<Float>(durationMillis = DURATION_SMOOTH, easing = FastOutSlowInEasing)

    /**
     * Material Motion Fade Through Transition:
     * Clean OTT transition that fades and scales incoming/outgoing screens
     * without any black flashes or screen jumps.
     */
    val FadeThroughEnter: EnterTransition = fadeIn(
        animationSpec = tween(DURATION_NORMAL, delayMillis = 60, easing = LinearOutSlowInEasing)
    ) + scaleIn(
        animationSpec = tween(DURATION_NORMAL, delayMillis = 60, easing = LinearOutSlowInEasing),
        initialScale = 0.95f
    )

    val FadeThroughExit: ExitTransition = fadeOut(
        animationSpec = tween(DURATION_FAST, easing = FastOutSlowInEasing)
    ) + scaleOut(
        animationSpec = tween(DURATION_FAST, easing = FastOutSlowInEasing),
        targetScale = 1.02f
    )

    val FadeThroughTransition: ContentTransform = FadeThroughEnter togetherWith FadeThroughExit

    /**
     * Material Motion Shared Axis X (Horizontal navigation):
     * Seamless lateral slide with fade to prevent abrupt jumps.
     */
    fun sharedAxisXTransition(
        slideOffset: Int = 120
    ): AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
        if (targetState > initialState) {
            (slideInHorizontally(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                initialOffsetX = { slideOffset }
            ) + fadeIn(animationSpec = tween(DURATION_NORMAL, easing = LinearOutSlowInEasing))
            ) togetherWith (
                slideOutHorizontally(
                    animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                    targetOffsetX = { -slideOffset }
                ) + fadeOut(animationSpec = tween(DURATION_FAST, easing = FastOutSlowInEasing))
            )
        } else {
            (slideInHorizontally(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                initialOffsetX = { -slideOffset }
            ) + fadeIn(animationSpec = tween(DURATION_NORMAL, easing = LinearOutSlowInEasing))
            ) togetherWith (
                slideOutHorizontally(
                    animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                    targetOffsetX = { slideOffset }
                ) + fadeOut(animationSpec = tween(DURATION_FAST, easing = FastOutSlowInEasing))
            )
        }
    }

    /**
     * Standard OTT Screen Horizontal Slide & Fade Transition.
     */
    fun horizontalScreenTransition(
        slideOffset: Int = 100
    ): AnimatedContentTransitionScope<Int>.() -> ContentTransform = sharedAxisXTransition(slideOffset)

    /**
     * Standard Bottom Sheet / Card Modal Slide-In Transition.
     */
    val SheetEnter: EnterTransition = slideInVertically(
        initialOffsetY = { fullHeight: Int -> fullHeight },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + fadeIn(animationSpec = tween(DURATION_NORMAL))

    val SheetExit: ExitTransition = slideOutVertically(
        targetOffsetY = { fullHeight: Int -> fullHeight },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    ) + fadeOut(animationSpec = tween(DURATION_FAST))
}

/**
 * Reusable Card Motion Modifier supporting Press, Selection, and Focus animations
 * with high-performance hardware layer transformations.
 */
@Composable
fun Modifier.maxStreamCardMotion(
    interactionSource: MutableInteractionSource,
    isSelected: Boolean = false,
    pressScale: Float = 0.97f,
    focusScale: Float = 1.05f,
    selectedScale: Float = 1.02f,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val targetScale = when {
        isPressed -> pressScale
        isFocused || isHovered -> focusScale
        isSelected -> selectedScale
        else -> 1.0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "card_motion_scale"
    )

    return this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}

