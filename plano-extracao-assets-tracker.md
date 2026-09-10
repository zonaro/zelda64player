# Plano de Extração de Assets para o Item Tracker — Zelda 64 Player

**Gerado:** 2026-09-09
**Autor:** Lobby (orquestração) + Bruce (implementação)
**Status:** Refinado a partir do guia genérico `Untitled-1` — adaptado ao projeto atual
**Objetivo:** Extrair ícones/texturas de itens diretamente das ROMs OoT/MM importadas pelo usuário, usar no Item Tracker e **remover os PNGs embarcados** de `drawable-nodpi/`

---

## 1. Contexto e Motivação

### 1.1 Estado atual (problema)

O Item Tracker manual (5 abas: Items, Locations, Songs, Hints, Upgrades) está **funcional**, mas usa **~97 PNGs embarcados** em `app/src/main/res/drawable-nodpi/` como placeholders:

| Origem                                                  | Exemplos                                                       | Licença / Risco                                    |
| ------------------------------------------------------- | -------------------------------------------------------------- | -------------------------------------------------- |
| `Draeko/ootr_gst` (`TrackerOOT/Resources/`)             | `kokiri_sword.png`, `master_sword.png`, `bow.png`, `bombs.png` | Sem licença explícita, redistribuição questionável |
| `griesenj/ZeldaTracker` (`src/img/` variantes ` 1.png`) | `mm_mask_*`, `mm_sword_*.png`, `mm_shield*.png`                | Idem                                               |
| Fallbacks cruzados                                      | `iron_boots`, `hover_boots`, `magic` reutilizam OoT para MM    | Inconsistência visual                              |

**Problemas:**
- **APK maior** (~2–4 MB só de ícones) — cada PNG 32×32 RGBA.
- **Risco legal** — redistribuir rips de assets, mesmo que placeholders, é zona cinzenta (Regra 2).
- **Inconsistência** — MM incompleto, mistura de estilos, sem fidelidade à ROM original.
- **Manutenção** — adicionar novo item exige novo PNG + `R.drawable` + rebuild.

O `plano-item-tracker.md` já marca esses assets como **temporários** ("will be replaced by original icons later").

### 1.2 Objetivo refinado

> **Extrair em tempo de execução, no próprio aparelho, os ícones originais 32×32 (RGBA16/CI8) diretamente da ROM base OoT/MM que o usuário já importou**, cachear como PNG em `filesDir`, exibir no Tracker via `ItemIconView`, e **remover 100% dos PNGs embarcados**, mantendo apenas um fallback vetorial mínimo (gerado por Dolfi, CC0) para o caso de nenhuma ROM importada.

**Princípios do projeto que sustentam a decisão:**
- **Regra 1 e 2:** Nunca distribuir ROMs nem assets proprietários. Extração local a partir da ROM do usuário é legal (usuário já possui a ROM).
- **Regra 9 (Performance):** Streaming, nunca carregar ROM inteira (32–64 MB) em heap.
- **Regra 8 (i18n):** Não afeta — nomes continuam em `strings.xml`.
- **Regra 14 (Switch UI):** Não afeta layout do RadialGamePad; apenas troca a fonte do bitmap dentro do `ItemIconView` (mesmo `SwitchGameCard` chrome).

---

## 2. Arquitetura Proposta

### 2.1 Visão geral

```
BaseRomRepository (importDir → storageDir/<crc32>.z64)
        │
        ▼
TrackerAssetExtractor (on-demand, Dispatchers.IO)
  ┌─────────────────────────────────────────────┐
  │ 1. RomNormalizer (z64/v64/n64 → BE)         │  ← já existe em patcher/n64/
  │ 2. RomHeader (gameCode/version → offset DMA)│  ← já existe
  │ 3. DmaTableParser (16 bytes/entry)          │
  │ 4. Yaz0Decompressor (se romEnd != 0)        │
  │ 5. TextureDecoder (RGBA16 / CI8+TLUT)       │
  │ 6. Bitmap → PNG (Android Bitmap.compress)   │
  └─────────────────────────────────────────────┘
        │
        ▼
TrackerAssetCache (filesDir/tracker_assets/<crc32>/<itemId>.png)
        │
        ▼
OotItemDatabase / MmItemDatabase (assetKey em vez de R.drawable)
        │
        ▼
ItemsTab → ItemIconView (Coil/BitmapFactory + placeholder vetorial)
```

