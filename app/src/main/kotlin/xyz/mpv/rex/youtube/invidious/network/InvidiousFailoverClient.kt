package xyz.mpv.rex.youtube.invidious.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import xyz.mpv.rex.youtube.invidious.cache.InvidiousCacheService
import xyz.mpv.rex.youtube.invidious.model.InvidiousInstance
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Invidious Failover HTTP Client.
 *
 * Implements resilient network communication with Invidious:
 * - Enforces a strict 5-second timeout per attempt.
 * - Routes requests initially through the saved primary URL or highest-ranked instance.
 * - Silently catches network timeouts, connection drops, HTTP 429 (Too Many Requests),
 *   and HTTP 5xx (Server Errors).
 * - Automatically switches Base URL to the next healthy instance in the ranked pool and retries.
 * - Persists the working instance as the default primary URL for subsequent requests upon success.
 */
class InvidiousFailoverClient(
    private val cacheService: InvidiousCacheService = InvidiousCacheService(),
) {
    companion object {
        private const val TAG = "InvidiousFailover"
        private const val TIMEOUT_SECONDS = 5L
        private const val MAX_FAILOVER_ATTEMPTS = 5
    }

    // OkHttpClient with strict 5-second timeout per instance attempt
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // Handle retry at application level with instance switching
        .build()

    @Volatile
    private var activeBaseUrl: String? = null

    /**
     * Executes a GET request with silent failover across ranked Invidious instances.
     *
     * @param endpointPath Relative path and query (e.g. "/api/v1/trending?type=Movies&region=US")
     * @param requestCustomizer Optional lambda to customize request headers or method
     * @return Successful response body string or null if all attempts failed
     */
    suspend fun executeGet(
        endpointPath: String,
        requestCustomizer: ((Request.Builder) -> Unit)? = null,
    ): String? = withContext(Dispatchers.IO) {
        val rankedInstances = cacheService.getRankedInstances()
        if (rankedInstances.isEmpty()) {
            Log.e(TAG, "No Invidious instances available in cache or network pool")
            return@withContext null
        }

        // Build candidate list: Start with active/saved primary URL, then remaining ranked instances
        val candidateBaseUrls = mutableListOf<String>()
        val savedPrimary = activeBaseUrl ?: cacheService.getSavedPrimaryUrl()
        if (!savedPrimary.isNullOrBlank()) {
            candidateBaseUrls.add(savedPrimary)
        }
        rankedInstances.forEach { inst ->
            if (!candidateBaseUrls.contains(inst.baseUrl)) {
                candidateBaseUrls.add(inst.baseUrl)
            }
        }

        var attempts = 0
        val maxAttempts = candidateBaseUrls.size.coerceAtMost(MAX_FAILOVER_ATTEMPTS)

        for (baseUrl in candidateBaseUrls) {
            attempts++
            val fullUrl = baseUrl.trimEnd('/') + "/" + endpointPath.trimStart('/')
            Log.d(TAG, "[Attempt $attempts/$maxAttempts] Querying Invidious: $fullUrl")

            val requestBuilder = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json")

            requestCustomizer?.invoke(requestBuilder)

            var response: Response? = null
            try {
                response = httpClient.newCall(requestBuilder.build()).execute()
                val statusCode = response.code

                // Silent failover on HTTP 429 (Too Many Requests) or HTTP 5xx (Server Errors)
                if (statusCode == 429 || statusCode in 500..599) {
                    Log.w(TAG, "Instance $baseUrl failed with HTTP $statusCode. Switching to next instance...")
                    response.close()
                    continue
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val trimmed = body?.trimStart() ?: ""
                    val isJson = trimmed.startsWith("[") || trimmed.startsWith("{")
                    val isBlocked = trimmed.contains("Endpoint disabled") ||
                            trimmed.contains("<html", ignoreCase = true) ||
                            trimmed.contains("<!doctype", ignoreCase = true) ||
                            trimmed.contains("Auth with CAPTCHA", ignoreCase = true)

                    if (isJson && !isBlocked) {
                        // Success! Save this working instance as primary for subsequent calls
                        if (activeBaseUrl != baseUrl) {
                            activeBaseUrl = baseUrl
                            cacheService.savePrimaryUrl(baseUrl)
                        }
                        Log.d(TAG, "Success on instance $baseUrl")
                        return@withContext body
                    } else {
                        Log.w(TAG, "Instance $baseUrl returned HTML/blocked response. Switching to next instance...")
                    }
                } else {
                    Log.w(TAG, "Instance $baseUrl returned unexpected HTTP ${response.code}. Switching...")
                    response.close()
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout (>5s) on instance $baseUrl. Shifting to next candidate...")
            } catch (e: IOException) {
                Log.w(TAG, "Network I/O failure on instance $baseUrl (${e.message}). Shifting...")
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error on instance $baseUrl (${e.message}). Shifting...")
            } finally {
                runCatching { response?.close() }
            }

            if (attempts >= maxAttempts) {
                break
            }
        }

        Log.e(TAG, "All $maxAttempts Invidious candidate attempts exhausted for $endpointPath")
        return@withContext null
    }

    /**
     * Returns the currently active instance base URL.
     */
    fun getActiveBaseUrl(): String {
        return activeBaseUrl ?: cacheService.getSavedPrimaryUrl() ?: "https://yewtu.be"
    }

    /**
     * Explicitly switches the active instance and persists it to local settings.
     */
    fun setActiveBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        activeBaseUrl = clean
        cacheService.savePrimaryUrl(clean)
        Log.i(TAG, "Switched active Invidious instance to: $clean")
    }

    /**
     * Measures response latency to an Invidious instance in milliseconds.
     */
    suspend fun pingInstance(baseUrl: String): Long? = withContext(Dispatchers.IO) {
        val testUrl = baseUrl.trimEnd('/') + "/api/v1/stats"
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(testUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@withContext (System.currentTimeMillis() - start)
                }
            }
        } catch (e: Exception) {
            // Fallback ping to trending
            try {
                val fbUrl = baseUrl.trimEnd('/') + "/api/v1/trending?type=Music"
                val fbReq = Request.Builder()
                    .url(fbUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                httpClient.newCall(fbReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext (System.currentTimeMillis() - start)
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }
}
