package xyz.mpv.rex.ui.theme.maxstream

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MaxStreamTheme {
    val AbyssBackground = Color(0xFF080B10)
    val MidnightSurface = Color(0xFF0E131F)
    val ElevatedSurface = Color(0xFF161E31)
    val GlassSurface = Color(0x331E293B)
    val GlassBorder = Color(0x4038BDF8)
    val CrimsonAccent = Color(0xFFE50914)
    val ElectricCyan = Color(0xFF00E5FF)
    val NeonGreen = Color(0xFF10B981)
    val AmberWarning = Color(0xFFF59E0B)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Backward compatibility alias
    val SurfaceDark = MidnightSurface
}

private val DarkColorScheme = darkColorScheme(
    primary = MaxStreamTheme.CrimsonAccent,
    onPrimary = Color.White,
    primaryContainer = MaxStreamTheme.ElevatedSurface,
    secondary = MaxStreamTheme.ElectricCyan,
    onSecondary = Color.Black,
    background = MaxStreamTheme.AbyssBackground,
    onBackground = MaxStreamTheme.TextPrimary,
    surface = MaxStreamTheme.MidnightSurface,
    onSurface = MaxStreamTheme.TextPrimary,
    surfaceVariant = MaxStreamTheme.ElevatedSurface,
    onSurfaceVariant = MaxStreamTheme.TextSecondary
)

@Composable
fun MaxStreamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