### 2.2 Pacote novo (puro Kotlin, sem dependência Android onde possível)

```
br.com.redclaw.zelda64player.tracker.assets/
├── RomAssetExtractor.kt          # fachada: extractAll(baseRom: File, game: TrackerGame): Result<ExtractReport>
├── dma/
│   ├── DmaEntry.kt               # data class (vromStart/End, romStart/End, isCompressed)
│   └── DmaTableParser.kt         # parseEntries(tableOffset, maxEntries=2000)
├── compression/
│   └── Yaz0Decompressor.kt       # object decompress(src: ByteArray): ByteArray (com validação)
├── graphics/
│   ├── N64TextureFormat.kt       # enum RGBA16, CI8, IA8, I8
│   └── TextureDecoder.kt         # decodeRGBA16 / decodeCI8 (→ IntArray ARGB_8888)
├── cache/
│   ├── TrackerAssetCache.kt      # get(itemId, crc32) / put / clear / hasValidCache
│   └── AssetKey.kt               # itemId → fileName + expectedSize
├── mapping/
│   ├── OotIconMap.kt             # itemId → (dmaFileIndex, offset, format, w, h, tlutOffset?)
│   └── MmIconMap.kt              # idem para MM
└── fallback/
    └── TrackerFallbackIcons.kt   # vetores CC0 mínimos (Dolfi) para quando sem ROM
```

**Reuso direto do projeto:**
- `patcher/n64/RomNormalizer.kt` — detecção de magic `0x80371240/0x37804012/0x40123780` e normalização streaming.
- `patcher/n64/RomHeader.kt` — `gameCode` (0x3B–0x3E) e `versionByte` (0x3F) para escolher offset da DMA Table.
- `patcher/n64/ChecksumCalculator.kt` — CRC32 para nomear cache por ROM (`<crc32>.z64` já usado por `BaseRomRepository`).

### 2.3 Armazenamento

```
filesDir/
├── base_roms/<crc32>.z64              # já existe (BaseRomRepository.storageDir)
├── tracker_assets/
│   ├── <crc32_oot>/                   # um diretório por ROM base (evita colisão OoT vs MM)
│   │   ├── kokiri_sword.png           # 32×32 PNG extraído
│   │   ├── master_sword.png
│   │   ├── ... (40+ itens OoT)
│   │   └── .meta.json                 # { game:"OOT", version:"1.0", extractedAt, count }
│   └── <crc32_mm>/
│       ├── mm_sword.png
│       └── ...
└── tracker_state_<hackId>.json        # já existe (TrackerRepository)
```

- **Chave do cache:** `crc32` da ROM normalizada (mesmo usado por `BaseRomRepository`), não `hackId`. Assim vanilla e hacks que compartilham a mesma base reutilizam o mesmo cache.
- **Invalidação:** se `.meta.json` ausente ou `count` divergir do `OotIconMap.size`, re-extrai.
- **Tamanho estimado:** ~40 itens × 32×32×4 bytes ≈ 160 KB por jogo em PNG (compressão reduz para ~80 KB). Irrelevante vs APK atual.

---

## 3. Pipeline Detalhada (adaptada do guia genérico)

### 3.1 Etapa 1 — Byte Order Handling (reuso)

Não reimplementar. Usar `RomNormalizer.normalize(input, output)` streaming já existente. O extrator recebe `File` já normalizado de `BaseRomRepository` (sempre `.z64` BE). Se receber `ByteArray` em testes, usar `ByteBuffer.order(BIG_ENDIAN)`.

```kotlin
// tracker/assets/RomAssetExtractor.kt
val normalizedRom: File = baseRomFile // já é .z64 BE vindo de BaseRomRepository
// Para leitura random-access, usar RandomAccessFile ou FileChannel, nunca ByteArray completo
```

### 3.2 Etapa 2 — DMA Table Parsing (adaptado)

O guia genérico lista offsets fixos, mas o projeto precisa **detectar versão** e resolver o arquivo `icon_item_static` via índice DMA, não offset bruto.

**Offsets da DMA Table por versão (confirmar via Calamari/Puffy antes de implementar):**

