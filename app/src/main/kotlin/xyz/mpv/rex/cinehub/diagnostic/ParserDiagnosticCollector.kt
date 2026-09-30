package xyz.mpv.rex.cinehub.diagnostic

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import xyz.mpv.rex.cinehub.diagnostic.model.CandidateLinkItem
import xyz.mpv.rex.cinehub.diagnostic.model.JsonMappingAnalysis
import xyz.mpv.rex.cinehub.diagnostic.model.LoadLinksFailureDiagnosticPayload
import xyz.mpv.rex.cinehub.diagnostic.model.ParserFailureDiagnosticPayload
import xyz.mpv.rex.cinehub.diagnostic.model.RegexMatchResultItem
import java.net.URLEncoder
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Diagnostic Collector for Provider Parser & Stream Link Extraction Failures.
 *
 * Exposes the exact point of failure when a provider returns HTTP 200 with 0 results
 * or when loadLinks() fails to resolve video streams.
 */
object ParserDiagnosticCollector {

    private const val TAG = "ParserDiagnostics"
    private const val MAX_PREVIEW_BYTES = 10 * 1024 // 10 KB max preview

    // Common OTT and scraping CSS selectors across major movie/series CMS platforms (WordPress, Dooplay, PsychoPlay, StreamLab, etc.)
    val COMMON_PARSER_SELECTORS = listOf(
        "div.post-item",
        "div.flw-item",
        "article.item-list",
        "div.item-poster",
        "div.ml-item",
        "div.browse-movie-wrap",
        "div.film-detail",
        "article.post",
        "div.result-item",
        "div.thumb",
        "div.card",
        "a.lnk-blk",
        "a[href*='/movie/']",
        "a[href*='/series/']",
        "a[href*='/movies/']",
        "a[href*='/tv/']",
        "h2.entry-title a",
        "div.search-results a",
        "div.movies-list",
        "div.items-list > div",
        "ul.movies > li",
        "div.poster",
        "div.item-inner"
    )

    private val _latestSearchDiagnostics = MutableStateFlow<List<ParserFailureDiagnosticPayload>>(emptyList())
    val latestSearchDiagnostics: StateFlow<List<ParserFailureDiagnosticPayload>> = _latestSearchDiagnostics.asStateFlow()

    private val _latestLoadLinksDiagnostics = MutableStateFlow<List<LoadLinksFailureDiagnosticPayload>>(emptyList())
    val latestLoadLinksDiagnostics: StateFlow<List<LoadLinksFailureDiagnosticPayload>> = _latestLoadLinksDiagnostics.asStateFlow()

    private val _selectedSearchDiagnostic = MutableStateFlow<ParserFailureDiagnosticPayload?>(null)
    val selectedSearchDiagnostic: StateFlow<ParserFailureDiagnosticPayload?> = _selectedSearchDiagnostic.asStateFlow()

    private val _selectedLoadLinksDiagnostic = MutableStateFlow<LoadLinksFailureDiagnosticPayload?>(null)
    val selectedLoadLinksDiagnostic: StateFlow<LoadLinksFailureDiagnosticPayload?> = _selectedLoadLinksDiagnostic.asStateFlow()

