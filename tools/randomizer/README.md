# OoTR Settings Schema Generator

This directory contains the offline tooling that produces the randomizer
settings schema asset consumed by the Android app.

## What it does

`generate_settings_schema.py` parses the OoT Randomizer project's
`SettingsList.py` (and `SettingsListTricks.py` for the logic-trick tables) and
emits `app/src/main/assets/randomizer/oot_settings_schema.json`. That JSON
drives the schema-driven settings form in `RandomizerActivity` — every shared
generator option becomes a row, grouped into category tabs.

The script uses only the Python standard library (`ast`, `json`, `pathlib`),
so it runs anywhere without third-party packages.

## How to regenerate

```bash
curl -sL -o /tmp/SettingsList.py \
  https://raw.githubusercontent.com/TestRunnerSRL/OoT-Randomizer/Dev/SettingsList.py
curl -sL -o /tmp/SettingsListTricks.py \
  https://raw.githubusercontent.com/TestRunnerSRL/OoT-Randomizer/Dev/SettingsListTricks.py
python3 tools/randomizer/generate_settings_schema.py \
  /tmp/SettingsList.py /tmp/SettingsListTricks.py \
  app/src/main/assets/randomizer/oot_settings_schema.json
```

## Scope decisions

* **Shared only.** Only settings declared with `shared=True` are emitted.
  Internal/non-shared settings (seed, output paths, GUI-only buttons, web-only
  fields, per-output cosmetics) are skipped.
* **Excluded by name.** Multiworld (`world_count`, `player_num`) and file
  inputs (`distribution_file`, `cosmetic_file`) are never emitted; the seed
  string is handled separately by the UI.
* **List type.** `MultipleSelect` / `SearchBox` settings are emitted as
  `type: "list"` when their choices resolve statically. The detailed logic
  tricks (`allowed_tricks`, `advanced_allowed_tricks`) resolve their choices
  from `SettingsListTricks.py`.
* **Cosmetic flag.** Options flagged `cosmetic=True` carry `"cosmetic": true`
  in the asset; the app strips them from the submitted settings map unless the
  user enables cosmetics. (In the current OoTR `Dev` snapshot all cosmetic
  options are non-shared, so none appear in the asset — the plumbing remains.)
* **Category assignment** is a documented heuristic: explicit core-rule names
  (`logic_rules`, `bridge`, `lacs_*`, `ganon_bosskey_*`, `triforce_*`, …) go to
  `main`; then the `cosmetic` flag; then name-keyword matching
  (`open_*`, `shuffle_*`, `dungeon*`/`*mq*`, `starting_*`, `timesaver*`/`fast*`/
  `skip*`, `trick*`). OoTR does not expose a single canonical category field.

## Skipped settings (programmatic / non-shared)

The following were intentionally omitted from the asset. Most are non-shared
(per-output or web-only) or have choices that are computed at runtime and
cannot be resolved statically. They are listed for transparency; the app does
not need them for v1 generation.

### Internal / non-shared holders
`cosmetics_only`, `check_version`, `output_settings`, `patch_without_output`,
`generating_patch_file`, `language`, `web_wad_file`, `web_common_key_file`,
`web_common_key_string`, `web_wad_channel_id`, `web_wad_channel_title`,
`web_wad_legacy_mode`, `web_output_type`, `web_persist_in_cache`,
`generate_from_file`, `enable_distribution_file`, `enable_cosmetic_file`,
`rom`, `output_dir`, `web_id`, `patch_file`, `count`, `repatch_cosmetics`,
`create_cosmetics_log`, `create_patch_file`, `create_compressed_rom`,
`create_wad_file`, `create_uncompressed_rom`, `wad_file`, `wad_channel_id`,
`wad_channel_title`, `bingosync_url`, `default_targeting`, `display_dpad`,
`dpad_dungeon_menu`, `correct_model_colors`, `randomize_all_cosmetics`,
`uninvert_y_axis_in_first_person_camera`, `input_viewer`, `model_adult`,
`model_adult_filepicker`, `model_child`, `model_child_filepicker`,
`kokiri_color`, `goron_color`, `zora_color`, `silver_gauntlets_color`,
`golden_gauntlets_color`, `mirror_shield_frame_color`, `heart_color`,
`magic_color`, `a_button_color`, `b_button_color`, `c_button_color`,
`start_button_color`, `navi_color_default_inner`, `navi_color_default_outer`,
`navi_color_enemy_inner`, `navi_color_enemy_outer`, `navi_color_npc_inner`,
`navi_color_npc_outer`, `navi_color_prop_inner`, `navi_color_prop_outer`,
`bombchu_trail_color_inner`, `bombchu_trail_color_outer`,
`boomerang_trail_color_inner`, `boomerang_trail_color_outer`,
`sword_trail_color_inner`, `sword_trail_color_outer`, `sword_trail_duration`,
`randomize_all_sfx`, `disable_battle_music`,
`speedup_music_for_last_triforce_piece`, `slowdown_music_when_lowhp`,
`custom_music_directorypicker`, `background_music`, `display_custom_song_names`,
`fanfares`, `ocarina_fanfares`, `credits_music`, `sfx_ocarina`, `sfx_bombchu_move`,
`sfx_hover_boots`, `sfx_iron_boots`, `sfx_boomerang_throw`, `sfx_hookshot_chain`,
`sfx_arrow_shot`, `sfx_slingshot_shot`, `sfx_magic_arrow_shot`, `sfx_explosion`,
`sfx_link_adult`, `sfx_link_child`, `sfx_navi_overworld`, `sfx_navi_enemy`,
`sfx_horse_neigh`, `sfx_cucco`, `sfx_daybreak`, `sfx_nightfall`,
`sfx_menu_cursor`, `sfx_menu_select`, `sfx_low_hp`, `sfx_silver_rupee`,
`sfx_get_small_item`.

### Excluded by name (multiworld / file inputs)
`world_count`, `player_num`, `distribution_file`, `cosmetic_file`.

### Non-literal choices (computed at runtime, not resolvable statically)
`disabled_locations`, `starting_equipment`, `starting_inventory`,
`starting_songs`, `hint_dist`.

These reference helper modules (`StartingItems`, `Hints`, `Models`) whose
values are built dynamically; they are omitted from v1 rather than guessed.
