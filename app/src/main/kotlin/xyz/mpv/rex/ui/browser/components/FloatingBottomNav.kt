package xyz.mpv.rex.ui.browser.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.player.controls.components.glassSurface

data class NavTabItem(
  val id: String,
  val label: String,
  val icon: ImageVector? = null,
  val iconResId: Int? = null,
)

/**
 * Reimagined Glassmorphism Navigation Bar built strictly using REX Player Controls glass surface styling.
 *
 * Requirements:
 * - Authentic `glassSurface` modifier matching player controls (directional inner highlights & soft shadows)
 * - Icons ONLY (text hidden)
 * - Uses exact CineHub brand mark `R.drawable.ic_max_stream_mark` without unnatural nested borders
 * - Individual circular glass button state for selected tab
 * - Spring physics for tactile feedback
 */
@Composable
fun FloatingBottomNav(
  tabs: List<NavTabItem>,
  selectedTab: Int,
  onTabSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (tabs.isEmpty()) return

  val isDark = isSystemInDarkTheme()

  // Original REX player controls glass container background
  val containerBg = if (isDark) {
    Color(0x3B12131D)
  } else {
    Color(0x45141624)
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .widthIn(max = 440.dp)
        .height(62.dp)
        .glassSurface(
          shape = RoundedCornerShape(31.dp),
          backgroundColor = containerBg,
          borderColor = Color.White.copy(alpha = 0.16f),
          borderWidth = 1.dp,
          innerHighlightColor = Color.White.copy(alpha = 0.30f),
          innerHighlightBlur = 6.dp,
          innerHighlightOffsetX = (-2).dp,
          innerHighlightOffsetY = (-2).dp,
          innerShadowColor = Color.Black.copy(alpha = 0.35f),
          innerShadowBlur = 6.dp,
          innerShadowOffsetX = 2.dp,
          innerShadowOffsetY = 2.dp
        ),
      contentAlignment = Alignment.Center
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tab ->
          val isSelected = selectedTab == index
          val isMaxStream = tab.id == "cinehub" || tab.id == "maxstream" ||
              tab.iconResId == R.drawable.ic_max_stream_mark ||
              tab.label.contains("MaxStream", ignoreCase = true) ||
              tab.label.contains("CineHub", ignoreCase = true)

          // Smooth spring scale animation
          val iconScale by animateFloatAsState(
            targetValue = if (isSelected) 1.12f else 1.0f,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_icon_scale"
          )

          // Icon tint transition
          val tabContentColor by animateColorAsState(
            targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
            animationSpec = tween(durationMillis = 180),
            label = "tab_content_color"
          )

          // Selected tab circular glass button (Player Controls style)
          val tabGlassModifier = if (isSelected) {
            Modifier.glassSurface(
              shape = CircleShape,
              backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
              borderColor = Color.White.copy(alpha = 0.35f),
              borderWidth = 1.dp,
              innerHighlightColor = Color.White.copy(alpha = 0.45f),
              innerHighlightBlur = 4.dp,
              innerHighlightOffsetX = (-1.5).dp,
              innerHighlightOffsetY = (-1.5).dp,
              innerShadowColor = Color.Black.copy(alpha = 0.30f),
              innerShadowBlur = 4.dp,
              innerShadowOffsetX = 1.5.dp,
              innerShadowOffsetY = 1.5.dp
            )
          } else {
            Modifier
          }

          val interactionSource = remember { MutableInteractionSource() }

          Box(
            modifier = Modifier
              .size(46.dp)
              .then(tabGlassModifier)
              .clip(CircleShape)
              .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 24.dp),
                onClick = { onTabSelected(index) }
              )
              .testTag("tab_${tab.id}"),
            contentAlignment = Alignment.Center
          ) {
            if (isMaxStream) {
              // Exact MaxStream brand mark used beside the name on CineHub top bar
              Image(
                painter = painterResource(id = R.drawable.ic_max_stream_mark),
                contentDescription = tab.label,
                modifier = Modifier
                  .size(28.dp)
                  .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                  }
              )
            } else if (tab.iconResId != null) {
              Image(
                painter = painterResource(id = tab.iconResId),
                contentDescription = tab.label,
                modifier = Modifier
                  .size(24.dp)
                  .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                  }
              )
            } else if (tab.icon != null) {
              Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                modifier = Modifier
                  .size(24.dp)
                  .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                  },
                tint = tabContentColor
              )
            }
          }
        }
      }
    }
  }
}
