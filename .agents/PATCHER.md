# Patcher (BPS / IPS / XDELTA) — Feature Deep Dive

## 1. Overview

Pure-Kotlin patcher module (no Android deps) that applies **BPS**, legacy **IPS**, and **xdelta3 (VCDIFF)** patches to user-provided base ROMs. BPS/IPS are applied with **triple CRC32 validation** (source checksum, patch checksum, target CRC after patch application); xdelta3 relies on its native decoder's internal source validation instead (see §4). Supports N64 byte-order normalization (.z64 / .v64 / .n64 → big-endian z64). Streaming — never holds the full 32–64 MB ROM in heap.

The facade auto-detects the container from its magic: `BPS1` (BPS), `PATCH` (IPS), or the VCDIFF header `0xD6 0xC3 0xC4 0x00` (xdelta3, RFC 3284).

## 2. Package (`patcher/`)

```
patcher/
├── bps/                  # BPS parser, applier, validator, varint
├── ips/                  # IPS applier (legacy format)
├── xdelta/               # XdeltaApplier (JNI bridge to native xdelta3 decoder)
├── n64/                  # RomNormalizer, RomHeader, ChecksumCalculator
└── PatcherFacade.kt      # applyPatch API with BPS/IPS/XDELTA auto-detection
```

| File                        | Responsibility                                                                                                       |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `n64/RomNormalizer.kt`      | Detect magic (0x80371240 / 0x37804012 / 0x40123780) → swap16/swap32 → z64 BE                                         |
| `n64/RomHeader.kt`          | Parse offset 0x3B–0x3E (gameCode), 0x3F (versionByte)                                                                |
| `n64/ChecksumCalculator.kt` | Streaming CRC32 (`java.util.zip.CRC32`), MD5, SHA-1                                                                  |
| `bps/VarInt.kt`             | Encode/decode varint (LEB128-like, 7-bit chunks, MSB continuation)                                                   |
| `bps/BpsParser.kt`          | Streaming parse: header "BPS1", sourceSize, targetSize, metadataSize, commands until footer, then 3×CRC32            |
| `bps/BpsApplier.kt`         | Streaming apply: SourceRead, TargetRead, SourceCopy, TargetCopy per spec                                             |
| `bps/BpsValidator.kt`       | Verify sourceCRC32 matches base ROM, targetCRC32 matches output, patchCRC32 matches file                             |
| `ips/IpsApplier.kt`         | Legacy IPS apply (RLE, EOF terminator)                                                                               |
| `xdelta/XdeltaApplier.kt`   | Applies xdelta3 (VCDIFF) patches via bundled native `libxdelta_jni.so`; maps decoder diagnostics to typed exceptions |
| `PatcherFacade.kt`          | `suspend fun applyPatch(baseRom: File, patch: File, output: File): Result<Unit>` with BPS/IPS/XDELTA auto-detection  |

## 3. BPS Spec (Clean-Room)

- Implemented from the public spec only: https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md
- **Footer:** 3×CRC32 in order: source, target, patch.
- **Command encoding:** low 2 bits = action, rest = length−1.
- **Varint:** LEB128 (7-bit, MSB continuation).
- **Hard Rule 4:** Do NOT read or copy code from `rom_patcher_js` (GPL-3.0) or `UniPatcher` (GPL-3.0). Document this in code headers.

## 4. XDELTA (xdelta3 / VCDIFF)

xdelta3 patches (RFC 3284 VCDIFF) are supported for hacks distributed as `.xdelta` files (e.g. `The Legend of Zelda - Voyager of Time.xdelta`, `CZLE_1.0.xdelta` in `catalog.json`). They are detected by the VCDIFF magic `0xD6 0xC3 0xC4 0x00`.

- **Native decoder:** applied via the bundled `libxdelta_jni.so` (JNI bridge `src/main/cpp/xdelta_jni.c` → `XdeltaApplier.applyNative`). Native source lives in `src/main/cpp/xdelta3` (jmacd/xdelta, **Apache License 2.0** — compatible with this app's GPL-3.0); the Kotlin wrapper is original code.
- **Source validation:** xdelta3 validates the source ROM **internally during decode** and fails on a mismatch. `XdeltaApplier` maps decoder diagnostics to typed exceptions — a source mismatch becomes `PatcherException.SourceChecksumMismatch` (so the UI can tell the user the imported base ROM is wrong); any other failure becomes `PatcherException.PatchFormatError`.
- **No embedded source CRC:** unlike BPS, the VCDIFF container does **not** carry the source CRC32. Base-ROM resolution for xdelta3 is therefore driven by the **catalog-declared CRC** (`DownloadManager.resolveBaseFile` / `expectedBaseCrc`), not read from the patch. `PatcherFacade.expectedSourceCrc32()` throws for XDELTA for this reason.
- **Host build for tests:** the `buildXdeltaHost` Gradle task compiles a Linux x86_64 copy of the decoder + CLI under `build/xdeltaHost` (never committed) so the JVM unit test `XdeltaApplierTest` can exercise `XdeltaApplier` end-to-end via the `zelda64.xdelta.jni.path` / `zelda64.xdelta.cli.path` system properties, without the Android toolchain.
- **Hard Rule 4:** the Kotlin wrapper is clean-room original; only the upstream Apache-2.0 native decoder is vendored.

## 5. Integration

- `RetroView.kt` loads `Storage.rom(hackId)` (patched ROM cache) instead of `context.assets.open()`.
- Default `config_load_bytes=false` (Rule 9): RetroView uses `gameFilePath` (file), not `gameFileBytes` (heap).
- `PatcherFacade.applyPatch` is called by `DownloadManager` / `ImportedPatchInstaller` after patch download/validation, writing the patched ROM to `Storage.rom(canonicalId)`.
- `DownloadManager` resolves the correct base ROM per format: for BPS it reads the source CRC32 from the patch footer; for **XDELTA it uses the catalog-declared base ROM CRC** (the patch carries none); IPS is self-contained.
- Store resolvers accept `.xdelta` alongside `.bps` / `.ips` / `.zip` (see `GitHubPatchResolver`, `HylianModdingParser`, `ZipExtractor`).

## 6. Validation (Rule 16)

- ROM checksums (base ROM matches hack's required version — read from BPS footer, or from the catalog-declared CRC for XDELTA)
- Patch checksums (downloaded BPS CRC32 matches catalog; xdelta3 source mismatch surfaced as `SourceChecksumMismatch`)
- Target CRC after patch application (BPS/IPS; xdelta3 relies on native internal validation)
- Catalog JSON schema
- Downloaded file sizes
