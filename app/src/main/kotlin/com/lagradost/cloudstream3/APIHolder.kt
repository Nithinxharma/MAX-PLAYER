package com.lagradost.cloudstream3

import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.ExtractorApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object APIHolder {
    val unixTimeMS: Long get() = System.currentTimeMillis()
    val unixTime: Long get() = unixTimeMS / 1000L

    fun String.capitalize(): String = capitalizeString(this)

    val apis = CopyOnWriteArrayList<MainAPI>()
    val allProviders = CopyOnWriteArrayList<MainAPI>()
    val extractorApis = CopyOnWriteArrayList<ExtractorApi>()
    private val apiMap = ConcurrentHashMap<String, MainAPI>()
    private val _registerMainApiCallsCount = AtomicInteger(0)
    val registerMainApiCallsCount: Int get() = _registerMainApiCallsCount.get()

    var onApiAddedListener: ((MainAPI) -> Unit)? = null
    var onApiRemovedListener: ((MainAPI) -> Unit)? = null

    init {
        Log.i("APIHolder", "INSTANCE_IDENTITY: APIHolder initialized. identityHashCode=${System.identityHashCode(this)}")
        runCatching {
            com.lagradost.cloudstream3.extractors.DefaultExtractors.registerAll()
        }
    }

    fun addPlugin(api: MainAPI) {
        _registerMainApiCallsCount.incrementAndGet()
        Log.i("ExtensionManager", "INSTANCE_IDENTITY: APIHolder.addPlugin called on APIHolder@${System.identityHashCode(this)} for API ${api.name} (${api.mainUrl})")
        Log.i("ExtensionManager", "EXTENSION_LOAD: registerMainAPI called: ${api.name} (${api.mainUrl})")
        val existing = allProviders.find { it.name.equals(api.name, ignoreCase = true) || (it.mainUrl.isNotBlank() && it.mainUrl == api.mainUrl) }
        if (existing != null) {
            allProviders.remove(existing)
            apis.remove(existing)
        }
        allProviders.add(api)
        apis.add(api)
        apiMap[api.name] = api
        Log.i("APIHolder", "Registered Cloudstream API: ${api.name} (${api.mainUrl}) [Total active: ${allProviders.size}]")
        Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 13: Provider name after registration: '${api.name}' [mainUrl='${api.mainUrl}', totalRegistered=${allProviders.size}]")
        Log.i("ExtensionManager", "EXTENSION_LOAD: Provider registered: ${api.name}")
        runCatching { onApiAddedListener?.invoke(api) }
    }

    fun addPluginMapping(api: MainAPI) {
        addPlugin(api)
    }

    fun addExtractor(api: ExtractorApi) {
        Log.i("ExtensionManager", "EXTENSION_LOAD: registerExtractorAPI called: ${api.name} (${api.mainUrl})")
        val existing = extractorApis.find { it.name.equals(api.name, ignoreCase = true) && (it.mainUrl.isBlank() || it.mainUrl == api.mainUrl) }
        if (existing != null) {
            extractorApis.remove(existing)
        }
        extractorApis.add(api)
        Log.i("APIHolder", "Registered Extractor API: ${api.name} (${api.mainUrl}) [Total active: ${extractorApis.size}]")
    }

    fun removePlugin(api: MainAPI) {
        allProviders.remove(api)
        apis.remove(api)
        apiMap.remove(api.name)
        runCatching { onApiRemovedListener?.invoke(api) }
    }

    fun removePluginMapping(api: MainAPI) {
        removePlugin(api)
    }

    fun getApi(name: String): MainAPI? = getApiFromNameNull(name)
    
    fun getApiFromNameNull(apiName: String?): MainAPI? {
        // 1. Guard against null, empty, or blank string
        if (apiName.isNullOrBlank()) return null

        val cleanName = apiName.trim()
        val stripped = cleanName.removePrefix("cs3_").replace("_", " ")
        val targetClean = apiName.replace(" ", "")

        // Check map direct matches safely
        apiMap[cleanName]?.let { return it }
        apiMap[stripped]?.let { return it }

        // 2. Safely iterate through allProviders, guarding against null providers and null names
        return allProviders.firstOrNull { provider ->
            val providerName = provider?.name ?: return@firstOrNull false
            providerName.equals(cleanName, ignoreCase = true) ||
            providerName.equals(stripped, ignoreCase = true) ||
            providerName.replace(" ", "").equals(targetClean, ignoreCase = true)
        } ?: apis.firstOrNull { provider ->
            val providerName = provider?.name ?: return@firstOrNull false
            providerName.equals(cleanName, ignoreCase = true) ||
            providerName.equals(stripped, ignoreCase = true) ||
            providerName.replace(" ", "").equals(targetClean, ignoreCase = true)
        }
    }
    
    fun getApiFromNameOrNull(name: String?): MainAPI? = getApiFromNameNull(name)
    
    fun getApiFromName(name: String): MainAPI = getApiFromNameNull(name) ?: throw ErrorLoadingException("No API found with name $name")

    fun getExtractorApiFromName(name: String): ExtractorApi? = extractorApis.find { it.name.equals(name, ignoreCase = true) }
    
    fun getExtractorApiFromNameNull(name: String?): ExtractorApi? = if (name != null) extractorApis.find { it.name.equals(name, ignoreCase = true) } else null
    
    fun getExtractorForUrl(url: String): ExtractorApi? = com.lagradost.cloudstream3.utils.getExtractorForUrl(url)

    fun removePluginsBySource(sourceFilename: String) {
        allProviders.removeAll { it.sourcePlugin == sourceFilename }
        apis.removeAll { it.sourcePlugin == sourceFilename }
        apiMap.entries.removeIf { it.value.sourcePlugin == sourceFilename }
        extractorApis.removeAll { it.sourcePlugin == sourceFilename }
    }

    fun clear() {
        apis.clear()
        allProviders.clear()
        apiMap.clear()
        extractorApis.clear()
        _registerMainApiCallsCount.set(0)
    }
}
