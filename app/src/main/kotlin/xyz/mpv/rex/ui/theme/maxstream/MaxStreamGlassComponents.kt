package xyz.mpv.rex.ui.theme.maxstream

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xyz.mpv.rex.ui.player.controls.components.glassSurface

/**
 * Flagship Reusable Glass Components for Max Stream OTT.
 */

/**
 * 1. GlassCard: Frosted glass container with specular edge and tactile feedback.
 */
@Composable
fun MaxStreamGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaxStreamTheme.CardShape,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isHovered || isPressed || isFocused

    val bg = backgroundColor ?: if (isDark) MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    val borderCol = borderColor ?: if (isHighlighted) {
        if (isDark) Color.White.copy(alpha = 0.40f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else if (isHighlighted && onClick != null) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glass_card_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isHighlighted) elevation + 4.dp else elevation,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(bg)
            .border(width = borderWidth, color = borderCol, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = if (isDark) Color.White else MaterialTheme.colorScheme.primary),
                        onClick = onClick
                    )
                } else Modifier
            ),
        content = content
    )
}

/**
 * 2. GlassButton: Cinematic frosted action button with glowing specular border.
 */
enum class GlassButtonVariant {
    Primary, Secondary, Ghost, Danger
}

@Composable
fun MaxStreamGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: GlassButtonVariant = GlassButtonVariant.Primary,
    shape: Shape = MaxStreamTheme.ButtonShape,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else if (isHovered) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "glass_btn_scale"
    )

    val (bgColor, textColor, borderColor) = when (variant) {
        GlassButtonVariant.Primary -> Triple(
            MaxStreamTheme.CrimsonAccent,
            Color.White,
            MaxStreamTheme.CrimsonAccent.copy(alpha = 0.8f)
        )
        GlassButtonVariant.Secondary -> Triple(
            if (isDark) Color(0xFF1E2638) else MaterialTheme.colorScheme.primaryContainer,
            if (isDark) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
            if (isDark) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
        GlassButtonVariant.Ghost -> Triple(
            if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            if (isDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        GlassButtonVariant.Danger -> Triple(
            MaterialTheme.colorScheme.error,
            Color.White,
            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor.copy(alpha = if (enabled) 1f else 0.4f))
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color.White),
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .maxStreamShimmer(shape = CircleShape)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    ),
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 3. GlassSearchBar: Unified OTT search capsule with debouncing & instant action.
 */
@Composable
fun MaxStreamGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search movies, series, anime, files...",
    onBackClick: (() -> Unit)? = null,
    onVoiceClick: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            if (isDark) MaxStreamTheme.CrimsonAccent else MaterialTheme.colorScheme.primary
        } else {
            if (isDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        },
        label = "search_bar_border"
    )

    val containerBg = if (isDark) Color(0x38121622) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .glassSurface(
                shape = RoundedCornerShape(26.dp),
                backgroundColor = containerBg,
                borderColor = borderColor,
                borderWidth = if (isFocused) 1.5.dp else 1.dp
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isFocused) MaxStreamTheme.CrimsonAccent else (if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .testTag("maxstream_glass_search_input"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(if (isDark) MaxStreamTheme.CrimsonAccent else MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onSearch(query)
                        }
                    )
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onVoiceClick != null && query.isEmpty()) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            trailingContent?.invoke(this)
        }
    }
}

/**
 * 4. GlassFilterChip: Frosted pill filter chip with active glow state.
 */
@Composable
fun MaxStreamGlassFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val (bg, textColor, borderColor) = if (isSelected) {
        Triple(
            MaxStreamTheme.CrimsonAccent,
            Color.White,
            MaxStreamTheme.CrimsonAccent.copy(alpha = 0.85f)
        )
    } else {
        Triple(
            if (isDark) Color(0x3B1A2234) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color.White),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

/**
 * 5. GlassTopBar: Cinematic unified top app bar.
 */
@Composable
fun MaxStreamGlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        ),
                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
}

/**
 * 6. GlassDialog: Unified OTT dialog overlay.
 */
@Composable
fun MaxStreamGlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            MaxStreamGlassCard(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = if (isDark) Color(0xF20F1420) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                borderColor = if (isDark) Color.White.copy(alpha = 0.20f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                elevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (icon != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.3f)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaxStreamTheme.CrimsonAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    content()

                    if (confirmButton != null || dismissButton != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            confirmButton?.invoke()
                        }
                    }
                }
            }
        }
    }
}