| ROM                | GameCode | Version | DMA Offset               |
| ------------------ | -------- | ------- | ------------------------ |
| OoT NTSC 1.0 (USA) | `CZLE`   | 0x00    | `0x00007430`             |
| OoT NTSC 1.1       | `CZLE`   | 0x01    | `0x00007430` (verificar) |
| OoT PAL 1.0        | `CZLE`   | —       | `0x00007950`             |
| MM NTSC 1.0 (USA)  | `NZSE`   | 0x00    | `0x0001A500`             |
| MM PAL             | `NZSP`   | —       | a confirmar              |

**Implementação adaptada:**

```kotlin
package br.com.redclaw.zelda64player.tracker.assets.dma

data class DmaEntry(
    val index: Int,
    val vromStart: Int,
    val vromEnd: Int,
    val romStart: Int,
    val romEnd: Int
) {
    val isCompressed: Boolean get() = romEnd != 0
    val exists: Boolean get() = romStart != -1 && vromStart != 0
    val compressedSize: Int get() = if (isCompressed) romEnd - romStart else vromEnd - vromStart
    val uncompressedSize: Int get() = vromEnd - vromStart
}

class DmaTableParser(private val romChannel: FileChannel, private val tableOffset: Long) {
    fun parseEntries(maxEntries: Int = 2000): List<DmaEntry> {
        val entries = mutableListOf<DmaEntry>()
        val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        for (i in 0 until maxEntries) {
            buf.clear()
            romChannel.read(buf, tableOffset + i * 16)
            buf.flip()
            val vStart = buf.int
            val vEnd = buf.int
            val rStart = buf.int
            val rEnd = buf.int
            if (vStart == 0 && vEnd == 0 && rStart == 0 && rEnd == 0) break
            entries.add(DmaEntry(i, vStart, vEnd, rStart, rEnd))
        }
        return entries
    }
}
```

**Diferença vs guia genérico:** leitura via `FileChannel` (streaming, sem `ByteArray` da ROM inteira) e `ByteBuffer` BE. O guia usa `RomBuffer` com `ByteArray` — inadequado para 64 MB em Android (Regra 9).

**Localização do arquivo de ícones:** em vez de offset fixo dentro de `icon_item_static`, o extrator:
1. Resolve `dmaIndex` de `icon_item_static` via `OotIconMap.dmaFileIndex` (ex.: índice  2  para OoT, a confirmar via `zeldaret/oot` `assets/xml/`).
2. Lê `DmaEntry` correspondente, extrai bytes `romStart..romEnd` (ou `vromStart..vromEnd` se não comprimido).
3. Se `isCompressed`, passa por Yaz0.

### 3.3 Etapa 3 — Yaz0 Decompression (corrigido)

O código do guia está funcional, mas precisa de **bounds checks** e tratamento de `dist`/`copyLen` para evitar OOB em ROMs corrompidas (Regra 16).

```kotlin
package br.com.redclaw.zelda64player.tracker.assets.compression

object Yaz0Decompressor {
    fun decompress(src: ByteArray): ByteArray {
        require(src.size >= 16) { "Yaz0: src muito pequeno" }
        val magic = String(src, 0, 4, Charsets.US_ASCII)
        require(magic == "Yaz0") { "Assinatura Yaz0 inválida: $magic" }
        val uncompressedSize = ByteBuffer.wrap(src, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        require(uncompressedSize in 1..32_000_000) { "Yaz0: tamanho inválido $uncompressedSize" }
        val dst = ByteArray(uncompressedSize)
        var srcPos = 16
        var dstPos = 0
        var validBitCount = 0
        var currCodeByte = 0
        while (dstPos < uncompressedSize) {
            if (validBitCount == 0) {
                require(srcPos < src.size) { "Yaz0: truncated code byte" }
                currCodeByte = src[srcPos++].toInt() and 0xFF
                validBitCount = 8
            }
            if ((currCodeByte and 0x80) != 0) {
                require(srcPos < src.size && dstPos < dst.size) { "Yaz0: truncated literal" }
                dst[dstPos++] = src[srcPos++]
            } else {
                require(srcPos + 1 < src.size) { "Yaz0: truncated copy header" }
                val byte1 = src[srcPos++].toInt() and 0xFF
                val byte2 = src[srcPos++].toInt() and 0xFF
                val dist = ((byte1 and 0x0F) shl 8) or byte2
                var copyLen = byte1 ushr 4
                if (copyLen == 0) {
                    require(srcPos < src.size) { "Yaz0: truncated extended length" }
                    copyLen = (src[srcPos++].toInt() and 0xFF) + 0x12
                } else copyLen += 2
                var copyFrom = dstPos - dist - 1
                require(copyFrom >= 0 && dstPos + copyLen <= dst.size) { "Yaz0: invalid copy dist=$dist len=$copyLen" }
                repeat(copyLen) { dst[dstPos++] = dst[copyFrom++] }
            }
            currCodeByte = currCodeByte shl 1
            validBitCount--
        }
        return dst
    }
}
```

