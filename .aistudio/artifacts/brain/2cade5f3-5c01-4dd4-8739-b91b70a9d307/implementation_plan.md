# Restore Working CineDetailBottomSheet & Media Details View

Restore the proven working `CineDetailView`, `CineDetailBottomSheet`, and associated media sheets (`QualitySelectorBottomSheet`, `StreamDiagnosticCard`, `KodiScraperBottomSheet`, `ProviderSelectorSheet`) from the user's provided code across all CineHub screens.

## User Review & Critical Decisions

> [!IMPORTANT]
> The user provided the complete source code of the last known working `CineDetailView` / `CineDetailBottomSheet` implementation. This plan restores this exact implementation and integrates it across all screens.

- **Confirmed Decision**: Revert `CineDetailBottomSheet` / `CineDetailView` to the user's attached implementation containing 16:9 YouTube-style banner, TMDB/Scraper metadata, Top Cast carousel, Library dropdown menu, TV episode browser with season selector, stream quality sheet, and failure diagnostics card.

## 1. Overview & Core Concept

- **What It Does**: When any movie, TV show, or clip poster is selected in CineHub (Home, Search, or Library/Unified Media screens), it presents the full-featured liquid glass media detail view/sheet with complete playback capabilities, episode extraction, provider selection, and stream link quality options.
- **Key Value**: Guarantees rich, interactive metadata and 1-tap stream playback without missing controls, blank sheets, or broken episode lists.

## 2. User Experience & Visual Design

- **YouTube-Style 16:9 Banner Header**:
  - Instant Play button with animated progress indicator during link resolution.
  - Top right 1080p HD quality badge and status pill.
- **Rich Media & Metadata Layout**:
  - Poster thumbnail, title, TMDB badge, release year, genre tag, and rating stars.
  - Expandable overview/synopsis text.
  - Top Cast carousel with circular actor avatars and character names.
  - "Add to Library" dropdown menu with watch statuses ("Watchlist", "Watching", "Completed", "Dropped").
- **Movie & TV Show Actions**:
  - Movies: Primary "Play Movie" button + "Scrape Online" metadata enrichment button.
  - TV Shows: Dynamic Season Filter chips ("Season 1", "Season 2"), episode cards with video stills, season/episode labels, synopses, and play triggers.
- **Stream Link Quality & Failure Diagnostics**:
  - `QualitySelectorBottomSheet`: Select HLS (M3U8) or direct HTTP stream links sorted by resolution.
  - `StreamDiagnosticCard`: Clear root cause diagnosis (DNS ISP blocks, Cloudflare 403, timeout) with retry and error log copy triggers.
- **Floating Glass Top Navigation**:
  - Minimize button (`KeyboardArrowDown`) and swipe-down drag handle.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Restoring Attached `CineDetailView` Implementation**
  - *Chosen Approach*: Replace the current bottom sheet logic with the attached `CineDetailView` and `CineDetailBottomSheet` composables.
  - *Why*: Ensures 100% feature parity with the last fully-working version, restoring episode browsing, online scraping, TMDB metadata enrichment, and stream quality selection.
- **Decision 2: Consistent Wiring Across All CineHub Entry Points**
  - *Chosen Approach*: Wire `CineDetailBottomSheet` / `CineDetailView` in `CineHubScreen.kt`, `CineHubSearchScreen.kt`, and `CineHubUnifiedMediaScreen.kt`.
  - *Why*: Provides uniform UX regardless of whether a user clicks a movie on the home carousel, search results grid, or library watchlist.

## 4. Technical Architecture & Component Structure

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CineHub Media Detail Flow                       │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  [ Home Carousel / Search Grid / Watchlist Card ]                      │
│                          │                                             │
│                          ▼ (onItemClick)                               │
│              [ CineDetailBottomSheet / Screen ]                        │
│                          │                                             │
│                          ▼                                             │
│                   [ CineDetailView ]                                   │
│    ┌─────────────────────┼─────────────────────┐                       │
│    │                     │                     │                       │
│    ▼                     ▼                     ▼                       │
│ [ 16:9 Banner ]  [ Meta / Cast ]     [ Season & Episode ]              │
│  Instant Play      TMDB Enrichment     Online Scrape & Eps             │
│    │                     │                     │                       │
│    └─────────────────────┼─────────────────────┘                       │
│                          │                                             │
│                          ▼                                             │
│         [ Stream Extraction / Provider Stream ]                        │
│             /                        \                                 │
│            ▼                          ▼                                │
│ [ QualitySelectorSheet ]     [ StreamDiagnosticCard ]                  │
│    (Multi-quality links)         (ISP / Cloudflare / DNS Error)        │
│            │                          │                                │
│            └─────────────┬────────────┘                                │
│                          ▼                                             │
│               [ REX MPV Player Launch ]                                │
└────────────────────────────────────────────────────────────────────────┘
```

## Implementation Steps

1. **Audit & Restore `CineDetailView` & `CineDetailBottomSheet`**:
   - Restore `CineDetailView`, `CineDetailBottomSheet`, `QualitySelectorBottomSheet`, `StreamDiagnosticCard`, `KodiScraperBottomSheet`, and `ProviderSelectorSheet` in `CineHubScreen.kt` using the attached code.
2. **Restore State Holders**:
   - Verify `CineDetailScreen` and `CineDetailStateHolder` for full-screen handoff if used.
3. **Wire Event Handlers**:
   - Ensure poster clicks in `CineHubScreen`, `CineHubSearchScreen`, and `CineHubUnifiedMediaScreen` open `CineDetailBottomSheet` / `CineDetailView`.
4. **Verification**:
   - Compile the project using `compile_applet` to confirm clean build.
