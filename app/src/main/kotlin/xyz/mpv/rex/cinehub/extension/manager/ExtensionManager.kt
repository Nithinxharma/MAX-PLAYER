package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.PluginUpdateInfo
import xyz.mpv.rex.cinehub.extension.providers.CineOnlineBridgeProvider
import xyz.mpv.rex.cinehub.extension.providers.DeclarativeCineHubProvider
import xyz.mpv.rex.cinehub.extension.providers.OpenArchiveProvider
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File

/**
 * ExtensionManager coordinates installed extensions, life-cycles,
 * updates, and provider registrations.
 */
class ExtensionManager(
    private val context: Context,
    private val db: MpvExDatabase,
    private val registry: ProviderRegistry,
    private val repositoryManager: RepositoryManager,
    private val client: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extensionDir = File(context.filesDir, "cinehub_extensions")

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    init {
        if (!extensionDir.exists()) extensionDir.mkdirs()
        // Register built-in providers immediately
        registry.register(OpenArchiveProvider(client), isEnabledByDefault = true)
        registry.register(CineOnlineBridgeProvider(context), isEnabledByDefault = true)

        scope.launch {
            loadInstalledExtensions()
        }
    }

    fun getAllInstalledExtensions(): Flow<List<InstalledExtension>> {
        return db.extensionDao().getAllInstalledExtensions()
    }

    suspend fun loadInstalledExtensions() = withContext(Dispatchers.IO) {
        val enabledExts = db.extensionDao().getEnabledExtensionsSync()
        for (ext in enabledExts) {
            try {
                val provider = DeclarativeCineHubProvider(ext, client)
                registry.register(provider, isEnabledByDefault = ext.isEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun installExtension(plugin: AvailablePlugin): Boolean = withContext(Dispatchers.IO) {
        try {
            val installed = InstalledExtension(
                pkgName = plugin.internalName,
                name = plugin.name,
                version = plugin.version,
                versionCode = plugin.versionCode,
                description = plugin.description,
                iconUrl = plugin.iconUrl,
                repositoryUrl = plugin.repositoryUrl,
                isEnabled = true,
                localFilePath = null,
                classesFile = plugin.authors.joinToString(", ")
            )
            db.extensionDao().insertExtension(installed)

            val provider = DeclarativeCineHubProvider(installed, client)
            registry.register(provider, isEnabledByDefault = true)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uninstallExtension(pkgName: String) = withContext(Dispatchers.IO) {
        try {
            val ext = InstalledExtension(
                pkgName = pkgName,
                name = "",
                version = "",
                versionCode = 0
            )
            db.extensionDao().deleteExtension(ext)
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
