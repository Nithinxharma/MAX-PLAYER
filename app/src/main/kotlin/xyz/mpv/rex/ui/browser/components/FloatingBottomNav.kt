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
 * Glassmorphism Navigation Bar styled after original player controls (icons only, hidden text names).
 *
 * Features:
 * - Player controls style glassmorphism (`intelligentGlassEffect` with translucent dark background & subtle highlight border)
 * - Icons ONLY (names hidden) for clean minimal look
 * - Center MaxStream circular logo badge (`ic_max_stream_logo`)
 * - Spring scale physics & glowing selection indicator
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

  // Original player controls glass style background & border
  val containerBg = if (isDark) {
    Color(0x3812131D)
  } else {
    Color(0x280D0E17)
  }
  val containerBorder = Color.White.copy(alpha = 0.18f)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      shape = RoundedCornerShape(30.dp),
      color = Color.Transparent,
      shadowElevation = 8.dp,
      tonalElevation = 0.dp,
      modifier = Modifier
        .widthIn(max = 440.dp)
        .height(58.dp)
        .intelligentGlassEffect(
          shape = RoundedCornerShape(30.dp),
          backgroundColor = containerBg,
          borderColor = containerBorder,
          borderWidth = 1.dp
        )
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 12.dp),
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

          // Icon tint transition
          val tabContentColor by animateColorAsState(
            targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
            animationSpec = tween(durationMillis = 200),
            label = "tab_content_color"
          )

          // Active tab glow
          val glowAlpha by animateFloatAsState(
            targetValue = if (isSelected) 0.35f else 0.0f,
            animationSpec = tween(durationMillis = 200),
            label = "tab_glow_alpha"
          )

          // Active indicator dot/bar width
          val indicatorWidth by animateDpAsState(
            targetValue = if (isSelected) (if (isMaxStream) 22.dp else 16.dp) else 0.dp,
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
              .clip(RoundedCornerShape(20.dp))
              .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 24.dp),
                onClick = { onTabSelected(index) }
              )
              .testTag("tab_${tab.id}"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(vertical = 2.dp)
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (isMaxStream) 38.dp else 26.dp)
              ) {
                // Radial glow behind active tab
                if (glowAlpha > 0.01f) {
                  Box(
                    modifier = Modifier
                      .size(if (isMaxStream) 40.dp else 28.dp)
                      .graphicsLayer {
                        alpha = glowAlpha
                        scaleX = iconScale
                        scaleY = iconScale
                      }
                      .clip(CircleShape)
                      .background(
                        Brush.radialGradient(
                          colors = listOf(
                            Color(0xFFFF2D92).copy(alpha = 0.60f),
                            Color(0xFF007AFF).copy(alpha = 0.25f),
                            Color.Transparent
                          )
                        )
                      )
                  )
                }

                if (isMaxStream) {
                  // Center MaxStream circular logo badge
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .size(36.dp)
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
                        width = 1.2.dp,
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
                      modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                    )
                  }
                } else {
                  // Standard tab icon
                  if (tab.iconResId != null) {
                    Image(
                      painter = painterResource(id = tab.iconResId),
                      contentDescription = tab.label,
                      modifier = Modifier
                        .size(22.dp)
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

              // Glowing indicator dot/bar below icon
              Box(
                modifier = Modifier
                  .height(2.5.dp)
                  .width(indicatorWidth)
                  .graphicsLayer {
                    alpha = indicatorAlpha
                  }
                  .clip(CircleShape)
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