**Nota:** manter `object` sem dependência Android para testes JVM puros.

### 3.4 Etapa 4 — Texture Decoding (adaptado para Android Bitmap)

O guia decodifica para `IntArray` ARGB_8888 — correto, mas no projeto o destino é `Bitmap` → PNG em disco.

```kotlin
package br.com.redclaw.zelda64player.tracker.assets.graphics

object TextureDecoder {
    fun decodeRGBA16(data: ByteArray, offset: Int, width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        var idx = offset
        for (i in pixels.indices) {
            val b1 = data[idx++].toInt() and 0xFF
            val b2 = data[idx++].toInt() and 0xFF
            val raw = (b1 shl 8) or b2
            val r = ((raw ushr 11) and 0x1F) * 255 / 31
            val g = ((raw ushr 6) and 0x1F) * 255 / 31
            val b = ((raw ushr 1) and 0x1F) * 255 / 31
            val a = if ((raw and 0x01) != 0) 255 else 0
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return pixels
    }

    fun decodeCI8(
        pixelData: ByteArray, pixelOffset: Int, width: Int, height: Int,
        tlutData: ByteArray, tlutOffset: Int
    ): IntArray {
        val palette = decodeRGBA16(tlutData, tlutOffset, 256, 1)
        return IntArray(width * height) { i ->
            val idx = pixelData[pixelOffset + i].toInt() and 0xFF
            palette[idx]
        }
    }

    /** Converte IntArray ARGB → Bitmap e salva como PNG. */
    fun saveAsPng(pixels: IntArray, width: Int, height: Int, outFile: File) {
        val bmp = android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888)
        outFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }
}
```

**Formatos por jogo:**
- **OoT `icon_item_static`:** predominante **RGBA16 32×32** (2048 bytes/ícone). Alguns itens CI8 com TLUT separada.
- **MM `icon_item_24_static`:** 24×24 ou 32×32 dependendo do HUD; verificar se usa CI8+TLUT ou RGBA16.

### 3.5 Etapa 5 — Mapeamento e Export (adaptado ao Tracker)

O guia lista 12 itens com offsets `0x0000, 0x0800...` dentro de `icon_item_static`. No projeto, o mapeamento deve cobrir **todos os itens do `OotItemDatabase`/`MmItemDatabase`** e usar `assetKey` em vez de `R.drawable`.

**Exemplo de `OotIconMap.kt`:**

```kotlin
package br.com.redclaw.zelda64player.tracker.assets.mapping

data class IconMapping(
    val itemId: String,          // ex.: "kokiri_sword" (mesmo id do TrackerItem)
    val dmaFileIndex: Int,       // índice DMA de icon_item_static (ex.: 2)
    val offset: Int,             // offset dentro do arquivo descomprimido
    val width: Int = 32,
    val height: Int = 32,
    val format: N64TextureFormat = N64TextureFormat.RGBA16,
    val tlutOffset: Int? = null  // só para CI8
)

object OotIconMap {
    val entries: List<IconMapping> = listOf(
        IconMapping("deku_stick", dmaFileIndex = 2, offset = 0x0000),
        IconMapping("deku_nut", dmaFileIndex = 2, offset = 0x0800),
        IconMapping("bomb_bag", dmaFileIndex = 2, offset = 0x1000),
        // ... todos os 40+ itens de OotItemDatabase
        // Offsets exatos a confirmar via zeldaret/oot assets/xml/icon_item_static.xml
    )
    val byId: Map<String, IconMapping> = entries.associateBy { it.itemId }
}
```

**Fonte da verdade para offsets:** `zeldaret/oot` e `zeldaret/mm` (`assets/xml/`), `Z64Utils` (visualização TLUT), `OoTMM` (mapeamento combinado). Validar com Calamari antes de congelar.

---

## 4. Integração com o Item Tracker Existente

### 4.1 Modelo (`TrackerModels.kt`)

**Antes:**
```kotlin
data class TrackerItem(val id: String, @StringRes val nameRes: Int, @DrawableRes val iconRes: Int = 0, ...)
```

