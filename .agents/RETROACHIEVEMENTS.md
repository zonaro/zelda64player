# RetroAchievements (RA) — Feature Deep Dive

> Migrated from `plano.md` (phases B1–B5). This is the authoritative RA spec.

## 1. Overview

RetroAchievements integration: users log in with their RA account, see unlocked/pending achievements per installed game, receive in-game unlock notifications (custom toast + badge), and access leaderboards **only inside the in-game menu** (GameActivity menu — never as an overlay over gameplay). Integration uses **rcheevos** (MIT, ANSI C) via JNI, with the vendored LibretroDroid 0.13.2 exposing core memory (RDRAM) pointers to rcheevos.

### Current identity and catalog flow

- `RaSessionManager.start` receives the library `hackId`. After the live rcheevos client identifies the final playable ROM, the session writes its verified hash, game ID and title to the shared `Zelda64PlayerApp.raInstallMetadataStore` before publishing `Running`. This applies to patched hacks and original games (`vanilla_<crc32>`); original games use the user's normalized playable ROM.
- Profile/achievement screens resolve identity through `RaHashService.ensureIdentity` and the same shared metadata store. Lazy resolution merges with the latest stored identity so a failed lookup does not discard an identity already supplied by the live session. Original games no longer launch a separate competing hash request alongside session identification.
- `RaCatalogRepository` builds authenticated standalone rapi requests using the stored username/token. HTTP response bodies pass through `nativeProcessFetchGameDataResponse` or `nativeProcessFetchUserUnlocksResponse` before Kotlin parses the normalized JSON. The raw service payload is not the app's data model.
- An unlock request failure returns `null`; a successful empty set means zero unlocked achievements. Achievement detail reports request failures, while the profile skips games whose data could not be loaded. The leaderboard dialog reports a failed request separately from a successful response with no visible leaderboards.
- **Leaderboard UI scope:** `RaLeaderboardDialogFragment` currently displays visible leaderboard definitions (title and description) in the in-game menu. It does not fetch or display ranking entries, player positions or scores; that portion of the original scope remains outstanding.

These describe the implementation contract, not evidence of successful device validation or a verified achievement unlock.

## 2. User Decisions (Final)

1. **Full scope INCLUDING leaderboards**, but leaderboards appear **only in the in-game menu** (GameActivity menu). No tracker/overlay over gameplay.
2. **Login screen** accessed from Library (main screen); first login with user+password, token stored encrypted (separate prefs file); subsequent logins silent via token; logout supported.
3. **Achievements screen** shows progress of all installed games (via RA hash computed at install + gameId resolution via rapi, badges loaded with Coil); tapping a game opens the full achievement list.
4. **Achievement unlock** generates a custom in-game toast popup (View over GLRetroView) WITH badge icon + optional system notification (toggle in settings, default ON; needs `POST_NOTIFICATIONS` on API 33+).
5. **Catalog gains optional RA compatibility metadata** (JSON backward-compatible; `catalogVersion` bump). Store UI shows RA badge on compatible hacks. At **INSTALL**, compute RA hash (via rhash exposed by our JNI) and resolve `gameId`, caching `{raHash, raGameId, raTitle}` per `hackId`.
6. **Hardcore mode:** setting exists but default **OFF** (softcore) until User-Agent validated with RAdmin.

## 3. rcheevos + LibretroDroid (Vendored)

### Why vendor LibretroDroid 0.13.2?
LibretroDroid 0.13.2 (JitPack `com.github.swordfish90:libretrodroid`) does **not** expose core memory to app code. Its `GLRetroEvents.FrameRendered` Flow describes rendered frames and is not the achievement evaluation clock. The vendored core loop provides a synchronous callback after each `retro_run`, including extra iterations during fast-forward.

**Decision:** Vendor LibretroDroid 0.13.2 source in local Gradle module `:libretrodroid` and expose core memory plus synchronous frame observation:
- `LibretroDroid.getMemoryData(id: Int): ByteBuffer?` — direct buffer wrapping `retro_get_memory_data`
- `LibretroDroid.getMemorySize(id: Int): Int` — region size
- Frame observer — evaluates the RA session immediately after each `retro_run` while `coreLock` remains held.

For N64 + mupen64plus-next, `RETRO_MEMORY_SYSTEM_RAM` is **RDRAM** (8MB with expansion pak) at a stable address while the game loads; RA N64 addresses map **directly into RDRAM**.

### rcheevos (MIT) — High-Level API (`rc_client_t`)

