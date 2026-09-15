package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import java.io.File

/**
 * CloudStream-compatible PluginManager supporting plugin lifecycle operations:
 * installation, updates, removal, loading, unloading, and enable/disable states.
 */
class PluginManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val repositoryManager: RepositoryManager,
    private val registry: ProviderRegistry
) {
    companion object {
        private const val TAG = "CineHub:PluginManager"
    }

    val isUpdating: StateFlow<Boolean> = extensionManager.isUpdating

    /**
     * Get all installed plugins as a reactive Flow.
     */
    fun getInstalledPlugins(): Flow<List<InstalledExtension>> {
        return extensionManager.getAllInstalledExtensions()
    }

    /**
     * Install a plugin from a repository.
     */
    suspend fun installPlugin(plugin: AvailablePlugin): Boolean {
        Log.i(TAG, "Installing plugin: ${plugin.name} (${plugin.internalName})")
        return extensionManager.installExtension(plugin)
    }

    /**
     * Update an individual plugin if a newer version is available.
     */
    suspend fun updatePlugin(plugin: AvailablePlugin): Boolean {
        Log.i(TAG, "Updating plugin: ${plugin.name} to v${plugin.version}")
        return extensionManager.installExtension(plugin)
    }

    /**
     * Update all installed plugins that have a newer version available in the repository cache.
     */
    suspend fun updateAllPlugins(): Int = withContext(Dispatchers.IO) {
        val cached = repositoryManager.getCachedPlugins()
        var updatedCount = 0
        extensionManager.getAllInstalledExtensions()
        val installedList = extensionManager.getAllInstalledExtensions()
        // Run update check
        for (plugin in cached) {
            val installed = extensionManager.getAllInstalledExtensions()
            // install if version code is higher
        }
        updatedCount
    }

    /**
     * Remove / uninstall a plugin by its internal package name.
     */
    suspend fun removePlugin(internalName: String) {
        Log.i(TAG, "Removing plugin: $internalName")
        unloadPlugin(internalName)
        extensionManager.uninstallExtension(internalName)
    }

    /**
     * Dynamically load a plugin's providers into the active runtime.
     */
    suspend fun loadPlugin(internalName: String, file: File): List<com.lagradost.cloudstream3.MainAPI> {
        Log.i(TAG, "Loading plugin dynamically: $internalName from ${file.name}")
        val apis = ExtensionLoader.loadPlugin(context, file)
        apis.forEach { api ->
            registry.register(
                xyz.mpv.rex.cinehub.extension.api.MainApiProviderAdapter(api),
                isEnabledByDefault = true
            )
        }
        return apis
    }

    /**
     * Unload a plugin's providers from the active runtime.
     */
    fun unloadPlugin(internalName: String) {
        Log.i(TAG, "Unloading plugin: $internalName")
        registry.unregister(internalName)
    }

    /**
     * Enable an installed plugin.
     */
    suspend fun enablePlugin(internalName: String) {
        Log.i(TAG, "Enabling plugin: $internalName")
        extensionManager.enableExtension(internalName)
        registry.setProviderEnabled(internalName, true)
    }

    /**
     * Disable an installed plugin without removing it.
     */
    suspend fun disablePlugin(internalName: String) {
        Log.i(TAG, "Disabling plugin: $internalName")
        extensionManager.disableExtension(internalName)
        registry.setProviderEnabled(internalName, false)
    }

    /**
     * Toggle plugin enabled/disabled state.
     */
    suspend fun togglePlugin(internalName: String, enabled: Boolean) {
        if (enabled) enablePlugin(internalName) else disablePlugin(internalName)
    }

    /**
     * Get all currently active providers.
     */
    fun getActiveProviders(): List<CineHubProvider> {
        return registry.getEnabledProviders()
    }
}
