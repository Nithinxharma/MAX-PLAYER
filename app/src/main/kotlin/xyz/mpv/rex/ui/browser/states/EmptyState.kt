package xyz.mpv.rex.ui.browser.states

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.mpv.rex.ui.components.glass.EmptyStateType
import xyz.mpv.rex.ui.components.glass.MaxStreamEmptyState

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
) {
  MaxStreamEmptyState(
    modifier = modifier,
    type = EmptyStateType.GENERIC,
    customTitle = title,
    customMessage = message,
    customIcon = icon
  )
}
