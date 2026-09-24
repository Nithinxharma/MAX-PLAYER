package xyz.mpv.rex.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.tv.model.TvPlaybackState
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass

@Composable
fun TvRemoteControlDeck(
    playbackState: TvPlaybackState,
    onCommand: (action: String, payload: Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableStateOf(0L) }

    val currentMs = if (isSeeking) seekPositionMs else playbackState.currentPositionMs
    val totalMs = if (playbackState.durationMs > 0L) playbackState.durationMs else 1L
    val progress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .maxStreamGlass(
                shape = RoundedCornerShape(22.dp),
                backgroundColor = MaxStreamTheme.GlassSurface,
                borderColor = MaxStreamTheme.GlassBorder,
                elevation = 12.dp
            )
            .padding(20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (playbackState.isPlaying) MaxStreamTheme.EmeraldLive else MaxStreamTheme.CrimsonAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (playbackState.isPlaying) "TV PLAYING" else "TV PAUSED",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = if (playbackState.isPlaying) MaxStreamTheme.EmeraldLive else MaxStreamTheme.CrimsonAccent
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCommand("fullscreen", null)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen on TV",
                    tint = MaxStreamTheme.ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Slider
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                isSeeking = true
                seekPositionMs = (newProgress * totalMs).toLong()
            },
            onValueChangeFinished = {
                isSeeking = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCommand("seek", seekPositionMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaxStreamTheme.CrimsonAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Time Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentMs),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaxStreamTheme.TextSecondary
            )
            Text(
                text = formatDuration(playbackState.durationMs),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaxStreamTheme.TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Big Tactile Playback Control Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seek -10s
            FilledIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = (playbackState.currentPositionMs - 10000L).coerceAtLeast(0L)
                    onCommand("seek", target)
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaxStreamTheme.GlassSurfaceActive,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(54.dp)
                    .border(1.dp, MaxStreamTheme.GlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Rewind 10 seconds",
                    modifier = Modifier.size(26.dp)
                )
            }

            // Central Glowing Play/Pause Button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        spotColor = MaxStreamTheme.CrimsonAccent,
                        ambientColor = MaxStreamTheme.CrimsonAccent
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaxStreamTheme.CrimsonAccent,
                                Color(0xFFFF5277)
                            )
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (playbackState.isPlaying) {
                            onCommand("pause", null)
                        } else {
                            onCommand("play", null)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Seek +10s
            FilledIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = if (playbackState.durationMs > 0L) {
                        (playbackState.currentPositionMs + 10000L).coerceAtMost(playbackState.durationMs)
                    } else {
                        playbackState.currentPositionMs + 10000L
                    }
                    onCommand("seek", target)
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaxStreamTheme.GlassSurfaceActive,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(54.dp)
                    .border(1.dp, MaxStreamTheme.GlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Forward 10 seconds",
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // TV Volume Slider Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (playbackState.volume <= 0.05f) Icons.Default.VolumeMute else Icons.Default.VolumeDown,
                contentDescription = "Volume",
                tint = MaxStreamTheme.TextSecondary,
                modifier = Modifier.size(20.dp)
            )

            Slider(
                value = playbackState.volume,
                onValueChange = { newVol ->
                    onCommand("volume", newVol)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaxStreamTheme.ElectricCyan,
                    activeTrackColor = MaxStreamTheme.ElectricCyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Max Volume",
                tint = MaxStreamTheme.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSec = millis / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
