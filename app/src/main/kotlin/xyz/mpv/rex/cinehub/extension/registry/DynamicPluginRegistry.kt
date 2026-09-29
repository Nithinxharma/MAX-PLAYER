package xyz.mpv.rex.cinehub.extension.registry

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Levenshtein
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import xyz.mpv.rex.cinehub.extension.api.CloudstreamMainApiAdapter
import java.io.File
import java.lang.reflect.Constructor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Metadata entry for a dynamically discovered MainAPI content provider.
 */
data class DiscoveredProviderEntry(
    val providerName: String,
    val className: String,
    val pluginId: String,
    val pluginVersion: String = "1.0.0",
    val packageName: String,
    val mainUrl: String = "",
    val sourceFilePath: String? = null
)

/**
 * Metadata entry for a dynamically discovered ExtractorApi stream link extractor.
 */
data class DiscoveredExtractorEntry(
    val extractorName: String,
    val className: String,
    val pluginId: String,
    val pluginVersion: String = "1.0.0",
    val packageName: String,
    val supportedDomains: List<String> = emptyList(),
    val mainUrl: String = "",
    val sourceFilePath: String? = null
)

/**
 * Runtime telemetry data model.
 */
data class DynamicRegistryTelemetry(
    val loadedProvidersCount: Int,
    val loadedExtractorsCount: Int,
    val indexedProvidersCount: Int,
    val indexedExtractorsCount: Int,
    val dynamicDiscoveriesCount: Int,
    val resolutionSuccessCount: Int,
    val resolutionFailureCount: Int,
    val lastSuccessfulResolution: String?,
    val lastFailureUrl: String?
)

/**
 * DynamicPluginRegistry manages automatic discovery, persistent indexing,
 * dynamic classloader caching, and self-healing resolution of CloudStream providers and extractors.
 */
object DynamicPluginRegistry {
    private const val TAG = "DynamicPluginRegistry"
    private const val PERSISTENCE_FILE_NAME = "dynamic_plugin_registry.json"
    private val schemaStripRegex = Regex("""^(https?:)?//(www\.)?""")

    private val providerIndex = ConcurrentHashMap<String, DiscoveredProviderEntry>()
    private val extractorIndex = ConcurrentHashMap<String, DiscoveredExtractorEntry>()
    private val activeClassLoaders = ConcurrentHashMap<String, ClassLoader>()
    private val pluginFiles = ConcurrentHashMap<String, File>()

    private val _dynamicDiscoveriesCount = AtomicInteger(0)
    private val _resolutionSuccessCount = AtomicInteger(0)
    private val _resolutionFailureCount = AtomicInteger(0)
    private var _lastSuccessfulResolution: String? = null
    private var _lastFailureUrl: String? = null

    private val _isRegistryLoaded = MutableStateFlow(false)
    val isRegistryLoaded: StateFlow<Boolean> = _isRegistryLoaded.asStateFlow()

    fun registerClassLoader(pluginId: String, classLoader: ClassLoader, sourceFile: File? = null) {
        activeClassLoaders[pluginId] = classLoader
        if (sourceFile != null) {
            pluginFiles[pluginId] = sourceFile
        }
    }

    fun unregisterClassLoader(pluginId: String) {
        activeClassLoaders.remove(pluginId)
        pluginFiles.remove(pluginId)
        providerIndex.values.removeAll { it.pluginId == pluginId || it.packageName == pluginId }
        extractorIndex.values.removeAll { it.pluginId == pluginId || it.packageName == pluginId }
    }

    /**
     * Initializes the registry from disk cache on application startup.
     */
    fun initialize(context: Context) {
        try {
            val file = File(context.filesDir, PERSISTENCE_FILE_NAME)
            if (file.exists()) {
                val jsonText = file.readText()
                val root = JSONObject(jsonText)

                val providersArr = root.optJSONArray("providers")
                if (providersArr != null) {
                    for (i in 0 until providersArr.length()) {
                        val obj = providersArr.getJSONObject(i)
                        val entry = DiscoveredProviderEntry(
                            providerName = obj.optString("providerName"),
                            className = obj.optString("className"),
                            pluginId = obj.optString("pluginId"),
                            pluginVersion = obj.optString("pluginVersion", "1.0.0"),
                            packageName = obj.optString("packageName"),
                            mainUrl = obj.optString("mainUrl"),
                            sourceFilePath = obj.optString("sourceFilePath").takeIf { it.isNotBlank() }
                        )
                        if (entry.className.isNotBlank()) {
                            providerIndex[entry.className] = entry
                        }
                    }
                }

                val extractorsArr = root.optJSONArray("extractors")
                if (extractorsArr != null) {
                    for (i in 0 until extractorsArr.length()) {
                        val obj = extractorsArr.getJSONObject(i)
                        val domainsList = mutableListOf<String>()
                        val domArr = obj.optJSONArray("supportedDomains")
                        if (domArr != null) {
                            for (j in 0 until domArr.length()) {
                                domainsList.add(domArr.getString(j))
                            }
                        }
                        val entry = DiscoveredExtractorEntry(
                            extractorName = obj.optString("extractorName"),
                            className = obj.optString("className"),
                            pluginId = obj.optString("pluginId"),
                            pluginVersion = obj.optString("pluginVersion", "1.0.0"),
                            packageName = obj.optString("packageName"),
                            supportedDomains = domainsList,
                            mainUrl = obj.optString("mainUrl"),
                            sourceFilePath = obj.optString("sourceFilePath").takeIf { it.isNotBlank() }
                        )
                        if (entry.className.isNotBlank()) {
                            extractorIndex[entry.className] = entry
                        }
                    }
                }

                Log.i(TAG, "Loaded persistent registry: ${providerIndex.size} providers, ${extractorIndex.size} extractors indexed")
            }
            _isRegistryLoaded.value = true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load persistent registry: ${e.message}")
            _isRegistryLoaded.value = true
        }
    }

