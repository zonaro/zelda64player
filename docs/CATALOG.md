# Hack Catalog Format

This document describes the JSON catalog format consumed by the **Zelda 64
Player** Hack Store. A catalog is a single JSON document listing the available
Zelda 64 hacks (BPS patches) and the base ROM each one requires.

The app fetches one or more catalog URLs (default:
`https://raw.githubusercontent.com/zonaro/zelda64player/main/catalog/catalog.json`),
merges them by hack id (later catalog wins), and caches the result on device
with ETag/If-None-Match conditional GETs.

See `catalog/catalog.json` for the shipped (empty) default catalog and
`docs/catalog.example.json` for a fully-populated example with two hacks.

---

## Top-level fields

| Field            | Required | Type   | Description |
|------------------|----------|--------|-------------|
| `catalogVersion` | Yes      | int    | Schema version for future migrations. |
| `lastUpdated`    | Yes      | string | ISO 8601 UTC timestamp (e.g. `2026-08-22T10:30:00Z`). |
| `hacks`          | Yes      | array  | List of hack objects (see below). |

---

## Hack object

| Field                   | Required | Type            | Description |
|-------------------------|----------|-----------------|-------------|
| `id`                    | Yes      | string          | Unique slug. Used for cache paths and installed-state keys. |
| `name`                  | Yes      | string          | Display name shown in the Store. |
| `description`           | Yes      | string          | Long description shown in the detail sheet. |
| `author`                | Yes      | string          | Hack author. |
| `version`               | Yes      | string          | Patch version (semver recommended). Used to detect updates. |
| `baseRom.name`          | Yes      | string          | Human-readable base ROM name. |
| `baseRom.gameCode`      | Yes      | string (4 chars)| N64 header game code (e.g. `CZLE`, `NSME`). |
| `baseRom.versionByte`   | Yes      | int             | Header version byte (0 = v1.0). |
| `baseRom.checksums.crc32` | Yes    | string          | CRC32 of the normalized big-endian `.z64` base ROM. **Minimum required.** May be prefixed with `0x`. |
| `baseRom.checksums.md5` | No       | string          | Optional extra validation. |
| `baseRom.checksums.sha1`| No       | string          | Optional extra validation. |
| `patch.url`             | Yes      | string (URL)    | Direct URL to the `.bps` file, or to a `.zip` containing it. |
| `patch.filename`        | Yes      | string          | The `.bps` file name (the file itself, or the entry name inside the zip). |
| `patch.size`            | Yes      | int (bytes)     | File size, used for download progress. |
| `patch.checksums.crc32` | Yes      | string          | CRC32 of the downloaded patch file. **Minimum required.** May be prefixed with `0x`. |
| `patch.checksums.md5`   | No       | string          | Optional extra validation (checked only when present). |
| `coverImageUrl`         | No       | string (URL)    | Cover image. A placeholder is shown when absent. |
| `tags`                  | No       | array of string| Free-form tags (future filtering). |
| `compatibleCores`       | No       | array of string| Libretro core ids known to work (e.g. `mupen64plus_next_gles3`). |

A malformed individual hack entry (missing required field, bad JSON) is skipped
at parse time so a single bad entry cannot break the whole catalog.

---

## Hosting guidance

- **Catalog JSON**: host anywhere reachable over HTTPS. A GitHub raw URL
  (e.g. `https://raw.githubusercontent.com/<user>/<repo>/main/catalog.json`) is
  the simplest option and supports ETag conditional requests, which the app uses
  to avoid re-downloading unchanged catalogs.
- **Patch files**: distribute the `.bps` (or a `.zip` containing it) via GitHub
  Releases or any static host. Use a stable, direct download URL in
  `patch.url`. The `patch.filename` must match the actual `.bps` file name inside
  the archive when distributing a `.zip`.
- **Checksums**: compute the patch CRC32 (and optionally MD5) over the final
  `.bps` bytes and put them in `patch.checksums`. The app validates the
  downloaded file against these before installing. Base ROM checksums let the app
  tell the user whether they own the correct ROM version.
- **Covers**: any image URL works; square or 10:7 landscape art is recommended.
  When omitted, the app shows a generated placeholder.

---

## Example

See `docs/catalog.example.json` for a complete, ready-to-adapt document
containing two hacks (an OoT hack distributed as a plain `.bps` and an MM hack
distributed as a `.zip`).
