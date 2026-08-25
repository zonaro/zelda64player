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
- **Consults**: Calamari (ROM checksums, core versions, rcheevos release tags, RA hash algorithm), Puffy (LibretroDroid updates, Android API changes, rcheevos API docs)
- **Receives assets from**: Dolfi (icons, cover placeholders, RA trophy/leaderboard icons, Switch dock icons, splash art, focus assets)
- **Provides strings to**: Wally (translation)

## Switch UI Revamp (New Phase — Mandatory)

### Design Tokens (from AGENTS.md)
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

### Component Inventory (Native Kotlin, Hand-Styled — No M3 Expressive)
| Component | Description |
|-----------|-------------|
| `SwitchHomeRow` | Horizontal scrollable row of square game cards + circular "Todos os Jogos" card at end |
| `SwitchGameCard` | Square card (1:1), cover image, game title overlay on focus, focus border, dimming overlay |
| `SwitchAllGamesCard` | Circular card (charcoal fill, cyan 2×2 grid icon, cyan border on focus) |
| `SwitchGridScreen` | Fullscreen grid ("Todos os Jogos"): header icon+title "Todos os Jogos" 20sp bold + thin separator, smaller square cards (~170dp), search/filter bar, ghosted placeholders |
| `SwitchDock` | Fixed bottom dock: 4 circular buttons (Loja, RetroAchievements, Sobre/Licenças), ~50dp diameter, colored glyphs, focus ring |
| `SwitchFooterHints` | Bottom bar: left TV+gamepad indicators, right "(i) Sobre" and "+ Opções" gray hints 11–12sp |
| `SwitchSidePanel` | Right slide-in panel (~50% width), sharp edges, header (teal badge icon + bold title 20–22sp + separator), numbered rows (gray circle 24dp badges), labels 16sp, "(default)" suffix 14sp gray, chevron right, thin line separators |
| `SwitchDialog` | Centered modal, scrim, box ~40% width, radius 12–16dp, bg `#3A3A3C`, header icon+title 18sp, rows 48–52dp with icon+text, focused row = cyan border outline |
| `SwitchFocusBorder` | Drawable: cyan 2–3dp stroke, transparent fill, for focus indication |
| `SfxManager` | SoundPool wrapper: focus-move tick, select, back, panel open/close; CC0/generated only; volume respect; toggle in settings |
| `ThemeManager` | Runtime dark/light switch, persists preference, applies tokens above |

### Implementation Order (Switch UI Revamp Phase)
1. **Theme tokens + ThemeManager + base styles** — Define colors in `colors.xml` (CSS-variable-style names), create `ThemeManager` (runtime dark/light switch, persists to SharedPreferences), base theme in `themes.xml` (parent `Theme.Material3.DayNight.NoActionBar` — technical base only, no expressive styles).
2. **Focus system + SwitchFocusBorder + SfxManager** — `SwitchFocusBorder` drawable, focus handling logic (D-pad/click drives cyan border + label above card), `SfxManager` (SoundPool, 5 SFX in `res/raw/`), SFX toggle in settings.
3. **Library Home rebuild** — `SwitchHomeRow` (horizontal RecyclerView), `SwitchGameCard` (square, cover, focus label, dimming), `SwitchAllGamesCard` (circular, charcoal, cyan grid icon), `SwitchDock` (4 circular buttons), `SwitchFooterHints` (TV/gamepad + "(i) Sobre" / "+ Opções"). Vanilla games first, then store hacks.
4. **Todos os Jogos grid** — `SwitchGridScreen` (fullscreen): header icon+title "Todos os Jogos" 20sp bold + separator, smaller square cards (~170dp), search/filter bar, ghosted placeholders. All entries together (vanilla + hacks + seeds).
5. **Side panel + Settings restyle** — `SwitchSidePanel` (right slide-in, ~50% width, sharp edges). Quick settings: theme toggle, RA profile status, link to full Settings. Full SettingsActivity restyled entirely (SwitchGridScreen or fullscreen SwitchSidePanel).
6. **Store / RetroAchievements screens restyle** — Store: Switch cards in grid, detail → SwitchDialog. RA: SwitchGridScreen (games), SwitchDialog (detail, leaderboards).
7. **In-game menu + overlays restyle** — Pause menu → SwitchDialog, leaderboards → SwitchDialog, achievement unlock overlay → custom Switch-style toast, ocarina HUD → Switch-style overlay. **RadialGamePad touch layout FROZEN (Rule 14)** — only chrome restyled.
8. **Splash** — `SplashActivity` or splash theme: Zelda gold/green palette (Dolfi original art), same structural layout as NS Launcher splash (flanking iconic shapes + two-line logo "Zelda 64" / "PLAYER"). No Nintendo IP.
9. **SFX integration + polish** — Wire SFX to all focus/select/back/panel actions, volume respect, mute toggle, cross-screen consistency, visual QA (Chululu).

