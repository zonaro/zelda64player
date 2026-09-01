# Zelda 64 Player — Project Rules for Agent Team

**Package:** `br.com.redclaw.zelda64player`
**Base project:** Ludere (fork of Swordfish90/Ludere, GPL-3.0) at `/mnt/GIT/Ludere`
**App name:** "Zelda 64 Player"
**Type:** Native Android (Kotlin) emulator frontend for Nintendo 64 Zelda ROM hacks (OoT/MM) with on-the-fly BPS/IPS patching.

> **Documentation is now organized in `.agents/`.** This file is a concise index. The detailed, categorized docs live in `.agents/*.md` (architecture, rules, visual identity, features, build, i18n, testing). The legacy `plano.md` has been superseded and removed — its RetroAchievements content now lives in `.agents/RETROACHIEVEMENTS.md`.

---

## Quick Facts

- **Never ships/downloads/distributes base ROMs** (OoT, MM). Users import their own. Only BPS/IPS patches + metadata are distributed. (Rules 1–2)
- **GPL-3.0** derivative of Ludere. Keep `LICENSE`, preserve headers, offer source. (Rule 3)
- **BPS patcher is clean-room** from public spec only. (Rule 4)
- **Nintendo Switch UI** is the mandatory visual standard for all screens; RadialGamePad touch layout is frozen. (Rule 14, `.agents/VISUAL_IDENTITY.md`)
- **i18n mandatory:** pt-BR default, en, es. Zero hardcoded strings. (Rule 8)

---

## `.agents/` File Index

### Categorized Project Docs
| File                                                           | Contents                                                                                                                       |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| [`.agents/ARCHITECTURE.md`](.agents/ARCHITECTURE.md)           | Stack decisions, actual package layout, ROM→patch→cache→core data flow, threading model, native build, Ludere migration scope  |
| [`.agents/RULES.md`](.agents/RULES.md)                         | **All hard rules** (legal, code quality, architecture, security, RA) consolidated + summary table                              |
| [`.agents/VISUAL_IDENTITY.md`](.agents/VISUAL_IDENTITY.md)     | Nintendo Switch UI design tokens, focus system, component inventory, screen mapping, sound rules, licensing guard              |
| [`.agents/FEATURES.md`](.agents/FEATURES.md)                   | Feature index + status + interaction map                                                                                       |
| [`.agents/RETROACHIEVEMENTS.md`](.agents/RETROACHIEVEMENTS.md) | RA deep dive: rcheevos/JNI, package structure, threading, data model, phases B1–B5, risk register                              |
| [`.agents/AUTO_OCARINA.md`](.agents/AUTO_OCARINA.md)           | Auto-Ocarina HUD: song catalog, game detection, coroutine sequencer                                                            |
| [`.agents/VANILLA_GAMES.md`](.agents/VANILLA_GAMES.md)         | Vanilla base-ROM tiles in Library, `vanilla_<crc32>` IDs, family badges, RA lazy hash                                          |
| [`.agents/CAPTURE_GALLERY.md`](.agents/CAPTURE_GALLERY.md)     | Screenshot/recording (MediaProjection) + local Gallery                                                                         |
| [`.agents/STORE.md`](.agents/STORE.md)                         | Hack Store, multi-store (Picks + Hylian Modding), catalog formats, cross-catalog dedupe via `canonicalId`, base ROM management |
| [`.agents/PATCHER.md`](.agents/PATCHER.md)                     | Pure-Kotlin BPS/IPS patcher, N64 normalization, triple CRC32 validation, clean-room spec                                       |
| [`.agents/BUILD.md`](.agents/BUILD.md)                         | Build requirements, Gradle, native (CMake + rcheevos), core fetch, release                                                     |
| [`.agents/I18N.md`](.agents/I18N.md)                           | i18n rule, locale files, translation process, RA exception                                                                     |
| [`.agents/TESTING.md`](.agents/TESTING.md)                     | Test stack, coverage, fixtures, commands                                                                                       |

