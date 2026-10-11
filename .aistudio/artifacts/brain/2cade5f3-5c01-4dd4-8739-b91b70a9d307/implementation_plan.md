# Implementation Plan: Audit and Integrate Combined CloudStream Reference Files

## Context & Objectives
The local `Reffer/` directory contains an unorganized merge of CloudStream's upstream `library/` (headless plugin, extractor, and provider runtime) and `shared/` (multiplatform Compose UI and Mihon preferences). The objective is to:
1. Reconstruct the original module boundaries across all 278 reference files.
2. Integrate missing core extractors, metaproviders, and utilities into the production app (`app/src/main/kotlin/com/lagradost/cloudstream3/`).
3. Resolve syntax, import, and architecture compatibility issues (e.g. replacing Ktor references with Java/Android standards, fixing regex escapes, adding `getCaptchaToken` to `MainAPI` and `APIHolder`).
4. Register all compatible extractors into `DefaultExtractors`.
5. Verify build integrity via Gradle compilation.
6. Safely remove `Reffer/` and deliver the final audit report.

---

## Phase 1: Reconstruct Original Structure (Manifest & Classification)

All 278 files in `Reffer/` have been audited and classified with zero ambiguity:
- **`library/` (185 files)**:
  - Configuration & ABI: `library/build.gradle.kts` (Buildkonfig, Dokka, Maven Publish), `library/README.md`, `library/lint.xml`, `library/api/jvm/library.api`.
  - Core API & Engine: `com.lagradost.cloudstream3.*` (MainAPI, APIHolder, plugin loading, video models, extractors, metaproviders, syncproviders, subtitles, utils).
  - Logging & Multiplatform Stubs: `com.lagradost.api.*` (`ContextHelper`, `Log`).
  - Unit tests: `commonTest`, `jvmTest`, `webTest`.
- **`shared/` (93 files)**:
  - Configuration & Docs: `shared/build.gradle.kts` (Compose Multiplatform, Android library with namespace `com.lagradost.cloudstream4`), `shared/readme.md` (QuickNovel sister project architecture), `shared/lint.xml`.
  - UI & Theming: `com.lagradost.cloudstream4.*` (Compose screens, color pickers, theme palettes, M3 dialogs, fragments).
  - Preferences Engine: `com.mihon.*` (Mihon preferences and settings widgets).
  - Assets: `composeResources/` (Product Sans fonts, vector drawables, UI strings).

Per user preference, the `shared/` multiplatform UI and Mihon widgets will be omitted from the production app, preserving Rex's existing native Material 3 architecture while integrating all core functionality from `library/`.

---

## Phase 2: Audit Existing Production Project & Gap Analysis

1. **Existing Implementations**:
   - Production already contains 114 extractors, Core `APIHolder`, `MainAPI`, video models, subtitle parsers, and CineHub bridge.
2. **Missing Core Extractors from `library`**:
   - `Hexload.kt`
   - `HubuCloud.kt`
   - `Playmate.kt`
   - `Streamcash.kt`
   - `Vidara.kt`
   - `Vids.kt`
   - `YoutubeExtractor.kt`
   - `Firestream.kt`
3. **Missing Metaproviders & Serializers**:
   - Metaproviders: `CrossTmdbProvider.kt`, `MyDramaList.kt`, `SyncRedirector.kt`, `TraktProvider.kt`
   - Utilities: `HlsPlaylistParser.kt`, `AtomicList.kt`, `AppDebug.kt`, `SubtitleHelperPlatform.kt`
   - Serializers: `FloatAsIntSerializer.kt`, `FloatAsLongSerializer.kt`, `JsonTransformSerializer.kt`, `NullableStringSerializer.kt`, `WriteOnlySerializer.kt`
4. **Compatibility & Syntax Bottlenecks**:
   - `UnshortenUrl.kt`: Replace Ktor `Url(currentUrl).host` with Android/Java `URI`.
   - `JsHunter.kt`: Fix regex string escaping and `hunter` function invocation.
   - `VstreamhubHelper.kt`: Restore uncorrupted quotes and clean substring calls.
   - `Streamplay.kt`: Replace Ktor `Url` with `URI` and link to `getCaptchaToken`.
   - `Vinovo.kt`: Clean regex pattern and connect `getCaptchaToken`.
   - `GogoHelper.kt`: Replace Ktor `Url` with `URI`.
   - `Streamhub.kt` vs `Zplayer.kt`: Rename `Streamhub2` in `Streamhub.kt` to `StreamhubInk` to resolve duplicate class declaration with `Zplayer.Streamhub2`.
   - `MainAPI.kt` & `APIHolder.kt`: Implement missing `getCaptchaToken` helper required by CloudStream extractors.

---

## Phase 3: Integration & Adaptation

1. **Core Runtime & Compatibility Fixes**:
   - Update `MainAPI.kt` and `APIHolder.kt` with `getCaptchaToken`.
   - Update `UnshortenUrl.kt`, `JsHunter.kt`, `VstreamhubHelper.kt`, `Streamplay.kt`, `Vinovo.kt`, `GogoHelper.kt`.
   - Resolve `StreamhubInk` vs `Streamhub2` in `Streamhub.kt` and `DefaultExtractors.kt`.
2. **Integrate Missing Extractors**:
   - Copy `Firestream.kt`, `Hexload.kt`, `HubuCloud.kt`, `Playmate.kt`, `Streamcash.kt`, `Vidara.kt`, `Vids.kt`, and `YoutubeExtractor.kt` into `app/src/main/kotlin/com/lagradost/cloudstream3/extractors/`.
   - Ensure imports match the Android target (replace any multiplatform Ktor/Cryptography references with Android native equivalents).
3. **Integrate Missing Metaproviders & Utilities**:
   - Copy metaproviders into `app/src/main/kotlin/com/lagradost/cloudstream3/metaproviders/`.
   - Copy missing serializers into `app/src/main/kotlin/com/lagradost/cloudstream3/utils/serializers/`.
   - Copy missing utilities (`HlsPlaylistParser.kt`, `AtomicList.kt`, `AppDebug.kt`, `SubtitleHelperPlatform.kt`) into `app/src/main/kotlin/com/lagradost/cloudstream3/utils/`.
4. **Extractor Registration**:
   - Register all compatible extractors in `DefaultExtractors.kt`.

---

## Phase 4: Verification Before Cleanup

1. Verify that no production source file references `Reffer/`.
2. Run `gradle compileDebugKotlin` to verify full compilation.
3. Fix any residual type mismatches, missing imports, or runtime signatures.

---

## Phase 5: Removal of Temporary Reference Material & Final Report

1. Delete `Reffer/` completely.
2. Re-run `gradle compileDebugKotlin` to verify the build remains completely green without `Reffer/`.
3. Provide the final report with full reconstruction audit, integrated files, and verification proof.
