package xyz.mpv.rex.ui.browser.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

data class CastDevice(
  val id: String,
  val name: String,
  val type: String,
  val isConnected: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDeviceSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onDeviceSelected: (CastDevice) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

  var isSearching by remember { mutableStateOf(true) }
  var connectedDevice by remember { mutableStateOf<CastDevice?>(null) }

  val devices = remember {
    listOf(
      CastDevice("1", "Living Room TV", "Chromecast Ultra"),
      CastDevice("2", "Bedroom Apple TV", "AirPlay / DLNA"),
      CastDevice("3", "Samsung Smart TV", "DLNA Renderer")
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 12.dp)
          .size(width = 36.dp, height = 4.dp)
          .background(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            shape = CircleShape
          )
      )
    },
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (connectedDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = null,
            tint = MaxStreamTheme.CrimsonAccent
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Cast to Device",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaxStreamTheme.TextPrimary
          )
        }

        if (isSearching) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaxStreamTheme.CrimsonAccent
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (connectedDevice != null) {
        GlassCard(
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "Casting to ${connectedDevice?.name}",
              style = MaterialTheme.typography.labelMedium,
              color = MaxStreamTheme.CrimsonAccent,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(onClick = { /* Remote pause/play */ }) {
                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaxStreamTheme.TextPrimary)
              }
              IconButton(onClick = { /* Volume up */ }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = MaxStreamTheme.TextPrimary)
              }
            }
          }
        }
      }

      Text(
        text = "Available Devices",
        style = MaterialTheme.typography.labelSmall,
        color = MaxStreamTheme.TextSecondary,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 24.dp)
      ) {
        items(devices, key = { it.id }) { device ->
          GlassCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                connectedDevice = device
                onDeviceSelected(device)
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(MaxStreamTheme.GlassSurface),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Tv,
                  contentDescription = null,
                  tint = MaxStreamTheme.TextPrimary
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = device.name,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaxStreamTheme.TextPrimary
                )
                Text(
                  text = device.type,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaxStreamTheme.TextSecondary
                )
              }
            }
          }
        }
      }
    }
  }
}
