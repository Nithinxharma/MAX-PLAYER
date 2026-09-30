package xyz.mpv.rex.cinehub.diagnostic.model

import kotlinx.serialization.Serializable

/**
 * Detailed candidate link discovered inside the parsed HTML response.
 */
@Serializable
data class CandidateLinkItem(
    val text: String,
    val href: String,
    val tag: String = "a",
    val isProbableMedia: Boolean = false
)

/**
 * Result of evaluating a regex pattern against the response payload.
 */
@Serializable
data class RegexMatchResultItem(
    val patternName: String,
    val pattern: String,
    val matchCount: Int,
    val samples: List<String> = emptyList()
)

/**
 * Detailed analysis of JSON payloads or embedded JSON script blocks.
 */
@Serializable
data class JsonMappingAnalysis(
    val isJson: Boolean,
    val rootType: String, // "JSONObject", "JSONArray", "Embedded Script Block", "Non-JSON"
    val totalKeysOrItems: Int,
    val previewKeys: List<String> = emptyList(),
    val summary: String
)

/**
 * Complete Diagnostic Payload for Provider Search / Parser failures (e.g. HTTP 200 with 0 results).
 */
@Serializable
data class ParserFailureDiagnosticPayload(
    val providerName: String,
    val query: String,
    val requestUrl: String,
    val finalUrl: String,
    val httpStatus: Int,
    val contentType: String,
    val responseSize: Long,
    val responseSizeFormatted: String,
    val responsePreview: String, // Up to 10 KB max
    val parserSelectors: List<String>,
    val selectorMatchCounts: Map<String, Int>,
    val candidateLinksFound: List<CandidateLinkItem>,
    val jsonMappingResults: JsonMappingAnalysis,
    val regexMatchResults: List<RegexMatchResultItem>,
    val timestamp: Long = System.currentTimeMillis(),
    val isZeroResultFailure: Boolean = true,
    val failureReason: String = "Parser executed successfully over HTTP 200 OK but returned 0 items."
)

/**
 * Complete Diagnostic Payload for Provider loadLinks() / Stream Resolution failures.
 */
@Serializable
data class LoadLinksFailureDiagnosticPayload(
    val providerName: String,
    val providerUrl: String,
    val episodeData: String,
    val extractorUrlsFound: List<String>,
    val extractorUrlsResolved: List<String>,
    val registeredExtractors: List<String>,
    val registryExtractors: List<String>,
    val dynamicExtractorDiscoveries: List<String>,
    val failureReason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)