**Depois:**
```kotlin
data class TrackerItem(
    val id: String,
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int = 0, // mantido como fallback vetorial (Dolfi), 0 = sem fallback
    val assetKey: String = id,         // chave para TrackerAssetCache (ex.: "kokiri_sword")
    val maxCount: Int = 1,
    val cycleLabels: List<Int> = emptyList(),
    val cycleIcons: List<Int> = emptyList() // ciclo também migra para assetKey quando possível
)
```

Migração gradual: `iconRes` permanece para fallback vetorial mínimo; `assetKey` é a fonte primária. Quando `TrackerAssetCache.has(itemId, crc32)` for true, usa PNG extraído; senão, usa `iconRes` vetorial.

### 4.2 Cache (`TrackerAssetCache.kt`)

```kotlin
class TrackerAssetCache(private val context: Context) {
    private fun dirFor(crc32: String): File = File(context.filesDir, "tracker_assets/$crc32").apply { mkdirs() }
    fun fileFor(itemId: String, crc32: String): File = File(dirFor(crc32), "$itemId.png")
    fun has(itemId: String, crc32: String): Boolean = fileFor(itemId, crc32).exists()
    fun put(itemId: String, crc32: String, pixels: IntArray, w: Int, h: Int) { /* saveAsPng */ }
    fun clear(crc32: String) { dirFor(crc32).deleteRecursively() }
    fun hasValidCache(crc32: String, expectedCount: Int): Boolean { /* .meta.json + count */ }
}
```

### 4.3 Extrator (`RomAssetExtractor.kt`)

```kotlin
class RomAssetExtractor(private val context: Context, private val cache: TrackerAssetCache) {
    /**
     * Extrai todos os ícones de [game] a partir de [baseRomFile] (.z64 BE).
     * Roda em Dispatchers.IO, streaming, sem carregar ROM em heap.
     * Retorna ExtractReport com sucessos/falhas por item.
     */
    suspend fun extractAll(baseRomFile: File, game: TrackerGame): Result<ExtractReport> = withContext(Dispatchers.IO) {
        val crc32 = ChecksumCalculator.crc32(baseRomFile)
        val crcHex = crc32.toString(16).padStart(8, '0')
        if (cache.hasValidCache(crcHex, expectedCountFor(game))) return@withContext Result.success(ExtractReport(cached = true))
        // 1. Detectar versão via RomHeader
        // 2. Resolver dmaTableOffset
        // 3. FileChannel + DmaTableParser
        // 4. Para cada IconMapping: ler DmaEntry, Yaz0 se necessário, decode, saveAsPng
        // 5. Escrever .meta.json
    }
}
```

**Quando extrair:**
- **On-demand** na primeira abertura do Tracker quando `BaseRomRepository` tem ROM mas cache vazio → coroutine em `TrackerViewModel` com `isExtracting` StateFlow e spinner Switch UI.
- **Após importação de nova ROM** (`BaseRomRepository.scanAndRegister` sucesso) → `WorkManager` one-shot ou coroutine direta.
- **Nunca no startup** se cache já válido (evita I/O desnecessário).

### 4.4 UI (`ItemIconView.kt` / `ItemsTab.kt`)

**Antes:** `cell.bind(item, ..., iconRes = R.drawable.kokiri_sword)` → `ImageView.setImageResource(iconRes)`.

**Depois:**
```kotlin
// ItemIconView.kt
fun bind(item: TrackerItem, displayName: String, obtained: Boolean, count: Int, crc32: String?) {
    val assetFile = crc32?.let { cache.fileFor(item.assetKey, it) }?.takeIf { it.exists() }
    if (assetFile != null) {
        // Coil ou BitmapFactory (Coil já é dependência do projeto)
        iconView.load(assetFile) {
            placeholder(R.drawable.placeholder_cover) // fallback vetorial mínimo
            error(R.drawable.placeholder_cover)
        }
    } else {
        iconView.setImageResource(item.iconRes.takeIf { it != 0 } ?: R.drawable.placeholder_cover)
    }
    // ... resto (badge, check, stepper) inalterado
}
```

`ItemsTab.buildGrid()` passa `crc32` do `BaseRomRepository` (ou `null` se nenhuma ROM importada). `TrackerViewModel` expõe `currentBaseRomCrc: StateFlow<String?>`.

