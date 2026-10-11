# Architectural Plan: Fix Cine Details Screen (Seasons & Episodes) and Clean CineHub Top Bar

## Problem Diagnosis

1. **Missing Seasons & Episodes in Details View**:
   - In `CineHubScreen.kt` (`loadExtensionItemDetails`), when an extension API does not return a direct response and falls back to fallback metadata, or returns generic results, it defaulted to `MovieLoadResponse` regardless of whether `fallbackType` was `TvType.TvSeries` or if the item was identified as a TV show.
   - When users click on TV show posters in search results or discovery cards, `fallbackType` was not propagated through all fallback paths, resulting in `isMovie = true` and completely hiding the Seasons & Episodes UI.
   - In `CineDetailView`, the Seasons & Episodes loading logic only activated under specific sub-branches (`item is TvShowItem` with local folders, or `item is ExtensionMediaDetails && loadResp is TvSeriesLoadResponse`). Other TV show types or items without pre-populated episodes showed no seasons or fallback episode queries.
   - When a TV show has 0 pre-populated episodes in the provider load response, it did not dynamically query `CineOnlineScraper.fetchTvShowDetails` and `CineOnlineScraper.fetchTvShowEpisodes`, leaving the episode section blank.

2. **Top Bar Clutter & Overcrowding**:
   - The CineHub `TopAppBar` had 5 crowded elements crammed into a small action row: a large `MaxStreamActiveProviderSelector` chip, Manage Extensions button, My Media Hub button, and Account Profile button.
   - On standard mobile viewports, this crushed the title or spilled over, creating an unsightly, misaligned top bar.

---

## User Direction & Alignment

Based on user feedback:
- **Top Bar**: Clean compact title with a combined overflow menu for secondary actions, and cleaner provider selector placement.
- **Cine Details Screen**: Full-screen modal overlay with a dedicated back button, displaying movies as movies and TV shows as TV shows with interactive season tabs, episode list, episode thumbnails/plots, and direct stream links.

---

## Proposed Changes

### 1. `CineHubScreen.kt` - Fix Fallback & Media Type Resolution in `loadExtensionItemDetails`
- Ensure that if `fallbackType == TvType.TvSeries` or `fallbackType == TvType.Anime`, the fallback creates a `TvSeriesLoadResponse` with an empty episode list rather than falling back to `MovieLoadResponse`.
- Preserve show/movie identity so `isMovie` correctly evaluates to `false` for TV shows.

### 2. `CineHubScreen.kt` - Clean Compact TopAppBar with Overflow Menu
- Redesign the `TopAppBar` in `CineHubScreen`:
  - **Title Area**: Sleek MaxStream icon + "CineHub" title in a compact, well-spaced format.
  - **Action Area**: 
    - Active Provider badge/chip (compacted).
    - User Profile avatar / account button.
    - Combined overflow menu (`IconButton` with `Icons.Default.MoreVert`) containing:
      - "Manage Extensions" (with `Icons.Default.Extension`)
      - "My Media Hub" (with `Icons.Rounded.Bookmark`)
      - "Scrape Online Metadata" (Kodi scraper dialog)

### 3. `CineHubScreen.kt` - Full-Screen Modal Overlay & Dedicated Back Button for `CineDetailView`
- Add a dedicated circular glass Back / Close button (`Icons.AutoMirrored.Filled.ArrowBack` or `Icons.Default.Close`) in the top-left of the detail view with `statusBarsPadding()` and generous touch target.
- Add `BackHandler` so system back gestures naturally dismiss the detail sheet overlay.
- Display a dedicated full-screen presentation that covers the top bar cleanly without messy z-index overlaps.

### 4. `CineHubScreen.kt` - Unified TV Show Seasons & Episodes Resolution Engine
- For **any TV Show** item (`TvShowItem`, `TMDBTvNode`, `TvSeriesLoadResponse`, `CineHubSearchItem` with TV type, `ExtensionMediaDetails` with TV type, or any item where `isMovie == false`):
  - Automatically query seasons from `CineOnlineScraper.fetchTvShowDetails(tmdbId, showTitle, context)`.
  - Fetch season episodes using `CineOnlineScraper.fetchTvShowEpisodes(context, tmdbId, selectedSeason, showTitle)`.
  - If the provider already supplied episodes (e.g., from an extension), combine/enrich them with TMDB episode stills, air dates, and overviews.
  - If the provider supplied 0 episodes, render the TMDB episodes with stream scraper resolution when clicked.
  - Provide season filter tabs (`Season 1`, `Season 2`, ...) in a horizontal scrollable row.
  - Render modern episode cards showing:
    - Episode thumbnail / still image.
    - Episode number and title.
    - Air date and overview snippet.
    - Play button to stream that specific episode.

---

## Verification Plan

### Automated Verification
- Run `compile_applet` to ensure full Kotlin compilation and zero syntax/type errors.

### Manual / CUJ Verification
1. Click a TV show poster from search results (e.g. from extensions or discovery).
2. Confirm the details screen opens as a full-screen overlay with a clear Back button.
3. Confirm Season selector tabs appear (`Season 1`, `Season 2`, etc.).
4. Confirm Episodes list appears with episode title, preview thumbnail, and play action.
5. Click a Movie poster and confirm it opens in movie mode with Instant Play and movie details.
6. Check the CineHub home screen top bar: confirm it is clean, uncrowded, with compact title and overflow menu.
