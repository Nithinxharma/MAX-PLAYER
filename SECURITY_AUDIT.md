# MAX STREAM — SECURITY AUDIT REPORT (PHASE 2)

**Audit Date:** 2026-09-24  
**Scope:** Complete Provider Lifecycle, Role-Based Access Control (RBAC), Authentication Guards, Manifest Verification, Dynamic Code Execution (DEX), Deep-Links, and Local Preferences.  
**Classification:** Confidential / System Architecture Audit  

---

## 1. Executive Summary

Max Stream has evolved from an open, community-style CloudStream client to an enterprise OTT streaming application. While the foundation includes robust playback (REX Player / MPV), reactive UI gating, and silent startup provider synchronization, the security audit reveals several critical vectors that must be hardened in Phase 2:

1. **Hardcoded Administrative Secrets:** Local unlock fallback strings (`MAXSTREAM777`, `ADMIN777`, `MAXSTREAM2026`) in client-side Compose code.
2. **Client-Side Admin Gating & In-Memory Role State:** Gating relies on local Kotlin flows (`isAdmin`) backed by client-side Firestore document reads without server-enforced custom claims or token verification.
3. **Unsigned Dynamic Code Execution:** Remote provider DEX files are downloaded and executed without public-key signature verification, creating man-in-the-middle (MITM) and manifest hijacking risks.
4. **Local Preference Tampering:** Flags such as `adminDeveloperMenuUnlocked` are persisted in standard unencrypted SharedPreferences, which can be modified on rooted devices or via backup exploits.
5. **Deep-Link / Intent Filter Surface:** Generic video/audio intent filters in `PlayerActivity` and direct composable instantiation if navigation graphs are bypassed.

---

## 2. Detailed Findings & Vulnerability Breakdown

### 2.1 Hardcoded Admin Access Codes (CRITICAL)
- **Location:** `app/src/main/kotlin/xyz/mpv/rex/ui/preferences/AboutScreen.kt` (lines 538–545)
- **Vulnerability:**
  ```kotlin
  val trimmed = enteredAdminCode.trim()
  if (trimmed == "MAXSTREAM777" || trimmed == "ADMIN777" || trimmed == "MAXSTREAM2026") {
      advancedPreferences.adminDeveloperMenuUnlocked.set(true)
      ...
  }
  ```
- **Risk Analysis:** Anyone de-compiling the APK (`jadx`, `apktool`) or running `strings` on the classes.dex can extract these codes in seconds. Although combined with an `isAdmin` check, having hardcoded strings in client code violates security best practices and provides an unauthorized attack vector if role checks are compromised.
- **Remediation:** Remove all hardcoded code strings completely. Replace with backend challenge-response or Firebase Custom Claims verification.

---

### 2.2 In-Memory Role-Based Access Control & Firestore Rule Vulnerability (HIGH)
- **Location:** `app/src/main/kotlin/xyz/mpv/rex/auth/FirebaseAuthManager.kt`
- **Vulnerability:**
  - `UserRole.ADMIN` is fetched directly from the Firestore collection `users/{uid}` via client SDK (`userDoc.getString("role")`).
  - If Firestore Security Rules permit write access to the `role` field by the user or are misconfigured (`allow write: if request.auth != null`), a standard user can alter their own document and elevate to `ADMIN`.
- **Risk Analysis:** Client-controlled role escalation.
- **Remediation:**
  1. Migrate role authorization strictly to **Firebase Auth Custom Claims** (`request.auth.token.role == 'ADMIN'`).
  2. Maintain strict Firestore security rules (`allow write: if false` on administrative fields).
  3. Validate administrative sessions with time-bounded, server-signed JWT tokens.

---