### Screen-by-Screen Mapping
| Screen | Switch Components | Notes |
|--------|-------------------|-------|
| Splash | Custom (Dolfi art) | Zelda gold/green, two-line logo |
| Library Home | SwitchHomeRow, SwitchAllGamesCard, SwitchDock, SwitchFooterHints | Vanilla → hacks → seeds order |
| Todos os Jogos | SwitchGridScreen | Search/filter, all entries |
| Store | SwitchGridScreen + SwitchDialog | Cards + detail bottom sheet |
| Randomizer | RandomizerWebActivity (WebView) + SwitchDialog | Embedded OoTR generator, ROM picker, capture progress |
| Settings (Quick) | SwitchSidePanel | Theme, RA status, link to full |
| Settings (Full) | SwitchGridScreen / SwitchSidePanel | Full restyle |
| RetroAchievements | SwitchGridScreen + SwitchDialog | Games, detail, leaderboards |
| GameActivity In-Game | SwitchDialog + custom overlays | Pause, leaderboards, achievement toast, ocarina HUD |

**Note**: RxJava in `gamepad/` package remains untouched. New Switch UI code uses coroutines/Flow only.

---

## RetroAchievements Feature Implementation (Phases B1–B5)

### Phase B1: Foundation — Vendored LibretroDroid + rcheevos Native + JNI Bridge (Week 1)
**Objective**: Build compiles, JNI loads, `rc_client_create` + `rc_client_do_frame` called each frame (test log).

**Files to create:**
- **Module `:libretrodroid`** (new Gradle module):
  - Vendor LibretroDroid 0.13.2 source (from GitHub tag 0.13.2) into `libretrodroid/src/main/`
  - Add 2 JNI passthroughs in `libretrodroid/src/main/cpp/`:
    - `LibretroDroidMemoryJni_getMemoryData(JNIEnv*, jclass, jint id) → jobject ByteBuffer` — wraps `retro_get_memory_data(id)` as direct ByteBuffer
    - `LibretroDroidMemoryJni_getMemorySize(JNIEnv*, jclass, jint id) → jint` — returns `retro_get_memory_size(id)`
  - `libretrodroid/build.gradle.kts` — Android library, `externalNativeBuild` for the 2 JNI functions
  - Kotlin bindings: `LibretroDroidMemoryJni.kt` (companion `getMemoryData(id)`, `getMemorySize(id)`)

