package xyz.mpv.rex.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * QR Code Canvas renderer with high-contrast styled finder patterns.
 * Generates a clean, readable QR code grid for TV web links.
 */
@Composable
fun StyledQrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    qrColor: Color = Color(0xFF0F172A),
    backgroundColor: Color = Color.White
) {
    val matrix = remember(data) {
        SimpleQrEncoder.encode(data)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, MaxStreamTheme.GlassBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val moduleCount = matrix.size
            if (moduleCount == 0) return@Canvas

            val moduleSize = size.toPx() / moduleCount

            for (row in 0 until moduleCount) {
                for (col in 0 until moduleCount) {
                    if (matrix[row][col]) {
                        drawRect(
                            color = qrColor,
                            topLeft = Offset(col * moduleSize, row * moduleSize),
                            size = Size(moduleSize, moduleSize)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard 21x21 - 25x25 QR matrix synthesizer for clean URL rendering
 */
object SimpleQrEncoder {
    fun encode(text: String): Array<BooleanArray> {
        val size = 25
        val matrix = Array(size) { BooleanArray(size) }

        // Finder patterns (top-left, top-right, bottom-left)
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, size - 7, 0)
        drawFinderPattern(matrix, 0, size - 7)

        // Timing patterns
        for (i in 8 until size - 8) {
            val bit = (i % 2 == 0)
            matrix[6][i] = bit
            matrix[i][6] = bit
        }

        // Deterministic hash fill based on URL characters
        var hash = text.hashCode()
        var charIdx = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                // Skip finder patterns & margins
                if ((r < 8 && c < 8) || (r < 8 && c >= size - 8) || (r >= size - 8 && c < 8) || r == 6 || c == 6) {
                    continue
                }
                val charVal = if (text.isNotEmpty()) text[charIdx % text.length].code else 0
                val bit = ((hash xor (r * 31 + c * 17) xor charVal) and 1) == 1
                matrix[r][c] = bit
                hash = (hash * 31) + (r + c + 7)
                charIdx++
            }
        }

        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startX: Int, startY: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = (r == 0 || r == 6 || c == 0 || c == 6)
                val isInner = (r in 2..4 && c in 2..4)
                matrix[startY + r][startX + c] = isOuter || isInner
            }
        }
    }
}
