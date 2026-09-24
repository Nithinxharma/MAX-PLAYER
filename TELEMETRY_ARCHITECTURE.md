# MAX STREAM — TELEMETRY & OBSERVABILITY ARCHITECTURE (PHASE 2)

**Document Version:** 2.0  
**Status:** System Architecture Specification  
**Focus:** Provider Reliability, Playback Quality of Experience (QoE), Error Diagnostics, Anonymized Reporting  

---

## 1. Objectives & Privacy Principles

Max Stream operates as a premium streaming client. To maintain 99.9% playback availability and proactively identify broken third-party scrapers or dead extractor streams, the telemetry framework collects anonymous, actionable performance and failure metrics.

### Privacy Guarantees:
- **Zero Personally Identifiable Information (PII):** User email, names, IP addresses, and personal media titles are never sent in telemetry events.
- **Content Masking:** Streaming queries can be hashed or generalized into genre/content categories.
- **Opt-Out Compliant:** Telemetry respecting user preferences configured in `PreferencesScreen`.

---

## 2. Telemetry Event Taxonomy

All events extend `MaxStreamTelemetryEvent`:

```
MaxStreamTelemetryEvent
  ├── ProviderSyncEvent (SYNC_SUCCESS, SYNC_FAILED, MANIFEST_INVALID)
  ├── ProviderLifecycleEvent (INSTALL_FAILED, DEX_VERIFY_FAILED, REVOKED)
  ├── SearchEvent (SEARCH_EMPTY, SEARCH_TIMEOUT, PROVIDER_ERROR)
  ├── ExtractionEvent (EXTRACTOR_FAILED, LINK_EXPIRED, CAPTCHA_TRIGGERED)
  └── PlaybackQoEEvent (PLAYBACK_ERROR, BUFFERING_STALL, FORMAT_UNSUPPORTED)
```

### Event Payload Definitions

#### 1. `provider_sync_failure`
```json
{
  "eventType": "PROVIDER_SYNC_FAILURE",
  "timestamp": 1727146000000,
  "appVersion": "1.0.0",
  "buildType": "release",
  "manifestUrl": "https://api.maxstream.app/v2/manifest",
  "errorCode": "SIGNATURE_VERIFICATION_FAILED",
  "httpStatusCode": 200,
  "errorMessage": "Expected public key signature mismatch",
  "networkType": "WIFI",
  "deviceIdHash": "a9f8e4b7c6..."
}
```

#### 2. `provider_install_failure`
```json
{
  "eventType": "PROVIDER_INSTALL_FAILURE",
  "providerId": "castletv",
  "targetVersion": 5,
  "currentVersion": 4,
  "failureReason": "DEX_CLASS_LOADER_EXCEPTION",
  "details": "java.lang.ClassNotFoundException: CastleTVProvider",
  "availableDiskSpaceMb": 1420
}
```

#### 3. `extractor_failure`
```json
{
  "eventType": "EXTRACTOR_FAILURE",
  "providerId": "castletv",
  "extractorName": "VidCloud",
  "streamHost": "rabbitstream.net",
  "failureType": "HTTP_403_FORBIDDEN",
  "durationMs": 1450,
  "episodeType": "SERIES"
}
```

#### 4. `playback_failure`
```json
{
  "eventType": "PLAYBACK_FAILURE",
  "engine": "MPV",
  "format": "HLS",
  "errorCategory": "DEMUXER_ERROR",
  "mpvErrorCode": -11,
  "mpvErrorString": "MPV_ERROR_UNSUPPORTED",
  "durationPlayedSec": 0,
  "qualitySelected": "1080p"
}
```

---

## 3. Client Architecture: `TelemetryManager`

```
 ┌────────────────────────────────────────────────────────┐
 │            Max Stream Subsystem Producers              │
 │  (ServerProviderSyncService, RexPlayerBridge, Search)  │
 └──────────────────────────┬─────────────────────────────┘
                            │ logEvent(event)
                            ▼
 ┌────────────────────────────────────────────────────────┐
 │                   TelemetryManager                     │
 │  - InMemory Event Channel / RingBuffer                 │
 │  - Batching (Max 20 events or 60-second flush)         │
 │  - Deduplication & Throttling Filters                  │
 └──────────────────────────┬─────────────────────────────┘
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
 ┌─────────────────────┐         ┌─────────────────────┐
 │ Room Cache (Offline)│         │ Remote Telemetry    │
 │ (Max 200 events)    │         │ Dispatcher (Worker) │
 └─────────────────────┘         └──────────┬──────────┘
                                            │ HTTP POST (GZIP)
                                            ▼
                                 ┌─────────────────────┐
                                 │ Max Stream Backend  │
                                 │ Telemetry Collector │
                                 └─────────────────────┘
```

### Key Components:
1. **Deduplication Engine:** If an extractor fails 50 times in a single search, only 1 event is emitted per 15-minute window with a `counter: 50` parameter.
2. **WorkManager Periodic Flush:** A low-priority background worker drains accumulated events when connected to unmetered network.
3. **Fail-Safe Isolation:** Telemetry operations execute on `Dispatchers.IO` wrapped in comprehensive try-catch handlers. Telemetry will **never** cause an ANR or application crash.
