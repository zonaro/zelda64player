# Plano do Projeto: Zelda 64 Player

## Visão Geral e Filosofia

**Zelda 64 Player** é um aplicativo Android nativo (Kotlin) derivado do projeto **Ludere** (fork de Swordfish90/Ludere, licenciado sob GPL-3.0), customizado como pacote `br.com.redclaw.ootdx` com nome "Zelda 64".

### Mudança Filosófica Central: **Nenhuma ROM Embutida**

O aplicativo **NUNCA** distribui, baixa ou inclui ROMs base (Ocarina of Time, Majora's Mask). O usuário **deve** fornecer suas próprias ROMs legalmente obtidas via tela de configurações. O app aplica patches BPS (fornecidos pela "Loja de Hacks") sobre a ROM base do usuário **em memória ou em arquivo de cache** antes de passar ao core Libretro.

**Legalidade**: Apenas patches (BPS) e metadados são distribuídos pelo app. ROMs base permanecem responsabilidade do usuário.

---

## Arquitetura Proposta

### Padrão Arquitetural
- **MVC adaptado para Android**: `View` (Activities/Fragments + ViewBinding), `ViewModel` (AndroidViewModel + LiveData/Flow), `Model` (Repositories + Use Cases)
- **Modularização por feature**: pacotes coesos por responsabilidade
- **Injeção de dependência manual** (sem Dagger/Hilt para manter leve; `Application` como Service Locator simples)

### Estrutura de Pacotes (Kotlin)

```
br.com.redclaw.zelda64player
├── data/                    # Modelos de dados imutáveis + catálogo local
│   ├── model/
│   │   ├── BaseRom.kt       # ROM base do usuário (checksums, path, metadados header)
│   │   ├── HackEntry.kt     # Entrada da loja (nome, descrição, URL patch, checksums requeridos)
│   │   ├── HackCatalog.kt   # Catálogo completo (lista de HackEntry + versão do catálogo)
│   │   └── PatchFile.kt     # Patch BPS baixado (bytes, checksum, path local)
│   └── local/
│       ├── BaseRomRepository.kt      # CRUD ROMs base do usuário (Room ou JSON simples)
│       ├── PatchRepository.kt        # Patches baixados (arquivos em cacheDir)
│       └── CatalogRepository.kt      # Catálogo remoto + cache local (ETag/Last-Modified)
├── patcher/                 # Módulo PURO Kotlin (sem dependências Android)
│   ├── bps/
│   │   ├── BpsPatch.kt            # Modelo do patch parseado (header, commands, checksums)
│   │   ├── BpsParser.kt           # Parser streaming (varint, commands)
│   │   ├── BpsApplier.kt          # Aplicador streaming (source/target copy/read)
│   │   ├── BpsValidator.kt        # Valida CRC32 source/target/patch
│   │   └── VarInt.kt              # Codificação/decodificação varint (LEB128-like)
│   ├── n64/
│   │   ├── RomNormalizer.kt       # Detecta byte order (.z64/.n64/.v64) → normaliza para z64 (BE)
│   │   ├── RomHeader.kt           # Parse do header N64 (game ID "CZLE"/"NSME", version, CRC1/2)
│   │   └── ChecksumCalculator.kt  # CRC32 (IEEE), MD5, SHA-1 da ROM normalizada
│   └── PatcherFacade.kt           # API única: applyPatch(baseRom: File, patch: File): Result<File>
├── store/                   # Loja de Hacks (UI + lógica de download)
│   ├── ui/
│   │   ├── StoreActivity.kt
│   │   ├── StoreViewModel.kt
│   │   ├── HackListAdapter.kt
│   │   └── HackDetailBottomSheet.kt
│   ├── DownloadManager.kt         # Download com retry, progress, validação checksum patch
│   └── CatalogFetcher.kt          # Fetch JSON remoto + cache ETag/If-None-Match
├── settings/                # Configurações do usuário
│   ├── ui/
│   │   ├── SettingsActivity.kt
│   │   ├── BaseRomImportFragment.kt    # Importar ROMs base (file picker + validação)
│   │   ├── BaseRomListFragment.kt      # Lista ROMs importadas com checksums
│   │   └── CatalogUrlFragment.kt       # URLs de catálogo customizadas (extensão)
│   └── SettingsViewModel.kt
├── repositories/            # Persistência (Storage adaptado do Ludere)
│   └── Storage.kt             # Paths por hackId: rom_<id>, sram_<id>, state_<id>
├── retroview/               # Emulação (adaptado do Ludere)
│   ├── RetroView.kt           # PONTO DE INTERCEPTAÇÃO: carrega ROM patcheada do cache
│   └── RetroViewUtils.kt      # Save/load state, fast forward, preservação
├── views/                   # UI principal (Library + Game)
│   ├── LibraryActivity.kt     # Grid de hacks instalados (instalados = patch baixado + base ROM válida)
│   ├── GameActivity.kt        # Activity de jogo (inalterada exceto gameId → hackId)
│   └── viewmodels/
│       └── GameActivityViewModel.kt
├── gamepad/                 # Controles touch (RadialGamePad) - INALTERADO
├── input/                   # Mapeamento controle físico - INALTERADO
├── utils/                   # Utilitários - INALTERADO (CorePrefs, RetroViewUtils)
└── di/                        # Service Locator simples
    └── AppContainer.kt
```

### Escopo de Migração: Limpeza do Ludere

O novo app NÃO é uma cópia integral do Ludere: é uma **extração enxuta**. Remove-se toda a maquinaria de empacotamento de ROMs (filosofia antiga do Ludere) e mantém-se apenas o núcleo de emulação + controles.

#### REMOVER do Ludere (não migrar)

| Item | Motivo |
|------|--------|
| `data/Games.kt` (catálogo hardcoded de 7 hacks + resolução `assets/roms/`) | Substituído pela Library dinâmica alimentada pela Loja de Hacks |
| Capas embutidas (`R.drawable.cover_*`, 7 drawables) | Capas passam a vir de `coverImageUrl` do catálogo + placeholders gerados |
| Empacotamento de ROM no APK (`assets/roms/`, `res/raw/rom`, `androidResources.noCompress` de z64/n64/v64/rom) | Filosofia nova: zero ROM no APK |
| `autogen/` (gerador batch de pacotes Ludere) | Ferramenta do modelo antigo de distribuição |
| `.github/workflows/autogen.yml` (build online de pacotes via payload URL) | Idem |
| `ludere.jks` + `signingConfigs.release` | Novo projeto = novo keystore próprio (gerar antes do primeiro release; debug usa o default) |
| Splits ABI + universalApk | Simplificar: um único build (AAB ou APK universal). Cores já vêm embutidos em `jniLibs` no build |

#### MANTER do Ludere (migrar adaptando apenas package/imports)

| Item | Adaptação necessária |
|------|---------------------|
| `retroview/RetroView.kt` + `RetroViewUtils.kt` | Trocar carga de `assets.open()` por arquivo patcheado do cache (`Storage.rom(hackId)`) |
| `views/GameActivity.kt` + `viewmodels/GameActivityViewModel.kt` | `gameId` → `hackId`; menu ganha atalho pra Loja/Settings (opcional) |
| `gamepad/` COMPLETO (ver seção abaixo) | **Congelado** — apenas rename de package |
| `input/` COMPLETO (`ControllerInput`, `InputMapper`) | **Congelado** — apenas rename de package |
| `utils/CorePrefs.kt` | Sem mudanças (seleção de core GLES3/GLES2/Parallel) |
| Ícone do app (`mipmap-*/ic_launcher*`, `drawable/ic_launcher_foreground.xml`, `values/ic_launcher_background.xml`) | **Reutilizado por enquanto** (decisão do usuário). Ícone próprio tema Zelda fica como tarefa futura da Dolfi |
| `res/values/config.xml` | Atualizar `config_id`/`config_name`; resto intacto |
| `repositories/Storage.kt` | Já é keyado por id — reutilizado como `hackId` |

### Layout de Controles Sob Medida (PRESERVAR VERBATIM)

O layout de botões atual do Ludere foi desenhado especificamente para jogos de Zelda 64 (OoT/MM) e é considerado **referência congelada**. Nenhuma posição, tamanho, tema ou comportamento pode ser alterado na migração — apenas o rename de package. Fonte da verdade: `gamepad/GamePadConfig.kt` (posições medidas de `ajustes-layout-controles/referencia.png`, 1684x774).

#### Características do layout (todas obrigatórias)

1. **Posicionamento fracionário**: cada pad é posicionado por frações (0..1) da largura/altura do overlay, medidos do `referencia.png` — resolution-independent.
2. **SIZE_SCALE 1.7x**: os diâmetros da referência são escalados 1.7x porque os botões originais são alvos de toque pequenos demais.
3. **Temas por botão**: A=azul, B=verde, C-buttons (C◀ C▶ C▲ C▼)=amarelo, Start=vermelho, Z/L/R neutros.
4. **Diagonais alinhadas sob medida**: R → C▶ → B formam uma diagonal reta e igualmente espaçada; C◀ → C▼ → A formam a outra. Os C-buttons ficam exatamente nos pontos médios calculados.
5. **ButtonStick dedicado** (canto inferior direito, entre Z e A): botão-stick arrastável com modos `Off / C-Right / C-Left / C-Down / A / B / Auto` (persistido em prefs).
6. **Modo Auto do ButtonStick**: segue automaticamente o último botão C pressionado em qualquer fonte de input (touch ou físico) via `trackCButtonPress()` unificado.
7. **Auto-Z**: double-tap no analógico alterna Z segurado (independente do stick); um Z real pressionado cancela o Z do double-tap; desligar Auto-Z no menu solta o Z imediatamente. Persistido em prefs (default ON).
8. **FloatingJoystick**: analógico N64 flutuante na região inferior-esquerda (60% da largura), com círculo-guia fixo (hint), alcance máximo limitado (`maxReachPx`) e sensibilidade ajustável.
9. **DoubleTapContainer**: wrapper intercept-only — detecta double-tap sem interferir no drag analógico.
10. **Controle físico espelhado**: o stick direito do gamepad físico espelha ao vivo o modo/sensibilidade do ButtonStick touch (mesma lambda de target); `ControllerInput.autoZEnabled` replica o Auto-Z no físico.
11. **Sensibilidade por stick**: dois sliders 0–200% (analógico N64 e ButtonStick), aplicados ao vivo e persistidos.
12. **Menu in-game**: Reset, Save State, Load State, Mute, Fast Forward, Button Stick (modo), Auto-Z (toggle), Sensibilidade.

#### Regras técnicas de integração (não regressar)

- Overlay usa `INVISIBLE` (nunca `GONE`) quando só há controle físico — `GONE` quebra o `OnGlobalLayoutListener` que cria ButtonStick/FloatingJoystick.
- Pads só são criados após o primeiro layout real do overlay (RadialGamePad cacheia posição/size no primeiro layout e não refresh em resize).
- Recuperação de GL context perdida (mupen64plus_next) é feita com `recreate()` completo da Activity, nunca `view.onDestroy()` direto (race com a render thread).
- `onDestroy` chama `super.onDestroy()` ANTES do dispose — a ordem garante o dispatch de ON_DESTROY que libera os ~90MB nativos do core.

> Qualquer ajuste futuro nesse layout exige aprovação explícita do usuário e atualização desta seção.

#### Controles Físicos: Regras e Mapeamentos (PRESERVAR VERBATIM)

O pacote `input/` (`ControllerInput.kt` + `InputMapper.kt`) também é **referência congelada**. Ele implementa uma cadeia de tradução em duas camadas pensada para o mupen64plus_next com a variável de core `mupen64plus-alt-map=True` (já presente no `config.xml`).

**Camada 1 — `InputMapper` (shift de face buttons estilo Xbox)**

O alt-map do core atribui os botões RetroPad assim: B→A(N64), Y→B(N64), R→C▶, L→C◀, A→C▼, X→C▲, L2→Z, R2→R, SELECT→L. Para o usuário ver um layout Xbox-style (A=A, B=B, Y=C▲, X=C▼), as face buttons são deslocadas antes de chegar ao core:

| Botão físico | Enviado ao core | Resultado N64 |
|--------------|-----------------|---------------|
| A | B | A |
| B | Y | B |
| Y | X | C▲ |
| X | A | C▼ |

Shoulders, triggers, DPAD, START e SELECT passam direto (sem shift).

**Camada 2 — `ControllerInput` (semântica Zelda sob medida)**

1. **Remap exclusivo do controle físico** (`PHYSICAL_C_BUTTON_REMAP`, diferente do layout touch!): LB→Y (vira C▲), Y→R1 (vira C▶), RB→L1 (vira C◀). O toque na tela nunca passa por esse mapa.
2. **L3 = Z físico** enquanto Auto-Z estiver ativo: segura o Z apenas enquanto o próprio L3 estiver pressionado (comportamento hold, diferente do double-tap-toggle do stick touch). Com Auto-Z off, L3 é ignorado.
3. **R3 = apertar o alvo do ButtonStick**: pressiona/solta o botão-alvo atual do modo do ButtonStick touch (mesma lambda `buttonStickTargetKeyCode`), com tracking de `r3HeldKeyCode` pra soltar o botão certo mesmo se o alvo do modo Auto mudar enquanto segurado. Com ButtonStick em Off, R3 fica sem função.
4. **Stick direito espelha o ButtonStick touch**: com modo ativo, o stick direito dirige o analógico N64 (`MOTION_SOURCE_ANALOG_LEFT`) escalado pela sensibilidade compartilhada e **somado** ao stick esquerdo numa única chamada (coerce -1..1) — segurar o botão é trabalho do R3. Com modo Off, comportamento padrão: stick esquerdo→ANALOG_LEFT, eixo bruto do direito (Z/RZ)→ANALOG_RIGHT (o alt-map do core converte em C-buttons digitais).
5. **Tracking unificado de C-buttons** (`TRACKED_C_BUTTONS`: R1/L1/X/L2 brutos): todo ACTION_DOWN dessas teclas dispara `onCButtonDown`, alimentando o modo Auto do ButtonStick a partir de qualquer fonte de input.
6. **Combos de menu**: START+SELECT simultâneos abrem o menu; botão Guide/Xbox (`KEYCODE_BUTTON_MODE`) também abre (e não é pipado ao core).
7. **Teclas excluídas** (`EXCLUDED_KEYS`): volume +/-, BACK e POWER nunca chegam ao core.
8. **DPAD** → `MOTION_SOURCE_DPAD`; port normalizado (`controllerNumber - 1`, mínimo 0).
9. Eventos só são processados após o primeiro frame renderizado (`frameRendered == true`).

> `input/` migra verbatim (apenas rename de package). Qualquer mudança nos mapeamentos exige aprovação explícita do usuário e atualização desta subseção.

### Diagrama de Fluxo: Launch Hack → Patched ROM → Core

```mermaid
flowchart TD
    A[User taps hack in Library] --> B{Base ROM imported?}
    B -->|No| C[Show Settings → Import Base ROM]
    B -->|Yes| D[Resolve BaseRom by checksum]
    D --> E{Checksum matches?}
    E -->|No| F[Error: wrong ROM version]
    E -->|Yes| G[Load BPS patch from cache]
    G --> H[PatcherFacade.applyPatch]
    H --> I{Validation OK?}
    I -->|No| J[Error: corrupt patch / mismatch]
    I -->|Yes| K[Write patched ROM to Storage.rom(hackId)]
    K --> L[RetroView loads patched ROM from cache]
    L --> M[LibretroDroid core starts]
    M --> N[Gameplay + SRAM/State per hackId]
```

---

## Especificação do Schema JSON da Loja de Hacks

### Arquivo: `catalog.json` (hospedado no GitHub, ex: `https://raw.githubusercontent.com/user/repo/main/catalog.json`)

```json
{
  "catalogVersion": 2,
  "lastUpdated": "2026-08-22T10:30:00Z",
  "hacks": [
    {
      "id": "ocarina_of_time_dx",
      "name": "Ocarina of Time DX",
      "description": "Quality-of-life improvements, restored content, and modern conveniences for OoT.",
      "author": "Kazemaru",
      "version": "2.5.1",
      "baseRom": {
        "name": "The Legend of Zelda: Ocarina of Time (NTSC-U 1.0)",
        "gameCode": "CZLE",
        "versionByte": 0,
        "checksums": {
          "crc32": "0xEC7011B7",
          "md5": "a1b2c3d4e5f678901234567890abcdef",
          "sha1": "fedcba09876543210fedcba09876543210fedcba"
        }
      },
      "patch": {
        "url": "https://github.com/user/repo/releases/download/v2.5.1/oot_dx_v2.5.1.bps",
        "filename": "oot_dx_v2.5.1.bps",
        "size": 1245184,
        "checksums": {
          "crc32": "0xA1B2C3D4",
          "md5": "112233445566778899aabbccddeeff00"
        }
      },
      "coverImageUrl": "https://raw.githubusercontent.com/user/repo/main/assets/covers/oot_dx.png",
      "tags": ["quality-of-life", "restoration", "enhancement"],
      "compatibleCores": ["mupen64plus_next_gles3", "mupen64plus_next_gles2", "parallel_n64"]
    },
    {
      "id": "majoras_mask_redux",
      "name": "Majora's Mask Redux",
      "description": "Definitive quality-of-life patch for Majora's Mask.",
      "author": "Rozlette",
      "version": "1.3.0",
      "baseRom": {
        "name": "The Legend of Zelda: Majora's Mask (NTSC-U 1.0)",
        "gameCode": "NSME",
        "versionByte": 0,
        "checksums": {
          "crc32": "0x9F2C3A1E",
          "md5": "abcdef1234567890abcdef1234567890",
          "sha1": "0987654321fedcba0987654321fedcba09876543"
        }
      },
      "patch": {
        "url": "https://github.com/user/repo/releases/download/v1.3.0/mm_redux_v1.3.0.zip",
        "filename": "mm_redux_v1.3.0.bps",
        "size": 987654,
        "checksums": {
          "crc32": "0x5E6F7A8B",
          "md5": "ffeeddccbbaa99887766554433221100"
        }
      },
      "coverImageUrl": "https://raw.githubusercontent.com/user/repo/main/assets/covers/mm_redux.png",
      "tags": ["quality-of-life", "bugfix"],
      "compatibleCores": ["mupen64plus_next_gles3", "parallel_n64"]
    }
  ]
}
```

### Campos Obrigatórios vs Opcionais

| Campo | Obrigatório | Descrição |
|-------|-------------|-----------|
| `catalogVersion` | Sim | Inteiro para migrações futuras |
| `lastUpdated` | Sim | ISO 8601 UTC para cache condicional |
| `hacks[].id` | Sim | Identificador único (slug, usado em paths de cache) |
| `hacks[].name` | Sim | Nome exibido |
| `hacks[].description` | Sim | Descrição longa |
| `hacks[].author` | Sim | Autor do hack |
| `hacks[].version` | Sim | Versão do patch (semver) |
| `hacks[].baseRom.name` | Sim | Nome legível da ROM base |
| `hacks[].baseRom.gameCode` | Sim | 4 chars do header N64 (ex: "CZLE", "NSME") |
| `hacks[].baseRom.versionByte` | Sim | Byte de versão do header (0 = v1.0) |
| `hacks[].baseRom.checksums.crc32` | Sim | CRC32 da ROM normalizada (z64 BE) — **mínimo obrigatório** |
| `hacks[].baseRom.checksums.md5` | Não | MD5 opcional para validação extra |
| `hacks[].baseRom.checksums.sha1` | Não | SHA-1 opcional para validação extra |
| `hacks[].patch.url` | Sim | URL direta para `.bps` ou `.zip` |
| `hacks[].patch.filename` | Sim | Nome do arquivo `.bps` dentro do zip (se zip) ou nome do arquivo |
| `hacks[].patch.size` | Sim | Tamanho em bytes (para progresso de download) |
| `hacks[].patch.checksums.crc32` | Sim | CRC32 do arquivo patch (integridade download) |
| `hacks[].patch.checksums.md5` | Não | MD5 opcional do patch |
| `hacks[].coverImageUrl` | Não | URL de imagem de capa (Dolfi pode gerar placeholders) |
| `hacks[].tags` | Não | Array de strings para filtros |
| `hacks[].compatibleCores` | Não | Lista de cores Libretro testadas |

### Extensibilidade: Múltiplos Catálogos

O app suporta **URLs de catálogo customizadas** nas configurações (array de strings). O `CatalogFetcher` itera sobre todas, mescla hacks por `id` (última vence), e expõe lista unificada. Útil para forks comunitários, hacks privados, etc.

---

## Estratégia de Checksums e Normalização de ROM N64

### Normalização de Byte Order (Obrigatória antes de hash)

| Formato | Extensão | Magic Bytes (offset 0) | Descrição | Conversão para z64 (BE) |
|---------|----------|------------------------|-----------|-------------------------|
| **z64** | `.z64` | `80 37 12 40` (0x80371240) | Big-endian nativo (MIPS) | **Já normalizado** |
| **v64** | `.v64` | `37 80 40 12` (0x37804012) | Byte-swapped (16-bit) | Swap cada `uint16` |
| **n64** | `.n64` | `40 12 37 80` (0x40123780) | Little-endian / word-swapped | Swap cada `uint32` (bswap32) |

**Algoritmo de detecção**:
```kotlin
fun detectAndNormalize(input: ByteArray): ByteArray {
    val magic = input[0].toInt() and 0xFF shl 24 |
                input[1].toInt() and 0xFF shl 16 |
                input[2].toInt() and 0xFF shl 8  |
                input[3].toInt() and 0xFF
    return when (magic) {
        0x80371240 -> input  // z64 (BE) - OK
        0x37804012 -> swap16(input)  // v64 → z64
        0x40123780 -> swap32(input)  // n64 → z64
        else -> throw InvalidRomException("Unrecognized N64 ROM format")
    }
}
```

### Identificação Interna via Header

Após normalizar para z64 (BE), ler offsets:
- **0x3B–0x3E (4 bytes)**: Game Code ASCII (ex: `CZLE` = OoT NTSC-U, `NSME` = MM NTSC-U)
- **0x3F (1 byte)**: Version Byte (0 = v1.0, 1 = v1.1, etc.)

**Validação em duas camadas**:
1. **Game Code + Version Byte** → identificação rápida sem hash completo
2. **CRC32/MD5/SHA-1 da ROM normalizada** → validação criptográfica definitiva

### Escolha de Algoritmo de Checksum

| Algoritmo | Velocidade | Colisão | Uso no Projeto |
|-----------|------------|---------|----------------|
| **CRC32 (IEEE 0xEDB88320)** | Muito rápido (streaming, ~500 MB/s) | 32-bit (aceitável para validação acidental) | **Primário** — usado pelo BPS spec e rompatcher.js; streaming nativo em `java.util.zip.CRC32` |
| **MD5** | Rápido | 128-bit (quebrado criptograficamente, OK para integridade) | **Secundário** — muitos hacks publicam MD5; opcional no catálogo |
| **SHA-1** | Moderado | 160-bit (quebrado para colisão intencional, OK para integridade) | **Terciário** — alguns bancos de dados (No-Intro, Redump) usam SHA-1 |

**Recomendação**: Armazenar **CRC32 obrigatório + MD5/SHA-1 opcionais** no catálogo. Validação em tempo de execução: CRC32 sempre; MD5/SHA-1 se presentes no catálogo.

---

## Plano de Implementação em Fases (Milestones)

### Fase 0: Fundação + Limpeza (Semana 1)
- [ ] Criar projeto Android Studio (package `br.com.redclaw.zelda64player`, minSdk 24, targetSdk 34, compileSdk 34)
- [ ] Migrar Gradle: Kotlin DSL, ViewBinding, Coroutines, LibretroDroid 0.6.2+, RadialGamePad 0.6.0
- [ ] **Migração seletiva** (ver "Escopo de Migração"): copiar APENAS `retroview/`, `views/GameActivity*`, `gamepad/`, `input/`, `utils/`, `config.xml`, `Storage.kt` — adaptando package/imports
- [ ] Copiar ícones do Ludere (`mipmap-*`, `ic_launcher_foreground.xml`, `ic_launcher_background.xml`) — reutilizados por enquanto; ícone próprio tema Zelda é tarefa futura (Dolfi)
- [ ] **NÃO migrar**: `Games.kt`, capas embutidas, empacotamento de ROM (assets/raw/noCompress), `autogen/`, workflow autogen, keystore Ludere, splits ABI
- [ ] Remover dependências mortas do build (RxAndroid só se ainda usado pelos controles; senão Flow)
- [ ] Configurar `config.xml` (id/name novos; core GLES3, variáveis, landscape, gamepad — intactos)
- [ ] **Entregável**: App compila, abre LibraryActivity vazia, GameActivity carrega core (sem ROM), layout de controles idêntico ao Ludere (validar contra referencia.png)

### Fase 1: ROMs Base + Patcher BPS (MVP Core) (Semanas 2–3)
- [ ] `data/model/BaseRom.kt`, `BaseRomRepository` (JSON em `filesDir/base_roms.json` + arquivos em `cacheDir/base_roms/`)
- [ ] `patcher/n64/RomNormalizer.kt` + `RomHeader.kt` + `ChecksumCalculator.kt` (testes unitários com fixtures)
- [ ] `patcher/bps/` — **Clean-room implementation** baseada na spec BPS (não copiar código GPL do rom_patcher_js/UniPatcher)
  - `VarInt.kt` (encode/decode)
  - `BpsParser.kt` (streaming, valida header "BPS1", parse commands até footer)
  - `BpsApplier.kt` (aplica SourceRead/TargetRead/SourceCopy/TargetCopy streaming)
  - `BpsValidator.kt` (CRC32 source/target/patch)
  - `PatcherFacade.kt` (API: `applyPatch(base: File, patch: File): Result<File>`)
- [ ] `Storage.kt` adaptado: `rom(hackId)` → cache do ROM patcheado
- [ ] `RetroView.kt` modificado: em vez de `context.assets.open(romAssetPath)`, lê `storage.rom(hackId)` (já patcheado)
- [ ] **Testes unitários**: fixtures BPS pequenas (conhecidas), normalização z64/v64/n64, CRC32 match
- [ ] **Entregável**: Importa OoT 1.0 z64 → aplica patch OoT DX local → joga funcional

### Fase 2: Loja de Hacks (Semanas 4–5)
- [ ] `data/model/HackEntry.kt`, `HackCatalog.kt`, `PatchFile.kt`
- [ ] `CatalogFetcher.kt` (OkHttp + ETag/If-None-Match + cache JSON em `cacheDir/catalog.json`)
- [ ] `DownloadManager.kt` (coroutines, progress notification, valida checksum patch após download)
- [ ] `PatchRepository.kt` (arquivos `.bps` em `cacheDir/patches/<hackId>.bps`)
- [ ] UI: `StoreActivity` (grid RecyclerView + Glide/Coil para capas), `StoreViewModel`, `HackDetailBottomSheet`
- [ ] Integração: LibraryActivity mostra apenas hacks "instalados" (patch baixado + base ROM válida)
- [ ] **Entregável**: Usuário abre Loja → vê catálogo → baixa patch → hack aparece na Library → joga

### Fase 3: Configurações + Polish (Semana 6)
- [ ] `SettingsActivity` com fragments: Importar ROM Base, Lista ROMs Base, URLs Catálogo Customizadas
- [ ] File picker (Storage Access Framework) para importar ROMs → validação imediata (normaliza + checksum + header)
- [ ] i18n: `strings.xml` (pt-BR default), `values-en/strings.xml`, `values-es/strings.xml`
- [ ] Ícones/capas: Dolfi gera placeholders SVG + PNG para hacks sem coverImageUrl (ícone do app: manter o do Ludere por enquanto; ícone próprio tema Zelda adiado)
- [ ] Acessibilidade: contentDescription, touch target sizes, TalkBack testado
- [ ] **Entregável**: App completo funcional, pronto para release

### Fase 4: Extras / Stretch Goals (Pós-MVP)
- [ ] Suporte a **IPS** (formato legado, simples, para hacks antigos)
- [ ] Atualização automática de catálogo (WorkManager periodic fetch)
- [ ] Backup/restore de saves (SRAM + states) via Google Drive / export ZIP
- [ ] Suporte a múltiplos saves por hack (slots)
- [ ] Telemetria opcional (opt-in, sem dados pessoais)

---

## Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **ROM versão errada** (usuário importa OoT 1.1 ou PAL) | Alta | Crash / patch falha / jogo quebrado | Validação estrita: gameCode + versionByte + CRC32. Mensagem clara: "Esta hack requer Ocarina of Time NTSC-U 1.0 (CRC32: 0xEC7011B7)" |
| **Patch BPS corrompido / incompleto** | Média | Falha silenciosa ou crash no core | Validação tripla: CRC32 do patch (download), CRC32 source (antes de aplicar), CRC32 target (após aplicar). Falha → delete patch + erro amigável |
| **Memória baixa** (OoT/MM = 32–64 MB descomprimidos; patcheados similar) | Média em low-end | OOM ao carregar ROM em bytes (`config_load_bytes=true`) | **Default `config_load_bytes=false`** → usa arquivo em cache (Storage.rom). Streaming patcher evita 2x RAM. Testar em dispositivos 2GB RAM. |
| **Licença GPL-3.0 do rom_patcher_js / UniPatcher** | — | Contaminação legal se copiar código | **Clean-room**: implementar só da spec BPS (documentos públicos: bps_spec.md, romhacking.net). Não ler código GPL. Documentar no AGENTS.md. |
| **Cores Libretro incompatíveis** (alguns hacks precisam GLES2, outros GLES3) | Baixa | Crash gráfico / performance ruim | Catálogo declara `compatibleCores`. App permite usuário trocar core nas configurações (CorePrefs existente). Default GLES3. |
| **Byte order detection falha** (ROMs homebrew, headers atípicos) | Baixa | ROM rejeitada incorretamente | Fallback: se magic não reconhecido, tentar heurística (tamanho múltiplo de 512KB, header válido em alguma ordem). Log detalhado para debug. |
| **Catálogo remoto indisponível / alterado** | Baixa | Loja vazia / hacks somem | Cache local persistente (último JSON válido). Modo offline funcional. ETag/If-None-Match economiza banda. |

---

## Decisões Técnicas com Justificativas

| Decisão | Justificativa |
|---------|---------------|
| **Kotlin nativo (não Flutter)** | Derivado direto de Ludere (Kotlin + LibretroDroid + RadialGamePad). Reescrita em Flutter descartaria anos de tuning de emulação, gamepad, ciclo de vida GL. Manter nativo = risco zero de regressão. |
| **BPS puro Kotlin clean-room** | rom_patcher_js (GPL-3.0) e UniPatcher (GPL-3.0) não podem ser copiados. Spec BPS é pública e simples. Implementação própria evita licença viral, permite otimizações (streaming, coroutines), e é ~500 linhas. |
| **Manter LibretroDroid + RadialGamePad** | Já funcionam, testados, performáticos. Trocar por outro frontend = retrabalho massivo. |
| **minSdk 21 → 24** | Android 5.0 (API 21) tem < 1% share. API 24 (Nougat) = 2016, > 95% share. Permite APIs modernas (Scoped Storage, melhor File API). |
| **targetSdk 34** | Requisito Play Store (agosto 2024+). |
| **Room vs JSON para BaseRomRepository** | JSON simples em `filesDir` suficiente (poucas ROMs base, ~2–4). Room adiciona boilerplate. Decidir na Fase 1. |
| **OkHttp vs Retrofit** | OkHttp direto (já dependência do LibretroDroid). Leve, sem reflection. |
| **Glide vs Coil** | Coil (Kotlin-first, coroutines, menor). Preferir Coil. |
| **Sem Dagger/Hilt** | Projeto pequeno, DI manual via `AppContainer` em `Application` é suficiente e mais legível. |

---

## i18n (pt-BR / en / es)

- **Default**: `values/strings.xml` = **pt-BR** (foco do usuário)
- `values-en/strings.xml` — Inglês
- `values-es/strings.xml` — Espanhol
- **Todas** strings de UI externalizadas. Zero hardcoded strings em código.
- Formatação de números/datas via `Locale` do dispositivo.
- RTL não necessário (pt-BR/en/es são LTR).

---

## Testes

### Unit Tests (JUnit 5 + KotlinTest / MockK)
- `patcher/n64/RomNormalizerTest.kt`: fixtures `.z64`, `.v64`, `.n64` → todos produzem mesmo bytes normalizados
- `patcher/n64/RomHeaderTest.kt`: parse gameCode "CZLE"/"NSME", versionByte
- `patcher/n64/ChecksumCalculatorTest.kt`: CRC32/MD5/SHA-1 de fixtures conhecidos
- `patcher/bps/VarIntTest.kt`: encode/decode round-trip (valores 0, 127, 128, 16383, 16384, max ULong)
- `patcher/bps/BpsParserTest.kt`: parse patch BPS válido → commands corretos
- `patcher/bps/BpsApplierTest.kt`: apply patch conhecido → output byte-identical ao esperado
- `patcher/bps/BpsValidatorTest.kt`: CRC32 source/target/patch match/fail
- `patcher/PatcherFacadeTest.kt`: integração completa base+patch→patched ROM

### Instrumented Tests (AndroidJUnitRunner)
- `BaseRomImportTest`: file picker → validação → persistência
- `CatalogFetcherTest`: fetch + ETag cache + merge múltiplos catálogos
- `DownloadManagerTest`: download + progress + checksum validation
- `RetroViewIntegrationTest`: launch hack → patched ROM loads → frame rendered

### Fixtures de Teste
- `test/fixtures/roms/` — ROMs dummy pequenas (header válido, tamanhos 512KB–2MB) em z64/v64/n64
- `test/fixtures/patches/` — patches BPS mínimos (source=dummy, target=dummy+1 byte change)
- **Não comitar ROMs reais** — apenas fixtures sintéticos gerados em script de teste

---

## Licenças e Atribuições

| Componente | Licença | Ação Necessária |
|------------|---------|-----------------|
| Ludere (base) | GPL-3.0 | Manter LICENSE, headers de copyright, oferecer source code |
| LibretroDroid | GPL-3.0 | Mesmo acima |
| RadialGamePad | GPL-3.0 | Mesmo acima |
| Cores Libretro (mupen64plus_next, parallel_n64) | GPL-3.0 | Binários em `jniLibs/` — source disponível no buildbot.libretro.com |
| **BPS Patcher (nosso)** | **Apache-2.0 ou MIT** (escolher) | Clean-room → podemos licenciar permissivamente |
| App completo | **GPL-3.0** (derivado) | Deve ser GPL-3.0 por herança. Documentar em LICENSE e README. |

---

## Próximos Passos Imediatos

1. **Aprovação do plano** pelo usuário (este documento)
2. **Coral** cria `AGENTS.md` + `.agents/*.md` + `.gitignore` (esta tarefa)
3. **Lobby** delega para **Bruce** (Android/Kotlin) iniciar Fase 0
4. **Bruce** configura projeto, migra código base, valida build
5. **Bruce** + **Coral** (consultoria) implementam Fase 1 (patcher BPS)
6. **Fishie** (se necessário) para UI da Loja (mas Bruce faz Android nativo)
7. **Dolfi** gera ícones/capas placeholders
8. **Wally** finaliza README.md, traduz strings pt-BR/en/es
9. **Calamari** valida checksums de ROMs conhecidas (No-Intro/Redump)
10. **Puffy** pesquisa updates de LibretroDroid / cores novos