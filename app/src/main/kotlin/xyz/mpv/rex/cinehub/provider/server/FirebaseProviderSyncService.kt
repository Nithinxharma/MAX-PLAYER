package xyz.mpv.rex.cinehub.provider.server

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.provider.server.model.FirestoreExtension
import xyz.mpv.rex.cinehub.provider.server.model.FirestoreRepository
import xyz.mpv.rex.cinehub.provider.server.model.PlanConfig
import xyz.mpv.rex.cinehub.provider.server.model.UserPermissions
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * FirebaseProviderSyncService implements the Server-Controlled Provider Platform for MAX STREAM.
 *
 * Responsibilities:
 * 1. Read logged-in Firebase user
 * 2. Read `user_permissions/{uid}`
 * 3. Determine plan: free, premium, vip, admin
 * 4. Read `plans/{plan}`
 * 5. Resolve allowed repositories
 * 6. Apply user overrides: customRepositories, blockedRepositories
 * 7. Resolve final repository list
 * 8. Load repositories from Firestore `repositories/{repositoryId}`
 * 9. Download plugin manifests
 * 10. Compare installed extensions
 * 11. Install missing extensions
 * 12. Update outdated extensions
 * 13. Remove blocked extensions
 * 14. Populate APIHolder providers
 * 15. Trigger provider refresh
 */
