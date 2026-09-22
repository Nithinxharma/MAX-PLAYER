package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Quality Badge (e.g. 4K, 1080p, 720p, 480p, HD).
 */
@Composable
fun MaxStreamQualityBadge(
    quality: String,
    modifier: Modifier = Modifier
) {
    val isUltra = quality.equals("4K", ignoreCase = true) || quality.equals("2160p", ignoreCase = true)
    val bgColor = if (isUltra) {
        MaxStreamTheme.CrimsonAccent.copy(alpha = 0.95f)
    } else {
        MaxStreamTheme.ElectricCyan.copy(alpha = 0.85f)
    }
    val textColor = if (isUltra) Color.White else Color.Black

    Surface(
        shape = MaxStreamTheme.BadgeShape,
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = quality.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            ),
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/**
 * Audio / Dub / Sub Badge (e.g. SUB, DUB, MULTI AUDIO, RAW).
 */
@Composable
fun MaxStreamDubSubBadge(
    dubSub: String,
    modifier: Modifier = Modifier
) {
    val upper = dubSub.uppercase()
    val (bgColor, textColor) = when {
        upper.contains("MULTI") -> Color(0xFF8B5CF6).copy(alpha = 0.90f) to Color.White
        upper.contains("DUB") -> Color(0xFFF59E0B).copy(alpha = 0.90f) to Color.Black
        upper.contains("SUB") -> Color(0xFF10B981).copy(alpha = 0.90f) to Color.White
        upper.contains("RAW") -> Color(0xFF64748B).copy(alpha = 0.90f) to Color.White
        else -> Color.Black.copy(alpha = 0.75f) to Color.White
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = upper,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp
            ),
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

/**
 * "NEW" release badge.
 */
@Composable
fun MaxStreamNewBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFEF4444),
        modifier = modifier
    ) {
        Text(
            text = "NEW",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

/**
 * Star Rating capsule badge.
 */
@Composable
fun MaxStreamRatingBadge(
    rating: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.75f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaxStreamTheme.AmberGold,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = String.format("%.1f", rating),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
    }
}