    /**
     * Persists the current index to disk cache.
     */
    fun persist(context: Context) {
        try {
            val root = JSONObject()
            val providersArr = JSONArray()
            for (p in providerIndex.values) {
                val obj = JSONObject().apply {
                    put("providerName", p.providerName)
                    put("className", p.className)
                    put("pluginId", p.pluginId)
                    put("pluginVersion", p.pluginVersion)
                    put("packageName", p.packageName)
                    put("mainUrl", p.mainUrl)
                    put("sourceFilePath", p.sourceFilePath ?: "")
                }
                providersArr.put(obj)
            }
            root.put("providers", providersArr)

            val extractorsArr = JSONArray()
            for (e in extractorIndex.values) {
                val obj = JSONObject().apply {
                    put("extractorName", e.extractorName)
                    put("className", e.className)
                    put("pluginId", e.pluginId)
                    put("pluginVersion", e.pluginVersion)
                    put("packageName", e.packageName)
                    put("supportedDomains", JSONArray(e.supportedDomains))
                    put("mainUrl", e.mainUrl)
                    put("sourceFilePath", e.sourceFilePath ?: "")
                }
                extractorsArr.put(obj)
            }
            root.put("extractors", extractorsArr)

            val file = File(context.filesDir, PERSISTENCE_FILE_NAME)
            file.writeText(root.toString(2))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to persist dynamic registry: ${e.message}")
        }
    }

