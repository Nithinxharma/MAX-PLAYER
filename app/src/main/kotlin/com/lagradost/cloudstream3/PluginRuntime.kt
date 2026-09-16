package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
    val apis = mutableListOf<MainAPI>()
    private val apiMap = ConcurrentHashMap<String, MainAPI>()

    fun addPlugin(api: MainAPI) {
        if (!apis.contains(api)) {
            apis.add(api)
            apiMap[api.name] = api
            Log.i("APIHolder", "Registered Cloudstream API: ${api.name} (${api.mainUrl})")
        }
    }

    fun removePlugin(api: MainAPI) {
        apis.remove(api)
        apiMap.remove(api.name)
    }

    fun getApi(name: String): MainAPI? = apiMap[name]

    fun clear() {
        apis.clear()
        apiMap.clear()
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
    private val optDir = File(context.codeCacheDir, "cloudstream_opt").apply { mkdirs() }

    fun loadPluginFile(pluginFile: File): List<MainAPI> {
        val loadedApis = mutableListOf<MainAPI>()
        if (!pluginFile.exists() || !pluginFile.canRead()) {
            Log.e("PluginManager", "Cannot read plugin file: ${pluginFile.absolutePath}")
            return loadedApis
        }

        val classNames = mutableListOf<String>()
        try {
            ZipFile(pluginFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: zip.getEntry("make.json")
                if (manifestEntry != null) {
                    val rawJson = zip.getInputStream(manifestEntry).bufferedReader().readText()
                    val json = JSONObject(rawJson)
                    val mainClass = json.optString("pluginClassName")
                    if (!mainClass.isNullOrBlank()) classNames.add(mainClass)
                    val classesArr = json.optJSONArray("classes")
                    if (classesArr != null) {
                        for (i in 0 until classesArr.length()) {
                            val c = classesArr.optString(i)
                            if (c.isNotBlank() && !classNames.contains(c)) classNames.add(c)
                        }
                    }
                }
            }

            val classLoader = DexClassLoader(
                pluginFile.absolutePath,
                optDir.absolutePath,
                null,
                context.classLoader
            )

            for (className in classNames) {
                try {
                    val clazz = classLoader.loadClass(className)
                    val instance = clazz.getDeclaredConstructor().newInstance()
                    if (instance is MainAPI) {
                        APIHolder.addPlugin(instance)
                        loadedApis.add(instance)
                    }
                } catch (t: Throwable) {
                    Log.w("PluginManager", "Failed instantiating $className from ${pluginFile.name}", t)
                }
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Error parsing plugin ${pluginFile.name}", e)
        }
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
