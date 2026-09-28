package xyz.mpv.rex.auth.model

/**
 * Standardized Role Constants & Verification Helpers for Max Stream RBAC.
 * Hierarchy: Owner > Admin > Moderator > User
 */
object UserRole {
    const val OWNER = "owner"
    const val ADMIN = "admin"
    const val MODERATOR = "moderator"
    const val USER = "user"
    const val DEVELOPER = "developer"
    const val SUPER_ADMIN = "super_admin"

    /**
     * Evaluates if a given role string satisfies owner privileges.
     */
    fun isOwner(role: String?): Boolean {
        if (role == null) return false
        val normalized = role.trim().lowercase()
        return normalized == OWNER || normalized == SUPER_ADMIN
    }

    /**
     * Evaluates if a given role string satisfies administrative privileges.
     */
    fun isAdmin(role: String?): Boolean {
        if (role == null) return false
        val normalized = role.trim().lowercase()
        return normalized == OWNER || normalized == ADMIN || normalized == SUPER_ADMIN || normalized == DEVELOPER
    }

    /**
     * Evaluates if a given role string satisfies moderator privileges.
     */
    fun isModerator(role: String?): Boolean {
        if (role == null) return false
        val normalized = role.trim().lowercase()
        return isOwner(role) || isAdmin(role) || normalized == MODERATOR
    }

    /**
     * Owner only can create and delete admins.
     */
    fun canManageAdmins(role: String?): Boolean = isOwner(role)

    /**
     * Owner and Admin can manage subscription plans.
     */
    fun canManagePlans(role: String?): Boolean = isAdmin(role)

    /**
     * Owner and Admin can manage extensions fleet-wide.
     */
    fun canManageExtensions(role: String?): Boolean = isAdmin(role)

    /**
     * Owner and Admin can manage repositories metadata.
     */
    fun canManageRepositories(role: String?): Boolean = isAdmin(role)

    /**
     * Owner, Admin, and Moderator can assign plans to users.
     */
    fun canAssignPlans(role: String?): Boolean = isModerator(role)

    /**
     * Owner, Admin, and Moderator can access the Admin Dashboard.
     */
    fun canAccessAdminDashboard(role: String?): Boolean = isModerator(role)

    /**
     * Determines whether an actor role can modify a target user's role.
     */
    fun canModifyRole(actorRole: String?, targetRole: String?): Boolean {
        if (!isAdmin(actorRole)) return false
        if (isOwner(actorRole)) return true
        // Admin cannot modify an Owner
        val normTarget = targetRole?.trim()?.lowercase() ?: USER
        return normTarget != OWNER && normTarget != SUPER_ADMIN
    }
}
