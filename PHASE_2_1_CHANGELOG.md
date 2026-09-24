# Phase 2.1 Changelog — Hardcoded Secrets Elimination & Volatile Session Elevation

**Date:** 2026-09-24  
**Component:** MAX STREAM Remote Authorization & Security Subsystem  
**Phase:** 2.1 (Security Hardening, Ephemeral Session Lifecycle, Remote Auth Abstraction)

---

## 1. Summary of Changes

Phase 2.1 eliminates all static hardcoded secrets, deprecates persistent client-side unlock flags, and introduces an in-memory, remote-authorized administrative elevation lifecycle with automatic TTL expiration.

---

## 2. Detailed Modifications

### A. Removal of Hardcoded Unlock Secrets
- **Deleted Codes:** Completely removed `MAXSTREAM777`, `ADMIN777`, and `MAXSTREAM2026`.
- **Source Verification:** Grepped codebase to confirm zero occurrences of admin secrets in `.kt`, XML resources, strings, manifests, and preferences.
- **Refactored UI:** `AboutScreen.kt` no longer compares entered text against local strings. Version tapping and dialog confirmation now delegate directly to the remote elevation layer (`AdminSessionManager.requestElevation`).

### B. Ephemeral In-Memory Session Architecture
- **No Persistent State:** Administrative elevation status is no longer written to `AdvancedPreferences` or disk.
- **Volatile State:** Governed by `AdminSessionManager` via Kotlin `StateFlow<Boolean>` and `StateFlow<AdminSessionInfo>`.
- **Automatic Expiration (TTL):** Elevation grants are bounded by a 15-minute TTL (`durationMs = 900_000ms`), automatically canceling elevation on timeout.
- **Sign-Out / Downgrade Invalidation:** Elevation state immediately terminates if the user signs out (`firebaseUser == null`) or loses admin role status (`isAdmin == false`).
- **Restart Reset:** When the application process terminates or restarts, elevation state drops to `false` automatically.

### C. Remote Authorization Abstraction Layer
- Added `xyz.mpv.rex.auth.elevation.AdminElevationContracts.kt`:
  - `AdminAuthorizationProvider`: Remote challenge/response contract.
  - `ElevationTokenValidator`: Cryptographic token validation contract.
  - `AdminSessionManager`: In-memory lifecycle manager contract.
  - `ElevationResult`: Sealed class (`Granted`, `Denied`, `Error`).
  - `AdminSessionInfo`: Data model containing TTL timestamps, remaining time, and granting UID.
- Added `xyz.mpv.rex.auth.elevation.DefaultAdminSessionManager.kt`:
  - Standard implementation hooked into `AuthManager` reactive streams.
  - Mock remote provider ready for swap with Cloud Functions in Phase 2.2.
- Registered elevation components in Koin `DomainModule.kt`.

### D. Route Guard & UI Gating Audit
- **`PreferencesScreen.kt`:** "Admin: Extensions & Providers" and "Developer Options" only render when `isAdmin && isElevated` are both `true`.
- **`DeveloperOptionsScreen.kt`:** Screen `Content()` enforces strict guard check: returns access-restricted screen unless `isAdmin && isElevated`.
- **`ExtensionPreferencesScreenRoute.kt`:** Screen `Content()` enforces strict guard check: returns access-restricted screen unless `isAdmin && isElevated`.

---

## 3. Preserved Architecture & Invariants
- REX Player playback engine & MPV integration untouched.
- CloudStream runtime compatibility, `PluginManager`, `RepositoryManager`, and `APIHolder` untouched.
- `ServerProviderSyncService` startup sync logic intact.
- Compilation verified clean.
