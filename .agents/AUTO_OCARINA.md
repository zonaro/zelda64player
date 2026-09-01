# Auto-Ocarina — In-Game Song Player

## Overview
Auto-Ocarina is a HUD overlay that **automatically plays Ocarina songs** from the in-game pause menu, so users don't need to manually input notes. It works for both Ocarina of Time (OoT) and Majora's Mask (MM) base games and any hack built on them.

## Game Detection
The HUD detects which game is running via the ROM's internal title string:
- **OoT:** title starts with `CZL*` (e.g., `CZL-OoT-1.0`)
- **MM:** title starts with `NZL*` (US) or `NSM*` (JP/EU)

Detection uses `LibretroDroid.getGameTitle()` (or the loaded ROM's header) at game start; the appropriate song catalog is selected.

## Package (`autoocarina/`)
```
autoocarina/
├── OcarinaNote.kt          # Note enum: LEFT_A, RIGHT_A, UP_C, DOWN_C, LEFT_C, RIGHT_C, X, Y, Z, START, etc. + timing
├── OcarinaSong.kt          # Song model: id, name, game (OOT/MM), notes: List<OcarinaNote>, bpm/timing
├── OcarinaSongCatalog.kt   # Built-in catalogs: OoT (12 songs), MM (11 songs); loads from assets/ocarina/*.json
├── OcarinaMacroPlayer.kt   # Coroutine sequencer: sends each note to game input at ~330ms intervals; cancellable
└── ui/
    └── OcarinaHudView.kt    # Overlay View: shows song name + note progress; drawn over GLRetroView
```

## Behavior
- Triggered from the in-game pause menu (GameActivity): a "Tocar Ocarina" (Play Ocarina) option lists available songs for the detected game.
- On selection, `OcarinaMacroPlayer` plays notes sequentially, injecting them as controller inputs (via the same input injection path used by RadialGamePad / physical controller mapping).
- **Timing:** ~330ms per note (tunable per song). The HUD shows current note + progress.
- **Cancellable:** pressing Back or any menu action cancels the sequence.
- Does not require the user to own a physical Ocarina peripheral; purely software-driven input injection.

## Catalog Integration
- `OcarinaSongCatalog` reads built-in songs from `assets/ocarina/oot.json` and `assets/ocarina/mm.json`.
- Hack catalogs may add a `ocarinaSongs` field (array of song objects) to extend the available list per hack.
- Songs are matched to the detected game; cross-game songs are filtered out.

## Notes
- No copyrighted song data is distributed; built-in songs are game-mechanic note sequences (fair use of game input patterns). User-provided custom songs stored locally.
- Fully i18n: song names and HUD strings in `strings.xml` (pt-BR/en/es).
