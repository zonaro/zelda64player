# Hack Store & Catalog — Feature Deep Dive

## 1. Overview

The Hack Store fetches the GitHub-hosted **Main Store** catalog, lets users
browse, download, and install Zelda 64 hacks, and validates the required
user-supplied base ROM before patching. There is one built-in store only:
**Main Store**.

The catalog includes hand-curated entries plus public metadata imported from
Hylian Modding. Hylian Modding remains visible as record provenance and credit;
it is not a second store, a selectable source, or a separate catalog in the UI.

## 2. Package

| File | Responsibility |
|---|---|
| `store/CatalogFetcher.kt` | OkHttp fetch, conditional GET (ETag/Last-Modified), cache, and merge of the default Picks catalog with optional user-added Picks URLs. |
| `store/StoreDefinitions.kt` | Defines the single Picks store and optional custom Picks catalog URLs. |
| `store/DownloadManager.kt` | Download progress, patch/base-ROM validation, patching, and install persistence. |
| `store/GitHubPatchResolver.kt` | Resolves a GitHub Releases page to a compatible patch asset at install time. |
| `store/ui/HackDetailDialog.kt` | Displays rich metadata and selects the direct/GitHub/external download behavior. |
| `work/CatalogRefreshWorker.kt` | Refreshes the catalog every 12 hours when a network is available. |
| `repositories/Storage.kt` | Per-hack ROM, SRAM, and save-state paths. |

## 3. Catalog and download behavior

`catalog/catalog.json` is the canonical shipped source and declares
`storeName: "Main Store"`. User-added catalog URLs are treated as extra
Picks sources for backwards compatibility; they never create a new built-in
store selector.

Every valid record supplies the regular identity and base-ROM fields plus
either `patch` or `downloadTarget`:

- A direct `patch` / `downloadTarget.type = "direct"` enters the normal
  download and patch queue.
- `downloadTarget.type = "github"` resolves a compatible GitHub Release asset
  at install time. If that cannot be resolved, the release page opens in a
  browser.
- `downloadTarget.type = "external"` opens the publisher page in a browser.

Do not fabricate patch size or checksum data for a source that does not publish
them. Imported direct links can use `size: 0` and an empty checksum; page-based
sources should use a GitHub or external download target instead. An empty base
ROM CRC uses the source-declared game code as the matching fallback, while the
explicit install flow still validates the supplied patch/base data.

See [`../docs/CATALOG.md`](../docs/CATALOG.md) for the full JSON contract.

## 4. Hylian Modding metadata import

`tools/import_hylian_modding.py` imports only public JSON metadata from Hylian
Modding's main index and the 2025 Crossover, 2024 Horror, 2023 Escape Room, and
HM Jam 1 competition indexes. It never downloads, commits, or distributes
patches or base ROMs.

Run it from the repository root:

```bash
python3 tools/import_hylian_modding.py
```

Useful commands:

```bash
python3 tools/import_hylian_modding.py --dry-run
python3 tools/import_hylian_modding.py --catalog path/to/catalog.json
python3 tools/import_hylian_modding.py --timeout 30 --workers 6
```

For every imported mod, retain as much public information as available:

- authors, description, category tags, supported game, compatibility, and
  completion status;
- cover thumbnail and an ordered screenshot gallery;
- source update time, change log, timestamp, and update flag;
- `importSource` attribution (`provider`, `catalogId`, and source `modUrl`);
- the original download link represented as a direct, GitHub, or external
  `downloadTarget`.

On refresh, imported records are updated by id. Manually verified direct-patch
size/checksums survive when the direct URL has not changed. A curated Picks
record wins an id collision, new imports are sorted, and the catalog's
`lastUpdated` is refreshed. Review the generated JSON before committing it.

## 5. Base ROM management

- Users import legally owned OoT/MM ROMs through **Settings → Import Base ROM**.
- `BaseRomRepository` normalizes and validates headers/checksums, then persists
  metadata in `filesDir` and the normalized ROM in `cacheDir/base_roms/`.
- An exact declared CRC is preferred. If Hylian Modding's public record does
  not provide it, the importer marks it unknown and the app uses the declared
  game code as a fallback selection aid.
- See [VANILLA_GAMES.md](VANILLA_GAMES.md) for using imported base ROMs directly.

## 6. Legal boundary

The app, catalog, and importer must never host, fetch, cache for distribution,
or include base ROMs. Public metadata and patch links do not change that rule.
Retain source credit and do not treat an external download page as a verified
patch artifact.

