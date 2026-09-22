package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass

data class TvNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val iconResId: Int? = null
)

/**
 * Dedicated Android TV Collapsible Navigation Rail.
 * Collapsed state: 72dp width, showing clean glowing icons.
 * Expanded state: 220dp width on focus/hover, displaying vibrant labels.
 */
@Composable
fun MaxStreamTvSidebar(
    items: List<TvNavItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val railWidth by animateDpAsState(
        targetValue = if (isExpanded) 210.dp else 74.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tv_sidebar_width"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .zIndex(50f)
            .shadow(elevation = 20.dp, spotColor = Color.Black)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(MaxStreamTheme.MidnightSurface.copy(alpha = 0.95f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            )
            .testTag("maxstream_tv_sidebar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Mark Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable { isExpanded = !isExpanded }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaxStreamTheme.GlassSurfaceActive,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_max_stream_mark),
                            contentDescription = "Max Stream",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                if (isExpanded) {
                    Text(
                        text = "MAX STREAM",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            fontSize = 15.sp
                        ),
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Items List
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items.forEach { item ->
                    val isSelected = item.id == selectedItemId
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val isActive = isFocused || isHovered

                    // Expand sidebar when focus moves into any item
                    if (isFocused && !isExpanded) {
                        isExpanded = true
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isSelected -> MaxStreamTheme.CrimsonAccent.copy(alpha = 0.25f)
                            isActive -> Color.White.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        },
                        border = when {
                            isSelected -> androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                MaxStreamTheme.CrimsonAccent
                            )
                            isActive -> androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.4f)
                            )
                            else -> null
                        },
                        modifier = Modifier
                            .fillMaxHeight(fraction = 0f) // wrap
                            .then(if (isExpanded) Modifier.width(186.dp) else Modifier.size(48.dp))
                            .focusable(interactionSource = interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onItemSelected(item.id) }
                            )
                            .testTag("tv_nav_${item.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (item.iconResId != null) {
                                Image(
                                    painter = painterResource(id = item.iconResId),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (item.icon != null) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) MaxStreamTheme.CrimsonAccent else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    color = if (isSelected) Color.White else MaxStreamTheme.TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Collapse Toggle or Footer info
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Toggle Sidebar",
                    tint = MaxStreamTheme.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
