## Visão Geral

Adicionar suporte completo a **RetroAchievements (RA)** ao emulador, permitindo que usuários loguem com sua conta RA, vejam conquistas desbloqueadas/pendentes por jogo instalado, recebam notificações de desbloqueio *in-game* (toast customizado + badge), e acessem leaderboards **apenas dentro do menu in-game** (GameActivity menu — **nunca** como overlay sobre o gameplay). A integração usa a biblioteca **rcheevos** (MIT, ANSI C) via JNI, com o core LibretroDroid 0.13.2 vendorado localmente para expor ponteiros de memória (RDRAM) ao rcheevos.

### Decisões do Usuário (Finais)

1. **Escopo completo INCLUINDO leaderboards**, MAS leaderboards **só aparecem no menu in-game** (GameActivity menu). Nada de tracker/overlay sobre o gameplay.
2. **Tela de login** acessada da Library (tela principal); primeiro login com usuário+senha, token armazenado criptografado (reuso padrão `OotrApiKeyStore`, prefs file separado); logins subsequentes silenciosos via token; suporte a logout.
3. **Tela de Conquistas** mostra progresso de todos os jogos instalados (via hash RA computado no install + resolução gameId via rapi, badges carregados com Coil); tocar num jogo abre lista completa de conquistas.
4. **Desbloqueio de conquista** gera toast-style popup **customizado in-game** (View sobre o GLRetroView, mais confiável que Toast de sistema sobre fullscreen GL) COM o ícone da badge **+** notificação de sistema opcional (toggle nas settings, default ON; precisa `POST_NOTIFICATIONS` no API 33+).
5. **Catálogo ganha metadados opcionais de compatibilidade RA** (JSON backward-compatible; bump de `catalogVersion`). Store UI mostra badge RA em hacks compatíveis. No **INSTALL**, computa hash RA (via rhash exposto pelo nosso JNI) e resolve `gameId`, cacheando `{raHash, raGameId, raTitle}` por `hackId` para a tela Library.
6. **Hardcore mode**: setting existe mas default **OFF** (softcore) até UA ser validado com RAdmin.

---

## Arquitetura rcheevos + LibretroDroid (Vendored)

### Por que vendor LibretroDroid 0.13.2?

O LibretroDroid 0.13.2 (JitPack `com.github.swordfish90:libretrodroid`) **não expõe memória do core** ao código do app. Porém, seu `GLRetroView` **emite `GLRetroEvents.FrameRendered` a CADA frame** (Flow, emitido pós-frame da thread GL para main dispatcher) — usável como tick per-frame **sem forkar**.

**Decisão aprovada**: Vendor LibretroDroid 0.13.2 source em módulo Gradle local `:libretrodroid` e adicionar **dois JNI passthroughs mínimos**:
- `LibretroDroid.getMemoryData(id: Int): ByteBuffer?` — direct buffer wrapando ponteiro `retro_get_memory_data`
- `LibretroDroid.getMemorySize(id: Int): Int` — tamanho da região

Para N64 + mupen64plus-next, `RETRO_MEMORY_SYSTEM_RAM` é **RDRAM** (8MB com expansion pak) em endereço estável enquanto o jogo carrega; endereços RA N64 mapeiam **direto na RDRAM**.

### rcheevos (MIT) — API de Alto Nível (`rc_client_t`)

| Função | Propósito |
|--------|-----------|
| `rc_client_create(read_memory_fn, server_call_fn)` | Cria cliente; callbacks obrigatórios |
| `rc_client_begin_login_with_password(user, pass)` | Login inicial |
| `rc_client_begin_login_with_token(token)` | Login silencioso subsequente |
| `rc_client_get_user_info` | username/token/display_name/score |
| `rc_client_begin_identify_and_load_game(client, RC_CONSOLE_NINTENDO_64, file_path, NULL, 0, cb, ud)` | **Usa rhash interno** para computar hash RA correto do arquivo ROM (trata byte-order/header N64); hash computado na **ROM final patcheada** |
| `rc_client_get_game_info` | title/badge_name/badge_url |
| `rc_client_get_user_game_summary` | num unlocked/total |
| `rc_client_create_achievement_list(client, category, grouping)` | Retorna buckets (label + achievements com title/description/points/badge_url/badge_locked_url/unlocked/measured_progress) |
| `rc_client_destroy_achievement_list` | Libera lista |
| `rc_client_do_frame(client)` | **Chamar 1x por frame emulado** |
| `rc_client_set_event_handler` | Eventos: ACHIEVEMENT_TRIGGERED, ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW/HIDE, ACHIEVEMENT_PROGRESS_INDICATOR_SHOW/UPDATE/HIDE, LEADERBOARD_STARTED/FAILED/SUBMITTED, LEADERBOARD_TRACKER_SHOW/UPDATE/HIDE, GAME_MASTERY, etc. |
| `rc_client_set_hardcore_enabled` | Hardcore on/off |
| `rc_client_enable_logging` | Debug logs |
| `rc_client_disconnect` | Logout |
| `rc_client_unload_game` | Descarrega jogo atual |
| `rc_client_set_userdata/get_userdata` | Ponteiro user data |

**Host deve implementar**:
- `read_memory(address, buffer, num_bytes)` → retorna bytes lidos (chamado pela thread do rcheevos)
- `server_call(request, callback, callback_data, client)` → **HTTP ASYNC** (GET se `request->post_data==NULL` senão POST); invoca callback com `rc_api_server_response_t{body, body_length, http_status_code}` de **qualquer thread**
- `log_message` → log interno

**User-Agent obrigatório**: `<produto>/<semver> (<system-info>) <extensões>` ex: `Zelda64Player/1.0 (Android) rcheevos/12.x`. Hardcore unlocks precisam UA validado por RAdmin; até lá, server faz downgrade para softcore.

### rapi (standalone requests)

Headers `rc_api_user.h`, `rc_api_runtime.h` permitem construir requests standalone (login, fetch_game_data, fetch_user_unlocks, resolve_hash, fetch_leaderboards, fetch_leaderboard_entries) — úteis para mostrar dados de jogos **não-rodando** sem bootar cores.

---

## Nova Estrutura de Pacotes: `retroachievements/`

```
br.com.redclaw.zelda64player
├── retroachievements/
│   ├── jni/
│   │   ├── RcheevosJni.kt              # JNI bridge: nativeInit, nativeShutdown, nativeDoFrame, nativeReadMemory, nativeServerCall, nativeLogin, nativeLogout, nativeIdentifyGame, nativeGetAchievements, nativeGetLeaderboards, nativeSetHardcore, nativeSetUserdata
│   │   └── LibretroDroidMemoryJni.kt   # JNI bridge para :libretrodroid module: getMemoryData(id), getMemorySize(id)
│   ├── api/
│   │   ├── RaHttpClient.kt             # OkHttp dispatcher para rc_api requests (login, fetch_game_data, fetch_user_unlocks, resolve_hash, fetch_leaderboards, fetch_leaderboard_entries) — implementa server_call callback do rcheevos
│   │   ├── RaApiModels.kt              # Data classes para requests/responses rapi
│   │   └── RaApiException.kt           # Sealed hierarchy: AuthError, NetworkError, RateLimited, ServerError, NotFound
│   ├── auth/
│   │   ├── RaCredentialStore.kt        # EncryptedSharedPreferences (pref_ra_token, pref_ra_username, pref_ra_hardcore_token) — padrão OotrApiKeyStore
│   │   ├── RaSessionManager.kt         # Lifecycle: login(password) → token, login(token) → restore, logout, auto-refresh token, user info cache
│   │   └── RaLoginFragment.kt          # Settings fragment: username/password + "Get Token" link → RA site; token login silencioso
│   ├── data/
│   │   ├── RaGameMetadata.kt           # Cached per hackId: raHash (String), raGameId (Int), raTitle (String), badgeUrl (String?), consoleId (Int = RC_CONSOLE_NINTENDO_64)
│   │   ├── RaAchievement.kt            # Achievement model: id, title, description, points, badgeUrl, badgeLockedUrl, unlocked, measuredProgress, measuredTarget, category, grouping
│   │   ├── RaLeaderboard.kt            # Leaderboard model: id, title, description, format, lowerIsBetter, entries (rank, score, user, date)
│   │   └── RaRepository.kt             # Persists RaGameMetadata per hackId (JSON em filesDir/ra_metadata.json); caches achievement lists per gameId (cacheDir/ra_achievements_<gameId>.json)
│   ├── ui/
│   │   ├── AchievementsActivity.kt     # Main screen: RecyclerView de jogos instalados com progresso (unlocked/total), badge do jogo, cover; click → AchievementDetailActivity
│   │   ├── AchievementDetailActivity.kt # Full list: sections por categoria/grouping, badges Coil-loaded, progress bars, unlocked state
│   │   ├── InGameAchievementOverlay.kt # Custom View overlay (addView no FrameLayout do GameActivity): toast animado com badge icon + title + points; queue para múltiplos desbloqueios rápidos
│   │   └── LeaderboardDialog.kt        # DialogFragment shown from GameActivity menu: tabs (leaderboards do jogo), RecyclerView entries, Coil badges
│   ├── viewmodel/
│   │   ├── AchievementsViewModel.kt    # StateFlow: installedGamesWithRA (List<RaGameMetadata>), selectedGameAchievements, loading states
│   │   └── InGameRaViewModel.kt        # Tied to GameActivityViewModel lifecycle: rc_client_do_frame driven by FrameRendered Flow; event handler → posts to overlay/notification
│   └── install/
│       ├── RaHashService.kt            # Install-time: given hackId + patched ROM path → compute RA hash via JNI (rhash), resolve gameId via rapi, cache RaGameMetadata
│       └── RaInstallRepository.kt      # Persists install-time RA metadata alongside existing install metadata
├── libretrodroid/                      # NOVO MÓDULO GRADLE LOCAL (vendor)
│   └── src/main/...                    # LibretroDroid 0.13.2 source + 2 JNI passthroughs (getMemoryData, getMemorySize)
└── app/src/main/cpp/rcheevos/          # rcheevos sources vendored (git subtree pinned to release tag master~12.x)
```