| Function | Purpose |
|----------|---------|
| `rc_client_create(read_memory_fn, server_call_fn)` | Create client; required callbacks |
| `rc_client_begin_login_with_password(user, pass)` | Initial login |
| `rc_client_begin_login_with_token(token)` | Silent subsequent login |
| `rc_client_get_user_info` | username/token/display_name/score |
| `rc_client_begin_identify_and_load_game(client, RC_CONSOLE_NINTENDO_64, file_path, NULL, 0, cb, ud)` | Uses internal rhash to compute correct RA hash of ROM file (handles N64 byte-order/header); hash computed on **final patched ROM** |
| `rc_client_get_game_info` | title/badge_name/badge_url |
| `rc_client_get_user_game_summary` | num unlocked/total |
| `rc_client_create_achievement_list(client, category, grouping)` | Returns buckets (label + achievements with title/description/points/badge_url/badge_locked_url/unlocked/measured_progress) |
| `rc_client_destroy_achievement_list` | Free list |
| `rc_client_do_frame(client)` | Call 1× per emulated frame |
| `rc_client_set_event_handler` | Events: ACHIEVEMENT_TRIGGERED, ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW/HIDE, ACHIEVEMENT_PROGRESS_INDICATOR_SHOW/UPDATE/HIDE, LEADERBOARD_STARTED/FAILED/SUBMITTED, LEADERBOARD_TRACKER_SHOW/UPDATE/HIDE, GAME_MASTERY, etc. |
| `rc_client_set_hardcore_enabled` | Hardcore on/off |
| `rc_client_enable_logging` | Debug logs |
| `rc_client_disconnect` | Logout |
| `rc_client_unload_game` | Unload current game |
| `rc_client_set_userdata/get_userdata` | User data pointer |

**Host must implement:**
- `read_memory(address, buffer, num_bytes)` → bytes read (called from rcheevos thread)
- `server_call(request, callback, callback_data, client)` → **ASYNC HTTP** (GET if `request->post_data==NULL` else POST); invoke callback with `rc_api_server_response_t{body, body_length, http_status_code}` from **any thread**
- `log_message` → internal log

**Required User-Agent:** `<product>/<semver> (<system-info>) <extensions>` e.g. `Zelda64Player/1.0 (Android) rcheevos/12.x`. Hardcore unlocks need UA validated by RAdmin; until then, server downgrades to softcore.

### rapi (standalone requests)
Headers `rc_api_user.h`, `rc_api_runtime.h` allow building standalone requests (login, fetch_game_data, fetch_user_unlocks, resolve_hash, fetch_leaderboards, fetch_leaderboard_entries) — useful for showing data of **non-running** games without booting cores.

## 4. Original Package Plan (`retroachievements/`)

This historical plan includes proposed classes and ranking-entry UI. For the implemented identity/catalog flow and current leaderboard scope, see section 1 above.

```
retroachievements/
├── jni/
│   ├── RcheevosJni.kt              # JNI bridge: nativeInit, nativeShutdown, nativeDoFrame, nativeReadMemory, nativeServerCall, nativeServerCallComplete, nativeLogin, nativeLogout, nativeIdentifyGame, nativeGetAchievements, nativeGetLeaderboards, nativeSetHardcore, nativeSetUserdata
│   └── LibretroDroidMemoryJni.kt   # JNI bridge to :libretrodroid module: getMemoryData(id), getMemorySize(id)
├── api/
│   ├── RaHttpClient.kt             # OkHttp dispatcher for rc_api requests (login, fetch_game_data, fetch_user_unlocks, resolve_hash, fetch_leaderboards, fetch_leaderboard_entries) — implements server_call callback
│   ├── RaApiModels.kt              # Data classes for requests/responses rapi
│   └── RaApiException.kt           # Sealed hierarchy: AuthError, NetworkError, RateLimited, ServerError, NotFound
├── auth/
│   ├── RaCredentialStore.kt        # EncryptedSharedPreferences (pref_ra_token, pref_ra_username, pref_ra_hardcore_token) — OotrApiKeyStore pattern
│   ├── RaSessionManager.kt         # Lifecycle: login(password) → token, login(token) → restore, logout, auto-refresh token, user info cache
│   └── RaLoginFragment.kt          # Settings fragment: username/password + "Get Token" link → RA site; silent token login
├── data/
│   ├── RaGameMetadata.kt           # Cached per hackId: raHash (String), raGameId (Int), raTitle (String), badgeUrl (String?), consoleId (Int = RC_CONSOLE_NINTENDO_64)
│   ├── RaAchievement.kt            # Achievement model: id, title, description, points, badgeUrl, badgeLockedUrl, unlocked, measuredProgress, measuredTarget, category, grouping
│   ├── RaLeaderboard.kt            # Leaderboard model: id, title, description, format, lowerIsBetter, entries (rank, score, user, date)
│   └── RaRepository.kt             # Persists RaGameMetadata per hackId (JSON in filesDir/ra_metadata.json); caches achievement lists per gameId (cacheDir/ra_achievements_<gameId>.json)
├── ui/
│   ├── AchievementsActivity.kt     # Main screen: RecyclerView of installed games with progress (unlocked/total), game badge, cover; click → AchievementDetailActivity
│   ├── AchievementDetailActivity.kt # Full list: sections by category/grouping, Coil-loaded badges, progress bars, unlocked state
│   ├── InGameAchievementOverlay.kt # Custom View overlay (addView in GameActivity FrameLayout): animated toast with badge icon + title + points; queue for rapid multiple unlocks
│   └── LeaderboardDialog.kt        # DialogFragment from GameActivity menu: tabs (game leaderboards), RecyclerView entries, Coil badges
├── viewmodel/
│   ├── AchievementsViewModel.kt    # StateFlow: installedGamesWithRA (List<RaGameMetadata>), selectedGameAchievements, loading states
│   └── InGameRaViewModel.kt        # Tied to GameActivityViewModel lifecycle: rc_client_do_frame driven by FrameRendered Flow; event handler → posts to overlay/notification
└── install/
    ├── RaHashService.kt            # Install-time: given hackId + patched ROM path → compute RA hash via JNI (rhash), resolve gameId via rapi, cache RaGameMetadata
    └── RaInstallRepository.kt      # Persists install-time RA metadata alongside existing install metadata
```

