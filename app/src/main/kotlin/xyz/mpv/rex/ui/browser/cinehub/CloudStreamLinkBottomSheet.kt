package xyz.mpv.rex.ui.browser.cinehub

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.failover.StreamCandidate
import xyz.mpv.rex.cinehub.failover.StreamHealthResolver
import xyz.mpv.rex.cinehub.stream.CloudStreamDownloadManager
import xyz.mpv.rex.cinehub.stream.CloudStreamLinkManager
import xyz.mpv.rex.cinehub.stream.CloudStreamRequest
import xyz.mpv.rex.utils.media.MediaUtils

/**
 * CloudStream-style Multi-Source Stream Selector & Mirror Download Sheet.
 *
 * Displays resolved streams from providers sorted by quality, server name,
 * and format indicator.
 * Supports:
 * 1. Auto-Play Best Stream
 * 2. Individual Stream Playback
 * 3. Mirror Download Sheet (CineHub DownloadManager, 1DM/ADM External Manager, Copy Link, Browser)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudStreamLinkBottomSheet(
    request: CloudStreamRequest,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    initialDownloadMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cineDownloadManager = koinInject<CloudStreamDownloadManager>()

    var isDownloadMode by remember { mutableStateOf(initialDownloadMode) }
    var candidates by remember { mutableStateOf<List<StreamCandidate>>(emptyList()) }
    var scannedStreams by remember { mutableStateOf<Map<String, StreamHealthResolver.ScannedStream>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isScanningHealth by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMirrorForAction by remember { mutableStateOf<StreamCandidate?>(null) }

    fun scanCandidateLinks(list: List<StreamCandidate>) {
        if (list.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isScanningHealth = true }
            val results = StreamHealthResolver.scanStreamsHealth(list)
            val resultMap = results.associateBy { it.candidate.url }
            val sortedCandidates = list.sortedWith(
                compareByDescending<StreamCandidate> { resultMap[it.url]?.isHealthy == true }
                    .thenByDescending {
                        // Extract number from quality string (e.g. "1080p" -> 1080)
                        Regex("\\d+").find(it.quality)?.value?.toIntOrNull() ?: 0
                    }
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
            primaryCandidate = selected.copy(name = displayName),
            backupCandidates = backups.map { it.copy(name = displayName) },
            context = context,
            title = displayName,
            launchSource = "cinehub",
            posterUrl = request.posterUrl,
            sourceType = "cinehub"
        )
        onDismiss()
    }

    fun startNativeDownload(candidate: StreamCandidate) {
        try {
            cineDownloadManager.startDownload(
                request = request,
                streamUrl = candidate.url,
                headers = candidate.headers,
                subtitles = emptyList()
            )
            Toast.makeText(context, "Added to CineHub Downloads: ${request.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val safeTitle = request.getFormattedDisplayName().replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val extension = if (candidate.isM3u8) ".m3u8" else ".mp4"
                val downloadRequest = DownloadManager.Request(Uri.parse(candidate.url))
                    .setTitle(request.getFormattedDisplayName())
                    .setDescription("Downloading ${candidate.quality} via CineHub")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$safeTitle$extension")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                candidate.headers.forEach { (k, v) -> downloadRequest.addRequestHeader(k, v) }
                dm.enqueue(downloadRequest)
                Toast.makeText(context, "Download enqueued for ${request.title}", Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                Toast.makeText(context, "Download failed: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
        selectedMirrorForAction = null
    }

    fun startExternalDownload(candidate: StreamCandidate) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(candidate.url), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("title", request.getFormattedDisplayName())
                putExtra("filename", "${request.getFormattedDisplayName()}.mp4")
                val flatHeaders = candidate.headers.flatMap { listOf(it.key, it.value) }.toTypedArray()
                if (flatHeaders.isNotEmpty()) putExtra("headers", flatHeaders)
            }
            context.startActivity(Intent.createChooser(intent, "Download with..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch download manager: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        selectedMirrorForAction = null
    }

    fun copyLink(candidate: StreamCandidate) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Stream URL", candidate.url))
        Toast.makeText(context, "Stream URL copied to clipboard", Toast.LENGTH_SHORT).show()
        selectedMirrorForAction = null
    }

    fun openBrowser(candidate: StreamCandidate) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(candidate.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        selectedMirrorForAction = null
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
            // Header: Poster + Title + Tabs + Close Button
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
                        text = if (isDownloadMode) "Download Mirrors" else "Select Stream",
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

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector: Stream vs Download Mirrors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isDownloadMode,
                    onClick = { isDownloadMode = false },
                    label = { Text("Stream Playback", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = isDownloadMode,
                    onClick = { isDownloadMode = true },
                    label = { Text("Download Mirrors", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Search")
                        }
                    }
                }
            } else {
                // Auto-Play or Fast Download Best Shortcut
                val bestCandidate = candidates.firstOrNull { scannedStreams[it.url]?.isHealthy == true } ?: candidates.firstOrNull()
                if (bestCandidate != null) {
                    Button(
                        onClick = {
                            if (isDownloadMode) {
                                selectedMirrorForAction = bestCandidate
                            } else {
                                playSelectedStream(bestCandidate)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            if (isDownloadMode) Icons.Default.Download else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDownloadMode) {
                                "Download Best Mirror (${bestCandidate.quality})"
                            } else if (scannedStreams[bestCandidate.url]?.isHealthy == true) {
                                "Auto-Play Best (${bestCandidate.quality} • ${scannedStreams[bestCandidate.url]?.latencyMs}ms)"
                            } else {
                                "Auto-Play Best (${bestCandidate.quality})"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val workingCount = candidates.count { scannedStreams[it.url]?.isHealthy == true }
                        Column {
                            Text(
                                text = if (isDownloadMode) "Download Mirrors (${candidates.size})" else "Available Sources (${candidates.size})",
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

                        TextButton(
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
                                    .clickable {
                                        if (isDownloadMode) {
                                            selectedMirrorForAction = candidate
                                        } else {
                                            playSelectedStream(candidate)
                                        }
                                    },
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
                                                isDownloadMode -> Icons.Default.Download
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
                                            }
                                        }
                                    }

                                    // Action buttons: Play or Mirror Options
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { selectedMirrorForAction = candidate }) {
                                            Icon(Icons.Default.Download, contentDescription = "Download Mirror", modifier = Modifier.size(20.dp))
                                        }
                                        OutlinedButton(
                                            onClick = { playSelectedStream(candidate) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Play", style = MaterialTheme.typography.labelMedium)
                                        }
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

    // CloudStream Mirror Download Options Dialog
    if (selectedMirrorForAction != null) {
        val mirror = selectedMirrorForAction!!
        AlertDialog(
            onDismissRequest = { selectedMirrorForAction = null },
            title = {
                Text(
                    text = "Download Mirror Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${mirror.name} • ${mirror.quality} (${if (mirror.isM3u8) "HLS Stream" else "MP4 Direct"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose how to download or open this stream link:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { startNativeDownload(mirror) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download with CineHub")
                    }

                    OutlinedButton(
                        onClick = { startExternalDownload(mirror) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("External Manager (1DM / ADM)")
                    }

                    OutlinedButton(
                        onClick = { copyLink(mirror) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Stream Link")
                    }

                    OutlinedButton(
                        onClick = { openBrowser(mirror) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Web Browser")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMirrorForAction = null }) {
                    Text("Close")
                }
            }
        )
    }
}
