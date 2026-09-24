# MAX STREAM — AUTHENTICATION & RBAC ARCHITECTURE (PHASE 2)

**Document Version:** 2.0  
**Status:** Architecture Specification & Readiness Evaluation  
**System Components:** Google Identity Services, Firebase Auth, Cloud Firestore, Custom Claims, Token Exchange  

---

## 1. Current State vs Target State

| Dimension | Current Implementation | Target Phase 2 Architecture |
|---|---|---|
| **Primary Identity** | Firebase Auth + Google Sign-In Client (legacy GMS) | Firebase Auth + Credential Manager / Modern Google Identity |
| **Email Login** | Basic Email/Password with state flow | Firebase Email + Password / Passwordless Magic Link |
| **Guest Mode** | Anonymous Firebase User or local guest stub | Ephemeral Guest Session with limited feature scope |
| **Role Management** | Firestore document field (`users/{uid}/role`) | Server-issued Firebase Custom Claims (`role: ADMIN \| USER \| VIP`) |
| **Admin Authorization** | Local 7-tap counter + hardcoded access strings | Dynamic Server Verification + Time-Limited Administrative Token |
| **Session Lifecycle** | Persistent until explicit sign-out | Active session monitoring, periodic token refresh, auto-timeout for admin |

---

## 2. Authentication Flow Architecture

```
   ┌────────────────────────────────────────────────────────┐
   │                   Max Stream Client                    │
   └──────────────────────────┬─────────────────────────────┘
                              │ 1. User Authenticates (Google / Email)
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │                  Firebase Auth SDK                     │
   └──────────────────────────┬─────────────────────────────┘
                              │ 2. Validates credentials, issues ID Token
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │              Max Stream Cloud Function                 │
   │               (Auth Hook / Token Mint)                 │
   └──────────────────────────┬─────────────────────────────┘
                              │ 3. Attaches Custom Claims:
                              │    { role: "ADMIN", tier: "VIP", exp: ... }
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │               Client AuthManager State                 │
   │    _userRole: StateFlow<UserRole>                      │
   │    _isAdmin: StateFlow<Boolean>                        │
   │    _sessionExpiry: StateFlow<Long>                     │
   └──────────────────────────┬─────────────────────────────┘
                              │ 4. Authorizes Remote Sync & Settings Access
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │               Protected App Subsystems                 │
   │    - ServerProviderSyncService (Manifest Access)       │
   │    - Admin Developer Console                           │
   │    - Provider Diagnostics & Repository Controls        │
   └────────────────────────────────────────────────────────┘
```

---

## 3. Remote Admin Authorization (Replacing Local Codes)

### 3.1 The Vulnerability to Eliminate
Currently, `AboutScreen.kt` accepts strings like `MAXSTREAM777` or requires 7 version taps that permanently write `adminDeveloperMenuUnlocked = true` to local preferences.

### 3.2 Target Remote Authorization Mechanism
1. **Challenge-Response / Time-Limited Admin Token:**
   - To unlock administrative features, the user initiates the unlock request from `AboutScreen` or `PreferencesScreen`.
   - The client calls a secured Cloud Function endpoint: `requestAdminElevation(uid, verificationFactor)`.
   - The backend validates:
     - Is this user account pre-registered in the secure administrative registry?
     - Is Multi-Factor Authentication (MFA) satisfied?
   - If verified, the server sets a short-lived Custom Claim or issues a signed admin session ticket with a **15–60 minute Time-To-Live (TTL)**.
2. **In-Memory Elevation State:**
   - Client stores `adminSessionToken` and `adminSessionExpiresAt` in volatile memory only.
   - When the TTL expires, the Admin Developer Menu automatically locks and dismisses any active admin screens, returning the user to the standard OTT interface.
3. **No Secrets in APK:**
   - No hardcoded access codes, master passwords, or backdoor phrases exist in the compiled codebase.

---

## 4. Firestore User Profile & RBAC Schema

### Collection: `users/{userId}`
```json
{
  "uid": "USER_FIREBASE_UID",
  "email": "user@domain.com",
  "displayName": "Alex Streamer",
  "photoUrl": "https://lh3.googleusercontent.com/...",
  "role": "USER",
  "subscriptionTier": "PREMIUM",
  "createdAt": 1727140000000,
  "lastActiveAt": 1727145000000,
  "clientMetadata": {
    "appVersion": "1.0.0",
    "deviceModel": "Pixel 8 Pro",
    "androidSdk": 35
  }
}
```

### Collection: `admin_audit_logs/{logId}`
```json
{
  "timestamp": 1727145200000,
  "adminUid": "ADMIN_FIREBASE_UID",
  "action": "ELEVATION_GRANTED",
  "ipAddress": "192.0.2.1",
  "durationMinutes": 30
}
```

### Firestore Security Rules Readiness:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read: if request.auth != null && (request.auth.uid == userId || request.auth.token.role == 'ADMIN');
      // Users cannot elevate their own role
      allow update: if request.auth != null && request.auth.uid == userId 
                    && !request.resource.data.diff(resource.data).affectedKeys().hasAny(['role', 'subscriptionTier']);
      allow create: if request.auth != null && request.auth.uid == userId;
    }
    match /admin_audit_logs/{logId} {
      allow read, write: if request.auth != null && request.auth.token.role == 'ADMIN';
    }
  }
}
```

---

## 5. Migration & Integration Strategy

1. **Keep `AuthManager` Interface Intact:**
   - Existing UI screens consume `koinInject<AuthManager>()` and observe `isAdmin`, `userProfile`, and `authState`.
   - The signature and reactive flows of `AuthManager` remain identical to prevent breaking dependent composables.
2. **Seamless Backend Token Refresh:**
   - In `FirebaseAuthManager`, attach an ID token change listener (`auth.addIdTokenListener`).
   - When custom claims update or token refreshes, parse `tokenResult.claims["role"]` to update `_isAdmin` reactively.
3. **Graceful Offline Degradation:**
   - If offline, the client uses cached tokens for regular playback, but administrative features strictly require online server re-validation.