- **App module native (app/src/main/cpp/)**:
  - `git subtree add --prefix=app/src/main/cpp/rcheevos https://github.com/RetroAchievements/rcheevos.git master --squash` (pinned to release tag ~12.x)
  - `CMakeLists.txt` — compiles rcheevos + `ra_jni_bridge.c`, links `:libretrodroid` JNI lib
  - `ra_jni_bridge.h` — JNI declarations for `RcheevosJni` + `LibretroDroidMemoryJni`
  - `ra_jni_bridge.c` — implementations:
    - `nativeInit(readMemFn, serverCallFn, logFn)` → `rc_client_create` → store `rc_client_t*` in global
    - `nativeShutdown()` → `rc_client_destroy`
    - `nativeDoFrame()` → `rc_client_do_frame(client)`
    - `nativeReadMemory(address, buffer, numBytes)` → calls `LibretroDroidMemoryJni.getMemoryData(RETRO_MEMORY_SYSTEM_RAM)` → copies to output buffer
    - `nativeServerCall(requestPtr)` → async OkHttp via `RaHttpClient` → on completion calls `nativeServerCallComplete(requestPtr, responseBody, httpStatus)`
    - `nativeLoginPassword(username, password)` → `rc_client_begin_login_with_password`
    - `nativeLoginToken(token)` → `rc_client_begin_login_with_token`
    - `nativeIdentifyGame(romPath)` → `rc_client_begin_identify_and_load_game(client, RC_CONSOLE_NINTENDO_64, romPath, ...)`
    - `nativeGetAchievements(category, grouping)` → `rc_client_create_achievement_list` → serialize to JSON string return
    - `nativeGetLeaderboards()` → `rc_client_create_leaderboard_list` (if needed) or use rapi standalone
    - `nativeSetHardcore(enabled)` → `rc_client_set_hardcore_enabled`
    - `nativeSetUserdata(ptr)` → `rc_client_set_userdata`
  - `RcheevosJni.kt` — Kotlin bindings (`System.loadLibrary("ra_jni_bridge")`, `external fun` declarations)
  - `LibretroDroidMemoryJni.kt` — Kotlin bindings to `:libretrodroid` module JNI

- **RaHttpClient.kt** (skeleton):
  - OkHttpClient singleton (shared with store)
  - `serverCall(requestPtr, callback)` — called from JNI `nativeServerCall`; executes async HTTP; on response calls back into JNI `nativeServerCallComplete`
  - rapi standalone methods: `fetchGameData(gameId)`, `fetchUserUnlocks(gameId)`, `resolveHash(hash)`, `fetchLeaderboards(gameId)`, `fetchLeaderboardEntries(leaderboardId)`

- **InGameRaViewModel.kt** (minimal):
  - `launchHack(hackId, patchedRomPath)` → `RcheevosJni.nativeInit(...)` → `RcheevosJni.nativeIdentifyGame(patchedRomPath)`
  - Collects `RetroView.frameRendered` Flow → `RcheevosJni.nativeDoFrame()`
  - `onCleared()` → `RcheevosJni.nativeShutdown()`

- **Integration in `GameActivityViewModel.launchHack`**: create `InGameRaViewModel` scoped to GameActivity lifecycle; pass patched ROM path.

**Build verification:**
- `./gradlew :app:assembleDebug :libretrodroid:assembleDebug` — success
- Runtime: logcat shows "RA frame tick" each frame (add temporary log in `nativeDoFrame`)

**Invariants:**
- `:libretrodroid` module **only** adds the 2 memory JNI functions — no other changes to LibretroDroid source
- rcheevos vendored via **git subtree** (not copied snapshot) — enables future `subtree pull` for updates
- ABIs: x86, x86_64, armeabi-v7a, arm64-v8a (match existing `jniLibs`)
- `rc_client_do_frame` runs on **main thread** (driven by `FrameRendered` Flow already on main dispatcher)
- `read_memory` callback executes on rcheevos background thread — must be thread-safe (ByteBuffer copy)
- `server_call` callback from OkHttp on background thread → JNI completion callback may need `AttachCurrentThread`

---

### Phase B2: Auth + Session + Install-time Hash Resolution (Week 2)
**Objective**: Login/logout functional, token persisted encrypted, RA hash computed at install and cached.

