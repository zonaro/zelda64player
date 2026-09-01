# Vanilla Games in Library

## Overview
Users can import their **legally-owned base ROMs** (OoT / MM) into the Library and play them directly — without any hack/patch. These appear as first-class Library entries alongside store hacks, with distinct visual treatment (family-colored badges) to differentiate from hacks.

## Identity & Resolution
- **Source:** `BaseRomLibrarySource` provides vanilla base-ROM entries from user imports (distinct from `HackLibrarySource` which reads the store catalog).
- **ID scheme:** Vanilla entries use IDs prefixed `vanilla_<crc32>` (e.g., `vanilla_EC70113C` for OoT 1.0). This avoids collision with hack `canonicalId`s.
- **Resolution:** `GameRomResolver.VANILLA_PREFIX = "vanilla_"`; when an entry ID starts with this prefix, the resolver returns the user's imported ROM path from `Storage.rom` (no patching, no download).
- **Fallback:** If the imported ROM is missing/corrupted, `Storage.rom` returns null → Library shows a "Re-import ROM" prompt.

## Library Presentation
- **Order:** Vanilla games appear **first** in the Library Home row and grid (before store hacks).
- **Badges:**
  - A `VANILLA` badge (gray/neutral) marks non-hack entries.
  - A small **robot/controller icon** (Dolfi asset) indicates "base game" vs hack.
  - **Family colors:** OoT = yellow (`#FFC107`), MM = purple (`#9C27B0`) — applied to the card accent / badge border.
- **`HackLibraryEntry.family`:** Each entry carries a `family` field (`OOT` / `MM` / `CUSTOM`) used for coloring and filtering.
- **Context menu:** Vanilla entries **omit** hack-specific actions (e.g., "View on Store", "Check for Updates"); they show only Play / Re-import / Delete-import.

## RetroAchievements for Vanilla
- Vanilla games **can** have RA support: at first play, the app computes the RA hash (via `RaHashService`) and resolves `gameId` lazily (no install step). The resolved `RaGameMetadata` is cached per `vanilla_<crc32>` ID.
- Store hacks and vanilla games share the same `AchievementsActivity` / `InGameRaViewModel` paths.

## Tests
- `BaseRomLibrarySourceTest` — verifies vanilla entries are produced from imported ROMs with correct `vanilla_<crc32>` IDs and family.
- `GameRomResolverTest` — verifies `VANILLA_PREFIX` routing returns the imported ROM path and that hack IDs route to patched storage.