    private val diagnosticHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(request)
        }
        .build()

    fun selectSearchDiagnostic(payload: ParserFailureDiagnosticPayload?) {
        _selectedSearchDiagnostic.value = payload
    }

    fun selectLoadLinksDiagnostic(payload: LoadLinksFailureDiagnosticPayload?) {
        _selectedLoadLinksDiagnostic.value = payload
    }

    fun clear() {
        _latestSearchDiagnostics.value = emptyList()
        _latestLoadLinksDiagnostics.value = emptyList()
        _selectedSearchDiagnostic.value = null
        _selectedLoadLinksDiagnostic.value = null
    }

    /**
     * Executes real HTTP request against the provider's search endpoint, captures headers,
     * status, raw payload preview (first 10 KB), CSS selector match counts, candidate links,
     * JSON mapping analysis, and regex match results.
     */
    suspend fun analyzeSearchParser(
        api: MainAPI,
        query: String,
        customClient: OkHttpClient? = null
    ): ParserFailureDiagnosticPayload = withContext(Dispatchers.IO) {
        val client = customClient ?: diagnosticHttpClient
        val encodedQuery = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
        val baseUrl = api.mainUrl.trimEnd('/')

        // Determine likely search URL
        val candidateSearchUrls = listOf(
            "$baseUrl/search?q=$encodedQuery",
            "$baseUrl/?s=$encodedQuery",
            "$baseUrl/search/$encodedQuery",
            "$baseUrl/api/search?keyword=$encodedQuery",
            "$baseUrl/?q=$encodedQuery"
        )
        val requestUrl = candidateSearchUrls.first()

        var finalUrl = requestUrl
        var httpStatus = 0
        var contentType = "unknown"
        var rawBytes = ByteArray(0)
        var responseBodyString = ""
        var isNetworkError = false
        var networkErrorMessage = ""

        try {
            val req = Request.Builder().url(requestUrl).build()
            client.newCall(req).execute().use { response ->
                httpStatus = response.code
                finalUrl = response.request.url.toString()
                contentType = response.header("Content-Type") ?: "text/html"
                val body = response.body
                if (body != null) {
                    rawBytes = body.bytes()
                    responseBodyString = String(rawBytes, Charsets.UTF_8)
                }
            }
        } catch (t: Throwable) {
            isNetworkError = true
            networkErrorMessage = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "Search network request error on ${api.name}: $networkErrorMessage")
        }

        val responseSize = rawBytes.size.toLong()
        val responseSizeFormatted = formatBytes(responseSize)

        // Response preview: first 10 KB max
        val previewLength = responseBodyString.length.coerceAtMost(MAX_PREVIEW_BYTES)
        val responsePreview = if (responseBodyString.isNotEmpty()) {
            responseBodyString.substring(0, previewLength)
        } else if (isNetworkError) {
            "Network Exception: $networkErrorMessage"
        } else {
            "Empty HTTP response body received (0 bytes)."
        }

        // 1. Evaluate Parser Selectors & Match Counts using Jsoup
        val selectorMatchCounts = mutableMapOf<String, Int>()
        val candidateLinks = mutableListOf<CandidateLinkItem>()
        var parsedDocument: Document? = null

        if (responseBodyString.isNotEmpty()) {
            runCatching {
                parsedDocument = Jsoup.parse(responseBodyString, finalUrl)
                val doc = parsedDocument!!

                // Test standard common selectors
                COMMON_PARSER_SELECTORS.forEach { selector ->
                    val count = try {
                        doc.select(selector).size
                    } catch (e: Exception) {
                        0
                    }
                    selectorMatchCounts[selector] = count
                }

                // Discover candidate links
                val anchorElements = doc.select("a[href]")
                anchorElements.take(40).forEach { a ->
                    val href = a.attr("abs:href").ifBlank { a.attr("href") }
                    val text = a.text().trim()
                    if (href.isNotBlank() && !href.startsWith("javascript:") && href != "#") {
                        val isProbable = href.contains("movie", ignoreCase = true) ||
                                href.contains("series", ignoreCase = true) ||
                                href.contains("film", ignoreCase = true) ||
                                href.contains("watch", ignoreCase = true) ||
                                href.contains("title", ignoreCase = true) ||
                                href.contains("episode", ignoreCase = true)
                        candidateLinks.add(
                            CandidateLinkItem(
                                text = text.ifBlank { href.substringAfterLast('/') },
                                href = href,
                                tag = "a",
                                isProbableMedia = isProbable
                            )
                        )
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Jsoup parsing error: ${e.message}")
            }
        }

        // 2. Evaluate JSON Mapping Results
        val jsonMapping = analyzeJsonMapping(responseBodyString)

        // 3. Evaluate Regex Match Results
        val regexMatchResults = evaluateRegexPatterns(responseBodyString)

        val isZeroResults = httpStatus == 200 && selectorMatchCounts.values.all { it == 0 } && candidateLinks.none { it.isProbableMedia }

        val failureReason = when {
            isNetworkError -> "Network transport failed: $networkErrorMessage"
            httpStatus != 200 -> "HTTP $httpStatus status received from search endpoint."
            isZeroResults -> "HTTP 200 OK received ($responseSizeFormatted), but parser CSS selectors matched 0 items. Possible website layout shift, Cloudflare challenge, or anti-bot obfuscation."
            else -> "HTTP 200 OK received. ${candidateLinks.size} candidate links discovered."
        }

        val payload = ParserFailureDiagnosticPayload(
            providerName = api.name,
            query = query,
            requestUrl = requestUrl,
            finalUrl = finalUrl,
            httpStatus = httpStatus,
            contentType = contentType,
            responseSize = responseSize,
            responseSizeFormatted = responseSizeFormatted,
            responsePreview = responsePreview,
            parserSelectors = COMMON_PARSER_SELECTORS,
            selectorMatchCounts = selectorMatchCounts,
            candidateLinksFound = candidateLinks,
            jsonMappingResults = jsonMapping,
            regexMatchResults = regexMatchResults,
            timestamp = System.currentTimeMillis(),
            isZeroResultFailure = isZeroResults,
            failureReason = failureReason
        )

        // Record in latest list
        val currentList = _latestSearchDiagnostics.value.toMutableList()
        currentList.removeAll { it.providerName == api.name && it.query == query }
        currentList.add(0, payload)
        if (currentList.size > 20) {
            _latestSearchDiagnostics.value = currentList.take(20)
        } else {
            _latestSearchDiagnostics.value = currentList
        }
        _selectedSearchDiagnostic.value = payload

        payload
    }

    /**
     * Analyzes loadLinks() stream extraction failure and captures detailed diagnostic breakdown.
     */
    suspend fun analyzeLoadLinks(
        api: MainAPI,
        episodeData: String,
        resolvedLinks: List<String> = emptyList(),
        explicitFailureReason: String? = null
    ): LoadLinksFailureDiagnosticPayload = withContext(Dispatchers.IO) {
        val providerUrl = api.mainUrl

        // Discover extractor URLs in episode data
        val urlPattern = Pattern.compile("https?://[^\t\r\n \"'<>]+")
        val matcher = urlPattern.matcher(episodeData)
        val extractedUrls = mutableListOf<String>()
        while (matcher.find()) {
            val u = matcher.group()
            if (!extractedUrls.contains(u)) {
                extractedUrls.add(u)
            }
        }

        val registeredExtractors = APIHolder.extractorApis.map { it.name }.distinct()
        val registryExtractors = ExtensionDiagnosticsEngine.KNOWN_REQUIRED_EXTRACTORS.map { it.name }.distinct()

        // Dynamic extractors discovered in runtime/classloader
        val dynamicExtractors = registeredExtractors.filter { !registryExtractors.contains(it) }

        val failureReason = explicitFailureReason ?: when {
            extractedUrls.isEmpty() -> "No embedded video host URLs or iframe sources found in episode data payload."
            resolvedLinks.isEmpty() -> "Found ${extractedUrls.size} candidate host URLs (${extractedUrls.take(2).joinToString()}), but no registered extractor could resolve playable media streams."
            else -> "Stream resolution succeeded with ${resolvedLinks.size} links."
        }

        val details = buildString {
            appendLine("Provider: ${api.name} (${api.mainUrl})")
            appendLine("Episode Data Length: ${episodeData.length} characters")
            appendLine("Candidate Host URLs Found: ${extractedUrls.size}")
            extractedUrls.forEach { appendLine("  - $it") }
            appendLine("Registered Extractor APIs in APIHolder: ${registeredExtractors.size} (${registeredExtractors.joinToString()})")
            appendLine("Known Industry Extractors: ${registryExtractors.size}")
            if (dynamicExtractors.isNotEmpty()) {
                appendLine("Dynamic DEX Extractors: ${dynamicExtractors.joinToString()}")
            }
            appendLine("Outcome: $failureReason")
        }

        val payload = LoadLinksFailureDiagnosticPayload(
            providerName = api.name,
            providerUrl = providerUrl,
            episodeData = episodeData.take(500),
            extractorUrlsFound = extractedUrls,
            extractorUrlsResolved = resolvedLinks,
            registeredExtractors = registeredExtractors,
            registryExtractors = registryExtractors,
            dynamicExtractorDiscoveries = dynamicExtractors,
            failureReason = failureReason,
            timestamp = System.currentTimeMillis(),
            details = details
        )

        val currentList = _latestLoadLinksDiagnostics.value.toMutableList()
        currentList.removeAll { it.providerName == api.name && it.episodeData == payload.episodeData }
        currentList.add(0, payload)
        if (currentList.size > 20) {
            _latestLoadLinksDiagnostics.value = currentList.take(20)
        } else {
            _latestLoadLinksDiagnostics.value = currentList
        }
        _selectedLoadLinksDiagnostic.value = payload

        payload
    }

    private fun analyzeJsonMapping(content: String): JsonMappingAnalysis {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return runCatching {
                val json = JSONObject(trimmed)
                val keys = json.keys().asSequence().take(15).toList()
                JsonMappingAnalysis(
                    isJson = true,
                    rootType = "JSONObject",
                    totalKeysOrItems = json.length(),
                    previewKeys = keys,
                    summary = "Root JSON Object parsed with ${json.length()} top-level properties: ${keys.joinToString()}"
                )
            }.getOrElse {
                JsonMappingAnalysis(false, "Invalid JSON", 0, emptyList(), "Malformed JSON syntax: ${it.message}")
            }
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return runCatching {
                val arr = JSONArray(trimmed)
                val firstItemKeys = if (arr.length() > 0 && arr.optJSONObject(0) != null) {
                    arr.getJSONObject(0).keys().asSequence().take(10).toList()
                } else emptyList()
                JsonMappingAnalysis(
                    isJson = true,
                    rootType = "JSONArray",
                    totalKeysOrItems = arr.length(),
                    previewKeys = firstItemKeys,
                    summary = "Root JSON Array with ${arr.length()} items. Item keys: ${firstItemKeys.joinToString()}"
                )
            }.getOrElse {
                JsonMappingAnalysis(false, "Invalid JSON Array", 0, emptyList(), "Malformed JSON array: ${it.message}")
            }
        }

        // Check for embedded JSON in script tags (e.g. Next.js __NEXT_DATA__, window.__INITIAL_STATE__)
        val nextDataPattern = Pattern.compile("<script[^>]+id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>", Pattern.DOTALL)
        val nextMatcher = nextDataPattern.matcher(content)
        if (nextMatcher.find()) {
            val jsonText = nextMatcher.group(1)?.trim() ?: ""
            return runCatching {
                val json = JSONObject(jsonText)
                JsonMappingAnalysis(
                    isJson = true,
                    rootType = "Embedded Next.js __NEXT_DATA__",
                    totalKeysOrItems = json.length(),
                    previewKeys = json.keys().asSequence().take(10).toList(),
                    summary = "Embedded Next.js JSON state found with ${json.length()} root keys."
                )
            }.getOrDefault(JsonMappingAnalysis(true, "Embedded Script", 1, listOf("__NEXT_DATA__"), "Found raw __NEXT_DATA__ block"))
        }

        return JsonMappingAnalysis(
            isJson = false,
            rootType = "HTML Document",
            totalKeysOrItems = 0,
            previewKeys = emptyList(),
            summary = "Response payload is HTML markup, not raw JSON API."
        )
    }

    private fun evaluateRegexPatterns(content: String): List<RegexMatchResultItem> {
        val patterns = listOf(
            "Packer Obfuscation" to "eval\\(function\\(p,a,c,k,e,d\\)",
            "Direct Stream Links (M3U8/MP4)" to "https?://[^\\s\"'<>]+\\.(?:m3u8|mp4|mkv)",
            "Embedded Host URLs" to "https?://(?:streamwish|vidhide|filemoon|streamtape|dood|mixdrop|vidsrc|hubcloud|fastream|superstream)[^\\s\"'<>]+",
            "File Key Values" to "(?:\"|')file(?:\"|')\\s*:\\s*(?:\"|')(https?://[^\"']+)",
            "Video Sources Array" to "sources\\s*:\\s*\\[([^\\]]+)\\]",
            "Title Attributes" to "(?:\"|')title(?:\"|')\\s*:\\s*(?:\"|')([^\"']+)"
        )

        return patterns.map { (name, regexStr) ->
            val pattern = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(content)
            val matches = mutableListOf<String>()
            var count = 0
            while (matcher.find() && count < 10) {
                count++
                val match = if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
                matches.add(match)
            }
            RegexMatchResultItem(
                patternName = name,
                pattern = regexStr,
                matchCount = count,
                samples = matches
            )
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
}
