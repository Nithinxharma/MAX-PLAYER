package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward compatibility alias redirecting to centralized xyz.mpv.rex.core.ui.MaxStreamLiquidGlassSearch
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
    xyz.mpv.rex.core.ui.MaxStreamLiquidGlassSearch(
        query = query,
        onQueryChange = onQueryChange,
        isExpanded = isExpanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        placeholder = placeholder
    )
}
