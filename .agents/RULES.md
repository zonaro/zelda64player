# Hard Rules — Zelda 64 Player

These rules are **mandatory** and apply to every agent and every change in this repository. Violations block merge.

## Legal / Copyright

1. **NEVER embed, download, or distribute base ROMs** (OoT, MM). This includes assets, raw resources, test fixtures, CI artifacts, and release APKs.
2. **NEVER commit copyrighted content** (ROMs, BIOS, proprietary assets). Only patches (BPS/IPS) and metadata are distributed.
3. **GPL-3.0 compliance:** This project is a derivative of Ludere (GPL-3.0). The entire app MUST be licensed GPL-3.0. Keep the `LICENSE` file, preserve copyright headers, offer source code.
4. **BPS Patcher is clean-room:** Implemented from the public spec (`bps_spec.md`) only. Do NOT read or copy code from `rom_patcher_js` (GPL-3.0) or `UniPatcher` (GPL-3.0). Document this in code headers.

## Code Quality

5. **DRY:** Extract shared logic into `utils/`, `patcher/`, or `di/`. No duplication.
6. **Modular:** Small focused classes/functions. Single responsibility.
7. **No emojis** in code, file paths, resource names, or technical output. Conversational text only.
8. **i18n mandatory:** Every user-facing string in `strings.xml`. Default `values/` = **pt-BR**. Provide `values-en/` and `values-es/`. Zero hardcoded strings in Kotlin/XML layouts.
9. **Performance:** Default `config_load_bytes=false` (stream from file). Avoid loading full 32–64 MB ROMs into heap. Use streaming patcher.

## Architecture

10. **RetroView interception:** The ONLY place ROM bytes reach the core is `RetroView.kt` → `GLRetroViewData.gameFilePath` (or `gameFileBytes`). This is where patched ROM from `Storage.rom(hackId)` is supplied.
11. **Storage paths keyed by hackId:** Each hack gets isolated `rom_<hackId>`, `sram_<hackId>`, `state_<hackId>`. SRAM/save-states never collide.
12. **Catalog schema versioning:** `catalogVersion` integer in JSON. Handle migrations gracefully.

## Migration Scope (Ludere extraction)

13. **Selective migration only:** Copy from Ludere exclusively `retroview/`, `views/GameActivity*` + ViewModel, `gamepad/`, `input/`, `utils/`, `config.xml`, `Storage.kt`, plus launcher icons. Do NOT migrate `data/Games.kt`, bundled cover drawables, ROM packaging machinery, `autogen/`, the autogen GitHub workflow, the Ludere keystore, or ABI splits.
14. **Gamepad layout is FROZEN:** The Zelda-tailored control layout (`gamepad/GamePadConfig.kt` placements, ButtonStick + Auto mode, Auto-Z double-tap, FloatingJoystick region/hint/reach, DoubleTapContainer, physical-controller mirroring, sensitivity dialogs) is measured from a reference image and tuned for OoT/MM. Migrate verbatim (package rename only). Any layout change requires explicit user approval + update to `plano.md` "Layout de Controles Sob Medida". Integration invariants must not regress: overlay uses INVISIBLE (never GONE), pads created only after first real layout pass, GL-context recovery via full Activity recreate(), `super.onDestroy()` called BEFORE dispose. **In-game menu chrome, overlays, pause menu, achievement overlay, leaderboard dialog, and ocarina HUD ARE restyled to Switch UI** — only the RadialGamePad touch-control LAYOUT remains frozen.

## Security / Privacy

15. **No telemetry without opt-in.** No analytics, crash reporting, or network calls except: catalog fetch (user-initiated), patch download (user-initiated), core download (build-time only).
16. **Validate all inputs:** ROM checksums, patch checksums, catalog JSON schema, downloaded file sizes.
17. **Dashboard settings parity:** The self-hosted Dashboard's **Settings** tab MUST expose every app configuration. Dashboard changes MUST use the same validation, persistence, and required side effects as the native Settings screen. Sensitive values (for example passwords and credentials) MAY be write-only, but their values MUST never be returned, logged, or exposed to the browser.

## RetroAchievements Feature

