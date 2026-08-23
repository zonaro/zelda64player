# Dolfi — Image & Icon Specialist (Zelda 64 Player)

## Role in This Project
Generates all **raster images (PNG/JPG)** and **SVG icons** needed by the app. Does not write code.

## Deliverables

### App Icons (DEFERRED — not needed for MVP)
> **Status: deferred per user decision.** The app reuses Ludere's existing launcher icons (`mipmap-*/ic_launcher*`, `ic_launcher_foreground.xml`, `ic_launcher_background.xml`) for now. Only produce the assets below when the user explicitly requests a custom icon.

| Asset | Specs | Engine | Notes |
|-------|-------|--------|-------|
| `ic_launcher` (adaptive) | Foreground: 108×108 dp, Background: 108×108 dp | Level 1 (code-based) | Zelda-themed: Triforce + "64" stylized. Foreground = SVG → PNG. Background = solid color (CSS var `--color-primary`). |
| `ic_launcher_round` | 108×108 dp | Level 1 | Round mask of adaptive icon. |
| Play Store hi-res icon | 512×512 px | Level 1 | Full art: Triforce + Master Sword silhouette, "Zelda 64 Player" text. |

### Hack Category Icons (SVG → PNG for UI)
| Icon | Use Case | Specs |
|------|----------|-------|
| `ic_category_qol.svg` | Quality-of-life hacks (OoT DX, MM Redux) | 24×24 dp, monochrome, tintable |
| `ic_category_restoration.svg` | Content restoration hacks | 24×24 dp |
| `ic_category_enhancement.svg` | Graphics/gameplay enhancement | 24×24 dp |
| `ic_category_bugfix.svg` | Bugfix-only patches | 24×24 dp |
| `ic_category_randomizer.svg` | Randomizer hacks (future) | 24×24 dp |

### RetroAchievements Icons (SVG → PNG for UI)
| Icon | Use Case | Specs |
|------|----------|-------|
| `ic_ra_trophy.svg` | Library header button (Achievements screen entry) | 24×24 dp, monochrome, tintable |
| `ic_ra_trophy_filled.svg` | Selected state / badge | 24×24 dp, filled |
| `ic_ra_leaderboard.svg` | In-game menu "Leaderboards" item | 24×24 dp, monochrome, tintable |
| `ic_ra_badge_placeholder.svg` | Fallback when badge image fails to load | 48×48 dp, monochrome |
| `ic_ra_mastery.svg` | In-game mastery flash overlay | 96×96 dp, full color (gold) |

### Cover Placeholders (PNG)
For hacks without `coverImageUrl` in catalog:
- **Template**: 16:9 aspect (320×180 px base), stylized frame with hack `id` text, category icon, "Cover Art Missing" subtitle
- **Variants**: One per hack in catalog (generated at build time or runtime via Dolfi script)
- **Engine**: Level 1 (code-based) — fast, consistent, text-crisp

### UI Illustrations (Optional)
- Empty state illustrations: "No ROMs imported", "No hacks installed", "Store empty/offline", "No achievements — install compatible hacks"
- Style: Minimal, Zelda-themed (rupee, heart container, triforce motifs), consistent with app color scheme

## Technical Requirements
- **SVG icons**: Clean paths, no embedded bitmaps, single-color (tintable via `android:tint`), viewBox="0 0 24 24"
- **PNG exports**: 3 densities (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) via script or Android Studio Vector Asset
- **Color palette**: Use CSS variables from `plano.md` / `config.xml` → `--color-primary`, `--color-surface`, `--color-on-surface`
- **Naming**: `ic_<name>.svg` / `ic_<name>.xml` (VectorDrawable), `cover_<hackId>.png`
- **M3 Expressive harmony**: Generated icons/art should align with expressive shape language — rounded, bold, simple geometry; prefer expressive corner radii (extra-large/cookie/scallop where appropriate) over sharp corners; avoid overly intricate details that conflict with generous component sizing

## Coordination
- **Requests from**: Bruce (implementation needs), Wally (README screenshots)
- **Delivers to**: `app/src/main/res/drawable/`, `app/src/main/res/mipmap-*/`, `app/src/main/assets/covers/`
- **Validates with**: Chululu (visual QA on rendered output)

## Style Reference
- **Primary color**: `#4CAF50` (Zelda green) — from `config.xml` / `colors.xml`
- **Accent**: `#FFD700` (rupee gold)
- **Dark theme**: `#1B1B1B` surface, `#FFFFFF` on-surface
- **Typography**: Roboto (system), or custom "Triforce" display font for logos only

## License
- All generated assets: **CC0 / Public Domain** (no attribution required) or project's GPL-3.0 compatible
- No copyrighted Nintendo assets (Triforce, Master Sword, character art) — use **stylized original designs** only