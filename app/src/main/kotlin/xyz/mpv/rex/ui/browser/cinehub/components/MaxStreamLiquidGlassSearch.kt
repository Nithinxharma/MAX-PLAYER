package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Ultra-Polished Liquid Glass Morphing Search Bar:
 * - Collapsed: Elegant frosted glass pill capsule with specular highlights and luminous accent glow.
 * - Expanded: Seamlessly expands into a full-width liquid glass search bar with single unified clear/close action.
 * - Single Unified Action: Zero dual close buttons; smoothly animates between clear query and collapse actions.
 * - Responsive glass styling with iridescent border highlights for Dark and Light themes.
 */
@Composable
fun MaxStreamLiquidGlassSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search titles, movies, series & anime…"
) {
    val isDark = isSystemInDarkTheme()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val placeholderTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    
    val collapsedGlassBg = if (isDark) {
        Color(0x35141A2E)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    
    val expandedGlassBg = if (isDark) {
        Color(0x5511172A)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }

    val glassBorderColor by animateColorAsState(
        targetValue = if (isExpanded) {
            if (isDark) MaxStreamTheme.CrimsonAccent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
        } else {
            if (isDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        },
        label = "glass_search_border_color"
    )

    val activeBorderBrush = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                MaxStreamTheme.CrimsonAccent,
                MaxStreamTheme.ElectricCyan.copy(alpha = 0.9f),
                MaxStreamTheme.CrimsonAccent
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.primary
            )
        )
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        if (!isExpanded) {
            // Collapsed Liquid Glass Pill Capsule
            Surface(
                onClick = { onExpandedChange(true) },
                shape = RoundedCornerShape(24.dp),
                color = collapsedGlassBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, glassBorderColor),
                shadowElevation = if (isDark) 8.dp else 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("liquid_glass_search_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (query.isNotBlank()) query else placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (query.isNotBlank()) primaryTextColor else placeholderTextColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    // Decorative Liquid Specular Dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaxStreamTheme.ElectricCyan,
                                        MaxStreamTheme.CrimsonAccent
                                    )
                                )
                            )
                    )
                }
            }
        } else {
            // Expanded Full Liquid Glass Search Input with glowing iridescent border
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = expandedGlassBg,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, activeBorderBrush),
                shadowElevation = if (isDark) 14.dp else 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("liquid_glass_search_input_container")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                color = placeholderTextColor,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = primaryTextColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(if (isDark) MaxStreamTheme.CrimsonAccent else MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("liquid_glass_search_text_field")
                        )
                    }

                    // Single Unified Action Button (No Dual Close Buttons!)
                    // - When query is non-empty: Clears search text
                    // - When query is empty: Collapses the search bar
                    IconButton(
                        onClick = {
                            if (query.isNotEmpty()) {
                                onQueryChange("")
                            } else {
                                onExpandedChange(false)
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        AnimatedContent(
                            targetState = query.isNotEmpty(),
                            transitionSpec = {
                                (fadeIn(animationSpec = spring()) + scaleIn(initialScale = 0.8f))
                                    .togetherWith(fadeOut(animationSpec = spring()) + scaleOut(targetScale = 0.8f))
                            },
                            label = "search_trailing_icon"
                        ) { hasQuery ->
                            if (hasQuery) {
                                Icon(
                                    imageVector = Icons.Rounded.Cancel,
                                    contentDescription = "Clear Search",
                                    tint = primaryTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close Search",
                                    tint = primaryTextColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
