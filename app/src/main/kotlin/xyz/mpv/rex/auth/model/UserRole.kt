package xyz.mpv.rex.auth.model

/**
 * Standardized Role Constants & Verification Helpers for Max Stream RBAC.
 */
object UserRole {
    const val USER = "user"
    const val ADMIN = "admin"
    const val MODERATOR = "moderator"
    const val DEVELOPER = "developer"
    const val SUPER_ADMIN = "super_admin"

    /**
     * Evaluates if a given role string satisfies administrative privileges.
     */
    fun isAdmin(role: String?): Boolean {
        if (role == null) return false
        val normalized = role.trim().lowercase()
        return normalized == ADMIN || normalized == SUPER_ADMIN || normalized == DEVELOPER
    }
}