---

## Modelo de Threading

| Componente | Thread | Detalhes |
|------------|--------|----------|
| `rc_client_do_frame` | **Main thread** | Drivido por `GLRetroEvents.FrameRendered` Flow (já em main dispatcher). Chamado 1x por frame. |
| `read_memory` callback | **Thread do rcheevos** (background) | Recebe endereço N64 (RDRAM offset). Deve ler via `LibretroDroidMemoryJni.getMemoryData(RETRO_MEMORY_SYSTEM_RAM)` → `ByteBuffer` → copy para buffer de saída. **Ponteiro válido apenas enquanto core rodando**. |
| `server_call` callback | **Qualquer thread** (OkHttp callback) | `RaHttpClient` faz request assíncrono OkHttp; no `onResponse`/`onFailure`, invoca callback C do rcheevos via JNI `nativeServerCallComplete(requestPtr, responseBody, httpStatus)`. **Marshaling thread-safe**: JNI `AttachCurrentThread` se necessário. |
| `event_handler` callbacks | **Thread do rcheevos** | Eventos: ACHIEVEMENT_TRIGGERED, CHALLENGE_INDICATOR_*, PROGRESS_INDICATOR_*, LEADERBOARD_*, GAME_MASTERY. **Post para Main** via `Handler(Looper.getMainLooper())` ou `runOnUiThread` → `InGameRaViewModel` processa → mostra overlay/notification. |
| `RaHttpClient` (rapi standalone) | **Dispatchers.IO** | Coroutines + OkHttp. Usado pela AchievementsActivity/ViewModel para fetch sem core rodando. |
| Teardown / GL destroy | **Main thread** | Ordem crítica (invariante existente): `super.onDestroy()` ANTES de `dispose()` → dispatch ON_DESTROY libera ~90MB nativos. `RaSessionManager` deve chamar `rc_client_unload_game` + `rc_client_destroy` **antes** do core ser destruído. `InGameRaViewModel.onCleared()` faz cleanup. |

### Guarda contra ponteiro inválido (game unload/reload)

- `rc_client_unload_game` chamado em `InGameRaViewModel.onCleared()` (ViewModel cleared quando GameActivity destroyed).
- `read_memory` **pode** ser chamado após unload se rcheevos ainda processando frame anterior → **defesa**: `LibretroDroidMemoryJni.getMemoryData` retorna `null` se core não inicializado; `read_memory` retorna 0 bytes lidos (rcheevos trata como falha de leitura, não crash).
- **Leituras rasgadas (torn reads)** da main thread durante avaliação de conquistas: **aceitável v1**, documentado. RDRAM não é atômica; rcheevos lê palavras de 1-4 bytes. Probabilidade baixa, impacto visual apenas (conquista dispara 1 frame tarde). Mitigação futura: travar emulação durante `do_frame` (precisa fork LibretroDroid).

---

## Integração Native Build (CMake + rcheevos)

### Estrutura

```
app/
├── src/main/cpp/
│   ├── CMakeLists.txt              # App-level: add_subdirectory(rcheevos), link rcheevos + libretrodroid JNI
│   ├── rcheevos/                   # VENDORED rcheevos sources (git subtree pinned to tag)
│   │   ├── include/rc_client.h
│   │   ├── include/rc_api_*.h
│   │   ├── src/rc_client.c
│   │   ├── src/rc_api_*.c
│   │   ├── src/rc_compat.c
│   │   ├── src/md5.c, sha1.c       # deps internas
│   │   └── LICENSE (MIT)           # MANTER
│   ├── ra_jni_bridge.c             # Thin JNI: RcheevosJni + LibretroDroidMemoryJni implementations
│   └── ra_jni_bridge.h
├── build.gradle.kts                # externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }
└── libretrodroid/                  # Módulo Gradle separado (vendor)
    ├── build.gradle.kts
    └── src/main/...                # LibretroDroid 0.13.2 + 2 JNI passthroughs
```

### CMakeLists.txt (app/src/main/cpp)

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("zelda64player_ra" LANGUAGES C CXX)

# rcheevos vendored (pinned to release tag)
add_subdirectory(rcheevos)

# JNI bridge
add_library(ra_jni_bridge SHARED ra_jni_bridge.c)
target_link_libraries(ra_jni_bridge PRIVATE rcheevos log android)
target_include_directories(ra_jni_bridge PRIVATE rcheevos/include ${CMAKE_CURRENT_SOURCE_DIR})

# Link com libretrodroid JNI (do módulo :libretrodroid) — via find_library ou imported target
find_library(LIBRETRODROID_JNI libretrodroid_jni PATHS ${CMAKE_SOURCE_DIR}/../libretrodroid/build/intermediates/cmake/debug/obj)
target_link_libraries(ra_jni_bridge PRIVATE ${LIBRETRODROID_JNI})
```

### Vendoring Strategy: **Git Subtree (Recomendado)**

```bash
# No repo zelda64player:
git subtree add --prefix=app/src/main/cpp/rcheevos https://github.com/RetroAchievements/rcheevos.git master --squash
# Para updates futuros:
git subtree pull --prefix=app/src/main/cpp/rcheevos https://github.com/RetroAchievements/rcheevos.git master --squash
```

**Por que subtree e não snapshot copiado?**
- Mantém histórico de updates rastreável
- Fácil `pull` para novas releases (tag `master` ~12.x series)
- LICENSE MIT mantido no lugar
- Evita "vendored snapshot esquecido por anos"

### ABI Coverage

Deve matchar `jniLibs` existentes: **x86, x86_64, armeabi-v7a, arm64-v8a**. CMake `ANDROID_ABI` loop no `build.gradle.kts` do app.

### Debug Symbols / Stripping

- `debug` build: `-g` symbols kept, `strip` disabled
- `release` build: `-O2 -DNDEBUG`, `strip` enabled (default AGP). `rcheevos` não tem símbolos sensíveis.

---

## Mudanças no Modelo de Dados

### HackEntry — Campo Opcional `retroAchievements`

```json
{
  "id": "ocarina_of_time_dx",
  "name": "Ocarina of Time DX",
  ...
  "retroAchievements": {
    "supported": true,
    "gameId": 12345,
    "title": "The Legend of Zelda: Ocarina of Time",
    "badgeName": "Ocarina of Time",
    "badgeUrl": "https://media.retroachievements.org/Badge/12345.png"
  }
}
```

- **Backward-compatible**: campo opcional; apps antigos ignoram.
- `catalogVersion` bumped para **2** (migração graceful: campo ausente = `supported=false`).
- `gameId` opcional no catálogo (pode ser resolvido no install via hash); se presente, Store mostra badge RA imediatamente.

### Armazenamento por Hack (Install-time)

Local: `filesDir/ra_metadata.json` (JSON array de `RaGameMetadata` keyed by hackId)

```json
{
  "schemaVersion": 1,
  "entries": {
    "ocarina_of_time_dx": {
      "raHash": "a1b2c3d4e5f6...",
      "raGameId": 12345,
      "raTitle": "The Legend of Zelda: Ocarina of Time",
      "badgeUrl": "https://media.retroachievements.org/Badge/12345.png",
      "consoleId": 13
    }
  }
}
```

### Settings Keys (CorePrefs Convention)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `pref_ra_enabled` | Boolean | `true` | Master toggle RA integration |
| `pref_ra_hardcore` | Boolean | `false` | Hardcore mode (OFF até UA validado) |
| `pref_ra_system_notifications` | Boolean | `true` | System notification on unlock (API 33+ needs POST_NOTIFICATIONS) |
| `pref_ra_show_challenge_indicators` | Boolean | `true` | Show challenge/progress indicators in-game |
| `pref_ra_username` | String | `""` | Cached username (display only) |

---

## Mudanças no Manifest

```xml
<!-- Nova Activity -->
<activity
    android:name=".retroachievements.ui.AchievementsActivity"
    android:exported="false"
    android:theme="@style/Theme.Zelda64Player.NoActionBar" />

<activity
    android:name=".retroachievements.ui.AchievementDetailActivity"
    android:exported="false"
    android:theme="@style/Theme.Zelda64Player.NoActionBar" />