**Files to create:**
- `retroachievements/auth/RaCredentialStore.kt` — `EncryptedSharedPreferences` (prefs file `ra_secure_prefs`, keys: `pref_ra_token`, `pref_ra_username`, `pref_ra_hardcore_token`).
- `retroachievements/auth/RaSessionManager.kt`:
  - `suspend fun login(username: String, password: String): Result<UserInfo>` — calls `RcheevosJni.nativeLoginPassword` + `RaHttpClient.fetchUserInfo()` → cache token + username
  - `suspend fun loginWithToken(): Result<UserInfo>` — `RcheevosJni.nativeLoginToken(token)` + fetch user info
  - `fun logout()` — `RcheevosJni.nativeLogout()` + clear credentials
  - `fun getUserInfo(): UserInfo?` — cached
  - `suspend fun refreshToken(): Result<Unit>` — re-login with token
- `retroachievements/auth/RaLoginFragment.kt` (Settings fragment):
  - UI: username/password fields (password visibility toggle), "Get Token" button → `Intent.ACTION_VIEW` to `https://retroachievements.org/controlpanel.php`
  - Login button → `RaSessionManager.login()` → loading state → on success: navigate back, show toast
  - If token exists: show "Logged in as X" + "Logout" button; silent re-login on app start
  - Error handling: invalid credentials, network error, rate limit
- `retroachievements/api/RaHttpClient.kt` — complete implementation:
  - `serverCall` dispatcher for rcheevos callback (async OkHttp → JNI completion)
  - rapi standalone methods with `Result<T>` returns, proper error mapping (`RaApiException` sealed hierarchy)
  - `RateLimiter` (token bucket: conservative 10 req/s) applied to all endpoints
- `retroachievements/api/RaApiModels.kt` — `@Serializable` data classes for rapi requests/responses
- `retroachievements/api/RaApiException.kt` — sealed: `AuthError`, `NetworkError`, `RateLimited(retryAfterMs)`, `ServerError`, `NotFound`
- `retroachievements/data/RaGameMetadata.kt` — data class: `hackId`, `raHash`, `raGameId`, `raTitle`, `badgeUrl`, `consoleId`
- `retroachievements/data/RaRepository.kt` — persists `Map<String, RaGameMetadata>` (key = hackId) in `filesDir/ra_metadata.json` (atomic write: temp + rename). Methods: `getAll()`, `get(hackId)`, `save(metadata)`, `remove(hackId)`.
- `retroachievements/install/RaHashService.kt`:
  - `suspend fun computeAndResolve(hackId: String, patchedRomPath: String): Result<RaGameMetadata>`
  - Calls `RcheevosJni.nativeComputeHash(romPath)` → uses rcheevos internal `rhash` (handles N64 byte-order/header)
  - Calls `RaHttpClient.resolveHash(hash)` → `gameId`
  - Calls `RaHttpClient.fetchGameData(gameId)` → title, badgeUrl
  - Saves via `RaRepository.save()`
- `retroachievements/install/RaInstallRepository.kt` — thin wrapper over `RaRepository` for install-time usage
- **Integration in `store/DownloadManager.kt`**: after patch applied and patched ROM written to `Storage.rom(hackId)`, launch coroutine `RaHashService.computeAndResolve(hackId, romPath)` (background, non-blocking UI)
- **Store UI**: in `HackListAdapter` / `HackDetailBottomSheet`, if `HackEntry.retroAchievements?.supported == true`, show RA badge icon (Dolfi asset) + "RetroAchievements Supported" label

**Unit tests:**
- `RaCredentialStoreTest` (encrypted prefs)
- `RaSessionManagerTest` (mock OkHttp + JNI)
- `RaHashServiceTest` (fixture ROM with known hash → verify gameId resolution)

**Build verification:** `./gradlew :app:assembleDebug :app:testDebugUnitTest`