### 2.3 Unsigned Dynamic DEX Loading & Manifest Tampering (CRITICAL)
- **Location:** `app/src/main/kotlin/xyz/mpv/rex/cinehub/provider/server/ServerProviderSyncService.kt` and `xyz.mpv.rex.cinehub.extension.manager.ExtensionManager.kt`
- **Vulnerability:**
  - Providers are distributed as `.cs3` files (ZIP containing `classes.dex` and `manifest.json`).
  - Currently, `ServerProviderSyncService` pulls manifests from external GitHub/raw URLs or hardcoded URLs without cryptographic signature validation (e.g., Ed25519 or RSA-SHA256).
  - SHA-256 hash checks may prevent corrupted downloads but do not verify the *publisher's identity*. If a CDN, DNS, or manifest endpoint is poisoned, malicious DEX code could be executed within the app process (`DexClassLoader`).
- **Risk Analysis:** Remote Code Execution (RCE) on the client device.
- **Remediation:**
  1. Every remote provider manifest and DEX package must be signed by the Max Stream Root Certificate Authority / Release Private Key.
  2. The application must store the trusted public key in a secure native module and verify the manifest signature **before** parsing or invoking `DexClassLoader`.

---

### 2.4 Route Protection & Screen Composition Bypass (MEDIUM)
- **Location:** `DeveloperOptionsScreen.kt` and `ExtensionPreferencesScreenRoute.kt`
- **Vulnerability:**
  - While both routes now check `if (!isAdmin || !isDeveloperMenuUnlocked)`, navigation uses Compose Navigation / Voyager stack (`LocalBackStack.current`).
  - If a deep link or internal intent directly invokes an activity or modifies the backstack state via reflection or state restoration, the screen UI could theoretically be forced into the visual tree.
- **Remediation:** Centralize route authorization guards in a single `SecurityGuard` layer that evaluates authorization upon every stack push, clearing unauthorized destinations before composition.

---

### 2.5 Local Preference Tampering (LOW-MEDIUM)
- **Location:** `app/src/main/kotlin/xyz/mpv/rex/preferences/AdvancedPreferences.kt`
- **Vulnerability:**
  - `adminDeveloperMenuUnlocked` is stored as an unencrypted boolean in the app's default SharedPreference file.
  - On a rooted device, an attacker can modify the XML file directly to toggle the developer menu flag without tapping the version or supplying credentials.
- **Remediation:**
  - Do not persist unlock state permanently in local disk preferences.
  - Keep the administrative session in **volatile in-memory state** with an automatic expiration timeout (e.g., 15 minutes of inactivity).

---

### 2.6 Deep-Link Surface in `AndroidManifest.xml` (MEDIUM)
- **Location:** `app/src/main/AndroidManifest.xml` (`PlayerActivity` intent filters)
- **Vulnerability:**
  - `PlayerActivity` exposes `android.intent.action.VIEW` for extensive MIME types and schemes (`http`, `https`, `content`, `file`).
  - While necessary for a robust media player, untrusted external apps can fire intents to launch arbitrary streams or potentially trigger buffer overflows in native MPV if input URLs are not sanitized.
- **Remediation:**
  - Validate and sanitize all incoming URIs in `PlayerActivity` before forwarding to MPV or REX Player bridge.
  - Reject internal control schemes (e.g., `maxstream://admin`) from external intents unless signed by the same signature.

---

## 3. Summary Matrix & Action Items

| ID | Vulnerability | Severity | Target Remediation |
|---|---|---|---|
| SEC-01 | Hardcoded access strings in `AboutScreen` | Critical | Replace with server-side challenge / Firebase Custom Claims |
| SEC-02 | Unsigned provider DEX downloads | Critical | Implement Ed25519 signature verification on manifests and `.cs3` files |
| SEC-03 | Client-side role evaluation | High | Enforce Firebase Custom Claims (`request.auth.token.role`) |
| SEC-04 | Persistent unencrypted unlock flag | Medium | Transition to short-lived in-memory session token with TTL |
| SEC-05 | Client-side manifest URL endpoints | Medium | Route all manifest requests through authenticated Max Stream API Gateway |
| SEC-06 | External Intent injection | Low-Medium | Add rigorous URI sanitization to `PlayerActivity` |
