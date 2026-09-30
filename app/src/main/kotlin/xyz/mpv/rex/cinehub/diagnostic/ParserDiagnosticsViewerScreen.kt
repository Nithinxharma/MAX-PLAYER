package xyz.mpv.rex.cinehub.diagnostic

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import xyz.mpv.rex.cinehub.diagnostic.model.CandidateLinkItem
import xyz.mpv.rex.cinehub.diagnostic.model.JsonMappingAnalysis
import xyz.mpv.rex.cinehub.diagnostic.model.LoadLinksFailureDiagnosticPayload
import xyz.mpv.rex.cinehub.diagnostic.model.ParserFailureDiagnosticPayload
import xyz.mpv.rex.cinehub.diagnostic.model.RegexMatchResultItem
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamGlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object ParserDiagnosticsViewerScreenRoute : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val context = LocalContext.current
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val isDark = isSystemInDarkTheme()

        val searchDiagnostics by ParserDiagnosticCollector.latestSearchDiagnostics.collectAsState()
        val loadLinksDiagnostics by ParserDiagnosticCollector.latestLoadLinksDiagnostics.collectAsState()
        val selectedSearchDiag by ParserDiagnosticCollector.selectedSearchDiagnostic.collectAsState()
        val selectedLoadDiag by ParserDiagnosticCollector.selectedLoadLinksDiagnostic.collectAsState()

        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Search Parser Failures", "loadLinks() Failures", "Live Diagnostic Runner")

        // Populate sample diagnostics on first launch if empty
        LaunchedEffect(Unit) {
            if (searchDiagnostics.isEmpty() && APIHolder.apis.isNotEmpty()) {
                val sampleApi = APIHolder.apis.firstOrNull { it.name.contains("movies", ignoreCase = true) } ?: APIHolder.apis.first()
                ParserDiagnosticCollector.analyzeSearchParser(sampleApi, "Inception")
            }
        }

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = "Parser & Link Diagnostics",
                    onBackClick = { backstack.removeLastOrNull() },
                    actions = {
                        IconButton(onClick = {
                            ParserDiagnosticCollector.clear()
                            Toast.makeText(context, "Diagnostics Cleared", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                        }
                    }
                )
            },
            containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("parser_diagnostics_viewer")
            ) {
                // Top Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.surface,
                    contentColor = MaxStreamTheme.CrimsonAccent,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.then(with(TabRowDefaults) { Modifier.tabIndicatorOffset(tabPositions[selectedTab]) }),
                                color = MaxStreamTheme.CrimsonAccent
                            )
                        }
                    },
                    divider = { HorizontalDivider(color = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outlineVariant) }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            selectedContentColor = MaxStreamTheme.CrimsonAccent,
                            unselectedContentColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> SearchParserDiagnosticsTab(
                        diagnostics = searchDiagnostics,
                        selected = selectedSearchDiag,
                        onSelect = { ParserDiagnosticCollector.selectSearchDiagnostic(it) }
                    )
                    1 -> LoadLinksDiagnosticsTab(
                        diagnostics = loadLinksDiagnostics,
                        selected = selectedLoadDiag,
                        onSelect = { ParserDiagnosticCollector.selectLoadLinksDiagnostic(it) }
                    )
                    2 -> LiveDiagnosticRunnerTab()
                }
            }
        }
    }
}

// =========================================================================
// TAB 1: SEARCH PARSER DIAGNOSTICS (HTTP 200 / 0 RESULTS)
// =========================================================================

