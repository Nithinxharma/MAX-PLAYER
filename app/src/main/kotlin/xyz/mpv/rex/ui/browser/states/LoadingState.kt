package xyz.mpv.rex.ui.browser.states

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamSkeletonListItem

@Composable
fun LoadingState(
  icon: ImageVector? = null,
  title: String = "Scanning for videos...",
  message: String = "Please wait while we search your device",
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    repeat(6) {
      MaxStreamSkeletonListItem(modifier = Modifier.fillMaxWidth())
    }
  }
}
