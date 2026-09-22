package xyz.mpv.rex.ui.browser.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect

data class NavTabItem(
  val id: String,
  val label: String,
  val icon: ImageVector? = null,
  val iconResId: Int? = null,
)

/**
 * Modern Glassmorphism Navigation Bar with Center MaxStream "M" Logo Badge.
 *
 * Features:
 * - High contrast dark frosted glass container (100% visible in both light & dark themes)
 * - Neon luminous gradient border outline
 * - Center MaxStream tab with glowing multi-color "M" logo badge
 * - Text labels under every tab icon
 * - Active tab animated indicator line & spring bounce scale physics
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

  // High contrast frosted glass container (sleek dark pill visible in both themes)
  val containerBg = if (isDark) {
    Color(0xEB0D0E17)
  } else {
    Color(0xF5141624)
  }

  // Neon gradient rim glow border
  val borderGradient = Brush.horizontalGradient(
    colors = listOf(
      Color(0xFF802D92),
      Color(0xFF007AFF),
      Color(0xFFFF2D92),
      Color(0xFF32D7FF)
    )
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      shape = RoundedCornerShape(36.dp),
      color = containerBg,
      shadowElevation = 16.dp,
      tonalElevation = 0.dp,
      border = BorderStroke(1.2.dp, borderGradient),
      modifier = Modifier
        .widthIn(max = 480.dp)
        .height(72.dp)
        .clip(RoundedCornerShape(36.dp))
        .intelligentGlassEffect(
          shape = RoundedCornerShape(36.dp),
          backgroundColor = containerBg,
          borderColor = Color.White.copy(alpha = 0.20f),
          borderWidth = 1.2.dp
        )
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tab ->
          val isSelected = selectedTab == index
          val isMaxStream = tab.id == "cinehub" || tab.id == "maxstream" ||
              tab.iconResId != null || tab.label.contains("MaxStream", ignoreCase = true)

          // Smooth spring scale physics
          val iconScale by animateFloatAsState(
            targetValue = if (isSelected) (if (isMaxStream) 1.15f else 1.12f) else 1.0f,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_icon_scale"
          )

          // Icon and label color transitions
          val activeColor = MaterialTheme.colorScheme.primary
          val tabContentColor by animateColorAsState(
            targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
            animationSpec = tween(durationMillis = 200),
            label = "tab_content_color"
          )

          // Soft icon glow behind active tab
          val glowAlpha by animateFloatAsState(
            targetValue = if (isSelected) 0.35f else 0.0f,
            animationSpec = tween(durationMillis = 200),
            label = "tab_glow_alpha"
          )

          // Active indicator line width & opacity
          val indicatorWidth by animateDpAsState(
            targetValue = if (isSelected) (if (isMaxStream) 28.dp else 22.dp) else 0.dp,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioLowBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_indicator_width"
          )

          val indicatorAlpha by animateFloatAsState(
            targetValue = if (isSelected) 1.0f else 0.0f,
            animationSpec = tween(durationMillis = 180),
            label = "tab_indicator_alpha"
          )

          val interactionSource = remember { MutableInteractionSource() }

          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(24.dp))
              .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 30.dp),
                onClick = { onTabSelected(index) }
              )
              .testTag("tab_${tab.id}"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(vertical = 4.dp)
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (isMaxStream) 42.dp else 28.dp)
              ) {
                // Soft radial glow for active tab
                if (glowAlpha > 0.01f) {
                  Box(
                    modifier = Modifier
                      .size(if (isMaxStream) 44.dp else 30.dp)
                      .graphicsLayer {
                        alpha = glowAlpha
                        scaleX = iconScale
                        scaleY = iconScale
                      }
                      .clip(CircleShape)
                      .background(
                        Brush.radialGradient(
                          colors = listOf(
                            Color(0xFFFF2D92).copy(alpha = 0.70f),
                            Color(0xFF007AFF).copy(alpha = 0.30f),
                            Color.Transparent
                          )
                        )
                      )
                  )
                }

                if (isMaxStream) {
                  // Center MaxStream "M" Logo glowing circular badge
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .size(38.dp)
                      .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                      }
                      .clip(CircleShape)
                      .background(
                        Brush.linearGradient(
                          colors = listOf(
                            Color(0xFF2E1236),
                            Color(0xFF121B36)
                          )
                        )
                      )
                      .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                          colors = listOf(
                            Color(0xFFFF2D92),
                            Color(0xFFA855F7),
                            Color(0xFF007AFF)
                          )
                        ),
                        shape = CircleShape
                      )
                  ) {
                    Image(
                      painter = painterResource(id = tab.iconResId ?: R.drawable.ic_max_stream_logo),
                      contentDescription = tab.label,
                      modifier = Modifier.size(28.dp)
                    )
                  }
                } else {
                  // Standard tab icon
                  if (tab.iconResId != null) {
                    Image(
                      painter = painterResource(id = tab.iconResId),
                      contentDescription = tab.label,
                      modifier = Modifier
                        .size(23.dp)
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
                        .size(22.dp)
                        .graphicsLayer {
                          scaleX = iconScale
                          scaleY = iconScale
                        },
                      tint = tabContentColor
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(2.dp))

              // Text Label under icon
              Text(
                text = tab.label,
                color = tabContentColor,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(3.dp))

              // Glowing indicator line directly below label
              Box(
                modifier = Modifier
                  .height(2.5.dp)
                  .width(indicatorWidth)
                  .graphicsLayer {
                    alpha = indicatorAlpha
                  }
                  .clip(RoundedCornerShape(1.25.dp))
                  .background(
                    Brush.horizontalGradient(
                      colors = listOf(
                        Color(0xFFFF2D92),
                        Color(0xFF32D7FF)
                      )
                    )
                  )
              )
            }
          }
        }
      }
    }
  }
}


