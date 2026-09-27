package xyz.mpv.rex.cinehub.provider.server.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Data representation of user permissions in Firestore (`user_permissions/{uid}`).
 *
 * Example:
 * {
 *   "plan": "premium",
 *   "enabled": true,
 *   "customExtensions": [],
 *   "blockedExtensions": [],
 *   "customRepositories": [],
 *   "blockedRepositories": []
 * }
 */
@IgnoreExtraProperties
data class UserPermissions(
    val plan: String = "free",
    val enabled: Boolean = true,
    val customExtensions: List<String> = emptyList(),
    val blockedExtensions: List<String> = emptyList(),
    val customRepositories: List<String> = emptyList(),
    val blockedRepositories: List<String> = emptyList()
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot?): UserPermissions {
            if (doc == null || !doc.exists()) return UserPermissions()
            val plan = doc.getString("plan") ?: "free"
            val enabled = doc.getBoolean("enabled") ?: true

            @Suppress("UNCHECKED_CAST")
            val customExtensions = (doc.get("customExtensions") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val blockedExtensions = (doc.get("blockedExtensions") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val customRepositories = (doc.get("customRepositories") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val blockedRepositories = (doc.get("blockedRepositories") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            return UserPermissions(
                plan = plan.trim().lowercase(),
                enabled = enabled,
                customExtensions = customExtensions,
                blockedExtensions = blockedExtensions,
                customRepositories = customRepositories,
                blockedRepositories = blockedRepositories
            )
        }
    }
}

/**
 * Data representation of plan configurations in Firestore (`plans/{planId}`).
 *
 * Example:
 * {
 *   "name": "Premium",
 *   "repositories": ["megix_repo", "phisher_repo"]
 * }
 */
@IgnoreExtraProperties
data class PlanConfig(
    val id: String = "free",
    val name: String = "Free",
    val repositories: List<String> = emptyList()
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot?): PlanConfig? {
            if (doc == null || !doc.exists()) return null
            val id = doc.id.lowercase()
            val name = doc.getString("name") ?: id.replaceFirstChar { it.uppercase() }

            @Suppress("UNCHECKED_CAST")
            val repositories = (doc.get("repositories") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            return PlanConfig(
                id = id,
                name = name,
                repositories = repositories
            )
        }

        fun defaultForPlan(planId: String): PlanConfig {
            val normalized = planId.trim().lowercase()
            return when (normalized) {
                "premium" -> PlanConfig(
                    id = "premium",
                    name = "Premium",
                    repositories = listOf("megix_repo", "phisher_repo")
                )
                "vip" -> PlanConfig(
                    id = "vip",
                    name = "VIP",
                    repositories = listOf("megix_repo", "phisher_repo", "cnc_repo")
                )
                "admin" -> PlanConfig(
                    id = "admin",
                    name = "Admin",
                    repositories = listOf("*")
                )
                else -> PlanConfig(
                    id = "free",
                    name = "Free",
                    repositories = listOf("megix_repo")
                )
            }
        }
    }
}

/**
 * Data representation of repositories in Firestore (`repositories/{repositoryId}`).
 *
 * Example:
 * {
 *   "name": "Megix Repo",
 *   "description": "Hindi & English Providers",
 *   "pluginListUrl": "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/plugins.json",
 *   "enabled": true
 * }
 */
@IgnoreExtraProperties
data class FirestoreRepository(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val pluginListUrl: String = "",
    val enabled: Boolean = true
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): FirestoreRepository {
            val id = doc.id
            val name = doc.getString("name") ?: id.replaceFirstChar { it.uppercase() }
            val description = doc.getString("description") ?: ""
            val pluginListUrl = doc.getString("pluginListUrl") 
                ?: doc.getString("url") 
                ?: ""
            val enabled = doc.getBoolean("enabled") ?: true

            return FirestoreRepository(
                id = id,
                name = name,
                description = description,
                pluginListUrl = pluginListUrl,
                enabled = enabled
            )
        }

        val BUILT_IN_DEFAULTS = listOf(
            FirestoreRepository(
                id = "megix_repo",
                name = "Megix Repo",
                description = "Hindi & English Providers",
                pluginListUrl = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/plugins.json",
                enabled = true
            ),
            FirestoreRepository(
                id = "phisher_repo",
                name = "Phisher Repo",
                description = "High-speed scrapers & multi-source providers",
                pluginListUrl = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/plugins.json",
                enabled = true
            ),
            FirestoreRepository(
                id = "cnc_repo",
                name = "CNCVerse Repo",
                description = "CNC community multimedia sources",
                pluginListUrl = "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/plugins.json",
                enabled = true
            )
        )
    }
}

/**
 * Data representation of extensions in Firestore (`extensions/{extensionId}`).
 *
 * Example:
 * {
 *   "name": "Bollyflix",
 *   "internalName": "Bollyflix",
 *   "repositoryId": "megix_repo",
 *   "url": "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/Bollyflix.cs3",
 *   "version": 33,
 *   "enabled": true
 * }
 */
@IgnoreExtraProperties
data class FirestoreExtension(
    val id: String = "",
    val name: String = "",
    val internalName: String = "",
    val repositoryId: String = "",
    val url: String = "",
    val version: Int = 1,
    val enabled: Boolean = true
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): FirestoreExtension {
            val id = doc.id
            val name = doc.getString("name") ?: id
            val internalName = doc.getString("internalName") ?: id
            val repositoryId = doc.getString("repositoryId") ?: ""
            val url = doc.getString("url") ?: doc.getString("downloadUrl") ?: ""
            val version = (doc.get("version") as? Number)?.toInt() ?: 1
            val enabled = doc.getBoolean("enabled") ?: true

            return FirestoreExtension(
                id = id,
                name = name,
                internalName = internalName,
                repositoryId = repositoryId,
                url = url,
                version = version,
                enabled = enabled
            )
        }
    }
}
