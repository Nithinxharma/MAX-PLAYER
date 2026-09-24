package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.model.PluginTraceSession
import xyz.mpv.rex.cinehub.extension.model.TraceStepItem
import xyz.mpv.rex.cinehub.extension.model.TraceStepStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginExecutionTraceScreen(
    onNavigateBack: () -> Unit = {},
    showTopBar: Boolean = true,
    viewModel: PluginExecutionTraceViewModel = koinInject()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val availableExtensions by viewModel.availableExtensions.collectAsState()
    val selectedExtension by viewModel.selectedExtension.collectAsState()
    val traceSession by viewModel.traceSession.collectAsState()
    val isTracing by viewModel.isTracing.collectAsState()
    val savedFilePath by viewModel.savedFilePath.collectAsState()
    val proofResult by viewModel.proofOfExecutionResult.collectAsState()
    val isRunningProofTest by viewModel.isRunningProofTest.collectAsState()

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var showLogsDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background,
        topBar = {
            xyz.mpv.rex.ui.components.glass.GlassTopBar(
                title = "Plugin Execution Trace",
                subtitle = if (selectedExtension != null) "Target: ${selectedExtension?.name}" else "Select an extension",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.loadAvailableExtensions() },
                        enabled = !isTracing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh List", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📋 Copy Full Trace") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val report = viewModel.copyTraceText()
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, "Trace copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            enabled = traceSession != null
                        )
                        DropdownMenuItem(
                            text = { Text("📤 Export / Share Trace") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.exportTrace(context)
                            },
                            enabled = traceSession != null
                        )
                        DropdownMenuItem(
                            text = { Text("💾 Save Trace to Disk") },
                            leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val path = viewModel.saveTraceLog(context)
                                if (path != null) {
                                    Toast.makeText(context, "Saved: $path", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to save trace", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = traceSession != null
                        )
                        DropdownMenuItem(
                            text = { Text("📜 View Raw Logs") },
                            leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showLogsDialog = true
                            },
                            enabled = traceSession != null
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!savedFilePath.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Saved: $savedFilePath",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.runTraceForSelected() },
                            enabled = selectedExtension != null && !isTracing,
                            modifier = Modifier.weight(1.3f)
                        ) {
                            if (isTracing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Tracing...")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run Trace")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val report = viewModel.copyTraceText()
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, "Trace copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            enabled = traceSession != null && !isTracing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", fontSize = 13.sp)
                        }

                        FilledTonalButton(
                            onClick = { viewModel.exportTrace(context) },
                            enabled = traceSession != null && !isTracing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Extension Selector Header
            item {
                ExtensionSelectorSection(
                    availableExtensions = availableExtensions,
                    selectedExtension = selectedExtension,
                    onSelect = { viewModel.selectExtension(it) },
                    onRunTrace = {
                        viewModel.selectExtension(it)
                        viewModel.runTrace(it)
                    },
                    isTracing = isTracing
                )
            }

            // Proof of Execution Section
            item {
                ProofOfExecutionCard(
                    isRunning = isRunningProofTest,
                    result = proofResult,
                    onRunProofTest = { viewModel.runProofOfExecutionTest() }
                )
            }

            // Summary Status Banner
            traceSession?.let { session ->
                item {
                    TraceSessionHeaderCard(session = session)
                }

                session.dexExecutionCheck?.let { dexCheck ->
                    item {
                        DexExecutionCheckCard(dexCheck = dexCheck)
                    }
                }

                items(session.steps, key = { it.stepNumber }) { step ->
                    TraceTimelineStepCard(step = step)
                }
            } ?: run {
                item {
                    EmptyTraceStateBanner(
                        selectedName = selectedExtension?.name ?: "an extension",
                        onRunTrace = { viewModel.runTraceForSelected() }
                    )
                }
            }
        }
    }

    if (showLogsDialog) {
        val logs = traceSession?.rawLogLines ?: emptyList()
        xyz.mpv.rex.ui.components.glass.MaxStreamGlassDialog(
            onDismissRequest = { showLogsDialog = false },
            title = "Raw Trace Logs",
            icon = Icons.Outlined.Terminal,
            confirmButton = {
                xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton(
                    text = "Close",
                    variant = xyz.mpv.rex.ui.components.glass.GlassButtonVariant.Primary,
                    onClick = { showLogsDialog = false }
                )
            }
        ) {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (line.contains("FAIL") || line.contains("❌")) MaterialTheme.colorScheme.error else (if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExtensionSelectorSection(
    availableExtensions: List<xyz.mpv.rex.cinehub.extension.model.InstalledExtension>,
    selectedExtension: xyz.mpv.rex.cinehub.extension.model.InstalledExtension?,
    onSelect: (xyz.mpv.rex.cinehub.extension.model.InstalledExtension) -> Unit,
    onRunTrace: (xyz.mpv.rex.cinehub.extension.model.InstalledExtension) -> Unit,
    isTracing: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Target Extension to Trace:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${availableExtensions.size} Available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableExtensions.forEach { ext ->
                    val isSelected = selectedExtension?.pkgName == ext.pkgName
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(ext) },
                        label = { Text(ext.name.ifBlank { ext.pkgName }) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun TraceSessionHeaderCard(session: PluginTraceSession) {
    val passedCount = session.steps.count { it.status == TraceStepStatus.PASSED }
    val failedCount = session.steps.count { it.status == TraceStepStatus.FAILED }
    val skippedCount = session.steps.count { it.status == TraceStepStatus.SKIPPED }

    val statusColor = when {
        session.isRunning -> Color(0xFF0288D1)
        session.hasFailed -> MaterialTheme.colorScheme.error
        else -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.4f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = session.pluginDisplayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (session.isRunning) "RUNNING..." else if (session.hasFailed) "FAILED AT STEP ${session.failedAtStep}" else "ALL 16 STEPS PASSED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Text(
                text = "Package: ${session.pluginPkgName} • Duration: ${session.totalDurationMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("✅ Passed: $passedCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Text("❌ Failed: $failedCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text("⏭️ Skipped: $skippedCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Total Steps: ${session.steps.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TraceTimelineStepCard(step: TraceStepItem) {
    var isExpanded by remember { mutableStateOf(step.status == TraceStepStatus.FAILED) }

    val iconColor = when (step.status) {
        TraceStepStatus.PASSED -> Color(0xFF2E7D32)
        TraceStepStatus.FAILED -> MaterialTheme.colorScheme.error
        TraceStepStatus.RUNNING -> Color(0xFF0288D1)
        TraceStepStatus.SKIPPED -> MaterialTheme.colorScheme.outline
        TraceStepStatus.IDLE -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (step.status == TraceStepStatus.FAILED) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (step.status == TraceStepStatus.FAILED) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    when (step.status) {
                        TraceStepStatus.PASSED -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Passed", tint = iconColor, modifier = Modifier.size(22.dp))
                        }
                        TraceStepStatus.FAILED -> {
                            Icon(Icons.Default.Cancel, contentDescription = "Failed", tint = iconColor, modifier = Modifier.size(22.dp))
                        }
                        TraceStepStatus.RUNNING -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = iconColor)
                        }
                        TraceStepStatus.SKIPPED -> {
                            Icon(Icons.Default.SkipNext, contentDescription = "Skipped", tint = iconColor, modifier = Modifier.size(22.dp))
                        }
                        TraceStepStatus.IDLE -> {
                            Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "Idle", tint = iconColor, modifier = Modifier.size(22.dp))
                        }
                    }

                    Column {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (step.status == TraceStepStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (step.durationMs > 0) {
                    Text(
                        text = "${step.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Summary Text
            if (!step.resultSummary.isNullOrBlank()) {
                Text(
                    text = step.resultSummary!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (step.status == TraceStepStatus.FAILED) MaterialTheme.colorScheme.error else if (step.status == TraceStepStatus.PASSED) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                )
            }

            // Failure Box
            if (step.status == TraceStepStatus.FAILED) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "❌ Execution stopped here.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (!step.errorMessage.isNullOrBlank()) {
                        SelectionContainer {
                            Text(
                                text = step.errorMessage!!,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Expandable Detailed Output / Stacktrace
            if (!step.detailedOutput.isNullOrBlank() || !step.stackTrace.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Hide details" else if (step.stackTrace != null) "View full exception stacktrace" else "View step output",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!step.detailedOutput.isNullOrBlank()) {
                                    Text(
                                        text = step.detailedOutput!!,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = Color(0xFFD4D4D4)
                                    )
                                }
                                if (!step.stackTrace.isNullOrBlank()) {
                                    Text(
                                        text = "Stacktrace:\n" + step.stackTrace!!,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFFFF8A80)
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

@Composable
fun EmptyTraceStateBanner(selectedName: String, onRunTrace: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.Troubleshoot,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "CloudStream Plugin Execution Trace",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Run a comprehensive 16-step diagnostic trace on '$selectedName' to pinpoint the EXACT class, method, or bytecode failure point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRunTrace,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Run 16-Step Trace Now")
            }
        }
    }
}

@Composable
fun DexExecutionCheckCard(dexCheck: xyz.mpv.rex.cinehub.extension.model.DexExecutionCheckResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = CardDefaults.outlinedCardBorder()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = if (dexCheck.isReadOnlyEnforced) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "DEX EXECUTION CHECK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Badge(containerColor = if (dexCheck.isReadOnlyEnforced) Color(0xFF1B5E20) else Color(0xFFE65100)) {
                    Text(
                        text = if (dexCheck.isReadOnlyEnforced) "Android 14+ Compliant" else "Standard Mode",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                text = "Android ART Security Status & ClassLoader Attributes:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DexCheckAttributeRow("Plugin Path", dexCheck.pluginPath)
                    DexCheckAttributeRow("Physical Exists", dexCheck.exists.toString())
                    DexCheckAttributeRow("Readable / Writable", "${dexCheck.isReadable} / ${dexCheck.isWritable}")
                    DexCheckAttributeRow("Read-Only Enforced", dexCheck.isReadOnlyEnforced.toString())
                    DexCheckAttributeRow("Executable File", dexCheck.executablePath)
                    DexCheckAttributeRow("Parent ClassLoader", dexCheck.parentClassLoader)
                    DexCheckAttributeRow("Loader Type", dexCheck.loaderType)
                    DexCheckAttributeRow("Android Version", "SDK ${dexCheck.androidSdkVersion} (${dexCheck.androidRelease})")
                    DexCheckAttributeRow("ART Exception", dexCheck.artException ?: "None (Clean Load)")
                    DexCheckAttributeRow("DEX Classes Count", dexCheck.dexVisibleClassesCount.toString())
                }
            }
        }
    }
}

@Composable
fun DexCheckAttributeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ProofOfExecutionCard(
    isRunning: Boolean,
    result: xyz.mpv.rex.cinehub.extension.model.ProofOfExecutionResult?,
    onRunProofTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = CardDefaults.outlinedCardBorder()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "PROOF OF EXECUTION",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Button(
                    onClick = onRunProofTest,
                    enabled = !isRunning,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Testing...", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run Proof Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "Validate complete dynamic execution: ClassLoader creation -> loadClass() -> Plugin.load(context) -> registerMainAPI() -> APIHolder verification.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (result != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Proof Result:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Badge(containerColor = if (result.isSuccess) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error) {
                                Text(
                                    text = if (result.isSuccess) "PASSED (${result.totalDurationMs}ms)" else "FAILED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DexCheckAttributeRow("1. ClassLoader Created", result.isClassLoaderCreated.toString())
                        DexCheckAttributeRow("2. Class Loaded", result.isClassLoaded.toString())
                        DexCheckAttributeRow("3. Instance Created", result.isInstanceCreated.toString())
                        DexCheckAttributeRow("4. Method Invoked", result.isMethodInvoked.toString())
                        DexCheckAttributeRow("5. plugin.load(ctx) Executed", result.isBasePluginLifecycleReached.toString())
                        DexCheckAttributeRow("6. registerMainAPI() Verified", result.isRegisterMainAPICalled.toString())
                        DexCheckAttributeRow("Registered Providers", result.registeredProviders.joinToString().ifBlank { "None" })

                        if (result.error != null) {
                            Text(
                                text = "Error: ${result.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
