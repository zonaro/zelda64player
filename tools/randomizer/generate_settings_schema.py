#!/usr/bin/env python3
"""Generate the OoTR randomizer settings schema asset for Zelda 64 Player.

This script parses ``SettingsList.py`` from the OoT Randomizer project
(https://github.com/TestRunnerSRL/OoT-Randomizer) and emits a compact,
Android-friendly JSON schema describing every *shared* generator setting.

The output drives the schema-driven settings form in the Randomizer feature
(``assets/randomizer/oot_settings_schema.json``). It is committed under
``tools/randomizer/`` and uses only the Python standard library so it can be
re-run on any machine without third-party dependencies.

Design notes / deliberate scope decisions (also recorded in README.md):
  * Only settings with ``shared=True`` are emitted. Internal / non-shared
    settings (seed, output_file, aliases, GUI-only buttons, etc.) are skipped.
  * Settings whose ``choices`` or ``default`` cannot be resolved statically
    (they call helper functions such as ``get_model_choices()`` or reference
    ``StartingItems``) are skipped and listed in the README so the gap is
    visible.
  * ``MultipleSelect`` / ``SearchBox`` (list-valued) settings ARE emitted as
    type ``list`` when their choices can be resolved statically. The detailed
    logic-tricks selectors (``allowed_tricks`` / ``advanced_allowed_tricks``)
    resolve their choices from ``SettingsListTricks.py`` (fetched alongside).
  * Multiworld settings (``world_count`` / ``player_num``) and file inputs
    (``distribution_file`` / ``cosmetic_file``) are explicitly excluded.
  * Category assignment is a documented heuristic (explicit core-rule names
    first, then the cosmetic flag, then name-keyword matching) since OoTR does
    not expose a single canonical category field.
"""

import ast
import json
import sys
from pathlib import Path

# Maps an OoTR setting constructor to our schema type. ``None`` means the
# constructor does not describe a real user-facing setting (GUI button, textbox,
# internal holder) and is always skipped.
SETTING_TYPES = {
    "Checkbutton": "bool",
    "Combobox": "enum",
    "Radiobutton": "enum",
    "ComboboxInt": "enum",
    "Textinput": "string",
    "Fileinput": "string",
    "Directoryinput": "string",
    "Scale": "int",
    "Numberinput": "int",
    "MultipleSelect": "list",
    "SearchBox": "list",
    "Textbox": None,
    "Button": None,
    "SettingInfoStr": None,
    "SettingInfoList": None,
    "SettingInfoDict": None,
    "SettingInfoNone": None,
}

# Positional argument names per constructor, in order. Keyword arguments
# (captured separately) override these.
POSITIONAL = {
    "Checkbutton": ["gui_text", "gui_tooltip", "disable", "shared", "default",
                    "disabled_default", "gui_params", "cosmetic"],
    "Combobox": ["gui_text", "choices", "default", "shared", "disable",
                 "gui_tooltip", "gui_params", "cosmetic"],
    "Radiobutton": ["gui_text", "choices", "default", "shared", "disable",
                    "gui_tooltip", "gui_params", "cosmetic"],
    "ComboboxInt": ["gui_text", "choices", "default", "shared", "disable",
                    "gui_tooltip", "gui_params", "cosmetic"],
    "Textinput": ["gui_text", "choices", "default", "shared", "disable",
                  "gui_tooltip", "gui_params", "cosmetic"],
    "Fileinput": ["gui_text", "choices", "default", "shared", "disable",
                  "gui_tooltip", "gui_params", "cosmetic"],
    "Directoryinput": ["gui_text", "choices", "default", "shared", "disable",
                       "gui_tooltip", "gui_params", "cosmetic"],
    "Scale": ["gui_text", "default", "minimum", "maximum", "step", "shared",
              "disable", "gui_tooltip", "gui_params", "cosmetic"],
    "Numberinput": ["gui_text", "default", "minimum", "maximum", "step", "shared",
                    "disable", "gui_tooltip", "gui_params", "cosmetic"],
    "MultipleSelect": ["gui_text", "choices", "default", "shared", "disable",
                       "gui_tooltip", "gui_params", "cosmetic"],
    "SearchBox": ["gui_text", "choices", "default", "shared", "disable",
                  "gui_tooltip", "gui_params", "cosmetic"],
}

# Settings that must never appear in the asset.
EXCLUDE_NAMES = {
    "world_count", "player_num",          # multiworld not supported in v1
    "distribution_file", "cosmetic_file",  # file inputs
    "seed",                                # handled separately by the UI
    "seed_number",                         # alias of seed
}

