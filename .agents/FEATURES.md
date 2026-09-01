# Features — Zelda 64 Player

Index of all major features. Each has a deep-dive doc in this folder.

| Feature | Status | Deep-Dive Doc | Summary |
|---------|--------|---------------|---------|
| **On-the-fly Patching** | Shipped | [PATCHER.md](PATCHER.md) | BPS + IPS patching with triple CRC32 validation; N64 byte-order normalization; streaming (no full ROM in heap). |
| **Hack Store** | Shipped | [STORE.md](STORE.md) | Main Store JSON catalog with ETag caching. Download, validate, and install hacks. |
| **Hylian Modding Metadata Import** | Shipped | [STORE.md](STORE.md) | Public metadata is imported into Main Store with attribution; there is no separate Hylian Modding store selector. |
| **Base ROM Management** | Shipped | [STORE.md](STORE.md) | User imports legally-owned OoT/MM ROMs; checksum-based matching validates hack compatibility. |
| **Vanilla Games in Library** | Shipped | [VANILLA_GAMES.md](VANILLA_GAMES.md) | User-imported base ROMs playable directly from Library; `vanilla_<crc32>` IDs; family-colored badges. |
| **Auto-Ocarina** | Shipped | [AUTO_OCARINA.md](AUTO_OCARINA.md) | In-game HUD auto-plays Ocarina songs from pause menu; built-in catalog (OoT: 12, MM: 11) + per-hack custom. |
| **RetroAchievements** | Shipped | [RETROACHIEVEMENTS.md](RETROACHIEVEMENTS.md) | Login, achievements screen, in-game unlock overlay, leaderboards (in-game menu only), rcheevos via JNI. |
| **Nintendo Switch UI** | Shipped | [VISUAL_IDENTITY.md](VISUAL_IDENTITY.md) | Custom native Switch HOME aesthetic across all screens; frozen RadialGamePad layout. |
| **Screen Capture / Recording / Gallery** | Shipped | [CAPTURE_GALLERY.md](CAPTURE_GALLERY.md) | PixelCopy screenshots (with/without overlay), MediaProjection recording, local Gallery (view/share/delete). |
| **Save Backup / Restore** | Shipped | — | ZIP backup per hackId (local, no cloud); `drive/` module adds optional Google Drive sync. |
| **Self-Hosted Dashboard** | In progress | — | Local browser dashboard for the library, backups, streaming, and a Settings tab with parity for every app configuration; sensitive values remain write-only. |
| **Background Catalog Refresh** | Shipped | [STORE.md](STORE.md) | WorkManager `CatalogRefreshWorker` (12h periodic, CONNECTED network). |
| **Gamepad Tester** | Shipped | — | `GamepadTesterActivity` visualizes physical/N64 input without starting a core; shared mapping with gameplay. |
| **i18n (pt-BR/en/es)** | Shipped | [I18N.md](I18N.md) | All user-facing strings externalized; zero hardcoded strings. |

---

## Roadmap (Remaining Stretch Goals)

- Multiple save slots per hack
- Optional opt-in telemetry (privacy-first, deferred)
- Hardcore mode enablement pending RAdmin User-Agent validation
- Custom Zelda-themed app icon (currently reuses Ludere launcher icons)

---

## Feature Interaction Map

```
Library Home (SwitchHomeRow + Dock)
   ├─ Store (SwitchGridScreen) ── download ──► Patcher ──► Storage.rom(canonicalId)
   ├─ Vanilla Games (BaseRomLibrarySource) ──► GameRomResolver ──► RetroView
   ├─ RetroAchievements (AchievementsActivity) ──► rcheevos JNI
   ├─ Gallery (SwitchGridScreen) ◄── Capture/Recording
   └─ Gamepad Tester (no core)
        │
        ▼ (launch)
GameActivity ── RetroView (ROM bytes) ── Libretro core
   ├─ Auto-Ocarina HUD (pause menu)
   ├─ In-Game RA Overlay (unlock toast + leaderboards dialog)
   └─ Capture/Recording (menu → Captura)
```
