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
 * Supports recursive inheritance:
 * Free -> Premium (inherits Free) -> VIP (inherits Premium) -> Admin (all extensions).
 */
@IgnoreExtraProperties
data class PlanConfig(
    val id: String = "free",
    val name: String = "Free",
    val description: String = "",
    val allowedExtensions: List<String> = emptyList(),
    val allowAllExtensions: Boolean = false,
    val inherits: String? = null,
    val price: String = "₹0",
    val strikePrice: String = "₹0",
    val enabled: Boolean = true
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "allowedExtensions" to allowedExtensions,
        "allowAllExtensions" to allowAllExtensions,
        "inherits" to inherits,
        "price" to price,
        "strikePrice" to strikePrice,
        "enabled" to enabled
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

            val inherits = doc.getString("inherits")?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != id }
            val price = doc.getString("price") ?: "₹0"
            val strikePrice = doc.getString("strikePrice") ?: "₹0"
            val enabled = doc.getBoolean("enabled") ?: true

            return PlanConfig(
                id = id,
                name = name,
                description = description,
                allowedExtensions = allowedExtensions.filter { it != "*" },
                allowAllExtensions = allowAllExtensions,
                inherits = inherits,
                price = price,
                strikePrice = strikePrice,
                enabled = enabled
            )
        }

        fun defaultForPlan(planId: String): PlanConfig {
            val normalized = planId.trim().lowercase()
            return when (normalized) {
                "admin", "super_admin", "developer", "owner" -> PlanConfig(
                    id = "admin",
                    name = "Admin",
                    description = "Full system administration and extension access",
                    allowedExtensions = emptyList(),
                    allowAllExtensions = true,
                    inherits = "vip",
                    price = "₹0",
                    strikePrice = "₹0",
                    enabled = true
                )
                "vip" -> PlanConfig(
                    id = "vip",
                    name = "VIP",
                    description = "Exclusive high-speed providers and VIP catalog",
                    allowedExtensions = emptyList(),
                    allowAllExtensions = true,
                    inherits = "premium",
                    price = "₹299",
                    strikePrice = "₹499",
                    enabled = true
                )
                "premium" -> PlanConfig(
                    id = "premium",
                    name = "Premium",
                    description = "Popular high-speed scrapers and movie/TV providers",
                    allowedExtensions = emptyList(),
                    allowAllExtensions = true,
                    inherits = "free",
                    price = "₹99",
                    strikePrice = "₹199",
                    enabled = true
                )
                else -> PlanConfig(
                    id = "free",
                    name = "Free",
                    description = "Baseline curated providers for standard streaming",
                    allowedExtensions = emptyList(),
                    allowAllExtensions = true,
                    inherits = null,
                    price = "₹0",
                    strikePrice = "₹0",
                    enabled = true
                )
            }
        }
    }
}

/**
 * Data representation of repositories in Firestore (`repositories/{repositoryId}`).
 * Repositories are metadata containers ONLY.
 */
@IgnoreExtraProperties
data class FirestoreRepository(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val pluginListUrl: String = "",
    val enabled: Boolean = true,
    val pluginCount: Int = 0,
    val lastSync: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "pluginListUrl" to pluginListUrl,
        "enabled" to enabled,
        "pluginCount" to pluginCount,
        "lastSync" to lastSync
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): FirestoreRepository {
            val id = doc.id
            val name = doc.getString("name") ?: id.replaceFirstChar { it.uppercase() }
            val description = doc.getString("description") ?: ""
            val pluginListUrl = doc.getString("pluginListUrl") 
                ?: doc.getString("url") 
                ?: ""
            val enabled = doc.getBoolean("enabled") ?: true
            val pluginCount = (doc.get("pluginCount") as? Number)?.toInt() ?: 0
            val lastSync = (doc.get("lastSync") as? Number)?.toLong() ?: System.currentTimeMillis()

            return FirestoreRepository(
                id = id,
                name = name,
                description = description,
                pluginListUrl = pluginListUrl,
                enabled = enabled,
                pluginCount = pluginCount,
                lastSync = lastSync
            )
        }
    }
}

/**
 * Data representation of extensions in Firestore (`extensions/{extensionId}`).
 */
@IgnoreExtraProperties
data class FirestoreExtension(
    val id: String = "",
    val name: String = "",
    val internalName: String = "",
    val repository: String = "",
    val repositoryId: String = "",
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
    val enabled: Boolean = true,
    val healthScore: Int = 100
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "internalName" to internalName,
        "repository" to repository,
        "repositoryId" to repositoryId.ifBlank { repository },
        "repositoryUrl" to repositoryUrl,
        "url" to url,
        "tvUrl" to tvUrl,
        "iconUrl" to iconUrl,
        "version" to version,
        "versionCode" to versionCode,
        "description" to description,
        "lang" to lang,
        "authors" to authors,
        "tvTypes" to tvTypes,
        "enabled" to enabled,
        "healthScore" to healthScore
    )

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
            val repositoryId = doc.getString("repositoryId") ?: repository
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
            val healthScore = (doc.get("healthScore") as? Number)?.toInt() ?: 100

            return FirestoreExtension(
                id = id,
                name = name,
                internalName = internalName,
                repository = repository,
                repositoryId = repositoryId,
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
                enabled = enabled,
                healthScore = healthScore
            )
        }
    }
}

/**
 * Analytics Data Models for Batched Local & Remote Storage
 */
data class SearchAnalyticsEvent(
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val resultCount: Int = 0,
    val selectedProvider: String? = null
)

data class StreamAnalyticsEvent(
    val mediaTitle: String,
    val provider: String,
    val extractor: String? = null,
    val isSuccess: Boolean = true,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val error: String? = null
)

data class ProviderHealthMetric(
    val providerName: String,
    val searchPassed: Boolean = true,
    val homePassed: Boolean = true,
    val loadPassed: Boolean = true,
    val streamPassed: Boolean = true,
    val healthScore: Int = 100, // 0 to 100%
    val lastTested: Long = System.currentTimeMillis()
)

data class DailyAnalyticsSummary(
    val date: String,
    val totalActiveUsers: Int = 0,
    val totalSearches: Int = 0,
    val totalStreams: Int = 0,
    val providerSuccessRate: Int = 100,
    val topExtension: String = "None",
    val topSearches: List<String> = emptyList()
)
