# MAX STREAM — VALIDATION & QUALITY ASSURANCE TEST PLAN (PHASE 2)

**Document Version:** 2.0  
**Test Suite Type:** Functional, Security, Regression, Offline Resilience & Telemetry  
**Platforms:** Android (ARM64, x86_64), Android TV, Leanback  

---

## 1. Test Matrix Overview

| Scenario | Objective | Expected Outcome | Pass/Fail Criteria |
|---|---|---|---|
| **TC-01: Fresh Install** | Install APK on clean device without prior data | App launches to Welcome / Home screen; CastleTV loads automatically; no admin UI visible | No crashes, default OTT interface active |
| **TC-02: Upgrade Install** | Update older Max Stream build to Phase 2 | Migrates local database, preserves recents/favorites, updates manifest silently | User data intact, providers synchronized |
| **TC-03: Offline Startup** | Launch app in Airplane mode with no network | Fallback providers load from assets; player launches local or cached media without ANR | No freeze, graceful offline warning |
| **TC-04: Failed Manifest Sync** | HTTP 500 / Network timeout during sync | System logs telemetry, maintains existing active providers without deletion | Playback remains uninterrupted |
| **TC-05: Revoked Provider** | Manifest marks active provider as `REVOKED` | Provider disappears from `APIHolder`; local DEX file deleted from internal storage | No lingering references, search clean |
| **TC-06: Standard User Protection** | Regular user navigates Settings & About | No Extensions, Repositories, or Developer Options visible anywhere | 100% hidden from standard users |
| **TC-07: Admin Dual-Key Unlock** | Authenticated admin taps version 7 times | Admin Developer Menu and Extensions appear dynamically in Settings | Unlocks only for verified admin account |
| **TC-08: Route Protection Bypass** | Attempt to open `ExtensionPreferences` via deep link or backstack manipulation | Route guard intercepts and displays security restriction screen | Direct access blocked |
| **TC-09: Force Provider Update** | Manifest specifies higher version with `forceUpdate: true` | Outdated provider immediately replaced before new media query executes | New version active in registry |
| **TC-10: Playback Verification** | Select 4K stream with subtitles and MPV engine | Stream resolves through bridge; MPV renders video smoothly with audio sync | Seamless playback, zero ANR |

---

## 2. Step-by-Step Execution Protocols

### TC-01: Fresh Install Verification
1. Uninstall existing application: `adb uninstall xyz.mpv.rex`
2. Install new build: `adb install -r maxstream-release.apk`
3. Launch app and complete authentication (or proceed as Guest).
4. Verify Home screen displays TMDB/Max Stream curated rows.
5. Inspect `Settings`: confirm **no** "Extensions", "Repositories", or "Developer Options" appear.
6. Trigger search for popular title: confirm media cards appear with generic "STREAM" or "4K UHD" labels instead of raw package IDs.

---

### TC-03: Offline Startup Protocol
1. Enable Airplane mode on device (disable Wi-Fi and Mobile Data).
2. Cold boot Max Stream.
3. Observe startup behavior in logcat (`adb logcat -s ServerProviderSync:D`):
   - Confirm `ServerProviderSyncService` catches network exception gracefully.
   - Confirm CastleTV fallback initialization triggers.
4. Navigate to local videos / Recents: ensure playback functions without waiting for network timeout.

---

### TC-05: Provider Revocation Protocol
1. Verify provider `test_provider` is loaded in `APIHolder`.
2. Push updated mock manifest with `"status": "REVOKED"` for `test_provider`.
3. Trigger sync (`serverProviderSyncService.syncProviders(force = true)`).
4. Inspect `APIHolder.allProviders`: confirm `test_provider` is removed.
5. Inspect `/data/user/0/xyz.mpv.rex/files/plugins/`: confirm binary file is deleted.
6. Execute search: confirm no results or errors reference `test_provider`.

---

### TC-07: Remote Admin Authorization & Unlock Protocol
1. Log in with user account possessing `UserRole.USER`:
   - Open `Settings` > `About`.
   - Tap version 7 times: toast displays `"Admin authorization required"`. Menu remains locked.
2. Log in with validated `UserRole.ADMIN`:
   - Open `Settings` > `About`.
   - Tap version: countdown prompts appear ("tap 6 more times...").
   - On 7th tap: toast displays `"Admin Developer Menu Unlocked!"`.
   - Return to `Settings`: confirm "Admin: Extensions & Providers" and "Developer Options" are now visible.

---

### TC-08: Direct Route Deep-Link Guard
1. Send explicit intent targeting Developer Options or Extensions route:
   `adb shell am start -n xyz.mpv.rex/.MainActivity --es "route" "developer_options"`
2. Verify screen displays:
   `"Access Restricted: Administrator privileges and security unlock required."`
3. Backstack remains functional and allows clean back navigation to the home screen.

---

### TC-10: Playback Verification with REX Bridge & MPV
1. Execute search for "Big Buck Bunny" or standard OTT content.
2. Select item to open detail view.
3. Tap "Play":
   - Confirm extractor link resolution executes asynchronously without blocking UI.
   - Confirm `RexPlayerBridge` launches `PlayerActivity`.
   - Verify hardware acceleration, aspect ratio controls, subtitle tracks, and audio passthrough function as expected.
4. Close player and verify watch progress is recorded in Recents.

---

## 3. Automation & Regression Strategy

- **Unit & Local Robolectric Tests:** Run `gradle :app:testDebugUnitTest` to verify manifest parsing, model serialization, and role validation rules.
- **Continuous Integration (CI):** Run automated compilation (`compile_applet`) and static lint checks on every pull request.
- **Nightly Smoke Test:** Automated script tests fresh install, manifest sync, and player bridge launch on simulated target devices.
