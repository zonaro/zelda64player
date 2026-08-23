# Zelda 64 Player

Native Android (Kotlin) player for fan-made Zelda 64 ROM hacks. Users provide their own legally-owned Ocarina of Time / Majora's Mask base ROMs; the app applies BPS (and legacy IPS) patches on-the-fly and plays them via Libretro cores (mupen64plus-next GLES3/GLES2, parallel-n64). No ROMs are embedded or distributed.

---

## Legal / Disclaimer

The app **never** embeds, downloads, or distributes base ROMs. Users must legally import their own Ocarina of Time and Majora's Mask ROMs via **Settings → Import Base ROM**. Only BPS patches and catalog metadata are hosted/distributed. The app is GPL-3.0 licensed (derivative of Ludere). The BPS implementation is clean-room, based on the public BPS spec only — no GPL code from rom_patcher_js or UniPatcher is used. Users are responsible for ensuring their base ROM matches the hack's required version.

---

## Features

- **Hack store** with GitHub-hosted JSON catalog (multi-catalog merge, ETag caching)
- **On-the-fly patching** with triple CRC32 validation (source / patch / target)
- **N64 byte-order normalization** (.z64 / .v64 / .n64 → big-endian z64)
- **Checksum-based base ROM matching** (gameCode + versionByte + CRC32)
- **Custom gamepad layout** with C-button mapping, Auto-Z, ButtonStick, FloatingJoystick
- **Save backup/restore** via ZIP (local, no cloud)
- **Background catalog refresh** via WorkManager (12h periodic, CONNECTED network)
- **i18n** (pt-BR default, English, Spanish) — all UI strings externalized

---

## Build Instructions

**Requirements:** Android Studio (latest), JDK 17, Android SDK 34.

```bash
git clone https://github.com/zonaro/zelda64player.git
cd zelda64player
./gradlew assembleDebug
```

The first build runs a `prepareCore` Gradle task that downloads Libretro cores
(mupen64plus_next GLES3/GLES2 and parallel-n64) from buildbot.libretro.com into
`app/src/main/jniLibs` (needs internet). Cores are skipped gracefully if unavailable
for a given ABI.

---

## Usage Quick-Start

1. Open **Settings** → **Import Base ROM** and select your legally-owned OoT or MM ROM (.z64 / .v64 / .n64).
2. Browse the **Store** → view available hacks and download one.
3. The app validates the base ROM checksum, downloads the BPS (or ZIP) patch, applies it, and launches the game.
4. Supported formats: `.z64` / `.v64` / `.n64` base ROMs; **BPS** and **IPS** patches.

---

## For Hack Authors

See **docs/CATALOG.md** for the JSON catalog schema and **docs/catalog.example.json** for a ready-to-adapt example with two hacks. Catalog PRs are welcome — add your hack by submitting a PR with a `catalog.json` that follows the schema (required fields: `id`, `name`, `description`, `author`, `version`, `baseRom.name`, `baseRom.gameCode`, `baseRom.versionByte`, `baseRom.checksums.crc32`, `patch.url`, `patch.filename`, `patch.size`, `patch.checksums.crc32`).

---

## Tech Stack Summary

| Layer | Technology | Notes |
|-------|------------|-------|
| Language | Kotlin 1.9+ | JVM target 1.8 |
| Min SDK | Android API 24 | >95% devices |
| Target SDK | Android API 34 | Play Store requirement |
| Build System | Gradle 8.x | Kotlin DSL (`build.gradle.kts`) |
| Architecture | MVC-ish | View → ViewModel → Model (Repository) |
| DI | Manual construction | Repositories built where needed; `Zelda64PlayerApp` schedules background work |
| Emulation | LibretroDroid 0.6.2+ | Cores fetched at build time |
| Touch Controls | RadialGamePad 0.6.0 | Frozen package — package rename only |
| Networking | OkHttp 4.12+ | Catalog fetch + patch download |
| Image Loading | Coil 2.6.0 | Kotlin-first, coroutines |
| Persistence | JSON files (org.json) | BaseRomRepository, PatchRepository, installed-hacks registry |
| Coroutines | kotlinx-coroutines 1.8+ | `lifecycle-runtime-ktx` |
| Background Tasks | WorkManager 2.9.0 | `CatalogRefreshWorker` (12h) |
| Testing | JUnit 4.13 | 84 JVM unit tests |

---

## Project Structure (Package Map)

```
br.com.redclaw.zelda64player
├── data/           # Models + local repositories
│   ├── model/      # BaseRom, HackEntry, HackCatalog, Checksums
│   └── local/      # BaseRomRepository, PatchRepository, InstalledHacksRepository, SaveBackupManager
├── patcher/        # Pure Kotlin module (no Android deps)
│   ├── bps/        # BPS parser, applier, validator, varint
│   ├── ips/        # IPS applier (legacy format)
│   ├── n64/        # RomNormalizer, RomHeader, ChecksumCalculator
│   └── PatcherFacade.kt # applyPatch API with BPS/IPS auto-detection
├── store/          # Hack Store logic + UI
│   ├── ui/         # StoreActivity, ViewModel, Adapter, BottomSheet
│   ├── DownloadManager.kt
│   ├── CatalogFetcher.kt
│   └── CatalogRefresher.kt # shared by ViewModel + WorkManager worker
├── settings/       # SettingsActivity (ROM import/list, catalog URLs, backups) + helpers
├── work/           # CatalogRefreshWorker (periodic background refresh)
├── repositories/   # Storage (per-hack ROM/SRAM/state paths)
├── retroview/      # Emulation (adapted from Ludere)
│   ├── RetroView.kt     # Loads patched ROM from Storage cache
│   └── RetroViewUtils.kt
├── views/
│   ├── LibraryActivity.kt     # Grid of installed hacks
│   ├── GameActivity.kt        # Gameplay activity
│   └── HackLibrarySource.kt   # Library data-source seam
├── viewmodels/     # GameActivityViewModel (launch flow: resolve base -> patch -> play)
├── gamepad/        # RadialGamePad — frozen (package rename only)
├── input/          # ControllerInput, InputMapper — frozen
└── utils/          # CorePrefs, RetroViewUtils
```

**Testing:** `./gradlew :app:testDebugUnitTest` — 84 unit tests covering patcher N64 normalization, BPS parsing/validation, checksums, and PatcherFacade integration. Fixtures use synthetic ROMs and patches; no real ROMs are committed.

**Roadmap:** See **plano.md** (pt-BR). Shipped: BPS + IPS patching, hack store, settings, save backup/restore, background catalog refresh. Remaining stretch goals (post-MVP): multiple save slots per hack, optional opt-in telemetry (privacy-first, deferred).

---

## Credits

- **Libretro / RetroArch** cores (mupen64plus-next, parallel-n64)
- **mupen64plus** — original emulation engine
- **parallel-n64** — accuracy-focused Libretro core
- **RadialGamePad** — custom touch gamepad library
- **LibretroDroid** — Android embedding framework
- **BPS format** — by byuu / rombp (MIT reference implementation consulted for clean-room)
- **Inspired by** the romhacking community and the original Ludere project (GPL-3.0)

---

## License

GPL-3.0 — See [LICENSE](LICENSE). This project is a derivative of Ludere (GPL-3.0). Full source must be offered; keep the LICENSE file and copyright headers intact.