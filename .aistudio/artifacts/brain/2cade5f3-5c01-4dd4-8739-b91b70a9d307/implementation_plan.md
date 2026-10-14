# Integration of CloudStream Core AAR Libraries & Legacy Cleanup

A comprehensive blueprint to decommission embedded and duplicated CloudStream source files and wire compiled local AAR libraries (`app-release.aar`, `library.aar`, and `shared.aar`) into Max Stream as the headless streaming and scraping engine.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The architectural decisions below incorporate your answers to ensure flawless build and runtime parity.

- **AAR Binary Selection**: Using `app/libs/app-release.aar`, `library.aar`, and `shared.aar` for clean release-grade optimization.
- **Application Class Entrypoint**: Retaining `xyz.mpv.rex.MaxStreamApp` which directly inherits from `com.lagradost.cloudstream3.AcraApplication`, preserving Koin dependency injection, image loading caches, crash logging, and lifecycle setup.
- **Provider Aggregation Strategy**: Implementing dynamic multi-provider search across all installed plugins while supporting `TmdbProvider` as a foundational baseline with Cloudflare and Google DNS resolution.

---

### 1. Overview & Core Concept

- **What It Does**: Replaces thousands of lines of copied and out-of-sync Kotlin sources in `com.lagradost.*` with pre-compiled, optimized Android Archive (`.aar`) binaries. This provides a headless core engine capable of executing CloudStream plugins, running HTML extractors (Rhino JS, Jsoup, Ksoup), resolving video streams, and querying providers in parallel.
- **Target Audience / Persona**: Users seeking fast, resilient media streaming with unified search across providers without build conflicts, runtime class duplication, or crashes.
- **Key Value**: Drastically reduces build times, prevents duplicate class collision errors, enforces stable AAR interfaces, and enables seamless plugin and extractor execution.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  - **Search & Discovery**: User enters a query into CineHub search; parallel queries dispatch across registered providers (including TMDB and active plugins) with unified result cards and failure isolation.
  - **Details & Episode Selection**: Selecting media loads seasons, episode lists, plot descriptions, and poster artwork directly mapped from provider models into Compose cards.
  - **Stream Resolution & Playback**: Clicking an episode initiates extractor resolution (`loadExtractor`) to fetch direct video streams (`.m3u8`, `.mp4`) and subtitle tracks, directly handing off playback to the REX MPV player.
- **Visual & UI Polish**: Existing Material 3 theme, dark-mode styling, responsive episode grids, and error state toasts are preserved without regressions.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Complete Removal of `app/src/main/kotlin/com/lagradost`**
  - *Chosen Approach*: Fully delete the local `com.lagradost.cloudstream3.*` and `com.lagradost.api.*` source directories.
  - *Why*: The compiled classes in `app-release.aar`, `library.aar`, and `shared.aar` provide the complete implementation. Retaining source files creates `Duplicate class com.lagradost... found in modules` fatal DEX errors.
  - *Alternatives Considered*: Retaining selective source overrides was rejected because it introduces ABI mismatches with the compiled AAR bytecode.

- **Decision 2: Gradle Repository & Desugaring Alignment**
  - *Chosen Approach*: Enable Java 8+ core library desugaring (`isCoreLibraryDesugaringEnabled = true` with `desugar_jdk_libs:2.1.4`) and configure JitPack in `settings.gradle.kts` to allow `com.github.Bliss23`.
  - *Why*: The AARs and NiceHttp require Java 8+ time/stream APIs on devices below API 34 and rely on Bliss23's NiceHttp client on JitPack.
  - *Alternatives Considered*: Shimming HTTP calls manually was rejected as NiceHttp is internally bound inside CloudStream's network layer.

- **Decision 3: Retaining MaxStreamApp with AcraApplication Inheritance**
  - *Chosen Approach*: Keep `MaxStreamApp : AcraApplication()` in `AndroidManifest.xml`.
  - *Why*: MaxStreamApp provides Koin DI, Coil image caching, locale switching, and crash management while initializing `AcraApplication` as its base class.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                   Max Stream Presentation                   │
│   (Jetpack Compose UI: CineHubScreen, SearchScreen, Player)  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Bridge Layer (com.maxstream.bridge)         │
│  ┌─────────────────────────┐   ┌──────────────────────────┐ │
│  │   CloudStreamEngine     │   │   CineHubSearchBridge    │ │
│  │   (Init, DNS, Plugins)  │   │   (Multi-Provider Flow)  │ │
│  └─────────────────────────┘   └──────────────────────────┘ │
│  ┌─────────────────────────┐   ┌──────────────────────────┐ │
│  │   CineHubDetailsBridge  │   │   RexPlayerBridge        │ │
│  │   (Metadata & Episodes) │   │   (Stream Link Extractor)│ │
│  └─────────────────────────┘   └──────────────────────────┘ │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Headless AAR Core Engine                    │
│  ┌──────────────────────────┐  ┌──────────────────────────┐ │
│  │ app-release.aar          │  │ library.aar & shared.aar │ │
│  │ (CloudStream core/engine)│  │ (Base models, utils, API)│ │
│  └──────────────────────────┘  └──────────────────────────┘ │
│  Transitive: NiceHttp, Jsoup, Ksoup, Rhino, Kotlinx.Serial  │
└─────────────────────────────────────────────────────────────┘
```

#### Detailed Execution Phases:

1. **Phase 1: Source Cleanup**
   - Delete legacy source tree `app/src/main/kotlin/com/lagradost`.
   - Audit and confirm no conflicting duplicate extractors or provider models remain.

2. **Phase 2: Gradle & Repository Configuration**
   - Update `settings.gradle.kts` to permit `com.github.Bliss23` in JitPack repository filters.
   - Update `app/build.gradle.kts`:
     - Set Java 17 compatibility and `isCoreLibraryDesugaringEnabled = true`.
     - Link `files("libs/app-release.aar")`, `files("libs/library.aar")`, and `files("libs/shared.aar")`.
     - Add transitive dependencies: `com.github.Bliss23:nicehttp:0.4.11`, `org.jsoup:jsoup:1.18.1`, `com.fleeksoft.ksoup:ksoup:0.2.2`, `org.mozilla:rhino:1.7.15`, and `desugar_jdk_libs:2.1.4`.

3. **Phase 3: Manifest & Proguard Configuration**
   - Verify `AndroidManifest.xml` has required permissions (`INTERNET`, `ACCESS_NETWORK_STATE`) and `android:name=".MaxStreamApp"`.
   - Update `app/proguard-rules.pro` with explicit rules for Rhino, Ksoup, Jsoup, and KotlinX serialization annotations.

4. **Phase 4: Bridge & Engine Validation**
   - Validate `CloudStreamEngine`, `CineHubSearchBridge`, `CineHubDetailsBridge`, and `RexPlayerBridge` against the AAR classes.
   - Ensure DNS setup (`addCloudFlareDns`, `addGoogleDns`) and dynamic multi-provider search with fallback work seamlessly.

5. **Phase 5: Build Verification**
   - Run `compile_applet` to confirm clean compilation and zero duplicate class errors.
