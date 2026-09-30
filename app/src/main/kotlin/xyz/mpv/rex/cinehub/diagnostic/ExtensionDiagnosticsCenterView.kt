package xyz.mpv.rex.cinehub.diagnostic

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Flagship Max Stream Extension Diagnostics Center:
 * Developer-grade audit tool for CloudStream provider runtime, parsers, extractors,
 * reflection, and root-cause failure analysis.
 */
@Composable
fun ExtensionDiagnosticsCenterView(
    modifier: Modifier = Modifier,
    initialReport: CompleteDiagnosticsReport? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isRunningAudit by remember { mutableStateOf(false) }
    var currentReport by remember { mutableStateOf<CompleteDiagnosticsReport?>(initialReport) }
    var selectedSubTab by remember { mutableIntStateOf(0) }

    // Run audit automatically on composition if no report yet
    LaunchedEffect(Unit) {
        if (currentReport == null && !isRunningAudit) {
            isRunningAudit = true
            try {
                currentReport = ExtensionDiagnosticsEngine.runFullAudit(context)
            } finally {
                isRunningAudit = false
            }
        }
    }

    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    val accentColor = MaxStreamTheme.CrimsonAccent

    val subTabs = listOf(
        "Scorecard",
        "Compatibility Audit",
        "Stream Links",
        "Search & Home",
        "Extractors",
        "Parsers & SDK",
        "Root Causes",
        "Full Report"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("extension_diagnostics_center")
    ) {
        // Hero Header & Quick Actions Row
        HeaderAuditSection(
            report = currentReport,
            isRunning = isRunningAudit,
            onRunAudit = {
                scope.launch {
                    isRunningAudit = true
                    try {
                        currentReport = ExtensionDiagnosticsEngine.runFullAudit(context)
                        Toast.makeText(context, "System Audit Complete", Toast.LENGTH_SHORT).show()
                    } catch (t: Throwable) {
                        Toast.makeText(context, "Audit error: ${t.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isRunningAudit = false
                    }
                }
            },
            onCopyReport = {
                currentReport?.let { rep ->
                    clipboardManager.setText(AnnotatedString(rep.formattedReportMarkdown))
                    Toast.makeText(context, "Developer Audit Report copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Sub-tabs navigation
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 16.dp,
            containerColor = if (isDark) MaxStreamTheme.MidnightSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = accentColor,
            indicator = { tabPositions ->
                if (selectedSubTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.then(with(TabRowDefaults) { Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]) }),
                        color = accentColor
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Sub-tab content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (isRunningAudit && currentReport == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Auditing CloudStream Provider Compatibility…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText
                        )
                        Text(
                            text = "Inspecting parsers, reflection, extractors, and endpoints",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText
                        )
                    }
                }
            } else if (currentReport != null) {
                val rep = currentReport!!
                when (selectedSubTab) {
                    0 -> ScorecardTabContent(rep)
                    1 -> CompatibilityAuditTabContent(rep.providerAudits)
                    2 -> StreamLinksTabContent(rep.streamAudits, rep.extractorAudit)
                    3 -> SearchAndHomeTabContent(rep.searchAudits, rep.homePageAudits, rep.loadAudits)
                    4 -> ExtractorsTabContent(rep.extractorAudit)
                    5 -> ParsersAndSdkTabContent(rep.parserAudit, rep.sdkAudit, rep.networkAudit, rep.reflectionAudit)
                    6 -> RootCauseTabContent(rep.rootCauses)
                    7 -> FullReportTabContent(rep.formattedReportMarkdown)
                }
            }
        }
    }
}

// =========================================================================
// HEADER & ACTION BAR
// =========================================================================

@Composable
private fun HeaderAuditSection(
    report: CompleteDiagnosticsReport?,
    isRunning: Boolean,
    onRunAudit: () -> Unit,
    onCopyReport: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    MaxStreamGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        borderColor = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.40f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaxStreamTheme.CrimsonAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Extension Diagnostics Center",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Text(
                            text = "Developer Audit & Compatibility Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedText
                        )
                    }
                }

                if (report != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            report.scorecard.overallScore >= 85 -> Color(0x3300E676)
                            report.scorecard.overallScore >= 70 -> Color(0x33FFB300)
                            else -> Color(0x33FF1744)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                report.scorecard.overallScore >= 85 -> Color(0xFF00E676)
                                report.scorecard.overallScore >= 70 -> Color(0xFFFFB300)
                                else -> Color(0xFFFF1744)
                            }.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = "${report.scorecard.overallScore}% Compat",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                report.scorecard.overallScore >= 85 -> Color(0xFF00E676)
                                report.scorecard.overallScore >= 70 -> Color(0xFFFFB300)
                                else -> Color(0xFFFF1744)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRunAudit,
                    enabled = !isRunning,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("run_audit_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auditing…", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Full Audit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = onCopyReport,
                    enabled = report != null,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("copy_report_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Report", fontSize = 13.sp)
                }
            }
        }
    }
}

