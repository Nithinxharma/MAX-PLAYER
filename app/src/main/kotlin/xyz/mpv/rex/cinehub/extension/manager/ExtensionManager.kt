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
import xyz.mpv.rex.cinehub.extension.api.CloudstreamMainApiAdapter
import xyz.mpv.rex.cinehub.extension.api.MainApiProviderAdapter
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.PluginUpdateInfo
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.lang.reflect.Constructor
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * ExtensionManager coordinates installed extensions, life-cycles,
 * class discovery, plugin instantiation, and provider registrations.
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

    // Diagnostic tracking metrics
    private val _installedExtensionsCount = MutableStateFlow(0)
    val installedExtensionsCount = _installedExtensionsCount.asStateFlow()

    private val _pluginFilesFoundCount = MutableStateFlow(0)
    val pluginFilesFoundCount = _pluginFilesFoundCount.asStateFlow()

    private val _successfullyLoadedPluginsCount = MutableStateFlow(0)
    val successfullyLoadedPluginsCount = _successfullyLoadedPluginsCount.asStateFlow()

    private val _failedPluginLoadsCount = MutableStateFlow(0)
    val failedPluginLoadsCount = _failedPluginLoadsCount.asStateFlow()

    private val loadedPluginInstances = mutableListOf<Any>()
    val loadedPluginCount: Int get() = loadedPluginInstances.size

    init {
        Log.i("ExtensionManager", "INSTANCE_IDENTITY: ExtensionManager initialized. identityHashCode=${System.identityHashCode(this)}, ProviderRegistry.identityHashCode=${System.identityHashCode(registry)}, APIHolder.identityHashCode=${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
        if (!extensionDir.exists()) extensionDir.mkdirs()

        // Immediate reactive bridge: when any plugin registers MainAPI, instantly register in ProviderRegistry
        com.lagradost.cloudstream3.APIHolder.onApiAddedListener = { api ->
            val adapter = CloudstreamMainApiAdapter(api)
            registry.register(adapter, isEnabledByDefault = true)
            Log.i("ExtensionManager", "Reactively registered provider to ProviderRegistry: ${api.name}")
        }
        com.lagradost.cloudstream3.APIHolder.onApiRemovedListener = { api ->
            registry.unregister(api.name)
            Log.i("ExtensionManager", "Reactively unregistered provider from ProviderRegistry: ${api.name}")
        }

        scope.launch {
            loadInstalledExtensions()
        }
    }

    fun getAllInstalledExtensions(): Flow<List<InstalledExtension>> {
        return db.extensionDao().getAllInstalledExtensions()
    }

    suspend fun loadInstalledExtensions() = withContext(Dispatchers.IO) {
        Log.i("ExtensionManager", "INSTANCE_IDENTITY: loadInstalledExtensions called on ExtensionManager@${System.identityHashCode(this)} with ProviderRegistry@${System.identityHashCode(registry)} and APIHolder@${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}")
        val installedExts = db.extensionDao().getAllInstalledExtensionsSync()
        _installedExtensionsCount.value = installedExts.size
        _pluginFilesFoundCount.value = 0
        _successfullyLoadedPluginsCount.value = 0
        _failedPluginLoadsCount.value = 0
        loadedPluginInstances.clear()

        Log.i("ExtensionManager", "EXTENSION_LOAD: Beginning load of ${installedExts.size} installed extensions from database...")

        for (ext in installedExts) {
            loadExtensionFromDisk(ext)
        }

        // Also enumerate any .cs3 files on disk that might not be recorded in database or were placed manually
        val diskDirs = listOf(extensionDir, File(context.filesDir, "cloudstream_plugins"))
        val loadedFilePaths = loadedPluginInstances.mapNotNull { 
            if (it is com.lagradost.cloudstream3.plugins.BasePlugin) it.filename 
            else runCatching { it.javaClass.getMethod("getFilename").invoke(it) as? String }.getOrNull() 
        }.toSet()
        for (dir in diskDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { f -> f.extension.equals("cs3", ignoreCase = true) || f.extension.equals("zip", ignoreCase = true) } ?: emptyArray()
                for (file in files) {
                    if (!loadedFilePaths.contains(file.absolutePath)) {
                        val basePkg = file.nameWithoutExtension
                        val existing = installedExts.firstOrNull { it.pkgName.equals(basePkg, ignoreCase = true) || it.localFilePath == file.absolutePath }
                        if (existing == null) {
                            Log.i("ExtensionManager", "EXTENSION_LOAD: Found unindexed .cs3 file on disk: ${file.name}, loading it...")
                            val ext = InstalledExtension(
                                pkgName = basePkg,
                                name = basePkg,
                                version = "1.0.0",
                                versionCode = 1,
                                localFilePath = file.absolutePath,
                                isEnabled = true
                            )
                            loadExtensionFromDisk(ext)
                        }
                    }
                }
            }
        }

        // Trigger afterPluginsLoaded() on all loaded BasePlugin instances
        for (plugin in loadedPluginInstances) {
            if (plugin is com.lagradost.cloudstream3.plugins.BasePlugin) {
                runCatching {
                    plugin.afterPluginsLoaded()
                }.onFailure {
                    Log.w("ExtensionManager", "Error in afterPluginsLoaded for ${plugin.filename}: ${it.message}")
                }
            } else {
                runCatching {
                    plugin.javaClass.methods.firstOrNull { it.name == "afterPluginsLoaded" && it.parameterTypes.isEmpty() }?.invoke(plugin)
                }
            }
        }

        // Ensure all APIHolder providers are synchronized into ProviderRegistry matching db enabled status
        val allApis = com.lagradost.cloudstream3.APIHolder.allProviders.toList()
        for (api in allApis) {
            val matchingExt = installedExts.firstOrNull { it.pkgName.contains(api.name, ignoreCase = true) || api.name.contains(it.pkgName, ignoreCase = true) }
            val isEnabled = matchingExt?.isEnabled ?: true
            val adapter = CloudstreamMainApiAdapter(api)
            registry.register(adapter, isEnabledByDefault = isEnabled)
        }

        Log.i("ExtensionManager", "Extension loading complete: " +
                "${_pluginFilesFoundCount.value} files found, " +
                "${_successfullyLoadedPluginsCount.value} plugins loaded, " +
                "${com.lagradost.cloudstream3.APIHolder.allProviders.size} APIs in APIHolder, " +
                "${registry.getAllProviders().size} registered providers, " +
                "${registry.getEnabledProviders().size} enabled providers.")
        Log.i("ExtensionManager", "INSTANCE_IDENTITY: ExtensionManager@${System.identityHashCode(this)} dump: APIHolder@${System.identityHashCode(com.lagradost.cloudstream3.APIHolder)}.providers.size=${com.lagradost.cloudstream3.APIHolder.allProviders.size}, ProviderRegistry@${System.identityHashCode(registry)}.registeredProviders.size=${registry.registeredProviders.value.size}, ProviderRegistry.activeProviders.size=${registry.activeProviders.value.size}")
    }

    private fun extractClassNamesFromZip(file: File): List<String> {
        val classNames = mutableListOf<String>()
        if (!file.exists()) return classNames
        runCatching {
            ZipFile(file).use { zip ->
                // Search for manifest.json, make.json, or plugin.json case-insensitively across any path
                val entry = zip.entries().asSequence().firstOrNull {
                    val name = it.name.substringAfterLast('/')
                    name.equals("manifest.json", ignoreCase = true) ||
                    name.equals("make.json", ignoreCase = true) ||
                    name.equals("plugin.json", ignoreCase = true)
                }

                if (entry != null) {
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 6: manifest.json content for ${file.name}:\n$text")
                    val json = JSONObject(text)
                    
                    val mainClass = json.optString("pluginClassName", 
                        json.optString("pluginClass", 
                            json.optString("mainClass", 
                                json.optString("class", 
                                    json.optString("plugin", 
                                        json.optString("entrypoint", ""))))))
                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 7: pluginClassName from manifest for ${file.name}: '$mainClass'")
                    if (mainClass.isNotBlank()) {
                        classNames.add(mainClass.trim())
                    }

                    // Array of classes
                    val classesArr = json.optJSONArray("classes")
                    if (classesArr != null) {
                        for (i in 0 until classesArr.length()) {
                            val cName = classesArr.optString(i).trim()
                            if (cName.isNotBlank() && !classNames.contains(cName)) {
                                classNames.add(cName)
                            }
                        }
                    } else if (json.has("classes") && json.optString("classes").isNotBlank()) {
                        json.optString("classes").split(",").forEach {
                            val c = it.trim()
                            if (c.isNotBlank() && !classNames.contains(c)) classNames.add(c)
                        }
                    }

                    // Array of providers or plugins
                    val providersArr = json.optJSONArray("providers") ?: json.optJSONArray("plugins")
                    if (providersArr != null) {
                        for (i in 0 until providersArr.length()) {
                            val item = providersArr.opt(i)
                            if (item is String && item.isNotBlank() && !classNames.contains(item.trim())) {
                                classNames.add(item.trim())
                            } else if (item is JSONObject) {
                                val c = item.optString("class", item.optString("className", "")).trim()
                                if (c.isNotBlank() && !classNames.contains(c)) classNames.add(c)
                            }
                        }
                    }
                } else {
                    Log.w("ExtensionManager", "EXTENSION_AUDIT: Step 6: No manifest.json/make.json/plugin.json found in archive ${file.name}")
                    Log.w("ExtensionManager", "EXTENSION_AUDIT: Step 7: pluginClassName: None (no manifest)")
                }
            }
        }.onFailure {
            Log.w("ExtensionManager", "EXTENSION_AUDIT: Step 5/6: Failed to open archive or parse manifest from ${file.name}: ${it.message}")
        }
        return classNames
    }

    private fun extractClassesFromDex(file: File, optDir: File): List<String> {
        val dexClasses = mutableListOf<String>()
        runCatching {
            // First attempt using dalvik.system.DexFile
            val dexOptPath = File(optDir, "${file.nameWithoutExtension}.dex.opt").absolutePath
            val dexFile = dalvik.system.DexFile.loadDex(
                file.absolutePath,
                dexOptPath,
                0
            )
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                val cName = entries.nextElement()
                if (!cName.contains("$") &&
                    !cName.startsWith("kotlin.") &&
                    !cName.startsWith("kotlinx.") &&
                    !cName.startsWith("java.") &&
                    !cName.startsWith("android.") &&
                    !cName.startsWith("androidx.")
                ) {
                    dexClasses.add(cName)
                }
            }
        }.onFailure { dexErr ->
            // Fallback: parse classes.dex directly from zip archive if DexFile.loadDex is unsupported (e.g. Android 14+ or Robolectric)
            runCatching {
                ZipFile(file).use { zip ->
                    val dexEntry = zip.getEntry("classes.dex")
                    if (dexEntry != null) {
                        val bytes = zip.getInputStream(dexEntry).readBytes()
                        dexClasses.addAll(extractClassNamesFromDexBytes(bytes))
                    }
                }
            }.onFailure {
                Log.d("ExtensionManager", "DexFile scan fallback for ${file.name}: ${dexErr.message}")
            }
        }
        return dexClasses
    }

    private fun extractClassNamesFromDexBytes(dexBytes: ByteArray): List<String> {
        val classNames = mutableListOf<String>()
        try {
            if (dexBytes.size < 0x70) return classNames
            val magic = String(dexBytes, 0, 8)
            if (!magic.startsWith("dex\n")) return classNames

            fun readInt(offset: Int): Int {
                return (dexBytes[offset].toInt() and 0xFF) or
                        ((dexBytes[offset + 1].toInt() and 0xFF) shl 8) or
                        ((dexBytes[offset + 2].toInt() and 0xFF) shl 16) or
                        ((dexBytes[offset + 3].toInt() and 0xFF) shl 24)
            }

            val stringIdsSize = readInt(0x38)
            val stringIdsOff = readInt(0x3C)
            val typeIdsSize = readInt(0x40)
            val typeIdsOff = readInt(0x44)
            val classDefsSize = readInt(0x60)
            val classDefsOff = readInt(0x64)

            fun getString(stringIdx: Int): String {
                if (stringIdx < 0 || stringIdx >= stringIdsSize) return ""
                val strOff = readInt(stringIdsOff + stringIdx * 4)
                var pos = strOff
                // ULEB128 utf16_size
                var b: Int
                do {
                    b = dexBytes[pos++].toInt() and 0xFF
                } while ((b and 0x80) != 0)
                // Read null-terminated modified UTF-8
                val start = pos
                while (pos < dexBytes.size && dexBytes[pos] != 0.toByte()) {
                    pos++
                }
                return String(dexBytes, start, pos - start, Charsets.UTF_8)
            }

            fun getTypeName(typeIdx: Int): String {
                if (typeIdx < 0 || typeIdx >= typeIdsSize) return ""
                val descriptorIdx = readInt(typeIdsOff + typeIdx * 4)
                return getString(descriptorIdx)
            }

            for (i in 0 until classDefsSize) {
                val offset = classDefsOff + i * 32
                val classIdx = readInt(offset)
                val descriptor = getTypeName(classIdx)
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    val className = descriptor.substring(1, descriptor.length - 1).replace('/', '.')
                    if (!className.contains("$") &&
                        !className.startsWith("kotlin.") &&
                        !className.startsWith("kotlinx.") &&
                        !className.startsWith("java.") &&
                        !className.startsWith("android.") &&
                        !className.startsWith("androidx.")
                    ) {
                        classNames.add(className)
                    }
                }
            }
        } catch (_: Throwable) {}
        return classNames
    }

    private fun instantiateClass(clazz: Class<*>, pkgName: String): Any? {
        // 1. Try public/declared 0-arg constructor
        try {
            val constructor: Constructor<*> = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            val obj = constructor.newInstance()
            Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 10: Plugin instance created for ${clazz.name} via 0-arg constructor")
            return obj
        } catch (e: Throwable) {
            Log.d("ExtensionManager", "0-arg constructor failed for ${clazz.name}: ${e.message}")
        }

        // 2. Try Kotlin object singleton INSTANCE field
        try {
            val field = clazz.getField("INSTANCE")
            field.isAccessible = true
            val obj = field.get(null)
            if (obj != null) {
                Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 10: Plugin instance created for ${clazz.name} via INSTANCE field")
                return obj
            }
        } catch (_: Throwable) {}

        try {
            val field = clazz.getDeclaredField("INSTANCE")
            field.isAccessible = true
            val obj = field.get(null)
            if (obj != null) {
                Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 10: Plugin instance created for ${clazz.name} via declared INSTANCE field")
                return obj
            }
        } catch (_: Throwable) {}

        // 3. Try constructor taking Context
        for (constructor in clazz.declaredConstructors) {
            try {
                constructor.isAccessible = true
                if (constructor.parameterTypes.isEmpty()) {
                    val obj = constructor.newInstance()
                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 10: Plugin instance created for ${clazz.name} via declared constructor")
                    return obj
                } else if (constructor.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(constructor.parameterTypes[0])) {
                    val obj = constructor.newInstance(context)
                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 10: Plugin instance created for ${clazz.name} via Context constructor")
                    return obj
                }
            } catch (e: Throwable) {
                Log.d("ExtensionManager", "Constructor invocation failed for ${clazz.name}: ${e.message}")
            }
        }
        Log.e("ExtensionManager", "EXTENSION_AUDIT: Step 10: FAILED: Could not create plugin instance for ${clazz.name} (package: $pkgName)")
        return null
    }

    private fun loadExtensionFromDisk(ext: InstalledExtension) {
        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 1: Read extension database row: pkgName='${ext.pkgName}', name='${ext.name}', version=${ext.version}, isEnabled=${ext.isEnabled}, localFilePath='${ext.localFilePath}', classesFile='${ext.classesFile}'")
        var localPath = ext.localFilePath
        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 2: localFilePath: '$localPath'")
        var file = localPath?.let { File(it) }

        val initialFileExists = file?.exists() == true
        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 3: Verify file exists: $initialFileExists (path='$localPath')")

        if (file == null || !file.exists()) {
            val candidateFiles = listOfNotNull(
                File(extensionDir, "${ext.pkgName}.cs3"),
                File(extensionDir, "${ext.pkgName}"),
                File(context.filesDir, "cloudstream_plugins/${ext.pkgName}.cs3"),
                File(context.filesDir, "cloudstream_plugins/${ext.pkgName}"),
                extensionDir.listFiles()?.firstOrNull { 
                    it.name.contains(ext.pkgName, ignoreCase = true) || 
                    (ext.name.isNotBlank() && it.name.contains(ext.name.replace(" ", ""), ignoreCase = true))
                },
                File(context.filesDir, "cloudstream_plugins").listFiles()?.firstOrNull { 
                    it.name.contains(ext.pkgName, ignoreCase = true) || 
                    (ext.name.isNotBlank() && it.name.contains(ext.name.replace(" ", ""), ignoreCase = true))
                }
            )
            val found = candidateFiles.firstOrNull { it.exists() && it.isFile }
            if (found != null) {
                file = found
                localPath = found.absolutePath
                Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 3: Resolved candidate file exists: true (path='$localPath')")
            }
        }

        if (file == null || !file.exists()) {
            Log.e("ExtensionManager", "EXTENSION_AUDIT: Step 3: FAILED: File does not exist for package '${ext.pkgName}' at '$localPath'")
            Log.w("ExtensionManager", "EXTENSION_LOAD: Extension file not found on disk for ${ext.pkgName} (path=$localPath)")
            _failedPluginLoadsCount.value++
            return
        }

        val fileSize = file.length()
        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 4: File size: $fileSize bytes (${file.name})")
        Log.i("ExtensionManager", "EXTENSION_LOAD: Found extension file: ${file.absolutePath} (size: $fileSize bytes)")
        _pluginFilesFoundCount.value++
        var loadSuccess = false

        try {
            Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 5: Opening archive: ${file.absolutePath}")
            val optDir = File(context.codeCacheDir, "cloudstream_dex_${ext.pkgName}").apply { mkdirs() }
            
            // Original CloudStream PluginManager uses PathClassLoader with context.classLoader as parent
            val classLoader: ClassLoader = try {
                dalvik.system.PathClassLoader(file.absolutePath, context.classLoader)
            } catch (pclErr: Throwable) {
                dalvik.system.DexClassLoader(
                    file.absolutePath,
                    optDir.absolutePath,
                    null,
                    context.classLoader
                )
            }

            Log.i("ExtensionManager", "DEX_LOAD: Archive path: ${file.absolutePath}")
            Log.i("ExtensionManager", "DEX_LOAD: Optimized directory: ${optDir.absolutePath}")
            Log.i("ExtensionManager", "DEX_LOAD: Parent classloader: ${context.classLoader}")

            val classNames = mutableListOf<String>()

            // 1. Inspect manifest.json / make.json / plugin.json from zip archive (Steps 6 & 7 executed inside)
            classNames.addAll(extractClassNamesFromZip(file))

            // 2. Also check classesFile recorded during install
            ext.classesFile?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it.contains(".") }?.forEach {
                if (!classNames.contains(it)) classNames.add(it)
            }

            // 3. Extract and print every class discovered in classes.dex (Step 8)
            val discoveredDexClasses = extractClassesFromDex(file, optDir)
            Log.i("ExtensionManager", "DEX_LOAD: DexFile entries count: ${discoveredDexClasses.size}")
            Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 8: Discovered ${discoveredDexClasses.size} classes in classes.dex for ${ext.pkgName}: $discoveredDexClasses")
            for (dexC in discoveredDexClasses) {
                if (!classNames.contains(dexC)) classNames.add(dexC)
            }

            Log.i("ExtensionManager", "EXTENSION_LOAD: Candidate classes to load for ${ext.pkgName}: $classNames")

            // If classes were extracted, self-heal database record
            if (classNames.isNotEmpty() && ext.classesFile != classNames.joinToString(", ")) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        db.extensionDao().insertExtension(
                            ext.copy(
                                localFilePath = file.absolutePath,
                                classesFile = classNames.joinToString(", ")
                            )
                        )
                    }
                }
            }

            val beforeApis = com.lagradost.cloudstream3.APIHolder.allProviders.toList()

            for (className in classNames) {
                try {
                    Log.i("ExtensionManager", "DEX_LOAD: Attempting class load: $className")
                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 9: Verifying plugin class can be loaded: $className")
                    val clazz = classLoader.loadClass(className)
                    Log.i("ExtensionManager", "DEX_LOAD: Class loaded successfully: $className")
                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 9: Successfully loaded class $className for ${ext.pkgName}")
                    val instance = instantiateClass(clazz, ext.pkgName)

                    if (instance != null) {
                        when (instance) {
                            is com.lagradost.cloudstream3.plugins.BasePlugin -> {
                                instance.filename = file.absolutePath
                                loadedPluginInstances.add(instance)
                                if (instance is com.lagradost.cloudstream3.plugins.Plugin) {
                                    runCatching {
                                        val assets = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                                        val addAssetPath = android.content.res.AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                                        addAssetPath.invoke(assets, file.absolutePath)
                                        instance.resources = android.content.res.Resources(
                                            assets,
                                            context.resources.displayMetrics,
                                            context.resources.configuration
                                        )
                                    }
                                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoking plugin.load(context) for $className (package: ${ext.pkgName})")
                                    instance.load(context)
                                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoked plugin.load(context) successfully for $className")
                                } else {
                                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoking plugin.load() for $className (package: ${ext.pkgName})")
                                    instance.load()
                                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoked plugin.load() successfully for $className")
                                }
                                loadSuccess = true
                                Log.i("ExtensionManager", "Successfully executed BasePlugin: $className from ${file.name}")
                            }
                            is com.lagradost.cloudstream3.MainAPI -> {
                                instance.sourcePlugin = file.absolutePath
                                com.lagradost.cloudstream3.APIHolder.addPlugin(instance)
                                registry.register(CloudstreamMainApiAdapter(instance), isEnabledByDefault = ext.isEnabled)
                                loadSuccess = true
                                Log.i("ExtensionManager", "Registered direct MainAPI instance: ${instance.name}")
                            }
                            is com.lagradost.cloudstream3.utils.ExtractorApi -> {
                                instance.sourcePlugin = file.absolutePath
                                com.lagradost.cloudstream3.APIHolder.addExtractor(instance)
                                loadSuccess = true
                                Log.i("ExtensionManager", "Registered direct ExtractorApi instance: ${instance.name}")
                            }
                            is CineHubProvider -> {
                                registry.register(instance, isEnabledByDefault = ext.isEnabled)
                                loadSuccess = true
                                Log.i("ExtensionManager", "Registered direct CineHubProvider instance: ${instance.name}")
                            }
                            is xyz.mpv.rex.cinehub.extension.api.MainAPI -> {
                                registry.register(MainApiProviderAdapter(instance), isEnabledByDefault = ext.isEnabled)
                                loadSuccess = true
                                Log.i("ExtensionManager", "Registered legacy MainAPI instance: ${instance.name}")
                            }
                            else -> {
                                // Reflection fallback for load(Context) / load() methods
                                try {
                                    val loadMethodWithContext = clazz.methods.firstOrNull { it.name == "load" && it.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(it.parameterTypes[0]) }
                                    val loadMethodNoArgs = clazz.methods.firstOrNull { it.name == "load" && it.parameterTypes.isEmpty() }
                                    if (loadMethodWithContext != null) {
                                        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoking plugin.load(context) (reflection) for $className")
                                        loadMethodWithContext.invoke(instance, context)
                                        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoked plugin.load(context) successfully (reflection) for $className")
                                        loadedPluginInstances.add(instance)
                                        loadSuccess = true
                                    } else if (loadMethodNoArgs != null) {
                                        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoking plugin.load() (reflection) for $className")
                                        loadMethodNoArgs.invoke(instance)
                                        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoked plugin.load() successfully (reflection) for $className")
                                        loadedPluginInstances.add(instance)
                                        loadSuccess = true
                                    }
                                } catch (loadErr: Throwable) {
                                    Log.e("ExtensionManager", "EXTENSION_AUDIT: Step 11: FAILED: plugin.load invocation failed for $className (package: ${ext.pkgName})", loadErr)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    if (e is ClassNotFoundException || e.cause is ClassNotFoundException) {
                        Log.e("ExtensionManager", "DEX_LOAD: ClassNotFoundException: Failed loading class $className from ${file.name}: ${e.message}")
                    }
                    Log.e("ExtensionManager", "EXTENSION_AUDIT: Step 9/10/11: FAILED for class $className (package: ${ext.pkgName}): ${e.message}", e)
                    Log.w("ExtensionManager", "Failed to load class $className for extension ${ext.pkgName}: ${e.message}")
                }
            }

            // Synchronize newly added or updated CloudStream APIs from this extension into ProviderRegistry
            val currentApis = com.lagradost.cloudstream3.APIHolder.allProviders.toList()
            for (api in currentApis) {
                if (api.sourcePlugin == file.absolutePath || !beforeApis.contains(api) || currentApis.size > beforeApis.size) {
                    if (api.sourcePlugin == null) api.sourcePlugin = file.absolutePath
                    registry.register(CloudstreamMainApiAdapter(api), isEnabledByDefault = ext.isEnabled)
                    loadSuccess = true
                }
            }

            if (loadSuccess) {
                _successfullyLoadedPluginsCount.value++
            } else {
                _failedPluginLoadsCount.value++
                Log.w("ExtensionManager", "No providers or plugins could be registered from ${file.name}")
            }
        } catch (e: Throwable) {
            _failedPluginLoadsCount.value++
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
                        if (discoveredClasses.isEmpty()) {
                            val optDir = File(context.codeCacheDir, "opt_${plugin.internalName}").apply { mkdirs() }
                            discoveredClasses = extractClassesFromDex(targetFile, optDir)
                        }
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
            Log.e("ExtensionManager", "Failed installing extension ${plugin.name}", e)
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
            val file = File(extensionDir, "$pkgName.cs3")
            com.lagradost.cloudstream3.APIHolder.removePluginsBySource(file.absolutePath)
            com.lagradost.cloudstream3.APIHolder.removePluginsBySource(file.name)
            registry.unregister(pkgName)
            registry.unregister("cs3_${pkgName.lowercase()}")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Failed uninstalling extension $pkgName", e)
        }
    }

    suspend fun toggleExtension(pkgName: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        db.extensionDao().updateExtensionState(pkgName, isEnabled)
        registry.setProviderEnabled(pkgName, isEnabled)
        registry.setProviderEnabled("cs3_${pkgName.lowercase()}", isEnabled)
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
