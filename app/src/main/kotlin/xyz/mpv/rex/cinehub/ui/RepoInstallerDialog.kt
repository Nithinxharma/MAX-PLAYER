package xyz.mpv.rex.cinehub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinehub.extension.installer.WebLinkRepoInstaller
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.RepoVerificationResult
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassButton
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassDialog
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * 1-Click Glassmorphic Repository Installer Dialog for MaxStream.
 */
@Composable
fun RepoInstallerDialog(
    repositoryManager: RepositoryManager,
    onDismissRequest: () -> Unit,
    onInstalledSuccess: () -> Unit,
    initialUrl: String = ""
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val installer = remember(repositoryManager) { WebLinkRepoInstaller(repositoryManager) }

    var inputUrl by remember { mutableStateOf(initialUrl) }
    var isInspecting by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var inspectionResult by remember { mutableStateOf<RepoVerificationResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    MaxStreamGlassDialog(
        onDismissRequest = onDismissRequest,
        title = "1-Click Extension Repository Installer",
        icon = Icons.Default.Extension,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = "Close",
                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (!isSuccess) {
                MaxStreamGlassButton(
                    text = if (isInstalling) "Installing..." else "Install Repository",
                    icon = Icons.Default.Download,
                    enabled = inputUrl.isNotBlank() && !isInstalling,
                    onClick = {
                        scope.launch {
                            isInstalling = true
                            statusMessage = "Fetching repository manifest..."
                            val result = installer.installRepositoryFromUrl(inputUrl)
                            isInstalling = false
                            if (result.isSuccess) {
                                isSuccess = true
                                statusMessage = result.message
                                onInstalledSuccess()
                            } else {
                                isSuccess = false
                                statusMessage = result.message
                            }
                        }
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Paste a repository JSON URL or custom CloudStream link (e.g. cloudstreamrepo://...) to instantly register plugins.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // URL input row
            OutlinedTextField(
                value = inputUrl,
                onValueChange = {
                    inputUrl = it
                    inspectionResult = null
                    statusMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://.../repo.json") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "URL",
                        tint = MaxStreamTheme.CrimsonAccent
                    )
                },
                trailingIcon = {
                    if (inputUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isInspecting = true
                                    inspectionResult = installer.inspectRepositoryUrl(inputUrl)
                                    isInspecting = false
                                }
                            }
                        ) {
                            if (isInspecting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Inspect Repository",
                                    tint = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaxStreamTheme.CrimsonAccent,
                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f),
                    unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
                ),
                singleLine = true
            )

            // Inspection result banner
            inspectionResult?.let { res ->
                MaxStreamGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = if (res.isOnline) Color(0x224CAF50) else Color(0x22F44336),
                    borderColor = if (res.isOnline) Color(0x664CAF50) else Color(0x66F44336)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (res.isOnline) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (res.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Column {
                            Text(
                                text = res.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (res.isOnline) "Online • ${res.pluginCount} Plugins • ${res.latencyMs}ms" else "Error: ${res.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Status message
            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (isSuccess) Color(0xFF4CAF50) else MaxStreamTheme.CrimsonAccent
                )
            }

            // Popular Presets Quick Selector
            Text(
                text = "Popular Community Repositories",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(RepositoryManager.BUILT_IN_PRESETS) { preset ->
                    MaxStreamGlassCard(
                        onClick = {
                            inputUrl = preset.url
                            scope.launch {
                                isInspecting = true
                                inspectionResult = installer.inspectRepositoryUrl(preset.url)
                                isInspecting = false
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = if (inputUrl == preset.url) MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f) else (if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f)),
                        borderColor = if (inputUrl == preset.url) MaxStreamTheme.CrimsonAccent else (if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.description ?: "By ${preset.author ?: "Community"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Select",
                                tint = if (inputUrl == preset.url) MaxStreamTheme.CrimsonAccent else (if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
