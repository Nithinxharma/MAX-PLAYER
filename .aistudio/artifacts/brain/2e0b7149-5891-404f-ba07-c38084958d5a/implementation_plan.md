# Standardize MaxStream Architecture & Connect Screens to AAR Engine

A unified architectural restructuring to consolidate scattered CineHub, MaxStream, and CloudStream bridge modules into `xyz.mpv.rex.maxstream`, centralize shared design components under `xyz.mpv.rex.core.ui`, and bind all media exploration, catalog rails, search discovery, and playback streams directly to the compiled AAR engine with automatic provider discovery and dynamic failover.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The architectural blueprint below directly integrates your confirmed specifications:

- **Package & Namespace Structure**: Standardizing on `xyz.mpv.rex.maxstream` across all streaming engine, bridge, provider, extension, and UI modules, maintaining seamless compatibility with `xyz.mpv.rex`.
- **AAR Engine Connection**: Auto-loading default providers (including built-in TMDB meta-providers and active plugin repositories) on launch with automatic background sync and health-verified stream failover.
- **Shared UI Component Directory**: Relocating and consolidating all reusable UI elements (glass cards, liquid search, media badges, poster cards, content rails, and media detail sheets) into a clean, unified `xyz.mpv.rex.core.ui` layer.

---

### 1. Overview & Core Concept

- **What It Does**: Unifies the currently split codebases (`com.maxstream.bridge`, `xyz.mpv.rex.cinehub`, and `xyz.mpv.rex.cinetv`) into a cohesive `xyz.mpv.rex.maxstream` domain. Connects all Compose screens (Catalog, Search, Details, and Live TV) directly to the compiled CloudStream AAR runtime (`app-release.aar`, `library.aar`, `shared.aar`), and routes video playback with auto-extracted `.m3u8` / `.mp4` URLs directly into REX MPV Player.
- **Target Audience / Persona**: Users seeking an ultra-fast, smooth streaming experience with unified multi-provider search, automatic stream link resolution, and a clean, responsive glassmorphic interface.
- **Key Value**: Eliminates duplicate bridge layers, resolves scattered package imports, standardizes plugin management, and guarantees 100% reliable stream extraction without UI lag.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  - **Launch & Auto-Discovery**: On app launch, `CloudStreamEngine` initializes background DNS, activates built-in and cached repository plugins, and seeds the home carousel and content rails.
  - **Discovery & Content Rails**: CineHub/MaxStream screen displays curated trending movies, TV series, anime, and live TV channels in fluid horizontal rails powered by `xyz.mpv.rex.core.ui.MaxStreamContentRail`.
  - **Unified Search**: `MaxStreamLiquidGlassSearch` dispatches real-time queries across all active providers in parallel via `CineHubSearchBridge`, displaying combined results with source badges and quality chips (`4K`, `1080p`, `Multi-Audio`).
  - **Detail & Stream Selection**: Clicking media opens `GlassmorphicMediaDetailSheet` with seasons, episodes, and providers. Selecting an episode triggers `RexPlayerBridge.resolveStream()` to extract healthy links and immediately start playback.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Premium Luxury Dark Glassmorphism with deep navy/black backgrounds, blurred glass backdrops, dynamic accent lighting, and 16.dp rounded corners.
  - *Shared Component Layer (`xyz.mpv.rex.core.ui`)*:
    - `MaxStreamGlassCard`: Acrylic glass surface with subtle borders and elevation.
    - `MaxStreamPosterCard`: Adaptive aspect-ratio card with lazy thumbnail loading and watched indicators.
    - `MaxStreamBadges`: Sharp pill badges for stream quality, dub/sub status, and source provider.
    - `MaxStreamContentRail`: Snap-scrolling horizontal list with header actions.
  - *Typography*: Google Sans Flex font family with sharp display headlines and readable subtext.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Package Unification under `xyz.mpv.rex.maxstream`**
  - *Chosen Approach*: Migrate all classes from `com.maxstream.bridge` and `xyz.mpv.rex.cinehub` into `xyz.mpv.rex.maxstream.*` subpackages (`engine`, `bridge`, `extension`, `provider`, `ui`, `data`).
  - *Why*: Eliminates cross-package ambiguity, prevents conflicting imports, and conforms strictly to standard Android module conventions.
  - *Alternatives Considered*: Keeping separate `cinehub` and `maxstream` folders was rejected because it created duplicate models and fragmented UI components.

