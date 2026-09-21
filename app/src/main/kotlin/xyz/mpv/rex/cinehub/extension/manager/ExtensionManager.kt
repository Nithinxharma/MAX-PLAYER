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
import xyz.mpv.rex.cinehub.extension.model.ExtensionFailureItem
import xyz.mpv.rex.cinehub.extension.model.ExtensionTestBatchReport
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.PluginUpdateInfo
import xyz.mpv.rex.cinehub.extension.util.LanguageUtils
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
        com.lagradost.cloudstream3.AcraApplication.init(context)

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
        val searchDirs = listOfNotNull(
            extensionDir,
            File(context.filesDir, "cloudstream_plugins"),
            File(context.filesDir, "Extensions"),
            context.filesDir,
            context.getExternalFilesDir(null)?.let { File(it, "plugins") },
            File(android.os.Environment.getExternalStorageDirectory(), "Cloudstream3/plugins")
        )
        val loadedFilePaths = loadedPluginInstances.mapNotNull { 
            if (it is com.lagradost.cloudstream3.plugins.BasePlugin) it.filename 
            else runCatching { it.javaClass.getMethod("getFilename").invoke(it) as? String }.getOrNull() 
        }.toSet()
        
        fun collectPluginFiles(dir: File, dest: MutableList<File>) {
            if (!dir.exists()) return
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        collectPluginFiles(file, dest)
                    } else if (file.extension.equals("cs3", ignoreCase = true) || file.extension.equals("zip", ignoreCase = true)) {
                        dest.add(file)
                    }
                }
            }
        }

        val discoveredFiles = mutableListOf<File>()
        for (dir in searchDirs) {
            collectPluginFiles(dir, discoveredFiles)
        }

        for (file in discoveredFiles.distinctBy { it.absolutePath }) {
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

    fun prepareExecutablePluginFile(sourceFile: File, pkgName: String): File {
        // Android 14+ ART Security Enforcement: Writable DEX/JAR/ZIP files passed to ClassLoader are prohibited.
        runCatching { sourceFile.setReadOnly() }

        return try {
            val execDir = File(context.codeCacheDir, "plugin_exec").apply { mkdirs() }
            val execFile = File(execDir, "${sourceFile.nameWithoutExtension}_exec.cs3")
            if (execFile.exists()) {
                execFile.setWritable(true)
                execFile.delete()
            }
            sourceFile.inputStream().use { input ->
                execFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            execFile.setReadOnly() // Strictly read-only for Android ART ClassLoaders
            execFile
        } catch (e: Throwable) {
            Log.w("ExtensionManager", "Could not create execution copy in codeCacheDir: ${e.message}")
            runCatching { sourceFile.setReadOnly() }
            sourceFile
        }
    }

    private fun extractClassesFromDex(file: File, optDir: File): List<String> {
        val dexClasses = mutableListOf<String>()
        val execFile = prepareExecutablePluginFile(file, file.nameWithoutExtension)
        runCatching {
            // First attempt using dalvik.system.DexFile on read-only executable file
            val dexOptPath = File(optDir, "${execFile.nameWithoutExtension}.dex.opt").absolutePath
            @Suppress("DEPRECATION")
            val dexFile = dalvik.system.DexFile.loadDex(
                execFile.absolutePath,
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
            dexFile.close()
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
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            Log.d("ExtensionManager", "0-arg constructor failed for ${clazz.name}: ${cause.javaClass.simpleName} - ${cause.message}")
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

        // 3. Try all declared constructors with sorted parameter matching
        val constructors = clazz.declaredConstructors.sortedBy { it.parameterTypes.size }
        for (constructor in constructors) {
            try {
                constructor.isAccessible = true
                val paramTypes = constructor.parameterTypes
                val args = Array(paramTypes.size) { idx ->
                    val type = paramTypes[idx]
                    when {
                        Context::class.java.isAssignableFrom(type) -> context
                        android.content.res.Resources::class.java.isAssignableFrom(type) -> context.resources
                        type == java.lang.String::class.java -> ""
                        type == java.lang.Integer.TYPE || type == java.lang.Integer::class.java -> 0
                        type == java.lang.Long.TYPE || type == java.lang.Long::class.java -> 0L
                        type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java -> false
                        type == java.lang.Float.TYPE || type == java.lang.Float::class.java -> 0f
                        type == java.lang.Double.TYPE || type == java.lang.Double::class.java -> 0.0
                        else -> null
                    }
                }
                val obj = constructor.newInstance(*args)
                if (obj != null) {
                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 10: Plugin instance created for ${clazz.name} via constructor(${paramTypes.joinToString { it.simpleName }})")
                    return obj
                }
            } catch (e: Throwable) {
                val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
                Log.d("ExtensionManager", "Constructor(${constructor.parameterTypes.joinToString { it.simpleName }}) failed for ${clazz.name}: ${cause.javaClass.simpleName} - ${cause.message}")
            }
        }

        // 4. Try sun.misc.Unsafe allocateInstance
        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = theUnsafeField.get(null)
            val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
            val obj = allocateInstanceMethod.invoke(unsafe, clazz)
            if (obj != null) {
                Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 10: Plugin instance created for ${clazz.name} via Unsafe.allocateInstance")
                return obj
            }
        } catch (e: Throwable) {
            Log.d("ExtensionManager", "Unsafe allocation failed for ${clazz.name}: ${e.message}")
        }

        Log.e("ExtensionManager", "EXTENSION_AUDIT: Step 10: FAILED: Could not create plugin instance for ${clazz.name} (package: $pkgName)")
        return null
    }

    fun loadExtensionFromDisk(ext: InstalledExtension): Boolean {
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
            return false
        }

        val fileSize = file.length()
        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 4: File size: $fileSize bytes (${file.name})")
        Log.i("ExtensionManager", "EXTENSION_LOAD: Found extension file: ${file.absolutePath} (size: $fileSize bytes)")
        _pluginFilesFoundCount.value++
        var loadSuccess = false

        try {
            Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 5: Opening archive: ${file.absolutePath}")
            val optDir = File(context.codeCacheDir, "cloudstream_dex_${ext.pkgName}").apply { mkdirs() }
            val execFile = prepareExecutablePluginFile(file, ext.pkgName)
            
            // CloudStream PluginManager uses PathClassLoader on read-only executable file with context.classLoader as parent
            val classLoader: ClassLoader = try {
                dalvik.system.PathClassLoader(execFile.absolutePath, context.classLoader)
            } catch (pclErr: Throwable) {
                dalvik.system.DexClassLoader(
                    execFile.absolutePath,
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
                                    val pluginCtx = xyz.mpv.rex.App.currentActivity ?: context
                                    Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 11: Invoking plugin.load(context) for $className (package: ${ext.pkgName})")
                                    try {
                                        instance.load(pluginCtx)
                                    } catch (t: Throwable) {
                                        Log.w("ExtensionManager", "instance.load(pluginCtx) failed with ${t.message}, retrying with base context or load()")
                                        try {
                                            instance.load(context)
                                        } catch (_: Throwable) {
                                            instance.load()
                                        }
                                    }
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
            return loadSuccess
        } catch (e: Throwable) {
            _failedPluginLoadsCount.value++
            Log.e("ExtensionManager", "Failed to load extension ${ext.pkgName} from $localPath", e)
            return false
        }
    }

    fun getCandidatePluginFiles(): List<File> {
        val searchDirs = listOfNotNull(
            extensionDir,
            File(context.filesDir, "cloudstream_plugins"),
            File(context.filesDir, "Extensions"),
            context.filesDir,
            context.getExternalFilesDir(null)?.let { File(it, "plugins") },
            File(android.os.Environment.getExternalStorageDirectory(), "Cloudstream3/plugins")
        )
        val files = mutableListOf<File>()
        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().maxDepth(2).forEach { f ->
                    if (f.isFile && (f.extension.equals("cs3", ignoreCase = true) || f.extension.equals("zip", ignoreCase = true))) {
                        files.add(f)
                    }
                }
            }
        }
        return files.distinctBy { it.absolutePath }
    }

    fun inspectZipManifest(file: File): Pair<String?, List<String>> {
        var manifestContent: String? = null
        val classes = mutableListOf<String>()
        if (!file.exists()) return Pair(null, emptyList())
        runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull {
                    val name = it.name.substringAfterLast('/')
                    name.equals("manifest.json", ignoreCase = true) ||
                    name.equals("make.json", ignoreCase = true) ||
                    name.equals("plugin.json", ignoreCase = true)
                }
                if (entry != null) {
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    manifestContent = text
                    classes.addAll(extractClassNamesFromZip(file))
                }
            }
        }
        return Pair(manifestContent, classes)
    }

    suspend fun forceReloadExtension(pkgName: String): Boolean = withContext(Dispatchers.IO) {
        val installed = db.extensionDao().getExtension(pkgName)
        val file = findExtensionFile(pkgName, installed?.localFilePath)
        if (file != null) {
            com.lagradost.cloudstream3.APIHolder.removePluginsBySource(file.absolutePath)
            com.lagradost.cloudstream3.APIHolder.removePluginsBySource(file.name)
        }
        registry.unregister(pkgName)
        registry.unregister("cs3_${pkgName.lowercase()}")

        val extToLoad = installed ?: file?.let {
            InstalledExtension(
                pkgName = pkgName,
                name = pkgName,
                version = "1.0.0",
                versionCode = 1,
                localFilePath = it.absolutePath,
                isEnabled = true
            )
        } ?: return@withContext false

        loadExtensionFromDisk(extToLoad)
    }

    fun findExtensionFile(pkgName: String, localPath: String?): File? {
        if (localPath != null) {
            val f = File(localPath)
            if (f.exists() && f.isFile) return f
        }
        val cleanPkg = pkgName.replace(" ", "").replace("_", "").lowercase()
        val dirsToSearch = listOfNotNull(
            extensionDir,
            File(context.filesDir, "cinehub_extensions"),
            File(context.filesDir, "cloudstream_plugins"),
            File(context.codeCacheDir, "plugin_exec"),
            context.filesDir,
            context.cacheDir
        )
        for (dir in dirsToSearch) {
            if (!dir.exists() || !dir.isDirectory) continue
            val direct = File(dir, "$pkgName.cs3")
            if (direct.exists() && direct.isFile) return direct
            val directNoExt = File(dir, pkgName)
            if (directNoExt.exists() && directNoExt.isFile) return directNoExt
            
            val files = dir.listFiles() ?: continue
            val found = files.firstOrNull { file ->
                val fClean = file.nameWithoutExtension.replace(" ", "").replace("_", "").lowercase()
                fClean == cleanPkg ||
                fClean.contains(cleanPkg) ||
                cleanPkg.contains(fClean) ||
                fClean.replace("provider", "").replace("plugin", "") == cleanPkg.replace("provider", "").replace("plugin", "")
            }
            if (found != null && found.isFile) return found
        }
        return null
    }

    suspend fun forceDexAudit(file: File): xyz.mpv.rex.cinehub.extension.model.DexAuditReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<String>()
        val classAudits = mutableListOf<xyz.mpv.rex.cinehub.extension.model.DexClassLoadAuditItem>()
        var zipEntriesCount = 0
        var hasManifest = false
        var manifestContent: String? = null
        var manifestPluginClass: String? = null
        val classesFromManifest = mutableListOf<String>()
        val classesFromDex = mutableListOf<String>()
        var registeredProvidersCount = 0
        var registeredExtractorsCount = 0
        var isSuccess = false
        var errorSummary: String? = null

        logs.add("[Step 1] Verifying file: ${file.absolutePath} (size: ${if (file.exists()) file.length() else 0} bytes)")

        if (!file.exists()) {
            logs.add("[Step 1 ERROR] File does not exist at ${file.absolutePath}")
            return@withContext xyz.mpv.rex.cinehub.extension.model.DexAuditReport(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = 0L,
                fileExists = false,
                zipEntriesCount = 0,
                hasManifest = false,
                manifestContent = null,
                manifestPluginClass = null,
                classesFromManifest = emptyList(),
                classesFromDex = emptyList(),
                classLoadAudits = emptyList(),
                registeredProvidersCount = 0,
                registeredExtractorsCount = 0,
                totalDurationMs = System.currentTimeMillis() - startTime,
                success = false,
                errorSummary = "File not found",
                logs = logs
            )
        }

        runCatching {
            ZipFile(file).use { zip ->
                zipEntriesCount = zip.size()
                logs.add("[Step 2] Opened ZIP archive. Total entries: $zipEntriesCount")
                val manifestEntry = zip.entries().asSequence().firstOrNull {
                    val n = it.name.substringAfterLast('/')
                    n.equals("manifest.json", ignoreCase = true) ||
                    n.equals("make.json", ignoreCase = true) ||
                    n.equals("plugin.json", ignoreCase = true)
                }
                if (manifestEntry != null) {
                    hasManifest = true
                    val txt = zip.getInputStream(manifestEntry).bufferedReader().readText()
                    manifestContent = txt
                    logs.add("[Step 3] Manifest found: ${manifestEntry.name}")
                    val json = JSONObject(txt)
                    manifestPluginClass = json.optString("pluginClassName", json.optString("pluginClass", json.optString("mainClass", json.optString("class", ""))))
                    if (!manifestPluginClass.isNullOrBlank()) {
                        classesFromManifest.add(manifestPluginClass!!.trim())
                    }
                    val classesArr = json.optJSONArray("classes")
                    if (classesArr != null) {
                        for (i in 0 until classesArr.length()) {
                            val cName = classesArr.optString(i).trim()
                            if (cName.isNotBlank() && !classesFromManifest.contains(cName)) {
                                classesFromManifest.add(cName)
                            }
                        }
                    }
                } else {
                    logs.add("[Step 3 WARNING] No manifest.json found in archive")
                }
            }
        }.onFailure {
            logs.add("[Step 2/3 ERROR] Failed reading ZIP archive: ${it.message}")
            errorSummary = it.message
        }

        // Dex scan
        val optDir = File(context.codeCacheDir, "opt_audit_${file.nameWithoutExtension}").apply { mkdirs() }
        val dexClasses = extractClassesFromDex(file, optDir)
        classesFromDex.addAll(dexClasses)
        logs.add("[Step 4] Extracted ${dexClasses.size} candidate classes from classes.dex")

        val allCandidateClasses = (classesFromManifest + classesFromDex).distinct()
        logs.add("[Step 5] Total unique candidate classes to inspect: ${allCandidateClasses.size}")

        val execFile = prepareExecutablePluginFile(file, file.nameWithoutExtension)
        val classLoader: ClassLoader = try {
            dalvik.system.PathClassLoader(execFile.absolutePath, context.classLoader)
        } catch (e: Throwable) {
            dalvik.system.DexClassLoader(execFile.absolutePath, optDir.absolutePath, null, context.classLoader)
        }

        val initialApis = com.lagradost.cloudstream3.APIHolder.allProviders.size
        val initialExtractors = com.lagradost.cloudstream3.APIHolder.extractorApis.size

        for (cName in allCandidateClasses) {
            try {
                val clazz = classLoader.loadClass(cName)
                val isInstantiable = clazz.declaredConstructors.any { it.parameterTypes.isEmpty() || (it.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(it.parameterTypes[0])) } ||
                        runCatching { clazz.getField("INSTANCE") }.isSuccess || runCatching { clazz.getDeclaredField("INSTANCE") }.isSuccess
                val resolvedType = when {
                    com.lagradost.cloudstream3.plugins.BasePlugin::class.java.isAssignableFrom(clazz) -> "BasePlugin"
                    com.lagradost.cloudstream3.MainAPI::class.java.isAssignableFrom(clazz) -> "MainAPI"
                    com.lagradost.cloudstream3.utils.ExtractorApi::class.java.isAssignableFrom(clazz) -> "ExtractorApi"
                    CineHubProvider::class.java.isAssignableFrom(clazz) -> "CineHubProvider"
                    else -> clazz.superclass?.simpleName ?: "Object"
                }
                val hasLoad = clazz.methods.any { it.name == "load" }

                classAudits.add(
                    xyz.mpv.rex.cinehub.extension.model.DexClassLoadAuditItem(
                        className = cName,
                        isClassFound = true,
                        isInstantiable = isInstantiable,
                        resolvedType = resolvedType,
                        hasLoadMethod = hasLoad,
                        errorMessage = null
                    )
                )
                logs.add("[Step 6 Class Audit] $cName -> Type: $resolvedType, Instantiable: $isInstantiable, hasLoad: $hasLoad")

                val instance = instantiateClass(clazz, file.nameWithoutExtension)
                if (instance != null) {
                    if (instance is com.lagradost.cloudstream3.plugins.BasePlugin) {
                        instance.filename = file.absolutePath
                        if (instance is com.lagradost.cloudstream3.plugins.Plugin) {
                            runCatching {
                                val assets = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                                val addAssetPath = android.content.res.AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                                addAssetPath.invoke(assets, file.absolutePath)
                                instance.resources = android.content.res.Resources(assets, context.resources.displayMetrics, context.resources.configuration)
                            }
                            instance.load(context)
                        } else {
                            instance.load()
                        }
                        logs.add("[Step 7 Execution] Invoked BasePlugin.load() for $cName successfully")
                    } else if (instance is com.lagradost.cloudstream3.MainAPI) {
                        instance.sourcePlugin = file.absolutePath
                        com.lagradost.cloudstream3.APIHolder.addPlugin(instance)
                        registry.register(CloudstreamMainApiAdapter(instance), isEnabledByDefault = true)
                        logs.add("[Step 7 Execution] Registered MainAPI: ${instance.name}")
                    } else if (instance is com.lagradost.cloudstream3.utils.ExtractorApi) {
                        instance.sourcePlugin = file.absolutePath
                        com.lagradost.cloudstream3.APIHolder.addExtractor(instance)
                        logs.add("[Step 7 Execution] Registered Extractor: ${instance.name}")
                    }
                }
            } catch (err: Throwable) {
                classAudits.add(
                    xyz.mpv.rex.cinehub.extension.model.DexClassLoadAuditItem(
                        className = cName,
                        isClassFound = false,
                        isInstantiable = false,
                        resolvedType = null,
                        hasLoadMethod = false,
                        errorMessage = err.message
                    )
                )
                logs.add("[Step 6 Class Audit FAILED] $cName: ${err.message}")
            }
        }

        registeredProvidersCount = (com.lagradost.cloudstream3.APIHolder.allProviders.size - initialApis).coerceAtLeast(0)
        registeredExtractorsCount = (com.lagradost.cloudstream3.APIHolder.extractorApis.size - initialExtractors).coerceAtLeast(0)
        isSuccess = classAudits.any { it.isClassFound }

        logs.add("[Step 8 Summary] Audit completed in ${System.currentTimeMillis() - startTime}ms. Success: $isSuccess, Providers Registered: $registeredProvidersCount, Extractors: $registeredExtractorsCount")

        xyz.mpv.rex.cinehub.extension.model.DexAuditReport(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            fileExists = true,
            zipEntriesCount = zipEntriesCount,
            hasManifest = hasManifest,
            manifestContent = manifestContent,
            manifestPluginClass = manifestPluginClass,
            classesFromManifest = classesFromManifest,
            classesFromDex = classesFromDex,
            classLoadAudits = classAudits,
            registeredProvidersCount = registeredProvidersCount,
            registeredExtractorsCount = registeredExtractorsCount,
            totalDurationMs = System.currentTimeMillis() - startTime,
            success = isSuccess,
            errorSummary = errorSummary,
            logs = logs
        )
    }

    suspend fun installExtension(plugin: AvailablePlugin): Boolean = withContext(Dispatchers.IO) {
        try {
            var localPath: String? = null
            var discoveredClasses: List<String> = emptyList()
            if (plugin.url.isNotBlank()) {
                val targetFile = File(extensionDir, "${plugin.internalName}.cs3")
                if (targetFile.exists()) {
                    runCatching { targetFile.setWritable(true) }
                }
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
                classesFile = if (discoveredClasses.isNotEmpty()) discoveredClasses.joinToString(", ") else null,
                lang = plugin.lang
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
            if (file.exists()) {
                runCatching { file.setWritable(true) }
                file.delete()
            }
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

    suspend fun testAllInstalledExtensions(
        onProgress: (current: Int, total: Int, currentName: String) -> Unit = { _, _, _ -> }
    ): ExtensionTestBatchReport = withContext(Dispatchers.IO) {
        val installedList = db.extensionDao().getAllInstalledExtensionsSync()
        val total = installedList.size
        val failures = mutableListOf<ExtensionFailureItem>()
        var workingCount = 0

        val repoMap = mutableMapOf<String, String>()
        runCatching {
            val cursor = db.openHelper.readableDatabase.query("SELECT url, name FROM extension_repositories")
            cursor.use {
                while (it.moveToNext()) {
                    val u = it.getString(0)
                    val n = it.getString(1)
                    if (!u.isNullOrBlank()) {
                        repoMap[u] = n ?: u
                    }
                }
            }
        }
        for (preset in RepositoryManager.BUILT_IN_PRESETS) {
            repoMap[preset.url] = preset.name
        }

        for (index in installedList.indices) {
            val ext = installedList[index]
            onProgress(index + 1, total, ext.name)

            val repoName = if (!ext.repositoryUrl.isNullOrBlank()) {
                repoMap[ext.repositoryUrl]
                    ?: repoMap.entries.firstOrNull { ext.repositoryUrl!!.contains(it.key) || it.key.contains(ext.repositoryUrl!!) }?.value
                    ?: ext.repositoryUrl!!
            } else {
                "Local / Sideloaded"
            }

            // Step 1: File existence & readability check
            val file = findExtensionFile(ext.pkgName, ext.localFilePath)
            if (file == null || !file.exists() || file.length() == 0L) {
                failures.add(
                    ExtensionFailureItem(
                        extensionName = ext.name,
                        packageName = ext.pkgName,
                        providerName = "N/A (Package Missing)",
                        repositoryName = repoName,
                        repositoryUrl = ext.repositoryUrl,
                        failureStage = "File Verification",
                        failureReason = "Plugin package file (.cs3) missing or empty on device storage (Path: ${ext.localFilePath ?: "none"})"
                    )
                )
                continue
            }

            // Step 2: Class discovery & DEX validation
            val (manifestContent, manifestClasses) = inspectZipManifest(file)
            val optDir = File(context.codeCacheDir, "opt_diag_${file.nameWithoutExtension}").apply { mkdirs() }
            val dexClasses = extractClassesFromDex(file, optDir)
            val candidateClasses = (manifestClasses + dexClasses).distinct()

            if (candidateClasses.isEmpty()) {
                failures.add(
                    ExtensionFailureItem(
                        extensionName = ext.name,
                        packageName = ext.pkgName,
                        providerName = "N/A (No Classes)",
                        repositoryName = repoName,
                        repositoryUrl = ext.repositoryUrl,
                        failureStage = "Class Discovery",
                        failureReason = "Archive does not contain a valid manifest or readable DEX class entries"
                    )
                )
                continue
            }

            // Step 3: Registration in APIHolder
            var providers = com.lagradost.cloudstream3.APIHolder.allProviders.filter { api ->
                api.sourcePlugin == file.absolutePath ||
                api.sourcePlugin?.contains(file.nameWithoutExtension) == true ||
                ext.pkgName.contains(api.name, ignoreCase = true) ||
                api.name.contains(ext.pkgName, ignoreCase = true)
            }

            if (providers.isEmpty()) {
                loadExtensionFromDisk(ext)
                providers = com.lagradost.cloudstream3.APIHolder.allProviders.filter { api ->
                    api.sourcePlugin == file.absolutePath ||
                    api.sourcePlugin?.contains(file.nameWithoutExtension) == true ||
                    ext.pkgName.contains(api.name, ignoreCase = true) ||
                    api.name.contains(ext.pkgName, ignoreCase = true)
                }
            }

            if (providers.isEmpty()) {
                failures.add(
                    ExtensionFailureItem(
                        extensionName = ext.name,
                        packageName = ext.pkgName,
                        providerName = "N/A (No Providers Registered)",
                        repositoryName = repoName,
                        repositoryUrl = ext.repositoryUrl,
                        failureStage = "Provider Registration",
                        failureReason = "Classes loaded into memory but 0 MainAPI provider instances were registered into APIHolder"
                    )
                )
                continue
            }

            // Step 4: Functional Testing on each registered provider
            var anyProviderWorking = false
            for (api in providers) {
                var isFailed = false
                var reason: String? = null
                var stage = "Network / Scraper"

                val mainUrl = api.mainUrl
                if (mainUrl.isBlank()) {
                    isFailed = true
                    reason = "Provider mainUrl is empty or unconfigured"
                    stage = "Configuration"
                } else {
                    try {
                        val req = Request.Builder()
                            .url(mainUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.code == 403) {
                                isFailed = true
                                reason = "HTTP 403 Forbidden - Cloudflare anti-bot verification required ($mainUrl)"
                                stage = "Cloudflare / Anti-Bot"
                            } else if (resp.code == 404) {
                                isFailed = true
                                reason = "HTTP 404 Not Found - Website domain or endpoint dead ($mainUrl)"
                                stage = "Defunct URL"
                            } else if (resp.code >= 500) {
                                isFailed = true
                                reason = "HTTP ${resp.code} Server Error on $mainUrl"
                                stage = "Server Error"
                            }
                        }
                    } catch (netEx: Exception) {
                        isFailed = true
                        val msg = netEx.localizedMessage ?: netEx.javaClass.simpleName
                        if (netEx is java.net.UnknownHostException || msg.contains("Unable to resolve host", ignoreCase = true)) {
                            reason = "DNS Lookup Failed: Domain failed to resolve ($mainUrl)"
                            stage = "DNS / Dead Host"
                        } else if (netEx is java.net.SocketTimeoutException) {
                            reason = "Network Timeout: Server took too long to respond ($mainUrl)"
                            stage = "Network Timeout"
                        } else {
                            reason = "Network Connection Error: $msg"
                            stage = "Network Error"
                        }
                    }
                }

                if (!isFailed) {
                    try {
                        val searchResult = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                            api.search("movie")
                        }
                        if (searchResult == null) {
                            isFailed = true
                            reason = "Search operation timed out after 10 seconds ($mainUrl)"
                            stage = "Scraper Timeout"
                        } else {
                            anyProviderWorking = true
                        }
                    } catch (searchEx: Exception) {
                        isFailed = true
                        val errMessage = searchEx.localizedMessage ?: searchEx.javaClass.simpleName
                        reason = "Scraper extraction failed: $errMessage"
                        stage = "Scraper Exception"
                    }
                }

                if (isFailed) {
                    failures.add(
                        ExtensionFailureItem(
                            extensionName = ext.name,
                            packageName = ext.pkgName,
                            providerName = api.name,
                            repositoryName = repoName,
                            repositoryUrl = ext.repositoryUrl,
                            failureStage = stage,
                            failureReason = reason ?: "Unknown functional test error"
                        )
                    )
                }
            }

            if (anyProviderWorking) {
                workingCount++
            }
        }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val formattedDate = sdf.format(java.util.Date())

        val reportBuilder = StringBuilder()
        reportBuilder.appendLine("================================================================================")
        reportBuilder.appendLine("                  CINEMA / CLOUDSTREAM EXTENSION FAILURE REPORT                 ")
        reportBuilder.appendLine("================================================================================")
        reportBuilder.appendLine("Generated At        : $formattedDate")
        reportBuilder.appendLine("Total Extensions    : $total")
        reportBuilder.appendLine("Working (Omitted)   : $workingCount (Healthy extensions filtered out)")
        reportBuilder.appendLine("Total Core Failures : ${failures.size}")
        reportBuilder.appendLine("================================================================================\n")

        if (failures.isEmpty()) {
            reportBuilder.appendLine("STATUS: All installed extensions passed functional diagnostics.")
            reportBuilder.appendLine("No failed extensions found.")
        } else {
            failures.forEachIndexed { i, fail ->
                reportBuilder.appendLine("--------------------------------------------------------------------------------")
                reportBuilder.appendLine("FAILURE #${i + 1}")
                reportBuilder.appendLine("Extension Name  : ${fail.extensionName}")
                reportBuilder.appendLine("Provider Name   : ${fail.providerName}")
                reportBuilder.appendLine("Repository Name : ${fail.repositoryName}")
                if (!fail.repositoryUrl.isNullOrBlank()) {
                    reportBuilder.appendLine("Repository URL  : ${fail.repositoryUrl}")
                }
                reportBuilder.appendLine("Package ID      : ${fail.packageName}")
                reportBuilder.appendLine("Failure Stage   : ${fail.failureStage}")
                reportBuilder.appendLine("Why It Failed   : ${fail.failureReason}")
                if (!fail.details.isNullOrBlank()) {
                    reportBuilder.appendLine("Details         : ${fail.details}")
                }
                reportBuilder.appendLine("--------------------------------------------------------------------------------\n")
            }
        }
        reportBuilder.appendLine("================================================================================")
        reportBuilder.appendLine("End of Failure Report")
        reportBuilder.appendLine("================================================================================")

        val reportText = reportBuilder.toString()
        val reportFile = File(context.filesDir, "extension_failures_report.txt")
        runCatching {
            reportFile.writeText(reportText)
        }

        ExtensionTestBatchReport(
            totalTested = total,
            totalWorkingCount = workingCount,
            totalFailedCount = failures.size,
            failures = failures,
            reportText = reportText,
            reportFilePath = reportFile.absolutePath,
            timestamp = System.currentTimeMillis()
        )
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
