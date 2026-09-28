package xyz.mpv.rex.cinehub.provider.server.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin

/**
 * Data representation of user permissions in Firestore (`user_permissions/{uid}`).
 *
 * Example:
 * {
 *   "plan": "premium",
 *   "enabled": true,
 *   "customExtensions": ["superstream", "bollyflix"],
 *   "blockedExtensions": ["streamwish"]
 * }
 */
@IgnoreExtraProperties
data class UserPermissions(
    val plan: String = "free",
    val enabled: Boolean = true,
    val customExtensions: List<String> = emptyList(),
    val blockedExtensions: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "plan" to plan,
        "enabled" to enabled,
        "customExtensions" to customExtensions,
        "blockedExtensions" to blockedExtensions
    )

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

            return UserPermissions(
                plan = plan.trim().lowercase(),
                enabled = enabled,
                customExtensions = customExtensions,
                blockedExtensions = blockedExtensions
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
 *   "description": "Premium tier extension access",
 *   "allowedExtensions": ["bollyflix", "vega", "uhdmovies", "superstream"],
 *   "allowAllExtensions": false
 * }
 */
@IgnoreExtraProperties
data class PlanConfig(
    val id: String = "free",
    val name: String = "Free",
    val description: String = "",
    val allowedExtensions: List<String> = emptyList(),
    val allowAllExtensions: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "allowedExtensions" to allowedExtensions,
        "allowAllExtensions" to allowAllExtensions
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot?): PlanConfig? {
            if (doc == null || !doc.exists()) return null
            val id = doc.id.lowercase()
            val name = doc.getString("name") ?: id.replaceFirstChar { it.uppercase() }
            val description = doc.getString("description") ?: ""

            @Suppress("UNCHECKED_CAST")
            val allowedExtensions = (doc.get("allowedExtensions") as? List<*>)
                ?.filterIsInstance<String>() 
                ?: (doc.get("extensions") as? List<*>)?.filterIsInstance<String>()
                ?: emptyList()

            val allowAllExtensions = doc.getBoolean("allowAllExtensions") 
                ?: doc.getBoolean("allExtensions") 
                ?: allowedExtensions.contains("*")

            return PlanConfig(
                id = id,
                name = name,
                description = description,
                allowedExtensions = allowedExtensions.filter { it != "*" },
                allowAllExtensions = allowAllExtensions
            )
        }

        fun defaultForPlan(planId: String): PlanConfig {
            val normalized = planId.trim().lowercase()
            return when (normalized) {
                "admin", "super_admin", "developer" -> PlanConfig(
                    id = "admin",
                    name = "Admin",
                    description = "Full system administration and extension access",
                    allowedExtensions = emptyList(),
                    allowAllExtensions = true
                )
                "vip" -> PlanConfig(
                    id = "vip",
                    name = "VIP",
                    description = "Exclusive high-speed providers and VIP catalog",
                    allowedExtensions = listOf(
                        "bollyflix", "vega", "uhdmovies", "allmoviesforyou",
                        "superstream", "moviesmod", "topmovies", "streamwish",
                        "phisher", "castletv"
                    ),
                    allowAllExtensions = false
                )
                "premium" -> PlanConfig(
                    id = "premium",
                    name = "Premium",
                    description = "Popular high-speed scrapers and movie/TV providers",
                    allowedExtensions = listOf(
                        "bollyflix", "vega", "uhdmovies", "superstream", "moviesmod"
                    ),
                    allowAllExtensions = false
                )
                else -> PlanConfig(
                    id = "free",
                    name = "Free",
                    description = "Baseline curated providers for standard streaming",
                    allowedExtensions = listOf(
                        "bollyflix", "vega", "superstream"
                    ),
                    allowAllExtensions = false
                )
            }
        }
    }
}

/**
 * Data representation of repositories in Firestore (`repositories/{repositoryId}`).
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
    }
}

/**
 * Data representation of extensions in Firestore (`extensions/{extensionId}`).
 *
 * Example:
 * {
 *   "name": "Bollyflix",
 *   "internalName": "bollyflix",
 *   "repository": "megix_repo_csx",
 *   "url": "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/Bollyflix.cs3",
 *   "version": "33.0.0",
 *   "versionCode": 33,
 *   "lang": "en",
 *   "enabled": true
 * }
 */
@IgnoreExtraProperties
data class FirestoreExtension(
    val id: String = "",
    val name: String = "",
    val internalName: String = "",
    val repository: String = "",
    val repositoryUrl: String = "",
    val url: String = "",
    val tvUrl: String? = null,
    val iconUrl: String? = null,
    val version: String = "1.0.0",
    val versionCode: Int = 1,
    val description: String? = null,
    val lang: String = "en",
    val authors: List<String> = emptyList(),
    val tvTypes: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    fun toAvailablePlugin(): AvailablePlugin {
        return AvailablePlugin(
            name = name.ifBlank { internalName },
            internalName = internalName.ifBlank { id },
            version = version,
            versionCode = versionCode,
            description = description ?: "Server Managed Extension",
            url = url,
            tvUrl = tvUrl,
            iconUrl = iconUrl,
            authors = authors,
            tvTypes = tvTypes,
            repositoryUrl = repositoryUrl,
            isInstalled = false,
            isEnabled = enabled,
            lang = lang
        )
    }

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): FirestoreExtension {
            val id = doc.id
            val name = doc.getString("name") ?: id
            val internalName = doc.getString("internalName") ?: id
            val repository = doc.getString("repository") ?: doc.getString("repositoryId") ?: ""
            val repositoryUrl = doc.getString("repositoryUrl") ?: ""
            val url = doc.getString("url") ?: doc.getString("downloadUrl") ?: ""
            val tvUrl = doc.getString("tvUrl")
            val iconUrl = doc.getString("iconUrl")
            val versionStr = doc.getString("version") ?: doc.getString("versionString") ?: "1.0.0"
            val versionCode = (doc.get("versionCode") as? Number)?.toInt() 
                ?: (doc.get("version") as? Number)?.toInt() 
                ?: 1
            val description = doc.getString("description")
            val lang = doc.getString("lang") ?: doc.getString("language") ?: "en"

            @Suppress("UNCHECKED_CAST")
            val authors = (doc.get("authors") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val tvTypes = (doc.get("tvTypes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            val enabled = doc.getBoolean("enabled") ?: true

            return FirestoreExtension(
                id = id,
                name = name,
                internalName = internalName,
                repository = repository,
                repositoryUrl = repositoryUrl,
                url = url,
                tvUrl = tvUrl,
                iconUrl = iconUrl,
                version = versionStr,
                versionCode = versionCode,
                description = description,
                lang = lang,
                authors = authors,
                tvTypes = tvTypes,
                enabled = enabled
            )
        }
    }
}
