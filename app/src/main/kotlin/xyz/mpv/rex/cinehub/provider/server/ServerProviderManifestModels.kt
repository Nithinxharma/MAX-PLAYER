package xyz.mpv.rex.cinehub.provider.server

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ProviderManifest(
    val schemaVersion: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val minAppVersionCode: Int = 1,
    val providers: List<ManagedProvider> = emptyList(),
    val revokedProviders: List<String> = emptyList()
)

@Keep
@Serializable
data class ManagedProvider(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val versionCode: Int = 1,
    val downloadUrl: String,
    val sha256: String? = null,
    val enabled: Boolean = true,
    val requiredRole: String = "USER",
    val description: String? = null,
    val lang: String = "en",
    val classes: List<String> = emptyList()
)