# Core rule settings that belong in the "main" category regardless of name
# heuristics.
MAIN_CORE_NAMES = {
    "logic_rules", "reachable_locations", "triforce_hunt",
    "triforce_count_per_world", "triforce_goal_per_world",
    "lacs_condition", "bridge", "shuffle_ganon_bosskey",
}
MAIN_CORE_PREFIXES = ("lacs_", "bridge_", "ganon_bosskey_", "triforce_")

# Heuristic keyword -> category (evaluated in order; first match wins).
CATEGORY_KEYWORDS = [
    ("open", "open"),
    ("kakariko", "open"),
    ("door_of_time", "open"),
    ("zora_fountain", "open"),
    ("gerudo_fortress", "open"),
    ("trial", "open"),
    ("shuffle", "shuffle"),
    ("dungeon", "dungeons"),
    ("mq", "dungeons"),
    ("starting", "starting_items"),
    ("timesaver", "timesavers"),
    ("fast", "timesavers"),
    ("skip", "timesavers"),
    ("trick", "tricks"),
]


def literal(node):
    """Safely evaluate a literal/constant AST node, or return None."""
    try:
        return ast.literal_eval(node)
    except Exception:
        return None


def extract_top_level_dict(tree, name):
    """Return the literal dict assigned to ``name = {...}`` at module level.

    Handles both plain assignments (``name = {...}``) and annotated assignments
    (``name: dict[...] = {...}``), the latter being how SettingsListTricks.py
    declares its trick tables.
    """
    for stmt in tree.body:
        target = None
        value = None
        if isinstance(stmt, ast.Assign) and len(stmt.targets) == 1:
            target = stmt.targets[0]
            value = stmt.value
        elif isinstance(stmt, ast.AnnAssign):
            target = stmt.target
            value = stmt.value
        if target is None or value is None:
            continue
        if isinstance(target, ast.Name) and target.id == name:
            return literal(value)
    return None


def load_dynamic_choices(tricks_path):
    """Build {logic_tricks: {value: label}, advanced_logic_tricks: {...}}.

    In SettingsList.py the trick SearchBoxes build their choices via a
    comprehension over ``logic_tricks`` / ``advanced_logic_tricks`` imported
    from SettingsListTricks.py. Those dicts map a display label to a record
    holding the canonical ``name`` (the value we must send to the API). We
    invert that into {value: label} form here.
    """
    dynamic = {}
    if not tricks_path or not Path(tricks_path).exists():
        return dynamic
    try:
        tree = ast.parse(Path(tricks_path).read_text(encoding="utf-8"))
    except Exception:
        return dynamic
    for src_name in ("logic_tricks", "advanced_logic_tricks"):
        raw = extract_top_level_dict(tree, src_name)
        if not isinstance(raw, dict):
            continue
        inverted = {}
        for label, record in raw.items():
            if isinstance(record, dict) and "name" in record:
                inverted[record["name"]] = label
            else:
                inverted[label] = label
        dynamic[src_name] = inverted
    return dynamic


def resolve_choices(node, dynamic):
    """Resolve a choices AST node to a plain dict, using dynamic tables."""
    lit = literal(node)
    if lit is not None:
        return lit
    # Comprehensions may iterate over a known dynamic name (e.g. logic_tricks).
    if isinstance(node, (ast.DictComp, ast.SetComp, ast.ListComp)):
        for sub in ast.walk(node):
            if isinstance(sub, ast.Name) and sub.id in dynamic:
                return dynamic[sub.id]
    return None


def extract_args(call):
    """Merge positional + keyword arguments of a setting constructor call."""
    func_name = call.func.id
    params = {}
    positional = POSITIONAL.get(func_name, [])
    for i, arg in enumerate(call.args):
        if i < len(positional):
            params[positional[i]] = literal(arg)
    for kw in call.keywords:
        if kw.arg is None:  # **kwargs splat - ignore
            continue
        params[kw.arg] = literal(kw.value)
    return params


def build_choices(raw):
    """Convert an OoTR choices dict/list into our [{value,label}] form."""
    if raw is None:
        return None
    if isinstance(raw, dict):
        return [{"value": k, "label": v} for k, v in raw.items()]
    if isinstance(raw, list):
        return [{"value": v, "label": str(v)} for v in raw]
    return None


def default_for(stype, raw, choices):
    if raw is not None:
        return raw
    if stype == "bool":
        return False
    if stype == "int":
        return 0
    if stype == "string":
        return ""
    if stype == "list":
        return []
    if stype == "enum":
        return choices[0]["value"] if choices else ""
    return None


def categorize(name, stype, cosmetic, tabs):
    if name in MAIN_CORE_NAMES or name.startswith(MAIN_CORE_PREFIXES):
        return "main"
    if cosmetic:
        return "cosmetics"
    if tabs:
        if "cosmetics_tab" in tabs or "sfx_tab" in tabs:
            return "cosmetics"
    lower = name.lower()
    for keyword, cat in CATEGORY_KEYWORDS:
        if keyword in lower:
            return cat
    return "misc"


