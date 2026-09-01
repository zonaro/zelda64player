# Architecture — Zelda 64 Player

**Package:** `br.com.redclaw.zelda64player`
**Base project:** Ludere (fork of Swordfish90/Ludere, GPL-3.0) at `/mnt/GIT/Ludere`
**App name:** "Zelda 64 Player"

---

## 1. Overview

Native Android (Kotlin) emulator frontend for Nintendo 64 **Zelda ROM hacks** (Ocarina of Time, Majora's Mask) with **on-the-fly BPS/IPS patching** of user-provided base ROMs.

**Core philosophy:** The app NEVER ships, downloads, or includes base ROMs. Users legally import their own OoT/MM ROMs. The app downloads only BPS patches + catalog metadata and applies them in cache before emulation.

**Why not Flutter?** This is a direct derivative of an existing native Kotlin app (Ludere) with years of tuning on LibretroDroid integration, RadialGamePad touch controls, GL lifecycle, and core configuration. Rewriting in Flutter would discard that work and introduce regression risk. The global "cross-platform apps use Flutter" rule does NOT apply to native derivatives.

---

## 2. Stack Decisions (Final)

| Layer | Technology | Version | Notes |
|-------|------------|---------|-------|
| Language | Kotlin | 1.9+ | JVM target 1.8 |
| Min SDK | Android API | 24 (Nougat) | Raised from 21; <1% API 21-23 share |
| Target SDK | Android API | 35 | Play Store requirement (Aug 2025+) |
| Compile SDK | Android API | 35 | Required for Material Components 1.14.0 (technical base only) |
| Build System | Gradle | 8.x | Kotlin DSL (`build.gradle.kts`), version catalogs (`libs.versions.toml`) |
| Architecture | MVC-ish | — | View (Activity+ViewBinding) → ViewModel (AndroidViewModel) → Model (Repository+UseCase) |
| DI | Manual Service Locator | — | `AppContainer` in `Application` subclass; no Dagger/Hilt |
| Emulation | LibretroDroid | 0.13.2 | **Vendored local module `:libretrodroid`** (source from tag 0.13.2) + 2 JNI passthroughs for memory access; cores fetched at build time |
| Touch Controls | RadialGamePad | 0.6.0 | Included as module or AAR |
| Networking | OkHttp | 4.12+ | Already transitive via LibretroDroid |
| Image Loading | Coil | 2.6+ | Kotlin-first, coroutines, lightweight |
| Persistence | JSON (files) + Room (optional) | — | BaseRomRepository: JSON in `filesDir`; PatchRepository: files in `cacheDir`; RA metadata: JSON in `filesDir` |
| Coroutines | kotlinx-coroutines | 1.8+ | `lifecycle-runtime-ktx` for ViewModelScope |
| Reactive | RxJava/RxAndroid | 2.x | KEPT as-is: frozen `gamepad/` package depends on it (`CompositeDisposable`, `pad.events()`). New code uses coroutines/Flow; do NOT refactor gamepad to Flow |
| Native | CMake + NDK | r26+ | `externalNativeBuild` for rcheevos (MIT) + JNI bridge; ABIs: x86, x86_64, armeabi-v7a, arm64-v8a |
| RetroAchievements | rcheevos | ~12.x (master tag) | MIT licensed, ANSI C; git subtree in `app/src/main/cpp/rcheevos/`; provides `rc_client_t` high-level API + `rapi` standalone |
| Material Components | material3 | 1.14.0 | **Technical base only — visual standard is custom Switch skin (overrides M3 Expressive)** |
| Background Tasks | WorkManager | 2.9.0 | `CatalogRefreshWorker` (12h periodic, CONNECTED network) |
| Testing | JUnit 5, MockK, Turbine | — | Unit + Instrumented |

---

## 3. Package Layout (Actual)

```
br.com.redclaw.zelda64player
├── Zelda64PlayerApp.kt        # Application subclass + AppContainer (Service Locator)
├── data/                     # Models + local repositories
│   ├── model/                # BaseRom, HackEntry, HackCatalog, PatchFile, Checksums
│   └── local/                # BaseRomRepository, PatchRepository, CatalogRepository, SaveBackupManager
├── patcher/                  # PURE KOTLIN MODULE (no Android deps)
│   ├── bps/                  # BPS parser, applier, validator, varint
│   ├── ips/                  # IPS applier (legacy format)
│   ├── n64/                  # RomNormalizer, RomHeader, ChecksumCalculator
│   └── PatcherFacade.kt      # applyPatch API with BPS/IPS auto-detection
├── store/                    # Hack Store feature
│   ├── ui/                   # StoreActivity, ViewModel, Adapter, BottomSheet
│   ├── DownloadManager.kt
│   ├── CatalogFetcher.kt
│   └── CatalogRefresher.kt   # shared by ViewModel + WorkManager worker
├── settings/                 # Responsive Switch Settings
│   ├── ui/                   # SettingsActivity, Fragments (BaseRomImport, BaseRomList, CatalogUrl, RetroAchievements)
│   └── SettingsViewModel.kt
├── work/                     # CatalogRefreshWorker (periodic background refresh)
├── repositories/             # Storage (per-hack ROM/SRAM/state paths) + GameRomResolver
│   ├── Storage.kt            # Paths per hackId: rom_<id>, sram_<id>, state_<id>
│   └── GameRomResolver.kt    # Single ROM resolver: vanilla_* via BaseRomRepository; else Storage.rom(hackId)
├── retroview/                # Emulation (adapted from Ludere)
│   ├── RetroView.kt          # INTERCEPTION POINT: loads patched ROM from cache
│   └── RetroViewUtils.kt
├── views/
│   ├── LibraryActivity.kt    # Switch HOME row, colored dock, RA profile entry, vanilla games
│   ├── GameActivity.kt       # Gameplay activity
│   ├── GamepadTesterActivity.kt  # Physical/N64 input visualizer; never starts a core
│   ├── BaseRomLibrarySource.kt
│   └── viewmodels/GameActivityViewModel.kt
├── viewmodels/               # Shared ViewModels (root-level)
├── gamepad/                  # RadialGamePad — UNCHANGED from Ludere (FROZEN)
├── input/                    # ControllerInput, InputMapper, N64ControllerMapping (shared gameplay/tester)
├── utils/                    # CorePrefs, RetroViewUtils — UNCHANGED
├── ocarina/                  # Auto-Ocarina feature
│   ├── OcarinaNote.kt        # Enum A/C_UP/C_DOWN/C_LEFT/C_RIGHT → raw keycodes
│   ├── OcarinaSong.kt        # Song model + tolerant JSON parsing
│   ├── OcarinaSongCatalog.kt # Built-in songs (OoT: 12, MM: 11) + catalog custom merge
│   ├── OcarinaMacroPlayer.kt # Coroutine sequencer → GLRetroView key events (~330ms/note)
│   └── ui/OcarinaHudView.kt  # Overlay: song name + highlighted note chips during playback
├── capture/                  # Screen capture + screen recording
│   ├── CaptureManager.kt     # PixelCopy screenshot (with/without overlay) + bridge to recording
│   ├── CaptureService.kt     # Foreground Service: MediaProjection + MediaRecorder
│   ├── RecordingIndicatorView.kt  # Switch-style recording-active indicator
│   └── CapturePreferences.kt # Reads pref_capture_include_overlay
├── gallery/                  # Gallery screen (Switch UI): view / share / delete captures
│   ├── GalleryRepository.kt  # List/delete items in galleryDir; FileProvider share URI
│   ├── GalleryItem.kt        # Model: type, path, hackId, timestamp, withOverlay
│   ├── GalleryActivity.kt    # SwitchGridScreen-style gallery
│   ├── GalleryAdapter.kt     # RecyclerView adapter for gallery items
│   └── GalleryViewModel.kt   # StateFlow of gallery items + actions
├── drive/                    # Cloud save backup/restore (Google Drive)
├── shortcuts/                # App shortcuts (dynamic/static)
├── retroachievements/        # RetroAchievements Integration feature
│   ├── jni/                  # RcheevosJni, LibretroDroidMemoryJni (JNI bridges)
│   ├── api/                  # RaHttpClient, RaApiModels, RaApiException
│   ├── auth/                 # RaCredentialStore, RaSessionManager, RaLoginFragment
│   ├── data/                 # RaGameMetadata, RaAchievement, RaLeaderboard, RaRepository
│   ├── ui/                   # AchievementsActivity, AchievementDetailActivity, InGameAchievementOverlay, LeaderboardDialog
│   ├── viewmodel/            # AchievementsViewModel, InGameRaViewModel
│   └── install/              # RaHashService, RaInstallRepository
├── ui/                       # Nintendo Switch UI components (native Kotlin, hand-styled)
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
├── libretrodroid/            # LOCAL GRADLE MODULE (vendor LibretroDroid 0.13.2 + 2 JNI passthroughs)
```

> **Note:** The original `AGENTS.md` referenced `di/AppContainer.kt` and `ui/switchui/`. The actual code places `AppContainer` inside `Zelda64PlayerApp.kt` and the Switch components under `ui/`. This doc reflects the real layout.

---

## 4. Data Flow (ROM → Patch → Cache → Core)

```
User imports base ROM (Settings → Import Base ROM)
   │  validate (normalize + checksum + header) → BaseRomRepository (JSON + cacheDir file)
   ▼
User browses Store → downloads BPS/ZIP patch → PatchRepository (cacheDir/patches/<id>.bps)
   │  validate (PatchValidator: source CRC32, patch CRC32, target CRC32)
   ▼
DownloadManager / ImportedPatchInstaller
   │  PatcherFacade.applyPatch(baseRom, patch, output)  [streaming, no full ROM in heap]
   ▼
Patched ROM written to Storage.rom(hackId)   [or Storage.rom(canonicalId) for cross-catalog]
   │
   ▼
GameRomResolver.resolve(libraryEntryId)  →  path to patched ROM (or vanilla base ROM)
   │
   ▼
RetroView.kt  →  GLRetroViewData.gameFilePath  ← ONLY place ROM bytes reach the core (Rule 10)
   │
   ▼
Libretro core (mupen64plus-next GLES3 / parallel-n64) emulates
```

**Rule 10 (RetroView interception):** The ONLY place ROM bytes reach the core is `RetroView.kt` → `GLRetroViewData.gameFilePath` (or `gameFileBytes`). Default `config_load_bytes=false` (stream from file).

**Rule 11 (Storage paths keyed by hackId):** Each hack gets isolated `rom_<hackId>`, `sram_<hackId>`, `state_<hackId>`. SRAM/save-states never collide. At install time, patched ROM is written to `Storage.rom(canonicalId)` (cross-catalog dedup → one ROM file per hack).

---

## 5. Threading Model

| Component | Thread | Details |
|-----------|--------|---------|
| `rc_client_do_frame` (RA) | **Main thread** | Driven by `GLRetroEvents.FrameRendered` Flow (already on main dispatcher). Called 1× per frame. |
| `read_memory` callback (RA) | **rcheevos thread** (background) | Reads via `LibretroDroidMemoryJni.getMemoryData(RETRO_MEMORY_SYSTEM_RAM)` → `ByteBuffer` → copy to output buffer. Pointer valid only while core running. |
| `server_call` callback (RA) | **Any thread** (OkHttp callback) | `RaHttpClient` does async OkHttp; on response invokes C callback via JNI `nativeServerCallComplete`. Marshaling thread-safe (`AttachCurrentThread` if needed). |
| `event_handler` callbacks (RA) | **rcheevos thread** | Events posted to Main via `Handler(Looper.getMainLooper())` → `InGameRaViewModel` → overlay/notification. |
| `RaHttpClient` (rapi standalone) | **Dispatchers.IO** | Coroutines + OkHttp. Used by AchievementsActivity/ViewModel to fetch without a running core. |
| Teardown / GL destroy | **Main thread** | Order: `super.onDestroy()` BEFORE `dispose()` → dispatch ON_DESTROY frees ~90MB natives. `RaSessionManager` calls `rc_client_unload_game` + `rc_client_destroy` BEFORE core destroyed. `InGameRaViewModel.onCleared()` cleans up. |

**Invalid pointer guard (game unload/reload):** `read_memory` may be called after unload if rcheevos still processing a previous frame → defense: `getMemoryData` returns `null` if core not initialized; `read_memory` returns 0 bytes read (rcheevos treats as read failure, no crash). Torn reads from main thread during achievement evaluation are acceptable in v1 (documented).

---

## 6. Native Build (CMake + rcheevos)

```
app/
├── src/main/cpp/
│   ├── CMakeLists.txt              # App-level: add_subdirectory(rcheevos), link rcheevos + libretrodroid JNI
│   ├── rcheevos/                   # VENDORED rcheevos sources (git subtree pinned to tag)
│   │   ├── include/rc_client.h
│   │   ├── include/rc_api_*.h
│   │   ├── src/rc_client.c, rc_api_*.c, rc_compat.c, md5.c, sha1.c
│   │   └── LICENSE (MIT)           # MUST be kept
│   ├── ra_jni_bridge.c/.h          # Thin JNI: RcheevosJni + LibretroDroidMemoryJni implementations
├── build.gradle.kts                # externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }
└── libretrodroid/                  # Separate Gradle module (vendor)
    ├── build.gradle.kts
    └── src/main/...                # LibretroDroid 0.13.2 + 2 JNI passthroughs
```

**Vendoring strategy:** `git subtree add --prefix=app/src/main/cpp/rcheevos https://github.com/RetroAchievements/rcheevos.git master --squash` (pinned to ~12.x). Updates via `git subtree pull ... --squash`.

**ABI coverage:** x86, x86_64, armeabi-v7a, arm64-v8a (matches `jniLibs`).

**Debug symbols:** `debug` keeps `-g`, disables strip; `release` uses `-O2 -DNDEBUG`, strip enabled (default AGP).

---

## 7. Migration Scope (Ludere Extraction)

**Selective migration only** — copy from Ludere exclusively:
- `retroview/`, `views/GameActivity*` + ViewModel, `gamepad/`, `input/`, `utils/`, `config.xml`, `Storage.kt`, launcher icons (`mipmap-*/ic_launcher*`, `drawable/ic_launcher_foreground.xml`, `values/ic_launcher_background.xml` — reused as-is; custom Zelda-themed icon is a future task).

**Do NOT migrate:** `data/Games.kt` (hardcoded catalog), bundled cover drawables, ROM packaging machinery (`assets/roms/`, `res/raw/rom`, `noCompress` rules), `autogen/`, the autogen GitHub workflow, the Ludere keystore, or ABI splits.

**Gamepad layout is FROZEN (Rule 14):** `gamepad/GamePadConfig.kt` placements, ButtonStick + Auto mode, Auto-Z double-tap, FloatingJoystick region/hint/reach, DoubleTapContainer, physical-controller mirroring, sensitivity dialogs — measured from a reference image, tuned for OoT/MM. Migrate verbatim (package rename only). Any layout change requires explicit user approval + update to `plano.md` "Layout de Controles Sob Medida". Integration invariants must not regress: overlay uses INVISIBLE (never GONE), pads created only after first real layout pass, GL-context recovery via full Activity recreate(), `super.onDestroy()` called BEFORE dispose. **In-game menu chrome, overlays, pause menu, achievement overlay, leaderboard dialog, ocarina HUD ARE restyled to Switch UI** — only the RadialGamePad touch-control LAYOUT remains frozen.

---

## 8. References

- **Master Plan (legacy):** `plano.md` — now superseded by `.agents/` categorized docs (see `RETROACHIEVEMENTS.md`, `STORE.md`, etc.).
- **Per-Agent Rules:** `.agents/*.md` — one file per agent.
- **Global Rules:** `~/.config/opencode/AGENTS.md` — team conventions, delegation patterns.
- **Source Project:** `/mnt/GIT/Ludere` — reference implementation (copy/adapt, don't rewrite from scratch).
- **BPS Spec:** https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md
- **N64 ROM Header:** https://n64brew.dev/wiki/ROM_Header
- **LibretroDroid:** https://github.com/Swordfish90/LibretroDroid
- **RadialGamePad:** https://github.com/Swordfish90/RadialGamePad
- **Hylian Modding:** https://hylianmodding.com
- **GitHub Releases API:** https://docs.github.com/en/rest/releases/releases
