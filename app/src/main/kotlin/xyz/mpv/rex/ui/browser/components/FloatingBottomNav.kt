package xyz.mpv.rex.ui.browser.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
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
 * Material 3 Style 8: Modern Line Glassmorphism Navigation Bar.
 *
 * Characteristics:
 * - Floating glass container with semi-transparent frosted background and subtle blur effect
 * - Large rounded corners (32.dp) and soft luminous border
 * - Active item: brighter dynamic icon color, soft radial icon glow, smooth spring scale,
 *   and a thin animated active line indicator under the tab
 * - Inactive items: clean, minimal appearance with reduced opacity
 * - Center MaxStream logo: slightly larger than standard icons without being a bulky FAB
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

  // Theme-aware frosted glass container colors
  val containerBg = if (isDark) {
    Color(0x8014161F)
  } else {
    Color(0xB3F6F7FA)
  }

  val containerBorder = if (isDark) {
    Color.White.copy(alpha = 0.15f)
  } else {
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      shape = RoundedCornerShape(32.dp),
      color = containerBg,
      shadowElevation = 10.dp,
      tonalElevation = 0.dp,
      border = BorderStroke(1.dp, containerBorder),
      modifier = Modifier
        .widthIn(max = 460.dp)
        .height(64.dp)
        .clip(RoundedCornerShape(32.dp))
        .intelligentGlassEffect(
          shape = RoundedCornerShape(32.dp),
          backgroundColor = containerBg,
          borderColor = containerBorder,
          borderWidth = 1.dp
        )
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tab ->
          val isSelected = selectedTab == index
          val isMaxStream = tab.id == "cinehub" || tab.id == "maxstream" ||
              tab.label.contains("MaxStream", ignoreCase = true)

          // Smooth animations for icon scale & spring physics
          val iconScale by animateFloatAsState(
            targetValue = if (isSelected) (if (isMaxStream) 1.12f else 1.10f) else 1.0f,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_icon_scale"
          )

          // Animated colors: active primary tint vs inactive low-opacity onSurfaceVariant
          val iconColor by animateColorAsState(
            targetValue = if (isSelected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
            animationSpec = tween(durationMillis = 220),
            label = "tab_icon_color"
          )

          // Soft icon glow background alpha animation
          val glowAlpha by animateFloatAsState(
            targetValue = if (isSelected) 0.22f else 0.0f,
            animationSpec = tween(durationMillis = 220),
            label = "tab_glow_alpha"
          )

          // Thin animated line indicator width and alpha
          val indicatorWidth by animateDpAsState(
            targetValue = if (isSelected) (if (isMaxStream) 26.dp else 20.dp) else 0.dp,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioLowBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_line_indicator_width"
          )

          val indicatorAlpha by animateFloatAsState(
            targetValue = if (isSelected) 1.0f else 0.0f,
            animationSpec = tween(durationMillis = 200),
            label = "tab_line_indicator_alpha"
          )

          val interactionSource = remember { MutableInteractionSource() }

          Box(
            modifier = Modifier
              .size(width = 56.dp, height = 54.dp)
              .clip(RoundedCornerShape(20.dp))
              .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 26.dp),
                onClick = { onTabSelected(index) }
              )
              .testTag("tab_${tab.id}"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.size(width = 56.dp, height = 54.dp)
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(38.dp)
              ) {
                // Soft glow effect behind active icon
                if (glowAlpha > 0.01f) {
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .graphicsLayer {
                        alpha = glowAlpha
                        scaleX = iconScale
                        scaleY = iconScale
                      }
                      .clip(CircleShape)
                      .background(
                        Brush.radialGradient(
                          colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                          )
                        )
                      )
                  )
                }

                // Main navigation icon (MaxStream is slightly larger)
                val baseIconSize = if (isMaxStream) 28.dp else 23.dp
                Icon(
                  imageVector = tab.icon,
                  contentDescription = tab.label,
                  modifier = Modifier
                    .size(baseIconSize)
                    .graphicsLayer {
                      scaleX = iconScale
                      scaleY = iconScale
                    },
                  tint = iconColor
                )
              }

              Spacer(modifier = Modifier.height(2.dp))

              // Thin animated line indicator directly under the active tab
              Box(
                modifier = Modifier
                  .height(3.dp)
                  .width(indicatorWidth)
                  .graphicsLayer {
                    alpha = indicatorAlpha
                  }
                  .clip(RoundedCornerShape(1.5.dp))
                  .background(MaterialTheme.colorScheme.primary)
              )
            }
          }
        }
      }
    }
  }
}

