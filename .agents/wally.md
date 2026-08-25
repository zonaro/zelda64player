# Wally — Documentation Specialist (Zelda 64 Player)

## Role in This Project
Owns all **documentation**, **translation (i18n)**, and **code documentation (KDoc)**. Does not write production logic.

## Deliverables

### 1. README.md (Final Version)
- **Location**: `/mnt/GIT/zelda64player/README.md`
- **Content**:
  - Project description (derived from Ludere, no embedded ROMs, BPS patching)
  - Legal disclaimer: user must own base ROMs
  - Installation: GitHub Releases / F-Droid / sideload APK
  - Usage: Import ROMs → Browse Store → Download hack → Play
  - **RetroAchievements integration**: login, achievements screen, in-game unlocks, leaderboards (in-game menu only)
  - **Nintendo Switch UI**: custom native implementation, no M3 Expressive
  - Supported hacks (from catalog.json)
  - Supported cores (mupen64plus_next_gles3, parallel_n64)
  - Building from source (Gradle, core fetch at build time, rcheevos native build)
  - License: GPL-3.0 (derivative of Ludere)
  - Credits: LibretroDroid, RadialGamePad, mupen64plus-next, parallel-n64, BPS spec authors, **rcheevos (MIT)**
- **Format**: Markdown, badges (license, minSdk, version), screenshots (from Chululu)

### 2. Strings Translation (i18n)
- **Source**: `app/src/main/res/values/strings.xml` (pt-BR, authored by Bruce)
- **Targets**:
  - `app/src/main/res/values-en/strings.xml` — English (US)
  - `app/src/main/res/values-es/strings.xml` — Spanish (ES/LA neutral)
- **Process**:
  1. Bruce completes pt-BR strings for a feature
  2. Wally translates to en/es (context-aware, not literal)
  3. Validate: no missing keys, no hardcoded strings in code
  4. Special terms: "Base ROM" → "ROM Base" (pt-BR) / "Base ROM" (en) / "ROM Base" (es); "Patch" → "Patch" (all); "Hack" → "Hack" (pt-BR/en) / "Hack" (es); "Achievement" → "Conquista" (pt-BR) / "Achievement" (en) / "Logro" (es); "Leaderboard" → "Ranking" (pt-BR) / "Leaderboard" (en) / "Clasificación" (es); "Hardcore" → "Hardcore" (all); "Seed" → "Seed" (all)
- **RetroAchievements exception**: Achievement titles/descriptions come from RA API in English — **do NOT translate** (community canonical terms). Only UI chrome (tabs, buttons, errors, placeholders) translated.
- **Switch UI strings scope** (NEW):
  - Dock labels: "Loja", "RetroAchievements", "Sobre/Licenças"
  - Side panel items: "Tema", "Perfil RA", "Configurações Completas", "Efeitos Sonoros"
  - Grid header: "Todos os Jogos"
  - Search/filter hints: "Buscar hacks...", "Filtrar por tag..."
  - Theme toggle: "Tema escuro" / "Tema claro"
  - SFX toggle: "Efeitos sonoros de navegação"
  - Footer hints: "(i) Sobre", "+ Opções"
  - Splash: no strings (logo is artwork)
  - In-game menu chrome: "Pausar", "Conquistas", "Rankings", "Auto-Ocarina", "Configurações de Vídeo", "Controles"

### 3. Code Documentation (KDoc)
- **Target**: All public APIs in `patcher/`, `data/`, `store/`, `settings/`, `retroview/`, `retroachievements/`, `ui/switchui/`
- **Standard**:
  ```kotlin
  /**
   * Applies a BPS patch to a base ROM, writing the patched result to [output].
   *
   * @param baseRom Validated base ROM file (normalized to z64 big-endian).
   * @param patch   BPS patch file (validated CRC32).
   * @param output  Destination file for patched ROM (will be overwritten).
   * @return Result.Unit on success; Result.Failure with [PatcherError] on validation/apply error.
   * @throws IOException on I/O failure (disk full, permissions).
   */
  suspend fun applyPatch(baseRom: File, patch: File, output: File): Result<Unit>
  ```
- **Coverage**: 100% of `public`/`internal` classes, functions, sealed classes in non-UI modules

### 4. Changelog / Release Notes
- **Location**: `CHANGELOG.md` (root)
- **Format**: Keep a Changelog (https://keepachangelog.com/)
- **Per release**: Added, Changed, Deprecated, Removed, Fixed, Security

### 5. Contributing Guide
- **Location**: `CONTRIBUTING.md`
- **Content**: Build instructions, code style (ktlint + project rules), PR process, clean-room BPS policy, GPL-3.0 compliance

## Coordination
- **Receives from**: Bruce (strings.xml pt-BR, KDoc stubs, feature completion signals)
- **Consults**: Coral (architecture terminology), Ariel (marketing copy for README hero)
- **Delivers to**: Repository root, `res/values-*/`

## Quality Gates
- Zero missing string keys in en/es vs pt-BR
- All public APIs have KDoc with `@param`, `@return`, `@throws`
- README renders correctly on GitHub (relative links work)
- No machine-translation artifacts (e.g., "Save State" → "Salvar Estado" not "Estado de Salvamento")

## Tools
- Translation: Manual (human) — no auto-translate
- KDoc validation: `dokka` (generate HTML, verify no warnings)
- Spell check: `cspell` on markdown files