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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * FirebaseProviderSyncService implements the Server-Controlled OTT Platform Engine for MAX STREAM.
 *
 * Core Architecture Principles:
 * 1. Repositories are metadata sources ONLY - they NEVER grant access or install anything.
 * 2. Extensions are the atomic entitlement unit.
 * 3. Subscription Plans are the access unit.
 * 4. Plan Inheritance Engine resolves recursive hierarchies (Free -> Premium -> VIP -> Admin).
 * 5. Extension Name = Provider Name intelligent equivalence engine.
 * 6. Entitlement Engine enforces: Allowed = PlanExtensions + CustomExtensions - BlockedExtensions.
 * 7. Background silent sync: users reach Home instantly while extensions install asynchronously.
 */
class FirebaseProviderSyncService(
    private val context: Context,
    private val firestore: FirebaseFirestore = runCatching {
        val dbId = context.getString(xyz.mpv.rex.R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }.getOrElse { FirebaseFirestore.getInstance() },
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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory cache for plugin manifests to avoid redundant downloads
    private val manifestCache = ConcurrentHashMap<String, List<AvailablePlugin>>()

    // Cache of resolved plan inheritance mappings
    private val resolvedPlanInheritanceCache = ConcurrentHashMap<String, Pair<Boolean, Set<String>>>()

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

    private val isInitialAdmin = auth.currentUser?.email?.trim()?.equals("sabhiron5@gmail.com", ignoreCase = true) == true

    private val _userPlan = MutableStateFlow(if (isInitialAdmin) "admin" else "free")
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
        resolvedPlanInheritanceCache.clear()
        syncUserProviders(currentUid, force = true)
    }

    /**
     * Clears cached manifests and temporary download artifacts, then reloads runtime providers.
     */
    suspend fun clearProviderCache() = withContext(Dispatchers.IO) {
        manifestCache.clear()
        resolvedPlanInheritanceCache.clear()
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
     * Diagnostic data regarding Firebase connection, permissions, and Plan Inheritance.
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
            "lastSyncTimestamp" to _lastSyncTimestamp.value,
            "planInheritanceCache" to resolvedPlanInheritanceCache.mapValues { "${it.value.first} -> ${it.value.second}" }
        )
    }

    // ==========================================
    // PLAN INHERITANCE ENGINE
    // ==========================================

    /**
     * Recursively resolves the effective extensions granted by a plan hierarchy.
     *
     * Example:
     * Free: [bollyflix, vega, superstream]
     * Premium inherits Free: [uhdmovies, moviesmod] -> Effective: [bollyflix, vega, superstream, uhdmovies, moviesmod]
     * VIP inherits Premium: [castletv, streamwish] -> Effective: [all above + castletv, streamwish]
     * Admin: allowAllExtensions = true
     *
     * Protected against circular inheritance cycles.
     */
    fun resolveEffectivePlanExtensions(
        planId: String,
        allPlans: Map<String, PlanConfig>,
        visited: MutableSet<String> = mutableSetOf()
    ): Pair<Boolean, Set<String>> {
        val cleanId = planId.trim().lowercase(Locale.ROOT)
        
        // Cycle detection
        if (visited.contains(cleanId)) {
            Log.w(TAG, "PLAN_INHERITANCE: Cycle detected at '$cleanId'. Active chain: $visited")
            return Pair(false, emptySet())
        }
        visited.add(cleanId)

        val plan = allPlans[cleanId] ?: PlanConfig.defaultForPlan(cleanId)
        if (plan.allowAllExtensions) {
            return Pair(true, emptySet())
        }

        val effectiveSet = plan.allowedExtensions.map { it.trim().lowercase(Locale.ROOT) }.toMutableSet()

        // Recursive inheritance
        val parentId = plan.inherits?.trim()?.lowercase(Locale.ROOT)
        if (!parentId.isNullOrBlank() && parentId != cleanId) {
            val (parentAllowAll, parentExtensions) = resolveEffectivePlanExtensions(parentId, allPlans, visited)
            if (parentAllowAll) {
                return Pair(true, emptySet())
            }
            effectiveSet.addAll(parentExtensions)
        }

        return Pair(false, effectiveSet)
    }

    // ==========================================
    // EXTENSION NAME = PROVIDER NAME ENGINE
    // ==========================================

    /**
     * Normalizes an identifier by removing punctuation, spaces, and provider/plugin suffixes.
     */
    fun normalizeExtensionIdentifier(raw: String): String {
        return raw.trim()
            .lowercase(Locale.ROOT)
            .removeSuffix(".cs3")
            .removeSuffix(".jar")
            .removeSuffix("provider")
            .removeSuffix("plugin")
            .replace(Regex("""[^a-z0-9]"""), "")
    }

    /**
     * Intelligent matching algorithm equating extension name and provider name.
     * Matches across:
     * - Name (case-insensitive & normalized)
     * - InternalName / package ID (case-insensitive & normalized)
     * - Substring & prefix variations (e.g. "superstream" matches "superstreamprovider" or "com.superstream")
     * - Authors / tvTypes / inner provider lists
     */
    fun matchesExtensionOrProvider(targetId: String, plugin: AvailablePlugin): Boolean {
        val normTarget = normalizeExtensionIdentifier(targetId)
        if (normTarget.isBlank()) return false

        val normName = normalizeExtensionIdentifier(plugin.name)
        val normInternal = normalizeExtensionIdentifier(plugin.internalName)

        // 1. Direct normalized match
        if (normName == normTarget || normInternal == normTarget) return true

        // 2. Suffix-agnostic & bidirectional substring match
        if (normTarget.length >= 3) {
            if (normName.contains(normTarget) || normTarget.contains(normName)) return true
            if (normInternal.contains(normTarget) || normTarget.contains(normInternal)) return true
        }

        // 3. Match against authors
        if (plugin.authors.any { normalizeExtensionIdentifier(it) == normTarget }) return true

        // 4. Exact raw match fallback
        val cleanTarget = targetId.trim().lowercase(Locale.ROOT)
        if (plugin.name.trim().lowercase(Locale.ROOT) == cleanTarget ||
            plugin.internalName.trim().lowercase(Locale.ROOT) == cleanTarget
        ) return true

        return false
    }

    // ==========================================
    // MAIN PROVIDER RESOLUTION & ORCHESTRATION
    // ==========================================

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

            val userEmail = userDoc?.getString("email") ?: auth.currentUser?.email ?: ""
            val isExplicitAdminEmail = userEmail.trim().equals("sabhiron5@gmail.com", ignoreCase = true)
            val rawRole = userDoc?.getString("role")?.trim()?.lowercase(Locale.ROOT)
            val isAdminBoolean = userDoc?.getBoolean("isAdmin") ?: false
            val userRole = when {
                !rawRole.isNullOrBlank() && rawRole != "user" -> rawRole
                isExplicitAdminEmail -> "owner"
                isAdminBoolean -> "admin"
                else -> rawRole ?: "user"
            }
            val isUserPremium = userDoc?.getBoolean("premium") ?: (isExplicitAdminEmail || userRole == "admin" || userRole == "owner")
            val isAdminUser = userRole == "admin" || userRole == "super_admin" || userRole == "developer" || userRole == "owner" || isExplicitAdminEmail

            val docPlan = userDoc?.getString("plan")?.trim()?.lowercase(Locale.ROOT)

            // Parse UserPermissions
            val permissions = if (userPermissionsDoc != null && userPermissionsDoc.exists()) {
                val parsed = UserPermissions.fromSnapshot(userPermissionsDoc)
                if (isAdminUser && parsed.plan.isBlank()) parsed.copy(plan = "admin") else parsed
            } else {
                val inferredPlan = when {
                    !docPlan.isNullOrBlank() && docPlan != "free" -> docPlan
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
                !docPlan.isNullOrBlank() && docPlan != "free" -> docPlan
                permissions.plan.isNotBlank() && permissions.plan != "free" -> permissions.plan.lowercase(Locale.ROOT)
                isAdminUser -> "admin"
                isUserPremium -> "premium"
                else -> permissions.plan.ifBlank { "free" }.lowercase(Locale.ROOT)
            }
            _userPlan.value = effectivePlan
            Log.i(TAG, "EXTENSION_SYNC: User $uid effective plan: $effectivePlan (role: $userRole, email: $userEmail)")

            if (!permissions.enabled) {
                _syncStatus.value = "Access Disabled by Server"
                Log.w(TAG, "EXTENSION_SYNC: User $uid permissions disabled by server.")
                return@withContext false
            }

            // STEP 4: Fetch all plans and run PLAN INHERITANCE ENGINE
            _syncStatus.value = "Resolving plan inheritance hierarchy..."
            val allPlansMap = fetchAllPlans().associateBy { it.id.lowercase(Locale.ROOT) }

            val (isAllowAll, inheritedExtensions) = resolveEffectivePlanExtensions(effectivePlan, allPlansMap)
            resolvedPlanInheritanceCache[effectivePlan] = Pair(isAllowAll, inheritedExtensions)
            Log.i(TAG, "EXTENSION_SYNC: Plan '$effectivePlan' resolved: allowAll=$isAllowAll, effectiveExtensions=$inheritedExtensions")

            // STEP 5: Resolve final extension set using Entitlement Engine:
            // AllowedExtensions = EffectivePlanExtensions + UserCustomExtensions - BlockedExtensions
            val blockedSet = permissions.blockedExtensions.map { it.trim().lowercase(Locale.ROOT) }.toSet()
            val customSet = permissions.customExtensions.map { it.trim().lowercase(Locale.ROOT) }.toSet()

            // STEP 6: Query and index extension catalog dynamically from:
            // 6a. Firestore `extensions` collection (Fleet-wide server management)
            // 6b. Active repositories (Parallel non-blocking fetch)
            // 6c. Room cached plugins & installed extensions
            _syncStatus.value = "Building unified extension catalog..."
            val allCatalogPlugins = mutableListOf<AvailablePlugin>()
            val globallyDisabledKeys = mutableSetOf<String>()

            // 6a. Fetch Firestore `extensions` collection
            try {
                val extensionsSnap = withTimeoutOrNull(5000L) {
                    firestore.collection(EXTENSIONS_COLLECTION).get().await()
                }
                if (extensionsSnap != null && !extensionsSnap.isEmpty) {
                    for (doc in extensionsSnap.documents) {
                        val ext = FirestoreExtension.fromSnapshot(doc)
                        val docKey = ext.internalName.ifBlank { ext.id }.trim().lowercase(Locale.ROOT)
                        val docNameKey = ext.name.trim().lowercase(Locale.ROOT)

                        if (!ext.enabled) {
                            // Globally disabled by administrator in Firestore
                            globallyDisabledKeys.add(docKey)
                            globallyDisabledKeys.add(docNameKey)
                            globallyDisabledKeys.add(normalizeExtensionIdentifier(docKey))
                            globallyDisabledKeys.add(normalizeExtensionIdentifier(docNameKey))
                            Log.i(TAG, "EXTENSION_SYNC: Extension ${ext.name} is globally DISABLED by server.")
                        } else {
                            val plugin = ext.toAvailablePlugin()
                            allCatalogPlugins.add(plugin)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying extensions collection: ${e.message}")
            }

            // 6b. Parallel non-blocking fetch from registered repository manifests
            val allRepoUrls = mutableSetOf<String>()
            RepositoryManager.BUILT_IN_PRESETS.forEach { allRepoUrls.add(it.url) }
            runCatching {
                db.extensionDao().getAllRepositoriesSync().forEach { allRepoUrls.add(it.url) }
            }

            coroutineScope {
                val deferredList = allRepoUrls.map { repoUrl ->
                    async(Dispatchers.IO) {
                        withTimeoutOrNull(5000L) {
                            fetchRepositoryPlugins(repoUrl)
                        } ?: emptyList()
                    }
                }
                val repoResults = deferredList.awaitAll()
                for (plugins in repoResults) {
                    allCatalogPlugins.addAll(plugins)
                }
            }

            // 6c. Include installed extensions from local DB
            runCatching {
                val installed = db.extensionDao().getAllInstalledExtensionsSync()
                for (inst in installed) {
                    allCatalogPlugins.add(
                        AvailablePlugin(
                            name = inst.name,
                            internalName = inst.pkgName,
                            version = inst.version,
                            versionCode = inst.versionCode,
                            description = inst.description,
                            url = inst.repositoryUrl ?: "",
                            iconUrl = inst.iconUrl,
                            lang = inst.lang,
                            repositoryUrl = inst.repositoryUrl ?: "",
                            isInstalled = true,
                            isEnabled = inst.isEnabled
                        )
                    )
                }
            }

            // Enrich any plugins that are missing direct download URLs from matching repo plugins
            val enrichedCatalog = mutableListOf<AvailablePlugin>()
            for (p in allCatalogPlugins) {
                var current = p
                if (current.url.isBlank()) {
                    val fallback = allCatalogPlugins.firstOrNull { 
                        it.url.isNotBlank() && matchesExtensionOrProvider(current.name, it) 
                    }
                    if (fallback != null && fallback.url.isNotBlank()) {
                        current = current.copy(url = fallback.url)
                    }
                }
                enrichedCatalog.add(current)
            }

            // Deduplicate catalog by normalized key
            val masterCatalog = enrichedCatalog.distinctBy { 
                normalizeExtensionIdentifier(it.internalName.ifBlank { it.name }) 
            }
            Log.i(TAG, "EXTENSION_SYNC: Built master catalog with ${masterCatalog.size} available plugins across all sources.")

            // STEP 7: Compute final target extensions to install (Extension Name = Provider Name matching)
            val targetPluginsToInstall = mutableListOf<AvailablePlugin>()
            val resolvedIdsList = mutableListOf<String>()

            if (isAllowAll) {
                // All available extensions minus blocked and minus globally disabled
                for (plugin in masterCatalog) {
                    val normName = normalizeExtensionIdentifier(plugin.name)
                    val normInternal = normalizeExtensionIdentifier(plugin.internalName)

                    val isBlocked = blockedSet.contains(normName) || blockedSet.contains(normInternal) ||
                            blockedSet.any { matchesExtensionOrProvider(it, plugin) }
                    val isGloballyDisabled = globallyDisabledKeys.contains(normName) || globallyDisabledKeys.contains(normInternal)

                    if (!isBlocked && !isGloballyDisabled) {
                        targetPluginsToInstall.add(plugin)
                        resolvedIdsList.add(plugin.name)
                    }
                }
            } else {
                // Exact match of (EffectivePlanExtensions + UserCustomExtensions - BlockedExtensions)
                // Supports extensionName == providerName, internalName, and partial identifiers
                val targetIds = (inheritedExtensions + customSet).filterNot { target ->
                    val norm = normalizeExtensionIdentifier(target)
                    blockedSet.contains(norm) || 
                            blockedSet.contains(target.lowercase(Locale.ROOT)) ||
                            globallyDisabledKeys.contains(norm) ||
                            globallyDisabledKeys.contains(target.lowercase(Locale.ROOT))
                }

                for (targetId in targetIds) {
                    val matchedPlugin = masterCatalog.firstOrNull { plugin ->
                        matchesExtensionOrProvider(targetId, plugin)
                    }

                    if (matchedPlugin != null) {
                        if (!targetPluginsToInstall.any { matchesExtensionOrProvider(it.internalName, matchedPlugin) }) {
                            targetPluginsToInstall.add(matchedPlugin)
                            resolvedIdsList.add(matchedPlugin.name)
                        }
                    } else {
                        Log.w(TAG, "EXTENSION_SYNC: Target extension/provider '$targetId' requested by plan/custom, but not yet present in repository catalogs.")
                    }
                }
            }

            _resolvedExtensionIds.value = resolvedIdsList
            Log.i(TAG, "EXTENSION_SYNC: Resolved ${targetPluginsToInstall.size} target extensions for user $uid: ${targetPluginsToInstall.map { "${it.name} (${it.internalName})" }}")

            // STEP 8: Remove blocked or disallowed extensions if currently installed
            val currentInstalled = db.extensionDao().getAllInstalledExtensionsSync()
            val targetNormKeySet = targetPluginsToInstall.map { normalizeExtensionIdentifier(it.internalName) }.toSet()
            val targetNormNameSet = targetPluginsToInstall.map { normalizeExtensionIdentifier(it.name) }.toSet()

            for (installed in currentInstalled) {
                val installedNormKey = normalizeExtensionIdentifier(installed.pkgName)
                val installedNormName = normalizeExtensionIdentifier(installed.name)

                val isExplicitlyBlocked = blockedSet.contains(installedNormKey) || 
                        blockedSet.contains(installedNormName) ||
                        globallyDisabledKeys.contains(installedNormKey) || 
                        globallyDisabledKeys.contains(installedNormName)

                val isDisallowed = !isAllowAll && 
                        !targetNormKeySet.contains(installedNormKey) && 
                        !targetNormNameSet.contains(installedNormName) &&
                        !targetPluginsToInstall.any { matchesExtensionOrProvider(installed.pkgName, it) || matchesExtensionOrProvider(installed.name, it) }

                if (isExplicitlyBlocked) {
                    _syncStatus.value = "Uninstalling explicitly blocked extension: ${installed.name}..."
                    Log.i(TAG, "EXTENSION_SYNC: Removing explicitly blocked extension ${installed.name}")
                    try {
                        extensionManager.uninstallExtension(installed.pkgName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed uninstalling extension ${installed.pkgName}", e)
                    }
                }
            }

            // STEP 9: Install ONLY allowed extensions & update outdated ones
            val freshInstalledList = db.extensionDao().getAllInstalledExtensionsSync()
            val freshInstalledMap = freshInstalledList.associateBy { normalizeExtensionIdentifier(it.pkgName) }
            var installCount = 0

            for (plugin in targetPluginsToInstall) {
                val normKey = normalizeExtensionIdentifier(plugin.internalName)
                val existing = freshInstalledMap[normKey]

                val fileExistsOnDisk = existing?.localFilePath?.let { path ->
                    val f = File(path)
                    f.exists() && f.length() > 100
                } == true

                val isMissing = existing == null || !fileExistsOnDisk
                val isOutdated = existing != null && fileExistsOnDisk && existing.versionCode < plugin.versionCode

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
            val snapshot = withTimeoutOrNull(6000L) {
                firestore.collection(PLANS_COLLECTION).get().await()
            }
            val plans = mutableListOf<PlanConfig>()
            if (snapshot != null && !snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    PlanConfig.fromSnapshot(doc)?.let { plans.add(it) }
                }
            }
            if (plans.isEmpty()) {
                val defaultPlans = listOf("free", "premium", "vip", "admin").map { PlanConfig.defaultForPlan(it) }
                scope.launch {
                    defaultPlans.forEach { p ->
                        try {
                            firestore.collection(PLANS_COLLECTION).document(p.id).set(p.toMap(), SetOptions.merge())
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed auto-seeding plan ${p.id}: ${e.message}")
                        }
                    }
                }
                defaultPlans
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
            resolvedPlanInheritanceCache.clear()
            
            // Initiate Firestore write (commits locally immediately and syncs with cloud)
            val task = firestore.collection(PLANS_COLLECTION).document(planId)
                .set(plan.toMap(), SetOptions.merge())
            
            withTimeoutOrNull(5000L) {
                task.await()
            }
            Log.i(TAG, "ADMIN_ACTION: Saved plan $planId to Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving plan ${plan.id}: ${e.message}", e)
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
            resolvedPlanInheritanceCache.clear()
            
            val task = firestore.collection(PLANS_COLLECTION).document(cleanId).delete()
            withTimeoutOrNull(5000L) {
                task.await()
            }
            Log.i(TAG, "ADMIN_ACTION: Deleted plan $cleanId from Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting plan $planId: ${e.message}", e)
            false
        }
    }

    /**
     * Fetches user permissions from Firestore (`user_permissions/{uid}`).
     */
    suspend fun fetchUserPermissions(uid: String): UserPermissions = withContext(Dispatchers.IO) {
        try {
            val doc = withTimeoutOrNull(6000L) {
                firestore.collection(PERMISSIONS_COLLECTION).document(uid).get().await()
            }
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
            if (uid.isBlank()) return@withContext false
            val task = firestore.collection(PERMISSIONS_COLLECTION).document(uid)
                .set(permissions.toMap(), SetOptions.merge())
            withTimeoutOrNull(5000L) {
                task.await()
            }
            if (auth.currentUser?.uid == uid) {
                _userPermissions.value = permissions
                _userPlan.value = permissions.plan
            }
            Log.i(TAG, "ADMIN_ACTION: Saved permissions for user $uid -> Plan: ${permissions.plan}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving permissions for user $uid: ${e.message}", e)
            false
        }
    }

    /**
     * Fetches all available extensions in the system dynamically with full metadata.
     */
    suspend fun fetchAllAvailableExtensions(): List<FirestoreExtension> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, FirestoreExtension>()

        // 1. Fill from all built-in preset catalogs & user repos in parallel
        val allRepoUrls = mutableSetOf<String>()
        RepositoryManager.BUILT_IN_PRESETS.forEach { allRepoUrls.add(it.url) }
        runCatching {
            db.extensionDao().getAllRepositoriesSync().forEach { allRepoUrls.add(it.url) }
        }

        coroutineScope {
            val deferredList = allRepoUrls.map { repoUrl ->
                async(Dispatchers.IO) {
                    val plugins = withTimeoutOrNull(5000L) { fetchRepositoryPlugins(repoUrl) } ?: emptyList()
                    val repoName = RepositoryManager.BUILT_IN_PRESETS.firstOrNull { it.url == repoUrl }?.name ?: "Repository"
                    Pair(repoName, Pair(repoUrl, plugins))
                }
            }
            val repoOutputs = deferredList.awaitAll()
            for ((repoName, pair) in repoOutputs) {
                val (repoUrl, plugins) = pair
                for (p in plugins) {
                    val key = normalizeExtensionIdentifier(p.internalName.ifBlank { p.name })
                    result[key] = FirestoreExtension(
                        id = key,
                        name = p.name,
                        internalName = p.internalName,
                        repository = repoName,
                        repositoryUrl = repoUrl,
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

        // 2. Include installed extensions from local DB
        runCatching {
            val installed = db.extensionDao().getAllInstalledExtensionsSync()
            for (inst in installed) {
                val key = normalizeExtensionIdentifier(inst.pkgName.ifBlank { inst.name })
                val existing = result[key]
                if (existing == null) {
                    result[key] = FirestoreExtension(
                        id = key,
                        name = inst.name,
                        internalName = inst.pkgName,
                        repository = "Installed",
                        url = inst.repositoryUrl ?: inst.localFilePath ?: "",
                        version = inst.version,
                        versionCode = inst.versionCode,
                        description = inst.description,
                        lang = inst.lang ?: "en",
                        enabled = inst.isEnabled
                    )
                }
            }
        }

        // 3. Merge / override from Firestore
        try {
            val snap = withTimeoutOrNull(5000L) {
                firestore.collection(EXTENSIONS_COLLECTION).get().await()
            }
            if (snap != null && !snap.isEmpty) {
                for (doc in snap.documents) {
                    val ext = FirestoreExtension.fromSnapshot(doc)
                    val key = normalizeExtensionIdentifier(ext.internalName.ifBlank { ext.id })
                    val existing = result[key]
                    val finalUrl = ext.url.ifBlank { existing?.url ?: "" }
                    result[key] = ext.copy(
                        url = finalUrl,
                        version = if (ext.version.isNotBlank()) ext.version else existing?.version ?: "1.0.0",
                        versionCode = if (ext.versionCode > 0) ext.versionCode else existing?.versionCode ?: 1
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching extensions collection: ${e.message}")
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
            if (cleanPlan.isBlank() || cleanExt.isBlank()) return@withContext false

            val doc = withTimeoutOrNull(4000L) {
                firestore.collection(PLANS_COLLECTION).document(cleanPlan).get().await()
            }
            val plan = PlanConfig.fromSnapshot(doc) ?: PlanConfig.defaultForPlan(cleanPlan)

            val newAllowed = plan.allowedExtensions.toMutableList()
            if (enable) {
                if (!newAllowed.any { it.equals(cleanExt, ignoreCase = true) }) {
                    newAllowed.add(cleanExt)
                }
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
                            var subUrl = lists.getString(i).trim()
                            if (subUrl.isNotBlank() && !subUrl.startsWith("http://") && !subUrl.startsWith("https://")) {
                                val baseUrl = pluginListUrl.substringBeforeLast("/") + "/"
                                subUrl = baseUrl + subUrl.removePrefix("./").removePrefix("/")
                            }
                            results.addAll(fetchRepositoryPlugins(subUrl))
                        }
                    }
                }
                if (root.has("providers")) {
                    val array = root.optJSONArray("providers")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            parsePlugin(obj, pluginListUrl)?.let { results.add(it) }
                        }
                    }
                }
                if (root.has("plugins")) {
                    val array = root.optJSONArray("plugins")
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
        
        // Handle integer version (CloudStream standard: "version": 33) or string version
        val versionCode = obj.optInt("versionCode", obj.optInt("version", 1))
        val version = obj.optString("version", "$versionCode")
        val description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null
        var url = obj.optString("url", "")
        val iconUrl = if (obj.has("iconUrl") && !obj.isNull("iconUrl")) obj.getString("iconUrl") else null
        val lang = obj.optString("lang", obj.optString("language", "en"))

        if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            val baseUrl = repoUrl.substringBeforeLast("/") + "/"
            url = baseUrl + url.removePrefix("./").removePrefix("/")
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
            url = url,
            description = description ?: "Server Managed Extension",
            iconUrl = iconUrl,
            repositoryUrl = repoUrl,
            authors = authors,
            tvTypes = tvTypes,
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