**Invariants:**
- **Never log credentials** — sanitize in all logs (`token.take(4) + "***"` or `"***"`)
- **EncryptedSharedPreferences only** (minSdk 24 supports it)
- **RA hash computed ONLY from final patched ROM** at `Storage.rom(hackId)` — never from base ROM or intermediate
- `RaHttpClient` **RateLimiter applies to ALL endpoints** (rcheevos server_call + rapi standalone)
- Coroutines + Flow only — no RxJava in new retroachievements code

---

### Phase B3: Achievements Screens (Library) (Week 3)
**Objective**: Achievements screen (list of installed games with progress) + detail screen per game.

**Files to create:**
- `retroachievements/ui/AchievementsActivity.kt` + `retroachievements/viewmodel/AchievementsViewModel.kt`:
  - `StateFlow<List<RaGameMetadata>> installedGamesWithRA` — merge `InstalledLibrary.entries()` + `RaRepository.getAll()` (join by hackId)
  - RecyclerView (GridLayoutManager spanCount from `R.integer.library_span_count`): cover (Coil from `HackEntry.coverImageUrl` or placeholder), game title, RA game badge (Coil from `badgeUrl`), progress "X/Y unlocked", total points
  - Empty state: "Nenhum jogo com conquistas instalado. Instale hacks compatíveis da Loja ou gere seeds."
  - Click item → `AchievementDetailActivity` (intent with `gameId` + `hackId`)
- `retroachievements/ui/AchievementDetailActivity.kt` + ViewModel:
  - Args: `gameId` (Int), `hackId` (String)
  - `RaRepository.getAchievements(gameId)` → cache file `cacheDir/ra_achievements_<gameId>.json` (max age 24h) OR fetch via `RaHttpClient.fetchUserUnlocks(gameId)` + `RcheevosJni.nativeGetAchievements()` → merge → update cache
  - UI: TabLayout (categories/groupings from achievement list), ViewPager2 with RecyclerView per tab
  - Item: badge (Coil, placeholder locked/unlocked), title, description, points, measured progress bar (if `measuredTarget > 0`), unlocked checkmark
  - Pull-to-refresh (SwipeRefreshLayout) → force re-fetch + update cache
- `retroachievements/data/RaAchievement.kt` — data class: `id`, `title`, `description`, `points`, `badgeUrl`, `badgeLockedUrl`, `unlocked`, `measuredProgress`, `measuredTarget`, `category`, `grouping`
- `retroachievements/data/RaLeaderboard.kt` — data class: `id`, `title`, `description`, `format`, `lowerIsBetter`, `entries` (rank, score, user, date, userBadgeUrl)
- **i18n**: `strings.xml` additions for all chrome UI (pt-BR/en/es) — **achievement titles/descriptions come from API in English, do NOT translate**
- **Accessibility**: contentDescription on all dynamic views, TalkBack tested, touch targets ≥48dp
- **Visual QA**: Chululu screenshots — AchievementsActivity (empty, populated), AchievementDetailActivity (tabs, badges, progress bars)

**Build verification:** `./gradlew :app:assembleDebug :app:connectedAndroidTest`

**Invariants:**
- Achievement list cached locally (JSON) — refresh only on pull-to-refresh or 24h expiry
- Coil for all badge images (async, placeholder, error handling)
- `RaRepository` handles migration via `schemaVersion` in JSON root

---

### Phase B4: In-Game Overlay + Notifications + Challenge/Progress Indicators (Week 4)
**Objective**: Custom in-game toast on unlock + optional system notification + challenge/progress indicators.

