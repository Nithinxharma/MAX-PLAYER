# MaxStream Core AAR Synchronization & Engine Bridge Plan

Fixing the AAR plugin synchronization (`aap*.aar`, `library.aar`, `shared.aar`), dynamic `DexClassLoader` scanning, automatic home page provider loading on launch, and parallel search link extraction with dynamic failover across the unified MaxStream engine architecture.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following confirmed decisions are integrated into this architectural fix based on user choices in Phase 1:

- **Confirmed Choice 1 (Home Page Synchronization)**: Automatic provider initialization on app launch with dynamic failover across default AAR plugins and registered providers.
- **Confirmed Choice 2 (Search & Link Extraction)**: Parallel query execution across active providers with dynamic failover to secondary extractors when primary links fail.
- **Confirmed Choice 3 (AAR Engine & Plugin Architecture)**: Dynamic `DexClassLoader` scanning of local AAR files (`library.aar`, `shared.aar`, `aap*.aar`) with full bridge synchronization under `xyz.mpv.rex.maxstream`.

---

## 1. Overview & Core Concept

MaxStream utilizes a dynamic plugin system compatible with CloudStream providers delivered as compiled AAR assets. Recently, home page sections, search results, and stream link resolvers experienced integration breakage because provider registration relied on legacy scattered packages (`com.maxstream.bridge`, `xyz.mpv.rex.cinehub`) instead of the unified `xyz.mpv.rex.maxstream` engine bridge.

This plan addresses:
1. **Dynamic AAR Discovery & Loading**: Scanning embedded AAR files (`library.aar`, `shared.aar`, `aap*.aar`) in `app/libs` and `assets`, extracting DEX bytecode, and instantiating provider classes (`MainAPI`, `ExtractorApi`, `BasePlugin`).
2. **Launch-time Provider Bootstrapping**: Guaranteeing default providers are registered and active immediately on app startup so home categories automatically populate.
3. **Parallel Search & Failover Resolver**: Updating `CloudStreamCompat` and `StreamHealthResolver` to execute search queries across all active plugins in parallel, handling link extraction retries and stream fallbacks smoothly.

---

## 2. User Experience & Visual Design

- **Home Screen Automatic Hydration**: Upon opening MaxStream, hero carousels and category rows (Trending, Movies, TV Series, Anime) immediately display content loaded from default AAR providers with glassmorphic skeleton shimmer states.
- **Unified Liquid Glass Search**: Performing a search query broadcasts to all indexed AAR providers simultaneously, displaying results as they stream in with source badges and quality indicators.
- **Robust Stream Link Extraction**: Selecting a media item launches the detail sheet and media player seamlessly, trying primary stream links and automatically falling back to secondary mirrors if a link is broken.

---

## 3. Key Product Decisions & Trade-Offs

- **DexClassLoader AAR Extraction**:
  - *Chosen Approach*: Extract `classes.dex` from local AAR files (`app/libs/library.aar`, `shared.aar`, etc.) into an optimized private cache directory on startup and index provider classes via reflection.
  - *Why*: Ensures standard CloudStream `.aar` / `.apk` extension files can run inside MaxStream without requiring direct re-compilation of provider source code.
- **Parallel Coroutine Dispatching**:
  - *Chosen Approach*: `async`/`awaitAll` coroutine scopes for search and link extraction with timeouts and error catching.
  - *Why*: Prevents a slow or failing provider from blocking search results or stream playback for other functional providers.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MaxStream App Launch                            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│  ExtensionManager & DynamicPluginRegistry (xyz.mpv.rex.maxstream)      │
│  - Scan app/libs/ & assets (library.aar, shared.aar, aap*.aar)         │
│  - DexClassLoader -> Load MainAPI / ExtractorApi / BasePlugin          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                        ┌───────────┴───────────┐
                        ▼                       ▼
┌────────────────────────────────┐     ┌────────────────────────────────┐
│  Home Screen ViewModel         │     │  Parallel Search Resolver      │
│  - Load default provider home  │     │  - Dispatch query across APIs  │
│  - Dynamic failover on error   │     │  - Aggregate & stream results  │
└────────────────────────────────┘     └────────────────────────────────┘
```

### Implementation Sequence

1. **Update `ExtensionManager` & `DynamicPluginRegistry`**:
   - Ensure local AAR files in `app/libs/` (`library.aar`, `shared.aar`, `app-release.aar`) are discovered, unpacked, and loaded into `DexClassLoader` on app startup.
   - Register discovered `MainAPI` and `ExtractorApi` instances into the central plugin registry under `xyz.mpv.rex.maxstream.engine`.

2. **Connect Home Screen & Search ViewModels**:
   - Verify `HomeViewModel` calls `CloudStreamEngine.getHomePage()` with active providers on launch.
   - Implement dynamic failover so if the primary provider returns empty or fails, secondary AAR providers automatically take over.
   - Verify `SearchViewModel` uses parallel coroutines to query all active providers and stream aggregated results to `MaxStreamLiquidGlassSearch`.

3. **Validate Compilation & Runtime**:
   - Execute `compile_applet` to confirm all Kotlin sources, AAR bridge methods, and UI components compile cleanly.
