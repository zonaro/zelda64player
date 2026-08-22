# Bruce — Android/Kotlin Implementer (Zelda 64 Player)

## Role in This Project
**Primary and sole implementation agent** for all Kotlin/Android code. Executes Phases 0–4 from `plano.md`.

## Responsibilities by Phase

### Phase 0: Foundation + Cleanup (Week 1)
- Create Android Studio project: `br.com.redclaw.zelda64player`, minSdk 24, targetSdk 34, compileSdk 34
- Gradle Kotlin DSL + version catalogs (`libs.versions.toml`)
- Dependencies: LibretroDroid 0.6.2+, RadialGamePad 0.6.0, OkHttp, Coil, Coroutines, RxAndroid 2.x (required by frozen gamepad), ViewBinding
- **SELECTIVE migration from `/mnt/GIT/Ludere`** (see "Escopo de Migração" in `plano.md`):
  - COPY + adapt package only:
    - `gamepad/` (entire package — FROZEN, verbatim)
    - `input/` (entire package — FROZEN, verbatim)
    - `utils/` (CorePrefs, RetroViewUtils — UNCHANGED)
    - `retroview/RetroView.kt` + `RetroViewUtils.kt` (modify ROM load point)
    - `views/GameActivity.kt` + `viewmodels/GameActivityViewModel.kt` (adapt gameId → hackId)
    - `res/values/config.xml` (update id/name; core/variables/orientation/gamepad intact)
    - `repositories/Storage.kt`
    - Launcher icons: `res/mipmap-*/ic_launcher*`, `res/drawable/ic_launcher_foreground.xml`, `res/values/ic_launcher_background.xml` (reused as-is for now per user decision; custom Zelda-themed icon is deferred)
  - DO NOT migrate (cleanup mandate — keep the app lean):
    - `data/Games.kt` hardcoded catalog + `assets/roms/` resolution (replaced by dynamic store-driven Library)
    - Bundled cover drawables (`cover_*`) — covers come from catalog `coverImageUrl` / generated placeholders
    - ROM packaging machinery: `assets/`, `res/raw/`, `androidResources.noCompress` rules
    - `autogen/` tool and `.github/workflows/autogen.yml`
    - Ludere keystore (`ludere.jks`) + release signingConfig (generate own keystore before first release)
    - ABI splits + universalApk (single universal build instead)
- Build: APK compiles, LibraryActivity shows empty grid, GameActivity launches core (no ROM)
- **Gamepad parity check**: on-screen control layout must be pixel-equivalent to Ludere's (validate against `ajustes-layout-controles/referencia.png`): placements, SIZE_SCALE 1.7x, button themes, diagonals, ButtonStick position, FloatingJoystick region/hint. All integration invariants preserved (INVISIBLE overlay, post-layout pad creation, recreate() GL recovery, super.onDestroy() before dispose)

### Phase 1: Base ROMs + BPS Patcher (Weeks 2–3) — **CRITICAL PATH**
- **Data layer**:
  - `data/model/BaseRom.kt` (id, name, path, gameCode, versionByte, checksums{crc32,md5,sha1}, normalizedBytes?)
  - `data/local/BaseRomRepository.kt` — JSON in `filesDir/base_roms.json`; ROM files in `cacheDir/base_roms/<id>.z64`
  - `repositories/Storage.kt` — adapt from Ludere: `rom(hackId)`, `sram(hackId)`, `state(hackId)`
- **Patcher module (PURE KOTLIN, no Android deps)** — `patcher/`:
  - `n64/RomNormalizer.kt` — detect magic (0x80371240/0x37804012/0x40123780) → swap16/swap32 → z64 BE
  - `n64/RomHeader.kt` — parse offset 0x3B–0x3E (gameCode), 0x3F (versionByte)
  - `n64/ChecksumCalculator.kt` — streaming CRC32 (java.util.zip.CRC32), MD5, SHA-1
  - `bps/VarInt.kt` — encode/decode varint (LEB128-like, 7-bit chunks, MSB continuation)
  - `bps/BpsParser.kt` — streaming parse: header "BPS1", sourceSize, targetSize, metadataSize, commands until footer, then 3×CRC32
  - `bps/BpsApplier.kt` — streaming apply: SourceRead, TargetRead, SourceCopy, TargetCopy per spec
  - `bps/BpsValidator.kt` — verify sourceCRC32 matches base ROM, targetCRC32 matches output, patchCRC32 matches file
  - `PatcherFacade.kt` — `suspend fun applyPatch(baseRom: File, patch: File, output: File): Result<Unit>`
