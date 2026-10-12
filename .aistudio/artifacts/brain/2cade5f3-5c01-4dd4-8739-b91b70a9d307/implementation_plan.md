# Implementation Plan: Max Stream Liquid Glass & Glassmorphism UI Overhaul

This plan outlines the end-to-end transformation of the **Max Stream** Android app (featuring **CineHub** Compose UI and **REX Player** MPV engine) into a modern, immersive **Liquid Glass / Glassmorphic** UI powered by **PrismalAGSL** and **Prismal**.

---

## 1. Overview & Architectural Strategy
The objective is a 100% redesigned UI with no legacy, flat elements remaining. The liquid glass design relies on:
- **PrismalAGSL** (`AGSL` runtime shaders) for dynamic refractive glass effects, chromatic dispersion, and fluid specular highlights across Jetpack Compose components.
- **Prismal** (OpenGL ES shader pipeline) for hardware-accelerated video overlay rendering and HUD effects over the MPV surface in **REX Player**.
- A unified design system with **Deep Neon Cyan / Prism Electric Blue** and **Cyberpunk Magenta** chromatic highlights on AMOLED black surfaces (`#000000`).

---

## 2. Gradle & Dependency Configuration

### A. Repository & Library Declaration
Add JitPack repository and the Prismal suite to `settings.gradle.kts` and `app/build.gradle.kts`.

1. **`settings.gradle.kts`**:
   ```kotlin
   dependencyResolutionManagement {
       repositories {
           mavenCentral()
           google()
           maven { url = uri("https://jitpack.io") }
       }
   }
   ```

2. **`gradle/libs.versions.toml`**:
   ```toml
   [versions]
   prismal = "1.0.0" # Latest stable release

   [libraries]
   prismal-agsl = { module = "com.github.styropyr0:PrismalAGSL", version.ref = "prismal" }
   prismal-core = { module = "com.github.styropyr0:Prismal", version.ref = "prismal" }
   ```

3. **`app/build.gradle.kts`**:
   ```kotlin
   dependencies {
       implementation(libs.prismal.agsl)
       implementation(libs.prismal.core)
   }
   ```

---

## 3. Design System & Reusable Liquid Glass Components

We will build a dedicated package `xyz.mpv.rex.ui.theme.glass` containing core liquid glass primitives:

### Key Components to Create:
1. **`LiquidGlassSurface`**:
   - Base wrapper composable leveraging AGSL runtime shaders (`PrismalAGSL`) for dynamic refraction, customizable blur radius, edge highlights, and specular light source tracking.
   - Graceful dynamic fallback for pre-Android 13 devices using hardware layer blurs (`RenderEffect`) and semi-transparent acrylic gradients.

2. **`GlassCard`**:
   - Multi-layered card container with dynamic border stroke gradients (Cyan to Magenta), inner shadow refraction, and touch-responsive light shimmer.

3. **`GlassSearchBar`**:
   - Frosted input container with animated glow on focus, clear glass action buttons, and integrated provider filter chips.

4. **`GlassBottomNavBar`**:
   - Floating pill-shaped glass navigation bar with active tab glow indicator, fluid tab transition animations, and background backdrop blur.

5. **`GlassButton`**:
   - Primary and secondary liquid glass action buttons with ripple refraction and vibrant gradient borders.

6. **`GlassChip`**:
   - Frosted pill tags for genres, media type (Movie/TV), quality tags (4K/1080p), and provider source indicators.

---

## 4. Screen-by-Screen Redesign Strategy

### Screen 1: Home Screen (`CineHubScreen.kt`)
- **Hero Carousel**: Full-bleed backdrop with liquid glass overlay card at the bottom containing title, description, match score, and glowing `GlassButton` ("Watch Now").
- **Movie/TV Poster Cards**: Replaced standard cards with `GlassCard` elevation. Posters will feature translucent gradient overlays, provider badge chips, and glowing border highlights on focus/hover.
- **Navigation Bar**: Floating `GlassBottomNavBar` centered at screen bottom, elevating off the page with frosted blur.

### Screen 2: Search Screen (`CineHubSearchScreen.kt`)
- **Glass Search Bar**: Integrated sticky header with `GlassSearchBar`, voice input icon, and multi-provider state indicators.
- **Filter Chips**: Horizontal scrollable `GlassChip` row for quick toggling between Movie, TV Series, Anime, and active CloudStream providers.
- **Unified Search Grid**: 3-column grid of liquid `GlassCard` items displaying search results across all scrapers simultaneously, complete with provider origin tags.

### Screen 3: Details Screen (`GlassmorphicMediaDetailSheet.kt`)
- **Backdrop & Header**: Blurred hero backdrop image with fluid parallax scrolling into a floating glass details sheet.
- **Synopsis & Info Card**: `GlassCard` holding overview text, cast avatars, rating badges, and resolution indicators.
- **Season/Episode Selector**: Translucent tab bar for seasons, leading into a list of episode cards with thumbnail preview, title, duration, and direct stream resolver status.

### Screen 4: REX Player Screen (`PlayerActivity.kt` / MPV Overlay)
- **Ultra-Light Liquid Glass HUD**: Auto-hiding HUD (3-second timeout) rendered with hardware-accelerated translucent glass overlays.
- **Playback Controls**: Central floating `GlassButton` play/pause/seek controls with neon cyan/magenta halo glow.
- **Scrubber Bar**: Custom liquid glass progress bar with glowing position handle and video preview thumbnail card.
- **Track Sheet**: `GlassmorphicSheet` for audio stream and subtitle track selection with real-time selection feedback.

---

## 5. Performance & Compatibility Optimization

1. **Hardware Acceleration**:
   - AGSL shaders operate directly on GPU graphics pipelines on Android 13+ (API 33+).
   - Pre-Android 13 devices dynamically switch to optimized `RenderEffect` / hardware layer matrix blurs to guarantee 60 FPS video playback without dropped frames.

2. **Overlay Optimization in REX Player**:
   - HUD overlays utilize dedicated View-based OpenGL ES layers (`Prismal`) or lightweight Compose `GraphicsLayer` invalidate-on-change modifiers to avoid unnecessary recompositions during MPV video rendering.

3. **Memory Management**:
   - Reuse shader instances and cached bitmap surfaces in `LiquidGlassSurface` to prevent allocations during scroll gestures.

---

## 6. Phased Implementation Roadmap

- **Phase 1: Foundation & Core Glass Design System**
  - Configure Gradle dependencies and repository setups.
  - Create core `xyz.mpv.rex.ui.theme.glass` component suite (`LiquidGlassSurface`, `GlassCard`, `GlassSearchBar`, `GlassBottomNavBar`, `GlassButton`).

- **Phase 2: Home Screen Overhaul**
  - Implement liquid hero carousel, glass poster cards, and floating glass bottom navigation bar in `CineHubScreen.kt`.

- **Phase 3: Unified Glass Search Screen**
  - Build `CineHubSearchScreen.kt` with glass search input, filter chips, and unified grid results.

- **Phase 4: Glassmorphic Details Sheet**
  - Update `GlassmorphicMediaDetailSheet.kt` with backdrop parallax, synopsis glass cards, and season/episode selector.

- **Phase 5: REX Player Liquid HUD & Controls**
  - Refactor `PlayerActivity` controls overlay with 3-second auto-hiding glass HUD, video scrubber, and audio/subtitle track selector sheet.

- **Phase 6: Compilation, Verification & Fine-Tuning**
  - Compile applet, test smooth rendering, verify performance across screens, and confirm zero build or runtime errors.
