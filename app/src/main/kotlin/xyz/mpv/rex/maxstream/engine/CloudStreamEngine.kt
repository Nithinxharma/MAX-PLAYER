@file:Suppress("DEPRECATION")
package xyz.mpv.rex.maxstream.engine

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.network.addCloudFlareDns
import com.lagradost.cloudstream3.network.addGoogleDns
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * CloudStreamEngine acts as the central headless controller for
 * CloudStream scraping, plugin execution, and link extraction in Max Stream.
 */
object CloudStreamEngine {
    private const val TAG = "CloudStreamEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isInitialized = false

    /**
     * Initializes the headless runtime environment, sets up HTTP singletons with
     * CloudFlare and Google DNS, registers built-in extractors, and triggers asynchronous plugin loading.
     */
    fun init(context: Context) {
        if (isInitialized) return
        try {
            runCatching {
                val companion = CloudStreamApp.Companion
                val method = companion::class.java.getDeclaredMethod("setContext", Context::class.java)
                method.isAccessible = true
                method.invoke(companion, context.applicationContext)
            }

            setupEngine()

            scope.launch {
                autoLoadDefaultProviders(context.applicationContext)
                loadInstalledPlugins(context.applicationContext)
            }

            isInitialized = true
            Log.i(TAG, "CloudStreamEngine successfully initialized with DNS & AAR engines.")
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing CloudStreamEngine", t)
        }
    }

    /**
     * Auto-loads and registers default providers into APIHolder.
     */
    fun autoLoadDefaultProviders(context: Context) {
        try {
            val tmdb = TmdbProvider()
            if (!APIHolder.allProviders.any { it.name.equals(tmdb.name, ignoreCase = true) }) {
                APIHolder.addPlugin(tmdb)
                Log.i(TAG, "Default TMDB provider auto-registered into APIHolder.")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not auto-register TMDB provider: ${t.message}")
        }
    }

    /**
     * Configures the global HTTP client with CloudFlare and Google DNS.
     */
    fun setupEngine() {
        try {
            val client = OkHttpClient.Builder()
                .addCloudFlareDns()
                .addGoogleDns()
                .build()
            initClient(client)
            Log.i(TAG, "CloudStreamEngine network initialized with CloudFlare and Google DNS.")
        } catch (t: Throwable) {
            Log.e(TAG, "Error setting up network client", t)
        }
    }

    fun initClient(client: OkHttpClient) {
        runCatching {
            com.lagradost.cloudstream3.app.baseClient = client
        }
    }

    /**
     * Scans and loads downloaded plugins from the extensions storage directory.
     */
    suspend fun loadInstalledPlugins(context: Context) {
        try {
            runCatching {
                val method = PluginManager::class.java.methods.firstOrNull { 
                    it.name.contains("loadAllLocalPlugins") 
                }
                method?.invoke(PluginManager, context, false)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error loading installed plugins: ${t.message}")
        }
    }

    /**
     * Media Search using TMDB provider.
     */
    suspend fun searchTmdb(query: String): List<SearchResponse>? {
        return try {
            val tmdb = TmdbProvider()
            tmdb.search(query)
        } catch (t: Throwable) {
            Log.e(TAG, "Error searching TMDB", t)
            null
        }
    }

    /**
     * Load Media Details using TMDB provider.
     */
    suspend fun loadTmdb(url: String): LoadResponse? {
        return try {
            val tmdb = TmdbProvider()
            tmdb.load(url)
        } catch (t: Throwable) {
            Log.e(TAG, "Error loading TMDB content", t)
            null
        }
    }

    /**
     * Stream Link Extraction using the AAR's loadExtractor.
     */
    suspend fun resolveStream(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            loadExtractor(url, referer = "", subtitleCallback = {}) { link ->
                callback(link)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error resolving stream extractor for url: $url", t)
        }
    }

    /**
     * Returns count of active extractors in the runtime.
     */
    fun getActiveExtractorsCount(): Int = com.lagradost.cloudstream3.utils.extractorApis.size
}
