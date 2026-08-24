# Zelda 64 Player

Native Android (Kotlin) player for fan-made Zelda 64 ROM hacks. Users provide their own legally-owned Ocarina of Time / Majora's Mask base ROMs; the app applies BPS (and legacy IPS) patches on-the-fly and plays them via Libretro cores (mupen64plus-next GLES3 and parallel-n64). No ROMs are embedded or distributed.

---

## Legal / Disclaimer

The app **never** embeds, downloads, or distributes base ROMs. Users must legally import their own Ocarina of Time and Majora's Mask ROMs via **Settings → Import Base ROM**. Only BPS patches and catalog metadata are hosted/distributed. The app is GPL-3.0 licensed (derivative of Ludere). The BPS implementation is clean-room, based on the public BPS spec only — no GPL code from rom_patcher_js or UniPatcher is used. Users are responsible for ensuring their base ROM matches the hack's required version.

---

## Features

- **Hack store** with GitHub-hosted JSON catalog (multi-catalog merge, ETag caching)
- **On-the-fly patching** with triple CRC32 validation (source / patch / target)
- **N64 byte-order normalization** (.z64 / .v64 / .n64 → big-endian z64)
- **Checksum-based base ROM matching** (gameCode + versionByte + CRC32)
- **OoT Randomizer generator** — schema-driven settings UI (~200 options), plandomizer support, ZPF/ZPFZ patch application and N64 boot CRC recomputation via the official OoTR API (user-provided API key); generated seeds live in a dedicated Library section
- **Auto-Ocarina** — in-game HUD that plays Ocarina songs for you (OoT: 12 songs, MM: 11), with optional per-hack custom songs from the catalog
- **RetroAchievements** — sign in with your RetroAchievements.org account; achievements are tracked during play with unlock popups, challenge/progress indicators and system notifications. Leaderboards are available only inside the in-game menu. The RA hash is computed exclusively from the final patched ROM at install time.
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

The first build runs a `prepareCore` Gradle task that downloads the two Libretro
cores into `app/src/main/jniLibs` (needs internet): `mupen64plus_next` (GLES3) is
fetched as a zip from buildbot.libretro.com, and `parallel_n64` is fetched from our
self-built rolling release (see `.github/workflows/build-parallel-n64.yml`, which
builds updated dynarec binaries from libretro/parallel-n64 with the current NDK)
and falls back to the buildbot nightly zip if that is unavailable. Cores already
present in `jniLibs` are not re-downloaded, and any core unavailable for a given
ABI is skipped gracefully.

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
| Target/Compile SDK | Android API 35 | Play Store requirement |
| Build System | Gradle 8.x | Kotlin DSL (`build.gradle.kts`) |
| Architecture | MVC-ish | View → ViewModel → Model (Repository) |
| DI | Manual construction | Repositories built where needed; `Zelda64PlayerApp` schedules background work |
| Emulation | LibretroDroid 0.13.2 | Vendored local Gradle module `:libretrodroid` + memory-access JNI passthroughs; cores fetched at build time |
| Touch Controls | RadialGamePad 0.6.0 | Frozen package — package rename only |
| RetroAchievements | rcheevos v12.4.0 (MIT) | ANSI C, git subtree in `app/src/main/cpp/rcheevos/`; JNI bridge `libra_jni.so` |
| UI Toolkit | Android Views + Material Components 1.14.0 | Material 3 Expressive theme |
| Networking | OkHttp 4.12+ | Catalog fetch, patch download, RA web API |
| Image Loading | Coil 2.6.0 | Kotlin-first, coroutines |
| Persistence | JSON files (org.json) | BaseRomRepository, PatchRepository, installed-hacks registry, RA identities; EncryptedSharedPreferences for credentials/API keys |
| Coroutines | kotlinx-coroutines 1.8+ | `lifecycle-runtime-ktx` |
| Background Tasks | WorkManager 2.9.0 | `CatalogRefreshWorker` (12h) |
| Testing | JUnit 4.13 | 191 JVM unit tests |

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
├── settings/       # SettingsActivity (ROM import/list, catalog URLs, backups, RA login) + helpers
├── work/           # CatalogRefreshWorker (periodic background refresh)
├── repositories/   # Storage (per-hack ROM/SRAM/state paths)
├── randomizer/     # OoT Randomizer generator (OoTR API client, schema UI, ZPF patcher, seeds)
├── ocarina/        # Auto-Ocarina (song catalog, coroutine sequencer, in-game HUD)
├── retroachievements/ # RetroAchievements integration
│   ├── jni/        # RcheevosJni bridge + shared HTTP dispatcher
│   ├── api/        # RaHttpClient, User-Agent
│   ├── auth/       # Encrypted credential store + interactive login service
│   ├── data/       # Install-time identities (rhash), catalog repository
│   ├── session/    # Live rc_client session state machine
│   └── ui/         # Achievements list, unlock overlay, leaderboards dialog
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
├── utils/          # CorePrefs, RetroViewUtils
└── libretrodroid/  # Vendored LibretroDroid 0.13.2 Gradle module (+ JNI passthroughs)
```

**Testing:** `./gradlew :app:testDebugUnitTest` — 191 unit tests covering patcher N64 normalization, BPS parsing/validation, checksums, PatcherFacade integration, randomizer patching/validation and RetroAchievements identity/catalog parsing. Fixtures use synthetic ROMs and patches; no real ROMs are committed.

**Roadmap:** See **plano.md** (pt-BR). Shipped: BPS + IPS patching, hack store, settings, save backup/restore, background catalog refresh, OoT Randomizer generator, Auto-Ocarina, RetroAchievements. Remaining stretch goals (post-MVP): multiple save slots per hack, optional opt-in telemetry (privacy-first, deferred), hardcore mode enablement pending RAdmin User-Agent validation.

---

## Credits

- **Libretro / RetroArch** cores (mupen64plus-next, parallel-n64)
- **mupen64plus** — original emulation engine
- **parallel-n64** — accuracy-focused Libretro core
- **RadialGamePad** — custom touch gamepad library
- **LibretroDroid** — Android embedding framework
- **rcheevos** (MIT) — RetroAchievements client library powering the achievements integration
- **RetroAchievements.org** — achievement database and web API
- **OoTR** (ootrandomizer.com) — randomizer generation API
- **BPS format** — by byuu / rombp (MIT reference implementation consulted for clean-room)
- **Inspired by** the romhacking community and the original Ludere project (GPL-3.0)

---

## License

GPL-3.0 — See [LICENSE](LICENSE). This project is a derivative of Ludere (GPL-3.0). Full source must be offered; keep the LICENSE file and copyright headers intact.