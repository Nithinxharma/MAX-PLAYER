package xyz.mpv.rex.ui.components.glass

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.theme.maxstream.GlassButtonVariant
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassButton
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassFilterChip
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassSearchBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

typealias GlassButtonVariant = xyz.mpv.rex.ui.theme.maxstream.GlassButtonVariant

/**
 * Universal Button wrapper for Glass Design System.
 */
@Composable
fun MaxStreamGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    shape: Shape = MaxStreamTheme.ButtonShape,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
) {
    MaxStreamGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        variant = if (isPrimary) GlassButtonVariant.Primary else GlassButtonVariant.Secondary,
        shape = shape,
        enabled = enabled,
        isLoading = isLoading,
        contentPadding = contentPadding
    )
}

/**
 * Variant-based MaxStreamGlassButton overload in glass package.
 */
@Composable
fun MaxStreamGlassButton(
    text: String,
    onClick: () -> Unit,
    variant: GlassButtonVariant,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = MaxStreamTheme.ButtonShape,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
) {
    xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        variant = variant,
        shape = shape,
        enabled = enabled,
        isLoading = isLoading,
        contentPadding = contentPadding
    )
}

/**
 * Typealias and delegates to unify button usage across the application.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        icon = icon,
        enabled = enabled
    )
}

@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onVoiceClick: (() -> Unit)? = null
) {
    MaxStreamGlassSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        modifier = modifier,
        placeholder = placeholder,
        onVoiceClick = onVoiceClick
    )
}

@Composable
fun GlassFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    MaxStreamGlassFilterChip(
        text = text,
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier,
        icon = icon
    )
}

