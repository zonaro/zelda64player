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

**New Feature — Auto-Ocarina**: In-game HUD to auto-play Ocarina songs in OoT/MM from the pause menu. Built-in song catalog (OoT: 12, MM: 11) + optional per-hack custom songs via catalog `ocarinaSongs` field. Coroutine sequencer sends key events to GLRetroView (~330ms/note), cancellable by any user input/menu open/lifecycle. Game detection at `launchHack` via `RomHeader.gameCode` prefix: `CZL*` = OoT family, `NZL*`/`NSM*` = MM family. Unknown games hide the menu item.

**New Feature — Vanilla Games in Library**: User-imported base ROMs (OoT, MM) are now playable directly from the main Library screen. New `views/BaseRomLibrarySource.kt` exposes `BaseRomRepository` entries as playable tiles with `vanilla_<crc32>` IDs (prefix `BaseRomLibrarySource.PREFIX` / `GameRomResolver.VANILLA_PREFIX`). Grid order: vanilla games first, then store hacks. Single ROM resolver `repositories/GameRomResolver.kt` is the only resolution point for any Library entry: `vanilla_*` resolves via `BaseRomRepository`; everything else falls back to `Storage.rom(hackId)`. All launch paths (RetroView, GameActivityViewModel.launchHack/prepareOcarinaDetection/startRaSessionIfNeeded, LibraryActivity.requestImportSaves) now route through it. Rule 10 unchanged: RetroView remains the only place bytes reach the core. Covers fetched at runtime from Libretro thumbnails CDN (Named_Boxarts, USA art) by game family via `OcarinaSongCatalog.detectGame` (CZL* → OoT, NZL*/NSM* → MM); unknown families fall back to placeholder. No copyrighted artwork committed (Rule 2). Library tiles carry an icon badge (VANILLA = ice-cream, HACK = robot) whose chip background and icon tint are colored by game family: OoT = yellow background / black icon, MM = purple background / white icon, unknown family = neutral chip (color_primary background / white icon). The family is `HackLibraryEntry.family` (OcarinaGame?), set per source: BaseRomLibrarySource reuses its detectGame result, CatalogBackedLibrarySource reads the installed patched ROM header, LocalPatchesSource leaves it null. Vanilla tiles use the VANILLA badge via `HackLibraryEntry.isVanilla`. Context menu omits management section (uninstall/delete-seed) for vanilla entries — base ROMs managed in Settings. RetroAchievements: vanilla games have no install step; RA identity (rhash + game id) computed lazily on first play (fire-and-forget in GameActivityViewModel after session start), consistent with Rule 21 (hash always from final playable ROM — for vanilla that IS the normalized base ROM). Tests: `BaseRomLibrarySourceTest` + `GameRomResolverTest` (JVM unit tests).

**UI Revamp — Nintendo Switch Style**: Complete visual overhaul replacing Material 3 Expressive with a custom native implementation of the Nintendo Switch HOME menu aesthetic (NS_Launcher / FLauncher reference). All screens (Library, Store, Settings, RetroAchievements, in-game menus/overlays, splash) follow Switch design tokens, focus system, and component inventory. RadialGamePad touch-control LAYOUT remains frozen (Rule 14); only chrome restyled.

---

## Stack Decisions (Final)