**Files to create:**
- `retroachievements/viewmodel/InGameRaViewModel.kt` (complete):
  - `rc_client_set_event_handler` with callbacks for:
    - `ACHIEVEMENT_TRIGGERED(achievementId)` → fetch achievement details (from cache or `RaHttpClient`) → `InGameAchievementOverlay.show(achievement)`
    - `ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW/UPDATE/HIDE` → `InGameAchievementOverlay.showChallengeIndicator(...)` / `hideChallengeIndicator()`
    - `ACHIEVEMENT_PROGRESS_INDICATOR_SHOW/UPDATE/HIDE` → `InGameAchievementOverlay.showProgressIndicator(...)` / `hideProgressIndicator()`
    - `LEADERBOARD_STARTED/FAILED/SUBMITTED` → log only (leaderboards only in menu)
    - `GAME_MASTERY` → `InGameAchievementOverlay.showMastery()`
  - All callbacks execute on rcheevos thread → **post to main** via `Handler(Looper.getMainLooper()).post { ... }` → `InGameAchievementOverlay` methods
  - `rc_client_do_frame` driven by `RetroView.frameRendered` Flow (already on main)
  - `rc_client_set_hardcore_enabled(pref_ra_hardcore)` on init
  - `onCleared()` → `RcheevosJni.nativeUnloadGame()` + `RcheevosJni.nativeShutdown()`
- `retroachievements/ui/InGameAchievementOverlay.kt` (custom View):
  - Added to `GameActivity` FrameLayout (above GLRetroView, below gamepad overlay) via `GameActivityViewModel` on launch
  - **Unlock toast**: slide-in from top → stay 3s → slide-out; shows badge icon (Coil), title, points, "Achievement Unlocked!"
  - **Queue**: multiple rapid unlocks → sequential, 3s each
  - **Challenge indicator**: small pill top-right: "Challenge: <title>" + progress ring
  - **Progress indicator**: horizontal bar top-center: "<title>: 7/10"
  - **Mastery**: full-screen center flash: "MASTERY!" + game badge
  - Respects `pref_ra_enabled` (if OFF, no overlays shown)
  - Respects `pref_ra_show_challenge_indicators` / `pref_ra_show_progress_indicators`
- **System Notification** (optional, `pref_ra_system_notifications` default true):
  - `NotificationCompat.Builder` with `BigPictureStyle` (badge image via Coil → bitmap)
  - Channel `ra_unlocks` (importance HIGH, default sound)
  - API 33+: runtime `POST_NOTIFICATIONS` permission request on first unlock if not granted
  - `NotificationManagerCompat.notify(unlockId, notification)`
- **Settings integration** (`settings/ui/RetroAchievementsFragment.kt`):
  - Section "RetroAchievements" in SettingsActivity
  - Toggles: `pref_ra_enabled` (master), `pref_ra_hardcore` (default OFF), `pref_ra_system_notifications` (default ON), `pref_ra_show_challenge_indicators` (default ON), `pref_ra_show_progress_indicators` (default ON)
  - Shows current login status (username, score) + Logout button
  - Links to RA website
- **Hardcore mode**: `rc_client_set_hardcore_enabled(true)` only when `pref_ra_hardcore == true` AND UA validated (server enforces; client just sends setting)

**Visual QA:** Chululu screenshots — in-game unlock toast, challenge indicator, progress indicator, mastery flash, system notification shade

**Build verification:** `./gradlew :app:assembleDebug`

**Invariants:**
- Overlay View added to GameActivity FrameLayout **once** at launch (not per-frame)
- Coil image loading for badges async — placeholder shown while loading
- Queue for unlock toasts prevents overlap
- `rc_client_unload_game` + `rc_client_destroy` in `onCleared()` **before** core destruction (GameActivity `onDestroy` calls `super.onDestroy()` first → core native memory freed)

---

### Phase B5: Leaderboards (In-Game Menu Only) + Catalog Integration + Polish (Week 5)
**Objective**: Leaderboards accessible ONLY in GameActivity menu (DialogFragment), catalog v2 with RA metadata, final polish.

**Files to create:**
- `retroachievements/ui/LeaderboardDialog.kt` (DialogFragment):
  - Args: `gameId` (Int)
  - Fetch leaderboards: `RaHttpClient.fetchLeaderboards(gameId)` → list of leaderboard metadata
  - TabLayout: one tab per leaderboard
  - Per tab: `RaHttpClient.fetchLeaderboardEntries(leaderboardId)` → RecyclerView entries: rank, user, score (formatted per `format` field: TIME, SCORE, VALUE, etc.), date, user badge (Coil)
  - **Modal DialogFragment only** — never overlay on gameplay
