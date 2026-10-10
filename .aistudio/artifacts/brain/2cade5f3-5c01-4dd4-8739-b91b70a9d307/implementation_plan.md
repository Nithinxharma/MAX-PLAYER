# Implementation Plan: CloudStream SDK Integration into MaxStream

This plan details the integration of essential CloudStream SDK features into MaxStream while maintaining the app's signature glassmorphic design system.

---

## 1. Episode & Playback Management System
- **Episode Watch Progress**:
  - Track per-episode playback positions with visual progress bars on episode cards.
  - Quick action to "Mark Watched", "Mark Unwatched", and "Mark Up To Here".
- **Season & Range Pagination**:
  - Horizontal range chips (e.g. `1-25`, `26-50`, `51-100`) for large anime/series with over 50 episodes.
  - Sub/Dub audio track indicator badges on episode items.
- **Batch Downloads & Stream Links**:
  - Batch download queue trigger for seasons or range selection.
  - Quick stream link extractor status chip (showing mirror count and resolution tags).

---

## 2. Glassmorphic Subtitle Customization Engine
- **In-Player Subtitle Styling Sheet**:
  - Custom font selector (Default, Sans-Serif, Serif, Monospace, Condensed).
  - Text color, background color, background corner radius, and edge/outline intensity sliders.
  - Text size offset (`12sp` to `28sp`) and vertical offset controls.
- **Track Selection & Auto-Language**:
  - Subtitle track selector dialog with automatic language preference matching (e.g., preferred English/Japanese subtitles).
  - External SRT/VTT file picker and sync timing adjustment (-5s to +5s offset).

---

## 3. Multi-Tab Library & Cast Cards
- **Multi-Category Library Screen**:
  - Tabs for **Watching**, **Plan to Watch**, **Completed**, **Favorites**, and **Subscriptions**.
  - Sorting options: *Rating*, *Release Date*, *Recently Updated*, and *Alphabetical*.
- **"Pick Random Title" Feature**:
  - Header action button that pops up a glassmorphic modal with a randomized title recommendation from user's watchlist/library.
- **Actor & Voice-Actor Cast Cards**:
  - Horizontal glassmorphic cast row on Media Details sheet and screen showing actor profile avatars, real names, and character roles.

---

## 4. Architectural Integration Plan
1. **Data Layer**:
   - Update `WatchProgressManager` and `UserLibraryRepository` to persist episode watch state and subtitle customization preferences.
2. **UI Layer**:
   - Enhance `CineHubDetailBottomSheet.kt` with cast cards, range pagination, and episode action popups.
   - Update `LibraryScreen.kt` with multi-category tabs, sorting popups, and random picker modal.
   - Create `SubtitleCustomizationSheet.kt` and integrate into player view.

---

## User Review Required

Please review the plan above. Click **Proceed** to start the implementation.
