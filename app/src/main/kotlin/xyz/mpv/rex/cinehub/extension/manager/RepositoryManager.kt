package xyz.mpv.rex.cinehub.extension.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.cinehub.extension.model.RepositorySyncResult
import xyz.mpv.rex.database.MpvExDatabase
import java.util.concurrent.ConcurrentHashMap

/**
 * RepositoryManager manages remote extension repositories,
 * manifest synchronization, and plugin catalog discovery.
 */
class RepositoryManager(
    private val client: OkHttpClient,
    private val db: MpvExDatabase
) {
    private val pluginCache = ConcurrentHashMap<String, List<AvailablePlugin>>()

    companion object {
        val POPULAR_PRESETS = listOf(
            ExtensionRepo(
                url = "https://raw.githubusercontent.com/recloudstream/extensions/builds/repo.json",
                name = "CloudStream English Repository",
                description = "Official community repository with popular english provider extensions."
            ),
            ExtensionRepo(
                url = "https://raw.githubusercontent.com/recloudstream/multilingual-providers/builds/repo.json",
                name = "CloudStream Multilingual Repository",
                description = "Multilingual provider plugins covering global regions and languages."
            ),
            ExtensionRepo(
                url = "https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/repo.json",
                name = "Hexated Community Providers",
                description = "High quality multimedia sources and extractors."
            )
        )
    }

    fun getAllRepositories(): Flow<List<ExtensionRepo>> {
        return db.extensionDao().getAllRepositories()
    }

    suspend fun addRepository(url: String, name: String? = null, description: String? = null): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return@withContext false

        val initialName = name?.ifBlank { null } ?: "Repository (${cleanUrl.takeLast(24)})"
        val repo = ExtensionRepo(
            url = cleanUrl,
            name = initialName,
            description = description,
            lastSync = System.currentTimeMillis()
        )
        db.extensionDao().insertRepository(repo)
        syncRepository(cleanUrl)
        true
    }

    suspend fun removeRepository(repo: ExtensionRepo) = withContext(Dispatchers.IO) {
        db.extensionDao().deleteRepository(repo)
        pluginCache.remove(repo.url)
    }

    suspend fun syncAllRepositories(): List<RepositorySyncResult> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<ExtensionRepo>()
        val resultList = mutableListOf<RepositorySyncResult>()
        // Collect current list
        val currentRepos = db.extensionDao().getAllRepositories()
        // Synchronous snapshot
        val enabledExts = db.extensionDao().getEnabledExtensionsSync()
        // Iterate through DB repositories
        val allRepos = db.openHelper.readableDatabase.query("SELECT url, name, description, lastSync FROM extension_repositories")
        try {
            while (allRepos.moveToNext()) {
                val url = allRepos.getString(0)
                val name = allRepos.getString(1)
                val desc = allRepos.getString(2)
                val sync = allRepos.getLong(3)
                repos.add(ExtensionRepo(url, name, desc, sync))
            }
        } finally {
            allRepos.close()
        }

        for (repo in repos) {
            resultList.add(syncRepository(repo.url))
        }
        resultList
    }

    suspend fun syncRepository(repoUrl: String): RepositorySyncResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(repoUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (CineHub-Extension-Engine/1.0)")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
                response.body?.string() ?: throw Exception("Empty repository response")
            }

            val parsedPlugins = mutableListOf<AvailablePlugin>()
            var repoTitle = "Repository"
            var repoDesc: String? = null

            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                repoTitle = json.optString("name", repoTitle)
                repoDesc = json.optString("description", null)

                if (json.has("pluginLists")) {
                    // CloudStream repo.json format
                    val lists = json.optJSONArray("pluginLists")
                    if (lists != null) {
                        for (i in 0 until lists.length()) {
                            val listUrl = lists.getString(i)
                            try {
                                val plugins = fetchPluginsList(listUrl, repoUrl)
                                parsedPlugins.addAll(plugins)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } else if (json.has("providers")) {
                    // Direct providers format
                    val providers = json.optJSONArray("providers")
                    if (providers != null) {
                        for (i in 0 until providers.length()) {
                            val p = providers.getJSONObject(i)
                            parsedPlugins.add(parsePluginJson(p, repoUrl))
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                // Direct plugins.json format
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val p = array.getJSONObject(i)
                    parsedPlugins.add(parsePluginJson(p, repoUrl))
                }
            }

            // Update database record with parsed name & timestamp
            db.extensionDao().insertRepository(
                ExtensionRepo(
                    url = repoUrl,
                    name = repoTitle,
                    description = repoDesc,
                    lastSync = System.currentTimeMillis()
                )
            )

            pluginCache[repoUrl] = parsedPlugins
            RepositorySyncResult(repoUrl, repoTitle, parsedPlugins)
        } catch (e: Exception) {
            e.printStackTrace()
            RepositorySyncResult(repoUrl, "Failed Sync", emptyList(), e.localizedMessage)
        }
    }

    private fun fetchPluginsList(listUrl: String, repoUrl: String): List<AvailablePlugin> {
        val req = Request.Builder()
            .url(listUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (CineHub-Extension-Engine/1.0)")
            .build()
        val listBody = client.newCall(req).execute().use { it.body?.string() ?: "" }
        if (listBody.isBlank()) return emptyList()

        val results = mutableListOf<AvailablePlugin>()
        val array = JSONArray(listBody)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            results.add(parsePluginJson(obj, repoUrl))
        }
        return results
    }

    private fun parsePluginJson(obj: JSONObject, repoUrl: String): AvailablePlugin {
        val name = obj.optString("name", "Unknown Plugin")
        val internalName = obj.optString("internalName", obj.optString("id", name.lowercase().replace(" ", "_")))
        val version = obj.optString("version", "1.0.0")
        val versionCode = obj.optInt("versionCode", 1)
        val description = obj.optString("description", null)
        var url = obj.optString("url", "")
        var tvUrl = obj.optString("tvUrl", null)
        var iconUrl = obj.optString("iconUrl", obj.optString("icon", null))

        // Resolve relative URLs if needed
        val baseUrl = repoUrl.substringBeforeLast("/") + "/"
        if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = baseUrl + url.removePrefix("./").removePrefix("/")
        }
        if (tvUrl != null && tvUrl.isNotBlank() && !tvUrl.startsWith("http://") && !tvUrl.startsWith("https://")) {
            tvUrl = baseUrl + tvUrl.removePrefix("./").removePrefix("/")
        }
        if (iconUrl != null && iconUrl.isNotBlank() && !iconUrl.startsWith("http://") && !iconUrl.startsWith("https://")) {
            iconUrl = baseUrl + iconUrl.removePrefix("./").removePrefix("/")
        }

        val authors = mutableListOf<String>()
        val authorsArr = obj.optJSONArray("authors")
        if (authorsArr != null) {
            for (j in 0 until authorsArr.length()) {
                authors.add(authorsArr.getString(j))
            }
        } else if (obj.has("author")) {
            authors.add(obj.optString("author"))
        }

        val tvTypes = mutableListOf<String>()
        val typesArr = obj.optJSONArray("tvTypes")
        if (typesArr != null) {
            for (j in 0 until typesArr.length()) {
                tvTypes.add(typesArr.getString(j))
            }
        }

        return AvailablePlugin(
            name = name,
            internalName = internalName,
            version = version,
            versionCode = versionCode,
            description = description,
            url = url,
            tvUrl = tvUrl,
            iconUrl = iconUrl,
            authors = authors,
            tvTypes = tvTypes,
            repositoryUrl = repoUrl
        )
    }

    fun getCachedPlugins(): List<AvailablePlugin> {
        return pluginCache.values.flatten()
    }

    fun getCachedPluginsForRepo(repoUrl: String): List<AvailablePlugin> {
        return pluginCache[repoUrl] ?: emptyList()
    }

    fun clearCache() {
        pluginCache.clear()
    }
}
