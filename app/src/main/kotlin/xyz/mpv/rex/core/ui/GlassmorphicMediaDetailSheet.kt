package xyz.mpv.rex.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable

/**
 * Re-export alias for GlassmorphicMediaDetailSheet in core UI package.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassmorphicMediaDetailSheet(
    item: Any,
    onDismiss: () -> Unit
) {
    xyz.mpv.rex.ui.browser.cinehub.components.GlassmorphicMediaDetailSheet(
        item = item,
        onDismiss = onDismiss
    )
}