<!-- Permissão para notificações de sistema (API 33+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Ponto de Entrada na Navegação (LibraryActivity)

`LibraryActivity` já tem 3 botões no header: Settings, Store, Adicionar **4º botão: Conquistas (ícone troféu)**.

- Verificar `LibraryMenuController` / `MenuGridBuilder` — o padrão atual usa `binding.librarySettings`, `binding.libraryStore`, `binding.` (ImageView/ImageButton no header).
- Adicionar `binding.libraryAchievements` → `startActivity(Intent(this, AchievementsActivity::class.java))`.
- Ícone: Dolfi gera SVG troféu consistente com estilo atual (Material Icons outlined style, 24dp).

---

## Plano de Implementação em Fases (B1–B5)

### Fase B1: Foundation — Vendored LibretroDroid + rcheevos Native + JNI Bridge (Semana 1)

**Objetivo**: Build compila, JNI carrega, `rc_client_create` + `rc_client_do_frame` chamado a cada frame (log de teste).

- [ ] Criar módulo Gradle `:libretrodroid` (vendor LibretroDroid 0.13.2 source do GitHub tag 0.13.2)
- [ ] Adicionar 2 JNI passthroughs no módulo `:libretrodroid`:
  - `Java_br_com_redclaw_zelda64player_libretrodroid_LibretroDroidMemoryJni_getMemoryData`
  - `Java_br_com_redclaw_zelda64player_libretrodroid_LibretroDroidMemoryJni_getMemorySize`
- [ ] No app module: `externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }`
- [ ] `git subtree add` rcheevos em `app/src/main/cpp/rcheevos/` (pinned to `master` tag ~12.x)
- [ ] `app/src/main/cpp/ra_jni_bridge.c` + `.h` — implementa `RcheevosJni` + `LibretroDroidMemoryJni` native methods
- [ ] `CMakeLists.txt` compila rcheevos + ra_jni_bridge, linka com `:libretrodroid` JNI lib
- [ ] `RcheevosJni.kt` + `LibretroDroidMemoryJni.kt` (Kotlin bindings, `System.loadLibrary("ra_jni_bridge")`)
- [ ] `RaHttpClient.kt` skeleton (OkHttp dispatcher para `server_call` callback)
- [ ] Integração mínima em `GameActivityViewModel` / `InGameRaViewModel`: `rc_client_create` no `launchHack`, `rc_client_do_frame` no `FrameRendered` Flow, `rc_client_destroy` no `onCleared`
- [ ] **Build verification**: `./gradlew :app:assembleDebug` + `./gradlew :libretrodroid:assembleDebug` sucesso
- [ ] **Runtime verification**: Log "RA frame tick" a cada frame no logcat (filtro `RetroAchievements`)

**Critério de aceite**: App compila, roda, core carrega, log "RA frame tick" aparece a cada frame sem crash.

---

### Fase B2: Auth + Session + Install-time Hash Resolution (Semana 2)

**Objetivo**: Login/logout funcional, token persistido criptografado, hash RA computado no install e cacheado.

- [ ] `RaCredentialStore.kt` (EncryptedSharedPreferences, prefs file `ra_secure_prefs`, keys: `pref_ra_token`, `pref_ra_username`, `pref_ra_hardcore_token`)
- [ ] `RaSessionManager.kt`: `login(username, password) → Result<Token>`, `loginWithToken() → Result<UserInfo>`, `logout()`, `getUserInfo()`, `refreshToken()`
- [ ] `RaLoginFragment.kt` (Settings): username/password fields, "Obter Token" link → `https://retroachievements.org/controlpanel.php` (página de API), botão Login, loading state, erro amigável
- [ ] `RaHttpClient.kt` completo: implementa `server_call` callback do rcheevos (async OkHttp → JNI callback completion) + rapi standalone methods (`fetchGameData`, `fetchUserUnlocks`, `resolveHash`, `fetchLeaderboards`, `fetchLeaderboardEntries`)
- [ ] `RaRepository.kt`: persiste `RaGameMetadata` por hackId em `filesDir/ra_metadata.json` (atomic write)
- [ ] `RaHashService.kt`: `suspend fun computeAndResolve(hackId: String, patchedRomPath: String): Result<RaGameMetadata>`
  - Chama `RcheevosJni.nativeComputeHash(romPath)` → usa `rhash` do rcheevos (já trata N64 byte-order/header)
  - Chama `RaHttpClient.resolveHash(hash)` → `gameId`
  - Chama `RaHttpClient.fetchGameData(gameId)` → title, badgeUrl
  - Salva no `RaRepository`
- [ ] Integração no `DownloadManager` / install flow: após patch aplicado e ROM patcheada escrita em `Storage.rom(hackId)`, chamar `RaHashService.computeAndResolve` (background, não bloqueia UI)
- [ ] Store UI: se `HackEntry.retroAchievements.supported == true`, mostra badge RA no card/bottom sheet
- [ ] **Unit tests**: `RaCredentialStoreTest`, `RaSessionManagerTest` (mock OkHttp), `RaHashServiceTest` (fixture ROM conhecida)
- [ ] **Build verification**: `./gradlew :app:assembleDebug :app:testDebugUnitTest`

**Critério de aceite**: Usuário loga com user/pass → token salvo → reloga app → login silencioso funciona; instala hack compatível → hash RA computado + gameId resolvido → metadata salvo em `ra_metadata.json`.

---

### Fase B3: Achievements Screens (Library) (Semana 3)

**Objetivo**: Tela de Conquistas (lista de jogos instalados com progresso) + tela de detalhe por jogo.

- [ ] `AchievementsActivity.kt` + `AchievementsViewModel.kt`
  - `StateFlow<List<RaGameMetadata>> installedGamesWithRA` — merge `InstalledLibrary.entries()` + `RaRepository.getAll()` (join por hackId)
  - RecyclerView grid/list: cover (Coil), título, badge do jogo (Coil), progresso "X/Y unlocked", points total
  - Empty state: "Nenhum jogo com conquistas instalado. Instale hacks compatíveis da Loja ou gere seeds."
- [ ] `AchievementDetailActivity.kt` + ViewModel
  - Recebe `gameId` + `hackId` via intent
  - `RaRepository.getAchievements(gameId)` → cache local (JSON) ou fetch via `RaHttpClient.fetchUserUnlocks` + `rc_client_create_achievement_list` (preferir cache; refresh pull-to-refresh)
  - UI: TabLayout por categoria/grouping (como RA site), RecyclerView por aba: badge (Coil), título, descrição, points, progress bar (measured), unlocked checkmark
  - Pull-to-refresh → re-fetch + update cache
- [ ] `RaApiModels.kt` + `RaApiException.kt` completos
- [ ] i18n: `strings.xml` pt-BR/en/es para todas as strings novas (chrome UI — labels de conquistas vêm da API em inglês, **não traduzir**)
- [ ] Acessibilidade: contentDescription, TalkBack, touch targets ≥48dp
- [ ] **Visual QA**: Chululu screenshots AchievementsActivity (grid vazio, grid populado), AchievementDetailActivity (abas, badges, progress)
- [ ] **Build verification**: `./gradlew :app:assembleDebug :app:connectedAndroidTest`

**Critério de aceite**: Abre Conquistas → vê jogos instalados com progresso → toca um → vê lista completa com badges, progresso, descrições → pull-to-refresh atualiza.

---

### Fase B4: In-Game Overlay + Notifications + Challenge/Progress Indicators (Semana 4)

**Objetivo**: Toast custom in-game no desbloqueio + notificação sistema opcional + indicadores de challenge/progress.

- [ ] `InGameRaViewModel.kt` (scoped to GameActivity lifecycle):
  - `rc_client_set_event_handler` → callbacks para:
    - `ACHIEVEMENT_TRIGGERED` → `InGameAchievementOverlay.show(achievement)`
    - `ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW/UPDATE/HIDE` → overlay indicator (pequeno, canto, não intrusivo)
    - `ACHIEVEMENT_PROGRESS_INDICATOR_SHOW/UPDATE/HIDE` → overlay progress bar (ex: "Kill 10 enemies: 7/10")
    - `LEADERBOARD_STARTED/FAILED/SUBMITTED` → log apenas (leaderboards só no menu)
    - `GAME_MASTERY` → overlay especial "Mastery!"
  - `rc_client_do_frame` driven by `RetroView.frameRendered` Flow (já em main)
  - `rc_client_unload_game` + `rc_client_destroy` em `onCleared()`
- [ ] `InGameAchievementOverlay.kt` (custom View):
  - Adicionado ao `FrameLayout` do GameActivity (acima do GLRetroView, abaixo do gamepad overlay)
  - Animação: slide-in from top → stay 3s → slide-out
  - Queue para múltiplos desbloqueios rápidos (sequencial, 3s cada)
  - Badge icon via Coil (carregado async, placeholder enquanto carrega)
  - Respeita `pref_ra_enabled` (se OFF, não mostra)
- [ ] System Notification (opcional, `pref_ra_system_notifications`):
  - `NotificationCompat.Builder` com `MediaStyle` ou `BigPictureStyle` (badge)
  - Channel `ra_unlocks` (importance HIGH, sound default)
  - API 33+: `POST_NOTIFICATIONS` runtime permission request no primeiro unlock se granted
- [ ] Settings integration: `SettingsActivity` ganha fragment/section "RetroAchievements" com toggles para: enabled, hardcore, system notifications, challenge/progress indicators
- [ ] Hardcore toggle: `rc_client_set_hardcore_enabled` (só efetivo se UA validado; default OFF)
- [ ] **Visual QA**: Chululu screenshots overlay in-game (desbloqueio, challenge indicator, progress indicator), notification shade
- [ ] **Build verification**: `./gradlew :app:assembleDebug`

**Critério de aceite**: Joga hack compatível logado → desbloqueia conquista → toast in-game aparece com badge + título + points → notificação sistema aparece (se enabled) → challenge/progress indicators aparecem/desaparecem corretamente → hardcore OFF por default.

---

### Fase B5: Leaderboards (In-Game Menu Only) + Catalog Integration + Polish (Semana 5)

**Objetivo**: Leaderboards acessíveis **apenas** no menu in-game (GameActivity menu), integração catálogo v2, polimento final.

- [ ] `LeaderboardDialog.kt` (DialogFragment):
  - Aberto via novo item no menu in-game (GameActivity menu grid: categoria "Conquistas" → "Leaderboards")
  - Tabs: um por leaderboard do jogo (fetch via `RaHttpClient.fetchLeaderboards(gameId)` → `fetchLeaderboardEntries(leaderboardId)`)
  - RecyclerView entries: rank, user, score (formatado per `format` field), date, badge do user (Coil)
  - **NUNCA** overlay sobre gameplay — só DialogFragment modal
- [ ] Integração no menu in-game (GameActivityViewModel.prepareMenu / menu grid builder):
  - Nova categoria "Conquistas" com itens: "Ver Conquistas" (abre AchievementsActivity via intent), "Leaderboards" (abre LeaderboardDialog)
  - Ícones: troféu, leaderboard (Dolfi)
- [ ] Catálogo v2: `catalogVersion: 2`, campo `retroAchievements` opcional em `HackEntry` (ver schema acima)
- [ ] `CatalogFetcher` / `MergedCatalogRepository`: handle `catalogVersion` migration (v1 → v2: default `supported=false`)
- [ ] Store UI: badge RA em hacks com `retroAchievements.supported == true`
- [ ] i18n completo: todas strings chrome pt-BR/en/es (Wally)
- [ ] Third-party license notices: adicionar rcheevos MIT license em `licenses/` + `About` screen / `Licenses` menu item
- [ ] **Risk register updates** (ver abaixo)
- [ ] **Visual QA**: Chululu screenshots menu in-game leaderboards, Store badge RA, Achievements screens
- [ ] **Build verification**: `./gradlew :app:assembleRelease` (release build test), `./gradlew :app:testDebugUnitTest :app:connectedAndroidTest`

**Critério de aceite**: Menu in-game tem "Conquistas" → "Leaderboards" abre dialog com tabs/entries → Store mostra badge RA → catálogo v2 parse OK → release build assinado roda → licenças terceiros documentadas.

---

## Registro de Riscos (Adições RetroAchievements)

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **JNI crashes** (segfault em `read_memory` / `do_frame`) | Média | App crash nativo (tombstone) | Defensive null checks em `LibretroDroidMemoryJni`; `read_memory` retorna 0 se ponteiro null; testar exaustivamente unload/reload; sanitizers (ASan) em CI se possível |
| **Ponteiro memória inválido após unload/reload** | Média | Leitura lixo / crash | `rc_client_unload_game` em `onCleared()` ANTES de core destroy; `getMemoryData` retorna null se core não ready; documentar torn reads aceitáveis v1 |
| **Leituras rasgadas (torn reads) RDRAM** | Baixa | Conquista dispara 1 frame tarde / progresso inconsistente | Aceitável v1 (documentado). Mitigação futura: travar emulação durante `do_frame` (precisa fork LibretroDroid) |
| **Rate limits RA API** (não documentados publicamente) | Baixa | Requests falham 429 | `RaHttpClient` com retry exponencial + `RateLimiter` token bucket (conservador: 10 req/s). Cache agressivo (achievements lists, leaderboards) |
| **Comportamento offline** | Média | Features RA indisponíveis | Cache local (achievements, leaderboards, user info). UI mostra "Offline — dados cacheados". Login falha com mensagem clara. Queue actions (unlocks sync quando online) — stretch |
| **Segurança conta (hardcore off by default)** | — | — | Hardcore **default OFF**. UA validation com RAdmin antes de habilitar. Token criptografado. Sem telemetria. |
| **GPL-3.0 / MIT attribution** | — | Compliance legal | rcheevos MIT → adicionar `licenses/rcheevos-LICENSE` + entrada no menu "Licenças". LibretroDroid/cores GPL-3.0 já cobertos. |
| **Play Store policy (UGC-ish)** | Baixa | Rejeição / remoção | RA não é UGC gerado pelo app; é integração com serviço terceiros. Sem chat, sem upload de conteúdo. Leaderboards read-only. Baixo risco. Documentar no README. |
| **Catálogo v2 migration** | Baixa | Loja quebra para usuários antigos | `catalogVersion` integer; `MergedCatalogRepository` trata campo ausente como `false`. Testar upgrade v1→v2. |

---

## Checklist de Milestones (Fases B1–B5)

#### Fase B1: Foundation — Vendored LibretroDroid + rcheevos Native + JNI Bridge
- [ ] Módulo `:libretrodroid` (vendor 0.13.2 + 2 JNI passthroughs)
- [ ] `app/src/main/cpp/rcheevos/` (git subtree pinned to master tag)
- [ ] `CMakeLists.txt` + `ra_jni_bridge.c/h` + `RcheevosJni.kt` + `LibretroDroidMemoryJni.kt`
- [ ] `RaHttpClient` skeleton (server_call dispatcher)
- [ ] `InGameRaViewModel` mínimo: create/do_frame/destroy no lifecycle GameActivity
- [ ] **Build**: `./gradlew :app:assembleDebug :libretrodroid:assembleDebug` ✓
- [ ] **Runtime**: "RA frame tick" log a cada frame ✓

#### Fase B2: Auth + Session + Install-time Hash Resolution
- [ ] `RaCredentialStore` + `RaSessionManager` + `RaLoginFragment` (Settings)
- [ ] `RaHttpClient` completo (server_call + rapi standalone)
- [ ] `RaRepository` + `RaHashService` (install-time hash → gameId → metadata cache)
- [ ] Store UI: badge RA em hacks compatíveis
- [ ] Unit tests auth/hash
- [ ] **Build + Unit tests** ✓

#### Fase B3: Achievements Screens (Library)
- [ ] `AchievementsActivity` + `AchievementsViewModel` (grid jogos instalados com progresso)
- [ ] `AchievementDetailActivity` (abas categorias, badges, progresso, pull-to-refresh)
- [ ] i18n strings.xml (chrome apenas)
- [ ] Acessibilidade + Visual QA (Chululu)
- [ ] **Build + Instrumented tests** ✓

#### Fase B4: In-Game Overlay + Notifications + Indicators
- [ ] `InGameRaViewModel` event handlers completos
- [ ] `InGameAchievementOverlay` (toast custom animado + queue)
- [ ] System notification (channel, permission API 33+)
- [ ] Settings fragment RA (enabled, hardcore, notifications, indicators)
- [ ] Hardcore default OFF
- [ ] Visual QA (Chululu)
- [ ] **Build** ✓

#### Fase B5: Leaderboards (Menu Only) + Catalog v2 + Polish
- [ ] `LeaderboardDialog` (DialogFragment, tabs, entries, Coil badges)
- [ ] Menu in-game integração (categoria "Conquistas")
- [ ] Catálogo v2 + `retroAchievements` field + migration
- [ ] Store badge RA
- [ ] Third-party licenses (rcheevos MIT)
- [ ] i18n completo (Wally)
- [ ] Visual QA final (Chululu)
- [ ] **Release build test** ✓

---

## Referências Técnicas

- **rcheevos repo**: https://github.com/RetroAchievements/rcheevos (branch `develop`, releases em tag `master` ~12.x)
- **rcheevos docs**: `docs/` no repo (client API, rapi, event codes, memory callbacks)
- **LibretroDroid 0.13.2**: https://github.com/Swordfish90/LibretroDroid (tag 0.13.2)
- **RetroAchievements API**: https://retroachievements.org/API/ (rapi endpoints)
- **N64 RDRAM mapping**: https://n64brew.dev/wiki/RDRAM (0x80000000–0x807FFFFF base, 8MB com expansion)
- **RetroArch RA integration** (referência de implementação host): `retroarch/libretro-common/include/libretro.h` + `retroarch/retroachievements.c`

---

# Phase 5: Multi-Store Catalog & Hylian Modding Integration

## Goal
Add multi-store support to the Hack Store with two built-in stores: **Hylian Modding** (default) and **Zelda 64 Picks** (existing catalog.json). Catalogs must not mix in the view — a top-bar store selector switches between stores. Hylian Modding pulls rich hack data from hylianmodding.com with specific parsing rules for different download link types.

## Store Model

### StoreDefinition
```kotlin
data class StoreDefinition(
    val id: String,                    // "picks" | "hylianmodding"
    val displayName: String,           // "Zelda 64 Picks" | "Hylian Modding"
    val sources: List<CatalogSourceMeta>
)

data class CatalogSourceMeta(
    val id: String,                    // "main" | "picks" | "competition-2025-crossover" etc.
    val type: CatalogSourceType,       // PICKS | HYLIANMODDING
    val url: String,                   // Base URL for this source
    val displayName: String            // "Main Index" | "2025 Crossover" etc.
)

enum class CatalogSourceType { PICKS, HYLIANMODDING }
```

### Built-in Stores (StoreDefinitions.kt)

| Store | Type | Sources |
|-------|------|---------|
| **Zelda 64 Picks** | PICKS | Single source: `DEFAULT_CATALOG_URL` (https://raw.githubusercontent.com/zonaro/zelda64player/main/catalog/catalog.json), `storeName` from catalog.json (default "Zelda 64 Picks") |
| **Hylian Modding** | HYLIANMODDING | 5 sources: main index + 4 competition indexes (see endpoints below) |

### Custom Catalog URLs (CatalogUrlStore)
- Backward compatible: custom URLs added via Settings merge into the **PICKS** store (single merged catalog for PICKS).
- No new store created; just additional source in PICKS store.

---

## Hylian Modding Endpoints (Verified)

| Source | Endpoint | Notes |
|--------|----------|-------|
| Main Index | `GET https://hylianmodding.com/mods/index.json` | Returns `{"mods": [slugs]}` |
| Per-Mod | `GET https://hylianmodding.com/mods/{slug}/mod.json` | Full mod metadata |
| Competition Index | `GET https://hylianmodding.com/competitions/{slug}/index.json` | Returns `{"mods": [slugs]}` |
| Competition Per-Mod | `GET https://hylianmodding.com/competitions/{slug}/{mod}/mod.json` | Full mod metadata |

**Corrected Competition Slugs** (verified):
- `2025-crossover`
- `2024-horror`
- `2023-escape-room`
- `hm-jam-1`

**Relative URLs** in mod.json resolve against `https://hylianmodding.com`.

---

## mod.json Schema (All Optional Except id/name)

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
- **NO video field exists** — be tolerant if added later (parse as optional `videos?: List<String>`).
- `screenshots` may contain empty strings — filter them.
- `download_link` determines `DownloadTarget` type (see below).

---

## Parsing Architecture

### CatalogParser Interface
```kotlin
interface CatalogParser {
    val sourceType: CatalogSourceType
    suspend fun parseIndex(indexJson: String, baseUrl: String): List<String>  // returns slugs
    suspend fun parseMod(modJson: String, baseUrl: String, sourceCatalogId: String): HackEntry
}
```

### PicksCatalogParser (Existing Format + storeName)
- Parses existing `HackCatalog` format (HackEntry, BaseRomRef, PatchRef, Checksums).
- **New**: reads top-level `storeName` from catalog.json (default "Zelda 64 Picks").
- Stamps `storeId = "picks"` on all produced `HackEntry`.
- IDs remain bare slugs (no prefix).

### HylianModdingParser
- `parseIndex`: extracts slugs from `{"mods": [...]}`.
- `parseMod`: tolerant parsing (all fields optional except id/name), resolves relative URLs against baseUrl, maps `supported_games` → generic OoT/MM `BaseRomRef` with **empty checksums** (matched at patch time via BPS source CRC).
- Builds `DownloadTarget` from `download_link` (see sealed hierarchy below).
- **Namespaces HM ids** with `hm_` prefix to avoid collision with PICKS bare-slug ids (e.g., `hm_ocarina-of-time-dx`).

---

## HackEntry Extensions

Add to `data/model/HackEntry.kt`:

```kotlin
data class HackEntry(
    // ... existing fields ...
    val storeId: String,                    // "picks" | "hylianmodding"
    val sourceCatalogId: String,            // "main" | "picks" | "competition-2025-crossover"
    val screenshots: List<String> = emptyList(),      // absolute URLs
    val videos: List<String> = emptyList(),           // tolerant/empty (HM has no field)
    val completionStatus: String? = null,
    val supportedGames: List<String>? = null,         // ["OoT", "MM"]
    val lastUpdated: String? = null,                  // ISO8601
    val changelog: List<ChangelogEntry> = emptyList(),
    val downloadTarget: DownloadTarget? = null,       // NEW: replaces patch for HM
    // Backward compat:
    val patch: PatchRef? = null                       // PICKS only
)

data class ChangelogEntry(val date: String, val content: String)
```

---

## DownloadTarget Sealed Hierarchy

```kotlin
sealed interface DownloadTarget {
    data class DirectPatch(val patchRef: PatchRef) : DownloadTarget
    data class GitHubRelease(val repoUrl: String) : DownloadTarget   // e.g., "https://github.com/user/repo/releases"
    data class ExternalLink(val url: String) : DownloadTarget        // HTML page, .7z/.rar/.ppf, or GitHub resolution failed
}
```

**Resolution Logic (at parse time):**
- `download_link` ends with `.bps`, `.ips`, `.xdelta`, `.zip` (case-insensitive) → `DirectPatch`
- `download_link` matches `github.com/*/releases` → `GitHubRelease`
- Otherwise (HTML, .7z, .rar, .ppf, unknown) → `ExternalLink`

---

## GitHubPatchResolver.kt

```kotlin
object GitHubPatchResolver {
    suspend fun resolve(repoUrl: String): Result<String>  // returns direct asset URL or failure
}
```

- Uses `api.github.com/repos/{owner}/{repo}/releases` (public, no auth needed for public repos).
- Finds asset matching `*.bps`, `*.ips`, `*.xdelta`, `*.zip` (prefer assets under `dist/` if present).
- Returns direct download URL on success; on failure (no matching asset, API error, rate limit) → returns failure, UI falls back to `ExternalLink` (opens browser).
- Runs **at download time** (not parse time); shows "Resolving…" state in UI.
- Handles pagination (per_page=100), sorts by `published_at` desc (latest release first).

---

## Browser Fallback

- `DownloadTarget.ExternalLink` → `Intent.ACTION_VIEW` with the URL.
- Covers: HTML mod pages, unsupported archives (.7z/.rar/.ppf), GitHub resolution failures.
- User sees "Open in browser" button in detail dialog.

---

## UI Changes

### StoreActivity
- **Top-bar store selector**: Segmented control / spinner with store display names.
- Persists last-selected store in SharedPreferences (`pref_selected_store_id`).
- Loads **only the selected store's catalog** (no mixing).
- Switch styling: `SwitchSidePanel` or custom segmented control matching Switch tokens.

### HackDetailDialog (Richer)
- Cover image (Coil)
- Screenshots gallery (horizontal pager, Coil)
- Title, authors, game badge (OoT/MM), completion status badge
- Scrollable description
- Expandable changelog (date + content)
- Video row (if `videos` non-empty) — placeholder for future HM video field
- Download button driven by `downloadTarget`:
  - `DirectPatch` → normal download flow
  - `GitHubRelease` → "Resolving…" → direct download or fallback to browser
  - `ExternalLink` → "Open in Browser"

---

## Persistence & Library Integration

### MergedCatalogRepository
- Stores **all stores' hacks** tagged by `storeId` and `sourceCatalogId`.
- `CatalogMerger` preserves `storeId` and `sourceCatalogId` (no longer discards source).
- Merged catalog persisted as `merged_catalog.json` (includes all stores).

### Store Filtering
- `StoreActivity` / `StoreViewModel` filters merged catalog by selected `storeId`.
- No cross-store mixing in UI.

### CatalogBackedLibrarySource
- Copies `storeId` into `HackLibraryEntry` (add field `storeId: String`).
- Library tiles retain store origin for potential future filtering.

---

## Catalog.json Update

Add top-level `storeName` to both catalog files:

**catalog/catalog.json** and **docs/catalog.json**:
```json
{
  "catalogVersion": 2,
  "storeName": "Zelda 64 Picks",
  "hacks": [...]
}
```

---

## Strings (pt-BR / en / es)

| Key | pt-BR | en | es |
|-----|-------|----|----|
| `store_selector_hylian` | Hylian Modding | Hylian Modding | Hylian Modding |
| `store_selector_picks` | Zelda 64 Picks | Zelda 64 Picks | Zelda 64 Picks |
| `detail_screenshots` | Capturas de tela | Screenshots | Capturas |
| `detail_videos` | Vídeos | Videos | Vídeos |
| `detail_open_browser` | Abrir no navegador | Open in browser | Abrir en navegador |
| `detail_resolving` | Resolvendo… | Resolving… | Resolviendo… |
| `detail_supported_game` | Jogo base | Base game | Juego base |
| `detail_completion` | Status | Status | Estado |
| `detail_changelog` | Changelog | Changelog | Registro de cambios |
| `detail_external_notice` | Este mod não tem download direto. Será aberto no navegador. | This mod has no direct download. Will open in browser. | Este mod no tiene descarga directa. Se abrirá en el navegador. |
| `store_source_error` | Falha ao carregar catálogo da loja | Failed to load store catalog | Error al cargar catálogo de la tienda |

---

## Limitations & Known Gaps

1. **No HM video field** — `videos` list always empty unless HM adds it later (parser tolerant).
2. **Unsupported archives** (.7z, .rar, .ppf) → browser fallback (no extraction).
3. **HM baseRom generic** — `supported_games` maps to generic OoT/MM `BaseRomRef` with **empty checksums**; actual match happens at patch time via BPS source CRC (existing `PatcherFacade` logic).
4. **GitHub API rate limits** — unauthenticated: 60 req/hr per IP. Mitigation: cache resolved URLs per session; show browser fallback on 403/429.
5. **Competition mods without patches** — many competition entries are showcase-only; `ExternalLink` handles gracefully.
6. **No auto-update for HM catalog** — user must pull-to-refresh in Store; no background sync (Rule 15: no telemetry/background network).

---

## Implementation Milestones (Phase 5)

### 5.1: Store Model + Parsers (Week 1)
- [ ] `StoreDefinition`, `CatalogSourceMeta`, `CatalogSourceType`, `CatalogParser` interface
- [ ] `StoreDefinitions.kt` with built-in stores
- [ ] `PicksCatalogParser` (refactor existing + `storeName` + `storeId` stamping)
- [ ] `HylianModdingParser` (index + mod parsing, URL resolution, `hm_` ID prefix)
- [ ] `DownloadTarget` sealed hierarchy + `GitHubPatchResolver`
- [ ] Unit tests: parser round-trips, HM mod.json fixtures, GitHub resolver mock

### 5.2: HackEntry Extensions + Persistence (Week 1–2)
- [ ] Extend `HackEntry` with new fields
- [ ] Update `CatalogMerger` to preserve `storeId`/`sourceCatalogId`
- [ ] Update `MergedCatalogRepository` schema (merged_catalog.json includes store tags)
- [ ] Add `storeId` to `HackLibraryEntry`
- [ ] Migration: existing merged catalog → add `storeId="picks"` to all entries

### 5.3: Store UI + Detail Dialog (Week 2)
- [ ] StoreActivity top-bar store selector (Switch-styled, persists selection)
- [ ] StoreViewModel loads single store's hacks from merged catalog
- [ ] HackDetailDialog redesign: cover, screenshots gallery, badges, description, changelog, video row, download button per `DownloadTarget`
- [ ] GitHubPatchResolver integration: "Resolving…" state, success→download, fail→browser
- [ ] ExternalLink → "Open in Browser" button
- [ ] Visual QA: Chululu screenshots (store selector, detail dialog per target type)

### 5.4: Catalog.json Update + i18n + Polish (Week 2–3)
- [ ] Add `storeName` to `catalog/catalog.json` and `docs/catalog.json`
- [ ] Bump `catalogVersion` to 3 (or 2 if not already bumped for RA)
- [ ] Strings.xml (pt-BR/en/es) for all new keys
- [ ] Wally: translate strings, update README with multi-store docs
- [ ] Build verification: `./gradlew :app:assembleDebug :app:testDebugUnitTest`

---

## Risk Register (Phase 5 Additions)

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **HM site structure changes** (index/mod.json schema) | Média | Store quebra | Parser tolerante (campos opcionais); versionamento de parser; monitorar HM changelog |
| **GitHub API rate limit / auth changes** | Baixa | Resolução falha → browser fallback | Cache resolved URLs; fallback gracioso; sem auth para repos públicos |
| **IDs collision (PICKS vs HM)** | Baixa | Dados errados no merge | HM ids prefixados `hm_`; PICKS ids inalterados; testes de merge |
| **Catálogo v3 migration** | Baixa | Loja quebra para usuários antigos | `catalogVersion` integer; `MergedCatalogRepository` trata campos ausentes; testar upgrade v1/v2→v3 |
| **HM competition slugs mudam** | Baixa | Fontes de competição 404 | Slugs hardcoded em `StoreDefinitions.kt`; documentar; update via app release |
| **DownloadTarget resolution no download time** | Média | UX "Resolving…" pode demorar | Timeout 10s; cancelável; fallback imediato para browser |
| **Switch UI compliance do seletor + dialog** | Média | Inconsistência visual | Chululu QA obrigatório antes de merge; tokens Switch aplicados |

---

## Referências Técnicas (Adicionais Phase 5)

- **Hylian Modding**: https://hylianmodding.com (mods index, competition indexes)
- **GitHub Releases API**: https://docs.github.com/en/rest/releases/releases
- **BPS Spec**: https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md (existing)
- **N64 ROM Header**: https://n64brew.dev/wiki/ROM_Header (existing)

(End of file)

---

# UI Revamp — Nintendo Switch Style (2026-08)

## Goal
Complete visual overhaul replacing Material 3 Expressive with a custom native implementation of the Nintendo Switch HOME menu aesthetic (NS_Launcher / FLauncher reference). All screens follow Switch design tokens, focus system, and component inventory. RadialGamePad touch-control LAYOUT remains frozen (Rule 14); only chrome restyled.

## User Decisions (Final)
1. **Library Home** = ONE main horizontal row of game cards (vanilla first, then hacks) + circular "Todos os Jogos" card at end opening fullscreen grid.
2. **Fullscreen grid ("Todos os Jogos")** = EVERYTHING together: vanilla games + store hacks, with search/filter.
3. **Dock (fixed, circular buttons)**: Loja (Store), RetroAchievements, Galeria (Gallery), Teste de Controle (Gamepad Tester), Configurações (Settings). Fixed set, no configurable slots. **Galeria added 2026-08** for the screen-capture / recording gallery feature (see "Captura de Tela, Gravação e Galeria" section).
4. **Settings** = side panel (right slide-in, NS Launcher "Options" style) with QUICK shortcuts (theme toggle dark/light, RA profile status, link to full settings) + the existing full SettingsActivity remains as a separate Switch-styled fullscreen screen.
5. **Status bar** = NONE. Clean screen (no clock/wifi).
6. **Themes** = Dark (`#2D2D2D` family) + Light (`#F0F0F0` family), runtime switchable from side panel.
7. **Splash** = Zelda-themed GOLD/GREEN palette with same structural layout as NS Launcher splash (flanking iconic shapes + two-line logo "Zelda 64" / "PLAYER"), designed by Dolfi. No Nintendo trademarks (no Joy-Con shapes, no Nintendo logos).
8. **Navigation SFX** = YES. Must be free/generated sounds (CC0 or synthesized) — NEVER extract from the NS Launcher APK (copyright). Stored in `res/raw/`.
9. **In-game** = RESTYLE EVERYTHING: pause menu, achievement overlay, leaderboard dialog, ocarina HUD all follow Switch style. The RadialGamePad touch-control LAYOUT itself remains FROZEN (Rule 14 unchanged — it governs control placement, not theme).
10. **Material 3 Expressive standard** = FULLY REPLACED by the Nintendo Switch UI standard. No M3 expressive shape/motion/typography requirements remain. (Whether the `com.google.android.material` dependency stays as technical base is Bruce's call during implementation — rules must not mandate M3 styling anymore.)

## Design Tokens
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

## Screen-by-Screen Mapping
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

## Component Inventory to Build (Native Kotlin, Hand-Styled)
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

## Implementation Milestones (Mirroring Bruce.md Order)
1. **Theme tokens + ThemeManager + base styles** — Define colors in `colors.xml` (CSS-variable-style names), create `ThemeManager` (runtime dark/light switch, persists to SharedPreferences), base theme in `themes.xml` (parent `Theme.Material3.DayNight.NoActionBar` — technical base only, no expressive styles).
2. **Focus system + SwitchFocusBorder + SfxManager** — `SwitchFocusBorder` drawable, focus handling logic (D-pad/click drives cyan border + label above card), `SfxManager` (SoundPool, 5 SFX in `res/raw/`), SFX toggle in settings.
3. **Library Home rebuild** — `SwitchHomeRow` (horizontal RecyclerView), `SwitchGameCard` (square, cover, focus label, dimming), `SwitchAllGamesCard` (circular, charcoal, cyan grid icon), `SwitchDock` (4 circular buttons), `SwitchFooterHints` (TV/gamepad + "(i) Sobre" / "+ Opções"). Vanilla games first, then store hacks.
4. **Todos os Jogos grid** — `SwitchGridScreen` (fullscreen): header icon+title "Todos os Jogos" 20sp bold + separator, smaller square cards (~170dp), search/filter bar, ghosted placeholders. All entries together (vanilla + hacks + seeds).
5. **Side panel + Settings restyle** — `SwitchSidePanel` (right slide-in, ~50% width, sharp edges). Quick settings: theme toggle, RA profile status, link to full Settings. Full SettingsActivity restyled entirely (SwitchGridScreen or fullscreen SwitchSidePanel).
6. **screens restyle screens restyle** — Store: Switch cards in grid, detail → SwitchDialog. .
7. **In-game menu + overlays restyle** — Pause menu → SwitchDialog, leaderboards → SwitchDialog, achievement unlock overlay → custom Switch-style toast, ocarina HUD → Switch-style overlay. **RadialGamePad touch layout FROZEN (Rule 14)** — only chrome restyled.
8. **Splash** — `SplashActivity` or splash theme: Zelda gold/green palette (Dolfi original art), same structural layout as NS Launcher splash (flanking iconic shapes + two-line logo "Zelda 64" / "PLAYER"). No Nintendo IP.
9. **SFX integration + polish** — Wire SFX to all focus/select/back/panel actions, volume respect, mute toggle, cross-screen consistency, visual QA (Chululu).

## Risk Register (UI Revamp Additions)
| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Licensing of sounds/art** | Medium | Legal/compliance | All SFX CC0/generated only; Dolfi assets original; NEVER extract from NS_Launcher APK (copyright). Document in AGENTS.md. |
| **Focus handling complexity on touch + D-pad** | High | UX regression | Implement unified focus manager; test on phone, tablet, TV (Android TV emulator); Chululu QA on all form factors. |
| **Regression risk vs frozen gamepad** | Medium | Emulation breakage | Gamepad package untouched; only in-game menu chrome/overlays restyled. Integration tests for GameActivity launch + menu open/close. |
| **Light/dark parity gaps** | Medium | Visual inconsistency | ThemeManager applies all tokens; Chululu validates both modes per screen; automated screenshot diff in CI (stretch). |
| **Performance on low-end (RecyclerView + animations)** | Low | Frame drops | Flat view hierarchy; `RecyclerView` with `DiffUtil`; avoid overdraw; profile with Perfetto. |
| **M3 Expressive remnants in codebase** | Low | Visual inconsistency | Grep for `Expressive`, `Material3.Expressive`, `shapeAppearanceCorner*`, `MotionSpec` — remove/replace. |

## Superseded Notice
**Material 3 Expressive requirements are superseded by this section.** All references to M3 Expressive in this document (theme parent, expressive shapes, motion, typography, component sizing, action emphasis) are **no longer applicable**. The `com.google.android.material:material:1.14.0` dependency may remain as a technical base (for `MaterialButton`, `MaterialCardView`, `TabLayout`, etc.) but MUST NOT be used for expressive styling. All visual standards now derive from the Nintendo Switch UI tokens and components defined above.

---

# Captura de Tela, Gravação e Galeria (2026-08)

## Visão Geral

Adicionar ao emulador a capacidade de **capturar tela** e **gravar a tela** durante as gameplays, persistindo imagens (PNG) e vídeos (MP4) em uma **nova tela de Galeria** estilo Nintendo Switch, onde o usuário pode **visualizar, compartilhar e excluir** cada item.

Regras de comportamento (definidas pelo usuário):
1. **Captura de tela**: sempre gera **2 imagens** por captura — uma **COM** o overlay dos controles e outra **SEM** o overlay dos controles.
2. **Gravação de tela**: gera **1 vídeo**; pode ou não incluir o overlay dos controles conforme **toggle nas Configurações** (`pref_capture_include_overlay`, default `true` = incluir).
3. **Controles no menu do emulador** (pause menu in-game): itens para "Capturar tela" e "Iniciar/Parar gravação".
4. **Galeria na tela principal**: novo botão circular **Galeria** no `SwitchDock` (5º botão), abrindo a `GalleryActivity`.

### Decisões do Usuário (Finais)
1. Captura de tela sempre dupla (com/sem overlay). Sem escolha por captura — é automático.
2. Gravação respeita o toggle global de overlay (configurações). Não há escolha por gravação.
3. Galeria é tela dedicada (não dialog), estilo Switch, com compartilhar/excluir.
4. Sem upload automático / sem telemetria (Regra 15). Mídia fica apenas no dispositivo; compartilhamento é via `ACTION_SEND` do usuário.

---

## Arquitetura

### Pacote novo: `capture/` (lógica de captura/gravação)
```
br.com.redclaw.zelda64player
├── capture/
│   ├── CaptureManager.kt        # Orquestra screenshot (PixelCopy + compositing) e ponte com o serviço de gravação
│   ├── CaptureService.kt        # Foreground Service: MediaProjection + MediaRecorder (gravação)
│   ├── RecordingIndicatorView.kt# Indicador visual Switch-style (dot/ícone) de gravação ativa
│   └── CapturePreferences.kt    # Helper de leitura de pref_capture_include_overlay
├── gallery/
│   ├── GalleryRepository.kt     # Lista/exclui itens da galleryDir; scan de arquivos
│   ├── GalleryItem.kt           # Model: tipo (image/video), path, hackId, timestamp, withOverlay
│   ├── GalleryActivity.kt       # Tela Switch UI (grid de itens + ações)
│   ├── GalleryAdapter.kt        # RecyclerView adapter (reutiliza padrão SwitchGameCard/Grid)
│   └── GalleryViewModel.kt      # StateFlow: itens, loading, ação delete/share
└── (modificações em): views/GameActivity.kt, viewmodels/GameActivityViewModel.kt,
    ui/switchui/SwitchDock.kt (via LibraryActivity.setupDock), settings/ui/SettingsActivity.kt,
    repositories/Storage.kt, AndroidManifest.xml, res/xml/file_paths.xml
```

### Mecanismo de CAPTURA DE TELA
- **Sem overlay**: `PixelCopy.request(SurfaceView, Bitmap, OnPixelCopyFinishedListener, Handler)` (API 24+) captura a `Surface` da `GLRetroView` (o `retroviewContainer`). O overlay (RadialGamePad, HUDs) NÃO é capturado porque vive em Views separadas sobre a Surface.
- **Com overlay**: compor a imagem do jogo (acima) + desenhar as Views de overlay por cima num `Canvas` (`overlayView.draw(canvas)`), escaladas para o bitmap. Overlays candidatos: `binding.gamepadOverlay` (RadialGamePad), `OcarinaHudView`, `RaOverlayView`.
  - **RISCO**: se o `gamepadOverlay` (RadialGamePad) renderizar para uma `SurfaceView` própria, `draw()` não o captura. Mitigação: verificar em tempo de implementação (Bruce) se o RadialGamePad é `View` ou `SurfaceView`. Se for `SurfaceView`, a versão "com overlay" usa `PixelCopy.request(Window, ...)` (API 26+) ou um snapshot via `MediaProjection` pontual; para API 24–25, fallback documentado (captura "com overlay" pode ficar indisponível ou usar compositing parcial). Decisão de implementação registrada em KDoc.
- Ambas as imagens são salvas por captura (`screenshotFile(hackId, ts, withOverlay=true/false)`). Feedback ao usuário via toast Switch-style ("Captura salva").

### Mecanismo de GRAVAÇÃO DE TELA
- **Abordagem recomendada**: `MediaProjection` (via `MediaProjectionManager.createScreenCaptureIntent()` → consentimento do usuário) + `MediaRecorder` gravando a `VirtualDisplay` da janela do app, em um **Foreground Service** (`CaptureService`).
- **Toggle de overlay**: ao iniciar a gravação, se `pref_capture_include_overlay == false`, ocultar temporariamente as Views de overlay (`gamepadOverlay`, `OcarinaHudView`, `RaOverlayView`) com `visibility = INVISIBLE` durante a gravação e restaurar (`VISIBLE`) ao parar. `INVISIBLE` mantém o layout/touch mas não é desenhado → não aparece na gravação do `MediaProjection`. Isso **não viola a Regra 14** (que governa posicionamento/modos do gamepad, não visibilidade temporária para captura).
  - **Tradeoff UX**: durante gravação "sem overlay" o usuário não vê os controles na tela (mas continua controlando). Aceitável conforme requisito; exibir um hint Switch-style pequeno ("Controles ocultos na gravação").
- **Permissões**: `FOREGROUND_SERVICE`; `FOREGROUND_SERVICE_MEDIA_PROJECTION` (API 29+); `POST_NOTIFICATIONS` (API 33+, solicitado no primeiro uso). O `MediaProjection` exige consentimento explícito do usuário a cada sessão.
- Vídeo salvo em `recordingFile(hackId, ts)` (MP4).

### STORAGE (reutiliza Storage.kt)
Novos métodos (keyed by hackId, fora do cacheDir para não ser evictado):
```kotlin
fun galleryDir(): File = File(storagePath, "gallery").apply { mkdirs() }
fun screenshotFile(hackId: String, timestamp: Long, withOverlay: Boolean): File =
    File(galleryDir(), "screenshot_${hackId}_${timestamp}_${if (withOverlay) "overlay" else "clean" }.png")
fun recordingFile(hackId: String, timestamp: Long): File =
    File(galleryDir(), "recording_${hackId}_${timestamp}.mp4")
```
Compartilhamento usa `FileProvider` (autoridade `br.com.redclaw.zelda64player.fileprovider`) com `res/xml/file_paths.xml` expondo `galleryDir`.

### MODELO + REPOSITORY
- `GalleryItem(tipo: MediaType, path: File, hackId: String?, timestamp: Long, withOverlay: Boolean)`.
- `GalleryRepository`: `suspend fun list(): List<GalleryItem>` (scan de `galleryDir()`, parse de nome), `fun delete(item): Boolean`, `fun shareUri(item): Uri` (via FileProvider).

### GALLERYACTIVITY (Switch UI)
- Reutiliza o padrão `SwitchGridScreen` (header "Galeria" + grid de cards). Cada card: thumbnail (Coil para imagens; `MediaMetadataRetriever` para frame de vídeo), badge de tipo (ícone vídeo/câmera), badge "overlay/clean" quando aplicável.
- Ações por item (via `SwitchDialog` ou menu de card): **Visualizar** (abre viewer/player nativo via `ACTION_VIEW` + FileProvider), **Compartilhar** (`ACTION_SEND` + URI FileProvider, tipo `image/png` ou `video/mp4`), **Excluir** (confirmação `SwitchDialog` → `GalleryRepository.delete` → refresh).
- Empty state: "Nenhuma captura ainda. Use o menu do emulador para capturar."
- Acessibilidade: contentDescription, TalkBack, touch targets ≥48dp.

### CONTROLES NO MENU DO EMULADOR
Em `GameActivityViewModel.buildMenuSections()`, adicionar nova `MenuSection` (ex: `R.string.menu_category_capture`) com:
- `MenuActionItem("screenshot", R.string.menu_screenshot, R.drawable.ic_screenshot)` → `captureManager.captureScreenshot(context, hackId)` (gera 2 arquivos).
- `MenuActionItem("record", R.string.menu_record_start / menu_record_stop, R.drawable.ic_record / ic_stop, isToggle=true, isActive={ isRecording })` → inicia/para `CaptureService` (respeitando `pref_capture_include_overlay`).
- Indicador de gravação ativa: `RecordingIndicatorView` (dot/ícone ciano/vermelho, estilo Switch) adicionado ao `binding.root` do `GameActivity` quando gravando.

### TOGGLE NAS CONFIGURAÇÕES
- Chave: `pref_capture_include_overlay` (Boolean, default `true`).
- Em `SettingsActivity` (estilo Switch): nova seção "Captura" com `SwitchPreference`-like row (ou linha Switch UI custom) "Incluir controles na gravação". Lida em `GameActivityViewModel` na hora de iniciar a gravação via `CapturePreferences`.

### DOCK (Tela Principal)
Em `LibraryActivity.setupDock()`, adicionar 5º `SwitchDock.DockItem`:
```kotlin
SwitchDock.DockItem(
    R.drawable.ic_gallery, R.string.dock_gallery, R.color.switch_accent_focus
) { startActivity(Intent(this, GalleryActivity::class.java)) }
```
Ícone `ic_gallery` (SVG) gerado por **Dolfi**.

### i18n (obrigatório)
Strings novas em `values/strings.xml` (pt-BR), `values-en/`, `values-es/`:
`menu_category_capture`, `menu_screenshot`, `menu_record_start`, `menu_record_stop`, `dock_gallery`, `gallery_title`, `gallery_empty`, `gallery_share`, `gallery_delete`, `gallery_delete_confirm`, `gallery_view`, `settings_capture_include_overlay`, `capture_saved`, `recording_started`, `recording_stopped`, `capture_overlay_hidden_hint`. Zero hardcoded.

### Mudanças no Manifest
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" /> <!-- API 29+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- API 33+ -->

<service android:name=".capture.CaptureService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection" />

<provider android:name="androidx.core.content.FileProvider"
    android:authorities="br.com.redclaw.zelda64player.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```
`res/xml/file_paths.xml`: `<files-path name="gallery" path="gallery/" />` (ou `external-files-path` conforme `storagePath`).

---

## Plano de Implementação em Fases (C1–C4)

### Fase C1: Foundation — Storage + Repository + Screenshot + Manifest (Semana 1)
- [ ] `Storage.kt`: `galleryDir()`, `screenshotFile()`, `recordingFile()`.
- [ ] `GalleryRepository.kt` + `GalleryItem.kt`.
- [ ] `CaptureManager.kt`: `captureScreenshot(context, hackId)` via PixelCopy (sem overlay) + compositing (com overlay); salva 2 arquivos; toast feedback.
- [ ] `AndroidManifest.xml`: permissões + `FileProvider` + `file_paths.xml`.
- [ ] Verificar se `gamepadOverlay` (RadialGamePad) é `View` ou `SurfaceView` (Bruce) → decidir compositing vs PixelCopy de Window.
- [ ] **Build**: `./gradlew :app:assembleDebug` ✓.

### Fase C2: Menu Controls + Recording Service (Semana 2)
- [ ] `CaptureService.kt`: MediaProjection + MediaRecorder (foreground service, consent flow).
- [ ] `CapturePreferences.kt` + leitura de `pref_capture_include_overlay`.
- [ ] `GameActivityViewModel`: novos itens no menu (screenshot + record toggle); ocultar/restaurar overlays na gravação; `RecordingIndicatorView`.
- [ ] `GameActivity.kt`: anexar `RecordingIndicatorView` ao `binding.root`.
- [ ] **Build + runtime**: capturar gera 2 PNG; gravar gera MP4; toggle oculta overlay. ✓

### Fase C3: Gallery Screen + Dock + Settings UI (Semana 3)
- [ ] `GalleryActivity.kt` + `GalleryAdapter.kt` + `GalleryViewModel.kt` (estilo SwitchGridScreen).
- [ ] Ações: visualizar (ACTION_VIEW), compartilhar (ACTION_SEND + FileProvider), excluir (SwitchDialog confirm → repo.delete).
- [ ] `LibraryActivity.setupDock()`: 5º DockItem "Galeria" → GalleryActivity.
- [ ] `SettingsActivity`: seção "Captura" com toggle `pref_capture_include_overlay`.
- [ ] **Visual QA** (Chululu): GalleryActivity (vazio/populado), menu in-game captura, indicador de gravação, dock com Galeria.

### Fase C4: i18n + Icons + Polish (Semana 4)
- [ ] **Dolfi**: ícones SVG `ic_gallery`, `ic_screenshot`, `ic_record`, `ic_stop` (consistentes com estilo Switch/dock).
- [ ] **Wally**: strings pt-BR/en/es completas; nota no README.
- [ ] **Chululu**: QA visual Switch (dark/light) das telas novas.
- [ ] **Build**: `./gradlew :app:assembleRelease :app:testDebugUnitTest`.

---

## Registro de Riscos (Captura/Gravação)

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **RadialGamePad é SurfaceView** (não capturável via `draw()`) | Média | "Com overlay" incompleto em API 24–25 | Verificar em C1; se SurfaceView, usar PixelCopy de Window (API 26+) ou MediaProjection snapshot; documentar fallback para 24–25 |
| **PixelCopy timing** (frame não pronto) | Baixa | Imagem preta/parcial | PixelCopy já entrega o frame mais recente da Surface; retry/timeout curto se necessário |
| **Permissões MediaProjection** (consentimento, foreground service API 29+) | Média | Gravação não inicia | Fluxo de consentimento explícito; tratar negação com toast; `foregroundServiceType=mediaProjection` |
| **Overlay oculto durante gravação "sem overlay"** (usuário não vê controles) | Alta (UX) | Confusão ao jogar | Hint Switch-style "Controles ocultos na gravação"; controles continuam funcionando (touch/INVISIBLE) |
| **Tamanho de arquivos de vídeo** | Média | Storage cheio | Sem upload (privacidade); opcional: limite de duração/config de qualidade futura; excluir pela Galeria |
| **GPL-3.0 / privacidade** | — | Compliance | Mídia 100% local; sem telemetria; FileProvider com `grantUriPermissions` temporário |
| **Regra 14 (gamepad frozen)** | — | — | Ocultar overlay via visibility NÃO altera layout/modos do RadialGamePad; ao parar, restaurado integralmente |

---

## Checklist de Milestones (Fases C1–C4)
- [ ] **C1**: Storage + GalleryRepository + CaptureManager (screenshot duplo) + Manifest/FileProvider + verificação RadialGamePad render target. **Build debug** ✓
- [ ] **C2**: CaptureService (MediaProjection+MediaRecorder) + menu (screenshot/record) + toggle overlay + indicador. **Runtime: 2 PNG + MP4** ✓
- [ ] **C3**: GalleryActivity (ver/compartilhar/excluir) + Dock 5º botão + Settings toggle. **Visual QA Chululu** ✓
- [ ] **C4**: Ícones Dolfi + i18n Wally + QA final + **Release build** ✓

## Delegação de Agentes (esta funcionalidade)
| Agente | Responsabilidade |
|--------|------------------|
| **Bruce** 🦈 | Kotlin/XML: CaptureManager, CaptureService, GalleryRepository/Activity/Adapter/ViewModel, menu items, Storage methods, manifest, settings toggle |
| **Dolfi** 🐬 | Ícones SVG: `ic_gallery`, `ic_screenshot`, `ic_record`, `ic_stop` (estilo Switch/dock) |
| **Wally** 🐋 | Strings pt-BR/en/es + nota README |
| **Chululu** 🐙 | QA visual Switch (dark/light) das telas novas |
| **Puffy** 🐡 | (consulta) APIs PixelCopy/MediaProjection/MediaRecorder API 24+ e permissões |