20. **RA credentials never logged:** Username/password/token sanitized in all logs (`***`). Stored only in `EncryptedSharedPreferences` (separate prefs file `ra_secure_prefs`).
21. **RA hash computed ONLY from final patched ROM:** At install time, after BPS/ZPF patch applied and ROM written to `Storage.rom(hackId)`. Never from base ROM or uncompressed intermediate.
22. **Leaderboards NEVER overlaid on gameplay:** Leaderboards accessible ONLY via GameActivity in-game menu (DialogFragment). No tracker widgets, no overlay views during emulation.
23. **Hardcore mode defaults OFF:** `pref_ra_hardcore = false` until User-Agent validated with RAdmin. Setting exists but softcore is default.
24. **Third-party license notices:** rcheevos (MIT) license file kept in `app/src/main/cpp/rcheevos/LICENSE` and referenced in app Licenses screen / About dialog.
25. **System notifications opt-in default ON:** `pref_ra_system_notifications = true` but requires `POST_NOTIFICATIONS` runtime permission on API 33+. First unlock triggers permission request if needed.

---

## Rule Summary Table (for quick reference)

| # | Rule | Summary |
|---|------|---------|
| 1 | No base ROMs | Never embed, download, or distribute base ROMs (OoT, MM). |
| 2 | No copyrighted content | Only patches (BPS) and metadata are distributed; no ROMs, BIOS, proprietary assets. |
| 3 | GPL-3.0 compliance | Entire app must be licensed GPL-3.0; keep LICENSE file, preserve copyright headers, offer source code. |
| 4 | Clean-room BPS | BPS implemented from public spec only; do not copy from rom_patcher_js or UniPatcher. |
| 5 | DRY | Extract shared logic into `utils/`, `patcher/`, or `di/`. |
| 6 | Modular | Small focused classes/functions, single responsibility. |
| 7 | No emojis | In code, file paths, resource names, or technical output. |
| 8 | i18n mandatory | All user-facing strings in `strings.xml` (pt-BR default, en, es). Zero hardcoded strings. |
| 9 | Performance | Default `config_load_bytes=false`; stream from file; avoid loading full 32–64 MB ROMs into heap. |
| 10 | RetroView interception | The ONLY place ROM bytes reach the core is `RetroView.kt` → `GLRetroViewData`. |
| 11 | Storage paths keyed by hackId | Each hack gets isolated `rom_<hackId>`, `sram_<hackId>`, `state_<hackId>`. |
| 12 | Catalog schema versioning | `catalogVersion` integer in JSON; handle migrations gracefully. |
| 13 | Selective migration | Copy from Ludere only specified modules; do NOT migrate data/Games.kt, bundled covers, ROM packaging machinery, `autogen/`, Ludere keystore, or ABI splits. |
| 14 | Gamepad layout FROZEN | RadialGamePad layout measured for OoT/MM; no changes without user approval + `plano.md` update. In-game chrome restyled to Switch UI. |
| 15 | No telemetry without opt-in | No analytics, crash reporting, or network calls except user-initiated catalog fetch, patch download, core download (build-time only). |
| 16 | Validate all inputs | ROM checksums, patch checksums, catalog JSON schema, downloaded file sizes. |
| 17 | Dashboard settings parity | The Dashboard Settings tab exposes every app configuration with the same validation, persistence, and side effects as native Settings; sensitive values are write-only. |
| 20 | RA credentials | Never logged; stored only in `EncryptedSharedPreferences` (`ra_secure_prefs`). |
| 21 | RA hash from final patched ROM | Computed at install time after BPS/ZPF patch applied and ROM written to `Storage.rom(hackId)`. |
| 22 | Leaderboards never overlaid on gameplay | Accessible ONLY via GameActivity in-game menu (DialogFragment). |
| 23 | Hardcore mode defaults OFF | `pref_ra_hardcore = false` until RAdmin User-Agent validated. |
| 24 | License notices | rcheevos (MIT) license file kept in `app/src/main/cpp/rcheevos/LICENSE`. |
| 25 | System notifications opt-in default ON | Requires `POST_NOTIFICATIONS` runtime permission on API 33+; first unlock triggers permission request if needed. |
