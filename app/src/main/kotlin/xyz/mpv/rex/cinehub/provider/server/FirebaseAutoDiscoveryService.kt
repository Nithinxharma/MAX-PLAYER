package xyz.mpv.rex.cinehub.provider.server

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import xyz.mpv.rex.cinehub.extension.model.RepositorySyncResult
import java.util.Locale

/**
 * FirebaseAutoDiscoveryService
 *
 * Implements the MAX STREAM Firebase Auto-Discovery Database Engine.
 *
 * Automatically scans remote CloudStream repositories (repo.json, pluginLists, plugins.json),
 * extracts all metadata, and populates Firestore in real time without any manual data entry.
 *
 * Collections Managed:
 * - `repositories/{repositoryId}`
 * - `providers/{internalName}`
 * - `extensions/{internalName}`
 * - `repositories/global`
 * - `providers/global`
 * - `provider_manifests/global`
 */
class FirebaseAutoDiscoveryService(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val repositoryManager: RepositoryManager
) {
    companion object {
        private const val TAG = "FirebaseAutoDiscovery"
        private const val REPOSITORIES_COLLECTION = "repositories"
        private const val PROVIDERS_COLLECTION = "providers"
        private const val EXTENSIONS_COLLECTION = "extensions"
        private const val MANIFEST_COLLECTION = "provider_manifests"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Register listener with RepositoryManager so whenever any repo is synced,
        // auto-discovery automatically mirrors it into Firestore!
        repositoryManager.onRepositorySynced = { syncResult ->
            scope.launch {
                publishDiscoveredRepositoryData(syncResult)
            }
        }
    }

    /**
     * Publishes a discovered repository and its plugin catalog into Firestore.
     */
    suspend fun publishDiscoveredRepositoryData(syncResult: RepositorySyncResult): Boolean = withContext(Dispatchers.IO) {
        if (syncResult.error != null && syncResult.plugins.isEmpty()) {
            Log.w(TAG, "AUTO_DISCOVERY: Skipping sync for failed repository ${syncResult.repoUrl}: ${syncResult.error}")
            return@withContext false
        }

        try {
            val repositoryId = deriveRepositoryId(syncResult.repoName, syncResult.repoUrl)
            Log.i(TAG, "AUTO_DISCOVERY: Starting live Firestore generation for $repositoryId (${syncResult.plugins.size} providers found)...")

            // 1. Generate repositories/{repositoryId} document
            val repoDocData = hashMapOf(
                "id" to repositoryId,
                "name" to syncResult.repoName,
                "description" to (syncResult.plugins.firstOrNull()?.description ?: "Live Auto-Discovered CloudStream Repository"),
                "repositoryUrl" to syncResult.repoUrl,
                "pluginCount" to syncResult.plugins.size,
                "enabled" to true,
                "lastSync" to FieldValue.serverTimestamp()
            )

            firestore.collection(REPOSITORIES_COLLECTION)
                .document(repositoryId)
                .set(repoDocData, SetOptions.merge())
                .await()

            Log.i(TAG, "AUTO_DISCOVERY: Created repositories/$repositoryId")

            // 2. Fetch existing Firestore provider documents for this repository to handle provider removal/disappearance
            val existingSnapshot = try {
                firestore.collection(PROVIDERS_COLLECTION)
                    .whereEqualTo("repository", repositoryId)
                    .get()
                    .await()
            } catch (e: Exception) {
                null
            }

            val existingProviderIds = existingSnapshot?.documents?.map { it.id }?.toMutableSet() ?: mutableSetOf()
            val currentDiscoveredIds = mutableSetOf<String>()

            // 3. Generate documents for each provider in plugins
            for (plugin in syncResult.plugins) {
                val internalName = plugin.internalName.ifBlank {
                    plugin.name.lowercase(Locale.ROOT).replace("[^a-z0-9_]".toRegex(), "_")
                }
                currentDiscoveredIds.add(internalName)

                val providerDocData = hashMapOf(
                    "internalName" to internalName,
                    "name" to plugin.name,
                    "version" to plugin.versionCode,
                    "versionString" to plugin.version,
                    "language" to (plugin.lang ?: "en"),
                    "repository" to repositoryId,
                    "repositoryUrl" to plugin.repositoryUrl,
                    "description" to (plugin.description ?: ""),
                    "iconUrl" to (plugin.iconUrl ?: ""),
                    "authors" to plugin.authors,
                    "tvTypes" to plugin.tvTypes,
                    "url" to plugin.url,
                    "tvUrl" to (plugin.tvUrl ?: ""),
                    "enabled" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                // Write to providers/{internalName}
                firestore.collection(PROVIDERS_COLLECTION)
                    .document(internalName)
                    .set(providerDocData, SetOptions.merge())
                    .await()

                // Write to extensions/{internalName}
                val extensionDocData = hashMapOf(
                    "internalName" to internalName,
                    "name" to plugin.name,
                    "version" to plugin.version,
                    "versionCode" to plugin.versionCode,
                    "url" to plugin.url,
                    "tvUrl" to (plugin.tvUrl ?: ""),
                    "iconUrl" to (plugin.iconUrl ?: ""),
                    "repositoryUrl" to plugin.repositoryUrl,
                    "lang" to (plugin.lang ?: "en"),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                firestore.collection(EXTENSIONS_COLLECTION)
                    .document(internalName)
                    .set(extensionDocData, SetOptions.merge())
                    .await()
            }

            // 4. Handle Disappeared/Removed Providers (Soft-disable or remove missing records)
            existingProviderIds.removeAll(currentDiscoveredIds)
            for (disappearedId in existingProviderIds) {
                Log.w(TAG, "AUTO_DISCOVERY: Disappeared provider detected ($disappearedId). Updating status to disabled.")
                firestore.collection(PROVIDERS_COLLECTION)
                    .document(disappearedId)
                    .update(
                        mapOf(
                            "enabled" to false,
                            "status" to 0,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }

            // 5. Update global index docs for backward compatibility and fast client sync
            updateGlobalIndexes()

            Log.i(TAG, "AUTO_DISCOVERY: Successfully auto-generated Firestore records for $repositoryId (${currentDiscoveredIds.size} active providers).")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "AUTO_DISCOVERY: Failed to auto-generate Firestore database for ${syncResult.repoName}", e)
            return@withContext false
        }
    }

    /**
     * Rebuilds global index documents (`repositories/global`, `providers/global`, `provider_manifests/global`)
     * from active Firestore documents.
     */
    suspend fun updateGlobalIndexes() = withContext(Dispatchers.IO) {
        try {
            val reposSnap = firestore.collection(REPOSITORIES_COLLECTION).get().await()
            val repoList = reposSnap.documents.mapNotNull { doc ->
                if (doc.id == "global") return@mapNotNull null
                val id = doc.getString("id") ?: doc.id
                val url = doc.getString("repositoryUrl") ?: return@mapNotNull null
                val enabled = doc.getBoolean("enabled") ?: true
                mapOf("id" to id, "url" to url, "enabled" to enabled)
            }

            firestore.collection(REPOSITORIES_COLLECTION).document("global")
                .set(mapOf("repositories" to repoList), SetOptions.merge())
                .await()

            val providersSnap = firestore.collection(PROVIDERS_COLLECTION)
                .whereEqualTo("enabled", true)
                .get()
                .await()

            val providerList = providersSnap.documents.mapNotNull { doc ->
                if (doc.id == "global") return@mapNotNull null
                val id = doc.getString("internalName") ?: doc.id
                val repo = doc.getString("repository") ?: "global"
                val url = doc.getString("url") ?: return@mapNotNull null
                val name = doc.getString("name") ?: id
                val version = (doc.get("version") as? Number)?.toInt() ?: 1
                val enabled = doc.getBoolean("enabled") ?: true

                mapOf(
                    "id" to id,
                    "repository" to repo,
                    "downloadUrl" to url,
                    "name" to name,
                    "version" to version,
                    "enabled" to enabled
                )
            }

            val indexPayload = mapOf("providers" to providerList)
            firestore.collection(PROVIDERS_COLLECTION).document("global")
                .set(indexPayload, SetOptions.merge())
                .await()

            firestore.collection(MANIFEST_COLLECTION).document("global")
                .set(indexPayload, SetOptions.merge())
                .await()

            Log.i(TAG, "AUTO_DISCOVERY: Updated repositories/global and providers/global index documents (${providerList.size} providers).")
        } catch (e: Exception) {
            Log.w(TAG, "AUTO_DISCOVERY: Error updating global indexes: ${e.message}")
        }
    }

    /**
     * Triggers Auto-Discovery for all configured repositories.
     * Automatically ensures built-in presets are added if no repositories exist yet.
     */
    suspend fun discoverAndSyncAll(): Int = withContext(Dispatchers.IO) {
        try {
            val addedPresets = repositoryManager.addAllPresets()
            if (addedPresets > 0) {
                Log.i(TAG, "AUTO_DISCOVERY: Added $addedPresets default preset repositories.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AUTO_DISCOVERY: Error adding presets: ${e.message}")
        }

        val results = repositoryManager.syncAllRepositories()
        var successCount = 0
        for (res in results) {
            if (publishDiscoveredRepositoryData(res)) {
                successCount++
            }
        }
        successCount
    }

    private fun deriveRepositoryId(name: String, url: String): String {
        val cleanName = name.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')

        if (cleanName.isNotBlank() && cleanName != "repository" && cleanName != "failed_sync") {
            return cleanName
        }

        val urlSlug = url.substringAfterLast("/").substringBefore(".").lowercase(Locale.ROOT)
            .replace("[^a-z0-9]".toRegex(), "_")

        return urlSlug.ifBlank { "repo_" + url.hashCode().coerceAtLeast(0) }
    }
}