| Layer | Technology | Version | Notes |
|-------|------------|---------|-------|
| Language | Kotlin | 1.9+ | JVM target 1.8 |
| Min SDK | Android API | 24 (Nougat) | Raised from 21; <1% API 21-23 share |
| Target SDK | Android API | 35 | Play Store requirement (Aug 2025+) |
| Compile SDK | Android API | 35 | Required for Material Components 1.14.0 (technical base only) |
| Build System | Gradle | 8.x | Kotlin DSL (`build.gradle.kts`) |
| Architecture | MVC-ish | — | View (Activity+ViewBinding) → ViewModel (AndroidViewModel) → Model (Repository+UseCase) |
| DI | Manual Service Locator | — | `AppContainer` in `Application` subclass; no Dagger/Hilt |
| Emulation | LibretroDroid | 0.13.2 | **Vendored local module `:libretrodroid`** (source from tag 0.13.2) + 2 JNI passthroughs for memory access; cores fetched at build time |
| Touch Controls | RadialGamePad | 0.6.0 | Included as module or AAR |
| Networking | OkHttp | 4.12+ | Already transitive via LibretroDroid |
| Image Loading | Coil | 2.6+ | Kotlin-first, coroutines, lightweight |
| Persistence | JSON (files) + Room (optional) | — | BaseRomRepository: JSON in `filesDir`; PatchRepository: files in `cacheDir`; RA metadata: JSON in `filesDir` |
| Coroutines | kotlinx-coroutines | 1.8+ | `lifecycle-runtime-ktx` for ViewModelScope |
| Reactive | RxJava/RxAndroid | 2.x | KEPT as-is: frozen `gamepad/` package depends on it (`CompositeDisposable`, `pad.events()`). New code (settings, retroachievements) uses coroutines/Flow; do NOT refactor gamepad to Flow |
| Native | CMake + NDK | r26+ | `externalNativeBuild` for rcheevos (MIT) + JNI bridge; ABIs: x86, x86_64, armeabi-v7a, arm64-v8a |
| RetroAchievements | rcheevos | ~12.x (master tag) | MIT licensed, ANSI C; git subtree in `app/src/main/cpp/rcheevos/`; provides `rc_client_t` high-level API + `rapi` standalone |
| Material Components | material3 | 1.14.0 | **Technical base only — visual standard is custom Switch skin (this overrides M3 Expressive)** |
| Testing | JUnit 5, MockK, Turbine | — | Unit + Instrumented |

**Why NOT Flutter?** This is a direct derivative of an existing native Kotlin app (Ludere) with years of tuning on LibretroDroid integration, RadialGamePad touch controls, GL lifecycle handling, and core configuration. Rewriting in Flutter would discard all that work and introduce regression risk. The global rule "cross-platform apps use Flutter" does not apply to native derivatives.

---

## Visual Standard — Nintendo Switch UI (MANDATORY)

**Nintendo Switch UI is the mandatory visual standard for ALL screens** (existing and future). This is a custom native implementation inspired by the Nintendo Switch HOME menu aesthetic (as seen in NS_Launcher / FLauncher). No Material 3 Expressive requirements remain. The only exemption is the **RadialGamePad touch-control LAYOUT** (Rule 14 — control placement, button-stick modes, floating joystick, auto-Z, physical-controller mirroring remain frozen). In-game menu chrome, overlays, pause menu, achievement overlay, leaderboard dialog, ocarina HUD, and all other UI MUST follow the Switch style.

### Design Tokens

| Token | Dark Mode | Light Mode | Usage |
|-------|-----------|------------|-------|
| `bg_primary` | `#2D2D2D` | `#F0F0F0` | Main background (Library Home, grid screens) |
| `bg_panel` | `#1E1E1E` – `#2A2A2A` | `#FFFFFF` | Side panels, dialogs, cards |
| `accent_focus` | `#00BCD4` (cyan) | `#00BCD4` (cyan) | Focus borders, focused labels, primary actions |
| `accent_amber` | `#FFA000` (amber) | `#FFA000` (amber) | Appearance/theme actions, warnings |
| `text_primary` | `#FFFFFF` | `#333333` | Primary text (titles, labels) |
| `text_secondary` | `#9E9E9E` | `#666666` | Secondary text (hints, "(default)" suffixes, footer) |
| `scrim` | `rgba(0,0,0,0.5–0.6)` | `rgba(0,0,0,0.3–0.4)` | Modal backdrop, panel overlays |
| `dock_circle` | `#555555` | `#FFFFFF` (subtle shadow) | Dock button backgrounds |
| `card_radius` | `4–6 dp` (near-square) | `4–6 dp` | Game cards (home row, grid) |
| `dialog_radius` | `12–16 dp` | `12–16 dp` | Dialogs, controllers modal |
| `panel_edges` | Sharp (0 dp) | Sharp (0 dp) | Side panel (right slide-in) |
| `card_aspect` | 1:1 (square) | 1:1 (square) | Home row ~220dp@1080p, grid ~170dp@1080p |
| `dock_button_diameter` | `~50 dp` | `~50 dp` | Circular dock icons |

