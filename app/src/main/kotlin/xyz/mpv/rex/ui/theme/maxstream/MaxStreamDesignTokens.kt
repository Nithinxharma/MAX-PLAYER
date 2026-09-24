package xyz.mpv.rex.ui.theme.maxstream

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Max Stream Flagship OTT Design System & Theme Tokens.
 * Deep obsidian midnight aesthetic with cinematic glowing accents and rich glassmorphism.
 */
object MaxStreamTheme {

    // Palette Tokens
    val AbyssBackground = Color(0xFF07080B)
    val MidnightSurface = Color(0xFF0C0F17)
    val ElevatedSurface = Color(0xFF131824)
    val GlassSurface = Color(0x3B1A2234)
    val GlassSurfaceActive = Color(0x66242F48)
    
    // Specular Border & Scrims
    val GlassBorder = Color(0x2EFFFFFF)
    val GlassBorderFocused = Color(0x80FFFFFF)
    val GlassHighlight = Color(0x40FFFFFF)
    val ShadowDark = Color(0x99000000)

    // Accent Tokens
    val CrimsonAccent = Color(0xFFFF2A55)
    val CrimsonAccentGlow = Color(0x55FF2A55)
    val ElectricCyan = Color(0xFF38BDF8)
    val ElectricCyanGlow = Color(0x5538BDF8)
    val NeonViolet = Color(0xFFA855F7)
    val AmberGold = Color(0xFFFBBF24)
    val EmeraldLive = Color(0xFF10B981)

    // Base Palette Tokens
    val TextPrimary = Color(0xFFF9FAFB)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Light Palette Tokens
    val TextPrimaryLight = Color(0xFF0F172A)
    val TextSecondaryLight = Color(0xFF475569)
    val TextMutedLight = Color(0xFF94A3B8)

    // Theme Adaptive Colors
    val isDark: Boolean
        @Composable
        get() {
            val surface = MaterialTheme.colorScheme.surface
            val lum = (0.299f * surface.red + 0.587f * surface.green + 0.114f * surface.blue)
            return lum < 0.5f
        }

    val primaryTextColor: Color
        @Composable
        get() = if (isDark) TextPrimary else TextPrimaryLight

    val secondaryTextColor: Color
        @Composable
        get() = if (isDark) TextSecondary else TextSecondaryLight

    val mutedTextColor: Color
        @Composable
        get() = if (isDark) TextMuted else TextMutedLight

    val backgroundColor: Color
        @Composable
        get() = if (isDark) AbyssBackground else Color(0xFFF8FAFC)

    val surfaceColor: Color
        @Composable
        get() = if (isDark) MidnightSurface else Color(0xFFFFFFFF)

    val elevatedSurfaceColor: Color
        @Composable
        get() = if (isDark) ElevatedSurface else Color(0xFFF1F5F9)

    val glassSurfaceColor: Color
        @Composable
        get() = if (isDark) GlassSurface else Color(0xFFF1F5F9).copy(alpha = 0.90f)

    val glassBorderColor: Color
        @Composable
        get() = if (isDark) GlassBorder else Color(0x2E000000)

    // Common Shapes
    val CardShape = RoundedCornerShape(18.dp)
    val HeroCardShape = RoundedCornerShape(24.dp)
    val CapsuleShape = RoundedCornerShape(32.dp)
    val ButtonShape = RoundedCornerShape(14.dp)
    val BadgeShape = RoundedCornerShape(8.dp)

    /**
     * Helper to detect if running on Android TV or Leanback environment.
     */
    fun isTelevision(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val config = context.resources.configuration
        return (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
                context.packageManager.hasSystemFeature("android.software.leanback")
    }
}

/**
 * High-performance Glassmorphism surface modifier with specular borders & ambient glow.
 */
fun Modifier.maxStreamGlass(
    shape: Shape = MaxStreamTheme.CardShape,
    backgroundColor: Color = MaxStreamTheme.GlassSurface,
    borderColor: Color = MaxStreamTheme.GlassBorder,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 8.dp,
    glowColor: Color = Color.Transparent,
    glowRadius: Dp = 0.dp
): Modifier = this
    .then(
        if (glowRadius > 0.dp && glowColor != Color.Transparent) {
            Modifier.drawBehind {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                        radius = (size.width.coerceAtLeast(size.height) / 2f) + glowRadius.toPx()
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                )
            }
        } else Modifier
    )
    .shadow(elevation = elevation, shape = shape, clip = false)
    .clip(shape)
    .background(backgroundColor)
    .border(width = borderWidth, color = borderColor, shape = shape)

/**
 * Custom TV & D-Pad focus engine modifier:
 * - Scales smoothly on focus with Spring physics (default 1.08x)
 * - Emits soft ambient neon glow halo behind component
 * - Brightens specular edge borders
 * - Elevates Z-index so it renders on top of neighbors
 * - Handles D-Pad Center & Enter key events
 */
@Composable
fun Modifier.maxStreamTvFocusable(
    onClick: () -> Unit,
    focusedScale: Float = 1.08f,
    glowColor: Color = MaxStreamTheme.CrimsonAccentGlow,
    shape: Shape = MaxStreamTheme.CardShape,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val activeState = isFocused || isHovered

    val animatedScale by animateFloatAsState(
        targetValue = if (activeState) focusedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "maxstream_focus_scale"
    )

    val animatedElevation by animateDpAsState(
        targetValue = if (activeState) 16.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "maxstream_focus_elevation"
    )

    return this
        .zIndex(if (activeState) 10f else 1f)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            cameraDistance = 16f
        }
        .focusable(enabled = enabled, interactionSource = interactionSource)
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyUp &&
                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
        .then(
            if (activeState) {
                Modifier
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.90f),
                                MaxStreamTheme.CrimsonAccent.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.40f)
                            )
                        ),
                        shape = shape
                    )
            } else {
                Modifier.border(
                    width = 1.dp,
                    color = MaxStreamTheme.GlassBorder,
                    shape = shape
                )
            }
        )
}
