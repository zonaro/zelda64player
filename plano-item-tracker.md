
## ⚠️ Feature Blocked — Removed Randomizer Dependency

**Status**: The item tracker feature was designed with OoT Randomizer seed dependency (via WebView/Rule 17). Since the Randomizer feature has been completely removed from this project, the item tracker integration is **BLOCKED** and requires rework.

**Current State**: 
- Item tracker was designed to track checks within Randomizer seeds
- No Randomizer seeds are generated, distributed, or supported in this app
- Auto-tracking was scoped to OoT 1.0 NTSC-U as the randomizer base ROM

**Required Rework**:
1. Define item tracker scope independent of Randomizer seeds
2. Determine base ROM support (vanilla OoT/MM only, or other patterns)
3. Re-evaluate memory access patterns without Randomizer seed context
4. Update MVP scope to focus on vanilla gameplay tracking only

**Alternative**: Item tracker can be re-implemented as a vanilla OoT/MM checklist feature, completely decoupled from any Randomizer functionality.

# Item Tracker Integration Plan — Zelda 64 Player

**Generated:** 2026-08-24
**Author:** Lobby (orchestrator) + Bruce (implementation lead)
**Status:** Draft for review — NOT yet merged into `plano.md`

---

## 1. Context & Motivation

checks during a race. Two reference repos were analyzed:

