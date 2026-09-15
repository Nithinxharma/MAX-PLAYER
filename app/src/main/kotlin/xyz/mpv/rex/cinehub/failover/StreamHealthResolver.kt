package xyz.mpv.rex.cinehub.failover

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Pre-Flight Stream Health Checker & Header Injector.
 *
 * Intercepts extracted stream links from providers and executes lightweight
 * pre-flight checks (HEAD or Range GET) to verify that streams respond with 200 OK
 * or 206 Partial Content before sending them to the player.
 *
 * Injects required HTTP headers (`Referer`, `Origin`, `User-Agent`) so hosters
 * do not reject playback with HTTP 403 Forbidden.
 */
object StreamHealthResolver {
    private const val TAG = "StreamHealthResolver"
    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val PREFLIGHT_TIMEOUT_SECONDS = 4L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(PREFLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PREFLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Converts a list of CineHubStreamLinks into StreamCandidates, applies header injection,
     * performs asynchronous pre-flight checks, and returns candidates ranked by health.
     */
    suspend fun resolveAndRankStreamLinks(
        links: List<CineHubStreamLink>,
    ): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val candidates = links.map { link ->
            StreamCandidate(
                url = link.url,
                name = link.name,
                quality = link.quality,
                isM3u8 = link.isM3u8,
                headers = link.headers
            )
        }
        resolveAndRankCandidates(candidates)
    }

    /**
     * Inspects candidate streams, injects essential HTTP headers, runs concurrent pre-flight
     * health checks, and places verified working streams first.
     */
    suspend fun resolveAndRankCandidates(
        candidates: List<StreamCandidate>,
    ): List<StreamCandidate> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // 1. Inject required headers (Referer, Origin, User-Agent) on all candidates
        val enrichedCandidates = candidates.map { injectRequiredHeaders(it) }

        // 2. Perform parallel pre-flight checks
        val checkDeferred = enrichedCandidates.map { candidate ->
            async {
                val isHealthy = verifyStreamHealth(candidate)
                Pair(candidate, isHealthy)
            }
        }

        val results = checkDeferred.awaitAll()
        val healthyStreams = results.filter { it.second }.map { it.first }
        val unverifiedStreams = results.filter { !it.second }.map { it.first }

        Log.d(TAG, "Pre-flight check summary: ${healthyStreams.size} verified healthy, ${unverifiedStreams.size} failed/unverified")

        return@withContext if (healthyStreams.isNotEmpty()) {
            // Verified streams first, followed by unverified as fallbacks
            healthyStreams + unverifiedStreams
        } else {
            // If all failed preflight (some hosters block HEAD/range probes completely),
            // do not block playback; return the original candidate list with headers injected
            Log.w(TAG, "All candidates failed preflight probing; returning original candidates with injected headers")
            enrichedCandidates
        }
    }

    /**
     * Injects required Referer, Origin, and User-Agent headers if missing.
     */
    fun injectRequiredHeaders(candidate: StreamCandidate): StreamCandidate {
        val uri = runCatching { Uri.parse(candidate.url) }.getOrNull()
        val host = uri?.host ?: ""
        val scheme = uri?.scheme ?: "https"
        val origin = if (host.isNotBlank()) "$scheme://$host" else "https://vidsrc.to"
        val referer = "$origin/"

        val updatedHeaders = candidate.headers.toMutableMap()

        if (!updatedHeaders.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            updatedHeaders["User-Agent"] = DEFAULT_USER_AGENT
        }
        if (!updatedHeaders.keys.any { it.equals("Referer", ignoreCase = true) }) {
            updatedHeaders["Referer"] = referer
        }
        if (!updatedHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
            updatedHeaders["Origin"] = origin
        }

        return candidate.copy(headers = updatedHeaders)
    }

    /**
     * Executes a lightweight HTTP HEAD or Range GET request to verify server status.
     * Accepts HTTP 200 OK or 206 Partial Content (or 302/301 redirects).
     */
    private suspend fun verifyStreamHealth(candidate: StreamCandidate): Boolean = withContext(Dispatchers.IO) {
        val url = candidate.url.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext true // Local or custom URI schemes are assumed healthy
        }

        // 1. Try lightweight HTTP HEAD first
        val headRequest = buildProbeRequest(candidate, method = "HEAD")
        var headResponse: Response? = null
        try {
            headResponse = httpClient.newCall(headRequest).execute()
            val code = headResponse.code
            if (code == 200 || code == 206 || code in 301..308) {
                return@withContext true
            }
            // If HEAD was rejected (405 Method Not Allowed or 403 Forbidden on HEAD), try Range GET
            if (code == 405 || code == 403) {
                Log.d(TAG, "HEAD returned HTTP $code for ${candidate.name}, falling back to Range GET probe")
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "HEAD timeout for ${candidate.name}: ${e.message}")
        } catch (e: IOException) {
            Log.w(TAG, "HEAD I/O failure for ${candidate.name}: ${e.message}")
        } catch (e: Exception) {
            // Ignored, proceed to Range GET fallback
        } finally {
            runCatching { headResponse?.close() }
        }

        // 2. Fallback: Range GET request (request only first byte)
        val rangeRequest = buildProbeRequest(candidate, method = "GET", rangeHeader = "bytes=0-0")
        var rangeResponse: Response? = null
        try {
            rangeResponse = httpClient.newCall(rangeRequest).execute()
            val code = rangeResponse.code
            if (code == 200 || code == 206 || code in 301..308) {
                return@withContext true
            }
            Log.w(TAG, "Range GET probe rejected with HTTP $code for ${candidate.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Range GET probe failed for ${candidate.name}: ${e.message}")
        } finally {
            runCatching { rangeResponse?.close() }
        }

        return@withContext false
    }

    private fun buildProbeRequest(
        candidate: StreamCandidate,
        method: String,
        rangeHeader: String? = null,
    ): Request {
        val builder = Request.Builder()
            .url(candidate.url.trim())

        candidate.headers.forEach { (key, value) ->
            builder.header(key, value)
        }

        if (!rangeHeader.isNullOrBlank()) {
            builder.header("Range", rangeHeader)
        }

        if (method == "HEAD") {
            builder.head()
        } else {
            builder.get()
        }

        return builder.build()
    }

    data class ScannedStream(
        val candidate: StreamCandidate,
        val isHealthy: Boolean,
        val latencyMs: Long,
        val statusMessage: String
    )

    /**
     * Actively scans all candidate stream links, measures response latency,
     * and returns structured status for each link (working vs failed).
     */
    suspend fun scanStreamsHealth(candidates: List<StreamCandidate>): List<ScannedStream> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()
        val enriched = candidates.map { injectRequiredHeaders(it) }
        val deferred = enriched.map { candidate ->
            async {
                val start = System.currentTimeMillis()
                val healthy = verifyStreamHealth(candidate)
                val latency = System.currentTimeMillis() - start
                val msg = if (healthy) "Active (${latency}ms)" else "Unreachable / 403"
                ScannedStream(candidate, healthy, latency, msg)
            }
        }
        deferred.awaitAll()
    }
}
