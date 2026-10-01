package xyz.mpv.rex.cinehub.diagnostic

import android.content.Context
import android.util.Log
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
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Modifier as JavaModifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val tag: String) {
    COMMAND("CMD"),
    INFO("INFO"),
    SUCCESS("OK"),
    WARN("WARN"),
    ERROR("FAIL"),
    STREAM("STREAM"),
    JSON("DATA"),
    LOGCAT_V("V"),
    LOGCAT_D("D"),
    LOGCAT_I("I"),
    LOGCAT_W("W"),
    LOGCAT_E("E")
}

data class TerminalLogEntry(
    val timestamp: String,
    val logcatTag: String,
    val message: String,
    val level: LogLevel,
    val rawLogcat: String,
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
    var isRawLogcatMode by remember { mutableStateOf(false) }

    val pid = android.os.Process.myPid()
    val tid = android.os.Process.myTid()

    fun addLog(
        msg: String,
        level: LogLevel = LogLevel.INFO,
        tag: String = "CS3_TERMINAL",
        linkUrl: String? = null
    ) {
        val now = Date()
        val timeDisplay = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(now)
        val logcatDate = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(now)
        val levelChar = when (level) {
            LogLevel.ERROR, LogLevel.LOGCAT_E -> "E"
            LogLevel.WARN, LogLevel.LOGCAT_W -> "W"
            LogLevel.SUCCESS, LogLevel.INFO, LogLevel.LOGCAT_I -> "I"
            LogLevel.LOGCAT_D, LogLevel.JSON, LogLevel.COMMAND -> "D"
            else -> "V"
        }
        val rawLogcat = "$logcatDate $pid $tid $levelChar $tag: $msg"
        
        when (level) {
            LogLevel.ERROR, LogLevel.LOGCAT_E -> Log.e(tag, msg)
            LogLevel.WARN, LogLevel.LOGCAT_W -> Log.w(tag, msg)
            LogLevel.SUCCESS, LogLevel.INFO, LogLevel.LOGCAT_I -> Log.i(tag, msg)
            else -> Log.d(tag, msg)
        }

        logEntries.add(
            TerminalLogEntry(
                timestamp = timeDisplay,
                logcatTag = tag,
                message = msg,
                level = level,
                rawLogcat = rawLogcat,
                linkUrl = linkUrl
            )
        )
    }

    LaunchedEffect(Unit) {
        if (logEntries.isEmpty()) {
            addLog("MAX STREAM CloudStream .cs3 Interactive Terminal & Inspector v3.0", LogLevel.INFO, "CS3_BOOT")
            addLog("Type 'help' for commands, 'inspect <provider>' for class structure, or 'debug <provider>' for step-by-step health check.", LogLevel.INFO, "CS3_BOOT")
            val apis = APIHolder.apis
            addLog("Active CS3 Providers loaded in runtime: ${apis.size}", LogLevel.SUCCESS, "CS3_BOOT")
        }
    }

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    // --- Command Processor ---
    fun executeTerminalCommand(cmdText: String) {
        val trimmed = cmdText.trim()
        if (trimmed.isBlank() || isExecuting) return

        addLog("$ $trimmed", LogLevel.COMMAND, "CONSOLE_INPUT")
        commandInput = ""
        isExecuting = true

        scope.launch {
            val startTime = System.currentTimeMillis()
            val tokens = trimmed.split(Regex("\\s+"))
            val mainCmd = tokens[0].lowercase(Locale.ROOT)

            try {
                when (mainCmd) {
                    "help", "?" -> {
                        addLog("===================== CS3 TERMINAL COMMANDS =====================", LogLevel.INFO, "HELP")
                        addLog("  ls / providers                   - List all loaded .cs3 plugins & APIs", LogLevel.INFO, "HELP")
                        addLog("  inspect <provider>               - View Class, Superclasses, Fields, Methods, & Dex structure", LogLevel.INFO, "HELP")
                        addLog("  debug <provider> [query]         - Run 6-step deep health test & isolate exact failure", LogLevel.INFO, "HELP")
                        addLog("  search <provider> <query>        - Execute provider search query", LogLevel.INFO, "HELP")
                        addLog("  searchall <query>                - Execute parallel search across all providers", LogLevel.INFO, "HELP")
                        addLog("  load <provider> <url>            - Query detailed metadata & episode listing", LogLevel.INFO, "HELP")
                        addLog("  extract <provider> <dataUrl>     - Extract live playable stream links & subtitles", LogLevel.INFO, "HELP")
                        addLog("  mainpage <provider>              - Fetch home sections & rows", LogLevel.INFO, "HELP")
                        addLog("  ping <url>                       - Test HTTP GET with Cloudflare challenge handler", LogLevel.INFO, "HELP")
                        addLog("  logcat on/off                    - Toggle raw Android Logcat stream display", LogLevel.INFO, "HELP")
                        addLog("  clear                            - Clear console buffer", LogLevel.INFO, "HELP")
                        addLog("  copy                             - Copy full output / logcat to clipboard", LogLevel.INFO, "HELP")
                        addLog("=================================================================", LogLevel.INFO, "HELP")
                    }

                    "logcat" -> {
                        if (tokens.size > 1 && tokens[1].equals("off", ignoreCase = true)) {
                            isRawLogcatMode = false
                            addLog("Raw Logcat Mode: DISABLED (Standard Terminal View)", LogLevel.INFO, "LOGCAT_MODE")
                        } else {
                            isRawLogcatMode = true
                            addLog("Raw Logcat Mode: ENABLED (Displaying Android Logcat Stream)", LogLevel.SUCCESS, "LOGCAT_MODE")
                        }
                    }

                    "clear", "cls" -> {
                        logEntries.clear()
                        addLog("Terminal buffer cleared.", LogLevel.INFO, "CS3_RUNTIME")
                    }

                    "copy" -> {
                        val fullText = if (isRawLogcatMode) {
                            logEntries.joinToString("\n") { it.rawLogcat }
                        } else {
                            logEntries.joinToString("\n") { "[${it.timestamp}] [${it.logcatTag}] ${it.message}" }
                        }
                        clipboardManager.setText(AnnotatedString(fullText))
                        Toast.makeText(context, "Terminal output copied to clipboard", Toast.LENGTH_SHORT).show()
                        addLog("Copied ${logEntries.size} entries to clipboard.", LogLevel.SUCCESS, "CLIPBOARD")
                    }

                    "ls", "providers" -> {
                        val apis = APIHolder.apis.toList()
                        addLog("--- LOADED .CS3 PROVIDERS IN RUNTIME (${apis.size}) ---", LogLevel.INFO, "PROVIDER_LIST")
                        if (apis.isEmpty()) {
                            addLog("No active CS3 providers found in APIHolder.", LogLevel.WARN, "PROVIDER_LIST")
                        } else {
                            apis.forEachIndexed { idx, api ->
                                val types = api.supportedTypes.joinToString(",") { it.name }
                                val source = api.sourcePlugin ?: "Internal"
                                val vpn = api.vpnStatus.name
                                addLog("[$idx] ${api.name} | lang=${api.lang} | types=[$types] | vpn=$vpn | source=$source | url=${api.mainUrl}", LogLevel.SUCCESS, "PROVIDER_LIST")
                            }
                        }
                    }

                    // --- CLASS & STRUCTURE INSPECTOR ---
                    "inspect", "structure", "class" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: inspect <provider_name>", LogLevel.WARN, "INSPECTOR")
                        } else {
                            val provName = tokens[1]
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.apis.firstOrNull { it.name.contains(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            if (provider == null) {
                                addLog("Error: Provider '$provName' not found. Type 'ls' to list loaded providers.", LogLevel.ERROR, "INSPECTOR")
                            } else {
                                val clazz = provider.javaClass
                                addLog("================ CLASS & STRUCTURE INSPECTION ================", LogLevel.INFO, "INSPECTOR")
                                addLog("Class Name:   ${clazz.name}", LogLevel.SUCCESS, "INSPECTOR")
                                addLog("Simple Name:  ${clazz.simpleName}", LogLevel.SUCCESS, "INSPECTOR")
                                addLog("Package:      ${clazz.`package`?.name ?: "default"}", LogLevel.JSON, "INSPECTOR")
                                addLog("Superclass:   ${clazz.superclass?.name ?: "None"}", LogLevel.JSON, "INSPECTOR")
                                
                                val interfaces = clazz.interfaces.map { it.simpleName }
                                addLog("Interfaces:   ${if (interfaces.isEmpty()) "None" else interfaces.joinToString(", ")}", LogLevel.JSON, "INSPECTOR")
                                
                                val cl = clazz.classLoader
                                addLog("ClassLoader:  ${cl?.javaClass?.name ?: "System"}", LogLevel.JSON, "INSPECTOR")

                                addLog("--- CORE PROVIDER PROPERTIES ---", LogLevel.INFO, "INSPECTOR")
                                addLog("  name:             \"${provider.name}\"", LogLevel.JSON, "INSPECTOR")
                                addLog("  mainUrl:          \"${provider.mainUrl}\"", LogLevel.JSON, "INSPECTOR")
                                addLog("  lang:             \"${provider.lang}\"", LogLevel.JSON, "INSPECTOR")
                                addLog("  providerType:     ${provider.providerType.name}", LogLevel.JSON, "INSPECTOR")
                                addLog("  vpnStatus:        ${provider.vpnStatus.name}", LogLevel.JSON, "INSPECTOR")
                                addLog("  supportedTypes:   ${provider.supportedTypes.map { it.name }}", LogLevel.JSON, "INSPECTOR")
                                addLog("  hasMainPage:      ${provider.hasMainPage} (${provider.mainPage.size} defined sections)", LogLevel.JSON, "INSPECTOR")
                                addLog("  hasQuickSearch:   ${provider.hasQuickSearch}", LogLevel.JSON, "INSPECTOR")
                                addLog("  sourcePlugin:     ${provider.sourcePlugin ?: "Compiled In-App"}", LogLevel.JSON, "INSPECTOR")

                                addLog("--- DECLARED METHODS (${clazz.declaredMethods.size}) ---", LogLevel.INFO, "INSPECTOR")
                                clazz.declaredMethods.take(15).forEach { method ->
                                    val mods = JavaModifier.toString(method.modifiers)
                                    val params = method.parameterTypes.joinToString(", ") { it.simpleName }
                                    addLog("  $mods fun ${method.name}($params): ${method.returnType.simpleName}", LogLevel.JSON, "INSPECTOR")
                                }
                                if (clazz.declaredMethods.size > 15) {
                                    addLog("  ... and ${clazz.declaredMethods.size - 15} more methods", LogLevel.LOGCAT_D, "INSPECTOR")
                                }

                                addLog("--- DECLARED FIELDS (${clazz.declaredFields.size}) ---", LogLevel.INFO, "INSPECTOR")
                                clazz.declaredFields.take(10).forEach { field ->
                                    field.isAccessible = true
                                    val value = runCatching { field.get(provider)?.toString()?.take(40) }.getOrNull() ?: "null"
                                    addLog("  val ${field.name}: ${field.type.simpleName} = $value", LogLevel.JSON, "INSPECTOR")
                                }
                                addLog("=============================================================", LogLevel.INFO, "INSPECTOR")
                            }
                        }
                    }

                    // --- DEBUG PROVIDER: DEEP STEP-BY-STEP HEALTH & FAILURE DIAGNOSTICS ---
                    "debug", "debug_provider", "diagnose" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: debug <provider_name> [optional_search_query]", LogLevel.WARN, "DEBUG_PIPELINE")
                        } else {
                            val provName = tokens[1]
                            val testQuery = if (tokens.size > 2) tokens.drop(2).joinToString(" ") else "Marvel"
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.apis.firstOrNull { it.name.contains(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            if (provider == null) {
                                addLog("FATAL: Provider '$provName' not found.", LogLevel.ERROR, "DEBUG_PIPELINE")
                            } else {
                                addLog(">>>>>>>>>> STARTING DEEP DIAGNOSTIC PIPELINE: ${provider.name} <<<<<<<<<<", LogLevel.INFO, "DEBUG_PIPELINE")
                                val repo = APIRepository(provider)
                                var pipelineFailed = false
                                var failedStep = ""
                                var stepErrorDetails = ""

                                // STEP 1: ClassLoader & Reflection Integrity
                                val s1Start = System.currentTimeMillis()
                                addLog("[STEP 1/6] Manifest & Reflection Verification...", LogLevel.INFO, "DEBUG_STEP1")
                                try {
                                    val clazz = provider.javaClass
                                    if (provider.name.isBlank() || provider.mainUrl.isBlank()) {
                                        throw IllegalStateException("Provider has blank name or mainUrl")
                                    }
                                    addLog("  ✓ Class '${clazz.simpleName}' OK | MainUrl: ${provider.mainUrl} (${System.currentTimeMillis() - s1Start}ms)", LogLevel.SUCCESS, "DEBUG_STEP1")
                                } catch (e: Throwable) {
                                    pipelineFailed = true
                                    failedStep = "STEP 1 (Manifest & Reflection)"
                                    stepErrorDetails = e.message ?: "Unknown reflection error"
                                    addLog("  ✗ STEP 1 FAILED: $stepErrorDetails", LogLevel.ERROR, "DEBUG_STEP1")
                                }

                                // STEP 2: Host Connectivity & Cloudflare Health Check
                                if (!pipelineFailed) {
                                    val s2Start = System.currentTimeMillis()
                                    addLog("[STEP 2/6] Network & Cloudflare Host Check: ${provider.mainUrl}...", LogLevel.INFO, "DEBUG_STEP2")
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val client = okHttpClient ?: OkHttpClient()
                                            val req = Request.Builder()
                                                .url(provider.mainUrl)
                                                .header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
                                                .build()
                                            val resp = client.newCall(req).execute()
                                            val code = resp.code
                                            val bodyLen = resp.body?.contentLength() ?: 0L
                                            val isCloudflare = resp.header("Server")?.contains("cloudflare", ignoreCase = true) == true ||
                                                    resp.header("cf-ray") != null
                                            resp.close()

                                            if (code in 200..399) {
                                                addLog("  ✓ HTTP $code OK | Size: ${bodyLen}B | Cloudflare: $isCloudflare (${System.currentTimeMillis() - s2Start}ms)", LogLevel.SUCCESS, "DEBUG_STEP2")
                                            } else if (code == 403 || code == 503) {
                                                pipelineFailed = true
                                                failedStep = "STEP 2 (Host Anti-Bot Challenge)"
                                                stepErrorDetails = "Server returned HTTP $code (Anti-Bot challenge / Blocked). CloudflareKiller will attempt WebView bypass."
                                                addLog("  ✗ STEP 2 FAILED: HTTP $code Anti-Bot Block", LogLevel.ERROR, "DEBUG_STEP2")
                                            } else {
                                                addLog("  ! Host returned HTTP $code (Non-standard, proceeding...)", LogLevel.WARN, "DEBUG_STEP2")
                                            }
                                        } catch (e: Throwable) {
                                            pipelineFailed = true
                                            failedStep = "STEP 2 (Network Connectivity)"
                                            stepErrorDetails = "${e.javaClass.simpleName}: ${e.message}"
                                            addLog("  ✗ STEP 2 FAILED: $stepErrorDetails", LogLevel.ERROR, "DEBUG_STEP2")
                                        }
                                    }
                                }

                                // STEP 3: Main Page Rows / Home Scraper Test
                                if (!pipelineFailed && provider.hasMainPage) {
                                    val s3Start = System.currentTimeMillis()
                                    addLog("[STEP 3/6] Fetching MainPage Categories (${provider.mainPage.size} sections)...", LogLevel.INFO, "DEBUG_STEP3")
                                    withContext(Dispatchers.IO) {
                                        try {
                                            when (val res = repo.getMainPage(1)) {
                                                is Resource.Success -> {
                                                    val rows = res.value.filterNotNull()
                                                    val totalItems = rows.sumOf { it.items.sumOf { item -> item.list.size } }
                                                    if (totalItems > 0) {
                                                        addLog("  ✓ MainPage OK: ${rows.size} categories with $totalItems total items (${System.currentTimeMillis() - s3Start}ms)", LogLevel.SUCCESS, "DEBUG_STEP3")
                                                    } else {
                                                        addLog("  ! MainPage returned 0 items (DOM selectors might need review)", LogLevel.WARN, "DEBUG_STEP3")
                                                    }
                                                }
                                                is Resource.Failure -> {
                                                    addLog("  ! MainPage failed: ${res.errorString}", LogLevel.WARN, "DEBUG_STEP3")
                                                }
                                                else -> {}
                                            }
                                        } catch (e: Throwable) {
                                            addLog("  ! MainPage Exception: ${e.message}", LogLevel.WARN, "DEBUG_STEP3")
                                        }
                                    }
                                }

                                // STEP 4: Search Scraper Query Execution
                                var firstResultItem: SearchResponse? = null
                                if (!pipelineFailed) {
                                    val s4Start = System.currentTimeMillis()
                                    addLog("[STEP 4/6] Executing Search Scraper for '$testQuery'...", LogLevel.INFO, "DEBUG_STEP4")
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val res = repo.search(testQuery)
                                            when (res) {
                                                is Resource.Success -> {
                                                    val items = res.value.list
                                                    if (items.isNotEmpty()) {
                                                        firstResultItem = items.first()
                                                        addLog("  ✓ Search OK: ${items.size} results in ${System.currentTimeMillis() - s4Start}ms", LogLevel.SUCCESS, "DEBUG_STEP4")
                                                        addLog("    Top Item: '${firstResultItem?.name}' -> ${firstResultItem?.url}", LogLevel.JSON, "DEBUG_STEP4")
                                                    } else {
                                                        pipelineFailed = true
                                                        failedStep = "STEP 4 (Search Scraper)"
                                                        stepErrorDetails = "0 search results returned for query '$testQuery'. Search CSS selectors or API endpoints may have changed."
                                                        addLog("  ✗ STEP 4 FAILED: 0 items parsed", LogLevel.ERROR, "DEBUG_STEP4")
                                                    }
                                                }
                                                is Resource.Failure -> {
                                                    pipelineFailed = true
                                                    failedStep = "STEP 4 (Search Scraper)"
                                                    stepErrorDetails = res.errorString
                                                    addLog("  ✗ STEP 4 FAILED: ${res.errorString}", LogLevel.ERROR, "DEBUG_STEP4")
                                                }
                                                else -> {}
                                            }
                                        } catch (e: Throwable) {
                                            pipelineFailed = true
                                            failedStep = "STEP 4 (Search Scraper)"
                                            stepErrorDetails = "${e.javaClass.simpleName}: ${e.message}"
                                            addLog("  ✗ STEP 4 FAILED: $stepErrorDetails", LogLevel.ERROR, "DEBUG_STEP4")
                                        }
                                    }
                                }

                                // STEP 5: Load Details & Episode Listing
                                var targetEpisodeData: String? = null
                                if (!pipelineFailed && firstResultItem != null) {
                                    val targetUrl = firstResultItem!!.url
                                    val s5Start = System.currentTimeMillis()
                                    addLog("[STEP 5/6] Loading Media Details for '${firstResultItem!!.name}' ($targetUrl)...", LogLevel.INFO, "DEBUG_STEP5")
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val res = repo.load(targetUrl)
                                            when (res) {
                                                is Resource.Success -> {
                                                    val loadRes = res.value
                                                    addLog("  ✓ Load Details OK in ${System.currentTimeMillis() - s5Start}ms", LogLevel.SUCCESS, "DEBUG_STEP5")
                                                    addLog("    Title: ${loadRes.name} | Year: ${loadRes.year} | Rating: ${loadRes.rating}", LogLevel.JSON, "DEBUG_STEP5")
                                                    if (loadRes is TvSeriesLoadResponse) {
                                                        addLog("    Episodes Parsed: ${loadRes.episodes.size}", LogLevel.JSON, "DEBUG_STEP5")
                                                        targetEpisodeData = loadRes.episodes.firstOrNull()?.data
                                                    } else if (loadRes is MovieLoadResponse) {
                                                        addLog("    Movie dataUrl: ${loadRes.dataUrl}", LogLevel.JSON, "DEBUG_STEP5")
                                                        targetEpisodeData = loadRes.dataUrl
                                                    } else {
                                                        targetEpisodeData = loadRes.url
                                                    }
                                                }
                                                is Resource.Failure -> {
                                                    pipelineFailed = true
                                                    failedStep = "STEP 5 (Load Details Scraper)"
                                                    stepErrorDetails = res.errorString
                                                    addLog("  ✗ STEP 5 FAILED: ${res.errorString}", LogLevel.ERROR, "DEBUG_STEP5")
                                                }
                                                else -> {}
                                            }
                                        } catch (e: Throwable) {
                                            pipelineFailed = true
                                            failedStep = "STEP 5 (Load Details Scraper)"
                                            stepErrorDetails = "${e.javaClass.simpleName}: ${e.message}"
                                            addLog("  ✗ STEP 5 FAILED: $stepErrorDetails", LogLevel.ERROR, "DEBUG_STEP5")
                                        }
                                    }
                                }

                                // STEP 6: Link Extraction & Decryption
                                if (!pipelineFailed && !targetEpisodeData.isNullOrBlank()) {
                                    val s6Start = System.currentTimeMillis()
                                    addLog("[STEP 6/6] Extracting Streams for dataUrl: '$targetEpisodeData'...", LogLevel.INFO, "DEBUG_STEP6")
                                    val extractedLinks = mutableListOf<ExtractorLink>()
                                    val extractedSubs = mutableListOf<SubtitleFile>()

                                    withContext(Dispatchers.IO) {
                                        try {
                                            repo.loadLinks(
                                                data = targetEpisodeData!!,
                                                isCasting = false,
                                                subtitleCallback = { s -> synchronized(extractedSubs) { extractedSubs.add(s) } },
                                                callback = { l -> synchronized(extractedLinks) { extractedLinks.add(l) } }
                                            )

                                            if (extractedLinks.isEmpty() && (targetEpisodeData!!.startsWith("http://") || targetEpisodeData!!.startsWith("https://"))) {
                                                try {
                                                    loadExtractor(
                                                        url = targetEpisodeData!!,
                                                        referer = null,
                                                        subtitleCallback = { s -> synchronized(extractedSubs) { extractedSubs.add(s) } },
                                                        callback = { l -> synchronized(extractedLinks) { extractedLinks.add(l) } }
                                                    )
                                                } catch (e: Throwable) {
                                                    addLog("  ! Fallback extractor: ${e.message}", LogLevel.WARN, "DEBUG_STEP6")
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            addLog("  ! loadLinks error: ${e.message}", LogLevel.WARN, "DEBUG_STEP6")
                                        }
                                    }

                                    if (extractedLinks.isNotEmpty()) {
                                        addLog("  ✓ Link Extraction OK: ${extractedLinks.size} streams in ${System.currentTimeMillis() - s6Start}ms", LogLevel.SUCCESS, "DEBUG_STEP6")
                                        extractedLinks.forEachIndexed { i, link ->
                                            addLog("    Stream #$i: [${link.name}] ${link.quality}p -> ${link.url}", LogLevel.STREAM, "DEBUG_STEP6", linkUrl = link.url)
                                        }
                                    } else {
                                        pipelineFailed = true
                                        failedStep = "STEP 6 (Stream Link Extraction)"
                                        stepErrorDetails = "No playable ExtractorLinks resolved. Video hosters / extractors (e.g. HubCloud/Dood/Streamwish) may have changed keys or encryptions."
                                        addLog("  ✗ STEP 6 FAILED: 0 playable links", LogLevel.ERROR, "DEBUG_STEP6")
                                    }
                                }

                                // FINAL SUMMARY
                                val totalTime = System.currentTimeMillis() - startTime
                                addLog("======================= DIAGNOSTIC REPORT =======================", LogLevel.INFO, "DEBUG_REPORT")
                                if (pipelineFailed) {
                                    addLog("STATUS: ❌ PROVIDER TEST FAILED at $failedStep", LogLevel.ERROR, "DEBUG_REPORT")
                                    addLog("ROOT CAUSE: $stepErrorDetails", LogLevel.ERROR, "DEBUG_REPORT")
                                    addLog("TIME ELAPSED: ${totalTime}ms", LogLevel.WARN, "DEBUG_REPORT")
                                } else {
                                    addLog("STATUS: ✅ ALL 6 STEPS PASSED SUCCESSFULLY!", LogLevel.SUCCESS, "DEBUG_REPORT")
                                    addLog("PROVIDER IS FULLY FUNCTIONAL AND READY FOR PLAYBACK", LogLevel.SUCCESS, "DEBUG_REPORT")
                                    addLog("TOTAL TIME: ${totalTime}ms", LogLevel.SUCCESS, "DEBUG_REPORT")
                                }
                                addLog("=================================================================", LogLevel.INFO, "DEBUG_REPORT")
                            }
                        }
                    }

                    // --- SEARCH COMMAND ---
                    "search" -> {
                        if (tokens.size < 3) {
                            addLog("Usage: search <provider_name> <query>", LogLevel.WARN, "SEARCH")
                        } else {
                            val provName = tokens[1]
                            val query = tokens.drop(2).joinToString(" ")
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.apis.firstOrNull { it.name.contains(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            if (provider == null) {
                                addLog("Error: Provider '$provName' not found. Type 'ls' to see active providers.", LogLevel.ERROR, "SEARCH")
                            } else {
                                addLog("Executing search on '${provider.name}' for '$query'...", LogLevel.INFO, "SEARCH")
                                val repo = APIRepository(provider)
                                val result = withContext(Dispatchers.IO) { repo.search(query) }
                                when (result) {
                                    is Resource.Success -> {
                                        val items = result.value.list
                                        addLog("Search SUCCESS: ${items.size} results in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS, "SEARCH")
                                        items.forEachIndexed { i, itm ->
                                            addLog("  #$i: [${itm.type?.name ?: "Unknown"}] ${itm.name} -> ${itm.url}", LogLevel.JSON, "SEARCH")
                                        }
                                    }
                                    is Resource.Failure -> {
                                        addLog("Search FAILED: ${result.errorString} in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR, "SEARCH")
                                    }
                                    is Resource.Loading -> {}
                                }
                            }
                        }
                    }

                    // --- SEARCHALL COMMAND ---
                    "searchall" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: searchall <query>", LogLevel.WARN, "SEARCH_ALL")
                        } else {
                            val query = tokens.drop(1).joinToString(" ")
                            val apis = APIHolder.apis.toList()
                            addLog("Searching across ${apis.size} providers for '$query'...", LogLevel.INFO, "SEARCH_ALL")
                            var totalCount = 0
                            apis.forEach { provider ->
                                val pStart = System.currentTimeMillis()
                                try {
                                    val repo = APIRepository(provider)
                                    val res = withContext(Dispatchers.IO) { repo.search(query) }
                                    if (res is Resource.Success && res.value.list.isNotEmpty()) {
                                        val list = res.value.list
                                        totalCount += list.size
                                        addLog("✓ [${provider.name}] (${list.size} results in ${System.currentTimeMillis() - pStart}ms)", LogLevel.SUCCESS, "SEARCH_ALL")
                                        list.take(3).forEach { itm ->
                                            addLog("   - ${itm.name} (${itm.type?.name ?: "Media"})", LogLevel.JSON, "SEARCH_ALL")
                                        }
                                    } else if (res is Resource.Failure) {
                                        addLog("✗ [${provider.name}] Error: ${res.errorString}", LogLevel.WARN, "SEARCH_ALL")
                                    }
                                } catch (e: Throwable) {
                                    addLog("✗ [${provider.name}] Exception: ${e.message}", LogLevel.ERROR, "SEARCH_ALL")
                                }
                            }
                            addLog("Total aggregated search results: $totalCount", LogLevel.INFO, "SEARCH_ALL")
                        }
                    }

                    // --- LOAD DETAILS ---
                    "load" -> {
                        if (tokens.size < 3) {
                            addLog("Usage: load <provider_name> <url>", LogLevel.WARN, "LOAD")
                        } else {
                            val provName = tokens[1]
                            val url = tokens.drop(2).joinToString(" ")
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.apis.firstOrNull { it.name.contains(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            if (provider == null) {
                                addLog("Error: Provider '$provName' not found.", LogLevel.ERROR, "LOAD")
                            } else {
                                addLog("Loading media details from '${provider.name}' for '$url'...", LogLevel.INFO, "LOAD")
                                val repo = APIRepository(provider)
                                val result = withContext(Dispatchers.IO) { repo.load(url) }
                                when (result) {
                                    is Resource.Success -> {
                                        val data = result.value
                                        addLog("Load SUCCESS: ${data.name} (${data.type.name}) in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS, "LOAD")
                                        addLog("  Year: ${data.year} | Rating: ${data.rating} | Plot: ${data.plot?.take(80)}...", LogLevel.JSON, "LOAD")
                                        addLog("  Poster: ${data.posterUrl}", LogLevel.JSON, "LOAD")
                                        if (data is TvSeriesLoadResponse) {
                                            addLog("  Episodes (${data.episodes.size}):", LogLevel.INFO, "LOAD")
                                            data.episodes.take(5).forEach { ep ->
                                                addLog("    S${ep.season ?: 1}E${ep.episode ?: 1} - ${ep.name} (data=${ep.data})", LogLevel.JSON, "LOAD")
                                            }
                                        } else if (data is MovieLoadResponse) {
                                            addLog("  Movie Stream dataUrl: ${data.dataUrl}", LogLevel.INFO, "LOAD")
                                        }
                                    }
                                    is Resource.Failure -> {
                                        addLog("Load FAILED: ${result.errorString} in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR, "LOAD")
                                    }
                                    is Resource.Loading -> {}
                                }
                            }
                        }
                    }

                    // --- EXTRACT STREAMS ---
                    "extract" -> {
                        if (tokens.size < 3) {
                            addLog("Usage: extract <provider_name> <dataUrl>", LogLevel.WARN, "EXTRACT")
                        } else {
                            val provName = tokens[1]
                            val dataUrl = tokens.drop(2).joinToString(" ")
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.apis.firstOrNull { it.name.contains(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)

                            addLog("Extracting streams from '$provName' for '$dataUrl'...", LogLevel.INFO, "EXTRACT")
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
                                        addLog("Fallback extractor failed: ${e.message}", LogLevel.WARN, "EXTRACT")
                                    }
                                }
                            }

                            if (extractedLinks.isNotEmpty()) {
                                addLog("Extractor SUCCESS: ${extractedLinks.size} links found in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS, "EXTRACT")
                                extractedLinks.forEachIndexed { i, link ->
                                    addLog("  Link #$i: [${link.name}] ${link.quality}p -> ${link.url}", LogLevel.STREAM, "EXTRACT", linkUrl = link.url)
                                }
                                if (extractedSubs.isNotEmpty()) {
                                    addLog("  Subtitles (${extractedSubs.size}):", LogLevel.INFO, "EXTRACT")
                                    extractedSubs.forEach { s ->
                                        addLog("    - [${s.lang}] ${s.url}", LogLevel.JSON, "EXTRACT")
                                    }
                                }
                            } else {
                                addLog("Extractor FAILED: 0 playable links returned in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR, "EXTRACT")
                            }
                        }
                    }

                    // --- PING HTTP ---
                    "ping" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: ping <url>", LogLevel.WARN, "HTTP_PING")
                        } else {
                            val url = tokens[1]
                            addLog("Pinging HTTP '$url'...", LogLevel.INFO, "HTTP_PING")
                            val client = okHttpClient ?: OkHttpClient()
                            withContext(Dispatchers.IO) {
                                try {
                                    val req = Request.Builder()
                                        .url(url)
                                        .header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
                                        .build()
                                    val resp = client.newCall(req).execute()
                                    val elapsed = System.currentTimeMillis() - startTime
                                    val code = resp.code
                                    val length = resp.body?.contentLength() ?: 0L
                                    addLog("HTTP Response: $code ${resp.message} (${length} bytes in ${elapsed}ms)", if (code in 200..299) LogLevel.SUCCESS else LogLevel.WARN, "HTTP_PING")
                                    resp.headers.names().take(6).forEach { hName ->
                                        addLog("  $hName: ${resp.header(hName)}", LogLevel.JSON, "HTTP_PING")
                                    }
                                    resp.close()
                                } catch (e: Throwable) {
                                    addLog("Ping FAILED: ${e.message} in ${System.currentTimeMillis() - startTime}ms", LogLevel.ERROR, "HTTP_PING")
                                }
                            }
                        }
                    }

                    // --- MAINPAGE ---
                    "mainpage" -> {
                        if (tokens.size < 2) {
                            addLog("Usage: mainpage <provider_name>", LogLevel.WARN, "MAINPAGE")
                        } else {
                            val provName = tokens[1]
                            val provider = APIHolder.apis.firstOrNull { it.name.equals(provName, ignoreCase = true) }
                                ?: APIHolder.getApi(provName)
                            if (provider == null) {
                                addLog("Error: Provider '$provName' not found.", LogLevel.ERROR, "MAINPAGE")
                            } else {
                                addLog("Fetching home sections for '${provider.name}'...", LogLevel.INFO, "MAINPAGE")
                                val repo = APIRepository(provider)
                                val result = withContext(Dispatchers.IO) { repo.getMainPage(1) }
                                when (result) {
                                    is Resource.Success -> {
                                        val sections = result.value.filterNotNull()
                                        addLog("MainPage SUCCESS: ${sections.size} categories in ${System.currentTimeMillis() - startTime}ms", LogLevel.SUCCESS, "MAINPAGE")
                                        sections.forEach { sec ->
                                            sec.items.forEach { row ->
                                                addLog("  Section: '${row.name}' -> ${row.list.size} items", LogLevel.JSON, "MAINPAGE")
                                            }
                                        }
                                    }
                                    is Resource.Failure -> {
                                        addLog("MainPage FAILED: ${result.errorString}", LogLevel.ERROR, "MAINPAGE")
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }

                    else -> {
                        addLog("Unknown command: '$mainCmd'. Type 'help' for available commands.", LogLevel.WARN, "CONSOLE")
                    }
                }
            } catch (t: Throwable) {
                val sw = StringWriter()
                t.printStackTrace(PrintWriter(sw))
                addLog("Exception: ${t.javaClass.simpleName}: ${t.message}", LogLevel.ERROR, "EXCEPTION")
                addLog(sw.toString().take(300), LogLevel.LOGCAT_E, "STACKTRACE")
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
                    text = if (isRawLogcatMode) "CS3 Raw Logcat Stream" else ".cs3 Inspector & Terminal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaxStreamTheme.TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Logcat View Mode Switch
                FilterChip(
                    selected = isRawLogcatMode,
                    onClick = { isRawLogcatMode = !isRawLogcatMode },
                    label = { Text(if (isRawLogcatMode) "Logcat" else "Terminal", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isRawLogcatMode) Icons.Default.Subject else Icons.Default.Terminal,
                            contentDescription = "Mode",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                        selectedLabelColor = Color.White,
                        containerColor = MaxStreamTheme.MidnightSurface,
                        labelColor = MaxStreamTheme.TextSecondary
                    ),
                    modifier = Modifier.height(32.dp)
                )

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
            listOf("help", "providers", "searchall Marvel").forEach { cmd ->
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
                    onClick = { executeTerminalCommand("inspect $selectedProviderName") },
                    label = { Text("Inspect '$selectedProviderName'", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaxStreamTheme.ElectricCyan.copy(alpha = 0.2f),
                        labelColor = MaxStreamTheme.ElectricCyan
                    )
                )

                AssistChip(
                    onClick = { executeTerminalCommand("debug $selectedProviderName") },
                    label = { Text("Debug Provider (Step-by-Step)", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.25f),
                        labelColor = MaxStreamTheme.CrimsonAccent
                    )
                )
            }
        }

        // Terminal Console Body / Raw Logcat View
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
                        if (isRawLogcatMode) {
                            val logcatColor = when (entry.level) {
                                LogLevel.ERROR, LogLevel.LOGCAT_E -> Color(0xFFFF6B6B)
                                LogLevel.WARN, LogLevel.LOGCAT_W -> Color(0xFFFFD166)
                                LogLevel.SUCCESS, LogLevel.LOGCAT_I -> Color(0xFF06D6A0)
                                LogLevel.LOGCAT_D, LogLevel.JSON, LogLevel.COMMAND -> Color(0xFF118AB2)
                                else -> Color(0xFFB0BEC5)
                            }
                            Text(
                                text = entry.rawLogcat,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = logcatColor,
                                lineHeight = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                            )
                        } else {
                            val textColor = when (entry.level) {
                                LogLevel.COMMAND -> Color(0xFF64B5F6)
                                LogLevel.SUCCESS -> Color(0xFF81C784)
                                LogLevel.WARN -> Color(0xFFFFB74D)
                                LogLevel.ERROR, LogLevel.LOGCAT_E -> Color(0xFFE57373)
                                LogLevel.STREAM -> Color(0xFFBA68C8)
                                LogLevel.JSON -> Color(0xFFB0BEC5)
                                LogLevel.INFO, LogLevel.LOGCAT_I -> Color(0xFFECEFF1)
                                else -> Color(0xFF90A4AE)
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
                                Text(
                                    text = "[${entry.logcatTag}]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.8f),
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
                                    text = "Executing pipeline / query...",
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
                        text = if (selectedProviderName != null) "e.g. inspect $selectedProviderName or debug $selectedProviderName" else "Enter command (e.g. inspect, debug, search, logcat)",
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
