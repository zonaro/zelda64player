# Plano: Novos Cores Libretro para Zelda 64 Player

## Cabeçalho

**Data:** 2026-08-23  
**Atualizado em:** 2026-08-23 (implementação da decisão de 2 cores)  
**Status:** Planejamento — Fase B concluída, Fase A pendente  
**Documentos relacionados:** `plano.md`, `AGENTS.md`, `app/build.gradle.kts`

---

## Objetivo

Avaliar, selecionar e integrar cores Libretro atualizados para Nintendo 64 no Zelda 64 Player, substituindo o `parallel_n64` obsoleto (buildbot parado em 2020-11-10) por um build próprio com dynarec moderno, e preparar o terreno para futuros cores experimentais (gopher64), mantendo a estabilidade do `mupen64plus_next_gles3` como padrão. O documento registra decisões, riscos, fases de implementação e critérios de aceite testáveis.

---

## Estado Atual

### Cores em uso hoje (via `prepareCore` no `app/build.gradle.kts`)

| Core (buildbot) | Arquivo em `jniLibs` | ID interno (`CorePrefs`) | Status buildbot | Notas |
|-----------------|----------------------|--------------------------|-----------------|-------|
| `mupen64plus_next_gles3` | `libcore_mupen_gles3.so` | `mupen64plus_next_gles3` | **Ativo** (nightly) | Padrão (`config_core` no `config.xml`). GLES3 recomendado. |
| `parallel_n64` | `libcore_parallel.so` | `parallel_n64` | **Self-build** (rolling release `parallel-n64-latest`) | Build próprio via CI (NDK r27d, 4 ABIs). Buildbot nightly como fallback. |

> **Mudança implementada (2026-08-23):** variante `mupen64plus_next_gles2` **removida** do player (glitches em GPUs modernas, APK menor). Arquivo `jniLibs/*/libcore_mupen_gles2.so` deletado. `CorePrefs.CORE_LIBS` reduzido a 2 entradas.

### Fluxo de seleção de core (runtime)

1. `utils/CorePrefs.kt` mantém array `CORE_LIBS = ["libcore_mupen_gles3.so", "libcore_parallel.so"]` + índice persistido (`selectedCoreIndex`).
2. `LibraryActivity` expõe diálogo de seleção (reutilizado do Ludere).
3. `GameActivityViewModel` lê preferência e passa para `RetroView` via `GLRetroViewData.coreName`.
4. Catálogo JSON (`catalog.json`) declara `compatibleCores` por hack (ex.: `["mupen64plus_next_gles3", "parallel_n64"]`).

### Comportamento do `prepareCore` (atualizado)

- Para `parallel_n64`: tenta **primeiro** a URL do release próprio (`parallel-n64-latest` no GitHub Releases); se falhar (404/timeout), cai para **buildbot nightly**; se ambos falharem, pula o core com log claro (guarda `skip` por ABI).
- Para `mupen64plus_next_gles3`: continua usando buildbot nightly (já ativo e atualizado).
- Guarda `skip` por core+ABI para não re-tentar downloads falhos no mesmo build.

### Restrições do projeto (regras imutáveis)

- **Nenhum download de core em runtime** (regra 15 do `AGENTS.md`: rede só para catálogo, patch e core no build time).
- Pacote `gamepad/` **congelado** — invariantes de overlay (`INVISIBLE` não `GONE`), criação de pads após primeiro layout, recuperação GL via `recreate()`, `super.onDestroy()` antes de dispose.
- GPL-3.0 compliance: binários distribuídos exigem oferta de source correspondente.
- i18n obrigatório: pt-BR (default `values/`), `values-en/`, `values-es/`.

---

## Resultados da Pesquisa (Matriz de Candidatos)

