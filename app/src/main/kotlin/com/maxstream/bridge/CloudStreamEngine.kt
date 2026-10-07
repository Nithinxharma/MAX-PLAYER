package com.maxstream.bridge

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.extractors.DefaultExtractors
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * CloudStreamEngine acts as the central headless controller for
 * CloudStream scraping, plugin execution, and link extraction in Max Stream.
 */
object CloudStreamEngine {
    private const val TAG = "CloudStreamEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isInitialized = false

    /**
     * Initializes the headless runtime environment, sets up HTTP singletons,
     * registers built-in extractors, and triggers asynchronous plugin loading.
     */
    fun init(context: Context) {
        if (isInitialized) return
        try {
            AcraApplication.init(context)
            com.lagradost.cloudstream3.app.initClient(context, ignoreSSL = false)
            com.lagradost.cloudstream3.insecureApp.initClient(context, ignoreSSL = true)
            DefaultExtractors.registerAll()

            scope.launch {
                loadInstalledPlugins(context)
            }

            isInitialized = true
            Log.i(TAG, "CloudStreamEngine successfully initialized.")
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing CloudStreamEngine", t)
        }
    }

    /**
     * Scans and loads downloaded .cs3 plugins from the extensions storage directory.
     */
    suspend fun loadInstalledPlugins(context: Context) {
        try {
            val extensionsDir = File(context.filesDir, RepositoryManager.ONLINE_PLUGINS_FOLDER)
            if (extensionsDir.exists() && extensionsDir.isDirectory) {
                extensionsDir.walkTopDown().maxDepth(3).forEach { file ->
                    if (file.isFile && (file.extension.equals("cs3", ignoreCase = true) || file.extension.equals("dex", ignoreCase = true))) {
                        runCatching {
                            PluginManager.loadPlugin(
                                context,
                                file,
                                PluginData(
                                    internalName = file.nameWithoutExtension,
                                    url = null,
                                    isOnline = true,
                                    filePath = file.absolutePath,
                                    version = 1
                                )
                            )
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error loading installed plugins: ${t.message}")
        }
    }

    fun getActiveProviders(): List<MainAPI> = APIHolder.apis.toList()

    fun getActiveExtractorsCount(): Int = APIHolder.extractorApis.size
}
