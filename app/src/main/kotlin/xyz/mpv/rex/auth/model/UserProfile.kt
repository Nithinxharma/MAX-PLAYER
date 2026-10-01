package xyz.mpv.rex.auth.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.PropertyName

object UserRole {
    const val USER = "user"
    const val VIP = "vip"
    const val MODERATOR = "moderator"
    const val ADMIN = "admin"
    const val OWNER = "owner"
}

data class UserProfile(
    @get:PropertyName("uid")
    @set:PropertyName("uid")
    var uid: String = "",

    @get:PropertyName("email")
    @set:PropertyName("email")
    var email: String = "",

    @get:PropertyName("displayName")
    @set:PropertyName("displayName")
    var displayName: String = "",

    @get:PropertyName("photoUrl")
    @set:PropertyName("photoUrl")
    var photoUrl: String = "",

    @get:PropertyName("role")
    @set:PropertyName("role")
    var role: String = UserRole.USER,

    @get:PropertyName("plan")
    @set:PropertyName("plan")
    var plan: String = "free",

    @get:PropertyName("premium")
    @set:PropertyName("premium")
    var premium: Boolean = false,

    @get:PropertyName("isAdmin")
    @set:PropertyName("isAdmin")
    var isAdmin: Boolean = false,

    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Long = System.currentTimeMillis(),

    @get:PropertyName("lastLoginAt")
    @set:PropertyName("lastLoginAt")
    var lastLoginAt: Long = System.currentTimeMillis()
) {
    val isAdministrator: Boolean
        get() = role.equals(UserRole.ADMIN, ignoreCase = true) ||
                role.equals(UserRole.OWNER, ignoreCase = true) ||
                isAdmin ||
                email.equals("sabhiron5@gmail.com", ignoreCase = true)

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): UserProfile {
            val email = doc.getString("email") ?: ""
            val rawRole = doc.getString("role") ?: doc.getString("userRole") ?: UserRole.USER
            val isAdminBoolean = doc.getBoolean("isAdmin") ?: false
            val isOwnerAccount = email.equals("sabhiron5@gmail.com", ignoreCase = true)

            val resolvedRole = when {
                isOwnerAccount -> UserRole.OWNER
                rawRole.equals(UserRole.OWNER, ignoreCase = true) -> UserRole.OWNER
                rawRole.equals(UserRole.ADMIN, ignoreCase = true) || isAdminBoolean -> UserRole.ADMIN
                rawRole.equals(UserRole.VIP, ignoreCase = true) -> UserRole.VIP
                else -> rawRole
            }

            val rawPlan = doc.getString("plan") ?: if (isOwnerAccount) "admin" else "free"
            val resolvedPlan = when {
                isOwnerAccount -> "admin"
                resolvedRole == UserRole.ADMIN || resolvedRole == UserRole.OWNER -> "admin"
                else -> rawPlan
            }

            val isPremium = doc.getBoolean("premium") ?: (resolvedRole != UserRole.USER || isOwnerAccount)

            return UserProfile(
                uid = doc.getString("uid") ?: doc.id,
                email = email,
                displayName = doc.getString("displayName") ?: doc.getString("name") ?: (if (email.contains("@")) email.substringBefore("@") else "User"),
                photoUrl = doc.getString("photoUrl") ?: doc.getString("avatarUrl") ?: doc.getString("profileImage") ?: "",
                role = resolvedRole,
                plan = resolvedPlan,
                premium = isPremium,
                isAdmin = resolvedRole == UserRole.ADMIN || resolvedRole == UserRole.OWNER || isAdminBoolean || isOwnerAccount,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                lastLoginAt = doc.getLong("lastLoginAt") ?: System.currentTimeMillis()
            )
        }
    }
}
