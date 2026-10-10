package xyz.mpv.rex.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassButton
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

data class DownloadItemState(
  val id: String,
  val title: String,
  val provider: String,
  val quality: String,
  val progress: Float,
  val speedMbps: Float,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val isPaused: Boolean,
  val isCompleted: Boolean,
  val posterUrl: String? = null,
)

@Serializable
object DownloadManagerScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current

    BackHandler {
      backstack.removeLastOrNull()
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    val downloadItems = remember {
      mutableStateListOf(
        DownloadItemState(
          id = "dl_1",
          title = "Cyberpunk: Edgerunners - Ep 01",
          provider = "AnimePahe",
          quality = "1080p FHD",
          progress = 0.68f,
          speedMbps = 4.2f,
          downloadedBytes = 380_000_000L,
          totalBytes = 550_000_000L,
          isPaused = false,
          isCompleted = false
        ),
        DownloadItemState(
          id = "dl_2",
          title = "Oppenheimer (2023)",
          provider = "FlixHQ",
          quality = "4K UHD",
          progress = 0.24f,
          speedMbps = 8.7f,
          downloadedBytes = 1_200_000_000L,
          totalBytes = 5_000_000_000L,
          isPaused = true,
          isCompleted = false
        ),
        DownloadItemState(
          id = "dl_3",
          title = "Arcane Season 2 - Ep 03",
          provider = "VidSrc",
          quality = "1080p HDR",
          progress = 1.0f,
          speedMbps = 0.0f,
          downloadedBytes = 820_000_000L,
          totalBytes = 820_000_000L,
          isPaused = false,
          isCompleted = true
        )
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
          ),
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaxStreamTheme.TextPrimary
              )
            }
          },
          title = {
            Text(
              text = "Download Manager",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaxStreamTheme.TextPrimary
            )
          },
          actions = {
            IconButton(onClick = {
              // Pause or Resume All
            }) {
              Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Pause All",
                tint = MaxStreamTheme.TextPrimary
              )
            }
          }
        )
      },
      containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp)
      ) {
        // Storage Status Card
        GlassCard(
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaxStreamTheme.CrimsonAccent
              )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Internal Storage",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaxStreamTheme.TextPrimary
              )
              Text(
                text = "14.2 GB used of 128 GB (88 GB free)",
                style = MaterialTheme.typography.bodySmall,
                color = MaxStreamTheme.TextSecondary
              )
              Spacer(modifier = Modifier.height(6.dp))
              LinearProgressIndicator(
                progress = { 0.22f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaxStreamTheme.CrimsonAccent,
                trackColor = Color(0x33FFFFFF)
              )
            }
          }
        }

        // Tabs
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilterChip(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            label = { Text("Active (${downloadItems.count { !it.isCompleted }})") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaxStreamTheme.CrimsonAccent,
              selectedLabelColor = Color.White
            )
          )
          FilterChip(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            label = { Text("Completed (${downloadItems.count { it.isCompleted }})") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaxStreamTheme.CrimsonAccent,
              selectedLabelColor = Color.White
            )
          )
        }

        // Downloads List
        val filteredItems = remember(selectedTab, downloadItems.size) {
          if (selectedTab == 0) downloadItems.filter { !it.isCompleted }
          else downloadItems.filter { it.isCompleted }
        }

        if (filteredItems.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaxStreamTheme.TextSecondary.copy(alpha = 0.5f)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = if (selectedTab == 0) "No active downloads" else "No completed downloads",
                style = MaterialTheme.typography.bodyLarge,
                color = MaxStreamTheme.TextSecondary
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(filteredItems, key = { it.id }) { item ->
              GlassCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaxStreamTheme.GlassSurface),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (item.isCompleted) Color(0xFF4CAF50) else MaxStreamTheme.CrimsonAccent
                      )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                          text = item.provider,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaxStreamTheme.CrimsonAccent
                        )
                        Text(
                          text = "• ${item.quality}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaxStreamTheme.TextSecondary
                        )
                      }
                    }

                    IconButton(onClick = {
                      downloadItems.removeIf { it.id == item.id }
                    }) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaxStreamTheme.TextSecondary
                      )
                    }
                  }

                  if (!item.isCompleted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                      progress = { item.progress },
                      modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                      color = if (item.isPaused) Color.Gray else MaxStreamTheme.CrimsonAccent,
                      trackColor = Color(0x33FFFFFF)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = if (item.isPaused) "Paused" else "${(item.progress * 100).toInt()}% • ${item.speedMbps} MB/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaxStreamTheme.TextSecondary
                      )
                      Text(
                        text = "${item.downloadedBytes / 1_000_000} MB / ${item.totalBytes / 1_000_000} MB",
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
    }
  }
}
