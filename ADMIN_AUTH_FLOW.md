# Remote Admin Authorization Architecture Flow

**Architecture Version:** Phase 2.1 Specification  
**Component:** MAX STREAM Remote Authorization & Ephemeral Session Governance

---

## 1. Overview & Security Model

In Phase 2.1, local unlock codes (`MAXSTREAM777`, etc.) have been completely replaced with a dual-condition remote authorization model. 

Access to administrative capabilities requires:
1. **Authoritative Role (RBAC):** `authManager.isAdmin.value == true` (validated against backend claims/Firestore user profile).
2. **Active Elevation Session (Ephemeral):** `adminSessionManager.isElevated.value == true` (active in-memory session within valid TTL).

Standard users cannot elevate even if they trigger UI gestures or craft deep links, because the backend and authorization layer reject requests from accounts lacking `UserRole.ADMIN`.

---

## 2. Elevation Request Flow

```
[User on AboutScreen]
        │
        ▼ (7-tap gesture / Elevation Action)
[AboutScreen Composable]
        │
        ├─► Check: authManager.isAdmin?
        │       ├─► False: Toast "Admin authorization required" -> STOP
        │       └─► True: Prompt "Request Administrative Elevation" Dialog
        │
        ▼ (User clicks "Authorize Elevation")
[AdminSessionManager.requestElevation(uid)]
        │
        ├─► Check: authManager.isAdmin == true? (Double check)
        │
        ▼
[AdminAuthorizationProvider.requestElevation(uid, challenge)]
        │
        ├─► In Phase 2.1: Validates authenticated UID against current Firebase user session
        ├─► Future Phase 2.2+: HTTPS Callable Cloud Function with 2FA / Passkey challenge
        │
        ▼
   [Server Grant]
   - Token: elev_<UUID>
   - DurationMs: 900,000 (15 minutes)
   - ExpirationEpochMs: currentTime + 900,000
        │
        ▼
[ElevationTokenValidator.validateToken(token, uid)]
        │
        ▼ (Valid)
[AdminSessionManager.applyElevation]
        ├─► _sessionInfo.value = AdminSessionInfo(isActive = true, ...)
        ├─► _isElevated.value = true
        ├─► Schedule Coroutine delay(remainingMs) -> Auto-Revocation
        └─► UI notifies: "Administrative Elevation Active (15 min TTL)"
```

---

## 3. Revocation & Termination Triggers

Administrative elevation is strictly volatile. The session terminates immediately under any of the following events:

1. **TTL Expiration:** Scheduled coroutine terminates session when 15 minutes elapse.
2. **User Sign-Out:** `authManager.firebaseUser` emits `null`. Observer in `AdminSessionManager` invokes `endSession()`.
3. **Role Downgrade:** `authManager.isAdmin` emits `false`. Observer in `AdminSessionManager` invokes `endSession()`.
4. **App Process Termination / Restart:** Process death discards memory. On next launch, `_isElevated` defaults to `false`.
5. **Manual Revocation:** Admin clicks "End Session" or changes profile.

---

## 4. Class Abstraction & Contracts

```kotlin
interface AdminAuthorizationProvider {
    suspend fun requestElevation(uid: String, challengeResponse: String? = null): ElevationResult
    suspend fun revokeElevation(uid: String, token: String): Boolean
}

interface ElevationTokenValidator {
    suspend fun validateToken(token: String, uid: String): Boolean
}

interface AdminSessionManager {
    val isElevated: StateFlow<Boolean>
    val sessionInfo: StateFlow<AdminSessionInfo>
    suspend fun requestElevation(uid: String, challenge: String? = null): ElevationResult
    fun endSession()
    fun checkSessionValidity(): Boolean
}
```
