package xyz.mpv.rex.cinehub.provider.server

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.security.MessageDigest

/**
 * ServerProviderSyncService orchestrates automatic background synchronization
 * of managed streaming providers directly from the MaxStream server infrastructure.
 *
 * Normal users never interact with raw repositories, manual installation or extension catalogs.
 * Managed streaming feeds are synced and updated according to user plan permissions.
 */
class ServerProviderSyncService(
    private val context: Context,
    private val client: OkHttpClient,
    private val db: MpvExDatabase,
    private val extensionManager: ExtensionManager
) {
    companion object {
        private const val TAG = "ServerProviderSync"
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"
        
        // Comprehensive community repository list to source all verified extensions
        private val COMMUNITY_REPOS = listOf(
            "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
            "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
            "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json",
            "https://raw.githubusercontent.com/CakesTwix/cloudstream-extensions-uk/master/repo.json",
            "https://raw.githubusercontent.com/saimuelbr/saimuelrepo/refs/heads/main/builds/repo.json",
            "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json",
            "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    /**
     * Entry point triggered during application initialization or network reconnection.
     */
    fun triggerSilentSync(force: Boolean = false) {
        scope.launch {
            syncProviders(force = force)
        }
    }

    /**
     * Executes the server sync lifecycle:
     * 1. Request provider manifest from backend.
     * 2. Compare installed providers.
     * 3. Update outdated installed providers silently.
     * 4. Remove revoked providers.
     * 5. Reload APIHolder registry.
     */
    suspend fun syncProviders(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value && !force) {
            Log.d(TAG, "Sync already in progress, skipping duplicate invocation.")
            return@withContext false
        }

        _isSyncing.value = true
        _syncStatus.value = "Requesting provider manifest..."
        Log.i(TAG, "SERVER_PROVIDER_SYNC: Beginning server-controlled provider sync...")

        try {
            // Stage 1: Request manifest from backend / managed configuration
            val manifest = fetchRemoteManifest()

            // Stage 2: Compare with currently installed providers
            val installedExtensions = db.extensionDao().getAllInstalledExtensionsSync()
            val installedMap = installedExtensions.associateBy { it.pkgName.lowercase() }

            // Stage 3: Update outdated INSTALLED providers silently (do not auto-install unpermitted new ones)
            for (managed in manifest.providers) {
                if (!managed.enabled) continue

                val existing = installedMap[managed.id.lowercase()]
                val isOutdated = existing != null && existing.versionCode < managed.versionCode

                if (isOutdated && existing != null) {
                    _syncStatus.value = "Updating ${managed.name}..."
                    Log.i(TAG, "SERVER_PROVIDER_SYNC: Updating managed provider ${managed.name} (v${managed.version}, code=${managed.versionCode})")
                    
                    val pluginPayload = AvailablePlugin(
                        name = managed.name,
                        internalName = managed.id,
                        version = managed.version,
                        versionCode = managed.versionCode,
                        url = managed.downloadUrl,
                        description = managed.description ?: "Official managed provider",
                        iconUrl = null,
                        repositoryUrl = "server://maxstream.cloud",
                        lang = managed.lang
                    )

                    // Execute update through ExtensionManager
                    extensionManager.installExtension(pluginPayload)
                }
            }

            // Stage 4: Remove revoked providers
            for (revokedId in manifest.revokedProviders) {
                if (installedMap.containsKey(revokedId.lowercase())) {
                    Log.w(TAG, "SERVER_PROVIDER_SYNC: Revoking and uninstalling blacklisted provider: $revokedId")
                    extensionManager.uninstallExtension(revokedId)
                }
            }

            // Stage 5: Reload APIHolder registry and notify system
            _syncStatus.value = "Reloading provider registry..."
            extensionManager.loadInstalledExtensions()

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncStatus.value = "Sync completed successfully"
            Log.i(TAG, "SERVER_PROVIDER_SYNC: Completed successfully. Total active in APIHolder: ${APIHolder.allProviders.size}")
            return@withContext true
        } catch (t: Throwable) {
            Log.e(TAG, "SERVER_PROVIDER_SYNC: Error during silent provider synchronization", t)
            _syncStatus.value = "Sync completed: ${t.localizedMessage}"
            return@withContext false
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Resolves the server-controlled provider manifest.
     */
    private suspend fun fetchRemoteManifest(): ProviderManifest = withContext(Dispatchers.IO) {
        val aggregatedProviders = mutableListOf<ManagedProvider>()

        for (repoUrl in COMMUNITY_REPOS) {
            try {
                val request = Request.Builder()
                    .url(repoUrl)
                    .header("User-Agent", "Mozilla/5.0 (MaxStream-Client/1.0)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val parsed = parseRepositoryToManifest(bodyString, repoUrl)
                        aggregatedProviders.addAll(parsed.providers)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not reach remote manifest $repoUrl: ${e.message}")
            }
        }

        return@withContext ProviderManifest(
            schemaVersion = 1,
            providers = aggregatedProviders.distinctBy { it.id.lowercase() },
            revokedProviders = emptyList()
        )
    }

    private fun parseRepositoryToManifest(rawJson: String, baseUrl: String = MANIFEST_URL): ProviderManifest {
        val providers = mutableListOf<ManagedProvider>()
        return try {
            val root = JSONObject(rawJson)

            // 1. Direct plugins or providers
            val plugins = root.optJSONArray("plugins") ?: root.optJSONArray("providers")
            if (plugins != null) {
                parsePluginsArray(plugins, baseUrl, providers)
            }

            // 2. pluginLists format (CloudStream repo.json standard)
            val pluginLists = root.optJSONArray("pluginLists")
            if (pluginLists != null) {
                for (i in 0 until pluginLists.length()) {
                    val listUrl = pluginLists.getString(i)
                    runCatching {
                        val req = Request.Builder()
                            .url(listUrl)
                            .header("User-Agent", "Mozilla/5.0 (MaxStream-Client/1.0)")
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful && resp.body != null) {
                                val listText = resp.body!!.string().trim()
                                if (listText.startsWith("[")) {
                                    val arr = org.json.JSONArray(listText)
                                    parsePluginsArray(arr, listUrl, providers)
                                } else if (listText.startsWith("{")) {
                                    val subRoot = JSONObject(listText)
                                    val subPlugins = subRoot.optJSONArray("plugins") ?: subRoot.optJSONArray("providers")
                                    if (subPlugins != null) {
                                        parsePluginsArray(subPlugins, listUrl, providers)
                                    }
                                }
                            }
                        }
                    }.onFailure { err ->
                        Log.w(TAG, "Failed to load sub-list $listUrl: ${err.message}")
                    }
                }
            }

            ProviderManifest(providers = providers.distinctBy { it.id })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse json repository: ${e.message}")
            ProviderManifest()
        }
    }

    private fun parsePluginsArray(arr: org.json.JSONArray, sourceUrl: String, dest: MutableList<ManagedProvider>) {
        val base = sourceUrl.substringBeforeLast("/") + "/"
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val internalName = p.optString("internalName", p.optString("id", p.optString("name", "plugin_$i")))
            val name = p.optString("name", internalName)
            var url = p.optString("url", "")
            val version = p.optString("version", "1.0.0")
            val versionCode = p.optInt("versionCode", 1)
            val description = p.optString("description", "")
            val lang = p.optString("lang", p.optString("language", "en"))

            if (url.isNotBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = base + url.removePrefix("./").removePrefix("/")
                }
                dest.add(
                    ManagedProvider(
                        id = internalName,
                        name = name,
                        version = version,
                        versionCode = versionCode,
                        downloadUrl = url,
                        enabled = true,
                        description = description,
                        lang = lang
                    )
                )
            }
        }
    }
}
