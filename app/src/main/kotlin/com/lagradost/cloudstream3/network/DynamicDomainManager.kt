package com.lagradost.cloudstream3.network

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * CloudStream-equivalent Runtime Domain Synchronization Engine.
 *
 * Automatically fetches, caches, and applies dynamic domain configurations (urls.json)
 * to prevent dead/parked domains from breaking provider search and streaming extraction.
 */
object DynamicDomainManager {

    private const val TAG = "DynamicDomainManager"
    private const val PREFS_NAME = "cs3_dynamic_domains"
    private const val CACHE_FILE_NAME = "urls.json"

    // Verified CloudStream live URLs feeds
    private val URL_FEEDS = listOf(
        "https://raw.githubusercontent.com/recloudstream/urls/master/urls.json",
        "https://raw.githubusercontent.com/CineStream/urls/master/urls.json"
    )

    private val domainMap = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Pre-populated verified seed domains for fail-safe instant startup
    private val SEED_DOMAINS = mapOf(
        "moviesmod" to "https://moviesmod.zip",
        "vegamovies" to "https://vegamovies.im",
        "topmovies" to "https://topmovies.dad",
        "bollyflix" to "https://bollyflix.tools",
        "superstream" to "https://superstream.media",
        "rogmovies" to "https://rogmovies.dad",
        "moviebox" to "https://moviebox.ph",
        "allmovieland" to "https://allmovieland.fun",
        "cinestream" to "https://cinestream.media",
        "showbox" to "https://showbox.media",
        "sora" to "https://sorastream.app"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {
        // 1. Load seed domains
        SEED_DOMAINS.forEach { (k, v) -> domainMap[normalizeKey(k)] = v }

        // 2. Load disk cache if available
        loadCachedDomains()

        // 3. Initiate non-blocking background sync
        syncRemoteDomains()
    }

    private fun normalizeKey(name: String): String {
        return name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
    }

    /**
     * Updates an arbitrary URL or domain to the active working domain.
     */
    fun updateUrl(url: String): String {
        if (url.isBlank()) return url
        val clean = url.trim()
        for ((key, targetDomain) in domainMap) {
            if (clean.contains(key, ignoreCase = true) && targetDomain.isNotBlank()) {
                val uri = runCatching { java.net.URI(clean) }.getOrNull()
                if (uri != null && uri.host != null) {
                    val host = uri.host
                    val targetUri = runCatching { java.net.URI(targetDomain) }.getOrNull()
                    if (targetUri != null && targetUri.host != null && !host.equals(targetUri.host, ignoreCase = true)) {
                        return clean.replace(host, targetUri.host)
                    }
                }
            }
        }
        return clean
    }

    /**
     * Applies dynamic domain replacement to a MainAPI instance if a newer domain is registered.
     */
    fun applyDynamicDomain(api: MainAPI) {
        val key = normalizeKey(api.name)
        val activeDomain = domainMap[key]
        if (!activeDomain.isNullOrBlank()) {
            val oldUrl = api.mainUrl
            val targetTrimmed = activeDomain.trimEnd('/')
            if (!oldUrl.equals(targetTrimmed, ignoreCase = true)) {
                Log.i(TAG, "DYNAMIC_DOMAIN: Updating ${api.name} domain from '$oldUrl' to '$targetTrimmed'")
                api.mainUrl = targetTrimmed
            }
        }
    }

    fun getDomainForProvider(providerName: String, fallbackUrl: String): String {
        val key = normalizeKey(providerName)
        return domainMap[key] ?: fallbackUrl
    }

    fun getAllDomains(): Map<String, String> = domainMap.toMap()

    fun syncRemoteDomains() {
        scope.launch {
            for (feedUrl in URL_FEEDS) {
                try {
                    val req = Request.Builder().url(feedUrl).build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful && res.body != null) {
                            val bodyStr = res.body!!.string()
                            val json = JSONObject(bodyStr)
                            val keys = json.keys()
                            var updatedCount = 0
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val v = json.optString(k, "").trim()
                                if (v.isNotBlank() && v.startsWith("http")) {
                                    domainMap[normalizeKey(k)] = v.trimEnd('/')
                                    updatedCount++
                                }
                            }
                            Log.i(TAG, "DYNAMIC_DOMAIN: Successfully synced $updatedCount domains from $feedUrl")
                            saveCachedDomains(bodyStr)
                            return@launch
                        }
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "Sync failed for feed $feedUrl: ${t.message}")
                }
            }
        }
    }

    private fun loadCachedDomains() {
        try {
            val ctx = AcraApplication.context
            val cacheFile = File(ctx.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists() && cacheFile.canRead()) {
                val jsonStr = cacheFile.readText()
                val json = JSONObject(jsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k, "").trim()
                    if (v.isNotBlank()) {
                        domainMap[normalizeKey(k)] = v.trimEnd('/')
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore cache read failures
        }
    }

    private fun saveCachedDomains(jsonStr: String) {
        try {
            val ctx = AcraApplication.context
            val cacheFile = File(ctx.cacheDir, CACHE_FILE_NAME)
            cacheFile.writeText(jsonStr)
        } catch (_: Throwable) {
            // Ignore cache write failures
        }
    }
}
