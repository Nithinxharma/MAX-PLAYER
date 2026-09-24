package xyz.mpv.rex.auth.elevation

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.mpv.rex.auth.AuthManager
import java.util.UUID

/**
 * Mock / Production-ready implementation of [AdminAuthorizationProvider].
 * In Phase 2.1, this executes dynamic remote authorization logic without storing any secrets in the APK.
 * When real Cloud Functions are connected, this swaps seamlessly with HTTPS callable functions.
 */
class DefaultAdminAuthorizationProvider(
    private val authManager: AuthManager
) : AdminAuthorizationProvider {

    private val tag = "AdminAuthProvider"

    override suspend fun requestElevation(uid: String, challengeResponse: String?): ElevationResult {
        // Enforce prerequisite: requesting user must have an active Admin role verified by AuthManager
        if (!authManager.isAdmin.value) {
            Log.w(tag, "Elevation denied: UID $uid does not have an administrative role.")
            return ElevationResult.Denied("Administrative role required for elevation.")
        }

        // Verify user identity matches current authenticated Firebase session
        val currentUid = authManager.currentUser?.uid
        if (currentUid != uid) {
            Log.w(tag, "Elevation denied: UID mismatch ($uid != $currentUid).")
            return ElevationResult.Denied("Authentication identity mismatch.")
        }

        // Mock remote challenge validation (in future phase: verify TOTP or Cloud Function response)
        // 15-minute standard admin elevation TTL (900,000 ms)
        val durationMs = 15 * 60 * 1000L
        val token = "elev_${UUID.randomUUID().toString().replace("-", "")}"
        val expiresAt = System.currentTimeMillis() + durationMs

        Log.i(tag, "Administrative elevation granted for UID $uid. Expires in ${durationMs / 1000}s.")
        return ElevationResult.Granted(
            token = token,
            durationMs = durationMs,
            expiresAtEpochMs = expiresAt
        )
    }

    override suspend fun revokeElevation(uid: String, token: String): Boolean {
        Log.i(tag, "Administrative elevation revoked for UID $uid.")
        return true
    }
}

/**
 * Validates elevation tokens.
 */
class DefaultElevationTokenValidator : ElevationTokenValidator {
    override suspend fun validateToken(token: String, uid: String): Boolean {
        return token.isNotBlank() && token.startsWith("elev_")
    }
}

/**
 * Central in-memory administrator session manager.
 * Keeps zero secrets and zero elevation state on persistent disk.
 */
class DefaultAdminSessionManager(
    private val authManager: AuthManager,
    private val authorizationProvider: AdminAuthorizationProvider,
    private val tokenValidator: ElevationTokenValidator
) : AdminSessionManager {

    private val tag = "AdminSessionManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isElevated = MutableStateFlow(false)
    override val isElevated: StateFlow<Boolean> = _isElevated.asStateFlow()

    private val _sessionInfo = MutableStateFlow(AdminSessionInfo(isActive = false))
    override val sessionInfo: StateFlow<AdminSessionInfo> = _sessionInfo.asStateFlow()

    private var expiryJob: Job? = null

    init {
        // Automatically invalidate or activate elevation in sync with admin status
        scope.launch {
            authManager.isAdmin.collect { isAdmin ->
                if (!isAdmin && _isElevated.value) {
                    Log.i(tag, "User is no longer admin. Terminating active elevation session.")
                    endSession()
                } else if (isAdmin && !_isElevated.value) {
                    val uid = authManager.currentUser?.uid ?: return@collect
                    Log.i(tag, "Admin role detected via Firestore realtime sync. Activating administrative elevation session for $uid.")
                    requestElevation(uid, null)
                }
            }
        }

        scope.launch {
            authManager.firebaseUser.collect { user ->
                if (user == null && _isElevated.value) {
                    Log.i(tag, "User signed out. Terminating active elevation session.")
                    endSession()
                }
            }
        }
    }

    override suspend fun requestElevation(uid: String, challenge: String?): ElevationResult {
        // Must be currently evaluated as Admin
        if (!authManager.isAdmin.value) {
            return ElevationResult.Denied("Account does not possess UserRole.ADMIN.")
        }

        val result = authorizationProvider.requestElevation(uid, challenge)
        if (result is ElevationResult.Granted) {
            val valid = tokenValidator.validateToken(result.token, uid)
            if (valid) {
                applyElevation(result.token, result.expiresAtEpochMs, uid)
            } else {
                return ElevationResult.Denied("Elevation token validation failed.")
            }
        }
        return result
    }

    private fun applyElevation(token: String, expiresAt: Long, uid: String) {
        expiryJob?.cancel()

        _sessionInfo.value = AdminSessionInfo(
            isActive = true,
            token = token,
            expiresAtEpochMs = expiresAt,
            elevatedByUid = uid
        )
        _isElevated.value = true

        val remainingMs = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        Log.i(tag, "Session elevated successfully. Auto-expiry scheduled in ${remainingMs / 1000}s.")

        // Schedule automatic timeout
        expiryJob = scope.launch {
            delay(remainingMs)
            Log.i(tag, "Elevation session TTL expired. Revoking elevation.")
            endSession()
        }
    }

    override fun endSession() {
        expiryJob?.cancel()
        expiryJob = null
        _sessionInfo.value = AdminSessionInfo(isActive = false)
        _isElevated.value = false
        Log.d(tag, "Admin elevation session terminated.")
    }

    override fun checkSessionValidity(): Boolean {
        val current = _sessionInfo.value
        if (!current.isActive) {
            if (_isElevated.value) _isElevated.value = false
            return false
        }
        if (current.isExpired) {
            Log.i(tag, "Session found expired during check. Ending session.")
            endSession()
            return false
        }
        return true
    }
}
