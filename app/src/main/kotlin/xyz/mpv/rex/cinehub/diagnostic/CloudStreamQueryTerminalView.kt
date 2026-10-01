package xyz.mpv.rex.cinehub.diagnostic

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.utils.media.MediaUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    COMMAND, INFO, SUCCESS, WARN, ERROR, STREAM, JSON
}

data class TerminalLogEntry(
    val timestamp: String,
    val message: String,
    val level: LogLevel,
    val linkUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudStreamQueryTerminalView(
    modifier: Modifier = Modifier,
    registry: ProviderRegistry? = null,
    extensionManager: ExtensionManager? = null,
    okHttpClient: OkHttpClient? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val logEntries = remember { mutableStateListOf<TerminalLogEntry>() }
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var selectedProviderName by remember { mutableStateOf<String?>(null) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    fun addLog(msg: String, level: LogLevel = LogLevel.INFO, linkUrl: String? = null) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        logEntries.add(TerminalLogEntry(timestamp = time, message = msg, level = level, linkUrl = linkUrl))
    }

    LaunchedEffect(Unit) {
        if (logEntries.isEmpty()) {
            addLog("MAX STREAM CloudStream .cs3 Interactive Terminal v2.4", LogLevel.INFO)
            addLog("Type 'help' for command list or select a provider below to test.", LogLevel.INFO)
            val apis = APIHolder.apis
            addLog("Active CS3 Providers loaded: ${apis.size}", LogLevel.SUCCESS)
        }
    }

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    fun executeTerminalCommand(cmdText: String) {
        val trimmed = cmdText.trim()
        if (trimmed.isBlank() || isExecuting) return

        addLog("$ $trimmed", LogLevel.COMMAND)
        commandInput = ""
        isExecuting = true

        scope.launch {
            val startTime = System.currentTimeMillis()
            val tokens = trimmed.split(Regex("\\s+"))
            val mainCmd = tokens[0].lowercase(Locale.ROOT)

            try {
                when (mainCmd) {
                    "help", "?" -> {
                        addLog("--- AVAILABLE TERMINAL COMMANDS ---", LogLevel.INFO)
                        addLog("  ls / providers                 - List all loaded .cs3 plugins & APIs", LogLevel.INFO)
                        addLog("  search <provider> <query>      - Query provider search endpoint", LogLevel.INFO)
                        addLog("  searchall <query>              - Search across all loaded .cs3 providers", LogLevel.INFO)
                        addLog("  load <provider> <url>          - Query media details & episode structure", LogLevel.INFO)
                        addLog("  extract <provider> <dataUrl>   - Extract live streams from provider / video host", LogLevel.INFO)
                        addLog("  mainpage <provider>            - Fetch home sections for provider", LogLevel.INFO)
                        addLog("  ping <url>                     - Send HTTP GET via CloudStream OkHttp/CloudflareKiller", LogLevel.INFO)
                        addLog("  doh <google|cloudflare>        - Test DNS over HTTPS latency", LogLevel.INFO)
                        addLog("  clear                          - Clear terminal output", LogLevel.INFO)
                        addLog("  copy                           - Copy entire console buffer to clipboard", LogLevel.INFO)
                    }

                    "clear", "cls" -> {
                        logEntries.clear()
                        addLog("Terminal cleared.", LogLevel.INFO)
                    }

                    "copy" -> {
                        val fullText = logEntries.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
                        clipboardManager.setText(AnnotatedString(fullText))
                        Toast.makeText(context, "Terminal output copied to clipboard", Toast.LENGTH_SHORT).show()
                        addLog("Console buffer copied to clipboard (${logEntries.size} entries)", LogLevel.SUCCESS)
                    }

                    "ls", "providers" -> {
                        val apis = APIHolder.apis.toList()
                        addLog("--- LOADED .CS3 PROVIDERS (${apis.size}) ---", LogLevel.INFO)
                        if (apis.isEmpty()) {
                            addLog("No active CS3 providers found in APIHolder.", LogLevel.WARN)
                        } else {
                            apis.forEachIndexed { idx, api ->
                                val types = api.supportedTypes.joinToString(",") { it.name }
                                val source = api.sourcePlugin ?: "Internal"
                                addLog("[$idx] ${api.name} | lang=${api.lang} | types=[$types] | source=$source | mainUrl=${api.mainUrl}", LogLevel.SUCCESS)
                            }
                        }
                    }

                    "search" -> {
                        if (tokens.size < 3) {
                            addLog("Usage: search <provider_name> <query>", LogLevel.WARN)
                        } else {
                            val provName = tokens[1]
                            val query = tokens.drop(2).joinToString(" ")
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            if (provider == null) {
                                addLog("Error: Provider '$provName' not found. Type 'ls' to see active providers.", LogLevel.ERROR)
                            } else {
                                addLog("Executing search on '${provider.name}' for '$query'...", LogLevel.INFO)
                                val repo = APIRepository(provider)
                                val result = withContext(Dispatchers.IO) { repo.search(query) }
                                when (result) {
                                    is Resource.Success -> {
                                        val items = result.value.list
                                        addLog("Search SUCCESS: ${items.size} results in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS)
                                        items.forEachIndexed { i, itm ->
                                            addLog("  #$i: [${itm.type?.name ?: "Unknown"}] ${itm.name} -> ${itm.url}", LogLevel.JSON)
                                        }
                                    }
                                    is Resource.Failure -> {
                                        addLog("Search FAILED: ${result.errorString} in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR)
                                    }
                                    is Resource.Loading -> {}
                                }
                            }
                        }
                    }

                    "searchall" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: searchall <query>", LogLevel.WARN)
                        } else {
                            val query = tokens.drop(1).joinToString(" ")
                            val apis = APIHolder.apis.toList()
                            addLog("Searching across ${apis.size} providers for '$query'...", LogLevel.INFO)
                            var totalCount = 0
                            apis.forEach { provider ->
                                val pStart = System.currentTimeMillis()
                                try {
                                    val repo = APIRepository(provider)
                                    val res = withContext(Dispatchers.IO) { repo.search(query) }
                                    if (res is Resource.Success && res.value.list.isNotEmpty()) {
                                        val list = res.value.list
                                        totalCount += list.size
                                        addLog("✓ [${provider.name}] (${list.size} results in ${System.currentTimeMillis() - pStart}ms)", LogLevel.SUCCESS)
                                        list.take(3).forEach { itm ->
                                            addLog("   - ${itm.name} (${itm.type?.name ?: "Media"})", LogLevel.JSON)
                                        }
                                    } else if (res is Resource.Failure) {
                                        addLog("✗ [${provider.name}] Error: ${res.errorString}", LogLevel.WARN)
                                    }
                                } catch (e: Throwable) {
                                    addLog("✗ [${provider.name}] Exception: ${e.message}", LogLevel.ERROR)
                                }
                            }
                            addLog("Total aggregated search results: $totalCount", LogLevel.INFO)
                        }
                    }

                    "load" -> {
                        if (tokens.size < 3) {
                            addLog("Usage: load <provider_name> <url>", LogLevel.WARN)
                        } else {
                            val provName = tokens[1]
                            val url = tokens.drop(2).joinToString(" ")
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            if (provider == null) {
                                addLog("Error: Provider '$provName' not found.", LogLevel.ERROR)
                            } else {
                                addLog("Loading media details from '${provider.name}' for '$url'...", LogLevel.INFO)
                                val repo = APIRepository(provider)
                                val result = withContext(Dispatchers.IO) { repo.load(url) }
                                when (result) {
                                    is Resource.Success -> {
                                        val data = result.value
                                        addLog("Load SUCCESS: ${data.name} (${data.type.name}) in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS)
                                        addLog("  Year: ${data.year} | Rating: ${data.rating} | Plot: ${data.plot?.take(80)}...", LogLevel.JSON)
                                        addLog("  Poster: ${data.posterUrl}", LogLevel.JSON)
                                        if (data is TvSeriesLoadResponse) {
                                            addLog("  Episodes (${data.episodes.size}):", LogLevel.INFO)
                                            data.episodes.take(5).forEach { ep ->
                                                addLog("    S${ep.season ?: 1}E${ep.episode ?: 1} - ${ep.name} (data=${ep.data})", LogLevel.JSON)
                                            }
                                        } else if (data is MovieLoadResponse) {
                                            addLog("  Movie Stream dataUrl: ${data.dataUrl}", LogLevel.INFO)
                                        }
                                    }
                                    is Resource.Failure -> {
                                        addLog("Load FAILED: ${result.errorString} in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR)
                                    }
                                    is Resource.Loading -> {}
                                }
                            }
                        }
                    }

                    "extract" -> {
                        if (tokens.size < 3) {
                            addLog("Usage: extract <provider_name> <dataUrl>", LogLevel.WARN)
                        } else {
                            val provName = tokens[1]
                            val dataUrl = tokens.drop(2).joinToString(" ")
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            addLog("Extracting streams from '$provName' for '$dataUrl'...", LogLevel.INFO)
                            val extractedLinks = mutableListOf<ExtractorLink>()
                            val extractedSubs = mutableListOf<SubtitleFile>()

                            withContext(Dispatchers.IO) {
                                if (provider != null) {
                                    val repo = APIRepository(provider)
                                    repo.loadLinks(
                                        data = dataUrl,
                                        isCasting = false,
                                        subtitleCallback = { sub -> synchronized(extractedSubs) { extractedSubs.add(sub) } },
                                        callback = { link -> synchronized(extractedLinks) { extractedLinks.add(link) } }
                                    )
                                }

                                if (extractedLinks.isEmpty() && (dataUrl.startsWith("http://") || dataUrl.startsWith("https://"))) {
                                    try {
                                        loadExtractor(
                                            url = dataUrl,
                                            referer = null,
                                            subtitleCallback = { sub -> synchronized(extractedSubs) { extractedSubs.add(sub) } },
                                            callback = { link -> synchronized(extractedLinks) { extractedLinks.add(link) } }
                                        )
                                    } catch (e: Throwable) {
                                        addLog("Fallback extractor failed: ${e.message}", LogLevel.WARN)
                                    }
                                }
                            }

                            if (extractedLinks.isNotEmpty()) {
                                addLog("Extractor SUCCESS: ${extractedLinks.size} links found in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS)
                                extractedLinks.forEachIndexed { i, link ->
                                    addLog("  Link #$i: [${link.name}] ${link.quality}p -> ${link.url}", LogLevel.STREAM, linkUrl = link.url)
                                }
                                if (extractedSubs.isNotEmpty()) {
                                    addLog("  Subtitles (${extractedSubs.size}):", LogLevel.INFO)
                                    extractedSubs.forEach { s ->
                                        addLog("    - [${s.lang}] ${s.url}", LogLevel.JSON)
                                    }
                                }
                            } else {
                                addLog("Extractor FAILED: 0 playable links returned in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR)
                            }
                        }
                    }

                    "ping" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: ping <url>", LogLevel.WARN)
                        } else {
                            val url = tokens[1]
                            addLog("Pinging HTTP '$url'...", LogLevel.INFO)
                            val client = okHttpClient ?: OkHttpClient()
                            withContext(Dispatchers.IO) {
                                try {
                                    val req = Request.Builder().url(url).header("User-Agent", com.lagradost.cloudstream3.USER_AGENT).build()
                                    val resp = client.newCall(req).execute()
                                    val elapsed = System.currentTimeMillis() - startTime
                                    val code = resp.code
                                    val length = resp.body?.contentLength() ?: 0L
                                    addLog("HTTP Response: $code ${resp.message} (${length} bytes in ${elapsed}ms)", if (code in 200..299) LogLevel.SUCCESS else LogLevel.WARN)
                                    resp.headers.names().take(6).forEach { hName ->
                                        addLog("  $hName: ${resp.header(hName)}", LogLevel.JSON)
                                    }
                                    resp.close()
                                } catch (e: Throwable) {
                                    addLog("Ping FAILED: ${e.message} in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR)
                                }
                            }
                        }
                    }

                    "mainpage" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: mainpage <provider_name>", LogLevel.WARN)
                        } else {
                            val provName = tokens[1]
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                            if (provider == null) {
                                addLog("Error: Provider '$provName' not found.", LogLevel.ERROR)
                            } else {
                                addLog("Fetching home sections for '${provider.name}'...", LogLevel.INFO)
                                val repo = APIRepository(provider)
                                val result = withContext(Dispatchers.IO) { repo.getMainPage(1) }
                                when (result) {
                                    is Resource.Success -> {
                                        val sections = result.value.filterNotNull()
                                        addLog("MainPage SUCCESS: ${sections.size} categories in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS)
                                        sections.forEach { sec ->
                                            sec.items.forEach { row ->
                                                addLog("  Section: '${row.name}' -> ${row.list.size} items", LogLevel.JSON)
                                            }
                                        }
                                    }
                                    is Resource.Failure -> {
                                        addLog("MainPage FAILED: ${result.errorString}", LogLevel.ERROR)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }

                    else -> {
                        addLog("Unknown command: '$mainCmd'. Type 'help' for available commands.", LogLevel.WARN)
                    }
                }
            } catch (t: Throwable) {
                addLog("Execution Exception: ${t.javaClass.simpleName}: ${t.message}", LogLevel.ERROR)
            } finally {
                isExecuting = false
            }
        }
    }

    val apisList = APIHolder.apis.toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaxStreamTheme.AbyssBackground)
            .padding(12.dp)
    ) {
        // Top Toolbar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Terminal",
                    tint = MaxStreamTheme.CrimsonAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ".cs3 Interactive Console",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaxStreamTheme.TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { executeTerminalCommand("providers") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Providers",
                        tint = MaxStreamTheme.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { executeTerminalCommand("copy") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Output",
                        tint = MaxStreamTheme.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { executeTerminalCommand("clear") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear",
                        tint = MaxStreamTheme.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Command / Provider Selector Ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider Picker Chip
            Box {
                AssistChip(
                    onClick = { providerDropdownExpanded = true },
                    label = {
                        Text(
                            text = selectedProviderName ?: "Select CS3 Provider",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaxStreamTheme.CrimsonAccent
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Extension",
                            tint = MaxStreamTheme.CrimsonAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown",
                            tint = MaxStreamTheme.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaxStreamTheme.MidnightSurface
                    )
                )

                DropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false },
                    modifier = Modifier.background(MaxStreamTheme.MidnightSurface)
                ) {
                    if (apisList.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No CS3 providers loaded", color = MaxStreamTheme.TextSecondary) },
                            onClick = { providerDropdownExpanded = false }
                        )
                    } else {
                        apisList.forEach { api ->
                            DropdownMenuItem(
                                text = { Text(api.name, color = MaxStreamTheme.TextPrimary) },
                                onClick = {
                                    selectedProviderName = api.name
                                    providerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Fast Preset Chips
            listOf("help", "providers", "searchall Marvel", "ping https://google.com").forEach { cmd ->
                AssistChip(
                    onClick = { executeTerminalCommand(cmd) },
                    label = { Text(cmd, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaxStreamTheme.MidnightSurface,
                        labelColor = MaxStreamTheme.TextSecondary
                    )
                )
            }

            if (selectedProviderName != null) {
                AssistChip(
                    onClick = { executeTerminalCommand("search $selectedProviderName Batman") },
                    label = { Text("Test '$selectedProviderName'", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f),
                        labelColor = MaxStreamTheme.CrimsonAccent
                    )
                )
            }
        }

        // Terminal Console Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0C10))
                .border(1.dp, MaxStreamTheme.GlassBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logEntries) { entry ->
                        val textColor = when (entry.level) {
                            LogLevel.COMMAND -> Color(0xFF64B5F6)
                            LogLevel.SUCCESS -> Color(0xFF81C784)
                            LogLevel.WARN -> Color(0xFFFFB74D)
                            LogLevel.ERROR -> Color(0xFFE57373)
                            LogLevel.STREAM -> Color(0xFFBA68C8)
                            LogLevel.JSON -> Color(0xFFB0BEC5)
                            LogLevel.INFO -> Color(0xFFECEFF1)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = entry.timestamp,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = textColor,
                                    lineHeight = 16.sp
                                )

                                if (entry.linkUrl != null) {
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                MediaUtils.playFile(
                                                    source = entry.linkUrl,
                                                    context = context,
                                                    launchSource = "cs3_terminal",
                                                    title = "CS3 Stream Test"
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play Stream", fontSize = 10.sp, color = Color.White)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(entry.linkUrl))
                                                Toast.makeText(context, "Stream URL copied", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("Copy URL", fontSize = 10.sp, color = MaxStreamTheme.TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isExecuting) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaxStreamTheme.CrimsonAccent
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Executing .cs3 query...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaxStreamTheme.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command Prompt Input Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = {
                    Text(
                        text = if (selectedProviderName != null) "e.g. search $selectedProviderName Avengers" else "Enter command (or 'help')",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaxStreamTheme.TextSecondary
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { executeTerminalCommand(commandInput) }),
                leadingIcon = {
                    Text(
                        text = "$",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.CrimsonAccent,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                trailingIcon = {
                    if (commandInput.isNotBlank()) {
                        IconButton(onClick = { commandInput = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear input",
                                tint = MaxStreamTheme.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaxStreamTheme.TextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaxStreamTheme.MidnightSurface,
                    unfocusedContainerColor = MaxStreamTheme.MidnightSurface,
                    focusedBorderColor = MaxStreamTheme.CrimsonAccent,
                    unfocusedBorderColor = MaxStreamTheme.GlassBorder
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { executeTerminalCommand(commandInput) },
                enabled = commandInput.isNotBlank() && !isExecuting,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (commandInput.isNotBlank() && !isExecuting) MaxStreamTheme.CrimsonAccent else MaxStreamTheme.MidnightSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Execute",
                    tint = if (commandInput.isNotBlank() && !isExecuting) Color.White else MaxStreamTheme.TextSecondary
                )
            }
        }
    }
}
