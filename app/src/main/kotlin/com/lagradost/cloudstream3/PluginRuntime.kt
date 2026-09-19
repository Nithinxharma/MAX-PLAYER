package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.utils.ExtractorApi
import dalvik.system.DexClassLoader
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@SuppressLint("StaticFieldLeak")
object AcraApplication {
    lateinit var context: Context
        private set

    fun init(appContext: Context) {
        context = appContext.applicationContext
    }
}

object APIHolder {
    val unixTimeMS: Long get() = System.currentTimeMillis()
    val unixTime: Long get() = unixTimeMS / 1000L

    val apis = java.util.concurrent.CopyOnWriteArrayList<MainAPI>()
    val allProviders = java.util.concurrent.CopyOnWriteArrayList<MainAPI>()
    val extractorApis = java.util.concurrent.CopyOnWriteArrayList<ExtractorApi>()
    private val apiMap = ConcurrentHashMap<String, MainAPI>()
    private val _registerMainApiCallsCount = java.util.concurrent.atomic.AtomicInteger(0)
    val registerMainApiCallsCount: Int get() = _registerMainApiCallsCount.get()

    var onApiAddedListener: ((MainAPI) -> Unit)? = null
    var onApiRemovedListener: ((MainAPI) -> Unit)? = null

    fun addPlugin(api: MainAPI) {
        _registerMainApiCallsCount.incrementAndGet()
        Log.i("ExtensionManager", "EXTENSION_LOAD: registerMainAPI called: ${api.name} (${api.mainUrl})")
        // Replace existing entry with same name if already present, or add new
        val existing = allProviders.find { it.name.equals(api.name, ignoreCase = true) || (it.mainUrl.isNotBlank() && it.mainUrl == api.mainUrl) }
        if (existing != null) {
            allProviders.remove(existing)
            apis.remove(existing)
        }
        allProviders.add(api)
        apis.add(api)
        apiMap[api.name] = api
        Log.i("APIHolder", "Registered Cloudstream API: ${api.name} (${api.mainUrl}) [Total active: ${allProviders.size}]")
        Log.i("ExtensionManager", "EXTENSION_LOAD: Provider registered: ${api.name}")
        runCatching { onApiAddedListener?.invoke(api) }
    }

    fun addExtractor(api: ExtractorApi) {
        Log.i("ExtensionManager", "EXTENSION_LOAD: registerExtractorAPI called: ${api.name} (${api.mainUrl})")
        val existing = extractorApis.find { it.name.equals(api.name, ignoreCase = true) || (it.mainUrl.isNotBlank() && it.mainUrl == api.mainUrl) }
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

    fun getApi(name: String): MainAPI? = apiMap[name]

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

object CloudstreamHttp {
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val defaultCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            synchronized(hostCookies) {
                cookies.forEach { cookie ->
                    hostCookies.removeAll { it.name == cookie.name }
                    hostCookies.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val hostCookies = cookieStore[url.host] ?: return emptyList()
            synchronized(hostCookies) {
                hostCookies.removeAll { it.expiresAt < now }
                return ArrayList(hostCookies)
            }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(defaultCookieJar)
        .addInterceptor { chain ->
            val req = chain.request()
            val builder = req.newBuilder()
            if (req.header("User-Agent").isNullOrBlank()) {
                builder.header("User-Agent", DEFAULT_USER_AGENT)
            }
            chain.proceed(builder.build())
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

class PluginManager(private val context: Context) {
    private val pluginDir = File(context.filesDir, "cloudstream_plugins").apply { mkdirs() }
    
    // We keep this return type to avoid breaking other files temporarily,
    // though real APIHolder holds everything now.
    fun loadPluginFile(pluginFile: File): List<MainAPI> {
        val loadedApis = mutableListOf<MainAPI>()
        if (!pluginFile.exists() || !pluginFile.canRead()) {
            Log.e("PluginManager", "Cannot read plugin file: ${pluginFile.absolutePath}")
            return loadedApis
        }

        val classNames = mutableListOf<String>()
        var requiresResources = false
        var manifestVersion: Int? = null

        try {
            java.util.zip.ZipFile(pluginFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: zip.getEntry("make.json")
                if (manifestEntry != null) {
                    val rawJson = zip.getInputStream(manifestEntry).bufferedReader().readText()
                    val json = org.json.JSONObject(rawJson)
                    val mainClass = json.optString("pluginClassName")
                    if (!mainClass.isNullOrBlank()) classNames.add(mainClass)
                    val classesArr = json.optJSONArray("classes")
                    if (classesArr != null) {
                        for (i in 0 until classesArr.length()) {
                            val c = classesArr.optString(i)
                            if (c.isNotBlank() && !classNames.contains(c)) classNames.add(c)
                        }
                    }
                    requiresResources = json.optBoolean("requiresResources", false)
                    manifestVersion = if (json.has("version")) json.optInt("version") else null
                }
            }

            val classLoader = dalvik.system.PathClassLoader(
                pluginFile.absolutePath,
                context.classLoader
            )

            for (className in classNames) {
                try {
                    val pluginClass = classLoader.loadClass(className) as Class<out com.lagradost.cloudstream3.plugins.BasePlugin>
                    val pluginInstance = pluginClass.getDeclaredConstructor().newInstance()
                    
                    pluginInstance.filename = pluginFile.absolutePath

                    if (requiresResources && pluginInstance is com.lagradost.cloudstream3.plugins.Plugin) {
                        try {
                            val assets = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                            val addAssetPath = android.content.res.AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                            addAssetPath.invoke(assets, pluginFile.absolutePath)
                            pluginInstance.resources = android.content.res.Resources(
                                assets,
                                context.resources.displayMetrics,
                                context.resources.configuration
                            )
                        } catch(e: Exception) {
                            Log.e("PluginManager", "Failed to load resources for ${pluginFile.name}", e)
                        }
                    }

                    if (pluginInstance is com.lagradost.cloudstream3.plugins.Plugin) {
                        pluginInstance.load(context)
                    } else {
                        pluginInstance.load()
                    }
                    
                    println("Successfully loaded $className from ${pluginFile.name}")

                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Error parsing plugin ${pluginFile.name}", e)
        }
        
        // As APIs were automatically added to APIHolder by BasePlugin.registerMainAPI,
        // we can fetch the ones sourced from this file to fulfill the legacy return signature.
        loadedApis.addAll(APIHolder.apis.filter { it.sourcePlugin == pluginFile.absolutePath })
        
        return loadedApis
    }

    suspend fun downloadAndLoadPlugin(url: String, filename: String): List<MainAPI> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val destination = File(pluginDir, if (filename.endsWith(".cs3")) filename else "$filename.cs3")
            val request = Request.Builder().url(url).build()
            CloudstreamHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    Log.e("PluginManager", "Failed downloading $url (HTTP ${response.code})")
                    return@withContext emptyList()
                }
                destination.outputStream().use { out ->
                    response.body!!.byteStream().copyTo(out)
                }
            }
            loadPluginFile(destination)
        }
}