### Per-Agent Responsibility Files
| File                                         | Agent                                                   |
| -------------------------------------------- | ------------------------------------------------------- |
| [`.agents/coral.md`](.agents/coral.md)       | Coral 🪸 — Chief Architect (owns plan/architecture/docs) |
| [`.agents/bruce.md`](.agents/bruce.md)       | Bruce 🦈 — Primary Kotlin/Android implementer            |
| [`.agents/dolfi.md`](.agents/dolfi.md)       | Dolfi 🐬 — Icons/covers/splash art                       |
| [`.agents/wally.md`](.agents/wally.md)       | Wally 🐋 — Documentation/translation/KDoc                |
| [`.agents/calamari.md`](.agents/calamari.md) | Calamari 🦑 — Fast fact-checking                         |
| [`.agents/puffy.md`](.agents/puffy.md)       | Puffy 🐡 — Deep documentation research                   |
| [`.agents/chululu.md`](.agents/chululu.md)   | Chululu 🐙 — Visual QA / Switch UI compliance            |

---

## Agent Team for THIS Project

| Agent          | Role                                                                                     | Delegation Trigger                                    |
| -------------- | ---------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| **Coral** 🪸    | Chief Architect — owns architecture, `AGENTS.md`, `.agents/`                             | New project setup, major arch changes, team selection |
| **Bruce** 🦈    | **Primary Implementer** — all Kotlin/Android code (all phases + Switch UI + multi-store) | All implementation tasks                              |
| **Dolfi** 🐬    | Icons/covers — SVG icons, PNG placeholders, splash art, gallery/capture icons            | When UI needs assets                                  |
| **Wally** 🐋    | Documentation — README, strings translation, KDoc                                        | After implementation, before release                  |
| **Calamari** 🦑 | Fact-checking — ROM checksums, core versions, BPS spec, sound licensing                  | When Bruce needs verified data                        |
| **Puffy** 🐡    | Research — LibretroDroid/core/Android API, rcheevos docs, TV focus, SoundPool            | When Bruce needs current docs                         |
| **Chululu** 🐙  | Visual QA — screenshot analysis, Switch UI compliance                                    | Before UI merges, release candidates                  |

**Agents NOT involved:** InnerLinho (PHP), Fishie (Web), Peep (Flutter), Snowflake (C#), Snuggle (Python), Nodi (Node.js), Ariel (Content), Tucso (Linux).

### Delegation Mapping
| Task                                                                  | Delegate     |
| --------------------------------------------------------------------- | ------------ |
| Android/Kotlin implementation                                         | **Bruce**    |
| Architecture changes / plan updates                                   | **Coral**    |
| App icon, category icons, covers, splash, gallery/capture icons       | **Dolfi**    |
| README, strings translation, KDoc                                     | **Wally**    |
| ROM checksum / core version / BPS spec / sound licensing              | **Calamari** |
| LibretroDroid/core/Android API / rcheevos docs / TV focus / SoundPool | **Puffy**    |
| UI screenshot analysis, Switch UI compliance                          | **Chululu**  |

---

## Hard Rules (Summary — Full text in `.agents/RULES.md`)

1. No base ROMs · 2. No copyrighted content · 3. GPL-3.0 compliance · 4. Clean-room BPS · 5. DRY · 6. Modular · 7. No emojis · 8. i18n mandatory · 9. Performance (stream, no full ROM in heap) · 10. RetroView interception (only place bytes reach core) · 11. Storage paths keyed by hackId · 12. Catalog schema versioning · 13. Selective Ludere migration · 14. Gamepad layout FROZEN (Switch UI chrome only) · 15. No telemetry without opt-in · 16. Validate all inputs · 17. Dashboard Settings parity for every app configuration (sensitive values write-only) · 20. RA credentials never logged · 21. RA hash from final patched ROM · 22. Leaderboards never overlaid on gameplay · 23. Hardcore defaults OFF · 24. rcheevos MIT license notice · 25. System notifications opt-in default ON.

---

## References

- **Categorized docs:** `.agents/*.md` (this folder)
- **Global rules:** `~/.config/opencode/AGENTS.md`
- **Source project:** `/mnt/GIT/Ludere`
- **BPS Spec:** https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md
- **N64 ROM Header:** https://n64brew.dev/wiki/ROM_Header
- **LibretroDroid:** https://github.com/Swordfish90/LibretroDroid
- **Hylian Modding:** https://hylianmodding.com
