package xyz.mpv.rex.cinehub.diagnostic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudStreamQueryTerminalView(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputCommand by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var isRawLogcatMode by remember { mutableStateOf(false) }
    var selectedProviderName by remember { mutableStateOf("SuperStream") }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    val logs = remember { mutableStateListOf<LogEntry>() }
    val allProviders = remember { ProviderRegistry.getAll() }

    fun addLog(msg: String, level: String = "INFO", tag: String = "CS3_TERMINAL", color: Color = MaxStreamTheme.ElectricCyan) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        logs.add(LogEntry(time, level, tag, msg, color))
    }

    LaunchedEffect(Unit) {
        if (logs.isEmpty()) {
            addLog("MAX STREAM CloudStream Core 3.0.0 Diagnostic Engine Initialized", "SYSTEM", "INIT", MaxStreamTheme.NeonGreen)
            addLog("Type 'help' for terminal commands or use quick diagnostic chips below.", "INFO", "INIT", MaxStreamTheme.TextSecondary)
            addLog("Installed providers count: ${allProviders.size}", "INFO", "REGISTRY", MaxStreamTheme.ElectricCyan)
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    fun executeInspect(providerName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val provider = ProviderRegistry.get(providerName)
            if (provider == null) {
                withContext(Dispatchers.Main) {
                    addLog("Provider '$providerName' not found in registry!", "ERROR", "INSPECT", MaxStreamTheme.CrimsonAccent)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                addLog("========== INSPECTING CLASS & STRUCTURE: [${provider.name}] ==========", "INSPECT", "INSPECT", MaxStreamTheme.ElectricCyan)
                addLog("Class: ${provider.javaClass.name}", "INFO", "CLASS", MaxStreamTheme.TextPrimary)
                addLog("Superclass: ${provider.javaClass.superclass?.name}", "INFO", "CLASS", MaxStreamTheme.TextSecondary)
                addLog("Interfaces: ${provider.javaClass.interfaces.joinToString { it.simpleName }}", "INFO", "CLASS", MaxStreamTheme.TextSecondary)
                addLog("ClassLoader: ${provider.javaClass.classLoader?.javaClass?.simpleName}", "INFO", "CLASS", MaxStreamTheme.TextSecondary)
                addLog("mainUrl: ${provider.mainUrl}", "INFO", "PROP", MaxStreamTheme.NeonGreen)
                addLog("lang: ${provider.lang} | supportedTypes: ${provider.supportedTypes}", "INFO", "PROP", MaxStreamTheme.TextPrimary)
                addLog("hasMainPage: ${provider.hasMainPage} | hasQuickSearch: ${provider.hasQuickSearch}", "INFO", "PROP", MaxStreamTheme.TextPrimary)

                val methods = provider.javaClass.declaredMethods
                    .filter { !it.name.contains("$") }
                    .take(15)
                    .map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }}): ${it.returnType.simpleName}" }

                addLog("Declared Methods (${methods.size}):", "INFO", "METHODS", MaxStreamTheme.AmberWarning)
                methods.forEach { m ->
                    addLog("  • $m", "DEBUG", "METHOD", MaxStreamTheme.TextSecondary)
                }
                addLog("==================================================================", "INSPECT", "INSPECT", MaxStreamTheme.ElectricCyan)
            }
        }
    }

    fun executeDebugProvider(providerName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRunning = true
                addLog(">>> STARTING STEP-BY-STEP DIAGNOSTIC PIPELINE FOR [$providerName] <<<", "TEST", "DEBUGGER", MaxStreamTheme.AmberWarning)
            }

            val provider = ProviderRegistry.get(providerName)
            if (provider == null) {
                withContext(Dispatchers.Main) {
                    addLog("❌ STEP 0 FAILED: Provider '$providerName' is not registered!", "FATAL", "DEBUGGER", MaxStreamTheme.CrimsonAccent)
                    isRunning = false
                }
                return@launch
            }

            // Step 1: Reachability check
            withContext(Dispatchers.Main) {
                addLog("STEP 1/5: Checking mainUrl network reachability (${provider.mainUrl})...", "STEP", "DEBUGGER", MaxStreamTheme.ElectricCyan)
            }
            try {
                val pingRes = app.head(provider.mainUrl, timeout = 10L)
                withContext(Dispatchers.Main) {
                    addLog("✅ STEP 1 PASSED: mainUrl HTTP status code = ${pingRes.code}", "PASS", "DEBUGGER", MaxStreamTheme.NeonGreen)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    addLog("⚠️ STEP 1 WARNING: Ping timed out or blocked: ${t.message} (Proceeding to search test)", "WARN", "DEBUGGER", MaxStreamTheme.AmberWarning)
                }
            }

            // Step 2: Search Query Test
            withContext(Dispatchers.Main) {
                addLog("STEP 2/5: Executing search query with keyword 'Marvel'...", "STEP", "DEBUGGER", MaxStreamTheme.ElectricCyan)
            }
            var searchResults: List<com.lagradost.cloudstream3.SearchResponse> = emptyList()
            try {
                searchResults = provider.search("Marvel") ?: provider.quickSearch("Marvel") ?: emptyList()
                withContext(Dispatchers.Main) {
                    if (searchResults.isNotEmpty()) {
                        addLog("✅ STEP 2 PASSED: Received ${searchResults.size} search results!", "PASS", "DEBUGGER", MaxStreamTheme.NeonGreen)
                        searchResults.take(3).forEach { r ->
                            addLog("   [Result] ${r.name} (${r.type}) -> ${r.url}", "DATA", "SEARCH", MaxStreamTheme.TextPrimary)
                        }
                    } else {
                        addLog("⚠️ STEP 2 NOTICE: Search returned 0 items. Trying fallback query...", "WARN", "DEBUGGER", MaxStreamTheme.AmberWarning)
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    addLog("❌ STEP 2 FAILED: search() threw exception: ${t.javaClass.simpleName}: ${t.message}", "ERROR", "DEBUGGER", MaxStreamTheme.CrimsonAccent)
                }
            }

            // Step 3: Load Details Test
            withContext(Dispatchers.Main) {
                addLog("STEP 3/5: Testing load details with content URL...", "STEP", "DEBUGGER", MaxStreamTheme.ElectricCyan)
            }
            val testContentUrl = searchResults.firstOrNull()?.url ?: provider.mainUrl
            var loadRes: com.lagradost.cloudstream3.LoadResponse? = null
            try {
                loadRes = provider.load(testContentUrl)
                withContext(Dispatchers.Main) {
                    if (loadRes != null) {
                        addLog("✅ STEP 3 PASSED: Loaded '${loadRes.name}' (Type: ${loadRes.type})", "PASS", "DEBUGGER", MaxStreamTheme.NeonGreen)
                    } else {
                        addLog("⚠️ STEP 3 NOTICE: load() returned null for $testContentUrl", "WARN", "DEBUGGER", MaxStreamTheme.AmberWarning)
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    addLog("❌ STEP 3 FAILED: load() threw exception: ${t.message}", "ERROR", "DEBUGGER", MaxStreamTheme.CrimsonAccent)
                }
            }

            // Step 4: Link Extraction Test
            withContext(Dispatchers.Main) {
                addLog("STEP 4/5: Testing stream link extraction pipeline...", "STEP", "DEBUGGER", MaxStreamTheme.ElectricCyan)
            }
            val extractedLinks = mutableListOf<ExtractorLink>()
            try {
                val targetData = if (loadRes is com.lagradost.cloudstream3.MovieLoadResponse) {
                    loadRes.dataUrl
                } else if (loadRes is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                    loadRes.episodes.firstOrNull()?.data ?: testContentUrl
                } else {
                    testContentUrl
                }

                ProviderRegistry.extractLinks(
                    provider.name,
                    targetData,
                    onSubtitle = { sub: SubtitleFile ->
                        coroutineScope.launch(Dispatchers.Main) {
                            addLog("   [Subtitle Discovered] (${sub.lang}) ${sub.url}", "SUB", "LINK", MaxStreamTheme.AmberWarning)
                        }
                    },
                    onLink = { link: ExtractorLink ->
                        extractedLinks.add(link)
                        coroutineScope.launch(Dispatchers.Main) {
                            addLog("   [Link Discovered] [${link.source}] ${link.name} (Quality: ${link.quality}p) -> ${link.url}", "STREAM", "LINK", MaxStreamTheme.NeonGreen)
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    if (extractedLinks.isNotEmpty()) {
                        addLog("✅ STEP 4 PASSED: Successfully extracted ${extractedLinks.size} direct stream links!", "PASS", "DEBUGGER", MaxStreamTheme.NeonGreen)
                    } else {
                        addLog("⚠️ STEP 4 NOTICE: No links directly extracted from test payload.", "WARN", "DEBUGGER", MaxStreamTheme.AmberWarning)
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    addLog("❌ STEP 4 FAILED: loadLinks error: ${t.message}", "ERROR", "DEBUGGER", MaxStreamTheme.CrimsonAccent)
                }
            }

            // Step 5: Final Report
            withContext(Dispatchers.Main) {
                addLog("------------------------------------------------------------------", "REPORT", "DEBUGGER", MaxStreamTheme.GlassBorder)
                if (extractedLinks.isNotEmpty() || searchResults.isNotEmpty()) {
                    addLog("DIAGNOSTIC STATUS: ✅ PROVIDER [$providerName] IS FUNCTIONAL & ACTIVE!", "STATUS", "SUMMARY", MaxStreamTheme.NeonGreen)
                } else {
                    addLog("DIAGNOSTIC STATUS: ⚠️ PROVIDER CHECK COMPLETED (Review warnings above)", "STATUS", "SUMMARY", MaxStreamTheme.AmberWarning)
                }
                addLog("------------------------------------------------------------------", "REPORT", "DEBUGGER", MaxStreamTheme.GlassBorder)
                isRunning = false
            }
        }
    }

    fun handleCommand(rawCmd: String) {
        val trimmed = rawCmd.trim()
        if (trimmed.isEmpty()) return
        val parts = trimmed.split(" ")
        val cmd = parts[0].lowercase()

        addLog("$ $trimmed", "CMD", "USER", Color.White)
        inputCommand = ""

        when (cmd) {
            "help" -> {
                addLog("Available Commands:", "HELP", "CLI", MaxStreamTheme.ElectricCyan)
                addLog("  • inspect <provider>   - View class structure, methods & capabilities", "HELP", "CLI", MaxStreamTheme.TextPrimary)
                addLog("  • debug <provider>     - Run complete step-by-step diagnostic test", "HELP", "CLI", MaxStreamTheme.TextPrimary)
                addLog("  • providers / ls       - List all active registered providers", "HELP", "CLI", MaxStreamTheme.TextPrimary)
                addLog("  • search <query>       - Search on currently selected provider", "HELP", "CLI", MaxStreamTheme.TextPrimary)
                addLog("  • searchall <query>    - Search across all installed providers", "HELP", "CLI", MaxStreamTheme.TextPrimary)
                addLog("  • logcat on/off        - Toggle between terminal and raw logcat", "HELP", "CLI", MaxStreamTheme.TextPrimary)
                addLog("  • clear                - Clear the terminal buffer", "HELP", "CLI", MaxStreamTheme.TextPrimary)
            }
            "clear" -> {
                logs.clear()
                addLog("Terminal buffer cleared.", "SYSTEM", "INIT", MaxStreamTheme.TextSecondary)
            }
            "providers", "ls" -> {
                val list = ProviderRegistry.getAll()
                addLog("Active Providers (${list.size}):", "REGISTRY", "CLI", MaxStreamTheme.ElectricCyan)
                list.forEach { p ->
                    addLog("  • [${p.name}] ${p.mainUrl} (Lang: ${p.lang}, Types: ${p.supportedTypes.joinToString()})", "REGISTRY", "CLI", MaxStreamTheme.TextPrimary)
                }
            }
            "inspect", "structure", "class" -> {
                val target = if (parts.size > 1) parts[1] else selectedProviderName
                executeInspect(target)
            }
            "debug", "test" -> {
                val target = if (parts.size > 1) parts[1] else selectedProviderName
                executeDebugProvider(target)
            }
            "logcat" -> {
                if (parts.size > 1 && parts[1].lowercase() == "off") {
                    isRawLogcatMode = false
                    addLog("Raw logcat mode: OFF", "MODE", "CLI", MaxStreamTheme.AmberWarning)
                } else {
                    isRawLogcatMode = !isRawLogcatMode
                    addLog("Raw logcat mode: ${if (isRawLogcatMode) "ON" else "OFF"}", "MODE", "CLI", MaxStreamTheme.AmberWarning)
                }
            }
            "search" -> {
                val query = parts.drop(1).joinToString(" ")
                if (query.isEmpty()) {
                    addLog("Usage: search <query>", "WARN", "CLI", MaxStreamTheme.AmberWarning)
                } else {
                    coroutineScope.launch(Dispatchers.IO) {
                        val results = ProviderRegistry.searchSingle(selectedProviderName, query)
                        withContext(Dispatchers.Main) {
                            addLog("Search results for '$query' on [$selectedProviderName]: ${results.size}", "SEARCH", "CLI", MaxStreamTheme.ElectricCyan)
                            results.forEach { r ->
                                addLog("  • ${r.name} -> ${r.url}", "RESULT", "CLI", MaxStreamTheme.TextPrimary)
                            }
                        }
                    }
                }
            }
            "searchall" -> {
                val query = parts.drop(1).joinToString(" ")
                if (query.isEmpty()) {
                    addLog("Usage: searchall <query>", "WARN", "CLI", MaxStreamTheme.AmberWarning)
                } else {
                    coroutineScope.launch(Dispatchers.IO) {
                        val map = ProviderRegistry.searchAll(query)
                        withContext(Dispatchers.Main) {
                            addLog("SearchAll results for '$query' across ${map.size} providers:", "SEARCH", "CLI", MaxStreamTheme.ElectricCyan)
                            map.forEach { (prov, list) ->
                                addLog("[$prov] (${list.size} hits):", "PROVIDER", "CLI", MaxStreamTheme.AmberWarning)
                                list.take(3).forEach { r ->
                                    addLog("   • ${r.name} -> ${r.url}", "ITEM", "CLI", MaxStreamTheme.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                addLog("Unknown command: '$trimmed'. Type 'help' for available commands.", "ERROR", "CLI", MaxStreamTheme.CrimsonAccent)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CloudStream Test Center",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaxStreamTheme.TextPrimary
                        )
                        Text(
                            text = "CS3 Query Terminal & Diagnostic Engine",
                            fontSize = 12.sp,
                            color = MaxStreamTheme.TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaxStreamTheme.TextPrimary
                        )
                    }
                },
                actions = {
                    // Logcat toggle
                    FilterChip(
                        selected = isRawLogcatMode,
                        onClick = { isRawLogcatMode = !isRawLogcatMode },
                        label = { Text(if (isRawLogcatMode) "Raw Logcat" else "Terminal", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isRawLogcatMode) Icons.Default.Code else Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                            selectedLabelColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Copy logs
                    IconButton(onClick = {
                        val allText = logs.joinToString("\n") { "[${it.timestamp}] [${it.tag}] ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("CS3 Terminal Logs", allText))
                        Toast.makeText(context, "Terminal logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy logs",
                            tint = MaxStreamTheme.TextSecondary
                        )
                    }

                    // Clear logs
                    IconButton(onClick = {
                        logs.clear()
                        addLog("Terminal logs cleared.", "SYSTEM", "INIT", MaxStreamTheme.TextSecondary)
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear logs",
                            tint = MaxStreamTheme.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaxStreamTheme.MidnightSurface
                )
            )
        },
        containerColor = MaxStreamTheme.AbyssBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Quick Action Bar & Provider Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { providerDropdownExpanded = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaxStreamTheme.MidnightSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Provider: $selectedProviderName",
                            fontSize = 12.sp,
                            color = MaxStreamTheme.ElectricCyan
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaxStreamTheme.ElectricCyan
                        )
                    }

                    DropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false },
                        modifier = Modifier.background(MaxStreamTheme.MidnightSurface)
                    ) {
                        val activeList = ProviderRegistry.getAll()
                        if (activeList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No providers registered", color = MaxStreamTheme.TextSecondary) },
                                onClick = { providerDropdownExpanded = false }
                            )
                        } else {
                            activeList.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name, color = MaxStreamTheme.TextPrimary) },
                                    onClick = {
                                        selectedProviderName = p.name
                                        providerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { executeDebugProvider(selectedProviderName) },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxStreamTheme.CrimsonAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Debug Provider", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { executeInspect(selectedProviderName) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaxStreamTheme.MidnightSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaxStreamTheme.ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspect", fontSize = 12.sp, color = MaxStreamTheme.ElectricCyan)
                }
            }

            // Quick Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { handleCommand("search Marvel") },
                    label = { Text("Search 'Marvel'", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaxStreamTheme.MidnightSurface)
                )
                AssistChip(
                    onClick = { handleCommand("searchall Action") },
                    label = { Text("SearchAll 'Action'", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaxStreamTheme.MidnightSurface)
                )
                AssistChip(
                    onClick = { handleCommand("providers") },
                    label = { Text("List Providers", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaxStreamTheme.MidnightSurface)
                )
            }

            // Terminal Console Window
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaxStreamTheme.MidnightSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, MaxStreamTheme.GlassBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { entry ->
                        if (isRawLogcatMode) {
                            Text(
                                text = "${entry.timestamp} 12345 12345 ${entry.level.take(1)}/${entry.tag}: ${entry.message}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = entry.color,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = "[${entry.timestamp}]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaxStreamTheme.TextMuted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[${entry.tag}]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaxStreamTheme.ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = entry.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = entry.color,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Command Input Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    placeholder = {
                        Text(
                            text = "Enter terminal command (e.g. debug, inspect, searchall)...",
                            fontSize = 13.sp,
                            color = MaxStreamTheme.TextMuted
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaxStreamTheme.MidnightSurface,
                        unfocusedContainerColor = MaxStreamTheme.MidnightSurface,
                        focusedBorderColor = MaxStreamTheme.ElectricCyan,
                        unfocusedBorderColor = MaxStreamTheme.GlassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { handleCommand(inputCommand) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaxStreamTheme.CrimsonAccent, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Execute Command",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