// =========================================================================
// TAB 0: SCORECARD & SYSTEM OVERVIEW
// =========================================================================

@Composable
private fun ScorecardTabContent(report: CompleteDiagnosticsReport) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    val sc = report.scorecard

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "CloudStream Compatibility Scorecard",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryText
        )

        // Radar / Progress Bars Breakdown Card
        MaxStreamGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScoreProgressRow("Overall Compatibility", sc.overallScore, Color(0xFF00E5FF))
                ScoreProgressRow("Installed Providers", sc.providersScore, Color(0xFF00E676))
                ScoreProgressRow("Extractor Registry", sc.extractorsScore, Color(0xFFFF9100))
                ScoreProgressRow("Parsers & Crypto Stack", sc.parsersScore, Color(0xFF7C4DFF))
                ScoreProgressRow("Network Engine", sc.networkScore, Color(0xFF00C853))
                ScoreProgressRow("CloudStream SDK APIs", sc.sdkScore, Color(0xFFFF4081))
            }
        }

        // Summary Quick Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricStatCard(
                title = "Providers",
                value = "${report.providerAudits.size}",
                subtitle = "${report.providerAudits.count { it.searchStatus == AuditStatus.PASSED }} Passed",
                color = Color(0xFF00E676),
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Extractors",
                value = "${report.extractorAudit.availableExtractorsCount}/${report.extractorAudit.requiredExtractorsCount}",
                subtitle = "${report.extractorAudit.missingExtractorsCount} Missing",
                color = if (report.extractorAudit.missingExtractorsCount > 0) Color(0xFFFF9100) else Color(0xFF00E676),
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                title = "Parsers",
                value = "${report.parserAudit.availableCount}/${report.parserAudit.totalCount}",
                subtitle = "Crypto & HTML",
                color = Color(0xFF7C4DFF),
                modifier = Modifier.weight(1f)
            )
        }

        // Reflection & Runtime Status Pill
        MaxStreamGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaxStreamTheme.ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "CloudStream SDK Core Engine",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = primaryText
                        )
                        Text(
                            text = "Headless Plugin Runtime & APIHolder Singleton",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x3300E676)
                ) {
                    Text(
                        text = "READY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreProgressRow(label: String, score: Int, color: Color) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = primaryText)
            Text("$score%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color)
        }
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.20f)
        )
    }
}

@Composable
private fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    MaxStreamGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =========================================================================
// TAB 1: EXTENSION COMPATIBILITY AUDIT MODE
// =========================================================================

@Composable
private fun CompatibilityAuditTabContent(audits: List<ProviderCompatibilityAudit>) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    if (audits.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No installed providers discovered to audit.", color = MaxStreamTheme.TextMuted)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Provider Compatibility Audit (${audits.size} Active)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
        }

        items(audits, key = { it.providerName }) { item ->
            ProviderAuditCard(item)
        }
    }
}

@Composable
private fun ProviderAuditCard(audit: ProviderCompatibilityAudit) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    MaxStreamGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = if (audit.extractorsStatus == AuditStatus.FAILED) Color(0xFFFF5252).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Provider: ${audit.providerName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryText
                    )
                    Text(
                        text = audit.mainUrl.ifBlank { "Embedded internal API" },
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (audit.isAvailable) Color(0x3300E676) else Color(0x33FF5252)
                ) {
                    Text(
                        text = if (audit.isAvailable) "Installed" else "Issues Detected",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (audit.isAvailable) Color(0xFF00E676) else Color(0xFFFF5252),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Capabilities Checklist Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AuditPill("Search", audit.searchStatus)
                AuditPill("Home Page", audit.homePageStatus)
                AuditPill("Load", audit.loadStatus)
                AuditPill("Extractors", audit.extractorsStatus)
            }

            // Reason and Missing Component Card (if any failure)
            if (audit.extractorsReason != null || audit.missingComponent != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0x332A1518) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (audit.extractorsReason != null) {
                            Text(
                                text = "Reason:\n${audit.extractorsReason}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFFF8A80)
                            )
                        }
                        if (audit.missingComponent != null) {
                            Text(
                                text = "Missing Component: ${audit.missingComponent}",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryText
                            )
                        }
                        if (audit.requiredClass != null) {
                            Text(
                                text = "Required Class: ${audit.requiredClass}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = mutedText
                            )
                        }
                    }
                }
            }

            // Recommendation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Recommendation:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryText
                )
                Text(
                    text = audit.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaxStreamTheme.ElectricCyan
                )
            }
        }
    }
}

