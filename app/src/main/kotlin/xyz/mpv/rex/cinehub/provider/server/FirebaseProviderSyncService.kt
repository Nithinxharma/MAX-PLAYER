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
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.database.MpvExDatabase

/**
 * FirebaseProviderSyncService implements the Firebase-Controlled Repository & Provider Orchestration System.
 *
 * Responsibilities:
 * 1. Auth Listener Trigger on Login
 * 2. Read `repositories/global` -> call RepositoryManager.addRepository() for missing repos -> RepositoryManager.syncAllRepositories()
 * 3. Read `providers/global` -> list of { id, repository, enabled }
 * 4. Read `users/{uid}` -> map/list-based `providerAccess`
 * 5. Filter allowed providers (map: {"castletv": true, "superstream": true} or role == ADMIN or "*")
 * 6. Locate provider package from CloudStream repository metadata or downloadUrl
 * 7. Install via ExtensionManager -> PluginManager -> APIHolder
 * 8. Track installation state in `provider_installations/{uid}`
 * 9. Offline resilience via try-catch and cached Firestore / Room DB fallback
 */
class FirebaseProviderSyncService(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: MpvExDatabase,
    private val extensionManager: ExtensionManager,
    private val repositoryManager: RepositoryManager
) {
    companion object {
        private const val TAG = "FirebaseProviderSync"
        private const val REPOSITORIES_COLLECTION = "repositories"
        private const val PROVIDERS_COLLECTION = "providers"
        private const val MANIFEST_COLLECTION = "provider_manifests"
        private const val MANIFEST_DOC = "global"
        private const val INSTALLATIONS_COLLECTION = "provider_installations"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                scope.launch {
                    syncUserProviders(user.uid)
                }
            }
        }
    }

    suspend fun syncUserProviders(uid: String = auth.currentUser?.uid ?: ""): Boolean = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext false
        if (_isSyncing.value) return@withContext false

        _isSyncing.value = true
        _syncStatus.value = "Fetching Firebase repository and provider configs..."
        Log.i(TAG, "ORCHESTRATION_SYNC: Starting sync for user $uid...")

        try {
            // STEP 1: Read user profile from users/{uid}
            val userDoc = try {
                firestore.collection("users").document(uid).get().await()
            } catch (e: Exception) {
                Log.w(TAG, "Offline/Error reading user doc: ${e.message}")
                null
            }

            val role = userDoc?.getString("role") ?: "USER"

            // Support both Map-based and List-based providerAccess datasets
            val rawAccess = userDoc?.get("providerAccess")
            val allowedSet = mutableSetOf<String>()

            when (rawAccess) {
                is Map<*, *> -> {
                    rawAccess.forEach { (key, value) ->
                        if (key is String && (value == true || value == "true")) {
                            allowedSet.add(key.lowercase())
                        }
                    }
                }
                is List<*> -> {
                    rawAccess.filterIsInstance<String>().forEach {
                        allowedSet.add(it.lowercase())
                    }
                }
            }

            if (allowedSet.isEmpty()) {
                allowedSet.addAll(listOf("castletv", "superstream"))
            }

            val isFullAccess = role == "ADMIN" || allowedSet.contains("*") || allowedSet.contains("all")

            // STEP 2: Read repositories/global
            val repoDoc = try {
                firestore.collection(REPOSITORIES_COLLECTION).document("global").get().await()
            } catch (e: Exception) {
                Log.w(TAG, "Offline/Error reading repositories/global: ${e.message}")
                null
            }

            val remoteRepos = mutableListOf<Map<String, Any>>()
            if (repoDoc != null && repoDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val list = repoDoc.get("repositories") as? List<Map<String, Any>>
                if (list != null) remoteRepos.addAll(list)
            }

            if (remoteRepos.isEmpty()) {
                Log.i(TAG, "ORCHESTRATION_SYNC: repositories/global is empty in Firestore. Triggering preset auto-discovery...")
                try {
                    repositoryManager.addAllPresets()
                    repositoryManager.syncAllRepositories()
                } catch (e: Exception) {
                    Log.w(TAG, "Error during preset auto-discovery fallback: ${e.message}")
                }
            }

            // Ensure repositories exist in RepositoryManager / Room DB
            val extensionDao = db.extensionDao()
            var addedNewRepo = false

            for (r in remoteRepos) {
                val repoId = (r["id"] as? String) ?: continue
                val repoUrl = (r["url"] as? String) ?: continue
                val enabled = r["enabled"] as? Boolean ?: true

                if (enabled) {
                    val existingRepo = extensionDao.getRepository(repoUrl)
                    if (existingRepo == null) {
                        try {
                            repositoryManager.addRepository(
                                url = repoUrl,
                                name = repoId.replaceFirstChar { it.uppercase() },
                                description = "Firebase Orchestrated Repository"
                            )
                            addedNewRepo = true
                            Log.i(TAG, "ORCHESTRATION_SYNC: Added repository $repoId ($repoUrl)")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error adding repository $repoId: ${e.message}")
                        }
                    }
                }
            }

            // STEP 3: Sync repository metadata
            if (addedNewRepo) {
                _syncStatus.value = "Syncing repository catalogs..."
                try {
                    repositoryManager.syncAllRepositories()
                } catch (e: Exception) {
                    Log.w(TAG, "Error syncing repositories: ${e.message}")
                }
            }

            // STEP 4: Read providers/global
            val providerDoc = try {
                firestore.collection(PROVIDERS_COLLECTION).document("global").get().await()
            } catch (e: Exception) {
                Log.w(TAG, "Offline/Error reading providers/global: ${e.message}")
                null
            }

            val remoteProviders = mutableListOf<Map<String, Any>>()
            if (providerDoc != null && providerDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val list = providerDoc.get("providers") as? List<Map<String, Any>>
                if (list != null) remoteProviders.addAll(list)
            }

            // Fallback to provider_manifests/global if providers/global is missing
            if (remoteProviders.isEmpty()) {
                val manifestDoc = try {
                    firestore.collection(MANIFEST_COLLECTION).document(MANIFEST_DOC).get().await()
                } catch (e: Exception) {
                    null
                }
                if (manifestDoc != null && manifestDoc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val list = manifestDoc.get("providers") as? List<Map<String, Any>>
                    if (list != null) remoteProviders.addAll(list)
                }
            }

            if (remoteProviders.isEmpty()) {
                remoteProviders.add(
                    mapOf(
                        "id" to "castletv",
                        "repository" to "hexated",
                        "downloadUrl" to "https://raw.githubusercontent.com/recloudstream/extensions/master/CastleTV.cs3",
                        "enabled" to true
                    )
                )
            }

            // STEP 5: Compare with installed plugins & install missing/outdated
            val installedExtensions = extensionDao.getAllInstalledExtensionsSync()
            val installedMap = installedExtensions.associateBy { it.pkgName.lowercase() }

            val installationReport = mutableMapOf<String, Map<String, Any>>()
            var installedCount = 0

            for (p in remoteProviders) {
                val id = (p["id"] as? String)?.lowercase() ?: continue
                val enabled = p["enabled"] as? Boolean ?: true
                val downloadUrl = p["downloadUrl"] as? String
                    ?: "https://raw.githubusercontent.com/recloudstream/extensions/master/${id.replaceFirstChar { it.uppercase() }}.cs3"
                val name = p["name"] as? String ?: id.replaceFirstChar { it.uppercase() }
                val versionNumber = (p["version"] as? Number)?.toInt() ?: 1

                val isAllowed = isFullAccess || allowedSet.contains(id)
                if (!enabled || !isAllowed) continue

                val existing = installedMap[id]
                val isMissing = existing == null
                val isOutdated = existing != null && existing.versionCode < versionNumber

                if ((isMissing || isOutdated) && downloadUrl.isNotBlank()) {
                    _syncStatus.value = "Installing/Updating $name..."
                    Log.i(TAG, "ORCHESTRATION_SYNC: ${if (isMissing) "Installing" else "Updating"} $name")

                    val pluginPayload = AvailablePlugin(
                        name = name,
                        internalName = id,
                        version = "$versionNumber.0.0",
                        versionCode = versionNumber,
                        url = downloadUrl,
                        description = "Firebase Orchestrated Provider",
                        iconUrl = null,
                        repositoryUrl = "firebase://repositories/global",
                        lang = "en"
                    )
                    try {
                        extensionManager.installExtension(pluginPayload)
                        installedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to install provider $id: ${e.message}")
                    }
                }

                installationReport[id] = mapOf(
                    "installed" to true
                )
            }

            // STEP 6: Update provider_installations/{uid}
            try {
                firestore.collection(INSTALLATIONS_COLLECTION).document(uid)
                    .set(installationReport, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write provider_installations record: ${e.message}")
            }

            // STEP 7: Refresh provider registry if new installations occurred
            if (installedCount > 0) {
                _syncStatus.value = "Reloading provider registry..."
                try {
                    extensionManager.loadInstalledExtensions()
                } catch (e: Exception) {
                    Log.w(TAG, "Error reloading extensions: ${e.message}")
                }
            }

            _syncStatus.value = "Sync complete"
            Log.i(TAG, "ORCHESTRATION_SYNC: Complete. Active APIHolder providers: ${APIHolder.allProviders.size}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "ORCHESTRATION_SYNC: Error during sync", e)
            _syncStatus.value = "Sync error: ${e.message}"
            return@withContext false
        } finally {
            _isSyncing.value = false
        }
    }
}
