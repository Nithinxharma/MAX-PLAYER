# MAX STREAM — ADMIN CONTROL PANEL ARCHITECTURE (PHASE 2)

**Document Version:** 2.0  
**Status:** Backend & Operations Specification  
**Target Environment:** Cloud Run / Firebase Hosting / Node.js & Go Backend / Cloud Storage  

---

## 1. Overview & System Purpose

The **Max Stream Admin Control Panel** is the operational command center for Max Stream services. It enables site reliability engineers (SREs) and content operations teams to:
- Package, sign, and upload provider `.cs3` binaries.
- Publish and modify the canonical `manifest.v2.json`.
- Execute instant emergency provider revocations.
- Inspect real-time client telemetry and playback health.
- Manage elevated administrator users without shipping client-side secrets.

---

## 2. Dashboard Information Architecture

```
 Max Stream Admin Console
  ├── 1. Provider Management
  │    ├── Provider Catalog (Status, Versions, Active Installs)
  │    ├── Upload New Provider (.cs3 bundle + signature generation)
  │    ├── Release Channels (Nightly, Beta, Production)
  │    └── Emergency Kill-Switch (Instant Revocation)
  │
  ├── 2. Manifest Orchestrator
  │    ├── Live Manifest Inspector
  │    ├── Draft Changes & Staging Review
  │    ├── Force Update Toggles & MinAppVersion Enforcer
  │    └── CDN Cache Invalidation (Cloudflare / Cloud Storage CDN)
  │
  ├── 3. Health & Telemetry Analytics
  │    ├── Real-Time Provider Success Rate (%)
  │    ├── Extractor Failure Top List (Broken hosts/403s)
  │    ├── Sync Error Breakdown by App Version
  │    └── Playback QoE Heatmap (Buffer stalls, MPV errors)
  │
  └── 4. Access & Security Governance
       ├── Admin User Registry (MFA Enforcement, IP Whitelisting)
       ├── Active Admin Session Monitor
       └── Security Audit Trail (Immutable Log)
```

---

## 3. Core Operational Workflows

### 3.1 Provider Upload & Automated Signing Flow
```
 [Admin Operator]
       │ 1. Uploads built provider JAR/DEX
       ▼
 [Admin Backend (Go / Node.js)]
       │ 2. Runs Automated Lint & Static Sandbox Check:
       │    - Prohibited permissions
       │    - Unsafe reflection
       │    - CloudStream API compliance
       ▼
 [Max Stream HSM / KMS]
       │ 3. Signs compiled .cs3 with Root Release Key (Ed25519)
       ▼
 [Cloud Storage CDN]
       │ 4. Places signed artifact at:
       │    https://cdn.maxstream.app/providers/{id}-v{version}.cs3
       ▼
 [Manifest Compiler]
       │ 5. Appends provider metadata and SHA-256 to manifest.v2.json
       ▼
 [CDN Broadcast]
       │ 6. Triggers Global Client Sync Notification (FCM / Polling)
```

### 3.2 Emergency Kill Switch (Instant Revocation)
When a third-party host changes anti-scraping protections or emits abusive traffic:
1. Operator clicks **"Revoke Provider"** on the dashboard.
2. The backend updates the provider's manifest record:
   ```json
   {
     "id": "affected_provider",
     "status": "REVOKED",
     "enabled": false
   }
   ```
3. A high-priority silent Firebase Cloud Messaging (FCM) data push is broadcasted to all active clients:
   `{ "action": "SYNC_PROVIDERS", "priority": "high" }`.
4. Client `ServerProviderSyncService` executes an immediate sync, unregisters the provider from `APIHolder`, and deletes local binaries.

---

## 4. API Endpoints for Admin Control Panel

| Method | Endpoint | Description | Auth Requirement |
|---|---|---|---|
| `GET` | `/api/v1/admin/providers` | Lists all registered providers & stats | Bearer JWT (Role: ADMIN) |
| `POST` | `/api/v1/admin/providers/upload` | Uploads `.cs3` and initiates signing | Bearer JWT (Role: ADMIN + MFA) |
| `POST` | `/api/v1/admin/providers/{id}/revoke` | Immediately revokes a provider | Bearer JWT (Role: SUPER_ADMIN) |
| `GET` | `/api/v1/admin/manifest/preview` | Shows live and staged manifest JSON | Bearer JWT (Role: ADMIN) |
| `POST` | `/api/v1/admin/manifest/publish` | Commits changes and purges CDN cache | Bearer JWT (Role: ADMIN) |
| `GET` | `/api/v1/admin/telemetry/summary`| Aggregated QoE and failure graphs | Bearer JWT (Role: ADMIN) |
| `POST` | `/api/v1/admin/users/grant-admin`| Grants time-limited admin access | Bearer JWT (Role: SUPER_ADMIN) |

---

## 5. Security Architecture & RBAC

1. **Authentication:**
   - Admin panel logins require Google Identity with mandatory Hardware FIDO2 / Passkey MFA.
2. **Audit Logging:**
   - Every mutation (manifest publish, provider upload, revocation) is recorded in Cloud Logging with immutable retention (Write-Once-Read-Many).
3. **Least Privilege:**
   - Standard OTT users are completely air-gapped from this backend. The public app only reads the static signed CDN manifest and posts anonymized telemetry events to an ingestion buffer.
