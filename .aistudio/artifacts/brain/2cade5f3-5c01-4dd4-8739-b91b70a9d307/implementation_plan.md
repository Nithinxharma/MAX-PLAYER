# Implementation Plan: Redesign & Remaster Home, Search & Details with Deep AMOLED Glassmorphism & CloudStream Stream Extraction

This plan details the complete redesign and remastering of the **HomeScreen**, **SearchScreen**, and **DetailsScreen** in MaxStream CloudStream. It fixes broken search query flows, poster click routing, and stream extraction logic while establishing a deep dark AMOLED Glassmorphism UI aesthetic.

---

## Architecture & Proposed Changes

### 1. Unified Search & Multi-Provider Scraping (`CineHubSearchScreen.kt` & `CineHubViewModel.kt`)
- **Multi-Provider Concurrent Scraping**: Query all active registered CloudStream providers in parallel (`APIHolder.apis`) using debounced user queries.
- **Unified Results Grid**: Display search results in a single, responsive grid with provider source badges (`SuperStream`, `Sflix`, `FlixHQ`, etc.), video quality tags (`4K`, `HD`, `FHD`), and release year.
- **Debounced Input & Quick Filters**: Provide real-time typing response with instant search filter chips (`All`, `Movies`, `TV Series`, `Anime`, `Live TV`).

### 2. Glassmorphic Media Details & Immediate Stream Resolver (`GlassmorphicMediaDetailSheet.kt` & `CloudStreamBridgeResolver.kt`)
- **Deep AMOLED Visual Styling**: AMOLED dark surface (`#07080D`), frosted glass blur cards, luminous border accents, and hero backdrop gradient scrims.
- **Movie Flow (1-Click Direct Playback)**:
  - Automatically load `MovieLoadResponse` details and query all 140+ registered `DefaultExtractors`.
  - Display resolved stream sources (e.g., `StreamWish 1080p`, `DoodStream 720p`, `FileMoon Auto`).
  - Provide a prominent glassmorphic **"Watch Now"** primary button that immediately launches the MPV player (`MediaUtils.playFile`).
- **TV Show Flow (Multi-Episode Navigation)**:
  - Support `TvSeriesLoadResponse` and `AnimeLoadResponse`.
  - Render Season selector tabs/chips and scrollable Episode cards with titles, season/episode numbers, and release dates.
  - Tapping any episode resolves its stream links and starts playback seamlessly.

### 3. Home Screen Glassmorphic Layout (`CineHubScreen.kt` & `CineHubUnifiedMediaScreen.kt`)
- **Remastered Home Sections**: Hero movie carousel with glowing glass play actions, Continue Watching rail, Trending & Popular rails across providers, and direct poster click handling.
- **Seamless Poster Click Routing**: Clicking any movie or TV poster opens the Glassmorphic Details Sheet directly with full stream resolution and cast details.

---

## Verification Plan

### Automated Build Verification
1. Run `compile_applet` to confirm clean compilation across all Kotlin files without errors.

### Functional Verification
1. **Search Screen Test**: Perform multi-provider searches and verify unified grid results with provider source badges and media quality tags.
2. **Movie Details & Stream Test**: Tap a movie poster, confirm the Glassmorphic Details Sheet opens with resolved working stream links, and tap "Watch Now" to launch MPV player.
3. **TV Show Details & Episode Test**: Tap a TV show poster, test season tabs and episode cards, and confirm episode stream extraction and playback.
