package xyz.mpv.rex.ui.browser.cinehub

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.failover.StreamCandidate
import xyz.mpv.rex.cinehub.failover.StreamHealthResolver
import xyz.mpv.rex.cinehub.stream.CloudStreamLinkManager
import xyz.mpv.rex.cinehub.stream.CloudStreamRequest
import xyz.mpv.rex.utils.media.MediaUtils

/**
 * CloudStream-style Multi-Source Stream Selector Sheet.
 *
 * Displays resolved streams from providers with server name, quality badge,
 * format indicator, and health status.
 * Provides an "Auto-Play Best Stream" shortcut as well as individual stream selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudStreamLinkBottomSheet(
    request: CloudStreamRequest,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf<List<StreamCandidate>>(emptyList()) }
    var scannedStreams by remember { mutableStateOf<Map<String, StreamHealthResolver.ScannedStream>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isScanningHealth by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun scanCandidateLinks(list: List<StreamCandidate>) {
        if (list.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isScanningHealth = true }
            val results = StreamHealthResolver.scanStreamsHealth(list)
            val resultMap = results.associateBy { it.candidate.url }
            // Sort candidates so working streams appear first
            val sortedCandidates = list.sortedWith(
                compareByDescending<StreamCandidate> { resultMap[it.url]?.isHealthy == true }
                    .thenBy { resultMap[it.url]?.latencyMs ?: Long.MAX_VALUE }
            )
            withContext(Dispatchers.Main) {
                scannedStreams = resultMap
                candidates = sortedCandidates
                isScanningHealth = false
            }
        }
    }

    fun loadStreams() {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            errorMessage = null
            try {
                val resolved = CloudStreamLinkManager.resolveStreamCandidates(request)
                withContext(Dispatchers.Main) {
                    candidates = resolved
                    isLoading = false
                    if (resolved.isEmpty()) {
                        errorMessage = "No working streams found from current provider or scrapers."
                    } else {
                        scanCandidateLinks(resolved)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = "Failed to resolve streams: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(request) {
        loadStreams()
    }

    fun playSelectedStream(selected: StreamCandidate) {
        val backups = candidates.filter { it.url != selected.url }
        val displayName = request.getFormattedDisplayName()

        Toast.makeText(context, "Playing: $displayName (${selected.quality})", Toast.LENGTH_SHORT).show()

        MediaUtils.playStreamWithFailover(
            primaryCandidate = selected,
            backupCandidates = backups,
            context = context,
            title = displayName,
            launchSource = "cinehub",
            posterUrl = request.posterUrl,
            sourceType = "cinehub"
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header: Poster + Title + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!request.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = request.posterUrl,
                        contentDescription = request.title,
                        modifier = Modifier
                            .size(56.dp, 80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Stream",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!request.isMovie && request.seasonNumber != null && request.episodeNumber != null) {
                        Text(
                            text = "Season ${request.seasonNumber} • Episode ${request.episodeNumber}${if (!request.episodeTitle.isNullOrBlank()) " - ${request.episodeTitle}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (request.year != null && request.year > 0) {
                        Text(
                            text = "${request.year}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Loading state
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching and verifying streams from providers...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (errorMessage != null) {
                // Error state (No infinite loop!)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "No streams available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { loadStreams() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Search")
                        }
                    }
                }
            } else {
                // Auto-Play Best Button
                val bestCandidate = candidates.firstOrNull { scannedStreams[it.url]?.isHealthy == true } ?: candidates.firstOrNull()
                if (bestCandidate != null) {
                    Button(
                        onClick = { playSelectedStream(bestCandidate) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (scannedStreams[bestCandidate.url]?.isHealthy == true) {
                                "Auto-Play Best Working (${bestCandidate.quality} • ${scannedStreams[bestCandidate.url]?.latencyMs}ms)"
                            } else {
                                "Auto-Play Best (${bestCandidate.quality})"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val workingCount = candidates.count { scannedStreams[it.url]?.isHealthy == true }
                        Column {
                            Text(
                                text = "Available Sources (${candidates.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (scannedStreams.isNotEmpty() || isScanningHealth) {
                                Text(
                                    text = if (isScanningHealth) "Scanning stream health..." else "$workingCount verified working",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (workingCount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        androidx.compose.material3.TextButton(
                            onClick = { scanCandidateLinks(candidates) },
                            enabled = !isScanningHealth
                        ) {
                            if (isScanningHealth) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scanning...", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan Links", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // List of resolved stream candidates
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(candidates) { index, candidate ->
                            val healthInfo = scannedStreams[candidate.url]
                            val isWorking = healthInfo?.isHealthy == true
                            val isFailed = healthInfo?.isHealthy == false

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playSelectedStream(candidate) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isWorking && index == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        isWorking -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isWorking -> Color(0xFF4CAF50)
                                                    isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            when {
                                                isWorking -> Icons.Default.CheckCircle
                                                isFailed -> Icons.Default.Close
                                                else -> Icons.Default.PlayArrow
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Quality Pill
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = candidate.quality,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            // Format Pill
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (candidate.isM3u8) "HLS" else "MP4",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            // Health Status Pill
                                            if (isWorking) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "Working (${healthInfo?.latencyMs}ms)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else if (isFailed) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Offline / Broken",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Scanning...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { playSelectedStream(candidate) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isWorking) "Play" else if (isFailed) "Try" else "Play", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
