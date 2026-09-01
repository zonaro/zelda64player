# Visual Identity — Nintendo Switch UI (MANDATORY)

**Nintendo Switch UI is the mandatory visual standard for ALL screens** (existing and future). This is a custom native implementation inspired by the Nintendo Switch HOME menu aesthetic (as seen in NS_Launcher / FLauncher). No Material 3 Expressive requirements remain. The only exemption is the **RadialGamePad touch-control LAYOUT** (Rule 14 — control placement, button-stick modes, floating joystick, auto-Z, physical-controller mirroring remain frozen). In-game menu chrome, overlays, pause menu, achievement overlay, leaderboard dialog, and ocarina HUD MUST follow the Switch style.

> The app retains its own green highlight color (`#02830C`), cyan focus treatment, and amber action color — blended into the Switch aesthetic.

---

## 1. Design Tokens

| Token | Dark Mode | Light Mode | Usage |
|-------|-----------|------------|-------|
| `bg_primary` | `#2D2D2D` | `#F0F0F0` | Main background (Library Home, grid screens) |
| `bg_panel` | `#1E1E1E` – `#2A2A2A` | `#FFFFFF` | Side panels, dialogs, cards |
| `accent_focus` | `#00BCD4` (cyan) | `#00BCD4` (cyan) | Focus borders, focused labels, primary actions |
| `accent_amber` | `#FFA000` (amber) | `#FFA000` (amber) | Appearance/theme actions, warnings |
| `accent_green` | `#02830C` | `#02830C` | App brand highlight (Zelda green) |
| `text_primary` | `#FFFFFF` | `#333333` | Primary text (titles, labels) |
| `text_secondary` | `#9E9E9E` | `#666666` | Secondary text (hints, "(default)" suffixes, footer) |
| `scrim` | `rgba(0,0,0,0.5–0.6)` | `rgba(0,0,0,0.3–0.4)` | Modal backdrop, panel overlays |
| `dock_circle` | `#555555` | `#FFFFFF` (subtle shadow) | Dock button backgrounds |
| `card_radius` | `4–6 dp` (near-square) | `4–6 dp` | Game cards (home row, grid) |
| `dialog_radius` | `12–16 dp` | `12–16 dp` | Dialogs, controllers modal |
| `panel_edges` | Sharp (0 dp) | Sharp (0 dp) | Side panel (right slide-in) |
| `card_aspect` | 1:1 (square) | 1:1 (square) | Home row ~220dp@1080p, grid ~170dp@1080p |
| `dock_button_diameter` | `~50 dp` | `~50 dp` | Circular dock icons |

> All colors defined as CSS-variable-style names in `colors.xml` (`color_primary`, `color_surface`, etc.). No hardcoded colors in layouts.

---

## 2. Focus System

- **D-pad / click focus** drives a **cyan 2–3 dp border** on the focused element + **label above the focused card** in cyan 18sp medium.
- **Unfocused cards:** 10% black overlay dimming.
- **Side-panel rows:** full-width focus border (cyan default, amber for theme/appearance actions).
- **Circular "All Games" card:** cyan border when focused, opens fullscreen grid.
- **Dock buttons:** focus ring (cyan) + glyph highlight.

---

## 3. Component Inventory (Native Kotlin, Hand-Styled)

| Component | Description |
|-----------|-------------|
| `SwitchHomeRow` | Horizontal scrollable row of square game cards + circular "Todos os Jogos" card at end |
| `SwitchGameCard` | Square card (1:1), cover image, game title overlay on focus, focus border, dimming overlay |
| `SwitchAllGamesCard` | Circular card (charcoal fill, cyan 2×2 grid icon, cyan border on focus) |
| `SwitchGridScreen` | Fullscreen grid ("Todos os Jogos"): header icon+title "Todos os Jogos" 20sp bold + thin separator, smaller square cards (~170dp), search/filter bar, ghosted placeholders |
| `SwitchDock` | Fixed bottom dock: 5 circular buttons (Loja, RetroAchievements, Galeria, Teste de Controle, Configurações), ~50dp diameter, colored glyphs, focus ring |
| `SwitchFooterHints` | Bottom bar: left TV+gamepad indicators, right "(i) Sobre" and "+ Opções" gray hints 11–12sp |
| `SwitchSidePanel` | Right slide-in panel (~50% width), sharp edges, header (teal badge icon + bold title 20–22sp + separator), numbered rows (gray circle 24dp badges), labels 16sp, "(default)" suffix 14sp gray, chevron right, thin line separators |
| `SwitchDialog` | Centered modal, scrim, box ~40% width, radius 12–16dp, bg `#3A3A3C`, header icon+title 18sp, rows 48–52dp with icon+text, focused row = cyan border outline |
| `SwitchFocusBorder` | Drawable: cyan 2–3dp stroke, transparent fill, for focus indication |
| `SfxManager` | SoundPool wrapper: focus-move tick, select, back, panel open/close; CC0/generated only; volume respect; toggle in settings |
| `ThemeManager` | Runtime dark/light switch, persists preference, applies tokens above |

