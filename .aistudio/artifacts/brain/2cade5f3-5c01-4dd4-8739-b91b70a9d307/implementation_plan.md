# Implementation Plan: Max Stream Prismal Ultra Liquid Glass UI Remake

Transform the entire **Max Stream** Android app (including **CineHub** main interface and **REX Player** playback engine) into a unified **Prismal Ultra Liquid Glass** experience. This plan covers a complete, single-batch UI remake across all components, screens, dialogs, overlays, and controls using a hybrid OpenGL ES surface layer paired with AGSL liquid glass shaders and Prismal Compose design system components.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed during the Phase 1 clarification interview and govern the entire UI architecture:

- **Remake Scope**: Full app remake across **all screens, dialogs, sheets, and controls** in a single complete batch (no legacy UI remnants).
- **Prismal Architectural Model**: Hybrid integration combining an `AndroidView` OpenGL ES surface overlay/backdrop with high-performance `PrismalAGSL` Compose shaders and Prismal component tokens.
- **Aesthetic Direction**: **Ultra Liquid** theme featuring high-contrast glass refractions, sharp chromatic dispersion edges, deep neon cyan (`#00F2FE`) and electric blue (`#4FACFE`) light sweeps, and hardware-accelerated fallbacks for high performance.

---

## 1. Overview & Core Concept

The **Max Stream** application will be completely revamped with the **Prismal** open-source glass design language. Every screen—from the CineHub Home hero carousel, Search filters, Details sheets, Downloads, Sync, and Settings, to the REX Player HUD, transport controls, and track selection sheets—will strictly implement Prismal liquid glass physics, frosted specular highlights, and cohesive glass geometry.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Max Stream Prismal Architecture                  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │  Jetpack Compose Prismal UI Components (Cards, Buttons, Nav)   │   │
│   └──────────────────────────────┬─────────────────────────────────┘   │
│                                  │                                     │
│   ┌──────────────────────────────▼─────────────────────────────────┐   │
│   │   PrismalAGSL Shader Engine (Chromatic Dispersion & Refraction)  │   │
│   └──────────────────────────────┬─────────────────────────────────┘   │
│                                  │                                     │
│   ┌──────────────────────────────▼─────────────────────────────────┐   │
│   │   AndroidView OpenGL ES Prismal Backdrop Surface (Live Glow)    │   │
│   └────────────────────────────────────────────────────────────────┘   │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Home Screen (CineHub)**: High-impact hero carousel floating over an animated liquid mesh backdrop, with Ultra Liquid glass poster cards, glass section headers, and a floating glass bottom navigation bar.
2. **Search Screen**: Glass search bar with live refractive focus ring, liquid filter chips with active specular glow, and an aligned media poster grid.
3. **Media Details Sheet**: Parallax media backdrop, translucent glass synopsis card, floating play/bookmark glass buttons, and a liquid season/episode selector.
4. **REX Player Overlay**: Ultra-light liquid glass HUD that auto-hides after 3 seconds, floating glass transport controls with ripple feedback, custom glass seekbar with glowing scrubber thumb, and glass track sheets (Audio, Subtitles, Quality).
5. **Secondary Screens (Downloads, Cloud Sync, Settings, Auth)**: Fully converted to Prismal glass cards, liquid list tiles, glass switch toggles, and frosted dialog surfaces.

### Visual Identity & Theme Tokens
- **Primary Refraction Tint**: `Color(0x3812131D)` (Deep Translucent Obsidian)
- **Specular Border Accent**: `Color(0x40FFFFFF)` to `Color(0x8000F2FE)` (Gradient Cyan Glow)
- **Primary Glow**: `#00F2FE` (Neon Cyan) & `#4FACFE` (Electric Blue)
- **Surface Geometry**: Continuous rounded corners (`18.dp` to `28.dp`), 1.5.dp specular borders, and dynamic noise texture overlays.

---

## 3. Comprehensive Component & Screen Remake Strategy

### Core Reusable Prismal Component Library
- **`PrismalAGSLShader.kt`**: AGSL RuntimeShader providing true physical chromatic dispersion, noise grain, and light sweeps on Android 13+, with 60 FPS fallback shaders for older devices.
- **`GlassModifier.kt`**: Universal `prismalLiquidGlass()` modifier applying refractive borders, surface elevation, and background distortion to any Compose node.
- **`MaxStreamGlassComponents.kt`**: Standardized `GlassCard`, `GlassButton`, `GlassSearchBar`, `GlassChip`, `GlassBottomNavBar`, `GlassDialog`, and `GlassTextField`.

### Screen Remake Mapping
1. **CineHub Main Browser (`CineHubScreen.kt`)**: Replaced all flat containers with `PrismalAGSL` glass surfaces, liquid poster cards (`MaxStreamPosterCard`), and a glass floating navigation rail.
2. **Search Engine (`MaxStreamLiquidGlassSearch.kt`)**: Replaced standard input fields with refractive glass search bars and liquid category chips.
3. **Media Details (`GlassmorphicMediaDetailSheet.kt`)**: Enhanced synopsis sheets, episode grids, and provider buttons with full Prismal glass rendering.
4. **REX Player Controls (`PlayerControlsPortrait.kt`, `PlayerControlsLandscape.kt`, `ControlsButton.kt`, `Seekbar.kt`)**: Wrapped transport controls, time labels, and gesture overlays in ultra-light liquid glass HUD layers with 3s auto-fade.
5. **Sheets & Panels (`PlayerSheets.kt`, `PlayerPanels.kt`, `AudioTracksSheet.kt`, `SubtitleTracksSheet.kt`)**: Fully converted modal bottom sheets and side panels to glass dialogs.

---

## 4. Technical Architecture & Verification Plan

### Architecture Diagram
```
┌─────────────────┐       ┌───────────────────────┐       ┌────────────────────┐
│ CineHub / Player│ ───>  │  prismalLiquidGlass   │ ───>  │ PrismalAGSLShader  │
│  Compose Views  │       │       Modifier        │       │ (Android 13+ AGSL) │
└─────────────────┘       └───────────────────────┘       └─────────┬──────────┘
         │                                                          │
         │                                              ┌───────────▼──────────┐
         └────────────────────────────────────────────> │  60 FPS Fallback     │
                                                        │ (RenderEffect / Canvas)│
                                                        └──────────────────────┘
```

### Verification Checklist
1. Build check via `compile_applet` after all file edits to confirm zero syntax or dependency errors.
2. Verify edge-to-edge system insets handling and minimum 48dp touch targets across all Prismal glass buttons.
3. Verify backward compatibility on earlier Android versions without dropping frame rates.