class FirebaseProviderSyncService(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: MpvExDatabase,
    private val extensionManager: ExtensionManager,
    private val repositoryManager: RepositoryManager,
    private val authManager: AuthManager? = null
) {
    companion object {
        private const val TAG = "FirebaseProviderSync"
        private const val USERS_COLLECTION = "users"
        private const val PERMISSIONS_COLLECTION = "user_permissions"
        private const val PLANS_COLLECTION = "plans"
        private const val REPOSITORIES_COLLECTION = "repositories"
        private const val EXTENSIONS_COLLECTION = "extensions"
        private const val INSTALLATIONS_COLLECTION = "provider_installations"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // In-memory cache for plugin manifests to avoid redundant network downloads
    private val manifestCache = ConcurrentHashMap<String, List<AvailablePlugin>>()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _lastSyncTimeFormatted = MutableStateFlow("Never")
    val lastSyncTimeFormatted: StateFlow<String> = _lastSyncTimeFormatted.asStateFlow()

    private val _loadedProvidersCount = MutableStateFlow(0)
    val loadedProvidersCount: StateFlow<Int> = _loadedProvidersCount.asStateFlow()

    private val _installedExtensionsCount = MutableStateFlow(0)
    val installedExtensionsCount: StateFlow<Int> = _installedExtensionsCount.asStateFlow()

    private val _syncedRepositoriesCount = MutableStateFlow(0)
    val syncedRepositoriesCount: StateFlow<Int> = _syncedRepositoriesCount.asStateFlow()

    private val _userPlan = MutableStateFlow("free")
    val userPlan: StateFlow<String> = _userPlan.asStateFlow()

    private val _userPermissions = MutableStateFlow<UserPermissions?>(null)
    val userPermissions: StateFlow<UserPermissions?> = _userPermissions.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                scope.launch {
                    syncUserProviders(user.uid)
                }
            } else {
                _userPlan.value = "free"
                _userPermissions.value = null
            }
        }
    }

    /**
     * Executes manual admin force-sync.
     */
    suspend fun forceSync(): Boolean = withContext(Dispatchers.IO) {
        val currentUid = auth.currentUser?.uid ?: authManager?.currentUser?.uid ?: ""
        manifestCache.clear()
        syncUserProviders(currentUid, force = true)
    }

    /**
     * Clears cached manifests and temporary download artifacts, then reloads runtime providers.
     */
    suspend fun clearProviderCache() = withContext(Dispatchers.IO) {
        manifestCache.clear()
        extensionManager.clearCache()
        extensionManager.loadInstalledExtensions()
        _loadedProvidersCount.value = APIHolder.allProviders.size
        _installedExtensionsCount.value = db.extensionDao().getAllInstalledExtensionsSync().size
    }

    /**
     * Diagnostic data regarding active CloudStream providers and runtime extensions.
     */
    fun getProviderDiagnostics(): Map<String, Any> {
        val providers = APIHolder.allProviders
        return mapOf(
            "totalProviders" to providers.size,
            "providerNames" to providers.map { "${it.name} (${it.lang})" },
            "installedCount" to _installedExtensionsCount.value,
            "syncedReposCount" to _syncedRepositoriesCount.value,
            "syncStatus" to _syncStatus.value,
            "lastSync" to _lastSyncTimeFormatted.value
        )
    }

    /**
     * Diagnostic data regarding Firebase connection and resolved permissions.
     */
    fun getFirebaseDiagnostics(): Map<String, Any> {
        val user = auth.currentUser
        return mapOf(
            "authenticated" to (user != null),
            "uid" to (user?.uid ?: "None"),
            "email" to (user?.email ?: "None"),
            "plan" to _userPlan.value,
            "permissions" to (_userPermissions.value?.toString() ?: "None"),
            "lastSyncTimestamp" to _lastSyncTimestamp.value
        )
    }

    /**
     * Main Provider Resolution & Orchestration Flow.
     */
    suspend fun syncUserProviders(
        uid: String = auth.currentUser?.uid ?: authManager?.currentUser?.uid ?: "",
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (uid.isBlank()) {
            _syncStatus.value = "Unauthenticated: Running offline defaults"
            Log.i(TAG, "ORCHESTRATION_SYNC: No authenticated UID. Skipping server sync.")
            return@withContext false
        }

        if (_isSyncing.value && !force) {
            Log.i(TAG, "ORCHESTRATION_SYNC: Sync already in progress, skipping duplicate call.")
            return@withContext false
        }

        _isSyncing.value = true
        _syncStatus.value = "Authenticating & resolving permissions..."
        Log.i(TAG, "ORCHESTRATION_SYNC: Starting server-controlled provider sync for user $uid...")

        try {
            // STEP 1 & 2: Read logged-in user profile & user_permissions/{uid}
            val userPermissionsDoc = try {
                firestore.collection(PERMISSIONS_COLLECTION).document(uid).get().await()
            } catch (e: Exception) {
                Log.w(TAG, "Error reading user_permissions/$uid: ${e.message}")
                null
            }

            val userDoc = try {
                firestore.collection(USERS_COLLECTION).document(uid).get().await()
            } catch (e: Exception) {
                Log.w(TAG, "Error reading users/$uid: ${e.message}")
                null
            }

            val userRole = userDoc?.getString("role")?.trim()?.lowercase() ?: "user"
            val isUserPremium = userDoc?.getBoolean("premium") ?: false
            val isAdminUser = userRole == "admin" || userRole == "super_admin" || userRole == "developer"

            // Parse UserPermissions
            val permissions = if (userPermissionsDoc != null && userPermissionsDoc.exists()) {
                UserPermissions.fromSnapshot(userPermissionsDoc)
            } else {
                // Fallback permissions based on users/{uid}
                val inferredPlan = when {
                    isAdminUser -> "admin"
                    userRole == "vip" -> "vip"
                    isUserPremium || userRole == "premium" -> "premium"
                    else -> "free"
                }
                UserPermissions(
                    plan = inferredPlan,
                    enabled = true,
                    customExtensions = emptyList(),
                    blockedExtensions = emptyList(),
                    customRepositories = emptyList(),
                    blockedRepositories = emptyList()
                )
            }

            _userPermissions.value = permissions

            // STEP 3: Determine plan (free, premium, vip, admin)
            val effectivePlan = when {
                isAdminUser -> "admin"
                permissions.plan.isNotBlank() -> permissions.plan.lowercase()
                isUserPremium -> "premium"
                else -> "free"
            }
            _userPlan.value = effectivePlan
            Log.i(TAG, "ORCHESTRATION_SYNC: User $uid determined plan: $effectivePlan (role: $userRole)")

            if (!permissions.enabled) {
                _syncStatus.value = "Access Disabled by Server"
                Log.w(TAG, "ORCHESTRATION_SYNC: User $uid permissions are disabled by server.")
                return@withContext false
            }

            // STEP 4: Read plans/{plan}
            _syncStatus.value = "Loading plan configuration: $effectivePlan..."
            val planDoc = try {
                firestore.collection(PLANS_COLLECTION).document(effectivePlan).get().await()
            } catch (e: Exception) {
                Log.w(TAG, "Error reading plans/$effectivePlan from Firestore: ${e.message}")
                null
            }

            val planConfig = PlanConfig.fromSnapshot(planDoc) ?: PlanConfig.defaultForPlan(effectivePlan)
            Log.i(TAG, "ORCHESTRATION_SYNC: Resolved plan config for ${planConfig.name}: ${planConfig.repositories}")

            // STEP 5: Resolve allowed repositories
            val allowedRepoIds = mutableSetOf<String>()
            allowedRepoIds.addAll(planConfig.repositories.map { it.trim().lowercase() })

            // STEP 6: Apply user overrides (customRepositories, blockedRepositories)
            if (permissions.customRepositories.isNotEmpty()) {
                Log.i(TAG, "ORCHESTRATION_SYNC: Applying customRepositories: ${permissions.customRepositories}")
                allowedRepoIds.addAll(permissions.customRepositories.map { it.trim().lowercase() })
            }

            if (permissions.blockedRepositories.isNotEmpty()) {
                val blockedSet = permissions.blockedRepositories.map { it.trim().lowercase() }.toSet()
                Log.i(TAG, "ORCHESTRATION_SYNC: Applying blockedRepositories: $blockedSet")
                allowedRepoIds.removeAll(blockedSet)
            }

            // STEP 7: Resolve final repository list
            val isAllReposAllowed = allowedRepoIds.contains("*") || effectivePlan == "admin"
            val blockedRepos = permissions.blockedRepositories.map { it.trim().lowercase() }.toSet()
            Log.i(TAG, "ORCHESTRATION_SYNC: Final allowed repository filter: isAll=$isAllReposAllowed, allowed=$allowedRepoIds")

            // STEP 8: Load repositories from Firestore `repositories/{repositoryId}`
            _syncStatus.value = "Fetching server repositories..."
            val firestoreRepos = mutableListOf<FirestoreRepository>()

            try {
                val repoSnapshots = firestore.collection(REPOSITORIES_COLLECTION).get().await()
                if (repoSnapshots != null && !repoSnapshots.isEmpty) {
                    for (doc in repoSnapshots.documents) {
                        firestoreRepos.add(FirestoreRepository.fromSnapshot(doc))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying repositories collection: ${e.message}")
            }

            // Seed built-in default repositories if Firestore has no repos collection
            if (firestoreRepos.isEmpty()) {
                Log.i(TAG, "ORCHESTRATION_SYNC: Using built-in default repository catalog")
                firestoreRepos.addAll(FirestoreRepository.BUILT_IN_DEFAULTS)
            }

            // Filter repositories by allowed list and blocked list
            val activeRepositories = firestoreRepos.filter { repo ->
                val repoId = repo.id.trim().lowercase()
                val isBlocked = blockedRepos.contains(repoId)
                val isPermitted = isAllReposAllowed || allowedRepoIds.contains(repoId)
                repo.enabled && isPermitted && !isBlocked
            }

            _syncedRepositoriesCount.value = activeRepositories.size
            Log.i(TAG, "ORCHESTRATION_SYNC: Active allowed repositories count: ${activeRepositories.size} (${activeRepositories.map { it.name }})")

            // Ensure repositories exist in local Room DB & RepositoryManager
            for (repo in activeRepositories) {
                val existing = db.extensionDao().getRepository(repo.pluginListUrl)
                if (existing == null && repo.pluginListUrl.isNotBlank()) {
                    try {
                        repositoryManager.addRepository(
                            url = repo.pluginListUrl,
                            name = repo.name,
                            description = repo.description
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error adding repository ${repo.name}: ${e.message}")
                    }
                }
            }

            // STEP 9: Download plugin manifests and resolve extensions
            _syncStatus.value = "Downloading plugin manifests & extensions..."
            val remoteAvailablePlugins = mutableListOf<AvailablePlugin>()

            // 9a. Manifests from active repositories
            for (repo in activeRepositories) {
                val plugins = fetchRepositoryPlugins(repo.pluginListUrl)
                remoteAvailablePlugins.addAll(plugins)
            }

            // 9b. Read standalone `extensions/{extensionId}` collection in Firestore
            try {
                val extensionSnapshots = firestore.collection(EXTENSIONS_COLLECTION).get().await()
                if (extensionSnapshots != null && !extensionSnapshots.isEmpty) {
                    for (doc in extensionSnapshots.documents) {
                        val ext = FirestoreExtension.fromSnapshot(doc)
                        val repoId = ext.repositoryId.trim().lowercase()
                        val isRepoAllowed = isAllReposAllowed || allowedRepoIds.contains(repoId)
                        val isCustomExt = permissions.customExtensions.any { it.equals(ext.internalName, ignoreCase = true) || it.equals(ext.name, ignoreCase = true) }

                        if (ext.enabled && (isRepoAllowed || isCustomExt) && ext.url.isNotBlank()) {
                            remoteAvailablePlugins.add(
                                AvailablePlugin(
                                    name = ext.name,
                                    internalName = ext.internalName,
                                    version = "${ext.version}.0.0",
                                    versionCode = ext.version,
                                    url = ext.url,
                                    description = "Server Controlled Extension (${ext.repositoryId})",
                                    iconUrl = null,
                                    repositoryUrl = "firebase://repositories/${ext.repositoryId}",
                                    lang = "en"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading extensions collection: ${e.message}")
            }

            // STEP 10: Compare installed extensions with blocked extensions & allowed manifests
            val installedExtensions = db.extensionDao().getAllInstalledExtensionsSync()
            val installedMap = installedExtensions.associateBy { it.pkgName.trim().lowercase() }
            val blockedExts = permissions.blockedExtensions.map { it.trim().lowercase() }.toSet()

            // STEP 13: Remove blocked extensions (Blocking Support requirement)
            for (installed in installedExtensions) {
                val isBlocked = blockedExts.contains(installed.pkgName.lowercase()) ||
                        blockedExts.contains(installed.name.lowercase())

                if (isBlocked) {
                    _syncStatus.value = "Removing blocked extension: ${installed.name}..."
                    Log.i(TAG, "ORCHESTRATION_SYNC: Uninstalling blocked extension ${installed.name} (${installed.pkgName})")
                    try {
                        extensionManager.uninstallExtension(installed.pkgName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to uninstall blocked extension ${installed.pkgName}", e)
                    }
                }
            }

            // Deduplicate plugins by internalName, keeping highest versionCode
            val candidatePlugins = mutableMapOf<String, AvailablePlugin>()
            for (p in remoteAvailablePlugins) {
                val key = p.internalName.trim().lowercase()
                // Skip blocked extensions
                if (blockedExts.contains(key) || blockedExts.contains(p.name.trim().lowercase())) {
                    continue
                }
                val existing = candidatePlugins[key]
                if (existing == null || p.versionCode > existing.versionCode) {
                    candidatePlugins[key] = p
                }
            }

            // STEP 11 & 12: Install missing extensions & update outdated extensions
            var newInstallsCount = 0
            val currentInstalledAfterRemoval = db.extensionDao().getAllInstalledExtensionsSync().associateBy { it.pkgName.trim().lowercase() }

            for ((key, plugin) in candidatePlugins) {
                val existing = currentInstalledAfterRemoval[key]
                val isMissing = existing == null
                val isOutdated = existing != null && existing.versionCode < plugin.versionCode

                if (isMissing || isOutdated) {
                    _syncStatus.value = "${if (isMissing) "Installing" else "Updating"} ${plugin.name}..."
                    Log.i(TAG, "ORCHESTRATION_SYNC: ${if (isMissing) "Installing missing" else "Updating outdated"} extension ${plugin.name} (v${plugin.versionCode})")

                    try {
                        val success = extensionManager.installExtension(plugin)
                        if (success) {
                            newInstallsCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed installing extension ${plugin.name}", e)
                    }
                }
            }

            // Record installation telemetry to provider_installations/{uid}
            val finalInstalled = db.extensionDao().getAllInstalledExtensionsSync()
            _installedExtensionsCount.value = finalInstalled.size

            try {
                val report = finalInstalled.associate { it.pkgName to mapOf("installed" to true, "version" to it.versionCode) }
                firestore.collection(INSTALLATIONS_COLLECTION).document(uid)
                    .set(report, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Error writing provider_installations: ${e.message}")
            }

            // STEP 14 & 15: Populate APIHolder providers & trigger provider refresh
            _syncStatus.value = "Reloading provider runtime..."
            try {
                extensionManager.loadInstalledExtensions()
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading runtime extensions", e)
            }

            val totalLoadedProviders = APIHolder.allProviders.size
            _loadedProvidersCount.value = totalLoadedProviders
            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now
            _lastSyncTimeFormatted.value = formatRelativeTime(now)
            _syncStatus.value = "Providers Synced"

            Log.i(TAG, "ORCHESTRATION_SYNC: Synchronization complete. Loaded $totalLoadedProviders providers into APIHolder. Installed: ${finalInstalled.size}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "ORCHESTRATION_SYNC: Error during provider synchronization", e)
            _syncStatus.value = "Sync error: ${e.localizedMessage ?: "Network error"}"
            return@withContext false
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Downloads and parses plugin manifest JSON from a repository URL, utilizing memory cache.
     */
    private fun fetchRepositoryPlugins(pluginListUrl: String): List<AvailablePlugin> {
        if (pluginListUrl.isBlank()) return emptyList()

        manifestCache[pluginListUrl]?.let { return it }

        return try {
            val request = Request.Builder()
                .url(pluginListUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (MAX-STREAM-Provider-Engine/2.0)")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string() ?: ""
            }

            if (body.isBlank()) return emptyList()

            val results = mutableListOf<AvailablePlugin>()
            val trimmed = body.trim()

            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    parsePlugin(obj, pluginListUrl)?.let { results.add(it) }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                if (root.has("pluginLists")) {
                    val lists = root.optJSONArray("pluginLists")
                    if (lists != null) {
                        for (i in 0 until lists.length()) {
                            val subUrl = lists.getString(i)
                            results.addAll(fetchRepositoryPlugins(subUrl))
                        }
                    }
                } else if (root.has("providers")) {
                    val array = root.optJSONArray("providers")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            parsePlugin(obj, pluginListUrl)?.let { results.add(it) }
                        }
                    }
                }
            }

            manifestCache[pluginListUrl] = results
            results
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading plugin list from $pluginListUrl: ${e.message}")
            emptyList()
        }
    }

    private fun parsePlugin(obj: JSONObject, repoUrl: String): AvailablePlugin? {
        val name = obj.optString("name")
        if (name.isBlank()) return null
        val internalName = obj.optString("internalName", obj.optString("id", name.lowercase().replace(" ", "_")))
        val version = obj.optString("version", "1.0.0")
        val versionCode = obj.optInt("versionCode", 1)
        val description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null
        var url = obj.optString("url", "")
        val iconUrl = if (obj.has("iconUrl") && !obj.isNull("iconUrl")) obj.getString("iconUrl") else null
        val lang = obj.optString("lang", "en")

        // Resolve relative URLs
        if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            val baseUrl = repoUrl.substringBeforeLast("/") + "/"
            url = baseUrl + url.removePrefix("./").removePrefix("/")
        }

        return AvailablePlugin(
            name = name,
            internalName = internalName,
            version = version,
            versionCode = versionCode,
            url = url,
            description = description ?: "Server Orchestrated Provider",
            iconUrl = iconUrl,
            repositoryUrl = repoUrl,
            lang = lang
        )
    }

    /**
     * Helper to compute user-friendly relative time for last sync display.
     */
    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        val diffMs = System.currentTimeMillis() - timestamp
        val seconds = (diffMs / 1000).coerceAtLeast(0)
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            seconds < 60 -> "Just now"
            minutes == 1L -> "1 min ago"
            minutes < 60 -> "$minutes min ago"
            hours == 1L -> "1 hr ago"
            hours < 24 -> "$hours hr ago"
            else -> "${hours / 24} d ago"
        }
    }
}