@Composable
private fun AuditPill(title: String, status: AuditStatus) {
    val color = when (status) {
        AuditStatus.PASSED -> Color(0xFF00E676)
        AuditStatus.FAILED -> Color(0xFFFF5252)
        AuditStatus.WARNING -> Color(0xFFFFB300)
        AuditStatus.SKIPPED -> Color(0xFF9E9E9E)
    }
    val symbol = when (status) {
        AuditStatus.PASSED -> "✓"
        AuditStatus.FAILED -> "✗"
        AuditStatus.WARNING -> "!"
        AuditStatus.SKIPPED -> "–"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(symbol, fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp)
            Text(title, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

// =========================================================================
// TAB 2: STREAM LINK DIAGNOSTICS & EXTRACTORS
// =========================================================================

@Composable
private fun StreamLinksTabContent(
    streamAudits: List<StreamLinkDiagnosticsResult>,
    extractorAudit: ExtractorAuditReport
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Stream Link Execution Chain",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
            Text(
                text = "Tests loadLinks() -> Extractor Invoked -> Extractor Response -> Playable Video Links",
                style = MaterialTheme.typography.bodySmall,
                color = MaxStreamTheme.TextMuted
            )
        }

        items(streamAudits) { audit ->
            StreamChainCard(audit)
        }
    }
}

@Composable
private fun StreamChainCard(audit: StreamLinkDiagnosticsResult) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    MaxStreamGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Provider: ${audit.providerName}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryText
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (audit.loadLinksSuccess) Color(0x3300E676) else Color(0x33FF5252)
                ) {
                    Text(
                        text = if (audit.loadLinksSuccess) "LoadLinks: Success" else "LoadLinks: Failed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (audit.loadLinksSuccess) Color(0xFF00E676) else Color(0xFFFF5252),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Flow Chain Representation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0x22FFFFFF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Provider: ${audit.providerName}", style = MaterialTheme.typography.bodySmall, color = primaryText)
                Text("↓", color = MaxStreamTheme.ElectricCyan, fontWeight = FontWeight.Bold)
                Text("LoadLinks(): ${if (audit.loadLinksSuccess) "Executed" else "Failed"}", style = MaterialTheme.typography.bodySmall, color = primaryText)
                Text("↓", color = MaxStreamTheme.ElectricCyan, fontWeight = FontWeight.Bold)
                Text("Extractor: ${audit.extractorInvoked}", style = MaterialTheme.typography.bodySmall, color = primaryText)
                Text("↓", color = MaxStreamTheme.ElectricCyan, fontWeight = FontWeight.Bold)
                Text("Extractor Registered: ${if (audit.extractorRegistered) "Yes (Available in ExtractorApi)" else "No (MISSING)"}", style = MaterialTheme.typography.bodySmall, color = if (audit.extractorRegistered) Color(0xFF00E676) else Color(0xFFFF5252))
                Text("↓", color = MaxStreamTheme.ElectricCyan, fontWeight = FontWeight.Bold)
                Text("Result: ${audit.resultText} (${audit.videoLinksFound} Links)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = if (audit.videoLinksFound > 0) Color(0xFF00E676) else Color(0xFFFF5252))
            }

            if (audit.rootCause != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FF5252),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Root Cause:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFF8A80))
                        Text(audit.rootCause, style = MaterialTheme.typography.bodySmall, color = primaryText)
                    }
                }
            }

            if (audit.sampleStreams.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                    Text(
                        text = "Verified: ${audit.sampleStreams.joinToString(" • ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E676)
                    )
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: SEARCH & HOME PAGE PIPELINE DIAGNOSTICS
// =========================================================================

