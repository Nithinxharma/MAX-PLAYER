cat << 'INNER_EOF' > app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/manager/RepositoryManager.kt
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
    
    suspend fun syncRepository(url: String) = withContext(Dispatchers.IO) {
        syncAllRepositories() // Simplified for now
    }
    
    suspend fun syncAllRepositories() = withContext(Dispatchers.IO) {
        val repos = db.extensionDao().getAllRepositoriesSync()
        val allPlugins = mutableListOf<AvailablePlugin>()
        
        for (repo in repos) {
            try {
                val request = Request.Builder().url(repo.url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val plugin = AvailablePlugin(
                            internalName = obj.optString("internalName"),
                            name = obj.optString("name"),
                            version = obj.optString("version"),
                            versionCode = obj.optInt("versionCode", 1),
                            description = obj.optString("description"),
                            iconUrl = obj.optString("iconUrl"),
                            authors = obj.optJSONArray("authors")?.let { arr ->
                                List(arr.length()) { arr.optString(it) }
                            } ?: emptyList(),
                            url = obj.optString("url"),
                            repositoryUrl = repo.url
                        )
                        allPlugins.add(plugin)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync repo ${repo.name}", e)
            }
        }
        
        saveCache(allPlugins)
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
INNER_EOF
