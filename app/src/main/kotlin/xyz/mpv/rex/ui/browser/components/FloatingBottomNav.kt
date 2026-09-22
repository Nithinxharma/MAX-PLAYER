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

import xyz.mpv.rex.ui.player.controls.components.glassSurface

data class NavTabItem(
  val id: String,
  val label: String,
  val icon: ImageVector? = null,
  val iconResId: Int? = null,
)

/**
 * Reimagined Glassmorphism Navigation Bar built strictly using player controls glass surface styling.
 *
 * Features:
 * - Direct `glassSurface` modifier matching REX Player Controls (directional inner highlight & drop shadow)
 * - Icons ONLY (text hidden) with individual player-control-like button glass capsules for selected tab
 * - Center MaxStream tab using `R.drawable.ic_max_stream_mark` (exact brand mark from CineHub top bar)
 * - Spring scale physics & glowing accent indicator
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

  // Original player controls glass background
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
        .widthIn(max = 420.dp)
        .height(62.dp)
        .glassSurface(
          shape = RoundedCornerShape(31.dp),
          backgroundColor = containerBg,
          borderColor = Color.White.copy(alpha = 0.18f),
          borderWidth = 1.dp,
          innerHighlightColor = Color.White.copy(alpha = 0.30f),
          innerHighlightBlur = 5.dp,
          innerHighlightOffsetX = (-2).dp,
          innerHighlightOffsetY = (-2).dp,
          innerShadowColor = Color.Black.copy(alpha = 0.40f),
          innerShadowBlur = 6.dp,
          innerShadowOffsetX = 2.dp,
          innerShadowOffsetY = 2.dp
        ),
      contentAlignment = Alignment.Center
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
            targetValue = if (isSelected) 1.15f else 1.0f,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "tab_icon_scale"
          )

          // Icon tint transition
          val tabContentColor by animateColorAsState(
            targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.50f),
            animationSpec = tween(durationMillis = 180),
            label = "tab_content_color"
          )

          // Selected tab glass button modifier (Player controls style)
          val tabGlassModifier = if (isSelected) {
            Modifier.glassSurface(
              shape = RoundedCornerShape(22.dp),
              backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
              borderColor = Color.White.copy(alpha = 0.30f),
              borderWidth = 1.dp,
              innerHighlightColor = Color.White.copy(alpha = 0.40f),
              innerHighlightBlur = 4.dp,
              innerHighlightOffsetX = (-1).dp,
              innerHighlightOffsetY = (-1).dp,
              innerShadowColor = Color.Black.copy(alpha = 0.30f),
              innerShadowBlur = 4.dp,
              innerShadowOffsetX = 1.dp,
              innerShadowOffsetY = 1.dp
            )
          } else {
            Modifier
          }

          val interactionSource = remember { MutableInteractionSource() }

          Box(
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .then(tabGlassModifier)
              .clip(RoundedCornerShape(22.dp))
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
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (isMaxStream) 34.dp else 24.dp)
              ) {
                if (isMaxStream) {
                  // Center MaxStream logo badge with exact brand mark from CineHubScreen top bar
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .size(32.dp)
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
                      painter = painterResource(id = tab.iconResId ?: R.drawable.ic_max_stream_mark),
                      contentDescription = tab.label,
                      modifier = Modifier.size(22.dp)
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

              if (isSelected && !isMaxStream) {
                Spacer(modifier = Modifier.height(2.dp))
                // Glowing indicator dot below standard tab icon
                Box(
                  modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                )
              }
            }
          }
        }
      }
    }
  }
}


