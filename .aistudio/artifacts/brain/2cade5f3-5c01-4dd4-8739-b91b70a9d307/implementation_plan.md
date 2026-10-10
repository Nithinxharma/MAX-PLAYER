# Max Stream: 100% CloudStream 3 Core Engine Integration with Glassmorphic CineHub UI & REX MPV Player

A comprehensive architectural blueprint for completing the full 100% core CloudStream 3 engine integration in Max Stream (`xyz.mpv.rex`), featuring fluid Glassmorphism UI components and explicit intent hand-off to the standalone MPV-based REX Player.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following choices were confirmed by the user during Phase 1 clarification:

- **Confirmed Decision 1 (Visual Style)**: Premium Glassmorphism visual theme with modern fluid animations, semi-transparent frosted cards (`blur` + `border`), and dynamic dark ambient backgrounds.
- **Confirmed Decision 2 (Navigation Layout)**: Preserved Max Stream navigation system (Bottom Navigation bar maintaining existing screen states and CineHub view switchers).
- **Confirmed Decision 3 (Stream Link Extraction & Player Launch)**: Interactive bottom sheet displaying extracted streams with server names, resolution/quality tags, subtitle tracks, and automatic selection of the highest quality stream.

---

## 1. Overview & Core Concept

Max Stream embeds the headless **CloudStream 3 engine** directly into a native Android architecture with **CineHub Compose UI** and the **MPV REX Player**.

- **Core Headless Engine**: 100% authentic CloudStream 3 SDK (`com.lagradost.cloudstream3.*` and `com.lagradost.api.*`), maintaining full ABI compatibility with dynamic `.cs3` community extensions.
- **CineHub Experience**: Glassmorphic Compose interface powering home feed discovery, multi-provider concurrent search, rich media detail screens, season/episode drawers, and extension sync.
- **MPV REX Player**: Native C/C++ libmpv hardware-accelerated media player handling video streaming, HLS/DASH manifest playback, subtitle rendering, and custom HTTP headers.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Home Discovery**: View trending lists, continue watching rail, and active provider feeds rendered with frosted glass cards (`Surface` with low alpha background + subtle border highlights).
2. **Concurrent Multi-Provider Search**: Real-time search across all enabled CloudStream providers (`APIHolder.apis`), merging provider responses smoothly with staggered entrance animations.
3. **Media Details & Season/Episode Drawer**: Display poster backdrop, metadata chips (quality, rating, genres), plot synopsis, and collapsible season/episode selectors.
4. **Stream Link Extraction Sheet**: When an episode or movie is selected, slide up a Glassmorphic Bottom Sheet displaying extracted `ExtractorLink` mirrors, server latency, resolution badges (1080p, 4K, 720p), and subtitle options.
5. **REX Player Activity Hand-off**: Fire explicit `Intent` to `PlayerActivity` with stream URL, HTTP headers (User-Agent, Referer, Cookies), subtitle links, and title metadata for zero-buffering hardware playback.

### Visual Identity & Theme
- **Theme**: Luxury Dark Ambient with Frosted Glassmorphism.
- **Color Tokens**:
  - Background: `#0B0E14` (Deep Ambient Void)
  - Surface Glass: `Color(0x1A202C70)` with `blur(16.dp)` and `BorderStroke(1.dp, Color(0x33FFFFFF))`
  - Accent Primary: `#6366F1` (Indigo Glow)
  - Accent Secondary: `#10B981` (Emerald Stream)
- **Typography**: Clean display hierarchy (`Typography.titleLarge`, `headlineMedium`) paired with high-contrast body text for max readability on TV/Mobile displays.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Full In-Place Engine vs Stubbing**
  - *Chosen Approach*: Maintain full authentic CloudStream SDK classes in `com.lagradost.cloudstream3.*`.
  - *Why*: Guarantees zero `IncompatibleClassChangeError` or `NoSuchMethodError` when loading precompiled `.cs3` plugins from community repos.

- **Decision 2: Intent Activity Hand-off for REX Player**
  - *Chosen Approach*: CloudStream resolves stream links -> `Intent` -> `PlayerActivity` (MPVLib).
  - *Why*: Isolates player UI/lifecycle from network scraping, enabling hardware acceleration and MPV options without UI thread blocking.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CineHub Compose UI                              │
│  [ Home Feed ]    [ Search Screen ]    [ Details View ]    [ Extensions] │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        CineHubViewModel                                │
│   - searchContent(query) -> APIRepository.search()                     │
│   - loadMediaDetails(url) -> APIRepository.load()                      │
│   - getStreamLinks(episodeData) -> APIRepository.loadLinks()           │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 CloudStream 3 Core Headless Engine                     │
│   - APIHolder (Registry)            - PluginRuntime (Loader)           │
│   - MainAPI / MainAPIKt (Contracts) - NiceHttp / CloudflareKiller      │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │ ExtractorLink + Headers
                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Standalone REX Player Activity                     │
│   - PlayerActivity (MPVLib Engine) - Hardware Decoders / Subtitles     │
└─────────────────────────────────┬──────────────────────────────────────┘
```

### Component & State Mapping
- `CineHubSearchScreen`: Observes `searchResults` and `isSearching` StateFlows; renders glassmorphic search bar and grid items.
- `CineHubDetailsScreen`: Displays `selectedMediaDetails` (`MovieLoadResponse` or `TvSeriesLoadResponse`), episode list cards, and triggers `getStreamLinks`.
- `StreamExtractionBottomSheet`: Displays extracted `ExtractorLink` instances with quality badges and subtitle options.
- `RexPlayerBridge`: Prepares intent extras (`EXTRA_STREAM_URL`, `EXTRA_HEADERS`, `EXTRA_SUBTITLES`) and launches `PlayerActivity`.
