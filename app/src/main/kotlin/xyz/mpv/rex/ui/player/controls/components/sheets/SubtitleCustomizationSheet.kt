package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Flagship Glassmorphic Subtitle Customization Sheet.
 * Provides live controls for subtitle font size, colors, outline, vertical alignment, and language preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleCustomizationSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier
) {
  val preferences = koinInject<SubtitlesPreferences>()

  val fontSizeVal by preferences.fontSize.collectAsState()
  val fontColorVal by preferences.textColor.collectAsState()
  val bgColorVal by preferences.backgroundColor.collectAsState()
  val borderSizeVal by preferences.borderSize.collectAsState()
  val subPosVal by preferences.subPos.collectAsState()
  val isBold by preferences.bold.collectAsState()
  val prefLangVal by preferences.preferredLanguages.collectAsState()

  val colorOptions = listOf(
    Color.White,
    Color(0xFFFFEB3B), // Yellow
    Color(0xFF00E676), // Electric Green
    Color(0xFF00B0FF), // Electric Blue
    Color(0xFFFF4081)  // Neon Pink
  )

  val bgOptions = listOf(
    Color.Transparent,
    Color.Black.copy(alpha = 0.5f),
    Color.Black.copy(alpha = 0.85f),
    Color(0xFF1A1D28)
  )

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = Color(0xFF0C0F17).copy(alpha = 0.96f),
    dragHandle = {
      BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f))
    },
    modifier = modifier.testTag("subtitle_customization_sheet")
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      // Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Palette,
              contentDescription = null,
              tint = MaxStreamTheme.ElectricCyan,
              modifier = Modifier.size(24.dp)
            )
            Text(
              text = "Subtitle Styling Engine",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
              ),
              color = Color.White
            )
          }

          IconButton(
            onClick = onDismissRequest,
            modifier = Modifier
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.10f))
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = Color.White
            )
          }
        }
      }

      // Live Subtitle Preview Box
      item {
        val previewBg = Color(bgColorVal)
        val previewTextColor = Color(fontColorVal)
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFF161A26),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
          modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(previewBg)
                .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
              Text(
                text = "MaxStream Subtitle Preview • Sub/Dub 1080p",
                fontSize = (fontSizeVal / 3.8).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                color = previewTextColor
              )
            }
          }
        }
      }

      // Font Size Control
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.FormatSize, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
              Text("Font Size", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text("${fontSizeVal}pt", style = MaterialTheme.typography.labelMedium, color = MaxStreamTheme.ElectricCyan, fontWeight = FontWeight.Bold)
          }

          Slider(
            value = fontSizeVal.toFloat(),
            onValueChange = { preferences.fontSize.set(it.toInt()) },
            valueRange = 25f..90f,
            colors = SliderDefaults.colors(
              thumbColor = MaxStreamTheme.CrimsonAccent,
              activeTrackColor = MaxStreamTheme.CrimsonAccent,
              inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
          )
        }
      }

      // Text Color Picker
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Text Color", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colorOptions.forEach { col ->
              val isSelected = fontColorVal == col.toArgb()
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(col)
                  .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaxStreamTheme.ElectricCyan else Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                  )
                  .clickable { preferences.textColor.set(col.toArgb()) }
              )
            }
          }
        }
      }

      // Background Color & Corner Style
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.FormatColorFill, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Text("Background Style", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            bgOptions.forEachIndexed { idx, bgCol ->
              val isSelected = bgColorVal == bgCol.toArgb()
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (bgCol == Color.Transparent) Color.White.copy(alpha = 0.1f) else bgCol,
                border = androidx.compose.foundation.BorderStroke(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) MaxStreamTheme.ElectricCyan else Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .clickable { preferences.backgroundColor.set(bgCol.toArgb()) }
              ) {
                Text(
                  text = when (idx) {
                    0 -> "None"
                    1 -> "Glass 50%"
                    2 -> "Solid Black"
                    else -> "Dark Slate"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }

      // Vertical Offset Position Slider
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.Tune, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
              Text("Vertical Position", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text("$subPosVal%", style = MaterialTheme.typography.labelMedium, color = MaxStreamTheme.ElectricCyan, fontWeight = FontWeight.Bold)
          }

          Slider(
            value = subPosVal.toFloat(),
            onValueChange = { preferences.subPos.set(it.toInt()) },
            valueRange = 50f..100f,
            colors = SliderDefaults.colors(
              thumbColor = MaxStreamTheme.ElectricCyan,
              activeTrackColor = MaxStreamTheme.ElectricCyan,
              inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
          )
        }
      }

      // Bold Text Toggle
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Bold Subtitle Text", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
          Switch(
            checked = isBold,
            onCheckedChange = { preferences.bold.set(it) },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = MaxStreamTheme.CrimsonAccent
            )
          )
        }
      }

      // Preferred Language Quick Presets
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Language, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Text("Preferred Auto Subtitle Language", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
          }

          val languages = listOf(
            "en" to "English",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "ja" to "Japanese",
            "hi" to "Hindi"
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            languages.forEach { (code, label) ->
              val isSelected = prefLangVal.contains(code, ignoreCase = true)
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaxStreamTheme.CrimsonAccent else Color.White.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaxStreamTheme.CrimsonAccent else Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .clickable { preferences.preferredLanguages.set(code) }
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
