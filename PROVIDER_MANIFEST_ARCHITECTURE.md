# MAX STREAM — PROVIDER MANIFEST ARCHITECTURE (PHASE 2)

**Document Version:** 2.0  
**Status:** Protocol & Architecture Specification  
**Subsystems:** ServerProviderSyncService, ExtensionManager, ProviderRegistry, APIHolder  

---

## 1. System Vision

In commercial OTT operations, client applications never query third-party GitHub raw links directly or let end-users manually enter repository URLs. 

The **Max Stream Remote Manifest Architecture** delivers:
- **Centralized Control:** Providers are distributed via signed JSON manifests hosted on the Max Stream CDN / Cloud Infrastructure.
- **Silent Lifecycle Management:** Installation, updates, dynamic activation, and revocations happen automatically in the background.
- **Cryptographic Trust:** Every provider binary (`.cs3` containing DEX) is cryptographically signed and verified prior to dynamic loading.

---

## 2. Remote Manifest JSON Schema

Below is the definitive schema for `manifest.v2.json`:

```json
{
  "$schema": "https://api.maxstream.app/schemas/provider-manifest.v2.json",
  "manifestVersion": 2,
  "generatedAt": 1727145600000,
  "minAppVersionCode": 100,
  "signature": "MEQCIFz...[Ed25519 or ECDSA Base64 signature of canonical payload]...",
  "meta": {
    "environment": "production",
    "region": "GLOBAL",
    "ttlSeconds": 3600
  },
  "providers": [
    {
      "id": "castletv",
      "package": "xyz.mpv.rex.cinehub.provider.castletv",
      "name": "CastleTV Direct",
      "version": 5,
      "versionName": "2.1.0",
      "status": "ACTIVE",
      "requirement": "MANDATORY",
      "downloadUrl": "https://cdn.maxstream.app/providers/castletv-v5.cs3",
      "sha256": "4b971a81dc193498877e8a93e36e63dfa838df2c0a4e760ec187a54a7c06eb18",
      "minApiVersion": 1,
      "categories": ["MOVIES", "TV", "ANIME"],
      "qualityLevels": ["4K", "1080P"],
      "enabled": true,
      "forceUpdate": true
    },
    {
      "id": "streamwire",
      "package": "xyz.mpv.rex.cinehub.provider.streamwire",
      "name": "StreamWire Ultra",
      "version": 3,
      "versionName": "1.3.2",
      "status": "ACTIVE",
      "requirement": "OPTIONAL",
      "downloadUrl": "https://cdn.maxstream.app/providers/streamwire-v3.cs3",
      "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      "minApiVersion": 1,
      "categories": ["MOVIES", "SERIES"],
      "qualityLevels": ["1080P", "720P"],
      "enabled": true,
      "forceUpdate": false
    },
    {
      "id": "legacy_stream",
      "package": "xyz.mpv.rex.cinehub.provider.legacystream",
      "name": "Legacy Streamer",
      "version": 1,
      "versionName": "1.0.0",
      "status": "REVOKED",
      "requirement": "DEPRECATED",
      "downloadUrl": "",
      "sha256": "",
      "minApiVersion": 1,
      "categories": [],
      "qualityLevels": [],
      "enabled": false,
      "forceUpdate": false
    }
  ]
}
```

---

## 3. Provider State Machine

Each managed provider exists in one of five operational states:

```
  [ DISCOVERED ]
        │
        ▼ (Download & Check SHA-256 / Signature)
  [ VERIFIED ]
        │
        ▼ (Install into App Private Storage & DexClassLoader)
   [ ACTIVE ] ◄──────────┐
        │                │ (Silent Background Update)
        ├────────────────┘
        │
        ├─► [ REVOKED ] ──► (Instant APIHolder Unregister & File Deletion)
        │
        └─► [ DISABLED ] ─► (Unregistered from Search, Kept on Storage)
```

### State Behaviors:
1. **ACTIVE:** Fully loaded into `APIHolder.allProviders` and indexed in `ProviderRegistry`. Included in global unified searches.
2. **REVOKED:** Flagged by the server as broken, malicious, or retired. `ServerProviderSyncService` immediately invokes `APIHolder.removePlugin()`, uninstalls the DEX from disk, and removes database references.
3. **MANDATORY:** Installed silently without requiring user intervention. If installation fails, retry with exponential backoff and notify telemetry.
4. **OPTIONAL:** Downloaded on-demand or during non-metered Wi-Fi synchronization.
5. **FORCE_UPDATE:** If `currentVersion < manifest.version` and `forceUpdate == true`, the old provider is unloaded immediately and replaced before subsequent playback attempts.

---

## 4. Cryptographic Signature Validation

To ensure that only authentic Max Stream providers execute on user devices, the manifest and binaries adhere to a strict verification protocol:

```
                        ┌───────────────────────────────┐
                        │   Remote Manifest Download    │
                        └───────────────┬───────────────┘
                                        │
                                        ▼
                        ┌───────────────────────────────┐
                        │ Extract Signature & Payload   │
                        └───────────────┬───────────────┘
                                        │
                                        ▼
                        ┌───────────────────────────────┐
                        │ Verify with Embedded Public   │
                        │ Key (Ed25519 / RSA-2048)      │
                        └───────────────┬───────────────┘
                                        │
                       ┌────────────────┴────────────────┐
                    Valid                              Invalid
                       │                                 │
                       ▼                                 ▼
         ┌───────────────────────────┐     ┌───────────────────────────┐
         │ Process Providers Array   │     │ Reject Manifest & Report  │
         │ - Verify binary SHA-256   │     │ Security Alert Telemetry  │
         │ - Atomic File Swap        │     └───────────────────────────┘
         │ - Register in APIHolder   │
         └───────────────────────────┘
```

### Signature Implementation Rules:
- **Canonical Serialization:** The JSON body excluding the `signature` attribute is normalized (RFC 8785) before hashing.
- **Embedded Public Key:** The public verification key is embedded within the native layer (`libs/arm64-v8a/librex.so`) or a protected Kotlin object to make tampering difficult.
- **Atomic File Replacement:** The updated DEX file is written to a temporary cache file (`provider.tmp`), SHA-256 is verified, file permissions set to read-only (`setReadOnly()`), and then atomically moved to its permanent path.

---

## 5. Backward Compatibility & CloudStream Guarantees

1. **`APIHolder` Independence:** `APIHolder.addPlugin` and `APIHolder.removePlugin` remain completely unaltered. CloudStream Core does not know whether a provider came from a legacy repository or the modern server sync service.
2. **CastleTV Embedded Fallback:** In offline scenarios or initial launch without network access, `ServerProviderSyncService` loads the pre-packaged CastleTV provider fallback so playback is never dead-on-arrival.
3. **Extractor Separation:** Extractors (e.g., Streamtape, Vidcloud, Rabbitstream) continue to operate globally via `CloudstreamCore` extractors registry, allowing new extractors to link dynamically with server-synced providers.
