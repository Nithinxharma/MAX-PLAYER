# MAX STREAM Changelog

All notable changes to MAX STREAM are documented in this file in reverse chronological order. This changelog is synced automatically to GitHub Releases, release notes, and GitHub build artifacts.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [5.2.0] - 2026-10-09

### 🚀 Highlights
- **CloudStream 3 Universal Scraper Engine Compatibility**: Full native support for headless CSX extensions, resilient multi-route scrapers, auto-serialization for episodes and links, and dynamic extractors.
- **Deduplication & Architecture Optimization**: Complete purge of duplicate and redundant screens, streamlining navigation, reducing APK size, and centralizing account & library views.

### ✨ Added
- **CloudStream 3 Engine Compatibility Subsystem**:
  - `com.lagradost.api.Log`: Direct Android system log bridge preventing runtime `ClassNotFoundException` during extension execution.
  - `com.lagradost.cloudstream3.MainAPI`: Resilient abstract provider contract with multi-route URL fallback (`/search/%s/page/%d/` vs `/?s=%s&paged=%d`), URI encoding sanitization, and automatic `search(query: String)` -> `search(query, 1)` bridge.
  - `com.lagradost.cloudstream3.Episode`: Structured episode model with automatic JSON serialization (`AppUtils.toJson`) for complex link lists, preventing serialization failures.
  - `com.lagradost.cloudstream3.LoadResponse`: Complete response hierarchy (`MovieLoadResponse`, `TvSeriesLoadResponse`) with safe builders, cast extraction, IMDb metadata parsing, and rating score normalizers.
  - `com.lagradost.cloudstream3.plugins.Plugin` & `APIHolder`: In-memory registration hooks and provider lookup registry for runtime DEX plugins.
  - `DynamicScraperUtils`: Universal selector engine with fallback CSS selector chains (`selectFirstAvailable`), title/poster/synopsis DOM extractors, and safe URL builders.
  - `ExtractorManager` & `PipelineLogger`: Centralized extractor registry supporting multi-host dispatching and phase-by-phase diagnostics (SEARCH -> METADATA -> STREAM EXTRACTION).
- **Automated Changelog Pipeline**:
  - `CHANGELOG.md` root source-of-truth tracking all feature additions, removals, bug fixes, and security enhancements.
  - GitHub Actions release workflow integration (`release.yml`, `pre-release.yml`, `build.yml`) automatically parsing and publishing release notes directly into GitHub Releases and packaging `CHANGELOG.md` with release artifacts.
  - Direct in-app access via **Settings > About > View Changelog & Release Notes** (`ChangelogScreen.kt`).

### 🗑️ Removed (Screens & Redundant Code)
- **Duplicate Library Screen**: Removed `CineHubLibraryScreen.kt` in favor of the unified media screen (`CineHubUnifiedMediaScreen.kt`).
- **Duplicate Download Manager**: Removed `CineDownloadManagerScreen.kt` in favor of the native Downloads tab and overlay manager.
- **Redundant Player Wrapper**: Removed `PlayerScreen.kt` which duplicated REX Player's core playback controller.
- **Duplicate Profile Screen**: Removed `ProfileScreen.kt` from `xyz.mpv.rex.ui.profile`, unifying all user account, RBAC tier management, and session preferences inside `AccountPreferencesScreen.kt` with backward-compatible typealiases.
- **Redundant Developer Options Links**: Removed duplicated "Installed Providers" and "Repository Manager" navigation items from `DeveloperOptionsScreen.kt`, consolidating extension management into `ExtensionPreferencesScreen.kt`.

### 🛡️ Security & Performance
- **Secrets Elimination**: Validated complete purge of hardcoded admin unlock passcodes.
- **TTL Admin Elevation**: Ephemeral in-memory elevation sessions with 15-minute expiration and automatic sign-out revocation.
- **APK Size & Build Optimization**: Decreased method count and bundle size by eliminating duplicate Composables and unused source files.

---

## [5.1.0] - 2026-09-24

### ✨ Added
- **Glassmorphism UI Engine**: Material 3 translucent glass design system with dynamic blur, subtle borders, and immersive abyss dark styling.
- **Remote Admin Elevation Subsystem (Phase 2.1)**:
  - Ephemeral in-memory elevation lifecycle via `AdminSessionManager`.
  - Challenge/response contracts (`AdminAuthorizationProvider`, `ElevationTokenValidator`).
  - Strict route guards for Developer Options and Provider Management.
- **Firebase Auto-Discovery & Sync**:
  - Remote repository indexing and provider package synchronization.
  - Live TV channel synchronization and EPG ingestion via `FirebaseProviderSyncService`.

### 🔒 Security
- Deprecated persistent client-side unlock flags.
- Purged legacy hardcoded unlock codes (`MAXSTREAM777`, `ADMIN777`).

---

## [5.0.0] - 2026-08-15

### 🚀 Initial Major Release of MAX STREAM
- High-performance media player powered by libmpv 0.38+ and FFmpeg 7.0+ core.
- Hardware-accelerated decoding for AV1, HEVC, VP9, and EAC3 audio passthrough.
- Multi-protocol network streaming supporting SMBv2/v3, WebDAV, FTP, SFTP, and direct HTTP/HLS/DASH links.
- CineHub media library with TMDB scraping and metadata caching.