def main():
    if len(sys.argv) != 4:
        print("usage: generate_settings_schema.py <SettingsList.py> "
              "<SettingsListTricks.py> <output.json>", file=sys.stderr)
        sys.exit(2)

    source_path = Path(sys.argv[1])
    tricks_path = Path(sys.argv[2])
    output_path = Path(sys.argv[3])

    dynamic = load_dynamic_choices(tricks_path)

    tree = ast.parse(source_path.read_text(encoding="utf-8"))

    cls = next((n for n in tree.body
                if isinstance(n, ast.ClassDef) and n.name == "SettingInfos"), None)
    if cls is None:
        print("error: SettingInfos class not found", file=sys.stderr)
        sys.exit(1)

    skipped = []          # (name, reason)
    options = []          # collected option dicts with a temp category

    for stmt in cls.body:
        if not isinstance(stmt, ast.Assign):
            continue
        if not isinstance(stmt.value, ast.Call):
            continue
        call = stmt.value
        if not isinstance(call.func, ast.Name):
            continue
        type_name = call.func.id
        if type_name not in SETTING_TYPES:
            continue
        stype = SETTING_TYPES[type_name]
        if stype is None:
            continue  # GUI-only / internal holder

        name = stmt.targets[0].id
        if name in EXCLUDE_NAMES:
            skipped.append((name, "excluded by name (multiworld/file/seed)"))
            continue

        params = extract_args(call)

        gui_text = params.get("gui_text")
        if gui_text is None:
            skipped.append((name, "no gui_text (internal)"))
            continue

        shared = bool(params.get("shared", False))
        if not shared:
            skipped.append((name, "not shared"))
            continue

        cosmetic = bool(params.get("cosmetic", False))
        gui_params = params.get("gui_params") or {}
        if not isinstance(gui_params, dict):
            gui_params = {}
        tabs = gui_params.get("tabs") or []

        # Resolve choices (enum / list types) from the call node directly, so
        # comprehensions over dynamic names (e.g. logic_tricks) can be resolved.
        choices_node = None
        for kw in call.keywords:
            if kw.arg == "choices":
                choices_node = kw.value
                break
        if choices_node is None and len(call.args) > 1:
            choices_node = call.args[1]
        choices_raw = resolve_choices(choices_node, dynamic) \
            if (stype in ("enum", "list") and choices_node is not None) else None

        choices = build_choices(choices_raw) if stype in ("enum", "list") else None
        if stype in ("enum", "list") and choices is None:
            skipped.append((name, "non-literal choices (dynamic/unresolvable)"))
            continue

        # Resolve default.
        default = default_for(stype, params.get("default"), choices)

        # Integer range.
        minimum = maximum = step = None
        if stype == "int":
            minimum = params.get("minimum")
            maximum = params.get("maximum")
            step = params.get("step", 1)
            if minimum is None:
                minimum = gui_params.get("min")
            if maximum is None:
                maximum = gui_params.get("max")
            if step is None:
                step = gui_params.get("step", 1)
            minimum = int(minimum) if minimum is not None else None
            maximum = int(maximum) if maximum is not None else None
            step = int(step) if step is not None else 1

        option = {
            "name": name,
            "type": stype,
            "label": gui_text,
            "default": default,
            "cosmetic": cosmetic,
        }
        tooltip = params.get("gui_tooltip")
        if tooltip:
            option["tooltip"] = " ".join(str(tooltip).split())
        if choices is not None:
            option["choices"] = choices
        if stype == "int":
            if minimum is not None:
                option["min"] = minimum
            if maximum is not None:
                option["max"] = maximum
            option["step"] = step or 1

        category = categorize(name, stype, cosmetic, tabs)
        option["_category"] = category
        options.append(option)

    # Group into categories, preserving a stable order.
    category_order = ["main", "open", "shuffle", "dungeons", "tricks",
                      "timesavers", "starting_items", "misc", "cosmetics"]
    grouped = {c: [] for c in category_order}
    for opt in options:
        cat = opt.pop("_category")
        grouped.setdefault(cat, []).append(opt)

    categories = []
    for cat in category_order:
        opts = grouped.get(cat)
        if opts:
            categories.append({"id": cat, "options": opts})

    schema = {
        "schemaVersion": 1,
        "sourceVersion": "Dev",
        "categories": categories,
    }

    output_path.write_text(json.dumps(schema, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")

    print(f"Wrote {len(options)} settings across {len(categories)} categories to "
          f"{output_path}", file=sys.stderr)
    print(f"Skipped {len(skipped)} settings (see README):", file=sys.stderr)
    for n, r in skipped:
        print(f"  - {n}: {r}", file=sys.stderr)


if __name__ == "__main__":
    main()
