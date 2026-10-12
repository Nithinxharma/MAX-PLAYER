package xyz.mpv.rex.ui.browser.cinehub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Full-screen immersive detail view for Movies and TV Shows.
 * Replaces popup cards with a full-screen cinematic presentation.
 */
@Serializable
object CineDetailScreen : Screen {

  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val item = CineDetailStateHolder.activeDetailItem

    BackHandler {
      CineDetailStateHolder.clear()
      backstack.removeLastOrNull()
    }

    if (item == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("No media item selected", color = MaterialTheme.colorScheme.onBackground)
          Spacer(modifier = Modifier.height(12.dp))
          Button(onClick = { backstack.removeLastOrNull() }) {
            Text("Go Back")
          }
        }
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
      ) {
        CineDetailView(
          item = item,
          onDismiss = {
            CineDetailStateHolder.clear()
            backstack.removeLastOrNull()
          }
        )
      }
    }
  }
}