@Composable
private fun SearchAndHomeTabContent(
    searchAudits: List<SearchDiagnosticsResult>,
    homeAudits: List<HomePageDiagnosticsResult>,
    loadAudits: List<LoadDiagnosticsResult>
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Search Pipeline Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
        }

        items(searchAudits) { audit ->
            SearchPipelineCard(audit)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Home Page & Load() Audits",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
        }

        items(homeAudits) { home ->
            HomePageAuditCard(home)
        }

        items(loadAudits) { load ->
            LoadAuditCard(load)
        }
    }
}

@Composable
private fun SearchPipelineCard(audit: SearchDiagnosticsResult) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    MaxStreamGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search: \"${audit.query}\" on ${audit.providerName}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryText
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (audit.isSuccess) Color(0x3300E676) else Color(0x33FFB300)
                ) {
                    Text(
                        text = if (audit.isSuccess) "Passed" else "Failed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (audit.isSuccess) Color(0xFF00E676) else Color(0xFFFFB300),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Pipeline Steps
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0x22FFFFFF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PipelineStepRow("Search Request", audit.requestStep)
                PipelineStepRow("Provider Called", audit.providerCalledStep)
                PipelineStepRow("Response Received", audit.responseReceivedStep)
                PipelineStepRow("Parser Executed", audit.parserExecutedStep)
                PipelineStepRow("Results Returned", audit.resultsReturnedStep)
            }

            if (!audit.isSuccess && audit.failureReason != null) {
                Text(
                    text = "Reason: ${audit.failureReason}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFF8A80)
                )

                if (audit.possibleCauses.isNotEmpty()) {
                    Text(
                        text = "Possible Causes:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryText
                    )
                    audit.possibleCauses.forEach { cause ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✓", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(cause, style = MaterialTheme.typography.bodySmall, color = mutedText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineStepRow(step: String, detail: String) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(step, style = MaterialTheme.typography.labelSmall, color = MaxStreamTheme.ElectricCyan)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomePageAuditCard(home: HomePageDiagnosticsResult) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    MaxStreamGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Homepage Audit: ${home.providerName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = primaryText)
                Text(if (home.parserSuccess) "✓ Success" else "✗ Failed", color = if (home.parserSuccess) Color(0xFF00E676) else Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text("Request: ${if (home.requestSuccess) "Success" else "Failed"} • Response: ${if (home.responseSuccess) "Success" else "Failed"} • Parser: ${if (home.parserSuccess) "Success" else "Failed"}", style = MaterialTheme.typography.bodySmall, color = mutedText)
            Text("Returned: ${home.returnedValue}", style = MaterialTheme.typography.bodySmall, color = primaryText)
            Text("Expected: ${home.expectedValue}", style = MaterialTheme.typography.bodySmall, color = MaxStreamTheme.ElectricCyan)
            if (home.reason != null) {
                Text("Reason: ${home.reason}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF8A80))
            }
        }
    }
}

@Composable
private fun LoadAuditCard(load: LoadDiagnosticsResult) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    MaxStreamGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Movie/Show Load Audit: ${load.providerName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = primaryText)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Metadata: ${if (load.metadataPassed) "✓" else "✗"}", color = if (load.metadataPassed) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 12.sp)
                Text("Poster: ${if (load.posterPassed) "✓" else "✗"}", color = if (load.posterPassed) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 12.sp)
                Text("Episodes: ${if (load.episodesPassed) "✓" else "✗"}", color = if (load.episodesPassed) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 12.sp)
                Text("Recommendations: ${if (load.recommendationsPassed) "✓" else "✗"}", color = if (load.recommendationsPassed) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 12.sp)
            }
            if (load.failureReason != null) {
                Text("Reason: ${load.failureReason}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF8A80))
            }
        }
    }
}

// =========================================================================
// TAB 4: EXTRACTOR REGISTRY AUDIT
// =========================================================================

