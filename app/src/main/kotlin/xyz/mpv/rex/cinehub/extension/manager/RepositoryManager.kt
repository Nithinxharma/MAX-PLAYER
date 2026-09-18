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
                url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
                name = "CloudStream English & Anime Repository",
                description = "Official community repository with popular english movie, tv series, and anime providers."
            ),
            ExtensionRepo(
                url = "https://raw.githubusercontent.com/HatsuneMikuUwU/cloudstream-extensions-uwu/master/repo.json",
                name = "HatsuneMiku Community Providers",
                description = "Extended multimedia streaming sources and extractors."
            ),
            ExtensionRepo(
                url = "https://raw.githubusercontent.com/CakesTwix/cloudstream-extensions-uk/master/repo.json",
                name = "CloudStream Multilingual Repository",
                description = "Multilingual provider plugins covering global regions and languages."
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
        val repos = db.extensionDao().getAllRepositoriesSync().toMutableList()
        if (repos.isEmpty()) {
            val defaultPreset = POPULAR_PRESETS.first()
            db.extensionDao().insertRepository(defaultPreset)
            repos.add(defaultPreset)
        }
        val resultList = mutableListOf<RepositorySyncResult>()
        for (repo in repos) {
            resultList.add(syncRepository(repo.url))
        }
        resultList
    }

    private fun fetchUrlWithFallback(targetUrl: String): Pair<String, String> {
        val candidates = mutableListOf(targetUrl)
        if (targetUrl.contains("/builds/")) {
            candidates.add(targetUrl.replace("/builds/", "/master/"))
            candidates.add(targetUrl.replace("/builds/", "/main/"))
        } else if (targetUrl.contains("/master/")) {
            candidates.add(targetUrl.replace("/master/", "/builds/"))
            candidates.add(targetUrl.replace("/master/", "/main/"))
        } else if (targetUrl.contains("/main/")) {
            candidates.add(targetUrl.replace("/main/", "/master/"))
            candidates.add(targetUrl.replace("/main/", "/builds/"))
        }

        var lastEx: Exception? = null
        for (url in candidates.distinct()) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (CineHub-Extension-Engine/1.0)")
                    .build()
                val response = client.newCall(req).execute()
                if (response.isSuccessful && response.body != null) {
                    val body = response.body!!.string()
                    if (body.isNotBlank() && !body.contains("404: Not Found")) {
                        return Pair(url, body)
                    }
                }
            } catch (e: Exception) {
                lastEx = e
            }
        }
        throw lastEx ?: Exception("Failed to fetch repository from $targetUrl")
    }

    suspend fun syncRepository(repoUrl: String): RepositorySyncResult = withContext(Dispatchers.IO) {
        try {
            val (effectiveUrl, body) = fetchUrlWithFallback(repoUrl)

            val parsedPlugins = mutableListOf<AvailablePlugin>()
            var repoTitle = "Repository"
            var repoDesc: String? = null

            val existing = db.extensionDao().getRepositoryByUrl(repoUrl)
            if (existing != null && existing.name.isNotBlank()) {
                repoTitle = existing.name
                repoDesc = existing.description
            }

            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                repoTitle = json.optString("name", repoTitle)
                repoDesc = json.optString("description", repoDesc)

                if (json.has("pluginLists")) {
                    // CloudStream repo.json format
                    val lists = json.optJSONArray("pluginLists")
                    if (lists != null) {
                        for (i in 0 until lists.length()) {
                            val listUrl = lists.getString(i)
                            try {
                                val plugins = fetchPluginsList(listUrl, effectiveUrl)
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
                            parsedPlugins.add(parsePluginJson(p, effectiveUrl))
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                // Direct plugins.json format
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val p = array.getJSONObject(i)
                    parsedPlugins.add(parsePluginJson(p, effectiveUrl))
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
        val (_, listBody) = fetchUrlWithFallback(listUrl)
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
        val versionRaw = obj.opt("version")?.toString() ?: "1.0.0"
        val versionCode = obj.optInt("versionCode", versionRaw.toIntOrNull() ?: 1)
        val description = obj.optString("description", "").ifBlank { null }
        
        var url = obj.optString("url", obj.optString("jarUrl", obj.optString("file", "")))
        if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            val baseUrl = repoUrl.substringBeforeLast("/")
            url = "$baseUrl/$url"
        }

        val tvUrl = obj.optString("tvUrl", "").ifBlank { null }
        val iconUrl = obj.optString("iconUrl", obj.optString("icon", "")).ifBlank { null }

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
            version = versionRaw,
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
