package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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

    private val cacheFile = File(context.cacheDir, "plugin_cache.json")
    
    fun getAllRepositories(): Flow<List<ExtensionRepo>> {
        return db.extensionDao().getAllRepositories()
    }
    
    suspend fun addRepository(url: String, name: String) = withContext(Dispatchers.IO) {
        db.extensionDao().insertRepository(ExtensionRepo(url = url, name = name))
        syncRepository(url)
    }
    
    suspend fun removeRepository(url: String) = withContext(Dispatchers.IO) {
        val repo = db.extensionDao().getAllRepositoriesSync().find { it.url == url }
        if (repo != null) {
            db.extensionDao().deleteRepository(repo)
        }
    }
    
    suspend fun validateRepository(url: String): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                val plugins = parseRepositoryBody(body, url)
                if (plugins.isEmpty()) {
                    return@withContext Result.failure(Exception("No valid plugins found in repository"))
                }
                val repoName = runCatching {
                    if (body.trimStart().startsWith("{")) {
                        JSONObject(body).optString("name", "Repository")
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

    private fun parseRepositoryBody(body: String, repoUrl: String): List<AvailablePlugin> {
        val list = mutableListOf<AvailablePlugin>()
        val trimmed = body.trim()
        try {
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("plugins") -> obj.getJSONArray("plugins")
                    obj.has("pluginLists") -> obj.getJSONArray("pluginLists")
                    else -> JSONArray()
                }
            } else {
                JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val plugin = AvailablePlugin(
                    internalName = item.optString("internalName"),
                    name = item.optString("name"),
                    version = item.optString("version"),
                    versionCode = item.optInt("versionCode", 1),
                    description = item.optString("description"),
                    iconUrl = item.optString("iconUrl"),
                    authors = item.optJSONArray("authors")?.let { arr ->
                        List(arr.length()) { arr.optString(it) }
                    } ?: emptyList(),
                    url = item.optString("url"),
                    repositoryUrl = repoUrl
                )
                if (plugin.internalName.isNotBlank() && plugin.name.isNotBlank()) {
                    list.add(plugin)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing repo json: ${e.message}")
        }
        return list
    }
    
    private fun saveCache(plugins: List<AvailablePlugin>) {
        val array = JSONArray()
        plugins.forEach { p ->
            val obj = JSONObject()
            obj.put("internalName", p.internalName)
            obj.put("name", p.name)
            obj.put("version", p.version)
            obj.put("versionCode", p.versionCode)
            obj.put("description", p.description)
            obj.put("iconUrl", p.iconUrl)
            obj.put("url", p.url)
            obj.put("repositoryUrl", p.repositoryUrl)
            array.put(obj)
        }
        cacheFile.writeText(array.toString())
    }
    
    fun getCachedPlugins(): List<AvailablePlugin> {
        if (!cacheFile.exists()) return emptyList()
        val list = mutableListOf<AvailablePlugin>()
        try {
            val array = JSONArray(cacheFile.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AvailablePlugin(
                        internalName = obj.optString("internalName"),
                        name = obj.optString("name"),
                        version = obj.optString("version"),
                        versionCode = obj.optInt("versionCode", 1),
                        description = obj.optString("description"),
                        iconUrl = obj.optString("iconUrl"),
                        authors = emptyList(),
                        url = obj.optString("url"),
                        repositoryUrl = obj.optString("repositoryUrl")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
    
    fun clearCache() {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }
}
data class PresetRepo(val name: String, val url: String, val description: String?)
