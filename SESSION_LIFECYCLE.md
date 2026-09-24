# Session Lifecycle & Volatile State Governance

**Document:** Administrative Elevation Session Lifecycle  
**Phase:** 2.1 Specification & Operational Model

---

## 1. Lifecycle States

```
 ┌───────────────┐
 │   UNLOCKED    │ ◄── Initial State on Launch / Process Start
 │ (isElevated=F)│
 └───────┬───────┘
         │
         │ Authenticated Admin requests elevation
         │ via AdminSessionManager.requestElevation(uid)
         ▼
 ┌───────────────┐
 │   ELEVATED    │
 │ (isElevated=T)│ ──► [Timer: 15 Minutes TTL Started]
 └───────┬───────┘
         │
         ├─── (TTL Expires: 900,000 ms) ────────────┐
         ├─── (User Logs Out: currentUser == null) ──┼──► [endSession()] ──► Back to UNLOCKED
         ├─── (Role Revoked: isAdmin == false) ─────┤
         └─── (App Process Terminated) ─────────────┘
```

---

## 2. Invalidation Vectors & Behaviors

| Trigger Event | Handling Mechanism | Outcome | Memory / Disk Impact |
|---|---|---|---|
| **App Cold Start / Process Death** | No persistent storage used for elevation | Session starts as `isElevated = false` | Zero disk footprints. No stale sessions. |
| **TTL Expiry (15 minutes)** | Kotlin Coroutine delay timer cancels and fires `endSession()` | `_isElevated.value = false`, `_sessionInfo` reset | UI automatically recomposes to hide menus. |
| **User Sign-Out** | Coroutine observer on `authManager.firebaseUser` detects `null` | Immediate call to `endSession()` | Prior elevation cannot carry over to another user. |
| **Admin Role Downgrade** | Coroutine observer on `authManager.isAdmin` detects `false` | Immediate call to `endSession()` | Elevation terminated in real-time. |
| **Manual End Session** | Calling `adminSessionManager.endSession()` | Session immediately marked inactive | Instant return to standard user mode. |

---

## 3. Comparison: Phase 1 vs Phase 2.1

| Property | Phase 1 (Persistent Flag) | Phase 2.1 (Ephemeral Elevation) |
|---|---|---|
| **Storage Location** | `SharedPreferences` (`admin_developer_menu_unlocked`) | RAM only (`StateFlow` in `AdminSessionManager`) |
| **Lifetime** | Indefinite (survived reboots and app updates) | 15 minutes TTL |
| **Session Reset on Sign Out** | No (remained set on device) | Yes (instant revocation) |
| **Secret Verification** | Hardcoded client strings (`MAXSTREAM777`) | Remote Authorization Provider Contract |
| **Process Death Resilience** | Persisted across app restarts | Completely cleared on process kill |