- **GameActivity menu integration**:
  - In `GameActivityViewModel.prepareMenu` / menu grid builder: add category "Conquistas" with items:
    - "Ver Conquistas" → `Intent(this, AchievementsActivity::class.java)` (opens library achievements screen)
    - "Leaderboards" → `LeaderboardDialog.show(supportFragmentManager, gameId)`
  - Icons: trophy (achievements), leaderboard (Dolfi SVG)
- **Catalog v2 integration**:
  - `data/model/HackEntry.kt` — add optional `retroAchievements: RetroAchievementsInfo?` data class (supported, gameId, title, badgeName, badgeUrl)
  - `CatalogFetcher` / `MergedCatalogRepository` — handle `catalogVersion` migration (v1 → v2: default `supported=false`)
  - `HackCatalog` schemaVersion bump to 2
- **Store UI**: badge RA on hacks with `retroAchievements.supported == true` (in grid card + bottom sheet)
- **Third-party licenses**:
  - Copy `app/src/main/cpp/rcheevos/LICENSE` → `app/src/main/assets/licenses/rcheevos-LICENSE`
  - Add "Licenças de Terceiros" menu item in Settings/About → shows licenses (GPL-3.0 for Ludere/LibretroDroid/RadialGamePad/cores, MIT for rcheevos, Apache-2.0 for BPS patcher)
- **i18n complete**: Wally translates all new strings (pt-BR/en/es)
- **Visual QA final**: Chululu screenshots — GameActivity menu "Conquistas", LeaderboardDialog, Store RA badge, Achievements screens

**Build verification:** `./gradlew :app:assembleRelease` (release build test), `./gradlew :app:testDebugUnitTest :app:connectedAndroidTest`

**Invariants:**
- Leaderboards **never** rendered as overlay — only DialogFragment from in-game menu
- Catalog v2 backward compatible: missing `retroAchievements` field = `supported=false`
- Release build signed (keystore generated) runs without debug-only code
- All third-party licenses accessible from app

---

## Technical Constraints (MUST Follow) — Updated

- **Frozen gamepad**: `gamepad/` + `input/` are migrated verbatim (package rename only). No refactoring to Flow, no layout tweaks, no "improvements". Bugs found there are reported to Lobby/Coral for a user-approved fix decision.
- **Clean-room BPS**: Zero code copied from GPL sources (rom_patcher_js, UniPatcher, beat). Implement from spec only.
- **Streaming patcher**: Never hold full 32–64 MB ROM + patched output in heap simultaneously. Use `FileChannel` + `ByteBuffer` streaming.
- **Default `config_load_bytes=false`**: RetroView uses `gameFilePath` (file), not `gameFileBytes` (heap).
- **Error handling**: Every fallible operation returns `Result<T>` or sealed `Result` class. No exceptions for expected failures (wrong ROM, corrupt patch, network error).
- **Coroutines**: All I/O on `Dispatchers.IO`. UI on `Dispatchers.Main`. ViewModel uses `viewModelScope`.
- **No Dagger/Hilt**: Manual DI via `di/AppContainer` in `Application` subclass.
- **JNI Safety**: `read_memory` returns 0 bytes if `LibretroDroidMemoryJni.getMemoryData` returns null (core not ready). `server_call` completion callback uses `AttachCurrentThread` if not on JVM thread. `rc_client_unload_game` + `rc_client_destroy` in `InGameRaViewModel.onCleared()` before core teardown.
- **Hardcore default OFF**: `pref_ra_hardcore = false` until UA validated with RAdmin.
- **Leaderboards never overlaid**: Only DialogFragment in GameActivity menu.
- **RA hash from final patched ROM only**: Install-time, after BPS/ZPF applied.