### Focus System
- **D-pad / click focus** drives a **cyan 2–3 dp border** on the focused element + **label above the focused card** in cyan 18sp medium.
- **Unfocused cards**: 10% black overlay dimming.
- **Side-panel rows**: full-width focus border (cyan default, amber for theme/appearance actions).
- **Circular "All Games" card**: cyan border when focused, opens fullscreen grid.
- **Dock buttons**: focus ring (cyan) + glyph highlight.

### Component Inventory to Build (Native Kotlin, Hand-Styled)
| Component | Description |
|-----------|-------------|
| `SwitchHomeRow` | Horizontal scrollable row of square game cards + circular "Todos os Jogos" card at end |
| `SwitchGameCard` | Square card (1:1), cover image, game title overlay on focus, focus border, dimming overlay |
| `SwitchAllGamesCard` | Circular card (charcoal fill, cyan 2×2 grid icon, cyan border on focus) |
| `SwitchGridScreen` | Fullscreen grid ("Todos os Jogos"): header icon+title "Todos os Jogos" 20sp bold + thin separator, smaller square cards (~170dp), search/filter bar, ghosted placeholders |
| `SwitchDock` | Fixed bottom dock: 5 circular buttons (Loja, RetroAchievements, Galeria, Teste de Controle, Configurações), ~50dp diameter, colored glyphs, focus ring |
| `SwitchFooterHints` | Bottom bar: left TV+gamepad indicators, right "(i) Sobre" and "+ Opções" gray hints 11–12sp |
| `SwitchSidePanel` | Right slide-in panel (~50% width), sharp edges, header (teal badge icon + bold title 20–22sp + separator), numbered rows (gray circle 24dp badges), labels 16sp, "(default)" suffix 14sp gray, chevron right, thin line separators |
| `SwitchDialog` | Centered modal, scrim, box ~40% width, radius 12–16dp, bg `#3A3A3C`, header icon+title 18sp, rows 48–52dp with icon+text, focused row = cyan border outline |
| `SwitchFocusBorder` | Drawable: cyan 2–3dp stroke, transparent fill, for focus indication |
| `SfxManager` | SoundPool wrapper: focus-move tick, select, back, panel open/close; CC0/generated only; volume respect; toggle in settings |
| `ThemeManager` | Runtime dark/light switch, persists preference, applies tokens above |

### Screen-by-Screen Mapping

| Screen | Switch Style Applied | Notes |
|--------|---------------------|-------|
| Splash | **Zelda gold/green palette** (Dolfi original art), same structural layout as NS Launcher splash (flanking iconic shapes + two-line logo "Zelda 64" / "PLAYER") | No Nintendo IP (no Joy-Con shapes, no Nintendo logos) |
| Library Home | `SwitchHomeRow` + `SwitchAllGamesCard` + `SwitchDock` + `SwitchFooterHints` | Vanilla games first, then store hacks |
| Todos os Jogos (Grid) | `SwitchGridScreen` | All entries together (vanilla + hacks + seeds), search/filter |
| Store | `SwitchSidePanel` for filters/sort? Or fullscreen grid with Switch cards | Store hacks as Switch cards; detail bottom sheet → SwitchDialog style |
| Settings (Quick) | `SwitchSidePanel` (right slide-in) | Quick shortcuts: theme toggle, RA profile status, link to full Settings |
| Settings (Full) | `SwitchGridScreen` or `SwitchSidePanel` fullscreen | Existing SettingsActivity restyled entirely |
| RetroAchievements | `SwitchGridScreen` (games with RA), `SwitchDialog` (detail), `SwitchDialog` (leaderboards) | In-game overlay = custom Switch-style toast |
| GameActivity In-Game Menu | `SwitchDialog` (pause menu), `SwitchDialog` (leaderboards), custom Switch-style overlay (achievement unlock, ocarina HUD) | **RadialGamePad touch layout FROZEN (Rule 14)** — only chrome restyled |

