# Zelda 64 Player

Native Android (Kotlin) player for fan-made Zelda 64 ROM hacks. Users provide their own legally-owned Ocarina of Time / Majora's Mask base ROMs; the app applies BPS (and legacy IPS) patches on-the-fly and plays them via Libretro cores (mupen64plus-next GLES3 and parallel-n64). No ROMs are embedded or distributed.

The app is a LibretroDroid-powered frontend for Nintendo 64 Zelda hacks (Ocarina of Time, Majora's Mask) with on-the-fly BPS patching of user-provided base ROMs. The app never ships, downloads, or includes base ROMs. Users must legally import their own Ocarina of Time and Majora's Mask ROMs.

---

## Legal / Disclaimer

The app **never** embeds, downloads, or distributes base ROMs. Users must legally import their own Ocarina of Time and Majora's Mask ROMs via **Settings → Import Base ROM**. Only BPS patches and catalog metadata are hosted/distributed. The app is GPL-3.0 licensed (derivative of Ludere). The BPS implementation is clean-room, based on the public BPS spec only — no GPL code from rom_patcher_js or UniPatcher is used. Users are responsible for ensuring their base ROM matches the hack's required version.

---

## Features

- **Hack Store** with GitHub-hosted JSON catalog (multi-catalog merge, ETag caching for incremental updates).
- **On-the-fly patching** with triple CRC32 validation (source checksum, patch checksum, target CRC after patch application), supporting BPS and legacy IPS formats.
- **N64 byte-order normalization** (.z64 / .v64 / .n64 → big-endian z64) via the pure Kotlin N64 normalizer module.
- **Checksum-based base ROM matching** using gameCode + versionByte + CRC32 to validate that the user's base ROM is compatible with a given hack before applying patches.
- **Auto-Ocarina** — in-game HUD that auto-plays Ocarina songs in OoT/MM from the pause menu. Built-in song catalog (OoT: 12 songs, MM: 11) plus optional per-hack custom songs from the catalog `ocarinaSongs` field. Coroutine sequencer sends key events to GLRetroView (~330ms/note), cancellable by any user input, menu open, or lifecycle changes. Game detection at launch via `RomHeader.gameCode` prefix: `CZL*` = OoT family, `NZL*`/`NSM*` = MM family. Unknown games hide the menu item.
- **RetroAchievements** — sign in with your RetroAchievements.org account; achievements are tracked during play with unlock popups, challenge/progress indicators, and system notifications. Leaderboards are accessible only inside the in-game menu. The RA hash is computed exclusively from the final patched ROM at install time, never from the base ROM or uncompressed intermediates. RA credentials (username/password/token) are never logged (sanitized as `***`) and are stored only in EncryptedSharedPreferences in a separate `ra_secure_prefs` file. Hardcore mode defaults OFF (`pref_ra_hardcore = false`) until User-Agent validated with RAdmin. System notifications opt-in default ON but require `POST_NOTIFICATIONS` runtime permission on API 33+.
- **Nintendo Switch-style UI revamp** — all screens follow a custom native implementation of the Nintendo Switch HOME menu aesthetic (inspired by NS_Launcher / FLauncher). Design tokens include dark background `#2D2D2D`, cyan accent `#00BCD4`, amber `#FFA000`, square game cards, focus borders, side panels, and dock. The RadialGamePad touch-control LAYOUT remains frozen (measured for OoT/MM control placement, ButtonStick modes, Auto-Z, FloatingJoystick, physical-controller mirroring); only chrome (menus, dialogs, overlays) is restyled to the Switch standard. Sound effects (focus-move "toc", select, back, panel open/close) are CC0/synthesized only, stored in `res/raw/`, and respect system volume with a mute toggle in Settings.
- **Vanilla Games in Library** — user-imported base ROMs (OoT, MM) are playable directly from the main Library screen, separate from Store hacks. New `BaseRomLibrarySource` exposes `BaseRomRepository` entries as playable tiles with `vanilla_<crc32>` IDs. Grid order: vanilla games first, then store hacks. `GameRomResolver.kt` handles resolution: `vanilla_*` resolves via `BaseRomRepository`; everything else falls back to `Storage.rom(hackId)`. All launch paths route through the resolver. Vanilla tiles carry a VANILLA badge (ice-cream icon) with neutral chip background. Context menu omits management sections (uninstall/delete-seed) for vanilla entries — base ROMs are managed in Settings. RetroAchievements for vanilla games have no install step; RA identity (rhash + game id) is computed lazily on first play.
- **Custom gamepad layout** (RadialGamePad) — C-button mapping, Auto-Z, ButtonStick modes, FloatingJoystick. The layout is frozen/measured from a reference image tuned for OoT/MM; the RadialGamePad package is unchanged from Ludere and must not be modified without explicit user approval and an update to the "Layout de Controles Sob Medida" section of `plano.md`.
- **Save backup/restore** via ZIP (local, no cloud). Backups stored in `filesDir` per hackId.
- **Background catalog refresh** via WorkManager (`CatalogRefreshWorker`, 12h periodic, CONNECTED network requirement).
- **i18n** (pt-BR default, English, Spanish) — all user-facing strings externalized to `strings.xml` (pt-BR in `values/`, en in `values-en/`, es in `values-es/`). Zero hardcoded strings in Kotlin/XML layouts. The embedded OoTR WebView generator renders its own English UI (canonical OoTR community terminology); only the app's native chrome uses `strings.xml`.

---

## Build Instructions

**Requirements:** Android Studio (latest), JDK 17, Android SDK 34.

```bash
git clone https://github.com/zonaro/zelda64player.git
cd zelda64player
./gradlew assembleDebug
```

The first build runs a `prepareCore` Gradle task that downloads the two Libretro cores into `app/src/main/jniLibs` (needs internet): `mupen64plus_next` (GLES3) is fetched as a zip from buildbot.libretro.com, and `parallel_n64` is fetched from the self-built rolling release (see `.github/workflows/build-parallel-n64.yml`, which builds updated dynarec binaries from libretro/parallel-n64 with the current NDK) and falls back to the buildbot nightly zip if unavailable. Cores already present in `jniLibs` are not re-downloaded, and any core unavailable for a given ABI is skipped gracefully.

---

## Usage Quick-Start

1. Open **Settings** → **Import Base ROM** and select your legally-owned OoT or MM ROM (.z64 / .v64 / .n64).
2. Browse the **Store** → view available hacks and download one.
3. The app validates the base ROM checksum, downloads the BPS (or ZIP) patch, applies it, and launches the game.
4. To play a vanilla base ROM without patches, select it from the Library screen.
5. To use Auto-Ocarina, start a game and open the pause menu — the HUD will auto-play songs from the built-in catalog or per-hack custom songs.

---

## For Hack Authors

See **docs/CATALOG.md** for the JSON catalog schema and **docs/catalog.example.json** for a ready-to-adapt example with two hacks. Catalog PRs are welcome — add your hack by submitting a PR with a `catalog.json` that follows the schema (required fields: `id`, `name`, `description`, `author`, `version`, `baseRom.name`, `baseRom.gameCode`, `baseRom.versionByte`, `baseRom.checksums.crc32`, `patch.url`, `patch.filename`, `patch.size`, `patch.checksums.crc32`).

---

## Tech Stack Summary

| Layer | Technology | Notes |
|-------|------------|-------|
| Language | Kotlin 1.9+ | JVM target 1.8 |
| Min SDK | Android API 24 | Raised from 21; <1% API 21-23 share |
| Target/Compile SDK | Android API 35 | Play Store requirement (Aug 2025+) |
| Build System | Gradle 8.x | Kotlin DSL (`build.gradle.kts`) |
| Architecture | MVC-ish | View (Activity+ViewBinding) → ViewModel (AndroidViewModel) → Model (Repository+UseCase) |
| DI | Manual Service Locator | `AppContainer` in `Application` subclass; no Dagger/Hilt |
| Emulation | LibretroDroid 0.13.2 | Vendored local Gradle module `:libretrodroid` (source from tag 0.13.2) + 2 JNI passthroughs for memory access; cores fetched at build time |
| Touch Controls | RadialGamePad 0.6.0 | Frozen package — package rename only; layout tuned for OoT/MM, must not be modified without user approval |
| Networking | OkHttp 4.12+ | Catalog fetch, patch download, RA web API |
| Image Loading | Coil 2.6.0 | Kotlin-first, coroutines, lightweight |
| Persistence | JSON (files) + EncryptedSharedPreferences | BaseRomRepository: JSON in `filesDir`; PatchRepository: files in `cacheDir`; RA metadata: JSON in `filesDir`; API keys in secure prefs |
| Coroutines | kotlinx-coroutines 1.8+ | `lifecycle-runtime-ktx` for ViewModelScope |
| Background Tasks | WorkManager 2.9.0 | `CatalogRefreshWorker` (12h periodic) |
| RetroAchievements | rcheevos ~12.x (master tag) | MIT licensed, ANSI C; git subtree in `app/src/main/cpp/rcheevos/`; JNI bridge `libra_jni.so` |
| UI Toolkit | Custom Nintendo Switch skin | Material Components 1.14.0 is technical base only; visual standard is custom Switch HOME menu aesthetic (dark bg #2D2D2D, cyan accent #00BCD4, amber #FFA000, square cards, focus borders, dock) |
| Testing | JUnit 5, MockK, Turbine | Unit + Instrumented; JVM unit tests covering patcher N64 normalization, BPS parsing/validation, checksums, PatcherFacade integration, (RomZipExtractor) and RetroAchievements identity/catalog parsing. Fixtures use synthetic ROMs and patches; no real ROMs are committed.|

---

## Project Structure (Package Map)

```
br.com.redclaw.zelda64player
├── data/           # Models + local repositories
│   ├── model/      # BaseRom, HackEntry, HackCatalog, PatchFile, Checksums
│   └── local/      # BaseRomRepository, PatchRepository, CatalogRepository, SaveBackupManager
├── patcher/        # PURE KOTLIN MODULE (no Android deps)
│   ├── bps/        # BPS parser, applier, validator, varint
│   ├── ips/        # IPS applier (legacy format)
│   ├── n64/        # RomNormalizer, RomHeader, ChecksumCalculator
│   └── PatcherFacade.kt # applyPatch API with BPS/IPS auto-detection
├── store/          # Hack Store feature
│   ├── ui/         # StoreActivity, ViewModel, Adapter, BottomSheet
│   ├── DownloadManager.kt
│   ├── CatalogFetcher.kt
│   └── CatalogRefresher.kt # shared by ViewModel + WorkManager worker
├── settings/       # SettingsActivity (ROM import/list, catalog URLs, backups, RA login) + helpers
│   ├── ui/              # SettingsActivity, Fragments (BaseRomImport, BaseRomList, CatalogUrl, RetroAchievements)
│   └── SettingsViewModel.kt
├── work/           # CatalogRefreshWorker (periodic background refresh)
├── repositories/   # Storage (per-hack ROM/SRAM/state paths) + GameRomResolver
│   ├── Storage.kt       # Paths per hackId: rom_<id>, sram_<id>, state_<id>
│   └── GameRomResolver.kt  # Single ROM resolver: vanilla_* via BaseRomRepository; else Storage.rom(hackId)
├── retroview/      # Emulation (adapted from Ludere)
│   ├── RetroView.kt     # INTERCEPTION POINT: loads patched ROM from cache
│   └── RetroViewUtils.kt
├── views/
│   ├── LibraryActivity.kt     # Grid of installed hacks + vanilla games
│   ├── GameActivity.kt        # Gameplay activity
│   ├── BaseRomLibrarySource.kt
│   └── viewmodels/GameActivityViewModel.kt
├── gamepad/        # RadialGamePad — UNCHANGED from Ludere
├── input/          # ControllerInput, InputMapper — UNCHANGED
├── utils/          # CorePrefs, RetroViewUtils — UNCHANGED
├── di/AppContainer.kt   # Service Locator
├── libretrodroid/       # LOCAL GRADLE MODULE (vendor LibretroDroid 0.13.2 + 2 JNI passthroughs)
```

**Testing:** `./gradlew :app:testDebugUnitTest` — JVM unit tests covering patcher N64 normalization, BPS parsing/validation, checksums, PatcherFacade integration and RetroAchievements identity/catalog parsing. Fixtures use synthetic ROMs and patches; no real ROMs are committed.

**Roadmap:** See **plano.md** (pt-BR). Shipped: BPS + IPS patching, hack store, settings, save backup/restore, background catalog refresh, Auto-Ocarina, RetroAchievements, Nintendo Switch UI revamp, Vanilla Games in Library. Remaining stretch goals (post-MVP): multiple save slots per hack, optional opt-in telemetry (privacy-first, deferred), hardcore mode enablement pending RAdmin User-Agent validation.

---

## Credits

- **Libretro / RetroArch** cores (mupen64plus-next, parallel-n64)
- **mupen64plus** — original emulation engine
- **parallel-n64** — accuracy-focused Libretro core
- **RadialGamePad** — custom touch gamepad library
- **LibretroDroid** — Android embedding framework
- **rcheevos** (MIT) — RetroAchievements client library powering the achievements integration
- **RetroAchievements.org** — achievement database and web API
- **BPS format** — by byuu / rombp (MIT reference implementation consulted for clean-room)
- **Inspired by** the romhacking community and the original Ludere project (GPL-3.0)
- **Dolfi** — SVG icon and cover art generation for the project

---

## License

GPL-3.0 — See [LICENSE](LICENSE). This project is a derivative of Ludere (GPL-3.0). Full source must be offered; keep the LICENSE file and copyright headers intact.

---

## Project Rules Summary (Hard Rules)

| # | Rule | Summary |
|---|------|---------|
| 1 | No base ROMs | Never embed, download, or distribute base ROMs (OoT, MM). |
| 2 | No copyrighted content | Only patches (BPS) and metadata are distributed; no ROMs, BIOS, proprietary assets. |
| 3 | GPL-3.0 compliance | Entire app must be licensed GPL-3.0; keep LICENSE file, preserve copyright headers, offer source code. |
| 4 | Clean-room BPS | BPS implemented from public spec only; do not copy from rom_patcher_js or UniPatcher. |
| 5 | DRY | Extract shared logic into `utils/`, `patcher/`, or `di/`. |
| 6 | Modular | Small focused classes/functions, single responsibility. |
| 7 | No emojis | In code, file paths, resource names, or technical output. |
| 8 | i18n mandatory | All user-facing strings in `strings.xml` (pt-BR default, en, es). Zero hardcoded strings. |
| 9 | Performance | Default `config_load_bytes=false`; stream from file; avoid loading full 32–64 MB ROMs into heap. |
| 10 | RetroView interception | The ONLY place ROM bytes reach the core is `RetroView.kt` → `GLRetroViewData`. |
| 11 | Storage paths keyed by hackId | Each hack gets isolated `rom_<hackId>`, `sram_<hackId>`, `state_<hackId>`. |
| 12 | Catalog schema versioning | `catalogVersion` integer in JSON; handle migrations gracefully. |
| 13 | Selective migration | Copy from Ludere only specified modules; do NOT migrate data/Games.kt, bundled covers, ROM packaging machinery, `autogen/`, Ludere keystore, or ABI splits. |
| 14 | Gamepad layout FROZEN | RadialGamePad layout measured for OoT/MM; no changes without user approval + `plano.md` update. In-game chrome restyled to Switch UI. |
| 15 | No telemetry without opt-in | No analytics, crash reporting, or network calls except user-initiated catalog fetch, patch download, core download (build-time only). |
| 16 | Validate all inputs | ROM checksums, patch checksums, catalog JSON schema, downloaded file sizes. |
| 17 | i18n exception for WebView generator | The OoTR generator site renders its own English UI; only the app's native chrome uses `strings.xml`. |
| 20 | RA credentials | Never logged; stored only in `EncryptedSharedPreferences` (`ra_secure_prefs`). |
| 21 | RA hash from final patched ROM | Computed at install time after BPS/ZPF patch applied and ROM written to `Storage.rom(hackId)`. |
| 22 | Leaderboards never overlaid on gameplay | Accessible ONLY via GameActivity in-game menu (DialogFragment). |
| 23 | Hardcore mode defaults OFF | `pref_ra_hardcore = false` until RAdmin User-Agent validated. |
| 24 | License notices | rcheevos (MIT) license file kept in `app/src/main/cpp/rcheevos/LICENSE`. |
| 25 | System notifications opt-in default ON | Requires `POST_NOTIFICATIONS` runtime permission on API 33+; first unlock triggers permission request if needed. |