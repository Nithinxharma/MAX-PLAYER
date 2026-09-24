package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.components.GroupedListItem
import xyz.mpv.rex.presentation.components.groupedItemShape
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * MaxStream Glass card container for grouping related preferences.
 * Supports standalone (GroupPosition.ONLY) or connected positions (FIRST, MIDDLE, LAST).
 */
@Composable
fun PreferenceCard(
  modifier: Modifier = Modifier,
  position: GroupPosition = GroupPosition.ONLY,
  color: Color? = null,
  tonalElevation: Dp = 1.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val isDark = isSystemInDarkTheme()
  val shape = groupedItemShape(position)
  val defaultBg = if (isDark) MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
  val defaultBorder = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .clip(shape)
      .background(color ?: defaultBg)
      .border(1.dp, defaultBorder, shape)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      content()
    }
  }
}

/**
 * MaxStream Glass single-item preference card with connected shape geometry.
 */
@Composable
fun GroupedPreferenceCard(
  position: GroupPosition,
  modifier: Modifier = Modifier,
  highlightKey: Any? = null,
  color: Color? = null,
  tonalElevation: Dp = 1.dp,
  content: @Composable () -> Unit,
) {
  val isDark = isSystemInDarkTheme()
  val shape = groupedItemShape(position)
  val defaultBg = if (isDark) MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
  val defaultBorder = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

  val highlightModifier = if (highlightKey != null) {
    Modifier.preferenceHighlight(highlightKey, shape)
  } else {
    Modifier
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .then(highlightModifier)
      .clip(shape)
      .background(color ?: defaultBg)
      .border(1.dp, defaultBorder, shape)
  ) {
    content()
  }
}

/**
 * MaxStream Glass subtle divider to separate preferences within a card.
 */
@Composable
fun PreferenceDivider(
  modifier: Modifier = Modifier,
) {
  val isDark = isSystemInDarkTheme()
  HorizontalDivider(
    modifier = modifier.padding(horizontal = 20.dp),
    color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
  )
}

/**
 * MaxStream Glass section header for preferences.
 */
@Composable
fun PreferenceSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
) {
  val isDark = isSystemInDarkTheme()
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaxStreamTheme.CrimsonAccent,
        modifier = Modifier.size(18.dp)
      )
    }
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelMedium.copy(
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Bold
      ),
      color = if (isDark) MaxStreamTheme.CrimsonAccent else MaterialTheme.colorScheme.primary,
    )
  }
}