### Sound Rules
- **Required SFX**: focus-move ("toc"), select, back, panel open, panel close.
- **Source**: CC0 or synthesized only. **NEVER extract from NS_Launcher APK** (copyright).
- **Storage**: `res/raw/sfx_focus_move.ogg`, `sfx_select.ogg`, `sfx_back.ogg`, `sfx_panel_open.ogg`, `sfx_panel_close.ogg`.
- **Volume**: Respects system media volume; mute toggle in Settings side panel.
- **Implementation**: `SfxManager` (SoundPool) — low latency, preloaded.

### Licensing Guard
- UI is an **original native implementation** inspired by the Switch aesthetic.
- **NEVER commit Nintendo assets/fonts/sounds** or files extracted from the NS_Launcher APK (extends Hard Rule 2).
- FLauncher is GPLv3 — consulting its code is allowed; this project stays GPLv3.
- All generated assets (Dolfi) are original, CC0/public domain or GPL-3.0 compatible.

### i18n / No-Emoji
- Hard Rules 7–8 still apply: every user-facing string in `strings.xml` (pt-BR default `values/`, `values-en/`, `values-es/`), zero hardcoded strings, no emojis in code/resources.

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
│   ├── ui/              # SettingsActivity, Fragments (BaseRomImport, BaseRomList, CatalogUrl, RetroAchievements)
│   └── SettingsViewModel.kt
├── repositories/
│   ├── Storage.kt       # Paths per hackId: rom_<id>, sram_<id>, state_<id>
│   └── GameRomResolver.kt  # Single ROM resolver: vanilla_* via BaseRomRepository; else Storage.rom(hackId)
├── retroview/           # Emulation (adapted from Ludere)
│   ├── RetroView.kt     # INTERCEPTION POINT: loads patched ROM from cache
│   └── RetroViewUtils.kt
├── views/
│   ├── LibraryActivity.kt
│   ├── GameActivity.kt
│   ├── BaseRomLibrarySource.kt
│   └── viewmodels/GameActivityViewModel.kt
├── gamepad/             # RadialGamePad — UNCHANGED from Ludere
├── input/               # ControllerInput, InputMapper — UNCHANGED
├── utils/               # CorePrefs, RetroViewUtils — UNCHANGED
├── di/AppContainer.kt   # Service Locator
├── retroachievements/   # RetroAchievements Integration feature
│   ├── jni/             # RcheevosJni, LibretroDroidMemoryJni (JNI bridges)
│   ├── api/             # RaHttpClient, RaApiModels, RaApiException
│   ├── auth/            # RaCredentialStore, RaSessionManager, RaLoginFragment
│   ├── data/            # RaGameMetadata, RaAchievement, RaLeaderboard, RaRepository
│   ├── ui/              # AchievementsActivity, AchievementDetailActivity, InGameAchievementOverlay, LeaderboardDialog
│   ├── viewmodel/       # AchievementsViewModel, InGameRaViewModel
│   └── install/         # RaHashService, RaInstallRepository
├── ocarina/             # Auto-Ocarina feature
│   ├── OcarinaNote.kt           # Enum A/C_UP/C_DOWN/C_LEFT/C_RIGHT → raw keycodes
│   ├── OcarinaSong.kt           # Song model + tolerant JSON parsing
│   ├── OcarinaSongCatalog.kt    # Built-in songs (OoT: 12, MM: 11) + catalog custom merge
│   ├── OcarinaMacroPlayer.kt    # Coroutine sequencer → GLRetroView key events (~330ms/note)
│   └── ui/OcarinaHudView.kt     # Overlay: song name + highlighted note chips during playback
├── capture/             # Screen capture + screen recording (see plano.md "Captura de Tela, Gravação e Galeria")
│   ├── CaptureManager.kt        # PixelCopy screenshot (with/without overlay compositing) + bridge to recording
│   ├── CaptureService.kt        # Foreground Service: MediaProjection + MediaRecorder
│   ├── RecordingIndicatorView.kt# Switch-style recording-active indicator
│   └── CapturePreferences.kt    # Reads pref_capture_include_overlay
├── gallery/             # Gallery screen (Switch UI): view / share / delete captures
│   ├── GalleryRepository.kt     # List/delete items in galleryDir; FileProvider share URI
│   ├── GalleryItem.kt           # Model: type, path, hackId, timestamp, withOverlay
│   ├── GalleryActivity.kt       # SwitchGridScreen-style gallery
│   ├── GalleryAdapter.kt        # RecyclerView adapter for gallery items
│   └── GalleryViewModel.kt      # StateFlow of gallery items + actions
├── ui/switchui/         # Nintendo Switch UI components (native Kotlin, hand-styled)
│   ├── SwitchHomeRow.kt
│   ├── SwitchGameCard.kt
│   ├── SwitchAllGamesCard.kt
│   ├── SwitchGridScreen.kt
│   ├── SwitchDock.kt
│   ├── SwitchFooterHints.kt
│   ├── SwitchSidePanel.kt
│   ├── SwitchDialog.kt
│   ├── SwitchFocusBorder.kt
│   ├── SfxManager.kt
│   └── ThemeManager.kt
├── libretrodroid/       # LOCAL GRADLE MODULE (vendor LibretroDroid 0.13.2 + 2 JNI passthroughs)
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
14. **Gamepad layout is FROZEN**: the Zelda-tailored control layout (`gamepad/GamePadConfig.kt` placements, ButtonStick + Auto mode, Auto-Z double-tap, FloatingJoystick region/hint/reach, DoubleTapContainer, physical-controller mirroring, sensitivity dialogs) is measured from a reference image and tuned for OoT/MM. Migrate verbatim (package rename only). Any layout change requires explicit user approval and an update to the "Layout de Controles Sob Medida" section of `plano.md`. The integration invariants must not regress: overlay uses INVISIBLE (never GONE), pads created only after first real layout pass, GL-context recovery via full Activity recreate(), and `super.onDestroy()` called BEFORE dispose. **In-game menu chrome, overlays, pause menu, achievement overlay, leaderboard dialog, and ocarina HUD ARE restyled to the Nintendo Switch UI standard** — only the RadialGamePad touch-control LAYOUT (control placement, button-stick modes, floating joystick, auto-Z, physical-controller mirroring) remains frozen per this rule.

