package xyz.mpv.rex.cinehub.provider.server

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
import xyz.mpv.rex.cinehub.provider.server.model.PlanConfig
import xyz.mpv.rex.cinehub.provider.server.model.UserPermissions
import xyz.mpv.rex.database.MpvExDatabase
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * FirebaseProviderSyncService implements the Server-Controlled Provider Platform for MAX STREAM.
 *
 * Controlled by Extension IDs (NOT repository membership):
 * 1. Read logged-in Firebase user profile & `user_permissions/{uid}`
 * 2. Determine effective plan: free, premium, vip, admin
 * 3. Read `plans/{plan}` from Firestore
 * 4. Resolve final extension set:
 *    finalExtensions = plan.allowedExtensions + customExtensions - blockedExtensions
 *    (or all available extensions if allowAllExtensions == true)
 * 5. Query `extensions` collection directly using extension document IDs
 * 6. Install ONLY extensions whose IDs exist in finalExtensions
 * 7. Do not install all extensions from a repository
 * 8. Admin respects allowAllExtensions or explicit allowedExtensions
 * 9. Update provider_installations/{uid} telemetry
 * 10. Populate APIHolder providers and refresh runtime
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
        const val USERS_COLLECTION = "users"
        const val PERMISSIONS_COLLECTION = "user_permissions"
        const val PLANS_COLLECTION = "plans"
        const val EXTENSIONS_COLLECTION = "extensions"
        const val REPOSITORIES_COLLECTION = "repositories"
        const val PROVIDERS_COLLECTION = "providers"
        const val INSTALLATIONS_COLLECTION = "provider_installations"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // In-memory cache for plugin manifests to avoid redundant downloads
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

    private val _resolvedExtensionIds = MutableStateFlow<List<String>>(emptyList())
    val resolvedExtensionIds: StateFlow<List<String>> = _resolvedExtensionIds.asStateFlow()

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
                _resolvedExtensionIds.value = emptyList()
            }
        }
    }

    /**
     * Executes manual force-sync.
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
            "resolvedExtensions" to _resolvedExtensionIds.value,
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
            "resolvedCount" to _resolvedExtensionIds.value.size,
            "lastSyncTimestamp" to _lastSyncTimestamp.value
        )
    }

    /**
     * Main Provider Resolution & Orchestration Flow by Extension IDs.
     */
    suspend fun syncUserProviders(
        uid: String = auth.currentUser?.uid ?: authManager?.currentUser?.uid ?: "",
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (uid.isBlank()) {
            _syncStatus.value = "Unauthenticated: Running offline defaults"
            Log.i(TAG, "EXTENSION_SYNC: No authenticated UID. Skipping server sync.")
            return@withContext false
        }

        if (_isSyncing.value && !force) {
            Log.i(TAG, "EXTENSION_SYNC: Sync already in progress, skipping duplicate call.")
            return@withContext false
        }

        _isSyncing.value = true
        _syncStatus.value = "Authenticating & resolving extension permissions..."
        Log.i(TAG, "EXTENSION_SYNC: Starting extension-based provider sync for user $uid...")

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

            val userRole = userDoc?.getString("role")?.trim()?.lowercase(Locale.ROOT) ?: "user"
            val isUserPremium = userDoc?.getBoolean("premium") ?: false
            val isAdminUser = userRole == "admin" || userRole == "super_admin" || userRole == "developer"

            // Parse UserPermissions
            val permissions = if (userPermissionsDoc != null && userPermissionsDoc.exists()) {
                UserPermissions.fromSnapshot(userPermissionsDoc)
            } else {
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
                    blockedExtensions = emptyList()
                )
            }

            _userPermissions.value = permissions

            // STEP 3: Determine effective plan (free, premium, vip, admin)
            val effectivePlan = when {
                permissions.plan.isNotBlank() -> permissions.plan.lowercase(Locale.ROOT)
                isAdminUser -> "admin"
                isUserPremium -> "premium"
                else -> "free"
            }
            _userPlan.value = effectivePlan
            Log.i(TAG, "EXTENSION_SYNC: User $uid effective plan: $effectivePlan (role: $userRole)")

            if (!permissions.enabled) {
                _syncStatus.value = "Access Disabled by Server"
                Log.w(TAG, "EXTENSION_SYNC: User $uid permissions disabled by server.")
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
            Log.i(TAG, "EXTENSION_SYNC: Resolved plan config for ${planConfig.name}: allowAll=${planConfig.allowAllExtensions}, allowedExts=${planConfig.allowedExtensions}")

            // STEP 5: Resolve final extension set using:
            // finalExtensions = plan.allowedExtensions + customExtensions - blockedExtensions
            val blockedSet = permissions.blockedExtensions.map { it.trim().lowercase(Locale.ROOT) }.toSet()
            val customSet = permissions.customExtensions.map { it.trim().lowercase(Locale.ROOT) }.toSet()
            val planSet = planConfig.allowedExtensions.map { it.trim().lowercase(Locale.ROOT) }.toSet()

            val isAllowAll = planConfig.allowAllExtensions

            // STEP 6: Query extensions collection directly using extension document IDs
            _syncStatus.value = "Querying extension catalog..."
            val availableExtensionsMap = mutableMapOf<String, AvailablePlugin>()

            // 6a. Fetch from Firestore `extensions` collection
            try {
                val extensionsSnap = firestore.collection(EXTENSIONS_COLLECTION).get().await()
                if (extensionsSnap != null && !extensionsSnap.isEmpty) {
                    for (doc in extensionsSnap.documents) {
                        val ext = FirestoreExtension.fromSnapshot(doc)
                        if (ext.enabled && ext.url.isNotBlank()) {
                            val plugin = ext.toAvailablePlugin()
                            val key = ext.internalName.ifBlank { ext.id }.trim().lowercase(Locale.ROOT)
                            availableExtensionsMap[key] = plugin
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying extensions collection: ${e.message}")
            }

            // 6b. Fallback: If Firestore `extensions` collection is not yet populated, discover from preset manifests
            if (availableExtensionsMap.isEmpty()) {
                Log.i(TAG, "EXTENSION_SYNC: Extensions collection is empty in Firestore. Sourcing from catalog manifests...")
                for (preset in RepositoryManager.BUILT_IN_PRESETS) {
                    val plugins = fetchRepositoryPlugins(preset.url)
                    for (p in plugins) {
                        val key = p.internalName.trim().lowercase(Locale.ROOT)
                        if (!availableExtensionsMap.containsKey(key)) {
                            availableExtensionsMap[key] = p
                        }
                    }
                }
            }

            // STEP 7: Compute final target extensions to install
            val targetPluginsToInstall = mutableListOf<AvailablePlugin>()
            val resolvedIdsList = mutableListOf<String>()

            if (isAllowAll) {
                // All available extensions minus blocked
                for ((key, plugin) in availableExtensionsMap) {
                    val pluginNameKey = plugin.name.trim().lowercase(Locale.ROOT)
                    if (!blockedSet.contains(key) && !blockedSet.contains(pluginNameKey)) {
                        targetPluginsToInstall.add(plugin)
                        resolvedIdsList.add(key)
                    }
                }
            } else {
                // Exact match of (plan.allowedExtensions + customExtensions - blockedExtensions)
                val targetIds = (planSet + customSet) - blockedSet
                for (targetId in targetIds) {
                    val cleanId = targetId.trim().lowercase(Locale.ROOT)
                    val matchedPlugin = availableExtensionsMap[cleanId] 
                        ?: availableExtensionsMap.values.firstOrNull { 
                            it.name.trim().lowercase(Locale.ROOT) == cleanId || 
                            it.internalName.trim().lowercase(Locale.ROOT) == cleanId 
                        }
                    if (matchedPlugin != null) {
                        targetPluginsToInstall.add(matchedPlugin)
                        resolvedIdsList.add(cleanId)
                    } else {
                        Log.d(TAG, "EXTENSION_SYNC: Target extension ID '$cleanId' requested by plan/custom, but not yet present in catalog.")
                    }
                }
            }

            _resolvedExtensionIds.value = resolvedIdsList
            Log.i(TAG, "EXTENSION_SYNC: Resolved ${targetPluginsToInstall.size} target extensions for user $uid: $resolvedIdsList")

            // STEP 8: Remove blocked or disallowed extensions if currently installed
            val currentInstalled = db.extensionDao().getAllInstalledExtensionsSync()
            val targetKeySet = targetPluginsToInstall.map { it.internalName.trim().lowercase(Locale.ROOT) }.toSet()

            for (installed in currentInstalled) {
                val installedKey = installed.pkgName.trim().lowercase(Locale.ROOT)
                val installedNameKey = installed.name.trim().lowercase(Locale.ROOT)
                val isExplicitlyBlocked = blockedSet.contains(installedKey) || blockedSet.contains(installedNameKey)

                if (isExplicitlyBlocked) {
                    _syncStatus.value = "Uninstalling blocked extension: ${installed.name}..."
                    Log.i(TAG, "EXTENSION_SYNC: Removing explicitly blocked extension ${installed.name} ($installedKey)")
                    try {
                        extensionManager.uninstallExtension(installed.pkgName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed uninstalling blocked extension ${installed.pkgName}", e)
                    }
                }
            }

            // STEP 9: Install ONLY allowed extensions & update outdated ones
            val freshInstalledMap = db.extensionDao().getAllInstalledExtensionsSync().associateBy { it.pkgName.trim().lowercase(Locale.ROOT) }
            var installCount = 0

            for (plugin in targetPluginsToInstall) {
                val key = plugin.internalName.trim().lowercase(Locale.ROOT)
                val existing = freshInstalledMap[key]
                val isMissing = existing == null
                val isOutdated = existing != null && existing.versionCode < plugin.versionCode

                if (isMissing || isOutdated) {
                    _syncStatus.value = "${if (isMissing) "Installing" else "Updating"} ${plugin.name}..."
                    Log.i(TAG, "EXTENSION_SYNC: ${if (isMissing) "Installing" else "Updating"} extension ${plugin.name} (${plugin.internalName}) [v${plugin.versionCode}]")
                    try {
                        val ok = extensionManager.installExtension(plugin)
                        if (ok) installCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error installing extension ${plugin.name}", e)
                    }
                }
            }

            // STEP 10: Record installation telemetry to provider_installations/{uid}
            val finalInstalled = db.extensionDao().getAllInstalledExtensionsSync()
            _installedExtensionsCount.value = finalInstalled.size

            try {
                val telemetryData = hashMapOf<String, Any?>(
                    "installedCount" to finalInstalled.size,
                    "plan" to effectivePlan,
                    "lastSync" to FieldValue.serverTimestamp(),
                    "extensions" to finalInstalled.associate { it.pkgName to mapOf("name" to it.name, "version" to it.versionCode) }
                )
                firestore.collection(INSTALLATIONS_COLLECTION).document(uid)
                    .set(telemetryData, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Telemetry update error: ${e.message}")
            }

            // STEP 11: Reload provider runtime & notify APIHolder
            _syncStatus.value = "Reloading runtime providers..."
            try {
                extensionManager.loadInstalledExtensions()
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading runtime extensions", e)
            }

            val totalProviders = APIHolder.allProviders.size
            _loadedProvidersCount.value = totalProviders
            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now
            _lastSyncTimeFormatted.value = formatRelativeTime(now)
            _syncStatus.value = "Extensions Synced"

            Log.i(TAG, "EXTENSION_SYNC: Synchronization finished. $totalProviders active providers loaded. Total installed extensions: ${finalInstalled.size}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "EXTENSION_SYNC: Error during extension synchronization", e)
            _syncStatus.value = "Sync error: ${e.localizedMessage ?: "Network error"}"
            return@withContext false
        } finally {
            _isSyncing.value = false
        }
    }

    // ==========================================
    // ADMIN UI BACKEND API METHODS
    // ==========================================

    /**
     * Fetches all plans from Firestore (`plans` collection).
     */
    suspend fun fetchAllPlans(): List<PlanConfig> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(PLANS_COLLECTION).get().await()
            val plans = mutableListOf<PlanConfig>()
            if (snapshot != null && !snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    PlanConfig.fromSnapshot(doc)?.let { plans.add(it) }
                }
            }
            if (plans.isEmpty()) {
                // Pre-populate standard defaults
                listOf("free", "premium", "vip", "admin").map { PlanConfig.defaultForPlan(it) }
            } else {
                plans.sortedBy { 
                    when (it.id) {
                        "free" -> 0
                        "premium" -> 1
                        "vip" -> 2
                        "admin" -> 3
                        else -> 4
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching plans: ${e.message}", e)
            listOf("free", "premium", "vip", "admin").map { PlanConfig.defaultForPlan(it) }
        }
    }

    /**
     * Saves or updates a plan in Firestore (`plans/{planId}`).
     */
    suspend fun savePlan(plan: PlanConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val planId = plan.id.trim().lowercase(Locale.ROOT)
            if (planId.isBlank()) return@withContext false
            firestore.collection(PLANS_COLLECTION).document(planId)
                .set(plan.toMap(), SetOptions.merge())
                .await()
            Log.i(TAG, "ADMIN_ACTION: Saved plan $planId to Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving plan ${plan.id}", e)
            false
        }
    }

    /**
     * Deletes a plan from Firestore (`plans/{planId}`).
     */
    suspend fun deletePlan(planId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanId = planId.trim().lowercase(Locale.ROOT)
            if (cleanId.isBlank() || cleanId == "free" || cleanId == "admin") return@withContext false
            firestore.collection(PLANS_COLLECTION).document(cleanId).delete().await()
            Log.i(TAG, "ADMIN_ACTION: Deleted plan $cleanId from Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting plan $planId", e)
            false
        }
    }

    /**
     * Fetches user permissions from Firestore (`user_permissions/{uid}`).
     */
    suspend fun fetchUserPermissions(uid: String): UserPermissions = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection(PERMISSIONS_COLLECTION).document(uid).get().await()
            UserPermissions.fromSnapshot(doc)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching permissions for $uid: ${e.message}")
            UserPermissions()
        }
    }

    /**
     * Saves user permissions in Firestore (`user_permissions/{uid}`).
     */
    suspend fun saveUserPermissions(uid: String, permissions: UserPermissions): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection(PERMISSIONS_COLLECTION).document(uid)
                .set(permissions.toMap(), SetOptions.merge())
                .await()
            Log.i(TAG, "ADMIN_ACTION: Saved permissions for user $uid -> Plan: ${permissions.plan}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving permissions for user $uid", e)
            false
        }
    }

    /**
     * Fetches all available extensions in the system with full metadata.
     */
    suspend fun fetchAllAvailableExtensions(): List<FirestoreExtension> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, FirestoreExtension>()
        try {
            // From Firestore
            val snap = firestore.collection(EXTENSIONS_COLLECTION).get().await()
            if (snap != null && !snap.isEmpty) {
                for (doc in snap.documents) {
                    val ext = FirestoreExtension.fromSnapshot(doc)
                    val key = ext.internalName.ifBlank { ext.id }.trim().lowercase(Locale.ROOT)
                    result[key] = ext
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching extensions collection: ${e.message}")
        }

        // Fill in any gaps from built-in preset catalogs
        for (preset in RepositoryManager.BUILT_IN_PRESETS) {
            val plugins = fetchRepositoryPlugins(preset.url)
            for (p in plugins) {
                val key = p.internalName.trim().lowercase(Locale.ROOT)
                if (!result.containsKey(key)) {
                    result[key] = FirestoreExtension(
                        id = key,
                        name = p.name,
                        internalName = p.internalName,
                        repository = preset.name,
                        repositoryUrl = preset.url,
                        url = p.url,
                        tvUrl = p.tvUrl,
                        iconUrl = p.iconUrl,
                        version = p.version,
                        versionCode = p.versionCode,
                        description = p.description,
                        lang = p.lang ?: "en",
                        authors = p.authors,
                        tvTypes = p.tvTypes,
                        enabled = true
                    )
                }
            }
        }

        result.values.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /**
     * Toggles assignment of an extension to a specific plan.
     */
    suspend fun toggleExtensionForPlan(extensionId: String, planId: String, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanPlan = planId.trim().lowercase(Locale.ROOT)
            val cleanExt = extensionId.trim().lowercase(Locale.ROOT)
            val doc = firestore.collection(PLANS_COLLECTION).document(cleanPlan).get().await()
            val plan = PlanConfig.fromSnapshot(doc) ?: PlanConfig.defaultForPlan(cleanPlan)

            val newAllowed = plan.allowedExtensions.toMutableList()
            if (enable) {
                if (!newAllowed.contains(cleanExt)) newAllowed.add(cleanExt)
            } else {
                newAllowed.removeAll { it.equals(cleanExt, ignoreCase = true) }
            }

            val updatedPlan = plan.copy(allowedExtensions = newAllowed)
            savePlan(updatedPlan)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling extension $extensionId for plan $planId", e)
            false
        }
    }

    /**
     * Downloads and parses plugin manifest JSON from a repository URL.
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
        val internalName = obj.optString("internalName", obj.optString("id", name.lowercase(Locale.ROOT).replace(" ", "_")))
        val version = obj.optString("version", "1.0.0")
        val versionCode = obj.optInt("versionCode", 1)
        val description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null
        var url = obj.optString("url", "")
        val iconUrl = if (obj.has("iconUrl") && !obj.isNull("iconUrl")) obj.getString("iconUrl") else null
        val lang = obj.optString("lang", "en")

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
            description = description ?: "Server Managed Extension",
            iconUrl = iconUrl,
            repositoryUrl = repoUrl,
            lang = lang
        )
    }

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
