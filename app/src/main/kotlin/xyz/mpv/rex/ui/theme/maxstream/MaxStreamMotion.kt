package xyz.mpv.rex.ui.theme.maxstream

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

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
     * Standard OTT Screen Horizontal Slide & Fade Transition.
     */
    fun horizontalScreenTransition(
        slideOffset: Int = 100
    ): AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
        if (targetState > initialState) {
            (slideInHorizontally(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                initialOffsetX = { slideOffset }
            ) + fadeIn(animationSpec = tween(DURATION_NORMAL))) togetherWith (
                slideOutHorizontally(
                    animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                    targetOffsetX = { -slideOffset }
                ) + fadeOut(animationSpec = tween(DURATION_FAST))
            )
        } else {
            (slideInHorizontally(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                initialOffsetX = { -slideOffset }
            ) + fadeIn(animationSpec = tween(DURATION_NORMAL))) togetherWith (
                slideOutHorizontally(
                    animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                    targetOffsetX = { slideOffset }
                ) + fadeOut(animationSpec = tween(DURATION_FAST))
            )
        }
    }

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
