package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File

class RepositoryManager(
    private val context: Context,
    private val db: MpvExDatabase,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "CineHub:RepoManager"
        val POPULAR_PRESETS = listOf(
            PresetRepo("English Providers", "https://raw.githubusercontent.com/recloudstream/cloudstream-extensions/builds/repo.json", "Official English"),
            PresetRepo("Hexated Providers", "https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/repo.json", "Hexated Plugins")
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        prettyPrint = false
    }

    private val cacheFile = File(context.cacheDir, "plugin_cache.json")
    
    fun getAllRepositories(): Flow<List<ExtensionRepo>> {
        return db.extensionDao().getAllRepositories()
    }
    
    suspend fun addRepository(url: String, name: String) = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val finalName = name.trim().ifBlank { "Custom Repo" }
        db.extensionDao().insertRepository(ExtensionRepo(url = trimmedUrl, name = finalName))
        syncRepository(trimmedUrl)
    }
    
    suspend fun removeRepository(url: String) = withContext(Dispatchers.IO) {
        val repo = db.extensionDao().getAllRepositoriesSync().find { it.url == url }
        if (repo != null) {
            db.extensionDao().deleteRepository(repo)
        }
    }
    
    suspend fun validateRepository(url: String): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = url.trim()
            val request = Request.Builder().url(trimmedUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                val plugins = parseRepositoryBody(body, trimmedUrl)
                if (plugins.isEmpty()) {
                    return@withContext Result.failure(Exception("No valid plugins found in repository"))
                }
                val repoName = runCatching {
                    val element = json.parseToJsonElement(body.trim())
                    if (element is JsonObject) {
                        element["name"]?.jsonPrimitive?.contentOrNull ?: "Repository"
                    } else "Repository"
                }.getOrDefault("Repository")
                Result.success(Pair(repoName, plugins.size))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncRepository(url: String) = withContext(Dispatchers.IO) {
        syncAllRepositories()
    }
    
    suspend fun syncAllRepositories() = withContext(Dispatchers.IO) {
        val repos = db.extensionDao().getAllRepositoriesSync()
        val allPlugins = mutableListOf<AvailablePlugin>()
        
        for (repo in repos) {
            try {
                val request = Request.Builder().url(repo.url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        allPlugins.addAll(parseRepositoryBody(body, repo.url))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync repo ${repo.name}", e)
            }
        }
        
        saveCache(allPlugins)
    }

    private suspend fun parseRepositoryBody(body: String, repoUrl: String): List<AvailablePlugin> {
        val list = mutableListOf<AvailablePlugin>()
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return list

        try {
            val rootElement = json.parseToJsonElement(trimmed)
            when (rootElement) {
                is JsonArray -> {
                    list.addAll(parsePluginArray(rootElement, repoUrl))
                }
                is JsonObject -> {
                    // Check for "plugins"
                    val pluginsElem = rootElement["plugins"]
                    if (pluginsElem is JsonArray) {
                        list.addAll(parsePluginArray(pluginsElem, repoUrl))
                    } else if (pluginsElem is JsonObject) {
                        list.addAll(parsePluginObjectMap(pluginsElem, repoUrl))
                    }

                    // Check for "pluginLists" (List of URLs pointing to plugins.json)
                    val pluginListsElem = rootElement["pluginLists"]
                    if (pluginListsElem is JsonArray) {
                        for (item in pluginListsElem) {
                            val listUrlStr = item.jsonPrimitive.contentOrNull
                            if (!listUrlStr.isNullOrBlank()) {
                                val fullUrl = resolveUrl(repoUrl, listUrlStr)
                                try {
                                    val req = Request.Builder().url(fullUrl).build()
                                    client.newCall(req).execute().use { resp ->
                                        if (resp.isSuccessful) {
                                            val subBody = resp.body?.string()
                                            if (!subBody.isNullOrBlank()) {
                                                val subElem = json.parseToJsonElement(subBody.trim())
                                                if (subElem is JsonArray) {
                                                    list.addAll(parsePluginArray(subElem, fullUrl))
                                                } else if (subElem is JsonObject) {
                                                    val subPlugins = subElem["plugins"]
                                                    if (subPlugins is JsonArray) {
                                                        list.addAll(parsePluginArray(subPlugins, fullUrl))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to fetch plugins from $fullUrl", e)
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing repo json: ${e.message}", e)
        }
        return list
    }

    private fun parsePluginArray(array: JsonArray, baseUrl: String): List<AvailablePlugin> {
        val result = mutableListOf<AvailablePlugin>()
        for (item in array) {
            if (item is JsonObject) {
                parseSinglePlugin(item, baseUrl)?.let { result.add(it) }
            }
        }
        return result
    }

    private fun parsePluginObjectMap(obj: JsonObject, baseUrl: String): List<AvailablePlugin> {
        val result = mutableListOf<AvailablePlugin>()
        for ((_, value) in obj) {
            if (value is JsonObject) {
                parseSinglePlugin(value, baseUrl)?.let { result.add(it) }
            }
        }
        return result
    }

    private fun parseSinglePlugin(item: JsonObject, baseUrl: String): AvailablePlugin? {
        val internalName = item["internalName"]?.jsonPrimitive?.contentOrNull
            ?: item["name"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val name = item["name"]?.jsonPrimitive?.contentOrNull
            ?: internalName
        if (internalName.isBlank() || name.isBlank()) return null

        val rawUrl = item["url"]?.jsonPrimitive?.contentOrNull ?: ""
        val resolvedUrl = if (rawUrl.isNotBlank()) resolveUrl(baseUrl, rawUrl) else ""

        val rawIconUrl = item["iconUrl"]?.jsonPrimitive?.contentOrNull
        val resolvedIconUrl = if (!rawIconUrl.isNullOrBlank()) resolveUrl(baseUrl, rawIconUrl) else null

        val authors = runCatching {
            item["authors"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull() ?: emptyList()

        val tvTypes = runCatching {
            item["tvTypes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull() ?: emptyList()

        val versionCode = runCatching {
            item["versionCode"]?.jsonPrimitive?.intOrNull
        }.getOrNull() ?: 1

        val version = item["version"]?.jsonPrimitive?.contentOrNull ?: "1.0.0"
        val description = item["description"]?.jsonPrimitive?.contentOrNull
        val tvUrl = item["tvUrl"]?.jsonPrimitive?.contentOrNull?.let { resolveUrl(baseUrl, it) }

        return AvailablePlugin(
            internalName = internalName,
            name = name,
            version = version,
            versionCode = versionCode,
            description = description,
            iconUrl = resolvedIconUrl,
            authors = authors,
            tvTypes = tvTypes,
            url = resolvedUrl,
            tvUrl = tvUrl,
            repositoryUrl = baseUrl
        )
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            val parsedBase = baseUrl.toHttpUrlOrNull()
            parsedBase?.resolve(trimmed)?.toString() ?: trimmed
        } catch (e: Exception) {
            if (baseUrl.contains("/")) {
                baseUrl.substringBeforeLast('/') + "/" + trimmed.trimStart('/')
            } else trimmed
        }
    }
    
    private fun saveCache(plugins: List<AvailablePlugin>) {
        try {
            val text = json.encodeToString(plugins)
            cacheFile.writeText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save plugin cache", e)
        }
    }
    
    fun getCachedPlugins(): List<AvailablePlugin> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            val text = cacheFile.readText().trim()
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<AvailablePlugin>>(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached plugins", e)
            emptyList()
        }
    }
    
    fun clearCache() {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }
}

data class PresetRepo(val name: String, val url: String, val description: String?)