@Composable
private fun ExtractorsTabContent(report: ExtractorAuditReport) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Extractor Registry Inspector",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
        }

        // Summary Statistics Card
        item {
            MaxStreamGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Required Extractors", style = MaterialTheme.typography.labelSmall, color = mutedText)
                        Text("${report.requiredExtractorsCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = primaryText)
                    }
                    Column {
                        Text("Available", style = MaterialTheme.typography.labelSmall, color = mutedText)
                        Text("${report.availableExtractorsCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF00E676))
                    }
                    Column {
                        Text("Missing", style = MaterialTheme.typography.labelSmall, color = mutedText)
                        Text("${report.missingExtractorsCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFF5252))
                    }
                    Column {
                        Text("Affected Providers", style = MaterialTheme.typography.labelSmall, color = mutedText)
                        Text("${report.totalAffectedProviders}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFFB300))
                    }
                }
            }
        }

        // Missing Extractors Section
        item {
            Text(
                text = "Missing Extractors (${report.missingExtractorsCount}):",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFFF5252)
            )
        }

        items(report.missingList) { missing ->
            MaxStreamGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                borderColor = Color(0xFFFF5252).copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(missing.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFF8A80))
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0x33FF5252)) {
                            Text("MISSING", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text("Required Class: ${missing.requiredClass}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = mutedText)
                    Text("Providers affected: ${missing.affectedProviders.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = primaryText)
                }
            }
        }
    }
}

// =========================================================================
// TAB 5: PARSERS, CRYPTO & SDK AUDIT
// =========================================================================

@Composable
private fun ParsersAndSdkTabContent(
    parserAudit: ParserAuditReport,
    sdkAudit: CloudStreamSdkAuditReport,
    networkAudit: NetworkStackAuditReport,
    reflectionAudit: RuntimeReflectionAuditReport
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Parser & Crypto Stack Audit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
        }

        items(parserAudit.items) { item ->
            MaxStreamGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${if (item.isAvailable) "✓" else "✗"} ${item.name}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (item.isAvailable) Color(0xFF00E676) else Color(0xFFFF5252))
                        Text(item.targetClass, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = mutedText)
                        if (!item.isAvailable) {
                            Text("Impact: ${item.impact}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB300))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isAvailable) Color(0x3300E676) else Color(0x33FF5252)
                    ) {
                        Text(if (item.isAvailable) "Available" else "Missing", color = if (item.isAvailable) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "CloudStream SDK Compatibility (${sdkAudit.implementationPercentage}% Implemented)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
        }

        items(sdkAudit.features) { feature ->
            MaxStreamGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${if (feature.isImplemented) "✓" else "✗"} ${feature.name}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (feature.isImplemented) Color(0xFF00E676) else Color(0xFFFF5252))
                        Text(feature.description, style = MaterialTheme.typography.bodySmall, color = mutedText)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (feature.isImplemented) Color(0x3300E676) else Color(0x33FF5252)
                    ) {
                        Text(if (feature.isImplemented) "OK" else "MISSING", color = if (feature.isImplemented) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 6: ROOT CAUSE ENGINE
// =========================================================================

@Composable
private fun RootCauseTabContent(rootCauses: List<RootCauseItem>) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Root Cause Analysis Engine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
            Text(
                text = "Never shows only \"Failed\" — explains the exact missing class, function, or parser.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText
            )
        }

        if (rootCauses.isEmpty()) {
            item {
                MaxStreamGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No critical compatibility failures detected across providers.", color = Color(0xFF00E676))
                    }
                }
            }
        } else {
            items(rootCauses) { item ->
                MaxStreamGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    borderColor = Color(0xFFFF5252).copy(alpha = 0.40f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Problem: ${item.problem}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = primaryText)
                        Text("Root Cause: ${item.rootCause}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFFF8A80))

                        if (item.missingClass != null) {
                            Text("Missing Class: ${item.missingClass}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = mutedText)
                        }
                        if (item.missingFunction != null) {
                            Text("Missing Function: ${item.missingFunction}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = mutedText)
                        }
                        if (item.missingExtractor != null) {
                            Text("Missing Extractor: ${item.missingExtractor}", style = MaterialTheme.typography.bodySmall, color = primaryText)
                        }
                        if (item.affectedProviders.isNotEmpty()) {
                            Text("Affected Providers: ${item.affectedProviders.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaxStreamTheme.ElectricCyan)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x2200E676),
                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Recommended Fix: ${item.recommendedFix}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB9F6CA),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 7: FULL REPORT & EXPORT
// =========================================================================

@Composable
private fun FullReportTabContent(reportMarkdown: String) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Complete System Report",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(reportMarkdown))
                    Toast.makeText(context, "Full Report copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy", fontSize = 12.sp)
            }
        }

        MaxStreamGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = reportMarkdown,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                    color = primaryText
                )
            }
        }
    }
}
