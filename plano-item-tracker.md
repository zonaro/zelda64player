
## ✅ Implemented — Manual Item Tracker + Timer (no auto-tracking)

**Status**: Auto-tracking was **removed** from this plan. The feature is implemented as a
**manual, vanilla OoT/MM checklist tracker** with an integrated run timer, fully decoupled
from any Randomizer functionality (which was removed from the project).

**Scope delivered**:
- 5-tab Switch-styled dialog: Items, Locations, Songs, Hints, Upgrades
- Per-hack persistence (each ROM hack keeps its own independent checklist + timer) via JSON in `filesDir`, keyed by `hackId` (falls back to the game name when no hack id is available)
- Integrated run timer (start/pause/reset), persisted per hack and kept counting across dialog close / app restart
- No core RAM is read — manual tracking only (race-legal)
- Item icons: **temporarily copied as PNG** placeholders into `app/src/main/res/drawable-nodpi/`:
  - OoT items → from `Draeko/ootr_gst` (`TrackerOOT/Resources/`)
  - MM items → from `griesenj/ZeldaTracker` (`src/img/`, the colored ` 1.png` variants),
    sanitized with an `mm_` prefix; missing MM icons fall back to the OoT drawables
    (e.g. `iron_boots`, `hover_boots`, `magic`).
  These are **placeholder assets** and will be replaced by original icons later (see §Icons).

# Item Tracker Integration Plan — Zelda 64 Player

**Generated:** 2026-08-24
**Author:** Lobby (orchestrator) + Bruce (implementation lead)
**Status:** Draft for review — NOT yet merged into `plano.md`

---

## 1. Context & Motivation

checks during a race. Two reference repos were analyzed:

