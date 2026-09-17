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
import org.json.JSONObject
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.api.MainAPI
import xyz.mpv.rex.cinehub.extension.api.MainApiProviderAdapter
import xyz.mpv.rex.cinehub.extension.api.CloudstreamMainApiAdapter
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.PluginUpdateInfo
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.util.zip.ZipFile

/**
 * ExtensionManager coordinates installed extensions, life-cycles,
 * updates, and provider registrations.
 * Only real installed extensions are registered.
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
            loadExtensionFromDisk(ext)
        }
    }

    private fun extractClassNamesFromZip(file: File): List<String> {
        val classNames = mutableListOf<String>()
        if (!file.exists()) return classNames
        runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: zip.getEntry("make.json")
                if (entry != null) {
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    val json = JSONObject(text)
                    val mainClass = json.optString("pluginClassName", "")
                    if (mainClass.isNotBlank()) {
                        classNames.add(mainClass)
                    }
                    val classesArr = json.optJSONArray("classes")
                    if (classesArr != null) {
                        for (i in 0 until classesArr.length()) {
                            val cName = classesArr.optString(i)
                            if (cName.isNotBlank() && !classNames.contains(cName)) {
                                classNames.add(cName)
                            }
                        }
                    }
                }
            }
        }.onFailure {
            Log.w("ExtensionManager", "Failed to parse manifest from zip ${file.name}: ${it.message}")
        }
        return classNames
    }

    private fun loadExtensionFromDisk(ext: InstalledExtension) {
        val localPath = ext.localFilePath ?: return
        val file = File(localPath)
        if (!file.exists()) return

        try {
            val optDir = File(context.codeCacheDir, "opt_${ext.pkgName}").apply { mkdirs() }
            val classLoader = dalvik.system.DexClassLoader(
                file.absolutePath,
                optDir.absolutePath,
                null,
                context.classLoader
            )

            val classNames = mutableListOf<String>()

            // 1. Inspect manifest.json / make.json directly from the zip archive
            classNames.addAll(extractClassNamesFromZip(file))

            // 2. Also check classesFile recorded during install, filtering out any author names or non-class entries
            ext.classesFile?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it.contains(".") }?.forEach {
                if (!classNames.contains(it)) classNames.add(it)
            }

            // If classesFile was previously corrupted and classNames were extracted from zip, self-heal database record
            if (classNames.isNotEmpty() && ext.classesFile != classNames.joinToString(", ")) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        db.extensionDao().insertExtension(ext.copy(classesFile = classNames.joinToString(", ")))
                    }
                }
            }

            for (className in classNames) {
                try {
                    val clazz = classLoader.loadClass(className)
                    val instance = clazz.getDeclaredConstructor().newInstance()
                    if (instance is CineHubProvider) {
                        registry.register(instance, isEnabledByDefault = ext.isEnabled)
                    } else if (instance is com.lagradost.cloudstream3.MainAPI) {
                        com.lagradost.cloudstream3.APIHolder.addPlugin(instance)
                        registry.register(CloudstreamMainApiAdapter(instance), isEnabledByDefault = ext.isEnabled)
                    } else if (instance is MainAPI) {
                        registry.register(MainApiProviderAdapter(instance), isEnabledByDefault = ext.isEnabled)
                    }
                } catch (e: Throwable) {
                    Log.w("ExtensionManager", "Failed to load class $className for extension ${ext.pkgName}: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.e("ExtensionManager", "Failed to load extension ${ext.pkgName} from $localPath", e)
        }
    }

    suspend fun installExtension(plugin: AvailablePlugin): Boolean = withContext(Dispatchers.IO) {
        try {
            var localPath: String? = null
            var discoveredClasses: List<String> = emptyList()
            if (plugin.url.isNotBlank()) {
                val targetFile = File(extensionDir, "${plugin.internalName}.cs3")
                val request = Request.Builder().url(plugin.url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        response.body!!.byteStream().use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        localPath = targetFile.absolutePath
                        discoveredClasses = extractClassNamesFromZip(targetFile)
                    }
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
                classesFile = if (discoveredClasses.isNotEmpty()) discoveredClasses.joinToString(", ") else null
            )
            db.extensionDao().insertExtension(installed)

            // Load real runtime extension
            loadExtensionFromDisk(installed)
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
            val file = File(extensionDir, "$pkgName.cs3")
            if (file.exists()) file.delete()
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
