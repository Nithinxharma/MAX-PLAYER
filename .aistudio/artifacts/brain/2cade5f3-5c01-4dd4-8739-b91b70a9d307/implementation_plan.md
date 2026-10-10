# Implementation Plan: Screen Cleanup, Extension Consolidation, and Codebase Optimization

This plan details the removal of unneeded/redundant UI screens (Shorts, Ytdl, Recently Played, MaxStreamSeeAllSheet, CineDetailScreen), consolidation of Extension & Repository management into a single tabbed view, and optimization of `SplashScreen.kt` and `CineHubUnifiedMediaScreen.kt`.

---

## 1. Extension & Provider Management Consolidation
- **Unified Extension Hub (`InstalledExtensionsScreen.kt`)**:
  - Add top scrollable/segmented tabs: **Installed Extensions**, **Repositories**, and **Preset Repositories**.
  - Move repository URL management and repository preset installation into `InstalledExtensionsScreen.kt`.
  - Deprecate standalone routes (`ExtensionRepositoriesScreenRoute.kt`, `RepositoryPresetsScreenRoute.kt`, `ExtensionPreferencesScreenRoute.kt`) and redirect all extension navigation directly to `InstalledExtensionsScreenRoute`.
  - Clean up redundant standalone repository files (`ExtensionRepositoriesScreen.kt`, `RepositoryPresetsScreen.kt`).

---

## 2. Removal of Redundant & Unused UI Screens & Navigation Entries
- **Shorts Module Removal**:
  - Delete `ShortsScreen.kt`, `ShortsViewModel.kt`, `ShortsPreferencesScreen.kt`, and `BlockedShortsScreen.kt`.
  - Remove Shorts tab from `MainScreen.kt` bottom navigation bar and navigation graph.
  - Remove Shorts & Blocked Shorts preferences entries from `PreferencesScreen.kt`.
- **Ytdl Settings & Recently Played Removal**:
  - Delete `YtdlSettingsScreen.kt` and remove Ytdl configuration entries from `PreferencesScreen.kt`.
  - Delete `RecentlyPlayedScreen.kt` and `RecentlyPlayedViewModel.kt`, and remove Recently Played options from MainScreen/Library navigation.
- **Redundant Details & Sheet Removal**:
  - Delete `CineDetailScreen.kt` (since `CineHubScreen.kt` contains the unified `CineDetailView` with inline stream resolution, episode ranges, and watch status).
  - Delete `MaxStreamSeeAllSheet.kt` and route "See All" actions to `CineHubSearchScreen` or inline section grids.

---

## 3. Screen Optimization
- **`SplashScreen.kt` Optimization**:
  - Streamline initial boot logic, remove unnecessary delays/animations, and ensure fast transition directly to `MainScreen`.
- **`CineHubUnifiedMediaScreen.kt` Optimization**:
  - Clean up unused imports, dead layout state, and streamline the library grid rendering logic.
- **Preferences & Main Navigation Cleanup**:
  - Remove all broken or removed screen references from `PreferencesScreen.kt` menu items and `MainScreen.kt` routes.

---

## Verification & Build Plan
1. Execute file removals and code modifications across all affected packages.
2. Verify with `compile_applet` to ensure zero compilation errors or broken route references.
3. Verify that navigation remains clean, fluid, and responsive.
