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
import xyz.mpv.rex.cinehub.extension.model.MegaRepoInstallResult
import xyz.mpv.rex.cinehub.extension.model.RepoPresetItem
import xyz.mpv.rex.cinehub.extension.model.RepoVerificationResult
import xyz.mpv.rex.cinehub.extension.model.RepositorySyncResult
import xyz.mpv.rex.database.MpvExDatabase
import java.util.concurrent.ConcurrentHashMap

/**
 * RepositoryManager manages remote extension repositories,
 * manifest synchronization, and plugin catalog discovery.
 */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

class RepositoryManager(
    private val client: OkHttpClient,
    private val db: MpvExDatabase
) {
    private val pluginCache = ConcurrentHashMap<String, List<AvailablePlugin>>()

    var onRepositorySynced: (suspend (RepositorySyncResult) -> Unit)? = null

    companion object {
        val BUILT_IN_PRESETS = listOf(
            RepoPresetItem(
                name = "CloudStream Providers Repo",
                url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
                description = "Official core CloudStream providers extension repository.",
                author = "CloudStream Team"
            ),
            RepoPresetItem(
                name = "MegaRepo",
                url = "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
                description = "Master multi-repository index aggregating all top community extension sources.",
                author = "Self-Similarity"
            ),
            RepoPresetItem(
                name = "Phisher Repo",
                url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
                description = "High-speed scrapers, Hindi, English, and multi-source streaming providers.",
                author = "Phisher98"
            ),
            RepoPresetItem(
                name = "doGior's Had Enough",
                url = "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json",
                description = "Curated collection of resilient streaming providers and extractors.",
                author = "doGior"
            ),
            RepoPresetItem(
                name = "CakesTwix Provider",
                url = "https://raw.githubusercontent.com/CakesTwix/cloudstream-extensions-uk/master/repo.json",
                description = "UK & International premium movie, series, and live streaming providers.",
                author = "CakesTwix"
            ),
            RepoPresetItem(
                name = "Saimuel Repo",
                url = "https://raw.githubusercontent.com/saimuelbr/saimuelrepo/refs/heads/main/builds/repo.json",
                description = "South American, Portuguese, and international provider sources.",
                author = "Saimuel"
            ),
            RepoPresetItem(
                name = "CNCVerse Repository",
                url = "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json",
                description = "CNC community multimedia sources, movies, and TV shows.",
                author = "NivinCNC"
            ),
            RepoPresetItem(
                name = "Megix Repo (CSX)",
                url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
                description = "CSX high-performance media extractors and direct streaming providers.",
                author = "Saurabh Kaperwan"
            ),
            RepoPresetItem(
                name = "King IPTV",
                url = "https://pastebin.com/raw/Cd2g2tfz",
                description = "Live IPTV channels, m3u streams, and global broadcast feeds.",
                author = "King IPTV"
            )
        )

        val POPULAR_PRESETS = BUILT_IN_PRESETS.map {
            ExtensionRepo(url = it.url, name = it.name, description = it.description)
        }
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

    suspend fun addAllPresets(): Int = withContext(Dispatchers.IO) {
        var added = 0
        for (preset in BUILT_IN_PRESETS) {
            val exists = db.extensionDao().getRepository(preset.url) != null
            if (!exists) {
                addRepository(preset.url, preset.name, preset.description)
                added++
            }
        }
        added
    }

    suspend fun removeRepository(repo: ExtensionRepo) = withContext(Dispatchers.IO) {
        db.extensionDao().deleteRepository(repo)
        pluginCache.remove(repo.url)
    }

    suspend fun verifyRepository(url: String): RepoVerificationResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (CineHub-Extension-Engine/1.0)")
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val code = response.code
                if (!response.isSuccessful) {
                    return@withContext RepoVerificationResult(
                        url = cleanUrl,
                        name = "Repository",
                        isOnline = false,
                        httpCode = code,
                        latencyMs = latency,
                        error = "HTTP $code: ${response.message}"
                    )
                }

                val body = response.body?.string() ?: ""
                var name = "Repository"
                var count = 0
                val trimmed = body.trim()

                if (trimmed.startsWith("{")) {
                    val json = JSONObject(trimmed)
                    name = json.optString("name", "Repository")
                    if (json.has("pluginLists")) {
                        val lists = json.optJSONArray("pluginLists")
                        count = lists?.length() ?: 0
                    } else if (json.has("providers")) {
                        count = json.optJSONArray("providers")?.length() ?: 0
                    }
                } else if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    count = arr.length()
                }

                RepoVerificationResult(
                    url = cleanUrl,
                    name = name,
                    isOnline = true,
                    httpCode = code,
                    pluginCount = count,
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            RepoVerificationResult(
                url = cleanUrl,
                name = "Repository",
                isOnline = false,
                latencyMs = System.currentTimeMillis() - start,
                error = e.localizedMessage ?: "Connection error"
            )
        }
    }

    suspend fun installMegaRepo(): MegaRepoInstallResult = withContext(Dispatchers.IO) {
        val megaRepoUrl = "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json"
        try {
            // 1. Add MegaRepo itself
            addRepository(megaRepoUrl, "MegaRepo", "Master multi-repository index aggregating all top community extension sources.")
            
            // 2. Add all known repositories through MegaRepo
            var installedCount = 1
            for (preset in BUILT_IN_PRESETS) {
                if (preset.url != megaRepoUrl) {
                    val added = addRepository(preset.url, preset.name, preset.description)
                    if (added) installedCount++
                }
            }

            MegaRepoInstallResult(
                isSuccess = true,
                megaRepoUrl = megaRepoUrl,
                discoveredReposCount = BUILT_IN_PRESETS.size,
                installedReposCount = installedCount,
                message = "MegaRepo installed successfully with $installedCount community extension feeds."
            )
        } catch (e: Exception) {
            MegaRepoInstallResult(
                isSuccess = false,
                megaRepoUrl = megaRepoUrl,
                discoveredReposCount = 0,
                installedReposCount = 0,
                message = "Failed to install MegaRepo: ${e.message}"
            )
        }
    }

    suspend fun exportRepositoriesJson(): String = withContext(Dispatchers.IO) {
        val repos = mutableListOf<ExtensionRepo>()
        val allRepos = db.openHelper.readableDatabase.query("SELECT url, name, description, lastSync FROM extension_repositories")
        try {
            while (allRepos.moveToNext()) {
                repos.add(
                    ExtensionRepo(
                        url = allRepos.getString(0),
                        name = allRepos.getString(1),
                        description = allRepos.getString(2),
                        lastSync = allRepos.getLong(3)
                    )
                )
            }
        } finally {
            allRepos.close()
        }

        val jsonArray = JSONArray()
        for (r in repos) {
            val obj = JSONObject()
            obj.put("name", r.name)
            obj.put("url", r.url)
            obj.put("description", r.description ?: "")
            obj.put("lastSync", r.lastSync)
            jsonArray.put(obj)
        }
        jsonArray.toString(2)
    }

    suspend fun importRepositoriesJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val url = obj.optString("url")
                    val name = obj.optString("name")
                    val desc = obj.optString("description")
                    if (url.isNotBlank()) {
                        addRepository(url, name, desc)
                        count++
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val url = obj.optString("url")
                val name = obj.optString("name")
                val desc = obj.optString("description")
                if (url.isNotBlank()) {
                    addRepository(url, name, desc)
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        count
    }

    suspend fun syncAllRepositories(): List<RepositorySyncResult> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<ExtensionRepo>()
        val resultList = mutableListOf<RepositorySyncResult>()
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
                repoDesc = json.optStringOrNull("description")

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
            val syncResult = RepositorySyncResult(repoUrl, repoTitle, parsedPlugins)
            try {
                onRepositorySynced?.invoke(syncResult)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            syncResult
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
        val description = obj.optStringOrNull("description")
        var url = obj.optString("url", "")
        var tvUrl = obj.optStringOrNull("tvUrl")
        var iconUrl = obj.optStringOrNull("iconUrl") ?: obj.optStringOrNull("icon")

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

        val lang = obj.optStringOrNull("lang")
            ?: obj.optStringOrNull("language")
            ?: obj.optJSONArray("languages")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.joinToString(", ")
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
            repositoryUrl = repoUrl,
            lang = lang
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