**Coil já está no projeto** (usado no Store), então `iconView.load(File)` é trivial. Alternativa sem Coil: `BitmapFactory.decodeFile`.

### 4.5 ViewModel (`TrackerViewModel.kt`)

Adicionar:
```kotlin
val isExtracting: StateFlow<Boolean>
val extractionProgress: StateFlow<Int> // 0..100
fun ensureAssetsExtracted(game: TrackerGame)
```

Chamado por `TrackerDialogFragment.onViewCreated` se `!cache.hasValidCache(crc, expectedCount)`.

---

## 5. Remoção dos Assets Embarcados

### 5.1 Inventário a remover

Todos os arquivos em `app/src/main/res/drawable-nodpi/` listados no §1.1 (97 PNGs), **exceto** `placeholder_cover.png` (usado como fallback genérico e já é placeholder, não rip).

Lista completa a deletar (confirmar com `git ls-files`):

```
biggoron.png, bombchu.png, bombs.png, boomerang.png, bow.png, deku_nut.png, deku_stick.png,
dins_fire.png, farores_wind.png, fire_arrow.png, goron_tunic.png, gossip_stone.png, green_tunic.png,
hammer.png, hookshot.png, hover_boots.png, hylian_shield.png, ice_arrow.png, iron_boots.png,
kokiri_shield.png, kokiri_sword.png, lens.png, light_arrow.png, longshot.png, magic.png,
master_sword.png, mirror_shield.png, mm_bomb_bag.png, mm_bombers_notebook.png, mm_fire_arrow.png,
mm_great_fairy_sword.png, mm_heros_bow.png, mm_hookshot.png, mm_ice_arrow.png, mm_lens_of_truth.png,
mm_light_arrow.png, mm_magic_beans.png, mm_mask_*.png (20+), mm_pictograph_box.png,
mm_remains_*.png (4), mm_shield*.png, mm_sword*.png, nairus_love.png, ocarina_of_time.png,
oot_mask_*.png (8), oot_medallion_*.png (6), scale.png, slingshot.png, strength*.png (3),
wallet*.png (3), zora_tunic.png
```

### 5.2 Passos de remoção (ordem importa)

1. **Fase A — Extrator funcional + cache (sem remover PNGs):**
   - Implementar `tracker/assets/**`, `TrackerAssetCache`, `RomAssetExtractor`.
   - Atualizar `TrackerItem` com `assetKey`, manter `iconRes` como fallback.
   - Atualizar `ItemIconView` para tentar cache primeiro, fallback para `iconRes`.
   - Testes JVM: Yaz0, RGBA16, DmaParser com fixtures sintéticas (sem ROM real).
   - QA manual: importar OoT 1.0 USA, abrir Tracker → ícones extraídos aparecem; sem ROM → fallback vetorial.

2. **Fase B — Fallback vetorial CC0 (Dolfi):**
   - Dolfi gera **vetores SVG mínimos** (ou PNGs CC0) para cada `itemId` — estilo Switch (paleta OoT amarelo / MM roxo), **não rips**.
   - Substituir `R.drawable.*` dos PNGs rippados por `R.drawable.ic_tracker_fallback_*` vetoriais (ou manter `placeholder_cover` único se preferir minimalismo).
   - Atualizar `OotItemDatabase`/`MmItemDatabase` para apontar para fallbacks vetoriais.

3. **Fase C — Deleção dos PNGs embarcados:**
   - `git rm app/src/main/res/drawable-nodpi/<lista acima>`
   - Remover referências `R.drawable.<nome>` restantes (grep).
   - Verificar `TrackerCatalogTest.kt` — atualizar para não depender de `R.drawable` rippado.
   - Build release + teste em device sem ROM importada (deve mostrar fallbacks) e com ROM (deve mostrar extraídos).

4. **Fase D — Limpeza:**
   - Atualizar `README.md` (seção Item Tracker) e `.agents/FEATURES.md` para documentar extração.
   - Atualizar `plano-item-tracker.md` §Icons para refletir "extraído da ROM, não embarcado".
   - Adicionar nota em `LICENSE`/`NOTICE` se necessário (rcheevos MIT já documentado; fallbacks CC0).

### 5.3 Critério de aceitação para remoção