| Core | Repositório | Licença | Atividade (ago/2026) | Buildbot Android | Esforço integração | Veredito / Recomendação |
|------|-------------|---------|----------------------|------------------|-------------------|-------------------------|
| **mupen64plus-next** (gles3) | `libretro/mupen64plus-libretro-nx` | GPL-2.0 | **Ativo** (push 2026-08-06) | **Sim** (nightly gles3) | Baixo (já integrado) | **Manter como padrão**. GLES3 nightly já entrega melhorias recentes (parallel RSP, angrylion RDP, framebuffer fixes). GLES2 **removido do player** (glitches em GPUs modernas). Vulkan indisponível no buildbot Android e inútil (LibretroDroid é GL-only). |
| **parallel-n64** | `libretro/parallel-n64` | GPL-2.0 | **Ativo** (pushes ago/2026) | **Obsoleto** (2020-11-10) | Médio (self-build NDK) | **Substituir por self-build**. Novos dynarec ARM/x86 (~10–15% speedup), parallel RSP updates, novas core options. Requer pipeline CI próprio. **IMPLEMENTADO** (rolling release `parallel-n64-latest`). |
| **mupen64plus** (old) | `libretro/mupen64plus-libretro` | GPL-3.0 | **Arquivado** (2019) | **Não** | — | **Rejeitado**. Dead end. |
| **gopher64** | `gopher64/gopher64` | GPL-3.0 | **Ativo** (Rust, sucessor spiritual do simple64) | **Incerto** (standalone só? RetroArch lista core libretro) | Alto (verificação + self-build) | **REJEITADO/CANCELADO** — decisão do usuário: manter apenas 2 cores. |
| **simple64** | `simple64/simple64` | GPL-3.0 | **Arquivado** (fev/2025) | **Não** | — | **Rejeitado**. Nunca teve core libretro. |
| **ares** | `ares-emulator/ares` | ISC | **Ativo** | **Não** (issue #1375 aberto, mantenedores dizem "gargantuan effort" por libco) | — | **Rejeitado por enquanto**. Sem core libretro. |

### Fatos verificados (Puffy + Calamari, ago/2026)

1. **LibretroDroid**: stable 0.13.2 (2026-03-14); 0.14.0 pre-release no JitPack. Mudanças desde 0.6.2: thread-safe core interaction (PR 126), texture-unbinding fix shader chain (PR 120), ambient/immersive mode configurável (PR 122), microphone support (0.13.0), shaders CUT2/CUT3 custom upscaling. **Sem breaking changes** no contrato `GLRetroView`/`GLRetroViewData`. Ludere ainda usa 0.6.2.
2. **mupen64plus-next**: variante GLES3 recomendada no Android; GLES2 tem glitches em dispositivos modernos. Vulkan não distribuído no buildbot Android.
3. **parallel-n64**: repo ativo mas artifact Android buildbot parado em 2020-11-10. Melhorias desde então: novo dynarec x86/ARM (~10–15% speedup), parallel RSP updates, novas core options. Obter requer self-compile via NDK.
4. **Self-build Android**: caminho padrão = `Makefile.libretro` com NDK clang (`make -f Makefile.libretro platform=android-arm64`) ou scripts `libretro-super` (`libretro-fetch.sh` + `libretro-build-android-mk.sh`). GitHub Actions cross-compile é padrão comprovado. Output `.so` pode ser anexado a nossos GitHub Releases e consumido pelo `prepareCore`.
5. **Outros frontends**: Lemuroid usa mesmo padrão build-time fetch; RetroArch empacota ou atualiza cores via canais de update do app; download runtime de core evitado por frontends FOSS devido a política Play e clareza agregação GPL.

---

## Decisões Recomendadas

### D1: Upgrade LibretroDroid 0.6.2 → 0.13.2 (stable)
**Racional:** Correções de thread-safety, texture unbinding, ambient mode, shaders CUT2/CUT3. Sem breaking changes no contrato GL. Mantém compatibilidade com `gamepad/` congelado.
**Checklist de regressão (obrigatório antes de merge):**
- Overlay usa `INVISIBLE` (nunca `GONE`) quando só controle físico
- Pads criados apenas após primeiro layout real do overlay
- Recuperação GL context perdida via `recreate()` completo da Activity (nunca `view.onDestroy()` direto)
- `onDestroy` chama `super.onDestroy()` **antes** do dispose
- `config_load_bytes=false` default mantido (streaming de arquivo)
- Nenhuma mudança no `config.xml` (core, variáveis, orientação, gamepad intactos)

**Status:** **PENDENTE** — não aplicado ainda (Fase A).

### D2: Manter `mupen64plus_next_gles3` como core padrão
**Racional:** Nightly buildbot já entrega melhorias contínuas (parallel RSP dynarec, angrylion RDP option, GLES3 framebuffer fixes, vertical interrupt buffer swap). GLES3 é recomendado upstream para Android. Nenhuma ação de build necessária além do upgrade do LibretroDroid.

**Status:** **MANTIDO** — core padrão confirmado.

### D3: Pipeline CI de self-build para `parallel_n64` atualizado
**Racional:** Buildbot Android está parado desde 2020-11-10. Repo upstream ativo com dynarec ARM/x86 novo (~10–15% speedup), parallel RSP updates, novas core options.
**Entrega:** GitHub Actions workflow que compila para 4 ABIs (x86, x86_64, armeabi-v7a, arm64-v8a) via NDK r27d, publica artifacts versionados em GitHub Releases com tag rolling `parallel-n64-latest`. `prepareCore` ganha lista de URLs de fallback por core (buildbot → nosso release → skip com log).

**Status:** **IMPLEMENTADA** — `.github/workflows/build-parallel-n64.yml` criado (matrix 4 ABIs, NDK r27d, rolling release tag `parallel-n64-latest`); `app/build.gradle.kts` `prepareCore` atualizado com fallback self-build → buildbot → skip guard por core+ABI; `catalog.json`, `docs/catalog.example.json`, `docs/CATALOG.md`, `README.md` atualizados.

### D4: Spike opcional — investigar `gopher64` target libretro Android
**Racional:** Projeto ativo, GPL-3.0, sucessor spiritual do simple64. RetroArch documenta core Gopher64 mas status Android incerto (Puffy: standalone apenas). Antes de qualquer integração, spike de 1–2 dias para: (a) confirmar se `Makefile.libretro` existe e compila para Android, (b) testar boot de OoT/MM, (c) medir performance vs mupen64plus-next. **Não bloqueia MVP.**

**Status:** **CANCELADA** — decisão do usuário ("deixar somente esses 2 mesmo").

### D5: Candidatos rejeitados (com razões)
| Candidato | Razão |
|-----------|-------|
| `simple64` | Arquivado fev/2025; nunca teve core libretro. |
| `ares` | Sem core libretro (issue #1375); mantenedores chamam port de "gargantuan effort" por threading model libco. |
| `mupen64plus` (old) | Arquivado 2019; sem artifact Android no buildbot. |
| Variantes Vulkan (mupen64plus-next vulkan, parallel-n64 vulkan) | LibretroDroid é **OpenGL ES only** (GLSurfaceView); sem backend Vulkan em nenhuma versão até 0.14.0 pre-release. Inúteis aqui. |

### D6: Remoção da variante GLES2 do mupen64plus-next
**Racional:** Glitches conhecidos em GPUs modernas; serve apenas dispositivos muito antigos (<1% share). Remoção reduz APK (~4–8 MB por ABI) e simplifica manutenção (2 cores em vez de 3).
**Entregue:** `CorePrefs.CORE_LIBS` reduzido a 2 entradas; `jniLibs/*/libcore_mupen_gles2.so` deletado; diálogo de seleção de core atualizado; `catalog.json` e docs ajustados.

**Status:** **IMPLEMENTADA** — aprovada pelo usuário.

---

## Plano de Implementação por Fases

### Fase A: Upgrade LibretroDroid + Testes de Regressão (Semana 1)

| Tarefa | Arquivos afetados | Entregável |
|--------|-------------------|------------|
| Atualizar dependência `libretrodroid` para `0.13.2` no `app/build.gradle.kts` | `app/build.gradle.kts` (linha 116) | Build compila com nova versão |
| Executar checklist de regressão completo (ver D1) em dispositivo físico + emulador | `views/GameActivity.kt`, `retroview/RetroView.kt`, `gamepad/` (validação visual) | Relatório de regressão: zero quebras nos invariantes congelados |
| Validar shaders CUT2/CUT3 opcionais (feature flag futura) | `config.xml` (novas variáveis opcionais) | Documentação de como habilitar; default OFF |
| Atualizar `CorePrefs` se nova API de core selection surgir (não esperado) | `utils/CorePrefs.kt` | Sem mudanças se API estável |

**Critério de aceite Fase A:** App compila, abre, carrega core, jogo roda, layout de controles idêntico ao `referencia.png`, zero crashes em rotação/background/foreground, GL recovery funcional.

**Status:** **PENDENTE** — não iniciada.

---

### Fase B: CI Self-Build `parallel_n64` (Semanas 2–3)

| Tarefa | Arquivos afetados | Entregável |
|--------|-------------------|------------|
| Criar `.github/workflows/build-parallel-n64.yml` com matrix 4 ABIs (NDK r26+) | Novo arquivo | Workflow roda em push tag `parallel-n64-*` e manual dispatch |
| Script de build: `libretro-super` ou `Makefile.libretro` direto com NDK clang | Scripts no repo (ex.: `scripts/build-parallel-n64.sh`) | Gera 4 `.so` nomeados `libcore_parallel.so` por ABI |
| Publicar artifacts em GitHub Release versionado (ex.: `parallel-n64-2026.08.23`) | GitHub Release (auto via workflow) | URLs estáveis para `prepareCore` consumir |
| Atualizar `prepareCore` no `app/build.gradle.kts`: lista de URLs fallback por core (buildbot → nosso release → skip) | `app/build.gradle.kts` (task `prepareCore`) | Build falha graciosamente se ambos indisponíveis; log claro |
| Verificar licença GPL-2.0 do parallel-n64: incluir `LICENSE.parallel-n64` no repo e oferecer source (link para commit/tag upstream) | `LICENSE.parallel-n64`, `README.md` | Conformidade GPL atendida |

**Critério de aceite Fase B:** `./gradlew prepareCore` baixa `libcore_parallel.so` atualizado (timestamp 2026+) para todos 4 ABIs; core carrega no app; OoT/MM bootam; performance ~10% melhor que artifact 2020 em dispositivo ARM64 médio.

**Status:** **CONCLUÍDA** — workflow criado (NDK r27d, matrix 4 ABIs, tag rolling `parallel-n64-latest`); `prepareCore` com fallback self-build → buildbot → skip guard; `LICENSE.parallel-n64` adicionado; `catalog.json`, `docs/catalog.example.json`, `docs/CATALOG.md`, `README.md` atualizados.

---

### Fase C: Integração Runtime dos Novos Cores (Semana 4)

| Tarefa | Arquivos afetados | Entregável |
|--------|-------------------|------------|
| Estender `CorePrefs.CORE_LIBS` se novo core adicionado (ex.: gopher64 futuro) | `utils/CorePrefs.kt` | Array atualizado; índice persistido compatível |
| Adicionar entradas no diálogo de seleção de core (LibraryActivity) | `views/LibraryActivity.kt` (diálogo) | Novo core aparece na lista com nome amigável |
| i18n: strings para nomes de core em `values/strings.xml` (pt-BR), `values-en/`, `values-es/` | 3 arquivos `strings.xml` | Textos traduzidos: "Mupen64Plus Next (GLES3)", "Parallel N64 (atualizado)" |
| Atualizar `catalog.json` schema: novos IDs de core compatíveis (`parallel_n64_updated`, `gopher64` futuro) | `catalog/catalog.json` (exemplo) + documentação | Hacks podem declarar `compatibleCores: ["mupen64plus_next_gles3", "parallel_n64_updated"]` |
| `config.xml` inalterado (default continua `mupen64plus_next_gles3`) | — | Sem mudança |

**Critério de aceite Fase C:** Usuário abre configurações → vê 2 cores (GLES3, Parallel atualizado) com nomes traduzidos; seleção persiste; catálogo valida `compatibleCores` corretamente.

**Status:** **PARCIALMENTE CONCLUÍDA** — partes de runtime já entregues: `CorePrefs.CORE_LIBS` reduzido a 2 entradas (GLES3 + Parallel); diálogo de seleção atualizado para 2 cores; `catalog.json` e docs (`catalog.example.json`, `CATALOG.md`, `README.md`) ajustados para IDs `mupen64plus_next_gles3` e `parallel_n64`; i18n de nomes de core pendente (strings para 2 cores). Fase C completa quando i18n estiver pronto.

---

### Fase D: Spike Gopher64 (Opcional, Pós-MVP)

| Tarefa | Entregável |
|--------|------------|
| Clonar `gopher64/gopher64`, buscar `Makefile.libretro` ou similar | Relatório: compila para Android? (sim/não + logs) |
| Se compila: build para arm64-v8a, testar boot OoT/MM | Métricas: FPS, compatibilidade, estabilidade vs mupen64plus-next |
| Decisão: integrar (Fase C estendida) ou arquivar | Documentação de decisão no `plano.md` |

**Status:** **CANCELADA** — decisão do usuário ("deixar somente esses 2 mesmo").

---

## Matriz de Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| **Regressão LibretroDroid 0.13.2 no ciclo de vida GL** (gamepad invariants) | Média | Alto (quebra controles, crash GL) | Checklist D1 obrigatório; testar em 3+ dispositivos (ARM64, x86_64, ARMv7); CI instrumentado roda `RetroViewIntegrationTest` |
| **Self-build parallel_n64 falha em alguma ABI** (x86, ARMv7) | Média | Médio (core indisponível para alguns usuários) | Workflow tolera falha por ABI (continue-on-error); `prepareCore` pula 404; documentar ABIs suportadas no README |
| **Manutenção toolchain NDK / libretro-super** | Baixa | Médio (builds param de funcionar) | Fixar NDK version no workflow (ex.: `ndk-version: r26d`); monitorar releases libretro-super; dependabot em `android-ndk` |
| **GPL compliance ao distribuir binários self-built** | Baixa | Alto (risco legal) | Incluir `LICENSE.parallel-n64` no repo; README aponta para tag/commit upstream exato; oferecer source via link GitHub (política padrão Libretro) |
| **Crescimento APK por core extra** | Baixa | Baixo | Cada core ~4–8 MB por ABI; 4 ABIs × 3 cores = ~48–96 MB em `jniLibs`. AAB entrega só ABI do dispositivo. Monitorar `bundletool` size. |
| **x86/x86_64 indisponíveis no buildbot ou self-build** | Média | Baixo (emuladores desktop raros) | `prepareCore` já pula 404 graciosamente; CI testa emulador x86_64 se disponível; não bloquear release. |
| **gopher64 não tem core libretro Android viável** | Alta (spike) | Baixo (spike opcional) | Timebox spike (2 dias); se falhar, documentar e arquivar; não bloqueia Fase A–C. |

---

## Critérios de Aceite (Testáveis por Fase)

### Fase A — LibretroDroid Upgrade
- [ ] `./gradlew assembleDebug` compila sem erros com `libretrodroid:0.13.2`
- [ ] `GameActivity` inicia, carrega core, renderiza frame (log `onFrameRendered`)
- [ ] Rotação tela (portrait↔landscape) não crasha; GL recovery via `recreate()` funciona
- [ ] Background → foreground retoma emulação sem tela preta
- [ ] Overlay touch: `INVISIBLE` quando só físico; pads criados após layout; `super.onDestroy()` antes dispose
- [ ] `config_load_bytes=false` default; ROM 32 MB carrega sem OOM em dispositivo 2 GB RAM

### Fase B — Self-Build parallel_n64
- [ ] Workflow GitHub Actions roda com sucesso em push tag `parallel-n64-2026.08.*`
- [ ] 4 artifacts `.so` publicados no Release (x86, x86_64, armeabi-v7a, arm64-v8a)
- [ ] `./gradlew prepareCore` baixa todos 4 (ou pula com log claro se 404)
- [ ] App com `parallel_n64` selecionado: OoT NTSC-U 1.0 boot → gameplay 5 min sem crash
- [ ] Benchmark informal: ~10% FPS gain vs artifact 2020 em Snapdragon 778G / equivalente

### Fase C — Integração Runtime
- [ ] Diálogo de core mostra 3 opções com nomes traduzidos pt-BR/en/es
- [ ] Seleção persiste após kill/restart app
- [ ] Catálogo valida `compatibleCores` com novos IDs; hack com apenas `parallel_n64_updated` não mostra GLES3 como opção (ou avisa)
- [ ] Zero strings hardcoded em Kotlin/XML

### Fase D — Spike Gopher64 (se executada)
- [ ] Relatório escrito com: compilação sucesso/falha, logs, FPS médio OoT/MM, compatibilidade save/state, veredito

---

## Questões Abertas (Decisão do Usuário)

1. **Shaders CUT2/CUT3 (upscaling) expostos ao usuário**: LibretroDroid 0.13+ suporta. Adicionar toggle em Settings (Fase C) ou deixar apenas interno/default OFF?

---

## Fontes

- LibretroDroid releases: https://github.com/Swordfish90/LibretroDroid/releases
- Buildbot LibRetro nightly Android: https://buildbot.libretro.com/nightly/android/latest/
- mupen64plus-libretro-nx: https://github.com/libretro/mupen64plus-libretro-nx
- parallel-n64: https://github.com/libretro/parallel-n64
- gopher64: https://github.com/gopher64/gopher64
- simple64 (arquivado): https://github.com/simple64/simple64
- ares issue #1375 (libretro core): https://github.com/ares-emulator/ares/issues/1375
- mupen64plus-core 2.6.0: https://github.com/mupen64plus/mupen64plus-core/releases/tag/2.6.0
- libretro-super docs: https://github.com/libretro/libretro-super
- BPS spec: https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md
- N64 ROM header: https://n64brew.dev/wiki/ROM_Header
- RadialGamePad: https://github.com/Swordfish90/RadialGamePad
- Ludere (base): https://github.com/Swordfish90/Ludere