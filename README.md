# Zelda 64 Player

Native Android (Kotlin) player for fan-made Zelda 64 ROM hacks. Users provide their own legally-owned Ocarina of Time / Majora's Mask base ROMs; the app applies BPS (and legacy IPS) patches on-the-fly and plays them via Libretro cores (mupen64plus-next GLES3 and parallel-n64). No ROMs are embedded or distributed.

The app is a LibretroDroid-powered frontend for Nintendo 64 Zelda hacks (Ocarina of Time, Majora's Mask) with on-the-fly BPS patching of user-provided base ROMs. The app never ships, downloads, or includes base ROMs. Users must legally import their own Ocarina of Time and Majora's Mask ROMs.

---

## Legal / Disclaimer

The app **never** embeds, downloads, or distributes base ROMs. Users must legally import their own Ocarina of Time and Majora's Mask ROMs via **Settings → Import Base ROM**. Only BPS patches and catalog metadata are hosted/distributed. The app is GPL-3.0 licensed (derivative of Ludere). The BPS implementation is clean-room, based on the public BPS spec only — no GPL code from rom_patcher_js or UniPatcher is used. Users are responsible for ensuring their base ROM matches the hack's required version.

---

## Features

- **Hack Store** with the GitHub-hosted **Main Store** JSON catalog (ETag caching for incremental updates). It combines curated entries with public metadata imported from Hylian Modding; Hylian Modding is not a separate selectable store.
- **On-the-fly patching** with triple CRC32 validation (source checksum, patch checksum, target CRC after patch application), supporting BPS and legacy IPS formats.
- **N64 byte-order normalization** (.z64 / .v64 / .n64 → big-endian z64) via the pure Kotlin N64 normalizer module.
- **Checksum-based base ROM matching** using gameCode + versionByte + CRC32 to validate that the user's base ROM is compatible with a given hack before applying patches.
- **Auto-Ocarina** — in-game HUD that auto-plays Ocarina songs in OoT/MM from the pause menu. Built-in song catalog (OoT: 12 songs, MM: 11) plus optional per-hack custom songs from the catalog `ocarinaSongs` field. Coroutine sequencer sends key events to GLRetroView (~330ms/note), cancellable by any user input, menu open, or lifecycle changes. Game detection at launch via `RomHeader.gameCode` prefix: `CZL*` = OoT family, `NZL*`/`NSM*` = MM family. Unknown games hide the menu item.
- **RetroAchievements** — sign in with your RetroAchievements.org account; achievements are tracked during play with unlock popups, challenge/progress indicators, and system notifications. Leaderboards are accessible only inside the in-game menu. The RA hash is computed exclusively from the final patched ROM at install time, never from the base ROM or uncompressed intermediates. A full profile screen is available from the Library avatar; it displays the authenticated player's avatar and safe profile fields, with a short-lived local cache. Credential-like response fields are removed before display or caching. RA credentials (username/password/token) are never logged (sanitized as `***`) and are stored only in EncryptedSharedPreferences in a separate `ra_secure_prefs` file. Hardcore mode defaults OFF (`pref_ra_hardcore = false`) until User-Agent validated with RAdmin. System notifications opt-in default ON but require `POST_NOTIFICATIONS` runtime permission on API 33+.
- **Nintendo Switch-style UI revamp** — all screens follow a custom native implementation of the Nintendo Switch HOME menu aesthetic (inspired by NS_Launcher / FLauncher), while retaining the app's green highlight color (`#02830C`), cyan focus treatment, and amber action color. The Library Home uses a selected-game label, a horizontal cover row that preserves the existing 13:9 artwork ratio, a profile entry point, a divider/footer, and a colored-icon dock for Store, RetroAchievements, Gamepad Tester, and Settings. Settings use a fixed category sidebar in landscape and a conventional hamburger drawer with scrim in portrait. The RadialGamePad touch-control LAYOUT remains frozen (measured for OoT/MM control placement, ButtonStick modes, Auto-Z, FloatingJoystick, physical-controller mirroring); only chrome (menus, dialogs, overlays) is restyled to the Switch standard. Sound effects (focus-move "toc", select, back, panel open/close) are CC0/synthesized only, stored in `res/raw/`, and respect system volume with a mute toggle in Settings.
- **Vanilla Games in Library** — user-imported base ROMs (OoT, MM) are playable directly from the main Library screen, separate from Store hacks. New `BaseRomLibrarySource` exposes `BaseRomRepository` entries as playable tiles with `vanilla_<crc32>` IDs. Grid order: vanilla games first, then store hacks. `GameRomResolver.kt` handles resolution: `vanilla_*` resolves via `BaseRomRepository`; everything else falls back to `Storage.rom(hackId)`. All launch paths route through the resolver. Vanilla tiles carry a VANILLA badge (ice-cream icon) with neutral chip background. Context menu omits management sections (uninstall/delete-seed) for vanilla entries — base ROMs are managed in Settings. RetroAchievements for vanilla games have no install step; RA identity (rhash + game id) is computed lazily on first play.
- **Custom gamepad layout and tester** (RadialGamePad) — C-button mapping, Auto-Z, ButtonStick modes, and FloatingJoystick. The Library dock includes a full-screen Gamepad Tester that visualizes physical-controller input or its effective Nintendo 64 mapping without creating an emulation core or changing the touch layout. A shared mapping definition keeps the tester and gameplay input behavior aligned. The layout is frozen/measured from a reference image tuned for OoT/MM; the RadialGamePad package is unchanged from Ludere and must not be modified without explicit user approval (see Hard Rule 14 in `.agents/RULES.md`).
- **Save backup/restore** via ZIP (local, no cloud). Backups stored in `filesDir` per hackId.
- **Background catalog refresh** via WorkManager (`CatalogRefreshWorker`, 12h periodic, CONNECTED network requirement).
- **i18n** (pt-BR default, English, Spanish) — all user-facing strings are externalized to `strings.xml` (pt-BR in `values/`, en in `values-en/`, es in `values-es/`), with zero hardcoded strings in Kotlin/XML layouts.

- **Screen Capture, Recording & Gallery**
  *What it does:* Capturing screen (always 2 images: with/without overlay of controls), recording screen (video; includes or not the overlay according to the "Include controls in recording" toggle in Settings), and Gallery (view, share, and delete captures).
  *How to use:* Via emulator menu (press Back button to open menu → "Captura" section); the Gallery is in the dock of the main screen (5th circular button); the toggle "Include controls in recording" is in Settings → Captura.
  *Technical notes:* Media saved locally on the device only (no upload or telemetry), recording permissions (MediaProjection) requested from the user the first time, compatible with Android API 24+.

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

See **docs/CATALOG.md** for the JSON catalog schema and **docs/catalog.example.json** for a ready-to-adapt example with two hacks. Submit a hack only through the [hack submission issue form](../../issues/new?template=hack-submission.yml); do not open a pull request to add a hack or alter the catalog. Each submission needs `name`, `description`, `author`, `version`, `baseRom`, and either a direct `patch` or `downloadTarget`. Use `downloadTarget` when the publisher provides a GitHub release or external page instead of a verified direct patch. Maintainers validate the submitted information and curate accepted entries in the catalog. The repository importer, `python3 tools/import_hylian_modding.py`, refreshes public Hylian Modding metadata (authors, descriptions, artwork, screenshots, status, changelog, and attribution) into the same Main Store catalog without downloading patch files.

---

## Tech Stack Summary

| Layer              | Technology                                | Notes                                                                                                                                                                                                                                                                                       |
| ------------------ | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Language           | Kotlin 1.9+                               | JVM target 1.8                                                                                                                                                                                                                                                                              |
| Min SDK            | Android API 24                            | Raised from 21; <1% API 21-23 share                                                                                                                                                                                                                                                         |
| Target/Compile SDK | Android API 35                            | Play Store requirement (Aug 2025+)                                                                                                                                                                                                                                                          |
| Build System       | Gradle 8.x                                | Kotlin DSL (`build.gradle.kts`)                                                                                                                                                                                                                                                             |
| Architecture       | MVC-ish                                   | View (Activity+ViewBinding) → ViewModel (AndroidViewModel) → Model (Repository+UseCase)                                                                                                                                                                                                     |
| DI                 | Manual Service Locator                    | `AppContainer` in `Application` subclass; no Dagger/Hilt                                                                                                                                                                                                                                    |
| Emulation          | LibretroDroid 0.13.2                      | Vendored local Gradle module `:libretrodroid` (source from tag 0.13.2) + 2 JNI passthroughs for memory access; cores fetched at build time                                                                                                                                                  |
| Touch Controls     | RadialGamePad 0.6.0                       | Frozen package — package rename only; layout tuned for OoT/MM, must not be modified without user approval                                                                                                                                                                                   |
| Networking         | OkHttp 4.12+                              | Catalog fetch, patch download, RA web API                                                                                                                                                                                                                                                   |
| Image Loading      | Coil 2.6.0                                | Kotlin-first, coroutines, lightweight                                                                                                                                                                                                                                                       |
| Persistence        | JSON (files) + EncryptedSharedPreferences | BaseRomRepository: JSON in `filesDir`; PatchRepository: files in `cacheDir`; RA metadata: JSON in `filesDir`; API keys in secure prefs                                                                                                                                                      |
| Coroutines         | kotlinx-coroutines 1.8+                   | `lifecycle-runtime-ktx` for ViewModelScope                                                                                                                                                                                                                                                  |
| Background Tasks   | WorkManager 2.9.0                         | `CatalogRefreshWorker` (12h periodic)                                                                                                                                                                                                                                                       |
| RetroAchievements  | rcheevos ~12.x (master tag)               | MIT licensed, ANSI C; git subtree in `app/src/main/cpp/rcheevos/`; JNI bridge `libra_jni.so`                                                                                                                                                                                                |
| UI Toolkit         | Custom Nintendo Switch skin               | Material Components 1.14.0 is technical base only; visual standard is a Switch-inspired HOME menu (dark bg #2D2D2D, green app accent #02830C, cyan focus, amber actions, 13:9 home covers, focus borders, dock, responsive Settings navigation).                                            |
| Testing            | JUnit 5, MockK, Turbine                   | Unit + Instrumented; JVM unit tests cover patcher N64 normalization, BPS parsing/validation, checksums, PatcherFacade integration, RetroAchievements identity/catalog parsing, and physical-controller-to-N64 mapping. Fixtures use synthetic ROMs and patches; no real ROMs are committed. |

---

## Project Documentation

Detailed, categorized documentation lives in **`.agents/`**:

- [`.agents/ARCHITECTURE.md`](.agents/ARCHITECTURE.md) — stack, package layout, data flow, threading
- [`.agents/RULES.md`](.agents/RULES.md) — all hard rules (legal, code quality, architecture, security, RA)
- [`.agents/VISUAL_IDENTITY.md`](.agents/VISUAL_IDENTITY.md) — Nintendo Switch UI design system
- [`.agents/FEATURES.md`](.agents/FEATURES.md) — feature index + interaction map
- [`.agents/RETROACHIEVEMENTS.md`](.agents/RETROACHIEVEMENTS.md) — RetroAchievements deep dive
- [`.agents/STORE.md`](.agents/STORE.md) — Main Store store, catalog format, public Hylian Modding metadata import
- [`.agents/PATCHER.md`](.agents/PATCHER.md) — BPS/IPS patcher
- [`.agents/AUTO_OCARINA.md`](.agents/AUTO_OCARINA.md), [`.agents/VANILLA_GAMES.md`](.agents/VANILLA_GAMES.md), [`.agents/CAPTURE_GALLERY.md`](.agents/CAPTURE_GALLERY.md) — feature deep dives
- [`.agents/BUILD.md`](.agents/BUILD.md), [`.agents/I18N.md`](.agents/I18N.md), [`.agents/TESTING.md`](.agents/TESTING.md) — build, i18n, testing

Agent team responsibilities: [`.agents/AGENTS.md`](AGENTS.md) (index).

---

## Testing

```bash
./gradlew :app:testDebugUnitTest                       # JVM unit tests
./gradlew :app:connectedAndroidTest                   # Instrumented tests
./gradlew :app:assembleRelease                        # Release build
```

JVM unit tests cover patcher N64 normalization, BPS parsing/validation, checksums, PatcherFacade integration, RetroAchievements identity/catalog parsing, and physical-controller-to-N64 mappings. Fixtures use synthetic ROMs and patches; no real ROMs are committed.

**Roadmap:** Shipped: BPS + IPS patching, hack store, responsive Switch-style Settings and Library Home, save backup/restore, background catalog refresh, Auto-Ocarina, RetroAchievements profile, Gamepad Tester, Nintendo Switch UI revamp, and Vanilla Games in Library. Remaining stretch goals (post-MVP): multiple save slots per hack, optional opt-in telemetry (privacy-first, deferred), hardcore mode enablement pending RAdmin User-Agent validation.

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

| #   | Rule                                    | Summary                                                                                                                                                     |
| --- | --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | No base ROMs                            | Never embed, download, or distribute base ROMs (OoT, MM).                                                                                                   |
| 2   | No copyrighted content                  | Only patches (BPS) and metadata are distributed; no ROMs, BIOS, proprietary assets.                                                                         |
| 3   | GPL-3.0 compliance                      | Entire app must be licensed GPL-3.0; keep LICENSE file, preserve copyright headers, offer source code.                                                      |
| 4   | Clean-room BPS                          | BPS implemented from public spec only; do not copy from rom_patcher_js or UniPatcher.                                                                       |
| 5   | DRY                                     | Extract shared logic into `utils/`, `patcher/`, or `di/`.                                                                                                   |
| 6   | Modular                                 | Small focused classes/functions, single responsibility.                                                                                                     |
| 7   | No emojis                               | In code, file paths, resource names, or technical output.                                                                                                   |
| 8   | i18n mandatory                          | All user-facing strings in `strings.xml` (pt-BR default, en, es). Zero hardcoded strings.                                                                   |
| 9   | Performance                             | Default `config_load_bytes=false`; stream from file; avoid loading full 32–64 MB ROMs into heap.                                                            |
| 10  | RetroView interception                  | The ONLY place ROM bytes reach the core is `RetroView.kt` → `GLRetroViewData`.                                                                              |
| 11  | Storage paths keyed by hackId           | Each hack gets isolated `rom_<hackId>`, `sram_<hackId>`, `state_<hackId>`.                                                                                  |
| 12  | Catalog schema versioning               | `catalogVersion` integer in JSON; handle migrations gracefully.                                                                                             |
| 13  | Selective migration                     | Copy from Ludere only specified modules; do NOT migrate data/Games.kt, bundled covers, ROM packaging machinery, `autogen/`, Ludere keystore, or ABI splits. |
| 14  | Gamepad layout FROZEN                   | RadialGamePad layout measured for OoT/MM; no changes without user approval + `plano.md` update. In-game chrome restyled to Switch UI.                       |
| 15  | No telemetry without opt-in             | No analytics, crash reporting, or network calls except user-initiated catalog fetch, patch download, core download (build-time only).                       |
| 16  | Validate all inputs                     | ROM checksums, patch checksums, catalog JSON schema, downloaded file sizes.                                                                                 |
| 20  | RA credentials                          | Never logged; stored only in `EncryptedSharedPreferences` (`ra_secure_prefs`).                                                                              |
| 21  | RA hash from final patched ROM          | Computed at install time after BPS/ZPF patch applied and ROM written to `Storage.rom(hackId)`.                                                              |
| 22  | Leaderboards never overlaid on gameplay | Accessible ONLY via GameActivity in-game menu (DialogFragment).                                                                                             |
| 23  | Hardcore mode defaults OFF              | `pref_ra_hardcore = false` until RAdmin User-Agent validated.                                                                                               |
| 24  | License notices                         | rcheevos (MIT) license file kept in `app/src/main/cpp/rcheevos/LICENSE`.                                                                                    |
| 25  | System notifications opt-in default ON  | Requires `POST_NOTIFICATIONS` runtime permission on API 33+; first unlock triggers permission request if needed.                                            |