| Repo                                                                  | What it is                                                                                                  | Reusable here                                                 |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| [coavins/EmoTrackerPacks](https://github.com/coavins/EmoTrackerPacks) | EmoTracker pack (Lua) with autotracking for BizHawk. Maps N64 RAM addresses → items/checks.                 | Item/song/upgrade lists (manual tracker content).             |
| [Draeko/ootr_gst](https://github.com/Draeko/ootr_gst)                 | C# WinForms **manual** Gossip-Stones tracker. Drag/drop items, songs, hints, WOTH, barren, upgrades, timer. | **UI/UX model** (Gossip Stones style) for the manual tracker. |

---

## 2. User Decisions (confirmed)

1. **MVP scope:** Tracker Manual Completo (5 tabs) — **manual only, no auto-tracking**.
2. **Visibility:** Configurable by user — setting with options: *Always / Never*.
3. **Majora's Mask:** Separate tracker per game (OoT Tracker vs MM Tracker), independent databases.
4. **Auto-Tracking:** **Removed.** Tracking is manual only (race-legal, no core RAM access).

---

## 3. Architecture Overview

```
GameActivity (in-game menu = SwitchDialog)
└── "Item Tracker" menu item
    └── TrackerDialogFragment (SwitchDialog container, FragmentTransaction tabs)
        ├── ItemsTab       (grid of item icons, tap to toggle obtained)
        ├── LocationsTab   (expandable list by region, checkbox per check)
        ├── SongsTab       (12 OoT / 11 MM songs, drag→Gossip Stone hints)
        ├── HintsTab       (WOTH x5, Barren x3, Gossip Stones)
        └── UpgradesTab    (strength, bombs, bow, wallet, scale, magic, etc.)
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
│   └── TrackerSettings.kt        # visibility mode (Always / Never)
├── data/
│   ├── OotItemDatabase.kt        # static OoT items/locations/songs
│   ├── MmItemDatabase.kt         # static MM items/locations/songs
│   ├── TrackerRepository.kt      # JSON persistence in filesDir (per hack, keyed by hackId)
│   └── TrackerExporter.kt        # export/import JSON
├── logic/
│   └── HintInterpreter.kt        # map spoiler hints → WOTH/Barren/Stone
└── ui/
    ├── TrackerDialogFragment.kt  # SwitchDialog + FragmentTransaction tabs
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

## 5. Manual Tracker (UI model from ootr_gst)

Tabs (Switch UI style, `SwitchDialog` chrome, `SwitchFocusBorder` for D-pad, `SfxManager`):

| Tab           | Content                                                                                                             | Source model                                   |
| ------------- | ------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| **Items**     | Square icon grid (1:1 `SwitchGameCard` style). Tap = obtained. Count badges for stackable (rupees, skulls, tokens). | ootr_gst item panel + EmoTrackerPacks `items/` |
| **Locations** | Regions (Kokiri Forest, Dodongo, etc.) → expandable checks with checkbox + name.                                    | EmoTrackerPacks `locations/`                   |
| **Songs**     | 12 OoT / 11 MM songs. Long-press drag onto a Gossip Stone to record where found (non-songsanity).                   | ootr_gst SongsTab                              |
| **Hints**     | WOTH (5 autocomplete slots from `oot_places.json`), Barren (3), free Gossip Stones (drag items/songs).              | ootr_gst HintsTab                              |
| **Upgrades**  | Visual progression: bottles 0→4, strength 0→3, hookshot→longshot, scale 0→2, ocarina 0→2, wallet, magic, etc.       | ootr_gst upgrade lists                         |

Timer: integrated `TrackerTimerView` (start/pause/reset), persisted per hack and resumed across dialog close / app restart.

---

## 6. Integration Points

| Existing component                           | Change                                                    |
| -------------------------------------------- | --------------------------------------------------------- |
| `GameActivity` in-game menu (`SwitchDialog`) | Add "Item Tracker" row → launches `TrackerDialogFragment` |

Tracker visibility logic:
- `Always` → menu item shown for every OoT/MM game.
- `Never` → hidden entirely.

---

## 7. Roadmap & Agent Delegation

### Phase 1 — Foundation (Bruce + Dolfi + Calamari)
| Task                                                  | Agent    | Notes                                                            |
| ----------------------------------------------------- | -------- | ---------------------------------------------------------------- |
| 1.1 `tracker/model/*` data classes                    | Bruce    | Immutable, DRY                                                   |
| 1.2 `OotItemDatabase` + `MmItemDatabase`              | Bruce    | Port from EmoTrackerPacks `items/`,`locations/` + ootr_gst lists |
| 1.3 `TrackerRepository` (JSON)                        | Bruce    | Per seedId / per game                                            |
| 1.4 SVG item/song/upgrade icons                       | Dolfi    | Original, CC0, Switch palette (OoT yellow / MM purple)           |
| 1.5 Validate OoT 1.0 NTSC-U/J checksums + address map | Calamari | Confirm offsets vs No-Intro/Redump                               |

### Phase 2 — Manual UI (Bruce + Dolfi)
| Task                                                                  | Agent | Notes |
| --------------------------------------------------------------------- | ----- | ----- |
| 2.1 `TrackerDialogFragment` (SwitchDialog + FragmentTransaction tabs) | Bruce |       |
| 2.2 `ItemsTab`                                                        | Bruce |       |
| 2.3 `LocationsTab`                                                    | Bruce |       |
| 2.4 `SongsTab` + `GossipStoneView`                                    | Bruce |       |
| 2.5 `HintsTab` (WOTH/Barren/Stones)                                   | Bruce |       |
| 2.6 `UpgradesTab`                                                     | Bruce |       |
| 2.7 Tab backgrounds, hint icons, stone sprites                        | Dolfi |       |

### Phase 5 — Polish & Docs (Bruce + Chululu + Wally)
| Task                                         | Agent   | Notes                |
| -------------------------------------------- | ------- | -------------------- |
| 5.1 Timer + export/import                    | Bruce   |                      |
| 5.2 Settings UI (visibility)                 | Bruce   |                      |
| 5.3 Visual QA (screenshots, focus nav, a11y) | Chululu | Switch UI compliance |
| 5.4 Strings pt-BR/en/es + README section     | Wally   |                      |

---

## 8. Effort Estimate

| Phase           | Weeks   | Complexity               |
| --------------- | ------- | ------------------------ |
| 1 Foundation    | 1–2     | Medium (large static DB) |
| 2 Manual UI     | 2–3     | High (5 tabs, Switch UI) |
| 5 Polish & Docs | 1–2     | Low–Medium               |
| **Total**       | **4–7** | Medium                   |

---

## 9. Open Questions / Risks

- **EmoTracker export compat:** Full `.emotracker` pack format is complex; MVP exports a simple JSON. Optional future work.
- **Race legality:** Manual tracking only — no core RAM access — so the tracker is always race-legal.

---

## 10. References

- ootr_gst Form1.cs (UI model): https://github.com/Draeko/ootr_gst/blob/master/TrackerOOT/Form1.cs
- OoT Save Format (cloudmodding): https://wiki.cloudmodding.com/oot/Save_Format