| Repo | What it is | Reusable here |
|------|-----------|---------------|
| [coavins/EmoTrackerPacks](https://github.com/coavins/EmoTrackerPacks) | EmoTracker pack (Lua) with **autotracking** for BizHawk. Maps N64 RAM addresses → items/checks. | **Memory map** (addresses + offsets) for OoT autotracking. |
| [Draeko/ootr_gst](https://github.com/Draeko/ootr_gst) | C# WinForms **manual** Gossip-Stones tracker. Drag/drop items, songs, hints, WOTH, barren, upgrades, timer. | **UI/UX model** (Gossip Stones style) for the manual tracker. |

The app already exposes core RAM via `GLRetroView.getMemoryRegion(id)` →
`retro_get_memory_data`/`retro_get_memory_size` (LibretroDroid `getMemoryRegion`,
zero-copy, mutex-guarded). This is the same path RetroAchievements uses.

---

## 2. User Decisions (confirmed)

1. **MVP scope:** Tracker Manual Completo (5 tabs) **+ Auto-Tracking for OoT 1.0 NTSC-U**.
2. **Visibility:** Configurable by user — setting with options: *Always / Never*.
3. **Majora's Mask:** Separate tracker per game (OoT Tracker vs MM Tracker), independent databases.
4. **Auto-Tracking:** Yes, in the MVP, scoped to OoT 1.0 NTSC-U/J ().

---

## 3. Architecture Overview

```
GameActivity (in-game menu = SwitchDialog)
└── "Item Tracker" menu item
    └── TrackerDialogFragment (SwitchDialog container, ViewPager2 + tabs)
        ├── ItemsTab       (grid of item icons, tap to toggle obtained)
        ├── LocationsTab   (expandable list by region, checkbox per check)
        ├── SongsTab       (12 OoT / 11 MM songs, drag→Gossip Stone hints)
        ├── HintsTab       (WOTH x5 autocomplete, Barren x3, Gossip Stones)
        └── UpgradesTab    (bottles, strength, hookshot, scale, ocarina, etc.)

AutoTrackerEngine (coroutine, only when enabled + OoT 1.0 detected)
└── reads GLRetroView.getMemoryRegion(0) each frame
    └── maps N64 RAM offsets → TrackerState (items/checks/flags)
```

### Package layout (new)
```
br.com.redclaw.zelda64player.tracker/
├── model/
│   ├── TrackerGame.kt            # enum OOT / MM
│   ├── TrackerItem.kt            # item id, name key, icon res, max count
│   ├── TrackerLocation.kt        # region, name key, isChecked
│   ├── TrackerSong.kt            # song id, name key, icon res
│   ├── TrackerHint.kt            # type (WOTH/BARREN/STONE), text, location
│   ├── TrackerUpgrade.kt         # upgrade chain (e.g. strength 0→3)
│   ├── TrackerState.kt           # full mutable state (per seed/game)
│   └── TrackerSettings.kt        # visibility mode, auto-track toggle, layout
├── data/
│   ├── OotItemDatabase.kt        # static OoT items/locations/songs (from EmoTrackerPacks items/ + locations/)
│   ├── MmItemDatabase.kt         # static MM items/locations/songs
│   ├── TrackerRepository.kt      # JSON persistence in filesDir (per seedId or game)
│   ├── SpoilerLogParser.kt       # parse OoTR spoiler log JSON → pre-fill state
│   └── TrackerExporter.kt        # export/import JSON (EmoTracker-compatible-ish)
├── autotrack/
│   ├── AutoTrackerEngine.kt      # coroutine loop, reads memory region
│   ├── OotMemoryMap.kt           # N64 RAM offsets → item/flag (1.0 NTSC-U/J)
│   └── RomVersionDetector.kt     # read ROM header / RAM signature → version
├── logic/
│   └── HintInterpreter.kt        # map spoiler hints → WOTH/Barren/Stone
└── ui/
    ├── TrackerDialogFragment.kt  # SwitchDialog + ViewPager2
    ├── TrackerViewModel.kt       # state holder, saved-state aware
    ├── tabs/
    │   ├── ItemsTab.kt
    │   ├── LocationsTab.kt
    │   ├── SongsTab.kt
    │   ├── HintsTab.kt
    │   └── UpgradesTab.kt
    └── components/
        ├── GossipStoneView.kt    # tappable/draggable stone
        ├── ItemIconView.kt       # item icon with obtained/locked states
        ├── LocationRowView.kt    # region row + check
        └── TrackerTimerView.kt   # integrated run timer
```

---

## 4. Auto-Tracking Feasibility (deep dive)

### 4.1 Memory access
- `GLRetroView.getMemoryRegion(0)` returns a `ByteBuffer` aliasing the mupen64plus
  **System Bus / RDRAM** (N64 virtual `0x80000000` maps to offset `0x00000000` in the buffer).
- EmoTrackerPacks autotracking.lua watches (all in the `0x80xxxxxx` range):
  - `0x8011A602` Magic Meter (1 byte)
  - `0x8011A642` Item Data 1 (0x1A = 26 bytes)
  - `0x8011A66A` Item Data 2 (4 bytes)
  - `0x8011A671` Quest Data (0x12 = 18 bytes)
  - `0x8011A68F` Key Data (0x13 = 19 bytes)
  - `0x8011A6A4`+ Save Context Dungeons 1-5
  - `0x8011AC54`+ Save Context Overworld 1-2
  - `0x8011B46C` Skulltula Data (0x18)
  - `0x8011B48C` INF Tables (event flags, 0x78)
  - `0x8040000C` Autotracker context, `0x80400CBC` Free Scarecrow
- These are **N64 virtual addresses**; subtract `0x80000000` to get the RDRAM offset
  (e.g. `0x8011A642 → 0x11A642`).

### 4.2 Version sensitivity
Addresses differ across OoT versions (1.0 / 1.1 / 1.2 / MQ / GC / VC / iQue).
MVP autotracking map targets **only 1.0 NTSC-U/J**. `RomVersionDetector` reads the
ROM header `gameCode` (`CZL*` = OoT family) and a RAM signature to confirm 1.0 before
enabling autotracking.

### 4.3 Race-rule compliance
Auto-tracking is **banned in official SRL/Racetime races** (unfair vs console).
Therefore:
- Auto-tracking is **OFF by default**.
- Enabling it shows a warning dialog citing race rules.
- Manual tracking remains the primary, race-legal path.

### 4.4 Performance
Reading ~0.5 KB/frame from a direct ByteBuffer is negligible. The engine runs on a
coroutine tied to the emulation lifecycle, throttled to ~10 Hz (not every frame) to
save battery.

---

## 5. Manual Tracker (UI model from ootr_gst)

Tabs (Switch UI style, `SwitchDialog` chrome, `SwitchFocusBorder` for D-pad, `SfxManager`):

| Tab | Content | Source model |
|-----|---------|--------------|
| **Items** | Square icon grid (1:1 `SwitchGameCard` style). Tap = obtained. Count badges for stackable (rupees, skulls, tokens). | ootr_gst item panel + EmoTrackerPacks `items/` |
| **Locations** | Regions (Kokiri Forest, Dodongo, etc.) → expandable checks with checkbox + name. | EmoTrackerPacks `locations/` |
| **Songs** | 12 OoT / 11 MM songs. Long-press drag onto a Gossip Stone to record where found (non-songsanity). | ootr_gst SongsTab |
| **Hints** | WOTH (5 autocomplete slots from `oot_places.json`), Barren (3), free Gossip Stones (drag items/songs). | ootr_gst HintsTab |
| **Upgrades** | Visual progression: bottles 0→4, strength 0→3, hookshot→longshot, scale 0→2, ocarina 0→2, wallet, magic, etc. | ootr_gst upgrade lists |

Timer: integrated `TrackerTimerView` (start/pause/reset), persisted per session.

---

## 6. Integration Points

| Existing component | Change |
|-------------------|--------|
| `GameActivity` in-game menu (`SwitchDialog`) | Add "Item Tracker" row → launches `TrackerDialogFragment` |
| `RomHeader.gameCode` | Reused by `RomVersionDetector` |
| `GLRetroView.getMemoryRegion` | Reused by `AutoTrackerEngine` (already exists, zero-copy) |

Tracker visibility logic:
- `Always` → menu item shown for every OoT/MM game.
- `Never` → hidden entirely.

---

## 7. Roadmap & Agent Delegation

### Phase 1 — Foundation (Bruce + Dolfi + Calamari)
| Task | Agent | Notes |
|------|-------|-------|
| 1.1 `tracker/model/*` data classes | Bruce | Immutable, DRY |
| 1.2 `OotItemDatabase` + `MmItemDatabase` | Bruce | Port from EmoTrackerPacks `items/`,`locations/` + ootr_gst lists |
| 1.3 `TrackerRepository` (JSON) | Bruce | Per seedId / per game |
| 1.4 SVG item/song/upgrade icons | Dolfi | Original, CC0, Switch palette (OoT yellow / MM purple) |
| 1.5 Validate OoT 1.0 NTSC-U/J checksums + address map | Calamari | Confirm offsets vs No-Intro/Redump |

### Phase 2 — Manual UI (Bruce + Dolfi)
| Task | Agent | Notes |
|------|-------|-------|
| 2.1 `TrackerDialogFragment` (SwitchDialog + ViewPager2) | Bruce | |
| 2.2 `ItemsTab` | Bruce | |
| 2.3 `LocationsTab` | Bruce | |
| 2.4 `SongsTab` + `GossipStoneView` | Bruce | |
| 2.5 `HintsTab` (WOTH/Barren/Stones) | Bruce | |
| 2.6 `UpgradesTab` | Bruce | |
| 2.7 Tab backgrounds, hint icons, stone sprites | Dolfi | |

| Task | Agent | Notes |
|------|-------|-------|
| 3.1 `SpoilerLogParser` (OoTR JSON) | Bruce | |
| 3.2 Auto-fill state on seed launch | Bruce | |
| 3.3 "Import Spoiler Log" button | Bruce | |
| 3.4 Persist per seed | Bruce | |

### Phase 4 — Auto-Tracking (Bruce + Calamari)
| Task | Agent | Notes |
|------|-------|-------|
| 4.1 `AutoTrackerEngine` coroutine | Bruce | 10 Hz poll of `getMemoryRegion(0)` |
| 4.2 `OotMemoryMap` (1.0 NTSC-U/J) | Bruce | From EmoTrackerPacks autotracking.lua |
| 4.3 `RomVersionDetector` | Bruce | Header + RAM signature |
| 4.4 Toggle + race-rule warning dialog | Bruce | Off by default |
| 4.5 Validate addresses across supported versions | Calamari | |

### Phase 5 — Polish & Docs (Bruce + Chululu + Wally)
| Task | Agent | Notes |
|------|-------|-------|
| 5.1 Timer + export/import | Bruce | |
| 5.2 Settings UI (visibility, autotrack) | Bruce | |
| 5.3 Visual QA (screenshots, focus nav, a11y) | Chululu | Switch UI compliance |
| 5.4 Strings pt-BR/en/es + README section | Wally | |

---

## 8. Effort Estimate

| Phase | Weeks | Complexity |
|-------|-------|------------|
| 1 Foundation | 1–2 | Medium (large static DB) |
| 2 Manual UI | 2–3 | High (5 tabs, drag/drop, Switch UI) |
| 4 Auto-Tracking | 2–3 | High (memory, version detection) |
| 5 Polish & Docs | 1–2 | Low–Medium |
| **Total** | **7–12** | High |

---

## 9. Open Questions / Risks

- **MM autotracking:** Out of MVP scope (no verified MM RAM map yet). MM tracker is manual-only at launch.
- **EmoTracker export compat:** Full `.emotracker` pack format is complex; MVP exports a simple JSON. Optional future work.
- **Memory region stability:** `getMemoryRegion(0)` must stay valid across pause/resume; current RA integration already relies on this, so risk is low.
- **Race legality:** Auto-tracking must never be on by default; document clearly in README and in-app warning.

---

## 10. References

- EmoTrackerPacks autotracking.lua: https://github.com/coavins/EmoTrackerPacks/blob/master/ootrando_overworldmap_hamsda/scripts/autotracking/autotracking.lua
- ootr_gst Form1.cs (UI model): https://github.com/Draeko/ootr_gst/blob/master/TrackerOOT/Form1.cs
- OoT Save Format (cloudmodding): https://wiki.cloudmodding.com/oot/Save_Format
- N64 RAM autotracking addresses (EmoTrackerPacks): see `scripts/autotracking/auto_*.lua`
- Zelda64Player `GLRetroView.getMemoryRegion`: `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:199`
- Zelda64Player `LibretroDroid::getMemoryRegion`: `libretrodroid/src/main/cpp/libretrodroid.cpp:187`