### Security / Privacy
15. **No telemetry without opt-in**. No analytics, crash reporting, or network calls except: catalog fetch (user-initiated), patch download (user-initiated), core download (build-time only).
16. **Validate all inputs**: ROM checksums, patch checksums, catalog JSON schema, downloaded file sizes.

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
| **Bruce** 🦈 | **Primary Implementer** — all Kotlin/Android code (Phases 0–4 + RetroAchievements B1–B5 + Switch UI Revamp + **Phase 5 Multi-Store**) | All implementation tasks: patcher, store, settings, retroview, UI, retroachievements, Switch UI, multi-store catalog |
| **Dolfi** 🐬 | Icons/covers — generates SVG icons (app icon, hack category icons, RA trophy/leaderboard icons, Switch dock icons, focus assets) and PNG cover placeholders for hacks without `coverImageUrl`; Zelda-gold splash artwork; **Gallery/capture icons** (`ic_gallery`, `ic_screenshot`, `ic_record`, `ic_stop`) | When UI needs icons, cover art, or splash art |
| **Wally** 🐋 | Documentation — finalizes `README.md`, translates `strings.xml` (pt-BR/en/es), writes code docs (KDoc) | After implementation phases, before release |
| **Calamari** 🦑 | Fact-checking — validates known ROM checksums (No-Intro/Redump), verifies LibretroDroid/core versions, checks BPS spec details, validates OoT 1.0 checksums and N64 boot CRC algorithm, validates sound asset licensing | When Bruce needs verified data |
| **Puffy** 🐡 | Research — up-to-date LibretroDroid releases, core buildbot URLs, Android API changes, Gradle plugin updates, Android TV focus handling, SoundPool latency | When Bruce needs current docs |
| **Chululu** 🐙 | Visual QA — analyzes screenshots of Library/Store/Game/RetroAchievements UI for layout, alignment, accessibility; **Nintendo Switch UI compliance verification** (including new store selector + detail dialog) | Before UI merges, release candidates |