    /**
     * Deeply scans classes loaded from a plugin DEX and registers any MainAPI or ExtractorApi.
     */
    fun indexAndRegisterPluginClasses(
        context: Context,
        pkgName: String,
        version: String,
        sourceFile: File,
        classLoader: ClassLoader,
        classNames: List<String>,
        providerRegistry: ProviderRegistry? = null
    ) {
        registerClassLoader(pkgName, classLoader, sourceFile)

        for (className in classNames) {
            try {
                val clazz = classLoader.loadClass(className)
                if (MainAPI::class.java.isAssignableFrom(clazz) && clazz != MainAPI::class.java) {
                    val instance = instantiateClass(clazz, context) as? MainAPI
                    if (instance != null) {
                        instance.sourcePlugin = sourceFile.absolutePath
                        val entry = DiscoveredProviderEntry(
                            providerName = instance.name,
                            className = className,
                            pluginId = pkgName,
                            pluginVersion = version,
                            packageName = pkgName,
                            mainUrl = instance.mainUrl,
                            sourceFilePath = sourceFile.absolutePath
                        )
                        providerIndex[className] = entry

                        // Add to APIHolder if not already present
                        if (!APIHolder.allProviders.any { it.name.equals(instance.name, ignoreCase = true) }) {
                            APIHolder.addPlugin(instance)
                            providerRegistry?.register(CloudstreamMainApiAdapter(instance), isEnabledByDefault = true)
                            _dynamicDiscoveriesCount.incrementAndGet()
                            Log.i(TAG, "DYNAMIC_DISCOVERY: Auto-registered MainAPI: ${instance.name} ($className)")
                        }
                    }
                } else if (ExtractorApi::class.java.isAssignableFrom(clazz) && clazz != ExtractorApi::class.java) {
                    val instance = instantiateClass(clazz, context) as? ExtractorApi
                    if (instance != null) {
                        instance.sourcePlugin = sourceFile.absolutePath
                        val domains = instance.mainUrl.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val entry = DiscoveredExtractorEntry(
                            extractorName = instance.name,
                            className = className,
                            pluginId = pkgName,
                            pluginVersion = version,
                            packageName = pkgName,
                            supportedDomains = domains,
                            mainUrl = instance.mainUrl,
                            sourceFilePath = sourceFile.absolutePath
                        )
                        extractorIndex[className] = entry

                        // Add to APIHolder if not already present
                        if (!APIHolder.extractorApis.any { it.name.equals(instance.name, ignoreCase = true) }) {
                            APIHolder.addExtractor(instance)
                            _dynamicDiscoveriesCount.incrementAndGet()
                            Log.i(TAG, "DYNAMIC_DISCOVERY: Auto-registered ExtractorApi: ${instance.name} ($className) [Domains: $domains]")
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        persist(context)
    }

    /**
     * Self-healing resolution: when loadExtractor() cannot resolve a URL with existing registered extractors,
     * this method checks the global extractor registry, loads the class from its plugin ClassLoader,
     * registers it into APIHolder, and executes it.
     */
    suspend fun discoverAndResolveExtractor(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val compareUrl = cleanUrl.lowercase().replace(schemaStripRegex, "").trimEnd('/')
        var resolved = false

        // 1. Check indexed extractors
        for (entry in extractorIndex.values) {
            val mainUrl = entry.mainUrl
            val domains = entry.supportedDomains.ifEmpty {
                mainUrl.split(",").map { it.trim().lowercase().replace(schemaStripRegex, "").trimEnd('/') }
            }
            val matches = domains.any { domain ->
                domain.isNotBlank() && (compareUrl.startsWith(domain) || compareUrl.contains(domain))
            } || (mainUrl.isNotBlank() && Levenshtein.partialRatio(mainUrl.lowercase().replace(schemaStripRegex, "").substringBefore('/'), compareUrl.substringBefore('/')) > 80)

            if (matches) {
                val classLoader = activeClassLoaders[entry.pluginId] ?: activeClassLoaders[entry.packageName]
                if (classLoader != null) {
                    try {
                        val clazz = classLoader.loadClass(entry.className)
                        val instance = instantiateClass(clazz) as? ExtractorApi
                        if (instance != null) {
                            instance.sourcePlugin = entry.sourceFilePath
                            APIHolder.addExtractor(instance)
                            _dynamicDiscoveriesCount.incrementAndGet()
                            Log.i(TAG, "SELF_HEALING: Dynamic extractor instantiated & registered: ${instance.name} for $cleanUrl")

                            instance.getSafeUrl(cleanUrl, referer, subtitleCallback, callback)
                            resolved = true
                            _resolutionSuccessCount.incrementAndGet()
                            _lastSuccessfulResolution = "${instance.name} ($cleanUrl)"
                            break
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed executing dynamically discovered extractor ${entry.className}: ${e.message}")
                    }
                }
            }
        }

        if (!resolved) {
            _resolutionFailureCount.incrementAndGet()
            _lastFailureUrl = cleanUrl
        }

        return@withContext resolved
    }

    private fun instantiateClass(clazz: Class<*>, context: Context? = null): Any? {
        // 1. Try public 0-arg constructor
        try {
            val constructor: Constructor<*> = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        } catch (_: Throwable) {}

        // 2. Try Kotlin object INSTANCE
        try {
            val field = clazz.getField("INSTANCE")
            field.isAccessible = true
            val obj = field.get(null)
            if (obj != null) return obj
        } catch (_: Throwable) {}

        // 3. Try declared constructors
        val constructors = clazz.declaredConstructors.sortedBy { it.parameterTypes.size }
        for (constructor in constructors) {
            try {
                constructor.isAccessible = true
                val paramTypes = constructor.parameterTypes
                val args = Array(paramTypes.size) { idx ->
                    val type = paramTypes[idx]
                    when {
                        context != null && Context::class.java.isAssignableFrom(type) -> context
                        context != null && android.content.res.Resources::class.java.isAssignableFrom(type) -> context.resources
                        type == java.lang.String::class.java -> ""
                        type == java.lang.Integer.TYPE || type == java.lang.Integer::class.java -> 0
                        type == java.lang.Long.TYPE || type == java.lang.Long::class.java -> 0L
                        type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java -> false
                        else -> null
                    }
                }
                val obj = constructor.newInstance(*args)
                if (obj != null) return obj
            } catch (_: Throwable) {}
        }
        return null
    }

    fun getTelemetryReport(): DynamicRegistryTelemetry {
        return DynamicRegistryTelemetry(
            loadedProvidersCount = APIHolder.allProviders.size,
            loadedExtractorsCount = APIHolder.extractorApis.size,
            indexedProvidersCount = providerIndex.size,
            indexedExtractorsCount = extractorIndex.size,
            dynamicDiscoveriesCount = _dynamicDiscoveriesCount.get(),
            resolutionSuccessCount = _resolutionSuccessCount.get(),
            resolutionFailureCount = _resolutionFailureCount.get(),
            lastSuccessfulResolution = _lastSuccessfulResolution,
            lastFailureUrl = _lastFailureUrl
        )
    }
}
