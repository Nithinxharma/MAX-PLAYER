# Architectural Plan: Redesign CineDetailBottomSheet (Unified Seasons, Episodes & Links)

This plan addresses the 3 main issues in `CineDetailBottomSheet`:
1. Missing season selector tabs across various media item types (`TMDBTvNode`, `SearchResponse`, `CineHubSearchItem`, etc.).
2. Missing episode cards list with preview stills, titles, plots, and play/download actions.
3. Missing stream link extraction list / quality chips.

---

## Proposed Changes

### `CineHubScreen.kt` / `CineDetailView`

#### 1. Unified Media Resolver Engine
- Create a unified `CineDetailState` that normalizes any incoming item type (`TMDBTvNode`, `TMDBMovieNode`, `TvShowItem`, `MovieItem`, `SearchResponse`, `CineHubSearchItem`, `ExtensionMediaDetails`, `LoadResponse`).
- For any TV Show item type, automatically query TMDB or Provider details in background to resolve all available seasons (`Season 1, Season 2, ...`).
- Automatically fetch and display episodes for the selected season with fallback to local scanned episodes or TMDB episode enrichment.

#### 2. Redesigned Modern Hero Sheet Layout
- **Hero Backdrop Header**: Full-width backdrop poster with dark gradient scrim, title, release year, rating badge, genres, and primary Play / Download buttons.
- **Season Selection Chips**: Horizontal `LazyRow` of frosted glass chips allowing smooth switching between `Season 1`, `Season 2`, etc.
- **Episode List Cards**: Clean card list showing:
  - Episode still image / thumbnail with play icon overlay.
  - Episode label (`S1 • E1`) and title.
  - Overview / plot summary.
  - Quick Play and Download action buttons per episode.
- **Stream Link Chips**: Interactive stream extraction chips when links are fetched, allowing direct playback or quality selection.

---

## Verification Plan

### Automated Build
- Run `compile_applet` to confirm clean compilation with Jetpack Compose.

### Behavior Verification
1. Open a TV show from TMDB, Search, or Extension Providers in `CineDetailBottomSheet`.
2. Confirm season tabs appear (`Season 1`, `Season 2`, etc.).
3. Confirm episode cards load with thumbnails, titles, plots, and play buttons.
4. Click Play on an episode to extract and display stream links / launch video player.
