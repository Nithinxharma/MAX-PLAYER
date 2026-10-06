package xyz.mpv.rex.ui.browser.cinehub.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.cinehub.download.ActiveDownloadTask
import xyz.mpv.rex.cinehub.download.DownloadedVideoItem
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import java.text.DecimalFormat

@Composable
fun MaxStreamDownloadsSection(
    activeTasks: List<ActiveDownloadTask>,
    completedVideos: List<DownloadedVideoItem>,
    modifier: Modifier = Modifier,
    onPlayVideo: (DownloadedVideoItem) -> Unit,
    onDeleteVideo: (DownloadedVideoItem) -> Unit,
    onCancelTask: (ActiveDownloadTask) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    var subTab by remember { mutableStateOf("All") } // "All", "Active", "Completed"

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val dec = DecimalFormat("#.##")
        return when {
            gb >= 1.0 -> "${dec.format(gb)} GB"
            mb >= 1.0 -> "${dec.format(mb)} MB"
            else -> "${dec.format(kb)} KB"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaxStreamTheme.ElectricCyan.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaxStreamTheme.ElectricCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = "Downloads",
                            tint = MaxStreamTheme.ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "Download Manager",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = primaryTextColor
                )
                val totalCount = activeTasks.size + completedVideos.size
                if (totalCount > 0) {
                    Surface(
                        shape = MaxStreamTheme.BadgeShape,
                        color = if (isDark) Color(0x33FFFFFF) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$totalCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Sub-filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == "All",
                onClick = { subTab = "All" },
                label = { Text("All (${activeTasks.size + completedVideos.size})") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = subTab == "Active",
                onClick = { subTab = "Active" },
                label = { Text("Downloading (${activeTasks.size})") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = subTab == "Completed",
                onClick = { subTab = "Completed" },
                label = { Text("Downloaded (${completedVideos.size})") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val showActive = subTab == "All" || subTab == "Active"
        val showCompleted = subTab == "All" || subTab == "Completed"

        if (activeTasks.isEmpty() && completedVideos.isEmpty()) {
            MaxStreamGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = if (isDark) Color(0x221E2436) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = MaxStreamTheme.ElectricCyan,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "No Downloads Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Text(
                        text = "Tap 'Download' on any movie or episode stream to save videos locally for offline playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedTextColor
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Active Tasks Section
                if (showActive && activeTasks.isNotEmpty()) {
                    Text(
                        text = "Active Downloads",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.ElectricCyan
                    )
                    activeTasks.forEach { task ->
                        MaxStreamGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = if (isDark) Color(0x33141824) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            borderColor = MaxStreamTheme.ElectricCyan.copy(alpha = 0.35f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onCancelTask(task) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel Download",
                                            tint = mutedTextColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${task.progressPercent}% • ${formatBytes(task.bytesDownloaded)} / ${formatBytes(task.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mutedTextColor
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaxStreamTheme.ElectricCyan.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = task.statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaxStreamTheme.ElectricCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaxStreamTheme.ElectricCyan,
                                    trackColor = if (isDark) Color(0x33FFFFFF) else MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }

                // Completed Downloads Section
                if (showCompleted && completedVideos.isNotEmpty()) {
                    Text(
                        text = "Downloaded Media",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    completedVideos.forEach { video ->
                        MaxStreamGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = if (isDark) Color(0x221E2436) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Download",
                                            tint = MaxStreamTheme.CrimsonAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatBytes(video.sizeBytes)} • ${video.file.extension.uppercase()} File",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mutedTextColor
                                    )
                                }

                                Button(
                                    onClick = { onPlayVideo(video) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                                ) {
                                    Text("Play", fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { onDeleteVideo(video) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete File",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
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
