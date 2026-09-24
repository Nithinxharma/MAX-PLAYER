package xyz.mpv.rex.ui.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.mpv.rex.ui.theme.maxstream.GlassButtonVariant
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassButton
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassFilterChip
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassSearchBar

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
    MaxStreamGlassButton(
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
