# Implementation Plan: MaxStream Glassmorphic Home Page & CloudStream-Inspired Bottom Details Sheet

Upgrade MaxStream's Home Page UI and Bottom Details View Sheet by combining CloudStream's core operational capabilities (provider switching, resume progress, category shelves, season/episode selectors, stream link extraction) with a modern **Glassmorphic Design System** (frosted glass blurs, subtle translucent borders, dynamic gradient highlights, and glowing pill chips).

---

## Proposed Architecture & UI/UX Upgrades

### 1. Glassmorphism Design System Framework (`xyz.mpv.rex.ui.theme.Glassmorphism`)
- **Glass Surfaces & Blur Wrappers**: Create reusable Composables (`GlassCard`, `GlassSurface`, `GlassPillChip`, `GlassButton`, `GlassModalBottomSheet`) utilizing Jetpack Compose `graphicsLayer` alpha, translucent background brushes, and render-effect blurs (`Modifier.blur(...)` with fallback border strokes for smooth rendering across Android versions).
- **Glass Palette**: Frosted glass containers using dynamic `MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)` paired with white/accent multi-layer borders `BorderStroke(1.dp, Brush.linearGradient(...))`.

---

### 2. Revamped Glassmorphic Home Page (`xyz.mpv.rex.ui.browser.cinehub.CineHubScreen.kt`)
- **Active Provider Selector Header**:
  - Glass drop-down chip at the top allowing instant switching between active CloudStream extension providers (e.g., Internet Archive, YouTube, custom DEX extensions).
  - Search trigger chip with quick query focus and layout toggle.
- **Hero Backdrop Carousel**:
  - Auto-scrolling horizontal banner featuring top trending media with high-resolution fanart/backdrops.
  - Overlayed with frosted glass info card containing title, genre tags, IMDb rating badge, and a glowing "Watch Now" glass action button.
- **"Resume Watching" Glass Shelf**:
  - Dedicated shelf showing recently watched titles with exact playback progress bar indicators and timestamp labels.
  - Tap-to-resume immediately launches MPV player engine at stored position.
- **Horizontal Category Shelves**:
  - CloudStream-style category rows ("Popular", "Trending Now", "Action", "Recently Added", "Top Rated").
  - Media poster cards encased in subtle glass frame outlines with quick bookmark and rating overlays.

---

### 3. Glassmorphic CloudStream Bottom Details View Sheet (`xyz.mpv.rex.ui.browser.cinehub.CineDetailScreen.kt` / `GlassCineDetailBottomSheet`)
- **Modal Glass Sheet Container**:
  - Glassmorphic modal sheet with frosted dark backdrop blur, smooth rounded top corners, and drag handle.
- **Media Header & Quick Info**:
  - Full-width backdrop banner fading seamlessly into frosted glass body.
  - Poster preview with metadata badges (Quality 4K/HD, Year, Duration, Age Rating, Genres, Star Rating).
  - Action row: Glowing "Quick Play" primary glass button, "Trailer" secondary button, and "Bookmark / Favorite" toggle.
- **CloudStream Season & Episode Selector Carousel**:
  - Season dropdown / tab selector pill row.
  - Horizontal scrollable episode cards with thumbnail preview, title, duration, watched progress indicator, and play icon.
- **Stream Extraction & Source Chips**:
  - Real-time link extraction section presenting resolved stream mirrors as glass filter chips with resolution (1080p, 720p, 4K), audio channels, extractor source name, and response latency indicators.
  - Tapping a stream chip directly streams the media via the MPV engine with custom stream headers (`Referer`, `User-Agent`).

---

## Step-by-Step Implementation Steps

### Phase 1: Reusable Glassmorphic Components
1. **`GlassComponents.kt`**: Implement `GlassSurface`, `GlassCard`, `GlassChip`, `GlassButton`, and `GlassTextField` with translucent gradient borders and backdrop blur effects.

### Phase 2: Refactor Home Screen Layout
1. Update `CineHubScreen.kt` / `HomeScreen.kt` with:
   - Provider Selector Header Bar
   - Hero Carousel Composable with Glass Card overlay
   - Resume Watching horizontal row with progress indicators
   - Responsive horizontal poster rows with glass card styling

### Phase 3: Build Glassmorphic Bottom Details Sheet
1. Refactor `CineDetailScreen.kt` or introduce `GlassCineDetailBottomSheet.kt` using Compose `ModalBottomSheet` or custom animated sliding sheet.
2. Integrate CloudStream media detail metadata binding:
   - Episode/Season carousels
   - Extractor stream links state management (Loading, Mirror list, Error retry)
   - MPV engine launch trigger passing headers and selected source URL

### Phase 4: Verification & Build
1. Build applet via `compile_applet`.
2. Ensure no UI regressions, verify smooth sheet animation, touch target sizes (>48dp), and glass visual harmony.

---

## Verification Plan

- **Compilation**: Run `compile_applet` to confirm build passes without syntax or Kotlin Compose type errors.
- **UI Integrity**: Verify edge-to-edge layout, status bar insets, glass readability, and high contrast against dark backgrounds.
- **Interactive Testing**: Verify bottom sheet opening, season/episode switching, and stream link extraction triggers.