## 5. Runtime Evaluation and Threading

| Component | Execution | Details |
|-----------|-----------|---------|
| `rc_client_do_frame` | Emulation/GL thread | Synchronous observer after every `retro_run`, under LibretroDroid's `coreLock`; includes fast-forward iterations. No asynchronous rendered-frame Flow is used for achievement evaluation. |
| `read_memory` callback | Calling rcheevos operation's thread | Reads the attached direct SYSTEM_RAM buffer; frame evaluation observes memory before the next emulated frame can mutate it. |
| Live client JNI operations | Serialized by `g_client_mutex` | Frame evaluation, HTTP response completion, idle processing and teardown cannot concurrently mutate the rcheevos client. |
| HTTP bridge | Asynchronous request completion | Network work runs outside frame evaluation; response completion re-enters the native client under its mutex. |
| `rc_client_idle` | Session coroutine, once per second | Processes pending work/retries even when gameplay is paused; it does not emulate frames. Cancelled when the session stops. |
| UI notifications | Main dispatcher | Native events are relayed through the session to UI collectors for toast/notification updates. |
| Standalone catalog requests | `Dispatchers.IO` | Fetch definitions and user unlocks without starting a core. |

### Memory and lifecycle

The session obtains and attaches SYSTEM_RAM before login/game loading, because achievement activation can inspect memory before the game-load callback completes. Missing memory fails startup. The direct buffer remains valid only while the core owns that ROM. Session stop disables evaluation, cancels idle work, detaches memory and unloads/destroys the native client before core disposal. Native reads with no attached memory return zero bytes.

### Verification scope and remaining work

- `python3 scripts/test-ra-runtime.py` builds the actual JNI bridge with vendored rcheevos on Linux, using synthetic RAM, a synthetic ROM and a simulated server without network access. Its assertions cover condition-triggered unlock, softcore award requests, retry through idle while paused, summary/list refresh, duplicate prevention, restored server unlocks, detached memory, serialized hit counts, resets, delayed callback teardown and replay signing. This is protocol/runtime regression coverage, not proof of an award accepted by the real service.
- `libretrodroid/src/test/cpp/frameiteration_test.cpp` checks the shared frame-iteration helper, including observer ordering and multiple fast-forward iterations. Android/core/device execution requires separate validation.
- No newly earned achievement on the user's real account has been verified by this work yet. Successful identification or a displayed achievement count does not establish unlock/submission completeness.
- Save-state integration stores core bytes, RA hit-count progress and ROM hash in one checksummed record, written using AtomicFile. Core-locked callbacks capture both in the same frame and restore RA only after successful core restoration. Legacy states reset RA progress; mismatched/corrupt pairs are rejected. Core reset calls `rc_client_reset`.
- Hardcore policy rejects state loading and cheats at the core boundary, blocks Auto-Ocarina input playback, enforces the native pause gate (forced lifecycle pauses downgrade to Casual), and handles native reset requests. Saving states is allowed. Fast-forward is allowed with per-emulated-frame evaluation; slowdown below 1x is prevented. These technical controls do not imply official User-Agent approval by RetroAchievements.
- `RaAwardOutbox` journals actual native awards synchronously in encrypted preferences before HTTP. WorkManager retries after session/process termination using the same account, original mode/hash/time and the official rapi builder with a fresh elapsed offset. Tokens are never persisted in the journal. Only an acknowledgment for the exact achievement removes it. Definitive rejections remain recorded and are surfaced; a fresh login permits retry. Late native callbacks are drained before client destruction to avoid accessing a freed session.