---

## 4. Screen-by-Screen Mapping

| Screen | Switch Style Applied | Notes |
|--------|---------------------|-------|
| Splash | **Zelda gold/green palette** (Dolfi original art), same structural layout as NS Launcher splash (flanking iconic shapes + two-line logo "Zelda 64" / "PLAYER") | No Nintendo IP (no Joy-Con shapes, no Nintendo logos) |
| Library Home | `SwitchHomeRow` + `SwitchAllGamesCard` + `SwitchDock` + `SwitchFooterHints` | Vanilla games first, then store hacks |
| Todos os Jogos (Grid) | `SwitchGridScreen` | All entries together (vanilla + hacks + seeds), search/filter |
| Store | `SwitchSidePanel` for filters/sort? Or fullscreen grid with Switch cards | Store hacks as Switch cards; detail bottom sheet → SwitchDialog style |
| Settings (Quick) | `SwitchSidePanel` (right slide-in) | Quick shortcuts: theme toggle, RA profile status, link to full Settings |
| Settings (Full) | `SwitchGridScreen` or `SwitchSidePanel` fullscreen | Existing SettingsActivity restyled entirely |
| RetroAchievements | `SwitchGridScreen` (games with RA), `SwitchDialog` (detail), `SwitchDialog` (leaderboards) | In-game overlay = custom Switch-style toast |
| GameActivity In-Game Menu | `SwitchDialog` (pause menu), `SwitchDialog` (leaderboards), custom Switch-style overlay (achievement unlock, ocarina HUD) | **RadialGamePad touch layout FROZEN (Rule 14)** — only chrome restyled |
| Gallery | `SwitchGridScreen`-style gallery | View / share / delete captures |

---

## 5. Sound Rules

- **Required SFX:** focus-move ("toc"), select, back, panel open, panel close.
- **Source:** CC0 or synthesized only. **NEVER extract from NS_Launcher APK** (copyright).
- **Storage:** `res/raw/sfx_focus_move.ogg`, `sfx_select.ogg`, `sfx_back.ogg`, `sfx_panel_open.ogg`, `sfx_panel_close.ogg`.
- **Volume:** Respects system media volume; mute toggle in Settings side panel.
- **Implementation:** `SfxManager` (SoundPool) — low latency, preloaded.

---

## 6. Licensing Guard

- UI is an **original native implementation** inspired by the Switch aesthetic.
- **NEVER commit Nintendo assets/fonts/sounds** or files extracted from the NS_Launcher APK (extends Hard Rule 2).
- FLauncher is GPLv3 — consulting its code is allowed; this project stays GPLv3.
- All generated assets (Dolfi) are original, CC0/public domain or GPL-3.0 compatible.

---

## 7. i18n / No-Emoji

- Hard Rules 7–8 still apply: every user-facing string in `strings.xml` (pt-BR default `values/`, `values-en/`, `values-es/`), zero hardcoded strings, no emojis in code/resources.

---

## 8. Asset Delivery (Dolfi)

All icons/covers generated by **Dolfi** (see `.agents/dolfi.md`):
- App icon (deferred — reuses Ludere launcher icons for now)
- Hack category icons, RA trophy/leaderboard icons, Switch dock icons, focus assets
- PNG cover placeholders for hacks without `coverImageUrl`
- Zelda-gold splash artwork
- Gallery/capture icons (`ic_gallery`, `ic_screenshot`, `ic_record`, `ic_stop`)

**Style reference:** Primary `#4CAF50` (Zelda green) / app accent `#02830C`, rupee gold `#FFD700`, dark surface `#1B1B1B`, Switch tokens (cyan `#00BCD4`, amber `#FFA000`, bg `#2D2D2D`). Clean, bold, simple geometry; near-square corners (4–6dp); avoid intricate details that conflict with generous component sizing.