- **Decision 2: Centralized `xyz.mpv.rex.core.ui` Design System**
  - *Chosen Approach*: Move common UI cards, glass modifiers, buttons, and sheets into `xyz.mpv.rex.core.ui`, accessible by both local media browser and streaming modules.
  - *Why*: Prevents code duplication and allows the entire application (local video player, file explorer, and online streaming) to share a coherent design language.

- **Decision 3: Auto-Loading with Dynamic Stream Failover**
  - *Chosen Approach*: Automatically initialize core DNS, TMDB meta-providers, and registered repository plugins upon boot, accompanied by automated candidate health checking in `StreamFailoverManager`.
  - *Why*: Provides instant search and playback without forcing the user to manually configure providers every session.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Unified MaxStream Presentation                       │
│    (CineHubScreen, CineDetailScreen, CineHubSearchScreen, LiveTvScreen)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ uses
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Unified Core UI (xyz.mpv.rex.core.ui)               │
│   ┌─────────────────────┐  ┌────────────────────┐  ┌────────────────┐  │
│   │ MaxStreamGlassCard  │  │ MaxStreamPosterCard│  │MaxStreamBadges │  │
│   └─────────────────────┘  └────────────────────┘  └────────────────┘  │
│   ┌─────────────────────┐  ┌────────────────────┐  ┌────────────────┐  │
│   │MaxStreamContentRail │  │GlassMediaDetailSheet│ │LiquidGlassSearch│  │
│   └─────────────────────┘  └────────────────────┘  └────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             MaxStream Bridge Layer (xyz.mpv.rex.maxstream.bridge)      │
│   ┌──────────────────────┐  ┌────────────────────┐  ┌────────────────┐ │
│   │ CineHubSearchBridge  │  │CineHubDetailsBridge│  │ RexPlayerBridge│ │
│   └──────────────────────┘  └────────────────────┘  └────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             MaxStream Engine Layer (xyz.mpv.rex.maxstream.engine)      │
│   ┌──────────────────────┐  ┌────────────────────┐  ┌────────────────┐ │
│   │  CloudStreamEngine   │  │ CloudStreamCompat  │  │ FailoverManager│ │
│   └──────────────────────┘  └────────────────────┘  └────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ executes via
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│              Compiled Headless CloudStream AAR Binaries                │
│             app-release.aar  │  library.aar  │  shared.aar             │
└────────────────────────────────────────────────────────────────────────┘
```

#### Detailed Execution Steps:
1. **Core UI Centralization (`xyz.mpv.rex.core.ui`)**:
   - Create `xyz.mpv.rex.core.ui` package.
   - Move and consolidate `MaxStreamGlassCard`, `MaxStreamGlassComponents`, `MaxStreamBadges`, `MaxStreamPosterCard`, `MaxStreamContentRail`, `MaxStreamLiquidGlassSearch`, and `GlassmorphicMediaDetailSheet` into `xyz.mpv.rex.core.ui`.
2. **Package & Subpackage Unification (`xyz.mpv.rex.maxstream.*`)**:
   - Reorganize engine files into `xyz.mpv.rex.maxstream.engine` (`CloudStreamEngine.kt`, `CloudStreamCompat.kt`).
   - Move bridges into `xyz.mpv.rex.maxstream.bridge` (`CineHubSearchBridge.kt`, `CineHubDetailsBridge.kt`, `RexPlayerBridge.kt`).
   - Standardize extensions, plugins, and failover managers under `xyz.mpv.rex.maxstream.extension` and `xyz.mpv.rex.maxstream.failover`.
   - Align UI screens under `xyz.mpv.rex.maxstream.ui`.
3. **Screen & Player Wire-Up**:
   - Connect `CineHubViewModel` and `CineHubSearchStateHolder` to invoke `CloudStreamEngine` and `CineHubSearchBridge`.
   - Update `RexPlayerBridge` handoff to seamlessly feed direct streams (`ExtractorLink`) into `PlayerActivity` / MPV controller.
4. **Build & Unit Test Verification**:
   - Update Koin DI modules in `DomainModule.kt` and `xyz.mpv.rex.MaxStreamApp`.
   - Verify compilation with `compile_applet` and run Robolectric tests.
