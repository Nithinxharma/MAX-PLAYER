# MAX STREAM

<p align="center">
  <img src="app/src/main/res/drawable/ic_max_stream_logo.xml" width="160" height="160" alt="MAX STREAM Logo" />
</p>

<p align="center">
  <b>Ultra-modern, high-performance Android media player & streaming platform built on libmpv and Jetpack Compose.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+-brightgreen.svg?style=flat-square&logo=android" />
  <img src="https://img.shields.io/badge/Engine-libmpv_v0.38-blue.svg?style=flat-square" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.1.0-purple.svg?style=flat-square&logo=kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_M3-teal.svg?style=flat-square&logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/License-Apache--2.0-orange.svg?style=flat-square" />
</p>

---

## 🌟 Overview

**MAX STREAM** is an advanced Android media client that fuses the raw performance and codec capabilities of `libmpv` with a modern Material 3 Jetpack Compose user experience and extensible media integration. Whether you are playing high-bitrate local 4K HDR videos, streaming over SMB/WebDAV network drives, or discovering media content via dynamic CloudStream plugins, **MAX STREAM** provides a seamless, fluid experience.

---

## ✨ Key Features

### 🍿 MAX STREAM Extension & Streaming Engine
- **CloudStream Plugin Compatibility**: Full support for `.cs3` dynamic extension plugins loaded via Android `PathClassLoader`.
- **Unified Multi-Source Search**: Concurrent search queries across all active extension providers with real-time poster cards, ratings, release years, and media type badges.
- **Headless Link Extraction**: Automated link resolver with multi-server quality selection, HTTP header injection (`User-Agent`, `Referer`), and direct zero-latency handoff to `libmpv`.
- **Media Details & Episode Selector**: Rich media cards showing detailed synopses, season/episode pickers for TV series and anime, and custom stream source selectors.
- **Diagnostic & Test Center**: Unified 16-stage DEX plugin trace, live provider diagnostic suite, and link resolution debugger built directly into Developer Options.

### 🎬 Advanced Playback & Gestures
- **Circular Double-Tap Seek**: Customizable circular seek overlay with smooth ripple animations.
- **Seek Cancellation**: Cancel a seek gesture mid-drag with interactive pointer scaling feedback.
- **Interactive Subtitle Control**: Tap and drag subtitles vertically anywhere on screen; swipe horizontally to jump precisely between subtitle lines.
- **Top Seek Capsule OSD**: Pill-shaped top OSD showing seek feedback without obscuring video content.
- **A-B Loop & Frame Navigation**: Pinpoint loop start and end points; step frame-by-frame using a non-colliding floating panel.
- **HDR-to-SDR Tone Mapping**: Real-time high-quality tone mapping via the `hdr-toys` shader pipeline.
- **Custom Aspect Ratio & Pan/Zoom**: Fine-grained video zoom, crop, and pan controls stored independently per video.

### 🎨 Glassmorphism UI & Dynamic Design
- **Glass Theme Player UI**: Translucent glassmorphism controls, seekbars, speed indicators, and gesture overlays.
- **Material You Dynamic Colors**: Player UI elements automatically adapt to your system Android palette.
- **Circular Theme Transition**: Smooth circular reveal animation when toggling dark and light themes.
- **Customizable Dashboard**: Hide, show, and reorder main bottom navigation tabs.

### 🗂️ Unified File Explorer & Network Streaming
- **Multi-Protocol Network Client**: Built-in high-performance streaming proxy for SMB, WebDAV, and FTP network shares with image preview caching.
- **M3U & Live IPTV Integration**: Native M3U playlist engine with drag-and-drop reordering, custom channel parsing, and live stream playback.
- **Batch Range Selection**: Multi-select file ranges by long-pressing the starting file and tapping the ending file.
- **Sectioned Grid & Tree Navigation**: Customizable subdirectory layouts, breadcrumb paths, and sorting by Name, Date, Size, and Duration.

### ⚡ Shorts Mode & Media Library
- **Shorts Player**: Dedicated vertical video player with directory source filters, session Free Mode, and reactive MPV observers.
- **Smart Watch Tracking**: Automatic watched/unplayed status, skip markers, "NEW" badges, and resume point sync via Room database persistence.

---

## 🏗️ Architecture & Technology Stack

MAX STREAM is engineered following modern Android architecture guidelines:

```
├── app/src/main/kotlin/
│   ├── xyz/mpv/rex/
│   │   ├── cinehub/                     # MAX STREAM Extension Engine & Diagnostics
│   │   │   ├── bridge/                  # Headless CloudStream Runner & Player Bridge
│   │   │   ├── diagnostic/              # CloudStream Diagnostic & Test Center
│   │   │   ├── extension/               # Plugin Manager, Registry & Provider Adapters
│   │   │   └── failover/                # Stream Health Resolver & Fallback Logic
│   │   ├── cinetv/                      # Live TV & IPTV Stream Management
│   │   ├── domain/                      # Core Business Logic & Models
│   │   ├── database/                    # Room Database (Video Metadata, Playback States)
│   │   ├── preferences/                 # Koin & Multiplatform DataStore Preferences
│   │   ├── repository/                  # Media File, Network & Search Repositories
│   │   └── ui/                          # Jetpack Compose Screens & Glassmorphism Components
│   │       ├── browser/                 # Main Dashboard, File Explorer & MAX STREAM Screen
│   │       ├── player/                  # MPV Surface View & Controls
│   │       ├── preferences/             # Settings, Extension & Diagnostic Screens
│   │       └── splash/                  # Cinematic MAX STREAM Splash Screen
│   └── com/lagradost/cloudstream3/      # MainAPI & Plugin Runtime Interfaces
```

### Core Libraries
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3.
- **Video Engine**: [libmpv](https://github.com/mpv-player/mpv) native JNI bindings.
- **Dependency Injection**: [Koin](https://insert-koin.io/).
- **Database & State**: [Room Database](https://developer.android.com/training/data-storage/room) & Kotlin `StateFlow`.
- **Image Loading**: [Coil 3](https://coil-kt.github.io/coil/).
- **Concurrency**: Kotlin Coroutines.

---

## 🚀 Building & Setup

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or IntelliJ IDEA.
- JDK 17 or higher.
- Android SDK 35.

### Steps
1. **Clone the repository**:
   ```bash
   git clone https://github.com/maxstream/MAX-STREAM.git
   cd MAX-STREAM
   ```

2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on connected Android device**:
   ```bash
   ./gradlew installDebug
   ```

---

## 📜 License

Distributed under the **Apache License 2.0**. See `LICENSE` for full details.

---

<p align="center">
  <b>MAX STREAM</b> — Built for performance, privacy, and seamless streaming.
</p>
