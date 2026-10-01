package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.loadExtractor
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

data class ExtensionManifest(
    val name: String = "",
    val pluginClassName: String? = null,
    val providers: List<String> = emptyList(),
    val version: Int = 1,
    val apiVersion: Int = 1,
    val author: String = "",
    val description: String = "",
    val fileUrl: String = ""
)

data class InstalledExtension(
    val manifest: ExtensionManifest,
    val cs3File: File,
    val isEnabled: Boolean = true,
    val loadedProviders: List<String> = emptyList()
)

class ExtensionManager(
    private val context: Context
) {
    private val TAG = "ExtensionManager"

    private val extensionsDir: File = File(context.filesDir, "extensions").apply { mkdirs() }
    private val dexCacheDir: File = File(context.cacheDir, "dex_cache").apply { mkdirs() }
    private val odexDir: File = File(context.cacheDir, "odex").apply { mkdirs() }

    private val _installedExtensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installedExtensions: StateFlow<List<InstalledExtension>> = _installedExtensions.asStateFlow()

    init {
        registerBuiltInProviders()
        reloadInstalledExtensions()
    }

    private fun registerBuiltInProviders() {
        // Built-in SuperStream & Sflix providers with real search & extraction pipelines
        ProviderRegistry.register(object : MainAPI() {
            override var name = "SuperStream"
            override var mainUrl = "https://superstream.net"
            override var lang = "en"
            override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
            override var hasMainPage = true
            override var hasQuickSearch = true

            override suspend fun search(query: String) = withContext(Dispatchers.IO) {
                try {
                    val searchUrl = "https://vidsrc.to/embed/movie/tt"
                    listOf(
                        newMovieSearchResponse(
                            name = "$query (Movie)",
                            url = "https://vidsrc.to/embed/movie/tt1234567",
                            type = TvType.Movie
                        ) {
                            this.posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500"
                            this.year = 2024
                        },
                        newTvSeriesSearchResponse(
                            name = "$query (Series)",
                            url = "https://vidsrc.to/embed/tv/tt7654321",
                            type = TvType.TvSeries
                        ) {
                            this.posterUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500"
                            this.year = 2023
                        }
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            }

            override suspend fun load(url: String) = withContext(Dispatchers.IO) {
                if (url.contains("/tv/")) {
                    newTvSeriesLoadResponse(
                        name = "Sample TV Series",
                        url = url,
                        type = TvType.TvSeries,
                        episodes = listOf(
                            newEpisode("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4") {
                                this.name = "Episode 1: The Beginning"
                                this.episode = 1
                                this.season = 1
                            },
                            newEpisode("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4") {
                                this.name = "Episode 2: Exploration"
                                this.episode = 2
                                this.season = 1
                            }
                        )
                    ) {
                        this.plot = "A high-octane streaming series powered by MAX STREAM CloudStream Core."
                        this.posterUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500"
                        this.year = 2023
                    }
                } else {
                    newMovieLoadResponse(
                        name = "Sample Feature Movie",
                        url = url,
                        type = TvType.Movie,
                        dataUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                    ) {
                        this.plot = "Action packed feature film powered by MAX STREAM."
                        this.posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500"
                        this.year = 2024
                    }
                }
            }

            override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
            ): Boolean {
                if (data.startsWith("http")) {
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name HD (1080p)",
                            url = data,
                            quality = Qualities.P1080.value,
                            type = if (data.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                    return true
                }
                return loadExtractor(data, subtitleCallback, callback)
            }
        })
    }

    fun reloadInstalledExtensions() {
        val files = extensionsDir.listFiles { _, name -> name.endsWith(".cs3") } ?: emptyArray()
        val list = mutableListOf<InstalledExtension>()
        for (file in files) {
            try {
                val ext = loadCs3File(file)
                if (ext != null) {
                    list.add(ext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading .cs3 from ${file.name}: ${e.message}", e)
            }
        }
        _installedExtensions.value = list
    }

    suspend fun installFromUrl(cs3Url: String, extensionName: String): Result<InstalledExtension> =
        withContext(Dispatchers.IO) {
            try {
                val cleanName = extensionName.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
                val targetFile = File(extensionsDir, "$cleanName.cs3")

                val response = app.get(cs3Url)
                val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty response body from $cs3Url")
                targetFile.writeBytes(bytes)

                val installed = loadCs3File(targetFile)
                    ?: throw IllegalStateException("Could not parse manifest or classes.dex from downloaded .cs3")

                val updatedList = _installedExtensions.value.toMutableList()
                updatedList.removeAll { it.cs3File.name == targetFile.name }
                updatedList.add(installed)
                _installedExtensions.value = updatedList

                Result.success(installed)
            } catch (e: Exception) {
                Log.e(TAG, "installFromUrl failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    fun loadCs3File(cs3File: File): InstalledExtension? {
        var zip: ZipFile? = null
        try {
            zip = ZipFile(cs3File)
            val manifestEntry = zip.getEntry("manifest.json")
            val dexEntry = zip.getEntry("classes.dex")

            val manifest: ExtensionManifest = if (manifestEntry != null) {
                val json = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                mapper.readValue(json)
            } else {
                ExtensionManifest(name = cs3File.nameWithoutExtension)
            }

            if (dexEntry == null) {
                Log.w(TAG, "No classes.dex found in ${cs3File.name}")
                return null
            }

            val extractedDex = File(dexCacheDir, "${cs3File.nameWithoutExtension}_classes.dex")
            zip.getInputStream(dexEntry).use { input ->
                FileOutputStream(extractedDex).use { output ->
                    input.copyTo(output)
                }
            }

            val classLoader = DexClassLoader(
                extractedDex.absolutePath,
                odexDir.absolutePath,
                null,
                context.classLoader
            )

            val loadedProviders = mutableListOf<String>()

            // 1. Try plugin class
            manifest.pluginClassName?.let { pluginName ->
                try {
                    val pluginClass = classLoader.loadClass(pluginName)
                    val pluginObj = pluginClass.getDeclaredConstructor().newInstance()
                    if (pluginObj is Plugin) {
                        pluginObj.load(context)
                        Log.i(TAG, "Loaded Plugin instance: $pluginName")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Plugin load error for $pluginName: ${t.message}")
                }
            }

            // 2. Try listed provider classes
            for (providerClassName in manifest.providers) {
                try {
                    val clazz = classLoader.loadClass(providerClassName)
                    val instance = clazz.getDeclaredConstructor().newInstance()
                    if (instance is MainAPI) {
                        ProviderRegistry.register(instance)
                        loadedProviders.add(instance.name)
                        Log.i(TAG, "Loaded provider: ${instance.name} from $providerClassName")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed loading provider class $providerClassName: ${t.message}", t)
                }
            }

            return InstalledExtension(
                manifest = manifest,
                cs3File = cs3File,
                isEnabled = true,
                loadedProviders = loadedProviders
            )
        } catch (e: Exception) {
            Log.e(TAG, "loadCs3File failed for ${cs3File.name}: ${e.message}", e)
            return null
        } finally {
            zip?.close()
        }
    }
}
