package xyz.mpv.rex.auth.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data representation of a Max Stream authenticated user in Firestore (`users/{uid}`).
 * Designed with flexible field-mapping and safe fallback defaults for backward compatibility.
 */
@IgnoreExtraProperties
data class UserProfile(
    @get:PropertyName("uid")
    @set:PropertyName("uid")
    var uid: String = "",

    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String? = null,

    @get:PropertyName("email")
    @set:PropertyName("email")
    var email: String? = null,

    @get:PropertyName("photo")
    @set:PropertyName("photo")
    var photo: String? = null,

    @get:PropertyName("role")
    @set:PropertyName("role")
    var role: String = UserRole.USER,

    @get:PropertyName("premium")
    @set:PropertyName("premium")
    var premium: Boolean = false,

    @get:PropertyName("providerAccess")
    @set:PropertyName("providerAccess")
    var providerAccess: List<String> = listOf("castletv"),

    @get:PropertyName("installedProviders")
    @set:PropertyName("installedProviders")
    var installedProviders: Map<String, Long> = mapOf("castletv" to 14L),

    @ServerTimestamp
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Date? = null,

    @ServerTimestamp
    @get:PropertyName("lastLogin")
    @set:PropertyName("lastLogin")
    var lastLogin: Date? = null
) {
    val photoUrl: String?
        get() = photo

    companion object {
        /**
         * Robust parser that extracts UserProfile even if fields in Firestore use alternative keys
         * (e.g. displayName vs name, photoUrl vs photo, isAdmin vs role, isPremium vs premium).
         */
        fun fromSnapshot(doc: DocumentSnapshot): UserProfile {
            val uid = doc.getString("uid") ?: doc.id
            val name = doc.getString("name") 
                ?: doc.getString("displayName") 
                ?: doc.getString("username")
            val email = doc.getString("email")
            val photo = doc.getString("photo") 
                ?: doc.getString("photoUrl") 
                ?: doc.getString("avatar")
                ?: doc.getString("profileImage")

            // Determine role: check 'role', 'userRole', or boolean 'isAdmin'
            val rawRole = doc.getString("role") ?: doc.getString("userRole")
            val isAdminBoolean = doc.getBoolean("isAdmin") ?: false
            val resolvedRole = when {
                !rawRole.isNullOrBlank() -> rawRole
                isAdminBoolean -> UserRole.ADMIN
                else -> UserRole.USER
            }

            // Determine premium: check 'premium' or 'isPremium'
            val resolvedPremium = doc.getBoolean("premium") 
                ?: doc.getBoolean("isPremium") 
                ?: false

            @Suppress("UNCHECKED_CAST")
            val rawAccess = doc.get("providerAccess") as? List<String> ?: listOf("castletv")

            @Suppress("UNCHECKED_CAST")
            val rawInstalled = (doc.get("installedProviders") as? Map<String, Any>)?.mapValues {
                (it.value as? Number)?.toLong() ?: 1L
            } ?: mapOf("castletv" to 14L)

            val createdAt = doc.getDate("createdAt")
            val lastLogin = doc.getDate("lastLogin")

            return UserProfile(
                uid = uid,
                name = name,
                email = email,
                photo = photo,
                role = resolvedRole,
                premium = resolvedPremium,
                providerAccess = rawAccess,
                installedProviders = rawInstalled,
                createdAt = createdAt,
                lastLogin = lastLogin
            )
        }
    }
}