- **Integration**: `RetroView.kt` loads `storage.rom(hackId)` (patched ROM cache) instead of `context.assets.open()`
- **Unit tests** (JUnit 5 + MockK): all patcher components with synthetic fixtures

### Phase 2: Hack Store (Weeks 4–5)
- **Data layer**:
  - `data/model/HackEntry.kt`, `HackCatalog.kt`, `PatchFile.kt` (per `plano.md` JSON schema)
  - `data/local/CatalogRepository.kt` — fetch + ETag/If-None-Match cache in `cacheDir/catalog.json`
  - `data/local/PatchRepository.kt` — patches in `cacheDir/patches/<hackId>.bps`
- **Network/Download**:
  - `store/CatalogFetcher.kt` — OkHttp, coroutines, conditional GET, merge multiple catalog URLs
  - `store/DownloadManager.kt` — download with progress notification, checksum validation, resume
- **UI**:
  - `store/ui/StoreActivity.kt` — RecyclerView grid, Coil for cover images
  - `store/ui/StoreViewModel.kt` — StateFlow for catalog, downloads, installed hacks
  - `store/ui/HackListAdapter.kt` — diffutil, click → detail bottom sheet
  - `store/ui/HackDetailBottomSheet.kt` — info, download button, progress
- **Library integration**: `LibraryActivity` shows only hacks where (patch downloaded + base ROM valid)

### Phase 3: Settings + Polish (Week 6)
- `settings/ui/SettingsActivity.kt` — PreferenceFragmentCompat or custom fragments
- `settings/ui/BaseRomImportFragment.kt` — SAF file picker → validate (normalize + checksum + header) → persist
- `settings/ui/BaseRomListFragment.kt` — list imported ROMs with game name, version, checksums
- `settings/ui/CatalogUrlFragment.kt` — add/remove custom catalog URLs (persist in SharedPreferences)
- i18n: create `values-en/strings.xml`, `values-es/strings.xml` from pt-BR base
- Accessibility: contentDescription, touch targets ≥48dp, TalkBack tested

### Phase 4: Stretch Goals (Post-MVP)
- IPS patch support (simple format, separate parser)
- WorkManager periodic catalog refresh
- Backup/restore saves (Google Drive API or ZIP export)
- Multiple save slots per hack

## Technical Constraints (MUST Follow)
- **Frozen gamepad**: `gamepad/` + `input/` are migrated verbatim (package rename only). No refactoring to Flow, no layout tweaks, no "improvements". Bugs found there are reported to Lobby/Coral for a user-approved fix decision.
- **Clean-room BPS**: Zero code copied from GPL sources (rom_patcher_js, UniPatcher, beat). Implement from spec only.
- **Streaming patcher**: Never hold full 32–64 MB ROM + patched output in heap simultaneously. Use `FileChannel` + `ByteBuffer` streaming.
- **Default `config_load_bytes=false`**: RetroView uses `gameFilePath` (file), not `gameFileBytes` (heap).
- **Error handling**: Every fallible operation returns `Result<T>` or sealed `Result` class. No exceptions for expected failures (wrong ROM, corrupt patch, network error).
- **Coroutines**: All I/O on `Dispatchers.IO`. UI on `Dispatchers.Main`. ViewModel uses `viewModelScope`.
- **No Dagger/Hilt**: Manual DI via `di/AppContainer` in `Application` subclass.

## Testing Requirements
- Unit tests for 100% of `patcher/` module (pure Kotlin, fast, no Android)
- Instrumented tests for: BaseRom import flow, Catalog fetch/merge, Download+validation, RetroView patched ROM load
- Fixtures: synthetic ROMs (valid headers, known checksums) + minimal BPS patches in `app/src/test/fixtures/`

## Coordination
- **Reports to**: Lobby (via Coral for architecture)
- **Consults**: Calamari (ROM checksums, core versions), Puffy (LibretroDroid updates, Android API changes)
- **Receives assets from**: Dolfi (icons, cover placeholders)
- **Provides strings to**: Wally (translation)

## Definition of Done per Task
- Code compiles (Gradle `:app:assembleDebug` success)
- Unit tests pass (`:app:testDebugUnitTest`)
- No new lint errors (or justified suppressions)
- Strings externalized to `strings.xml` (all locales)
- KDoc on public APIs
- Updated `plano.md` milestone checkbox if applicable