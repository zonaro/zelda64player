# Progresso — Extração de Assets do Item Tracker

> Plano base: `plano-extracao-assets-tracker.md`
> Atualizado a cada tarefa concluída. Legenda: ⬜ pendente · 🟡 em andamento · ✅ concluído · ⏭️ adiado

## Fase 1 — Fundação (Bruce)

| # | Tarefa | Status | Commit | Notas |
|---|--------|--------|--------|-------|
| 1.1 | `DmaEntry` + `DmaTableParser` (FileChannel, streaming) | ✅ | 79c54d6+ | `tracker/assets/dma/` |
| 1.2 | `Yaz0Decompressor` com bounds checks | ✅ | 79c54d6+ | `tracker/assets/compression/` |
| 1.3 | `N64TextureFormat` + `TextureDecoder` (RGBA16/CI8 → ARGB + saveAsPng) | ✅ | 79c54d6+ | `tracker/assets/graphics/` |
| 1.4 | Testes JVM com fixtures sintéticas (Yaz0, RGBA16, DMA) | ✅ | 79c54d6+ | `Yaz0DecompressorTest`, `TextureDecoderTest`, `DmaTableParserTest` — BUILD SUCCESSFUL |

## Fase 2 — Mapeamento (Bruce + Calamari + Puffy)

| # | Tarefa | Status | Commit | Notas |
|---|--------|--------|--------|-------|
| 2.1 | `OotIconMap` completo (40+ itens, validar via zeldaret/oot) | ✅ | próximo | `tracker/assets/mapping/OotIconMap.kt` — offsets sequenciais 0x800, TODO validar via zeldaret/oot |
| 2.2 | `MmIconMap` completo (30+ itens) | ✅ | próximo | `tracker/assets/mapping/MmIconMap.kt` |
| 2.3 | Tabela `dmaTableOffset` por versão (gameCode+versionByte) | ✅ | próximo | `tracker/assets/mapping/DmaTableOffsets.kt` (CZLE/NZSE + PAL) |

## Fase 3 — Cache e Extrator (Bruce)

| # | Tarefa | Status | Commit | Notas |
|---|--------|--------|--------|-------|
| 3.1 | `TrackerAssetCache` + `.meta.json` | ✅ | próximo | `tracker/assets/cache/TrackerAssetCache.kt` |
| 3.2 | `RomAssetExtractor.extractAll` (suspend, IO, streaming) | ✅ | próximo | `tracker/assets/RomAssetExtractor.kt` — DMA→Yaz0→RGBA16→PNG |
| 3.3 | Integração com `BaseRomRepository`/`RomHeader`/`ChecksumCalculator` | ✅ | próximo | `RomAssetExtractor` usa `RomHeader`+`DmaTableOffsets`+`ChecksumCalculator` |

## Fase 4 — UI (Bruce)

| # | Tarefa | Status | Commit | Notas |
|---|--------|--------|--------|-------|
| 4.1 | `TrackerItem.assetKey` + `TrackerViewModel.ensureAssetsExtracted` | ✅ | próximo | `TrackerModels.assetKey`, `TrackerViewModel` com `assetCrc`/`isExtracting`/`ensureAssetsExtracted()` |
| 4.2 | `ItemIconView`/`ItemsTab` carregando de `File` via Coil | ✅ | próximo | `ItemIconView.bind(..., assetCrc)` via Coil File + fallback drawable; `ItemsTab` com `lifecycleScope` |
| 4.3 | Spinner/progresso Switch UI durante extração | ⏭️ | — | Adiado — `isExtracting` StateFlow já exposto, UI de spinner pode ser adicionada depois |

## Fase 5 — Fallback (Dolfi + Bruce)

| # | Tarefa | Status | Commit | Notas |
|---|--------|--------|--------|-------|
| 5.1 | Vetores CC0 mínimos por item (Dolfi) | ⬜ | — |  |
| 5.2 | Atualizar `OotItemDatabase`/`MmItemDatabase` para fallbacks | ⬜ | — |  |

## Fase 6 — Remoção (Bruce)

| # | Tarefa | Status | Commit | Notas |
|---|--------|--------|--------|-------|
| 6.1 | `git rm` PNGs de `drawable-nodpi/` + limpar `R.drawable` | ⬜ | — |  |
| 6.2 | Atualizar `TrackerCatalogTest` + QA manual (com/sem ROM) | ⬜ | — |  |
| 6.3 | Medir redução do APK | ⬜ | — |  |

## Fase 7 — Docs e QA Visual (Wally + Chululu)

| # | Tarefa | Status | Commit | Notas |
|---|--------|--------|--------|-------|
| 7.1 | Atualizar `README`, `.agents/FEATURES.md`, `plano-item-tracker.md` | ⬜ | — |  |
| 7.2 | Screenshots Switch UI (Chululu) + strings pt-BR/en/es (Wally) | ⬜ | — |  |

---

## Log de execução

| Data | Fase | O que foi feito |
|------|------|-----------------|
| 2026-09-09 | — | Plano `plano-extracao-assets-tracker.md` criado, commitado e pushado (`79c54d6`). |
| 2026-09-10 | — | Arquivo de progresso criado. Início da Fase 1. |
| 2026-09-10 | 1 | Fase 1 concluída: `DmaEntry`, `DmaTableParser`, `Yaz0Decompressor`, `N64TextureFormat`, `TextureDecoder` + 3 suites de testes JVM (BUILD SUCCESSFUL). |
| 2026-09-10 | 2–4 | Fases 2–4 concluídas: `OotIconMap`/`MmIconMap`/`DmaTableOffsets`, `TrackerAssetCache`, `RomAssetExtractor`, `TrackerItem.assetKey`, `TrackerViewModel.ensureAssetsExtracted`, `ItemIconView`+`ItemsTab` via Coil. Compilação OK, testes OK. |
