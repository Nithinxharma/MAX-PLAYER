package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

data class ContentRailItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val subtitle: String? = null,
    val rating: Double? = null,
    val qualityBadge: String? = null,
    val dubSubBadge: String? = null,
    val isNew: Boolean = false,
    val watchProgress: Float? = null,
    val rawPayload: Any? = null
)

/**
 * Modern Flagship Content Rail / Row.
 * Features:
 * - Fluid horizontal scrolling with optimized memory footprint
 * - Clear visual hierarchy: Section Title, Count / Tag Pill, Expand Action
 * - Focus-enabled TV support with smooth snapping & hover elevation
 */
@Composable
fun MaxStreamContentRail(
    title: String,
    items: List<ContentRailItem>,
    modifier: Modifier = Modifier,
    categoryBadge: String? = null,
    cardWidth: Dp = 150.dp,
    onItemClick: (ContentRailItem) -> Unit,
    onSeeAllClick: (() -> Unit)? = null
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("content_rail_${title.take(15)}")
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = MaxStreamTheme.TextPrimary
                )

                if (!categoryBadge.isNullOrBlank()) {
                    Surface(
                        shape = MaxStreamTheme.BadgeShape,
                        color = MaxStreamTheme.GlassSurfaceActive
                    ) {
                        Text(
                            text = categoryBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaxStreamTheme.ElectricCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (onSeeAllClick != null) {
                val seeAllInteraction = remember { MutableInteractionSource() }
                val isSeeAllFocused by seeAllInteraction.collectIsFocusedAsState()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = seeAllInteraction,
                            indication = null,
                            onClick = onSeeAllClick
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isSeeAllFocused) MaxStreamTheme.CrimsonAccent else MaxStreamTheme.TextSecondary
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "See all $title",
                        tint = if (isSeeAllFocused) MaxStreamTheme.CrimsonAccent else MaxStreamTheme.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Rail Items List
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                MaxStreamPosterCard(
                    title = item.title,
                    posterUrl = item.posterUrl,
                    subtitle = item.subtitle,
                    rating = item.rating,
                    qualityBadge = item.qualityBadge,
                    dubSubBadge = item.dubSubBadge,
                    isNew = item.isNew,
                    watchProgress = item.watchProgress,
                    cardWidth = cardWidth,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}