@Composable
private fun SearchParserDiagnosticsTab(
    diagnostics: List<ParserFailureDiagnosticPayload>,
    selected: ParserFailureDiagnosticPayload?,
    onSelect: (ParserFailureDiagnosticPayload) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (diagnostics.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaxStreamTheme.TextMuted, modifier = Modifier.size(48.dp))
                Text(
                    text = "No Parser Failure Payloads Captured Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Run a search test in Developer Test Center or execute the Live Diagnostic Runner to capture raw HTTP 200 response previews, CSS selectors, candidate links, and regex results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Horizontal list of captured items if multiple
            if (diagnostics.size > 1) {
                item {
                    Text(
                        text = "Captured Diagnostics Payloads (${diagnostics.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.CrimsonAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        diagnostics.forEach { diag ->
                            val isCurrent = diag == (selected ?: diagnostics.first())
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f) else if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (isCurrent) MaxStreamTheme.CrimsonAccent else Color.Transparent),
                                modifier = Modifier.clickable { onSelect(diag) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (diag.isZeroResultFailure) Icons.Default.Error else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (diag.isZeroResultFailure) Color(0xFFFF2D55) else Color(0xFF00E676),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${diag.providerName} [\"${diag.query}\"]",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Render Selected Detailed Payload
            val currentPayload = selected ?: diagnostics.first()
            item {
                ParserFailurePayloadCard(currentPayload)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParserFailurePayloadCard(payload: ParserFailureDiagnosticPayload) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isPreviewExpanded by remember { mutableStateOf(false) }

    MaxStreamGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = payload.providerName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF2D55).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFFF2D55).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "HTTP ${payload.httpStatus} • 0 RESULTS",
                                color = Color(0xFFFF2D55),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Search Query: \"${payload.query}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaxStreamTheme.TextMuted
                    )
                }

                IconButton(
                    onClick = {
                        val json = formatParserPayloadJson(payload)
                        clipboardManager.setText(AnnotatedString(json))
                        Toast.makeText(context, "Full Diagnostic JSON Copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", tint = MaxStreamTheme.CrimsonAccent)
                }
            }

            // Failure Diagnosis Callout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFF2D55).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFFFF2D55).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF2D55), modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "PARSER POINT OF FAILURE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF2D55)
                        )
                        Text(
                            text = payload.failureReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // URLs Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DiagnosticUrlRow(label = "Request URL", url = payload.requestUrl)
                DiagnosticUrlRow(label = "Final Redirected URL", url = payload.finalUrl)
            }

            // HTTP Metadata Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticMetaBadge(label = "STATUS", value = "${payload.httpStatus} OK", icon = Icons.Default.Http, modifier = Modifier.weight(1f))
                DiagnosticMetaBadge(label = "CONTENT TYPE", value = payload.contentType.substringBefore(';'), icon = Icons.Outlined.DataObject, modifier = Modifier.weight(1.3f))
                DiagnosticMetaBadge(label = "PAYLOAD SIZE", value = payload.responseSizeFormatted, icon = Icons.Outlined.Storage, modifier = Modifier.weight(1f))
            }

            HorizontalDivider(color = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outlineVariant)

            // 1. Response Preview (first 10 KB max)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Code, contentDescription = null, tint = MaxStreamTheme.CrimsonAccent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Raw Response Preview (First 10 KB)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { isPreviewExpanded = !isPreviewExpanded }) {
                            Text(if (isPreviewExpanded) "Collapse" else "Expand", fontSize = 11.sp)
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(payload.responsePreview))
                            Toast.makeText(context, "10 KB Preview Copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Preview", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF0D0E15) else Color(0xFFF1F3F7),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = if (isPreviewExpanded) payload.responsePreview else payload.responsePreview.take(1200) + if (payload.responsePreview.length > 1200) "\n... [Tap Expand to view full 10 KB preview]" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp),
                            color = if (isDark) Color(0xFF00FF66) else Color(0xFF006622),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outlineVariant)

            // 2. Parser Selectors & Match Counts
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.FindInPage, contentDescription = null, tint = Color(0xFF00C2FF), modifier = Modifier.size(16.dp))
                    Text(
                        text = "Parser Selectors & Match Counts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    payload.selectorMatchCounts.forEach { (selector, count) ->
                        val hasMatches = count > 0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (hasMatches) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF2D55).copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, if (hasMatches) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF2D55).copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = selector,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    fontWeight = FontWeight.Medium
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (hasMatches) Color(0xFF00E676) else Color(0xFFFF2D55)
                                ) {
                                    Text(
                                        text = "$count",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outlineVariant)

            // 3. Candidate Links Found
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                    Text(
                        text = "Candidate Media Links Discovered (${payload.candidateLinksFound.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (payload.candidateLinksFound.isEmpty()) {
                    Text(
                        text = "No candidate <a> links discovered in the response body.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaxStreamTheme.TextMuted
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        payload.candidateLinksFound.take(8).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.text.ifBlank { "Untitled Link" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.href,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
                                        color = MaxStreamTheme.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (item.isProbableMedia) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF00E676).copy(alpha = 0.2f)) {
                                        Text("MEDIA", color = Color(0xFF00E676), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outlineVariant)

            // 4. JSON Mapping Results & Regex Matches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.DataArray, contentDescription = null, tint = Color(0xFFAF52DE), modifier = Modifier.size(16.dp))
                    Text(
                        text = "JSON Mapping & Regex Engine Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "JSON Parsing: ${payload.jsonMappingResults.rootType} (${payload.jsonMappingResults.totalKeysOrItems} keys)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (payload.jsonMappingResults.isJson) Color(0xFF00E676) else MaxStreamTheme.TextMuted
                        )
                        Text(
                            text = payload.jsonMappingResults.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaxStreamTheme.TextSecondary
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Regex Evaluated Patterns:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        payload.regexMatchResults.forEach { r ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = r.patternName, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                Text(
                                    text = "${r.matchCount} matches",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (r.matchCount > 0) Color(0xFF00E676) else MaxStreamTheme.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 2: LOADLINKS() EXTRACTION DIAGNOSTICS
// =========================================================================

@Composable
private fun LoadLinksDiagnosticsTab(
    diagnostics: List<LoadLinksFailureDiagnosticPayload>,
    selected: LoadLinksFailureDiagnosticPayload?,
    onSelect: (LoadLinksFailureDiagnosticPayload) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (diagnostics.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = MaxStreamTheme.TextMuted, modifier = Modifier.size(48.dp))
                Text(
                    text = "No loadLinks() Failures Captured Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "When loadLinks() is invoked and fails to resolve media streams, the full extractor audit payload (Provider URL, Extractor URLs Found/Resolved, Registered Extractors, Dynamic DEX Discoveries, and Failure Reason) will be displayed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val currentPayload = selected ?: diagnostics.first()
            item {
                LoadLinksFailurePayloadCard(currentPayload)
            }
        }
    }
}

@Composable
private fun LoadLinksFailurePayloadCard(payload: LoadLinksFailureDiagnosticPayload) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    MaxStreamGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = payload.providerName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF2D55).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFFF2D55).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "LOADLINKS RESOLUTION FAILED",
                                color = Color(0xFFFF2D55),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Provider URL: ${payload.providerUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaxStreamTheme.TextMuted
                    )
                }

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(payload.details))
                        Toast.makeText(context, "loadLinks Diagnostic Copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Details", tint = MaxStreamTheme.CrimsonAccent)
                }
            }

            // Failure Reason Callout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFF2D55).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFFFF2D55).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF2D55), modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "FAILURE REASON",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF2D55)
                        )
                        Text(
                            text = payload.failureReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Extractor URLs Found vs Resolved
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiagnosticMetaBadge(label = "EXTRACTOR URLS FOUND", value = "${payload.extractorUrlsFound.size} URLs", icon = Icons.Default.Link, modifier = Modifier.weight(1f))
                DiagnosticMetaBadge(label = "EXTRACTOR URLS RESOLVED", value = "${payload.extractorUrlsResolved.size} Links", icon = Icons.Default.PlayArrow, modifier = Modifier.weight(1f))
            }

            // Discovered Extractor URLs
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Discovered Host / Extractor URLs:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (payload.extractorUrlsFound.isEmpty()) {
                    Text("No candidate extractor URLs found in data payload.", style = MaterialTheme.typography.bodySmall, color = MaxStreamTheme.TextMuted)
                } else {
                    payload.extractorUrlsFound.forEach { u ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color.White.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = u,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outlineVariant)

            // Extractor Registry Overview
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Extractor Runtime Inventory:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticMetaBadge(label = "APIHOLDER REGISTERED", value = "${payload.registeredExtractors.size} active", icon = Icons.Default.CheckCircle, modifier = Modifier.weight(1f))
                    DiagnosticMetaBadge(label = "REGISTRY EXTRACTORS", value = "${payload.registryExtractors.size} catalog", icon = Icons.Default.Extension, modifier = Modifier.weight(1f))
                }

                if (payload.dynamicExtractorDiscoveries.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFAF52DE).copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Dynamic DEX Extractor Discoveries:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFAF52DE))
                            Text(payload.dynamicExtractorDiscoveries.joinToString(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: LIVE DIAGNOSTIC RUNNER
// =========================================================================

@Composable
private fun LiveDiagnosticRunnerTab() {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProviderName by remember { mutableStateOf(APIHolder.apis.firstOrNull()?.name ?: "Moviesmod") }
    var queryInput by remember { mutableStateOf("Inception") }
    var isRunning by remember { mutableStateOf(false) }
    var executionResult by remember { mutableStateOf<ParserFailureDiagnosticPayload?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MaxStreamGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Live Search & Parser Diagnostics Inspector",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Execute live HTTP inspection on any installed CloudStream provider to verify response headers, redirect chains, HTML previews up to 10 KB, CSS selectors, candidate links, and regex matches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaxStreamTheme.TextMuted
                    )

                    // Provider selector chips
                    Text(text = "Select Provider:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val allApis = APIHolder.apis
                        if (allApis.isEmpty()) {
                            listOf("Moviesmod", "TopMovies", "Bollyflix", "SuperStream", "VegaMovies").forEach { name ->
                                val isSelected = selectedProviderName == name
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaxStreamTheme.CrimsonAccent else if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { selectedProviderName = name }
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        } else {
                            allApis.forEach { api ->
                                val isSelected = selectedProviderName == api.name
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaxStreamTheme.CrimsonAccent else if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { selectedProviderName = api.name }
                                ) {
                                    Text(
                                        text = api.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        label = { Text("Search Query") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isRunning = true
                                try {
                                    val api = APIHolder.apis.find { it.name.equals(selectedProviderName, ignoreCase = true) }
                                        ?: object : com.lagradost.cloudstream3.MainAPI() {
                                            override var name = selectedProviderName
                                            override var mainUrl = when (selectedProviderName.lowercase()) {
                                                "moviesmod" -> "https://moviesmod.ai.in"
                                                "topmovies" -> "https://moviesleech.club"
                                                "bollyflix" -> "https://bollyflix.tools"
                                                "superstream" -> "https://superstream.media"
                                                else -> "https://vegamovies.ist"
                                            }
                                        }
                                    executionResult = ParserDiagnosticCollector.analyzeSearchParser(api, queryInput)
                                    Toast.makeText(context, "Parser Diagnostics Completed", Toast.LENGTH_SHORT).show()
                                } catch (t: Throwable) {
                                    Toast.makeText(context, "Execution failed: ${t.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isRunning = false
                                }
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inspecting HTTP Response & Parsing...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Live Parser Diagnostics")
                        }
                    }
                }
            }
        }

        if (executionResult != null) {
            item {
                Text(
                    text = "Live Execution Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaxStreamTheme.CrimsonAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                ParserFailurePayloadCard(executionResult!!)
            }
        }
    }
}

// =========================================================================
// HELPER COMPONENTS & JSON EXPORTERS
// =========================================================================

@Composable
private fun DiagnosticUrlRow(label: String, url: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaxStreamTheme.TextMuted)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(url))
                Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun DiagnosticMetaBadge(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color.White.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaxStreamTheme.CrimsonAccent, modifier = Modifier.size(16.dp))
            Column {
                Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaxStreamTheme.TextMuted)
                Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun formatParserPayloadJson(payload: ParserFailureDiagnosticPayload): String {
    return buildString {
        appendLine("{")
        appendLine("  \"providerName\": \"${payload.providerName}\",")
        appendLine("  \"query\": \"${payload.query}\",")
        appendLine("  \"requestUrl\": \"${payload.requestUrl}\",")
        appendLine("  \"finalUrl\": \"${payload.finalUrl}\",")
        appendLine("  \"httpStatus\": ${payload.httpStatus},")
        appendLine("  \"contentType\": \"${payload.contentType}\",")
        appendLine("  \"responseSize\": ${payload.responseSize},")
        appendLine("  \"failureReason\": \"${payload.failureReason}\",")
        appendLine("  \"selectorMatchCounts\": {")
        payload.selectorMatchCounts.entries.forEachIndexed { i, (k, v) ->
            val comma = if (i < payload.selectorMatchCounts.size - 1) "," else ""
            appendLine("    \"$k\": $v$comma")
        }
        appendLine("  },")
        appendLine("  \"candidateLinksFoundCount\": ${payload.candidateLinksFound.size},")
        appendLine("  \"jsonMappingResults\": {")
        appendLine("    \"isJson\": ${payload.jsonMappingResults.isJson},")
        appendLine("    \"rootType\": \"${payload.jsonMappingResults.rootType}\",")
        appendLine("    \"summary\": \"${payload.jsonMappingResults.summary}\"")
        appendLine("  }")
        appendLine("}")
    }
}
