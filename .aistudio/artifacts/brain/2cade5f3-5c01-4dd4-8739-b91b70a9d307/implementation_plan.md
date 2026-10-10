# Architectural Plan: Purge Hardcoded Providers & Plan-Based Extension Sync

This revised plan incorporates your feedback:
1. **Preserve Preset Repositories**: Keep default preset extension repositories in `RepositoryManager.kt` intact so users can browse repository catalogs.
2. **Remove Hardcoded Provider Fallbacks**: Completely remove `CastleTV` fallbacks, default CastleTV user profile references, and built-in provider fallbacks.
3. **Strict Plan-Based Extension Activation**: Fix unpermissioned bulk auto-installations on app launch/login by restricting active extension installations strictly to extensions permitted by the user's Firestore subscription plan.

---

## Proposed Changes

### Task 1: Complete Removal of Hardcoded Providers & Fallbacks

#### 1. `ServerProviderSyncService.kt`
- Delete `ensureManagedCastleTvFallback()`, `CASTLE_TV_ID`, `CASTLE_TV_NAME`, and `CastleTV.cs3` manifest logic.
- Remove automatic silent background auto-installation of all catalog plugins on app startup.
- Retain repository query capability without forced auto-installs.

#### 2. `FirebaseProviderSyncService.kt`
- Remove `"castletv"` and `"streamwish"` hardcoded provider IDs from fallback entitlement lists.

#### 3. `UserProfile.kt`
- Update `providerAccess` default from `listOf("castletv")` to `emptyList()`.
- Update `installedProviders` default from `mapOf("castletv" to 14L)` to `emptyMap()`.

#### 4. `RepositoryManager.kt`
- **Keep Preset Repositories Intact**: Maintain preset community extension repositories (`BUILT_IN_PRESETS`) so users can view available plugins in the Extension Manager without forced background installation.

---

### Task 2: Fix Unpermissioned Auto-Installation on First Launch / Login

#### 1. `FirebaseProviderModels.kt`
- Update `PlanConfig.defaultForPlan("free")` so `allowAllExtensions = false` and `allowedExtensions = emptyList()` (or configured explicitly via Firestore).
- Ensure default plans (`free`, `premium`, `vip`) do NOT default to `allowAllExtensions = true` unless specified in Firestore or for `admin`/`owner` roles.

#### 2. `FirebaseProviderSyncService.kt`
- Enforce strict plan entitlement checks: extensions are installed/activated **ONLY** if they match the user's active plan `allowedExtensions` or `customExtensions` in Firestore.
- Disallowed extensions are not auto-installed on login or app launch.

---

## Verification Plan

### Automated Build
- Run `compile_applet` to verify clean compilation.

### Behavior Verification
1. **No CastleTV Fallbacks**: Verify that no hardcoded CastleTV or dummy providers are injected into `APIHolder`.
2. **Preset Repositories Available**: Confirm preset repositories remain accessible in the Extension Manager UI.
3. **Plan-Gated Extensions**: Verify that launching the app or logging in on a Free plan does not auto-install unpermitted extensions.
