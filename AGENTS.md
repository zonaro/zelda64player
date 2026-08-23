# Zelda 64 Player — Project Rules for Agent Team

**Generated:** 2026-08-22  
**Project Path:** `/mnt/GIT/zelda64player`  
**Base Project:** Ludere (fork of Swordfish90/Ludere, GPL-3.0) at `/mnt/GIT/Ludere`  
**Package:** `br.com.redclaw.zelda64player`  
**App Name:** "Zelda 64 Player"

---

## Overview

This is a **native Android (Kotlin)** application derived from the existing Ludere codebase. It is a LibretroDroid-powered emulator frontend for Nintendo 64 Zelda hacks (Ocarina of Time, Majora's Mask) with **on-the-fly BPS patching** of user-provided base ROMs.

**Core Philosophy**: The app NEVER ships, downloads, or includes base ROMs. Users must legally import their own Ocarina of Time and Majora's Mask ROMs. The app downloads only BPS patches from a GitHub-hosted JSON catalog and applies them in-memory/cache before emulation.

**New Feature — OoT Randomizer Generator**: Integrated Ocarina of Time randomizer/plandomizer using the official OoTR API (`https://ootrandomizer.com/api/docs`). User provides their own API key (obtained via OoTR Discord). Schema-driven settings UI (~200 options from asset JSON), plandomizer support (text editor + JSON import + visual builder), ZPF/ZPFZ patch application on compressed ROM, N64 boot CRC (CIC 6105) recomputation. Generated seeds appear in a dedicated "Randomizadores" Library section, separate from Store hacks. Unlimited seeds per user.

---

## Stack Decisions (Final)

| Layer | Technology | Version | Notes |
|-------|------------|---------|-------|
| Language | Kotlin | 1.9+ | JVM target 1.8 |
| Min SDK | Android API | 24 (Nougat) | Raised from 21; <1% API 21-23 share |
| Target SDK | Android API | 35 | Play Store requirement (Aug 2025+) |
| Compile SDK | Android API | 35 | Required for Material Components 1.14.0 (M3 Expressive) |
| Build System | Gradle | 8.x | Kotlin DSL (`build.gradle.kts`) |
| Architecture | MVC-ish | — | View (Activity+ViewBinding) → ViewModel (AndroidViewModel) → Model (Repository+UseCase) |
| DI | Manual Service Locator | — | `AppContainer` in `Application` subclass; no Dagger/Hilt |
| Emulation | LibretroDroid | 0.13.2 | **Vendored local module `:libretrodroid`** (source from tag 0.13.2) + 2 JNI passthroughs for memory access; cores fetched at build time |
| Touch Controls | RadialGamePad | 0.6.0 | Included as module or AAR |
| Networking | OkHttp | 4.12+ | Already transitive via LibretroDroid |
| Image Loading | Coil | 2.6+ | Kotlin-first, coroutines, lightweight |
| Persistence | JSON (files) + Room (optional) | — | BaseRomRepository: JSON in `filesDir`; PatchRepository: files in `cacheDir`; RA metadata: JSON in `filesDir` |
| Coroutines | kotlinx-coroutines | 1.8+ | `lifecycle-runtime-ktx` for ViewModelScope |
| Reactive | RxJava/RxAndroid | 2.x | KEPT as-is: frozen `gamepad/` package depends on it (`CompositeDisposable`, `pad.events()`). New code (store, settings, randomizer, retroachievements) uses coroutines/Flow; do NOT refactor gamepad to Flow |
| Native | CMake + NDK | r26+ | `externalNativeBuild` for rcheevos (MIT) + JNI bridge; ABIs: x86, x86_64, armeabi-v7a, arm64-v8a |
| RetroAchievements | rcheevos | ~12.x (master tag) | MIT licensed, ANSI C; git subtree in `app/src/main/cpp/rcheevos/`; provides `rc_client_t` high-level API + `rapi` standalone |
| Material Components | material3 | 1.14.0 | **M3 Expressive** — minSdk 23 (our 24 OK); requires compileSdk 35; theme parent `Theme.Material3.Expressive.*` |
| Testing | JUnit 5, MockK, Turbine | — | Unit + Instrumented |

**Why NOT Flutter?** This is a direct derivative of an existing native Kotlin app (Ludere) with years of tuning on LibretroDroid integration, RadialGamePad touch controls, GL lifecycle handling, and core configuration. Rewriting in Flutter would discard all that work and introduce regression risk. The global rule "cross-platform apps use Flutter" does not apply to native derivatives.

---

## Visual Standard — Material 3 Expressive (MANDATORY)

**Material 3 Expressive is the mandatory visual standard for ALL screens** (existing and future). No exceptions except the frozen gamepad overlay and in-game menu chrome (Rule 14).

### Key Rules
| Rule | Requirement |
|------|-------------|
| **Theme parent** | Must be `Theme.Material3.Expressive.DayNight` (or `NoActionBar` variant) in `res/values/themes.xml` |
| **Components** | Use Material components (`MaterialButton`, `MaterialCardView`, `TextInputLayout`, `SwitchMaterial`, `Slider`, `TabLayout`, `BottomSheet`, `NavigationBarView`, etc.) — never plain `AppCompat` widgets or legacy `Widget.MaterialComponents.*` styles in new code |
| **Shape tokens** | Prefer expressive `ShapeAppearance` / `MaterialShapes` (cookie, scallop, extra-large corners) over hand-rolled drawables; use `shapeAppearanceCorner*` attributes |
| **Motion** | Use spring-based motion defaults (expressive `MotionSpec`); avoid linear easing for primary transitions |
| **Typography** | Use emphasized typescale text appearances (`TextAppearance.Material3.TitleLarge.Emphasized`, `BodyLarge.Emphasized`, etc.) for hierarchy |
| **Color** | Expressive color palettes via theme; CSS variables (`color_primary`, `color_surface`, etc.) still apply for customization |
| **Sizing** | Generous component sizing; clear primary/secondary action emphasis (filled vs tonal vs outlined buttons) |
| **i18n / no-emoji** | Hard rules (Rules 7–8) still apply to all UI work |
| **Exemption** | **Gamepad overlay + in-game menu card style** (frozen RadialGamePad layout per Rule 14) are EXEMPT from restyling — they keep their current look |

### Migration Notes
- Dependency: `implementation(libs.material3)` → version catalog entry `material3 = "androidx.compose.material3:material3:1.14.0"` (or `com.google.android.material:material:1.14.0` for Views)
- Compile SDK **must be 35** (already in Stack Decisions)
- Verify exact style/attribute names against the **1.14.0 AAR** during implementation — research summary paraphrased some names; do not trust docs blindly

---

## Project Structure (Package Layout)

```
br.com.redclaw.zelda64player
├── data/
│   ├── model/           # Immutable data classes (BaseRom, HackEntry, HackCatalog, PatchFile)
│   └── local/           # Repositories (BaseRomRepository, PatchRepository, CatalogRepository)
├── patcher/             # PURE KOTLIN MODULE (no Android deps)
│   ├── bps/             # BPS parser, applier, validator, varint
│   ├── n64/             # ROM normalizer, header parser, checksum calculator
│   └── PatcherFacade.kt # Single public API
├── store/               # Hack Store feature
│   ├── ui/              # StoreActivity, ViewModel, Adapter, BottomSheet
│   ├── DownloadManager.kt
│   └── CatalogFetcher.kt
├── settings/            # Settings feature
│   ├── ui/              # SettingsActivity, Fragments (BaseRomImport, BaseRomList, CatalogUrl, RandomizerApiKey, RetroAchievements)
│   └── SettingsViewModel.kt
├── repositories/
│   └── Storage.kt       # Paths per hackId: rom_<id>, sram_<id>, state_<id>
├── retroview/           # Emulation (adapted from Ludere)
│   ├── RetroView.kt     # INTERCEPTION POINT: loads patched ROM from cache
│   └── RetroViewUtils.kt
├── views/
│   ├── LibraryActivity.kt
│   ├── GameActivity.kt
│   └── viewmodels/GameActivityViewModel.kt
├── gamepad/             # RadialGamePad — UNCHANGED from Ludere
├── input/               # ControllerInput, InputMapper — UNCHANGED
├── utils/               # CorePrefs, RetroViewUtils — UNCHANGED
├── di/AppContainer.kt   # Service Locator
├── randomizer/          # OoT Randomizer Generator feature
│   ├── api/             # OotrApiClient, models, exceptions, RateLimiter
│   ├── settings/        # Schema loader, form renderer, validator, plandomizer models
│   ├── patch/           # ZPF/ZPFZ parser, applier, validator, N64 boot CRC (CIC 6105)
│   ├── ui/              # RandomizerActivity, ViewModel, Plandomizer fragments, progress dialog
│   └── repository/      # RandomizedSeedEntry, RandomizedSeedRepository
├── retroachievements/   # RetroAchievements Integration feature
│   ├── jni/             # RcheevosJni, LibretroDroidMemoryJni (JNI bridges)
│   ├── api/             # RaHttpClient, RaApiModels, RaApiException
│   ├── auth/            # RaCredentialStore, RaSessionManager, RaLoginFragment
│   ├── data/            # RaGameMetadata, RaAchievement, RaLeaderboard, RaRepository
│   ├── ui/              # AchievementsActivity, AchievementDetailActivity, InGameAchievementOverlay, LeaderboardDialog
│   ├── viewmodel/       # AchievementsViewModel, InGameRaViewModel
│   └── install/         # RaHashService, RaInstallRepository
└── libretrodroid/       # LOCAL GRADLE MODULE (vendor LibretroDroid 0.13.2 + 2 JNI passthroughs)
```

---

## Hard Rules (MUST Follow)

### Legal / Copyright
1. **NEVER embed, download, or distribute base ROMs** (OoT, MM). This includes: assets, raw resources, test fixtures, CI artifacts, release APKs.
2. **NEVER commit copyrighted content** (ROMs, BIOS, proprietary assets). Only patches (BPS) and metadata are distributed.
3. **GPL-3.0 compliance**: This project is a derivative of Ludere (GPL-3.0). The entire app MUST be licensed GPL-3.0. Keep `LICENSE` file, preserve copyright headers, offer source code.
4. **BPS Patcher is clean-room**: Implemented from public spec (bps_spec.md) only. Do NOT read or copy code from `rom_patcher_js` (GPL-3.0) or `UniPatcher` (GPL-3.0). Document this in code headers.

### Code Quality
5. **DRY**: Extract shared logic into `utils/`, `patcher/`, or `di/`. No duplication.
6. **Modular**: Small focused classes/functions. Single responsibility.
7. **No emojis** in code, file paths, resource names, or technical output. Conversational text only.
8. **i18n mandatory**: Every user-facing string in `strings.xml`. Default `values/` = **pt-BR**. Provide `values-en/` and `values-es/`. Zero hardcoded strings in Kotlin/XML layouts.
9. **Performance**: Default `config_load_bytes=false` (stream from file). Avoid loading full 32–64 MB ROMs into heap. Use streaming patcher.

### Architecture
10. **RetroView interception**: The ONLY place ROM bytes reach the core is `RetroView.kt` → `GLRetroViewData.gameFilePath` (or `gameFileBytes`). This is where patched ROM from `Storage.rom(hackId)` is supplied.
11. **Storage paths keyed by hackId**: Each hack gets isolated `rom_<hackId>`, `sram_<hackId>`, `state_<hackId>`. SRAM/save-states never collide.
12. **Catalog schema versioning**: `catalogVersion` integer in JSON. Handle migrations gracefully.

### Migration Scope (Ludere extraction)
13. **Selective migration only**: copy from Ludere exclusively `retroview/`, `views/GameActivity*` + ViewModel, `gamepad/`, `input/`, `utils/`, `config.xml`, `Storage.kt`, plus the launcher icons (`mipmap-*/ic_launcher*`, `drawable/ic_launcher_foreground.xml`, `values/ic_launcher_background.xml` — reused as-is for now per user decision; a custom Zelda-themed icon is a future task). Do NOT migrate: `data/Games.kt` (hardcoded catalog), bundled cover drawables, ROM packaging machinery (`assets/roms/`, `res/raw/rom`, `noCompress` rules), `autogen/`, the autogen GitHub workflow, the Ludere keystore, or ABI splits.
14. **Gamepad layout is FROZEN**: the Zelda-tailored control layout (`gamepad/GamePadConfig.kt` placements, ButtonStick + Auto mode, Auto-Z double-tap, FloatingJoystick region/hint/reach, DoubleTapContainer, physical-controller mirroring, sensitivity dialogs) is measured from a reference image and tuned for OoT/MM. Migrate verbatim (package rename only). Any layout change requires explicit user approval and an update to the "Layout de Controles Sob Medida" section of `plano.md`. The integration invariants must not regress: overlay uses INVISIBLE (never GONE), pads created only after first real layout pass, GL-context recovery via full Activity recreate(), and `super.onDestroy()` called BEFORE dispose.

### Security / Privacy
15. **No telemetry without opt-in**. No analytics, crash reporting, or network calls except: catalog fetch (user-initiated), patch download (user-initiated), core download (build-time only).
16. **Validate all inputs**: ROM checksums, patch checksums, catalog JSON schema, downloaded file sizes.

### Randomizer Feature (OoTR)
17. **Randomizer NEVER bypasses base-ROM legality checks**: Only OoT 1.0 NTSC-U (CZLE) and NTSC-J (CZLJ) version byte 0x00 are accepted for randomization. All other ROM versions (1.1, 1.2, PAL, MQ, GC, VC, iQue, Majora's Mask) are rejected with clear error messages.
18. **API key never hardcoded, logged, or committed**: Stored only in `EncryptedSharedPreferences` (key `pref_ootr_api_key`). Sanitized in all logs (`***`). Never included in backups, BuildConfig, assets, or any network request except the OoTR API query param.
19. **i18n exception for settings labels**: The ~200 individual setting labels/tooltips in `assets/randomizer/oot_settings_schema.json` remain in English (canonical OoTR community terminology). Only UI chrome (tab titles, buttons, error messages, placeholders) uses `strings.xml` (pt-BR/en/es). This exception is documented and intentional.

### RetroAchievements Feature
20. **RA credentials never logged**: Username/password/token sanitized in all logs (`***`). Stored only in `EncryptedSharedPreferences` (separate prefs file `ra_secure_prefs`).
21. **RA hash computed ONLY from final patched ROM**: At install time, after BPS/ZPF patch applied and ROM written to `Storage.rom(hackId)`. Never from base ROM or uncompressed intermediate.
22. **Leaderboards NEVER overlaid on gameplay**: Leaderboards accessible ONLY via GameActivity in-game menu (DialogFragment). No tracker widgets, no overlay views during emulation.
23. **Hardcore mode defaults OFF**: `pref_ra_hardcore = false` until User-Agent validated with RAdmin. Setting exists but softcore is default.
24. **Third-party license notices**: rcheevos (MIT) license file kept in `app/src/main/cpp/rcheevos/LICENSE` and referenced in app Licenses screen / About dialog.
25. **System notifications opt-in default ON**: `pref_ra_system_notifications = true` but requires `POST_NOTIFICATIONS` runtime permission on API 33+. First unlock triggers permission request if needed.

---

## Agent Team for THIS Project

| Agent | Role in This Project | Delegation Trigger |
|-------|---------------------|-------------------|
| **Coral** 🪸 | Chief Architect — owns `plano.md`, `AGENTS.md`, `.agents/`, architecture decisions | New project setup, major arch changes, team selection |
| **Bruce** 🦈 | **Primary Implementer** — all Kotlin/Android code (Phases 0–4 + Randomizer R1–R5 + RetroAchievements B1–B5) | All implementation tasks: patcher, store, settings, retroview, UI, randomizer, retroachievements |
| **Dolfi** 🐬 | Icons/covers — generates SVG icons (app icon, hack category icons, RA trophy/leaderboard icons) and PNG cover placeholders for hacks without `coverImageUrl` | When UI needs icons or cover art |
| **Wally** 🐋 | Documentation — finalizes `README.md`, translates `strings.xml` (pt-BR/en/es), writes code docs (KDoc) | After implementation phases, before release |
| **Calamari** 🦑 | Fact-checking — validates known ROM checksums (No-Intro/Redump), verifies LibretroDroid/core versions, checks BPS spec details, validates OoT 1.0 checksums and N64 boot CRC algorithm, **validates rcheevos release tags and RA hash algorithm** | When Bruce needs verified data |
| **Puffy** 🐡 | Research — up-to-date LibretroDroid releases, core buildbot URLs, Android API changes, Gradle plugin updates, OoTR API docs, **rcheevos API docs and release notes** | When Bruce needs current docs |
| **Chululu** 🐙 | Visual QA — analyzes screenshots of Library/Store/Game/Randomizer/RetroAchievements UI for layout, alignment, accessibility | Before UI merges, release candidates |

**Agents NOT involved**: InnerLinho (PHP), Fishie (Web frontend), Peep (Flutter), Snowflake (C#), Snuggle (Python), Nodi (Node.js), Ariel (Content), Tucso (Linux scripts).

---

## Delegation Mapping (This Project)

| Task Type | Delegate To |
|-----------|-------------|
| Android/Kotlin implementation (all phases) | **Bruce** |
| Architecture changes / plan updates | **Coral** |
| App icon, hack category icons, cover placeholders, RA icons | **Dolfi** |
| README, strings translation, KDoc | **Wally** |
| ROM checksum verification, core version check, rcheevos release validation | **Calamari** |
| LibretroDroid/core/Android API research, rcheevos API docs | **Puffy** |
| UI screenshot analysis | **Chululu** |

---

## References

- **Master Plan**: `plano.md` (this directory) — complete architecture, milestones, JSON schema, risk register
- **Per-Agent Rules**: `.agents/` folder — one file per agent with concrete responsibilities
- **Global Rules**: `~/.config/opencode/AGENTS.md` — team conventions, delegation patterns
- **Source Project**: `/mnt/GIT/Ludere` — reference implementation (copy/adapt, don't rewrite from scratch)
- **BPS Spec**: https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md
- **N64 ROM Header**: https://n64brew.dev/wiki/ROM_Header
- **LibretroDroid**: https://github.com/Swordfish90/LibretroDroid
- **RadialGamePad**: https://github.com/Swordfish90/RadialGamePad

---

## File Conventions

| File | Convention |
|------|------------|
| `build.gradle.kts` | Kotlin DSL, version catalogs (`libs.versions.toml`) |
| `strings.xml` | pt-BR in `values/`, en in `values-en/`, es in `values-es/` |
| `colors.xml` | CSS-variable-style names (`color_primary`, `color_surface`) |
| `config.xml` | Per-build config (core, variables, orientation, gamepad) — keep from Ludere |
| `.gitignore` | Adapted from Ludere + add `*.bps`, `catalog.json`, `base_roms/` |
| `LICENSE` | GPL-3.0 header + project copyright |
| `README.md` | Draft placeholder; Wally finalizes |

---

## Permissions Matrix

| Agent | Can Read | Can Write | Can Execute |
|-------|----------|-----------|-------------|
| Coral | All | `plano.md`, `AGENTS.md`, `.agents/*.md`, `.gitignore` | Plan validation |
| Bruce | All | All Kotlin/XML/Gradle files in `app/src/main/` | Build, test, run |
| Dolfi | `plano.md` (schema) | `res/drawable/`, `res/mipmap-*`, `assets/covers/` | Image generation |
| Wally | All | `README.md`, `strings.xml` (all locales), KDoc comments | Doc generation |
| Calamari | `plano.md`, web | Reports only | Fact-check queries |
| Puffy | `plano.md`, web | Reports only | Research queries |
| Chululu | Screenshots | Reports only | Visual analysis |

---

## Change Control

- **Architecture changes** (package structure, data flow, new modules) → **Coral** updates `plano.md` + `AGENTS.md` → informs Lobby
- **Implementation details** (class names, algorithms, UI layout) → **Bruce** decides, documents in KDoc
- **Visual assets** → **Dolfi** proposes, **Bruce** integrates
- **Documentation** → **Wally** owns final output
- **External facts** → **Calamari**/**Puffy** provide, **Bruce**/**Coral** consume