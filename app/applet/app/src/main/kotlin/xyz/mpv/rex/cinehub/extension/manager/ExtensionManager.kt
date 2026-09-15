package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.extension.api.MainApiProviderAdapter
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.PluginUpdateInfo
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.io.FileOutputStream

class ExtensionManager(
    private val context: Context,
    private val db: MpvExDatabase,
    private val registry: ProviderRegistry,
    private val repositoryManager: RepositoryManager,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "CineHub:ExtensionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extensionDir = File(context.filesDir, "cinehub_extensions")
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    init {
        if (!extensionDir.exists()) extensionDir.mkdirs()
        scope.launch {
            loadInstalledExtensions()
        }
    }

    fun getAllInstalledExtensions(): Flow<List<InstalledExtension>> {
        return db.extensionDao().getAllInstalledExtensions()
    }

    suspend fun loadInstalledExtensions() = withContext(Dispatchers.IO) {
        val allExts = db.extensionDao().getAllInstalledExtensionsSync()
        Log.i(TAG, "Loading ${allExts.size} installed extension providers from database")
        for (ext in allExts) {
            try {
                if (ext.localFilePath != null && File(ext.localFilePath).exists()) {
                    val apis = ExtensionLoader.loadPlugin(context, File(ext.localFilePath))
                    apis.forEach { api ->
                        registry.register(MainApiProviderAdapter(api), isEnabledByDefault = ext.isEnabled)
                        Log.d(TAG, "Registered loaded provider: ${api.name} (enabled=${ext.isEnabled})")
                    }
                } else {
                    Log.w(TAG, "Plugin file for ${ext.name} not found locally.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to instantiate provider for ${ext.name}: ${e.message}", e)
            }
        }
    }

    suspend fun installExtension(plugin: AvailablePlugin): Boolean = withContext(Dispatchers.IO) {
        try {
            var localPath: String? = null
            
            if (plugin.url.isNotBlank()) {
                val pluginFile = File(extensionDir, "${plugin.internalName}.csx")
                val request = Request.Builder().url(plugin.url).build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        FileOutputStream(pluginFile).use { fos ->
                            fos.write(bytes)
                        }
                        localPath = pluginFile.absolutePath
                        
                        val apis = ExtensionLoader.loadPlugin(context, pluginFile)
                        apis.forEach { api ->
                            registry.register(MainApiProviderAdapter(api), isEnabledByDefault = true)
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to download plugin from ${plugin.url}, code: ${response.code}")
                    return@withContext false
                }
            }
            
            val installed = InstalledExtension(
                pkgName = plugin.internalName,
                name = plugin.name,
                version = plugin.version,
                versionCode = plugin.versionCode,
                description = plugin.description,
                iconUrl = plugin.iconUrl,
                repositoryUrl = plugin.repositoryUrl,
                isEnabled = true,
                localFilePath = localPath,
                classesFile = plugin.authors.joinToString(", ")
            )
            
            db.extensionDao().insertExtension(installed)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed: ${e.message}", e)
            false
        }
    }

    suspend fun uninstallExtension(pkgName: String) = withContext(Dispatchers.IO) {
        try {
            val ext = db.extensionDao().getExtensionSync(pkgName)
            if (ext != null && ext.localFilePath != null) {
                val file = File(ext.localFilePath)
                if (file.exists()) file.delete()
            }
            
            val dummyExt = InstalledExtension(
                pkgName = pkgName,
                name = "",
                version = "",
                versionCode = 0
            )
            db.extensionDao().deleteExtension(dummyExt)
            
            registry.unregister(pkgName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun toggleExtension(pkgName: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        db.extensionDao().updateExtensionState(pkgName, isEnabled)
        registry.setProviderEnabled(pkgName, isEnabled)
    }

    suspend fun checkForUpdates(): List<PluginUpdateInfo> = withContext(Dispatchers.IO) {
        val updates = mutableListOf<PluginUpdateInfo>()
        val installedList = db.extensionDao().getEnabledExtensionsSync()
        val cachedPlugins = repositoryManager.getCachedPlugins()
        for (inst in installedList) {
            val remote = cachedPlugins.find { it.internalName == inst.pkgName }
            if (remote != null && remote.versionCode > inst.versionCode) {
                updates.add(
                    PluginUpdateInfo(
                        pkgName = inst.pkgName,
                        currentVersion = inst.version,
                        newVersion = remote.version,
                        plugin = remote
                    )
                )
            }
        }
        updates
    }

    suspend fun updateExtension(plugin: AvailablePlugin): Boolean = withContext(Dispatchers.IO) {
        installExtension(plugin)
    }

    suspend fun updateAll(): Int = withContext(Dispatchers.IO) {
        _isUpdating.value = true
        var count = 0
        try {
            repositoryManager.syncAllRepositories()
            val updates = checkForUpdates()
            for (up in updates) {
                if (updateExtension(up.plugin)) {
                    count++
                }
            }
        } finally {
            _isUpdating.value = false
        }
        count
    }

    fun clearCache() {
        repositoryManager.clearCache()
        try {
            extensionDir.deleteRecursively()
            extensionDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
