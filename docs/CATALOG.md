# Hack Catalog Format

This document describes the JSON catalog consumed by the **Zelda 64 Player**
Hack Store. The app has one built-in store, **Main Store**. Its catalog can
contain curated records and public metadata imported from Hylian Modding; Hylian
Modding is an attribution source, not a separately selectable store.

The default catalog is
`https://raw.githubusercontent.com/zonaro/zelda64player/main/catalog/catalog.json`.
It is cached on device with ETag/If-None-Match conditional requests. See
`catalog/catalog.json` for the live catalog and `docs/catalog.example.json` for
a small author-maintained example.

---

## Top-level fields

| Field | Required | Type | Description |
|---|---:|---|---|
| `catalogVersion` | Yes | int | Schema version for compatible migrations. The shipped catalog is version 2, which permits `downloadTarget` as an alternative to a direct `patch`. |
| `storeName` | Recommended | string | Human-readable name. The shipped catalog uses `Main Store`. |
| `lastUpdated` | Yes | ISO 8601 UTC string | Catalog generation timestamp. |
| `hacks` | Yes | array | Hack records described below. |

## Required hack fields

Each record needs the following fields. It must provide either a direct
`patch` or a `downloadTarget`; it may contain both when `downloadTarget.type`
is `direct`.

| Field | Type | Description |
|---|---|---|
| `id` | string | Stable unique slug. Do not change after publication. |
| `name` | string | Display name. |
| `description` | string | Plain-text long description. |
| `author` | string | One author or a comma-separated author list. |
| `version` | string | Patch/release version or a source update date. |
| `baseRom` | object | Required user-owned base ROM; see below. |
| `patch` **or** `downloadTarget` | object | How the user obtains the patch; see below. |

### `baseRom`

| Field | Type | Description |
|---|---|---|
| `name` | string | Human-readable base-ROM name. |
| `gameCode` | 4-char string | N64 header code, usually `CZLE` (OoT) or `NSME` (MM). |
| `versionByte` | int | Header version. Use `-1` only when the public source does not disclose it. |
| `checksums.crc32` | string | Normalized big-endian `.z64` CRC32. It may be empty only when the source does not publish it; the app then falls back to the matching imported game code. |
| `checksums.md5` / `checksums.sha1` | string | Optional additional known checksums. |

### Direct `patch`

Use this for a direct `.bps`, `.ips`, `.xdelta`, or `.zip` URL.

| Field | Type | Description |
|---|---|---|
| `url` | HTTPS URL | Patch file or archive URL. |
| `filename` | string | File name, or the expected patch entry inside a ZIP. |
| `size` | int | Bytes when known. `0` means the public source did not publish a size. |
| `checksums.crc32` | string | Patch CRC32 when known; an empty string means it was not published. |
| `checksums.md5` / `checksums.sha1` | string | Optional stronger patch digests. |

For valid BPS files the full-file CRC32 is always `2144df1c`, so include MD5
or SHA-1 whenever independently verified. Do not invent a checksum or size.

### `downloadTarget`

Use `downloadTarget` as an alternative to `patch` when the public entry points
to a release page or another non-direct source. This preserves a complete
catalog record without pretending that checksum or size data is known.

| Type | Required fields | Behavior in the app |
|---|---|---|
| `direct` | `patch` | Downloads through the normal patch pipeline. Its nested `patch` has the same shape as the direct `patch` above. |
| `github` | `repoUrl` | Finds a compatible patch asset from GitHub Releases at install time. If resolution fails, opens the release page in the browser. |
| `external` | `url` | Opens the publisher/source page in the browser so the user can obtain the file there. |

```json
"downloadTarget": {
  "type": "github",
  "repoUrl": "https://github.com/example/project/releases"
}
```

An imported Hylian Modding direct link may therefore have `size: 0` and an
empty checksum, while GitHub and external links have no `patch` object at all.
The app still validates BPS/Xdelta source data during the explicit install
flow; source metadata must never be represented as a verified download digest.

## Rich metadata

These optional fields make the detail screen, filters, and future catalog
tools more useful. Preserve source values as public metadata and use absolute
HTTPS URLs for remote media.

| Field | Type | Description |
|---|---|---|
| `coverImageUrl` | URL | Primary cover/thumbnail image. |
| `screenshots` | array of URLs | Ordered gallery images. Empty entries are omitted during import. |
| `tags` | array of strings | Search/filter labels. The importer includes the source category and completion status. |
| `supportedGames` | string | Source-declared game(s), for example `OoT` or `MM`. |
| `completionStatus` | string | Source-declared status, such as demo, complete, or in progress. |
| `compatibility` | string | Source compatibility notes; do not infer this from the base-ROM fallback. |
| `lastUpdated` | ISO 8601 string | Source-record update time, distinct from the catalog's top-level timestamp. |
| `changelog` | array of `{ "date", "content" }` | Source release/change notes. Either property may be absent. |
| `compatibleCores` | array of strings | Known supported core ids: `mupen64plus_next_gles3` and/or `parallel_n64`. |
| `videos` | array of URLs | Optional source video links. |
| `ocarinaSongs` | array | Optional per-hack Auto-Ocarina extension. |
| `retroAchievements` | object | Optional known RetroAchievements metadata. |

### Provenance metadata

Imported records retain enough provenance to refresh them safely and credit the
source. These fields are metadata only; they never grant permission to
redistribute a base ROM or patch.

| Field | Type | Description |
|---|---|---|
| `importSource.provider` | string | Source name, for example `Hylian Modding`. |
| `importSource.catalogId` | string | Imported source collection (`mods` or a competition id). |
| `importSource.modUrl` | URL | Public `mod.json` URL used to obtain the record. |
| `sourceMetadata.timestamp` | number or string | Source timestamp when supplied. |
| `sourceMetadata.isUpdate` | boolean | Source update flag when supplied. |

`storeId` and `sourceCatalogId` remain `picks` for the shipped single-store
catalog. They are compatibility metadata, not a store selector.

## Importing Hylian Modding metadata

Run the importer from the repository root:

```bash
python3 tools/import_hylian_modding.py
```

It reads only public Hylian Modding indexes and per-mod `mod.json` documents:
the main collection plus the 2025 Crossover, 2024 Horror, 2023 Escape Room,
and HM Jam 1 collections. It does **not** download, cache, or commit patch
files or base ROMs.

Useful options:

```bash
python3 tools/import_hylian_modding.py --dry-run
python3 tools/import_hylian_modding.py --catalog path/to/catalog.json
python3 tools/import_hylian_modding.py --timeout 30 --workers 6
```

The importer updates prior Hylian Modding-derived records, preserves manually
verified direct-patch size/checksums when the URL did not change, leaves curated
Main Store records authoritative on an id collision, sorts new records,
and refreshes the top-level `lastUpdated`. Review the resulting JSON and
validate it before committing.

## Author contribution guidance

Submit a hack only through the repository's **Hack submission** issue form.
Do not open a pull request to add a hack or edit `catalog/catalog.json` for a
submission. Maintainers review the issue, validate the supplied metadata, and
curate accepted records into the catalog.

- Host catalogs and media over HTTPS.
- Never add a base ROM, BIOS, proprietary asset, or a fabricated checksum.
- Prefer stable direct patch URLs with independently verified size and MD5 or
  SHA-1. Use `downloadTarget` instead when the publisher exposes only a release
  page or external download page.
- Keep descriptions and changelogs attribution-friendly, and retain
  `importSource`/`sourceMetadata` on imported records.

A malformed individual entry is skipped at parse time so one record cannot
break the entire store.
