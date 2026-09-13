package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.extension.api.MainAPI
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.net.URL

class ExtensionManager(private val context: Context, private val db: MpvExDatabase) {

    private val _activeProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    val activeProviders = _activeProviders.asStateFlow()

    private val extensionDir = File(context.filesDir, "cinehub_extensions")
    private val optimizedDir = File(context.filesDir, "cinehub_extensions_opt")

    init {
        if (!extensionDir.exists()) extensionDir.mkdirs()
        if (!optimizedDir.exists()) optimizedDir.mkdirs()
    }

    suspend fun loadEnabledExtensions() = withContext(Dispatchers.IO) {
        val enabledExts = db.extensionDao().getEnabledExtensionsSync()
        val providers = mutableListOf<MainAPI>()

        for (ext in enabledExts) {
            try {
                if (ext.localFilePath != null && File(ext.localFilePath).exists()) {
                    val loader = DexClassLoader(
                        ext.localFilePath,
                        optimizedDir.absolutePath,
                        null,
                        context.classLoader
                    )
                    
                    if (ext.classesFile != null) {
                        val classes = ext.classesFile.split(",")
                        for (className in classes) {
                            if (className.isNotBlank()) {
                                val loadedClass = loader.loadClass(className)
                                val providerInstance = loadedClass.getDeclaredConstructor().newInstance() as MainAPI
                                providers.add(providerInstance)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Safemode: disable if crash on load
                db.extensionDao().updateExtensionState(ext.pkgName, false)
            }
        }
        
        _activeProviders.value = providers
    }

    suspend fun addRepository(url: String, name: String) = withContext(Dispatchers.IO) {
        db.extensionDao().insertRepository(
            xyz.mpv.rex.cinehub.extension.model.ExtensionRepo(
                url = url,
                name = name,
                lastSync = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeRepository(url: String) = withContext(Dispatchers.IO) {
        db.extensionDao().deleteRepository(
            xyz.mpv.rex.cinehub.extension.model.ExtensionRepo(url = url, name = "")
        )
    }

    // Dummy logic to demonstrate fetching plugins from repo.json
    suspend fun fetchAvailablePlugins(repoUrl: String): List<AvailablePlugin> = withContext(Dispatchers.IO) {
        try {
            // In a real implementation this fetches repoUrl
            // e.g. val json = URL(repoUrl).readText()
            // val repo = Gson().fromJson(json, RepoResponse::class.java)
            // return repo.pluginLists
            
            // For now, simulate an empty list or parse real JSON if available
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun installExtension(plugin: AvailablePlugin): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(extensionDir, "${plugin.internalName}.cs3")
            // URL(plugin.url).openStream().use { input ->
            //     file.outputStream().use { output ->
            //         input.copyTo(output)
            //     }
            // }
            
            val installed = InstalledExtension(
                pkgName = plugin.internalName,
                name = plugin.name,
                version = plugin.version,
                versionCode = plugin.versionCode,
                description = plugin.description,
                iconUrl = plugin.iconUrl,
                repositoryUrl = plugin.tvUrl, 
                isEnabled = true,
                localFilePath = file.absolutePath,
                classesFile = plugin.classes.joinToString(",")
            )
            db.extensionDao().insertExtension(installed)
            loadEnabledExtensions()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}

data class AvailablePlugin(
    val name: String,
    val internalName: String,
    val version: String,
    val versionCode: Int,
    val description: String?,
    val url: String,
    val tvUrl: String?,
    val iconUrl: String?,
    val classes: List<String>
)