- [ ] `find app/src/main/res/drawable-nodpi -name "*.png" | wc -l` == 1 (`placeholder_cover.png` apenas) ou 0 se placeholder também vetorizado.
- [ ] `grep -r "R.drawable\." app/src/main/java/br/com/redclaw/zelda64player/tracker/` não referencia nenhum PNG deletado.
- [ ] Tracker abre e mostra ícones corretos com OoT 1.0 USA e MM 1.0 USA importados.
- [ ] Tracker abre e mostra fallbacks vetoriais sem nenhuma ROM importada (sem crash).
- [ ] APK release diminui ~2–4 MB vs antes.
- [ ] Nenhum asset proprietário commitado (verificar `git diff --stat`).

---

## 6. Roadmap e Delegação

| Fase                     | Tarefas                                                                                                                                                                           | Agente                       | Estimativa |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- | ---------- |
| **1 — Fundação**         | `DmaEntry`, `DmaTableParser` (FileChannel), `Yaz0Decompressor` (com bounds checks), `TextureDecoder` (RGBA16/CI8), `N64TextureFormat`, testes JVM com fixtures sintéticas         | **Bruce**                    | 1 semana   |
| **2 — Mapeamento**       | `OotIconMap`/`MmIconMap` completos (40+ OoT, 30+ MM), validar offsets via `zeldaret/oot` `assets/xml/`, confirmar `dmaFileIndex` e `dmaTableOffset` por versão com Calamari/Puffy | **Bruce + Calamari + Puffy** | 1 semana   |
| **3 — Cache e Extrator** | `TrackerAssetCache`, `RomAssetExtractor` (coroutine, streaming, `.meta.json`), integração com `BaseRomRepository`/`ChecksumCalculator`/`RomHeader`                                | **Bruce**                    | 1 semana   |
| **4 — UI**               | Atualizar `TrackerItem.assetKey`, `ItemIconView` (Coil File), `ItemsTab`/`TrackerViewModel` (`isExtracting`, `ensureAssetsExtracted`), spinner Switch UI                          | **Bruce**                    | 1 semana   |
| **5 — Fallback**         | Vetores CC0 mínimos (Dolfi) para cada item, atualizar `OotItemDatabase`/`MmItemDatabase`                                                                                          | **Dolfi + Bruce**            | 3–5 dias   |
| **6 — Remoção**          | `git rm` PNGs, limpar `R.drawable`, atualizar `TrackerCatalogTest`, QA manual (com/sem ROM), medir APK                                                                            | **Bruce**                    | 2–3 dias   |
| **7 — Docs e QA visual** | Atualizar `README`, `.agents/FEATURES.md`, `plano-item-tracker.md`, screenshots Switch UI (Chululu), strings pt-BR/en/es (Wally)                                                  | **Wally + Chululu**          | 3–5 dias   |

**Total estimado:** 4–6 semanas (complexidade média-alta; maior risco é mapeamento de offsets).

---

## 7. Testes

| Camada                | Ferramenta                              | O que testar                                                                                                                                                                                                                                                 |
| --------------------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Unit (JVM)**        | JUnit 5 + fixtures sintéticas           | `Yaz0Decompressor` (round-trip: comprimir sintético → descomprimir → assert), `TextureDecoder.decodeRGBA16` (pixel 0xFFFF → ARGB), `DmaTableParser` (buffer fake com 3 entries + terminador zero), `TrackerAssetCache` (put/has/clear com `TemporaryFolder`) |
| **Integration (JVM)** | JUnit 5 + `BaseRomRepository` temp dirs | `RomAssetExtractor` com ROM fake mínima (header + DMA + Yaz0 block sintético) — sem ROM real commitada (Regra 1)                                                                                                                                             |
| **Instrumented**      | Espresso / Compose Test (se migrar)     | `ItemsTab` mostra `ImageView` com drawable correto (mock cache)                                                                                                                                                                                              |
| **Manual QA**         | Device físico                           | Importar OoT 1.0 USA real → Tracker → ícones idênticos ao jogo; importar MM → idem; sem ROM → fallbacks; reimportar ROM diferente → cache invalidado e re-extraído                                                                                           |

**Fixtures:** nunca commitar ROMs nem trechos de ROM (Regra 1/2). Usar dados sintéticos: `Yaz0` block com `uncompressedSize=4` e payload `0x41 0x42 0x43 0x44`, `RGBA16` buffer com 2 pixels conhecidos.

Comandos:
```bash
./gradlew :app:testDebugUnitTest --tests "*tracker.assets.*"
./gradlew :app:connectedDebugAndroidTest
```

---

## 8. Riscos e Mitigações

