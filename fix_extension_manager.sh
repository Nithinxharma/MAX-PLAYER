#!/bin/bash
cat << 'INNER_EOF' > app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/manager/ExtensionManager.kt
package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.io.FileOutputStream
import xyz.mpv.rex.cinehub.extension.api.MainApiProviderAdapter

class ExtensionManager(
    private val context: Context,
    private val db: MpvExDatabase,
    private val registry: ExtensionRegistry,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "CineHub:ExtensionManager"
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val exts = db.extensionDao().getAllExtensionsSync()
        exts.forEach { ext ->
            try {
                if (ext.localFilePath != null && File(ext.localFilePath).exists()) {
                    val apis = ExtensionLoader.loadPlugin(context, File(ext.localFilePath))
                    apis.forEach { api ->
                        registry.register(MainApiProviderAdapter(api), isEnabledByDefault = ext.isEnabled)
                    }
                } else {
                    // Fallback to hardcoded providers for now if not downloaded (or native providers)
                    val provider = when {
                        ext.name.lowercase().contains("archive") -> xyz.mpv.rex.cinehub.extension.providers.ArchiveOrgProvider(ext, client)
                        ext.name.lowercase().contains("bolly") -> xyz.mpv.rex.cinehub.extension.providers.BollyflixProvider(ext, client)
                        else -> xyz.mpv.rex.cinehub.extension.providers.VidsrcProvider()
                    }
                    registry.register(provider, isEnabledByDefault = ext.isEnabled)
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
                val pluginsDir = context.getDir("plugins", Context.MODE_PRIVATE)
                if (!pluginsDir.exists()) pluginsDir.mkdirs()
                
                val pluginFile = File(pluginsDir, "${plugin.internalName}.csx")
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
            
            if (localPath == null) {
                // If it's a native one
                val provider = when {
                    installed.name.lowercase().contains("archive") -> xyz.mpv.rex.cinehub.extension.providers.ArchiveOrgProvider(installed, client)
                    installed.name.lowercase().contains("bolly") -> xyz.mpv.rex.cinehub.extension.providers.BollyflixProvider(installed, client)
                    else -> xyz.mpv.rex.cinehub.extension.providers.VidsrcProvider()
                }
                registry.register(provider, isEnabledByDefault = true)
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed: ${e.message}", e)
            false
        }
    }

    suspend fun uninstallExtension(pkgName: String) = withContext(Dispatchers.IO) {
        try {
            val ext = db.extensionDao().getExtension(pkgName)
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
            // Note: Since a plugin might contain multiple providers, we might need a better way to unregister them
            // The CloudStream MainAPI doesn't have pkgName, so unregistering by pkgName might fail.
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun enableExtension(pkgName: String) = withContext(Dispatchers.IO) {
        db.extensionDao().updateExtensionState(pkgName, true)
    }

    suspend fun disableExtension(pkgName: String) = withContext(Dispatchers.IO) {
        db.extensionDao().updateExtensionState(pkgName, false)
    }
}
INNER_EOF
