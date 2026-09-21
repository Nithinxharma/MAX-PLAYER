package xyz.mpv.rex.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect

data class NavTabItem(
  val id: String,
  val label: String,
  val icon: ImageVector,
)

/**
 * OpenTune-style floating pill navigation bar.
 * Features a floating rounded pill container with circular indicator for the active tab,
 * smooth scale and fade spring animations, and Material Symbols Rounded icons.
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

  // Theme-aware container colors matching OpenTune style
  val containerBg = if (isDark) {
    Color(0xFF1C1D22)
  } else {
    Color(0xFFF3ECE6)
  }
  val containerBorder = if (isDark) {
    Color.White.copy(alpha = 0.12f)
  } else {
    Color(0xFFE5DDD5)
  }

  // Selected tab circular background colors matching OpenTune screenshot
  val selectedPillColor = if (isDark) {
    Color(0xFF4A2E26).copy(alpha = 0.90f)
  } else {
    Color(0xFFF6D9D0)
  }
  val selectedIconColor = if (isDark) {
    Color.White
  } else {
    Color(0xFF2C1510)
  }
  val unselectedIconColor = if (isDark) {
    Color.White.copy(alpha = 0.60f)
  } else {
    Color(0xFF756E68)
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      shape = RoundedCornerShape(36.dp),
      color = containerBg,
      shadowElevation = 8.dp,
      tonalElevation = 2.dp,
      border = BorderStroke(1.dp, containerBorder),
      modifier = Modifier
        .widthIn(max = 440.dp)
        .height(64.dp)
        .clip(RoundedCornerShape(36.dp))
        .intelligentGlassEffect(
          shape = RoundedCornerShape(36.dp),
          backgroundColor = containerBg.copy(alpha = if (isDark) 0.85f else 0.95f),
          borderColor = containerBorder
        )
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tab ->
          val isSelected = selectedTab == index

          // Smooth spring scale animation for the icon using SpringSpec
          val iconScale by animateFloatAsState(
            targetValue = if (isSelected) 1.15f else 1.0f,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_icon_scale"
          )

          // Smooth spring animation for the active circular/pill background using SpringSpec
          val pillScale by animateFloatAsState(
            targetValue = if (isSelected) 1.0f else 0.4f,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_pill_scale"
          )

          val pillAlpha by animateFloatAsState(
            targetValue = if (isSelected) 1.0f else 0.0f,
            animationSpec = tween(durationMillis = 200),
            label = "tab_pill_alpha"
          )

          val interactionSource = remember { MutableInteractionSource() }

          Box(
            modifier = Modifier
              .size(52.dp)
              .clip(CircleShape)
              .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 26.dp),
                onClick = { onTabSelected(index) }
              )
              .testTag("tab_${tab.id}"),
            contentAlignment = Alignment.Center
          ) {
            // Active background circular pill
            Box(
              modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                  scaleX = pillScale
                  scaleY = pillScale
                  alpha = pillAlpha
                }
                .clip(CircleShape)
                .background(selectedPillColor)
            )

            // Tab icon (Material Symbols Rounded)
            Icon(
              imageVector = tab.icon,
              contentDescription = tab.label,
              modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                  scaleX = iconScale
                  scaleY = iconScale
                },
              tint = if (isSelected) selectedIconColor else unselectedIconColor
            )
          }
        }
      }
    }
  }
}