| Risco                                                    | Impacto                                  | Mitigação                                                                                                                                                              |
| -------------------------------------------------------- | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **DMA offset varia por versão** (1.0 vs 1.1, USA vs PAL) | Extração falha silenciosa, ícones pretos | Detectar `gameCode`+`versionByte` via `RomHeader`; tabela de offsets versionada; fallback para erro amigável ("ROM não suportada para extração, usando ícones padrão") |
| **Arquivo `icon_item_static` comprimido vs não**         | `romEnd==0` vs `!=0` mal interpretado    | Respeitar `DmaEntry.isCompressed` (Regra do guia: `romEnd != 0` → comprimido)                                                                                          |
| **CI8 sem TLUT**                                         | Cores erradas                            | Mapear `tlutOffset` por item; se ausente, assumir RGBA16                                                                                                               |
| **ROM corrompida / Yaz0 truncado**                       | Crash OOB                                | Bounds checks em `Yaz0Decompressor` + `Result` com `PatcherException`-like typed errors; UI mostra fallback                                                            |
| **Performance em device low-end**                        | ANR se extrair na main thread            | Sempre `Dispatchers.IO`, `FileChannel`, nunca `ByteArray` da ROM inteira; extração one-shot com progresso                                                              |
| **APK sem ROM importada**                                | Tracker vazio                            | Fallbacks vetoriais CC0 garantem UX degradada mas funcional; mensagem "Importe uma ROM OoT/MM para ver ícones originais"                                               |
| **Mudança de `zeldaret/oot` offsets**                    | Mapeamento desatualizado                 | Versionar `OotIconMap` com `catalogVersion`-like; testes de regressão com ROMs conhecidas (CRC32)                                                                      |

---

## 9. Referências

- **Guia genérico original:** `Untitled-1` (anexo) — pipeline 5 etapas, código `RomBuffer`/`DmaTableParser`/`Yaz0Decompressor`/`TextureDecoder`.
- **BPS Spec (clean-room):** https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md
- **N64 ROM Header:** https://n64brew.dev/wiki/ROM_Header
- **zeldaret/oot & zeldaret/mm** (descompilação C): `assets/xml/` — nomes exatos de arquivos, `icon_item_static` DMA index, offsets.
- **Z64Utils** (C#/.NET): https://github.com/zeldaret/Z64Utils — referência para F3DZEX, TLUT, conversão de texturas.
- **zelda-internal-file-extractor** (C): https://github.com/politerust/zelda-internal-file-extractor — iteração DMA + Yaz0.
- **OoTMM** (TypeScript/C): https://github.com/OoTMM/OoTMM — mapeamento combinado OoT+MM.
- **Projeto atual:** `patcher/n64/RomNormalizer.kt`, `RomHeader.kt`, `ChecksumCalculator.kt`, `data/local/BaseRomRepository.kt`, `tracker/model/TrackerModels.kt`, `tracker/data/OotItemDatabase.kt`, `tracker/ui/components/ItemIconView.kt`.

---

## 10. Checklist de Implementação (para Bruce)

- [ ] Criar `tracker/assets/**` com estrutura do §2.2
- [ ] Implementar `DmaEntry`/`DmaTableParser` com `FileChannel` (não `ByteArray`)
- [ ] Implementar `Yaz0Decompressor` com bounds checks
- [ ] Implementar `TextureDecoder` + `saveAsPng`
- [ ] Mapear `OotIconMap`/`MmIconMap` completos (validar com Calamari/Puffy)
- [ ] Implementar `TrackerAssetCache` + `.meta.json`
- [ ] Implementar `RomAssetExtractor.extractAll` (suspend, IO, streaming)
- [ ] Atualizar `TrackerItem.assetKey` + `TrackerViewModel.ensureAssetsExtracted`
- [ ] Atualizar `ItemIconView`/`ItemsTab` para carregar de `File` via Coil
- [ ] Dolfi: fallbacks vetoriais CC0
- [ ] Fase C: `git rm` PNGs + limpar `R.drawable`
- [ ] Testes JVM + QA manual (com/sem ROM)
- [ ] Docs: `README`, `.agents/FEATURES.md`, `plano-item-tracker.md`

---

*Este plano substitui o guia genérico `Untitled-1` para o contexto do Zelda 64 Player. A extração é 100% on-device, a partir da ROM do usuário, sem distribuição de assets proprietários.*
