package xyz.mpv.rex.auth.elevation

import kotlinx.coroutines.flow.StateFlow

/**
 * Result representing the outcome of an administrative elevation request.
 */
sealed class ElevationResult {
    data class Granted(
        val token: String,
        val durationMs: Long,
        val expiresAtEpochMs: Long
    ) : ElevationResult()

    data class Denied(
        val reason: String
    ) : ElevationResult()

    data class Error(
        val throwable: Throwable
    ) : ElevationResult()
}

/**
 * Information regarding the active, in-memory administrative elevation session.
 */
data class AdminSessionInfo(
    val isActive: Boolean,
    val token: String? = null,
    val expiresAtEpochMs: Long = 0L,
    val elevatedByUid: String? = null
) {
    val remainingTimeMs: Long
        get() = (expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)

    val isExpired: Boolean
        get() = !isActive || System.currentTimeMillis() >= expiresAtEpochMs
}

/**
 * Contract for validating administrative elevation tokens against an authoritative source.
 */
interface ElevationTokenValidator {
    /**
     * Validates if the supplied token is cryptographically sound, belongs to the current user,
     * and has not expired.
     */
    suspend fun validateToken(token: String, uid: String): Boolean
}

/**
 * Contract for requesting administrative elevation from the backend authorization layer.
 * Note: No secrets or hardcoded passwords exist in client implementations.
 */
interface AdminAuthorizationProvider {
    /**
     * Sends an elevation challenge to the server for the current authenticated user.
     * @param uid The Firebase UID of the requesting administrator.
     * @param challengeResponse Optional dynamic challenge factor (e.g. dynamic TOTP / passkey assertion).
     */
    suspend fun requestElevation(uid: String, challengeResponse: String? = null): ElevationResult

    /**
     * Revokes elevation immediately on the backend if supported.
     */
    suspend fun revokeElevation(uid: String, token: String): Boolean
}

/**
 * Manages the in-memory, time-bounded administrative elevation lifecycle.
 *
 * Guarantees:
 * 1. Elevation is NEVER stored in persistent storage (e.g., SharedPreferences, Room, DataStore).
 * 2. Elevation automatically terminates on TTL expiry (default: 15 minutes).
 * 3. Elevation terminates immediately on user logout or token invalidation.
 * 4. Gated administrative UI and route guards observe [isElevated].
 */
interface AdminSessionManager {
    /**
     * Reactive stream indicating if the current session possesses active administrative elevation.
     */
    val isElevated: StateFlow<Boolean>

    /**
     * Observable details about the active session (expiry, remaining time, token).
     */
    val sessionInfo: StateFlow<AdminSessionInfo>

    /**
     * Requests elevation for the authenticated user.
     * Fails if user does not satisfy administrative RBAC or challenge verification.
     */
    suspend fun requestElevation(uid: String, challenge: String? = null): ElevationResult

    /**
     * Manually terminates the active administrative elevation session immediately.
     */
    fun endSession()

    /**
     * Verifies that the current session is still valid. If expired, clears in-memory state.
     */
    fun checkSessionValidity(): Boolean
}