**Agents NOT involved**: InnerLinho (PHP), Fishie (Web frontend), Peep (Flutter), Snowflake (C#), Snuggle (Python), Nodi (Node.js), Ariel (Content), Tucso (Linux scripts).

---

## Delegation Mapping (This Project)

| Task Type | Delegate To |
|-----------|-------------|
| Android/Kotlin implementation (all phases + Phase 5 Multi-Store) | **Bruce** |
| Architecture changes / plan updates | **Coral** |
| App icon, hack category icons, cover placeholders, RA icons, Switch dock icons, splash art, **Gallery/capture icons** (`ic_gallery`, `ic_screenshot`, `ic_record`, `ic_stop`) | **Dolfi** |
| README, strings translation, KDoc | **Wally** |
| ROM checksum verification, core version check, rcheevos release validation, sound licensing | **Calamari** |
| LibretroDroid/core/Android API research, rcheevos API docs, Android TV focus, SoundPool | **Puffy** |
| UI screenshot analysis, Switch UI compliance (including new store selector + detail dialog) | **Chululu** |

---

## Hylian Modding Catalog Format

### Endpoints (Verified)
| Source | Endpoint |
|--------|----------|
| Main Index | `GET https://hylianmodding.com/mods/index.json` → `{"mods": [slugs]}` |
| Per-Mod | `GET https://hylianmodding.com/mods/{slug}/mod.json` |
| Competition Index | `GET https://hylianmodding.com/competitions/{slug}/index.json` → `{"mods": [slugs]}` |
| Competition Per-Mod | `GET https://hylianmodding.com/competitions/{slug}/{mod}/mod.json` |

**Corrected Competition Slugs**: `2025-crossover`, `2024-horror`, `2023-escape-room`, `hm-jam-1`.

### mod.json Schema (All Optional Except id/name)
```json
{
  "id": "string",
  "name": "string",
  "authors": ["string"],
  "description": "string",
  "category": "string",
  "supported_games": ["OoT" | "MM"],
  "compatibility": "string",
  "completion_status": "string",
  "thumbnail_image": "relative/path.png",
  "screenshots": ["relative/path1.png", "relative/path2.png", ""],
  "download_link": "string",  // relative patch path, GitHub releases URL, or HTML URL
  "last_updated": "ISO8601",
  "is_update": true,
  "timestamp": 1234567890,
  "changelog": [{"date": "ISO8601", "content": "string"}]
}
```
- **NO video field** — parser tolerant if added later.
- Relative URLs resolve against `https://hylianmodding.com`.
- `screenshots` may contain empty strings — filter them.

---

## Multi-Store Architecture

- **StoreDefinition**: `id`, `displayName`, `sources: List<CatalogSourceMeta>`
- **Built-in stores** (StoreDefinitions.kt):
  - `picks` (Zelda 64 Picks): single PICKS source = `DEFAULT_CATALOG_URL`; `storeName` from catalog.json
  - `hylianmodding` (Hylian Modding): 5 HYLIANMODDING sources (main + 4 competitions)
- **Custom catalog URLs** merge into PICKS store (backward compat).
- **CatalogParser interface**: `PicksCatalogParser` (existing + storeName, stamps `storeId="picks"`), `HylianModdingParser` (tolerant, `hm_` ID prefix, builds `DownloadTarget`).
- **DownloadTarget sealed hierarchy**: `DirectPatch(PatchRef)` | `GitHubRelease(repoUrl)` | `ExternalLink(url)`.
- **GitHubPatchResolver**: resolves at download time via GitHub Releases API; fallback to browser.
- **MergedCatalogRepository** preserves `storeId` + `sourceCatalogId`; Store UI filters by selected store.
- **HackLibraryEntry** gains `storeId` field.
- **catalog.json** gets top-level `storeName: "Zelda 64 Picks"`.

---

## Cross-Catalog Hack Dedupe (Phase 6)

### Canonical Identity (`canonicalId`)
- **Definition**: Stable, store-agnostic hack identifier derived by slug normalization + explicit alias map.
- **Slug Normalization**: `lowercase().removePrefix("hm_").replace(Regex("[^a-z0-9]+"), "")`.
  - Examples: `"hm_themissinglink"` → `"themissinglink"`, `"the-missing-link"` → `"themissinglink"`.
- **Alias Map**: `catalog/aliases.json` (separate file, versioned).
  ```json
  { "version": 1, "aliases": { "hm_themissinglink": "the-missing-link", "hm_ocarinaoftime3d": "ocarina-of-time-3d" } }
  ```
  - Keys = raw HM ids (with `hm_`); Values = PICKS bare-slug ids (canonical form).
  - Unidirectional HM → PICKS; PICKS slugs are canonical by definition.
- **Resolver**: `CanonicalIdResolver.resolve(rawId, storeId)` → applies alias map then normalization.
- **HackEntry.canonicalId**: Read-only property delegating to `CanonicalIdResolver`.

### Install-Time Patch Checksum Persistence
- **Extended `InstalledHack`** (in `InstalledHacksRepository`, file `filesDir/installed_hacks.json`):
  ```kotlin
  data class InstalledHack(
      val hackId: String,
      val version: String,
      val fileName: String,
      val canonicalId: String,           // NEW
      val patchChecksums: Checksums? = null  // NEW: CRC32/MD5/SHA1 of downloaded BPS
  )
  ```
- **Capture point**: `DownloadManager.download` (after `PatchValidator.validate`, compute from `bpsBytes`) and `ImportedPatchInstaller.install` (from `patchFile`).
- **Migration**: On load, missing `canonicalId` backfilled via catalog lookup (fallback `normalizeSlug(hackId)`); `patchChecksums` = `null` for legacy.

### Cross-Catalog Recognition
- **`isSameHack(a, b)`**:
  ```kotlin
  fun isSameHack(a: HackEntry, b: HackEntry): Boolean {
      if (a.canonicalId == b.canonicalId) return true
      val ca = a.patch?.checksums; val cb = b.patch?.checksums
      return ca != null && cb != null && ca.crc32 == cb.crc32 && ca.md5 == cb.md5 && ca.sha1 == cb.sha1
  }
  ```
  - Primary: `canonicalId` match.
  - Fallback: both have non-empty checksums AND all three digests match.
  - `canonicalId` match + checksum mismatch → same hack, different patch version (UI note).

### Library Grouping
- `CatalogBackedLibrarySource.available()` groups by `canonicalId` → one `HackLibraryEntry` per hack.
- `HackLibraryEntry.id = canonicalId` (stable key for launch).
- Representative picked: prefer PICKS entry, else first HM source.
- Vanilla games (`vanilla_*`) excluded (handled by `BaseRomLibrarySource`).

### Store Install-State Recognition
- `StoreViewModel.statusFor` checks **any installed hack** with same `canonicalId` OR matching checksums.
- "Installed" badge appears in **both stores** when hack installed from either.
- Detail dialog shows note: `"Instalado como \"{otherName}\" (versão {otherVersion})"` when matched via canonicalId but not exact id.

### Storage Path Keying
- At install time, patched ROM written to `Storage.rom(canonicalId)` (not `hack.id`).
- Ensures **one ROM file per hack** regardless of store origin.
- `GameRomResolver` unchanged (receives `canonicalId` from Library).

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
- **Hylian Modding**: https://hylianmodding.com
- **GitHub Releases API**: https://docs.github.com/en/rest/releases/releases

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