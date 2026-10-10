# CloudStream SDK & UI Implementation Plan: Search & Details Logic

Comprehensive implementation plan for integrating **CloudStream Core SDK logic** and UI screens into **Max Stream / CineHub**.

---

## User Preferences & Critical UX Decisions

> [!IMPORTANT]
> All UI screens adhere strictly to Max Stream's existing **Glassmorphism design system** (`GlassCard`, `GlassButton`, `GlassDialog`, `GlassTopBar`) with modern fluid ambient themes and responsive edge-to-edge support.

- **Search Screen UX**: Minimal list view focused on fast stream link extraction and resolution tags (1080p, 4K, Sub/Dub, Server badges).
- **Media Details & Episode Logic**: Full-screen detailed view with dynamic backdrop imagery, TV show season/episode carousel, episode watch progress badges, and an integrated bottom sheet for stream source extraction and auto-resolution selection.

---

## 1. CloudStream Core SDK & Media Fetching Logic Audit

CloudStream's core SDK provides the media extraction, provider abstraction, and season/episode parser:

| Core SDK Domain | CloudStream Source File Path | Max Stream Target Path | Core Logic & Data Flow |
| :--- | :--- | :--- | :--- |
| **Provider Contracts** | `app/src/main/java/com/lagradost/cloudstream3/MainAPI.kt` | `app/src/main/kotlin/com/lagradost/cloudstream3/MainAPI.kt` | `search(query)` returning `SearchResponse` list. `load(url)` returning `LoadResponse` (`TvSeriesLoadResponse` / `MovieLoadResponse`) containing episode lists and seasons. |
| **Stream Extraction** | `app/src/main/java/com/lagradost/cloudstream3/utils/ExtractorLink.kt` | `app/src/main/kotlin/com/lagradost/cloudstream3/utils/ExtractorLink.kt` | `loadLinks(data, isCasting, subtitleCallback, callback)` extracts high-res stream URLs, headers, and quality tags. |
| **Bridge Adapter** | *N/A (CloudStream native)* | `app/src/main/kotlin/com/maxstream/bridge/CloudStreamBridge.kt` | Bridges CloudStream `MainAPI` providers to Kotlin Coroutines `Flow<UiState>` for Jetpack Compose UI screens. |

---

## 2. Detailed Implementation Plan: Search Screen & Media Details View

### Phase 1: Minimal Stream-Focused Search Screen (`SearchScreen.kt`)
- **File Path**: `app/src/main/kotlin/xyz/mpv/rex/ui/search/SearchScreen.kt`
- **Logic & Functionality**:
  1. **Debounced Query Execution**: 300ms debounce on search text input triggering `CloudStreamBridge.searchAllProviders(query)`.
  2. **Active Provider Chips**: Filterable chips to toggle individual active CloudStream providers (e.g., FlixHQ, SuperStream, AnimePahe, VidSrc).
  3. **Minimal Stream Resolution List Item**:
     - Compact horizontal row with poster thumbnail, title, release year, media type (Movie / TV Series / Anime), and primary active provider badge.
     - **Quick Stream Trigger**: Immediate "Extract Stream" icon button to resolve links directly without deep navigating.
     - Quality and audio indicators (4K, 1080p, Multi-Sub, Dubbed).
  4. **State Machine**: Unified `SearchUiState` handling `Idle`, `Loading`, `Success(List<SearchResponse>)`, and `Empty/Error`.

---

### Phase 2: Full-Screen Media Details & Episode Carousel View (`MediaDetailView.kt`)
- **File Path**: `app/src/main/kotlin/xyz/mpv/rex/ui/browser/sheets/MediaDetailSheet.kt` & `MediaDetailScreen.kt`
- **Logic & Functionality**:
  1. **Dynamic Backdrop & Ambient Header**:
     - High-resolution hero backdrop image with dark ambient gradient overlay and frosted glass top navigation bar (`GlassTopBar`).
     - Metadata panel: Rating, duration/seasons, genre tags, release year, and provider source attribution.
  2. **TV Series Season & Episode Carousel**:
     - **Season Selector**: Horizontal pill row / drop-down drawer for switching seasons (Season 1, Season 2, Specials).
     - **Episode Carousel & List**: Horizontal scroll or vertical card list showing episode thumbnail, episode number, title, overview synopsis, runtime, and watched status progress bar.
  3. **Stream Link Extraction Bottom Sheet (`PlayLinkSheet.kt`)**:
     - Clicking any episode or movie "Play" button opens the glassmorphic extraction sheet.
     - Fetches live stream links via `MainAPI.loadLinks()`.
     - Displays server names (Server 1, Server 2, VidCloud, UpCloud), video quality tags (1080p, 720p, 4K), and auto-selects the highest resolution link by default for seamless playback in ExoPlayer/MPV.

---

### Phase 3: Additional CloudStream UI Features to Complete 110% Parity

1. **Download Manager Screen (`DownloadManagerScreen.kt`)**:
   - `app/src/main/kotlin/xyz/mpv/rex/ui/downloads/DownloadManagerScreen.kt`
   - Active download progress, download speed stats (MB/s), storage space bar, and offline media playback launcher.

2. **CloudSync & Scrobble Screen (`CloudSyncScreen.kt`)**:
   - `app/src/main/kotlin/xyz/mpv/rex/ui/sync/CloudSyncScreen.kt`
   - Multi-account sync dashboard for **Trakt.tv**, **AniList**, **MyAnimeList**, and **Simkl**.

3. **Extension Detail Screen (`ExtensionDetailScreen.kt`)**:
   - `app/src/main/kotlin/xyz/mpv/rex/ui/preferences/ExtensionDetailScreen.kt`
   - Extension version inspection, provider cache cleaner, and auto-update toggles.

4. **Cast / DLNA Device Selector (`CastDeviceSheet.kt`)**:
   - `app/src/main/kotlin/xyz/mpv/rex/ui/browser/sheets/CastDeviceSheet.kt`
   - Chromecast and DLNA device discovery modal sheet with remote playback controls.

---

## 3. Step-by-Step Execution Order

1. **Update `SearchScreen.kt`**: Integrate minimal list view with debounced query search and instant stream extraction action.
2. **Implement Media Details View**: Build full-screen detailed view with hero backdrop, season episode carousel, and link extraction bottom sheet trigger.
3. **Connect CloudStream SDK Data Layer**: Ensure `CloudStreamBridge` handles `LoadResponse` parsing for episode lists and stream links seamlessly.
4. **Build Download & CloudSync Screens**: Complete remaining UI screens for 110% feature parity.
5. **Verify Compilation**: Run `compile_applet` to validate syntax and Jetpack Compose bindings.

---

## 4. Architectural Verification Matrix

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Max Stream / CineHub UI Layer                         │
├─────────────────┬───────────────────┬───────────────────┬───────────────────┤
│ SearchScreen.kt │ MediaDetailScreen │ DownloadManager   │ CloudSyncScreen   │
│ (Minimal List)  │(Episode Carousel) │ (Download Queue)  │ (Trakt/AniList)   │
└────────┬────────┴─────────┬─────────┴─────────┬─────────┴─────────┬─────────┘
         │                  │                   │                   │
         ▼                  ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CloudStreamBridge & Extraction Logic                     │
│  - searchAllProviders()     - loadMediaDetails()    - loadEpisodeLinks()    │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CloudStream Core SDK Layer                           │
│  - MainAPI (FlixHQ, SuperStream, AnimePahe, VidSrc)                          │
│  - ExtractorLink Engine (VidCloud, UpCloud, MixDrop, StreamTape)            │
└─────────────────────────────────────────────────────────────────────────────┘
```