## 6. Data Model Changes

### HackEntry — Optional `retroAchievements` Field
```json
{
  "id": "ocarina_of_time_dx",
  "retroAchievements": {
    "supported": true,
    "gameId": 12345
  }
}
```
- **Backward-compatible:** optional field; old apps ignore.
- `catalogVersion` bumped to **2** (graceful migration: missing field = `supported=false`).
- `gameId` optional in catalog (may be resolved at install via hash); if present, Store shows RA badge immediately.

### Shared Identity Storage (per Library Game)

`RaInstallMetadataStore` persists `filesDir/ra_metadata.json` as an object keyed by `hackId`, shared by installation, lazy identity resolution and live session identification:

```json
{
  "ocarina_of_time_dx": {
    "raHash": "...",
    "gameId": 12345,
    "title": "Ocarina of Time DX"
  }
}
```

Original games use `vanilla_<crc32>` keys in the same document. `gameId: 0` represents an unresolved identity; the title may be null. The hash always describes the final playable ROM supplied to the core.

### Settings Keys (CorePrefs Convention)
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `pref_ra_enabled` | Boolean | `true` | Master toggle RA integration |
| `pref_ra_hardcore` | Boolean | `false` | Hardcore mode (OFF until UA validated) |
| `pref_ra_system_notifications` | Boolean | `true` | System notification on unlock (API 33+ needs POST_NOTIFICATIONS) |
| `pref_ra_show_challenge_indicators` | Boolean | `true` | Show challenge/progress indicators in-game |
| `pref_ra_username` | String | `""` | Cached username (display only) |

## 7. Manifest Changes
```xml
<activity android:name=".retroachievements.ui.AchievementsActivity" android:theme="@style/Theme.Zelda64Player.NoActionBar" />
<activity android:name=".retroachievements.ui.AchievementDetailActivity" android:theme="@style/Theme.Zelda64Player.NoActionBar" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## 8. Navigation Entry Point (LibraryActivity)
`LibraryActivity` header has buttons: Settings, Store, **+ Achievements (trophy icon)**. `binding.libraryAchievements` → `startActivity(Intent(this, AchievementsActivity::class.java))`. Icon: Dolfi-generated trophy SVG (Material Icons outlined style, 24dp).

## 9. Historical Implementation Phases

The list below records the original phase breakdown, not current verification results. B5 ranking-entry display is still incomplete; see section 1.

- **B1 Foundation:** Vendored LibretroDroid + rcheevos native + JNI bridge; `rc_client_create` + `rc_client_do_frame` per frame (log "RA frame tick").
- **B2 Auth + Session + Install-time Hash:** `RaCredentialStore`, `RaSessionManager`, `RaLoginFragment`, `RaHttpClient` complete, `RaRepository`, `RaHashService`; integrated into DownloadManager install flow; Store RA badge.
- **B3 Achievements Screens:** `AchievementsActivity` + `AchievementsViewModel`, `AchievementDetailActivity` + ViewModel, `RaApiModels`/`RaApiException`; i18n; accessibility; planned Chululu visual QA.
- **B4 In-Game Overlay + Notifications + Indicators:** `InGameRaViewModel`, `InGameAchievementOverlay`, system notification, Settings RA section, hardcore toggle.
- **B5 Leaderboards (In-Game Menu Only) + Catalog Integration + Polish:** `LeaderboardDialog`, in-game menu "Conquistas" category, catalog v2, Store RA badge, i18n, third-party license notices (rcheevos MIT).

## 10. Risk Register (RA)
| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| rcheevos native build fails on an ABI | Medium | High | Pin rcheevos tag; test all 4 ABIs in CI |
| `read_memory` called after unload → crash | Low | High | Null-guard in `getMemoryData`; return 0 bytes |
| Hardcore unlock rejected (UA not validated) | High | Low | Default OFF; show notice until RAdmin validates |
| Missed evaluation during fast-forward | Low | High | Observe every `retro_run` synchronously under `coreLock`; exercise device/core behavior separately |
| Process exit loses pending offline awards | Medium | High | Encrypted synchronous journal and network-constrained WorkManager replay; exact-id acknowledgment |
| Hardcore approval pending | High | Medium | Technical restrictions implemented; default OFF pending external User-Agent validation |
| Token expiry mid-session | Medium | Medium | `RaSessionManager` auto-refresh; silent re-login |
| Notification permission denied (API 33+) | Medium | Low | Graceful degrade to in-game toast only |
