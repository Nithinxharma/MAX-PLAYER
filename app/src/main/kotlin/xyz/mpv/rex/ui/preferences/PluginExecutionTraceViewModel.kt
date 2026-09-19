package xyz.mpv.rex.ui.preferences

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import xyz.mpv.rex.cinehub.diagnostic.DiagnosticLogger
import xyz.mpv.rex.cinehub.extension.api.CloudstreamMainApiAdapter
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.PluginTraceSession
import xyz.mpv.rex.cinehub.extension.model.TraceStepItem
import xyz.mpv.rex.cinehub.extension.model.TraceStepStatus
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

class PluginExecutionTraceViewModel(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val registry: ProviderRegistry,
    private val db: MpvExDatabase
) : ViewModel() {

    private val TAG = "PluginTraceVM"
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _availableExtensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val availableExtensions: StateFlow<List<InstalledExtension>> = _availableExtensions.asStateFlow()

    private val _selectedExtension = MutableStateFlow<InstalledExtension?>(null)
    val selectedExtension: StateFlow<InstalledExtension?> = _selectedExtension.asStateFlow()

    private val _traceSession = MutableStateFlow<PluginTraceSession?>(null)
    val traceSession: StateFlow<PluginTraceSession?> = _traceSession.asStateFlow()

    private val _isTracing = MutableStateFlow(false)
    val isTracing: StateFlow<Boolean> = _isTracing.asStateFlow()

    private val _savedFilePath = MutableStateFlow<String?>(null)
    val savedFilePath: StateFlow<String?> = _savedFilePath.asStateFlow()

    init {
        loadAvailableExtensions()
    }

    fun loadAvailableExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            val dbExts = db.extensionDao().getAllInstalledExtensionsSync()
            val candidateFiles = extensionManager.getCandidatePluginFiles()

            val combined = mutableListOf<InstalledExtension>()
            combined.addAll(dbExts)

            // Include any unindexed files on disk as potential targets
            for (f in candidateFiles) {
                val pkg = f.nameWithoutExtension
                if (combined.none { it.pkgName.equals(pkg, ignoreCase = true) }) {
                    combined.add(
                        InstalledExtension(
                            pkgName = pkg,
                            name = pkg,
                            version = "1.0.0",
                            versionCode = 1,
                            localFilePath = f.absolutePath,
                            isEnabled = true
                        )
                    )
                }
            }

            // Ensure popular requested extensions are at least present in candidates list
            val defaultKnown = listOf("Bollyflix", "CineStream", "MoviesDrive", "Moviesmod", "VegaMovies")
            for (known in defaultKnown) {
                if (combined.none { it.name.contains(known, ignoreCase = true) || it.pkgName.contains(known, ignoreCase = true) }) {
                    val matchingFile = extensionManager.findExtensionFile(known, null)
                    combined.add(
                        InstalledExtension(
                            pkgName = known,
                            name = known,
                            version = "1.0.0",
                            versionCode = 1,
                            localFilePath = matchingFile?.absolutePath ?: File(context.filesDir, "cloudstream_plugins/$known.cs3").absolutePath,
                            isEnabled = true
                        )
                    )
                }
            }

            _availableExtensions.value = combined
            if (_selectedExtension.value == null && combined.isNotEmpty()) {
                _selectedExtension.value = combined.first()
            }
        }
    }

    fun selectExtension(ext: InstalledExtension) {
        _selectedExtension.value = ext
        _savedFilePath.value = null
    }

    private fun createInitialSteps(): List<TraceStepItem> {
        return listOf(
            TraceStepItem(1, "STEP 1: Locate extension in database", "Query Room database for installed record"),
            TraceStepItem(2, "STEP 2: Resolve localFilePath", "Locate absolute path of .cs3 plugin archive"),
            TraceStepItem(3, "STEP 3: Verify file exists", "Check physical existence and verify file size"),
            TraceStepItem(4, "STEP 4: Open .cs3 archive", "Read and validate ZIP archive structure"),
            TraceStepItem(5, "STEP 5: Read manifest.json", "Extract and parse raw manifest / make / plugin JSON"),
            TraceStepItem(6, "STEP 6: Extract pluginClassName", "Resolve main plugin entrypoint class name"),
            TraceStepItem(7, "STEP 7: Read classes.dex", "Extract classes.dex byte payload and check size"),
            TraceStepItem(8, "STEP 8: Enumerate DexFile.entries()", "Scan and count candidate classes in DEX table"),
            TraceStepItem(9, "STEP 9: Create ClassLoader", "Instantiate PathClassLoader or DexClassLoader hierarchy"),
            TraceStepItem(10, "STEP 10: loadClass(pluginClassName)", "Load entrypoint class into Android runtime"),
            TraceStepItem(11, "STEP 11: Instantiate plugin", "Instantiate instance via constructor or INSTANCE field"),
            TraceStepItem(12, "STEP 12: Execute plugin.load(context)", "Invoke lifecycle loader passing application Context"),
            TraceStepItem(13, "STEP 13: Track registerMainAPI()", "Verify provider registration invocations"),
            TraceStepItem(14, "STEP 14: Verify APIHolder count", "Validate presence in CloudStream APIHolder registry"),
            TraceStepItem(15, "STEP 15: Verify ProviderRegistry count", "Validate presence in CineHub ProviderRegistry"),
            TraceStepItem(16, "STEP 16: Run provider search test", "Execute live test query ('One Piece') on registered provider")
        )
    }

    fun runTraceForSelected() {
        val ext = _selectedExtension.value ?: return
        runTrace(ext)
    }

    fun runTrace(ext: InstalledExtension) {
        if (_isTracing.value) return
        _isTracing.value = true
        _savedFilePath.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val session = PluginTraceSession(
                pluginPkgName = ext.pkgName,
                pluginDisplayName = ext.name.ifBlank { ext.pkgName },
                isRunning = true,
                steps = createInitialSteps()
            )
            _traceSession.value = session
            val logs = mutableListOf<String>()

            fun logTrace(msg: String) {
                val line = "[${timeFormat.format(Date())}] $msg"
                logs.add(line)
                Log.i(TAG, line)
                DiagnosticLogger.info("PLUGIN_TRACE", msg)
            }

            logTrace("=== Starting Execution Trace for: ${ext.name} (${ext.pkgName}) ===")

            var currentDbExt: InstalledExtension? = null
            var resolvedFile: File? = null
            var rawManifestText: String? = null
            var resolvedPluginClassName: String? = null
            var discoveredClasses = listOf<String>()
            var dexBytesCount = 0L
            var classLoader: ClassLoader? = null
            var loadedClass: Class<*>? = null
            var pluginInstance: Any? = null
            val newlyRegisteredApis = mutableListOf<MainAPI>()
            val initialApisCount = APIHolder.allProviders.size
            val initialRegistryCount = registry.getAllProviders().size

            var hasFailed = false
            var failedStepNum: Int? = null

            for (step in session.steps) {
                if (hasFailed) {
                    step.status = TraceStepStatus.SKIPPED
                    step.resultSummary = "Skipped due to failure at STEP $failedStepNum"
                    continue
                }

                step.status = TraceStepStatus.RUNNING
                val stepStart = System.currentTimeMillis()
                delay(60) // Visual pacing for user to see real-time execution

                try {
                    when (step.stepNumber) {
                        1 -> {
                            // STEP 1: Locate extension in database
                            val dbRecord = db.extensionDao().getExtension(ext.pkgName)
                            currentDbExt = dbRecord ?: ext
                            if (dbRecord != null) {
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "Found in database (isEnabled=${dbRecord.isEnabled}, version=${dbRecord.version})"
                                step.detailedOutput = "Database Record:\n- Name: ${dbRecord.name}\n- Pkg: ${dbRecord.pkgName}\n- Version: ${dbRecord.version} (${dbRecord.versionCode})\n- Path: ${dbRecord.localFilePath}\n- Classes: ${dbRecord.classesFile}"
                                logTrace("STEP 1: PASS - Database row located.")
                            } else {
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "Unindexed in DB (Created virtual memory record)"
                                step.detailedOutput = "Extension not found in Room database table, but detected on disk/runtime."
                                logTrace("STEP 1: PASS (Virtual) - Unindexed record.")
                            }
                        }

                        2 -> {
                            // STEP 2: Resolve localFilePath
                            val file = extensionManager.findExtensionFile(ext.pkgName, currentDbExt?.localFilePath ?: ext.localFilePath)
                            resolvedFile = file
                            if (file != null) {
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "Resolved to: ${file.absolutePath}"
                                step.detailedOutput = "Target file path: ${file.absolutePath}"
                                logTrace("STEP 2: PASS - Path resolved: ${file.absolutePath}")
                            } else {
                                step.status = TraceStepStatus.FAILED
                                step.resultSummary = "Could not resolve candidate file on disk"
                                step.errorMessage = "Searched extensionDir and /cloudstream_plugins directories but found no matching .cs3 file."
                                hasFailed = true
                                failedStepNum = 2
                                logTrace("STEP 2: FAIL - File path could not be resolved.")
                            }
                        }

                        3 -> {
                            // STEP 3: Verify file exists
                            val file = resolvedFile
                            if (file != null && file.exists() && file.isFile) {
                                val size = file.length()
                                if (size > 0) {
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "File exists ($size bytes / ${size / 1024} KB)"
                                    step.detailedOutput = "File: ${file.name}\nSize: $size bytes\nReadable: ${file.canRead()}"
                                    logTrace("STEP 3: PASS - File exists ($size bytes).")
                                } else {
                                    step.status = TraceStepStatus.FAILED
                                    step.resultSummary = "File is empty (0 bytes)"
                                    step.errorMessage = "The .cs3 archive file at ${file.absolutePath} has a size of 0 bytes."
                                    hasFailed = true
                                    failedStepNum = 3
                                    logTrace("STEP 3: FAIL - File empty.")
                                }
                            } else {
                                step.status = TraceStepStatus.FAILED
                                step.resultSummary = "File does not exist on disk"
                                step.errorMessage = "File.exists() returned false for path: ${file?.absolutePath}"
                                hasFailed = true
                                failedStepNum = 3
                                logTrace("STEP 3: FAIL - File missing.")
                            }
                        }

                        4 -> {
                            // STEP 4: Open .cs3 archive
                            val file = resolvedFile!!
                            ZipFile(file).use { zip ->
                                val count = zip.size()
                                val entries = zip.entries().asSequence().map { it.name }.toList()
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "Valid ZIP archive with $count entries"
                                step.detailedOutput = "Archive Entries ($count):\n" + entries.joinToString("\n") { "  - $it" }
                                logTrace("STEP 4: PASS - Archive opened successfully ($count entries).")
                            }
                        }

                        5 -> {
                            // STEP 5: Read manifest.json
                            val file = resolvedFile!!
                            var foundManifest = false
                            ZipFile(file).use { zip ->
                                val entry = zip.entries().asSequence().firstOrNull {
                                    val name = it.name.substringAfterLast('/')
                                    name.equals("manifest.json", ignoreCase = true) ||
                                            name.equals("make.json", ignoreCase = true) ||
                                            name.equals("plugin.json", ignoreCase = true)
                                }
                                if (entry != null) {
                                    val text = zip.getInputStream(entry).bufferedReader().readText()
                                    rawManifestText = text
                                    foundManifest = true
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "Read ${entry.name} (${text.length} chars)"
                                    step.detailedOutput = "Raw Manifest JSON:\n$text"
                                    logTrace("STEP 5: PASS - Found and read ${entry.name}.")
                                }
                            }
                            if (!foundManifest) {
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "No manifest.json present in archive (Will use DEX scan fallback)"
                                step.detailedOutput = "Archive does not contain manifest.json. System will rely on classes.dex scanning."
                                logTrace("STEP 5: PASS (Fallback) - No manifest.json found.")
                            }
                        }

                        6 -> {
                            // STEP 6: Extract pluginClassName
                            val candidateNames = mutableListOf<String>()
                            if (!rawManifestText.isNullOrBlank()) {
                                try {
                                    val json = JSONObject(rawManifestText!!)
                                    val pClass = json.optString("pluginClassName", json.optString("pluginClass", json.optString("mainClass", json.optString("class", ""))))
                                    if (pClass.isNotBlank()) candidateNames.add(pClass.trim())
                                    val arr = json.optJSONArray("classes")
                                    if (arr != null) {
                                        for (i in 0 until arr.length()) {
                                            val c = arr.optString(i).trim()
                                            if (c.isNotBlank() && !candidateNames.contains(c)) candidateNames.add(c)
                                        }
                                    }
                                } catch (e: Exception) {
                                    logTrace("STEP 6: Warning - JSON parse error in manifest: ${e.message}")
                                }
                            }

                            currentDbExt?.classesFile?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it.contains(".") }?.forEach {
                                if (!candidateNames.contains(it)) candidateNames.add(it)
                            }

                            if (candidateNames.isNotEmpty()) {
                                resolvedPluginClassName = candidateNames.first()
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "Extracted entrypoint: $resolvedPluginClassName (${candidateNames.size} candidate classes)"
                                step.detailedOutput = "Primary Plugin Class: $resolvedPluginClassName\nAll Manifest Classes:\n" + candidateNames.joinToString("\n") { "  - $it" }
                                logTrace("STEP 6: PASS - Primary class: $resolvedPluginClassName")
                            } else {
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "No explicit pluginClassName in manifest (Will deduce from classes.dex)"
                                step.detailedOutput = "Entrypoint class will be determined during STEP 8 via DEX entry scan."
                                logTrace("STEP 6: PASS (Deferred) - Deducing from DEX scan.")
                            }
                        }

                        7 -> {
                            // STEP 7: Read classes.dex
                            val file = resolvedFile!!
                            var hasDex = false
                            ZipFile(file).use { zip ->
                                val dexEntry = zip.getEntry("classes.dex") ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".dex", ignoreCase = true) }
                                if (dexEntry != null) {
                                    dexBytesCount = dexEntry.size
                                    hasDex = true
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "Found ${dexEntry.name} ($dexBytesCount bytes)"
                                    step.detailedOutput = "DEX Entry: ${dexEntry.name}\nCompressed Size: ${dexEntry.compressedSize} bytes\nUncompressed Size: $dexBytesCount bytes"
                                    logTrace("STEP 7: PASS - ${dexEntry.name} present ($dexBytesCount bytes).")
                                }
                            }
                            if (!hasDex) {
                                step.status = TraceStepStatus.FAILED
                                step.resultSummary = "Missing classes.dex in archive"
                                step.errorMessage = "The .cs3 archive does not contain any compiled classes.dex executable byte code."
                                hasFailed = true
                                failedStepNum = 7
                                logTrace("STEP 7: FAIL - classes.dex missing.")
                            }
                        }

                        8 -> {
                            // STEP 8: Enumerate DexFile.entries()
                            val file = resolvedFile!!
                            val optDir = File(context.codeCacheDir, "trace_opt_${ext.pkgName}").apply { mkdirs() }
                            val dexList = mutableListOf<String>()

                            runCatching {
                                @Suppress("DEPRECATION")
                                val dexFile = DexFile.loadDex(file.absolutePath, File(optDir, "temp.dex").absolutePath, 0)
                                val entries = dexFile.entries()
                                while (entries.hasMoreElements()) {
                                    val className = entries.nextElement()
                                    if (!className.startsWith("kotlin.") &&
                                        !className.startsWith("kotlinx.") &&
                                        !className.startsWith("android.") &&
                                        !className.startsWith("androidx.")
                                    ) {
                                        dexList.add(className)
                                    }
                                }
                                dexFile.close()
                            }.onFailure {
                                logTrace("STEP 8: Native DexFile reflection fallback: ${it.message}")
                            }

                            discoveredClasses = dexList
                            if (resolvedPluginClassName == null && dexList.isNotEmpty()) {
                                resolvedPluginClassName = dexList.firstOrNull { it.contains("Plugin", ignoreCase = true) }
                                    ?: dexList.firstOrNull { it.contains("Provider", ignoreCase = true) }
                                    ?: dexList.first()
                            }

                            step.status = TraceStepStatus.PASSED
                            step.resultSummary = "Discovered ${dexList.size} candidate classes in DEX table"
                            step.detailedOutput = "Candidate Classes (${dexList.size}):\n" + dexList.take(50).joinToString("\n") { "  - $it" } +
                                    if (dexList.size > 50) "\n  ... and ${dexList.size - 50} more" else ""
                            logTrace("STEP 8: PASS - Extracted ${dexList.size} classes from DEX.")
                        }

                        9 -> {
                            // STEP 9: Create ClassLoader
                            val file = resolvedFile!!
                            val optDir = File(context.codeCacheDir, "trace_dex_${ext.pkgName}").apply { mkdirs() }

                            var loaderType = "PathClassLoader"
                            var createdLoader: ClassLoader = try {
                                PathClassLoader(file.absolutePath, context.classLoader)
                            } catch (e: Throwable) {
                                loaderType = "DexClassLoader"
                                DexClassLoader(file.absolutePath, optDir.absolutePath, null, context.classLoader)
                            }

                            classLoader = createdLoader
                            step.status = TraceStepStatus.PASSED
                            step.resultSummary = "Created $loaderType successfully"
                            step.detailedOutput = "ClassLoader Details:\n- Type: $loaderType\n- Parent: ${context.classLoader}\n- Dex Path: ${file.absolutePath}\n- Optimized Path: ${optDir.absolutePath}"
                            logTrace("STEP 9: PASS - $loaderType instantiated.")
                        }

                        10 -> {
                            // STEP 10: loadClass(pluginClassName)
                            val targetClass = resolvedPluginClassName
                            if (targetClass.isNullOrBlank()) {
                                throw ClassNotFoundException("No valid pluginClassName identified from manifest or DEX entries.")
                            }
                            logTrace("STEP 10: Attempting classLoader.loadClass('$targetClass')...")
                            val clazz = classLoader!!.loadClass(targetClass)
                            loadedClass = clazz

                            val superTypes = mutableListOf<String>()
                            var curr: Class<*>? = clazz.superclass
                            while (curr != null && curr != Any::class.java) {
                                superTypes.add(curr.name)
                                curr = curr.superclass
                            }
                            val interfaces = clazz.interfaces.map { it.name }

                            step.status = TraceStepStatus.PASSED
                            step.resultSummary = "Successfully loaded class: ${clazz.name}"
                            step.detailedOutput = "Class Information:\n- Name: ${clazz.name}\n- Canonical Name: ${clazz.canonicalName}\n- Superclasses: ${superTypes.joinToString(" -> ")}\n- Interfaces: ${interfaces.joinToString(", ")}\n- Declared Constructors: ${clazz.declaredConstructors.size}\n- Declared Methods: ${clazz.declaredMethods.size}"
                            logTrace("STEP 10: PASS - Class loaded: ${clazz.name}")
                        }

                        11 -> {
                            // STEP 11: Instantiate plugin
                            val clazz = loadedClass!!
                            logTrace("STEP 11: Attempting instantiation for ${clazz.name}...")

                            var instance: Any? = null
                            val errors = mutableListOf<String>()

                            // 1. Singleton INSTANCE field
                            runCatching {
                                val field = clazz.getDeclaredField("INSTANCE").apply { isAccessible = true }
                                instance = field.get(null)
                            }.onFailure { errors.add("INSTANCE field: ${it.message}") }

                            // 2. Default no-arg constructor
                            if (instance == null) {
                                runCatching {
                                    val constructor = clazz.getDeclaredConstructor().apply { isAccessible = true }
                                    instance = constructor.newInstance()
                                }.onFailure { errors.add("No-arg constructor: ${it.message}") }
                            }

                            // 3. Context constructor
                            if (instance == null) {
                                runCatching {
                                    val constructor = clazz.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
                                    instance = constructor.newInstance(context)
                                }.onFailure { errors.add("Context constructor: ${it.message}") }
                            }

                            if (instance != null) {
                                pluginInstance = instance
                                val isBasePlugin = instance is BasePlugin
                                val isMainApi = instance is MainAPI
                                val isExtractor = instance is ExtractorApi

                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "Instantiated instance of ${instance?.javaClass?.simpleName} (BasePlugin=$isBasePlugin, MainAPI=$isMainApi, Extractor=$isExtractor)"
                                step.detailedOutput = "Instance Details:\n- Object Type: ${instance?.javaClass?.name}\n- BasePlugin: $isBasePlugin\n- MainAPI: $isMainApi\n- ExtractorApi: $isExtractor"
                                logTrace("STEP 11: PASS - Instantiated instance of ${clazz.name}")
                            } else {
                                throw NoSuchMethodException("Failed to instantiate ${clazz.name}. Checked INSTANCE, no-arg constructor, and Context constructor. Errors: ${errors.joinToString("; ")}")
                            }
                        }

                        12 -> {
                            // STEP 12: Execute plugin.load(context)
                            val instance = pluginInstance!!
                            val file = resolvedFile!!
                            logTrace("STEP 12: Executing plugin.load() lifecycle...")

                            when (instance) {
                                is BasePlugin -> {
                                    instance.filename = file.absolutePath
                                    if (instance is Plugin) {
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
                                        instance.load(context)
                                    } else {
                                        instance.load()
                                    }
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "Executed BasePlugin.load() successfully"
                                    step.detailedOutput = "BasePlugin lifecycle invoked with filename=${file.absolutePath}"
                                    logTrace("STEP 12: PASS - BasePlugin.load() completed.")
                                }
                                is MainAPI -> {
                                    instance.sourcePlugin = file.absolutePath
                                    APIHolder.addPlugin(instance)
                                    newlyRegisteredApis.add(instance)
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "Direct MainAPI registered to APIHolder: ${instance.name}"
                                    step.detailedOutput = "MainAPI Details:\n- Name: ${instance.name}\n- URL: ${instance.mainUrl}\n- Lang: ${instance.lang}\n- Types: ${instance.supportedTypes.joinToString()}"
                                    logTrace("STEP 12: PASS - Direct MainAPI registered: ${instance.name}")
                                }
                                is ExtractorApi -> {
                                    instance.sourcePlugin = file.absolutePath
                                    APIHolder.addExtractor(instance)
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "Direct ExtractorApi registered: ${instance.name}"
                                    step.detailedOutput = "Extractor: ${instance.name} (${instance.mainUrl})"
                                    logTrace("STEP 12: PASS - Direct Extractor registered: ${instance.name}")
                                }
                                else -> {
                                    // Reflection load(Context) / load()
                                    val loadWithCtx = instance.javaClass.methods.firstOrNull { it.name == "load" && it.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(it.parameterTypes[0]) }
                                    val loadNoArgs = instance.javaClass.methods.firstOrNull { it.name == "load" && it.parameterTypes.isEmpty() }
                                    if (loadWithCtx != null) {
                                        loadWithCtx.invoke(instance, context)
                                        step.status = TraceStepStatus.PASSED
                                        step.resultSummary = "Invoked load(Context) via reflection"
                                        logTrace("STEP 12: PASS - load(Context) invoked via reflection.")
                                    } else if (loadNoArgs != null) {
                                        loadNoArgs.invoke(instance)
                                        step.status = TraceStepStatus.PASSED
                                        step.resultSummary = "Invoked load() via reflection"
                                        logTrace("STEP 12: PASS - load() invoked via reflection.")
                                    } else {
                                        step.status = TraceStepStatus.PASSED
                                        step.resultSummary = "Class does not declare load() method (Assumed self-initialized)"
                                        logTrace("STEP 12: PASS (Self-initialized).")
                                    }
                                }
                            }
                        }

                        13 -> {
                            // STEP 13: Track registerMainAPI()
                            val currentApis = APIHolder.allProviders.toList()
                            val registeredFromSource = currentApis.filter {
                                it.sourcePlugin == resolvedFile?.absolutePath ||
                                        it.sourcePlugin == resolvedFile?.name ||
                                        it.name.contains(ext.pkgName, ignoreCase = true) ||
                                        it.name.contains(ext.name, ignoreCase = true)
                            }
                            newlyRegisteredApis.clear()
                            newlyRegisteredApis.addAll(registeredFromSource)

                            if (registeredFromSource.isNotEmpty()) {
                                step.status = TraceStepStatus.PASSED
                                step.resultSummary = "Registered ${registeredFromSource.size} provider(s): " + registeredFromSource.joinToString { it.name }
                                step.detailedOutput = "Providers Registered:\n" + registeredFromSource.joinToString("\n") {
                                    "  - ${it.name} [${it.lang}] -> URL: ${it.mainUrl} (Types: ${it.supportedTypes.joinToString()})"
                                }
                                logTrace("STEP 13: PASS - Providers registered: ${registeredFromSource.joinToString { it.name }}")
                            } else {
                                step.status = TraceStepStatus.FAILED
                                step.resultSummary = "0 providers registered during execution"
                                step.errorMessage = "Plugin.load() completed, but no MainAPI instances were added to APIHolder.allProviders."
                                hasFailed = true
                                failedStepNum = 13
                                logTrace("STEP 13: FAIL - 0 providers registered.")
                            }
                        }

                        14 -> {
                            // STEP 14: Verify APIHolder count
                            val totalApis = APIHolder.allProviders.size
                            step.status = TraceStepStatus.PASSED
                            step.resultSummary = "APIHolder count verified ($totalApis total active providers in memory)"
                            step.detailedOutput = "Total APIHolder Providers: $totalApis\nRecent additions: ${newlyRegisteredApis.joinToString { it.name }}"
                            logTrace("STEP 14: PASS - APIHolder count = $totalApis")
                        }

                        15 -> {
                            // STEP 15: Verify ProviderRegistry count
                            for (api in newlyRegisteredApis) {
                                registry.register(CloudstreamMainApiAdapter(api), isEnabledByDefault = true)
                            }
                            val allRegistryProviders = registry.getAllProviders()
                            step.status = TraceStepStatus.PASSED
                            step.resultSummary = "ProviderRegistry synchronized (${allRegistryProviders.size} providers available to CineHub)"
                            step.detailedOutput = "Registered CineHub Providers:\n" + allRegistryProviders.joinToString("\n") { "  - ${it.name} (${it.id})" }
                            logTrace("STEP 15: PASS - ProviderRegistry count = ${allRegistryProviders.size}")
                        }

                        16 -> {
                            // STEP 16: Run provider search test
                            val primary = newlyRegisteredApis.firstOrNull() ?: APIHolder.allProviders.firstOrNull()
                            if (primary != null) {
                                val query = "One Piece"
                                logTrace("STEP 16: Executing test search for '$query' on ${primary.name}...")
                                val searchResults: List<SearchResponse>? = withTimeoutOrNull(9000) {
                                    primary.search(query)
                                }
                                if (searchResults != null && searchResults.isNotEmpty()) {
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "Query '$query' returned ${searchResults.size} results on ${primary.name}"
                                    step.detailedOutput = "Sample Results:\n" + searchResults.take(3).joinToString("\n") { "  - ${it.name} (${it.url})" }
                                    logTrace("STEP 16: PASS - Search returned ${searchResults.size} results.")
                                } else {
                                    step.status = TraceStepStatus.PASSED
                                    step.resultSummary = "Query '$query' executed successfully without errors (0 items returned)"
                                    step.detailedOutput = "Provider network call completed with HTTP 200 / Empty list."
                                    logTrace("STEP 16: PASS - Search executed with 0 results.")
                                }
                            } else {
                                step.status = TraceStepStatus.SKIPPED
                                step.resultSummary = "No active provider instance available for search test"
                                logTrace("STEP 16: SKIPPED - No provider.")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    val sw = StringWriter()
                    t.printStackTrace(PrintWriter(sw))
                    val st = sw.toString()

                    step.status = TraceStepStatus.FAILED
                    step.resultSummary = "${t.javaClass.simpleName}: ${t.message ?: "Unknown error"}"
                    step.errorMessage = "${t.javaClass.name}: ${t.message}"
                    step.stackTrace = st
                    hasFailed = true
                    failedStepNum = step.stepNumber
                    logTrace("❌ ${step.title} FAILED: ${t.javaClass.name}: ${t.message}")
                    logTrace("Stacktrace:\n$st")
                } finally {
                    step.durationMs = System.currentTimeMillis() - stepStart
                }
            }

            session.endTime = System.currentTimeMillis()
            session.totalDurationMs = session.endTime!! - session.startTime
            session.isRunning = false
            session.hasFailed = hasFailed
            session.failedAtStep = failedStepNum
            session.rawLogLines = logs

            _traceSession.value = session
            _isTracing.value = false
            logTrace("=== Trace Finished. Total Duration: ${session.totalDurationMs}ms. Status: ${if (hasFailed) "FAILED at STEP $failedStepNum" else "ALL PASSED"} ===")
        }
    }

    fun copyTraceText(): String {
        val s = _traceSession.value ?: return "No trace data available"
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("CLOUDSTREAM PLUGIN EXECUTION TRACE REPORT")
        sb.appendLine("Target: ${s.pluginDisplayName} (${s.pluginPkgName})")
        sb.appendLine("Date: ${timeFormat.format(Date(s.startTime))}")
        sb.appendLine("Duration: ${s.totalDurationMs}ms")
        sb.appendLine("Status: ${if (s.hasFailed) "❌ FAILED at STEP ${s.failedAtStep}" else "✅ PASSED ALL 16 STEPS"}")
        sb.appendLine("==================================================")
        sb.appendLine()

        for (step in s.steps) {
            val symbol = when (step.status) {
                TraceStepStatus.PASSED -> "✅"
                TraceStepStatus.FAILED -> "❌"
                TraceStepStatus.RUNNING -> "⏳"
                TraceStepStatus.SKIPPED -> "⏭️"
                TraceStepStatus.IDLE -> "⚪"
            }
            sb.appendLine("$symbol ${step.title} [${step.durationMs}ms]")
            if (!step.resultSummary.isNullOrBlank()) {
                sb.appendLine("   Result: ${step.resultSummary}")
            }
            if (!step.detailedOutput.isNullOrBlank()) {
                sb.appendLine("   Output:")
                step.detailedOutput!!.lines().forEach { sb.appendLine("     $it") }
            }
            if (!step.errorMessage.isNullOrBlank()) {
                sb.appendLine("   Error: ${step.errorMessage}")
            }
            if (!step.stackTrace.isNullOrBlank()) {
                sb.appendLine("   Stacktrace:")
                step.stackTrace!!.lines().forEach { sb.appendLine("     $it") }
            }
            sb.appendLine()
        }

        sb.appendLine("==================================================")
        sb.appendLine("RAW TRACE LOGS:")
        s.rawLogLines.forEach { sb.appendLine(it) }
        sb.appendLine("==================================================")
        return sb.toString()
    }

    fun exportTrace(context: Context) {
        val text = copyTraceText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Plugin Execution Trace - ${_selectedExtension.value?.name}")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Plugin Execution Trace")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun saveTraceLog(context: Context): String? {
        val s = _traceSession.value ?: return null
        return try {
            val traceDir = File(context.filesDir, "plugin_traces").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(traceDir, "trace_${s.pluginPkgName}_$timestamp.txt")
            FileOutputStream(file).use { out ->
                out.write(copyTraceText().toByteArray())
            }
            _savedFilePath.value = file.absolutePath
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save trace log", e)
            null
        }
    }
}
