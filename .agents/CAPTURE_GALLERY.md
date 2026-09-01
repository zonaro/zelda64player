# Capture & Gallery — Screenshots, Recording, Local Gallery

## Overview
Zelda 64 Player lets users capture their gameplay: take screenshots (with or without the on-screen overlay), record gameplay video via MediaProjection, and browse/manage captures in a local Gallery. All captures are stored on-device (no cloud upload).

## What It Does
1. **Screenshots (2 modes):**
   - **With overlay:** captures the full `GLRetroView` including HUD/RadialGamePad/achievement overlays (standard `PixelCopy` of the SurfaceView).
   - **Without overlay:** captures only the emulated frame (core render) — hides overlays before `PixelCopy`, restores after. Useful for clean ROM-hack showcase shots.
2. **Recording:** `MediaProjection` captures the screen to an MP4 (H.264) in `Movies/Zelda64Player/`. Started/stopped from the in-game menu.
3. **Gallery:** A local grid (`SwitchGridScreen`-style) lists all captures (images + videos) with thumbnail, date, game; supports **view** (open in viewer/player), **share** (Intent share), **delete**.

## How to Use
- **In-game menu → "Captura"** (Capture) submenu:
  - "Screenshot (com HUD)" / "Screenshot (sem HUD)"
  - "Gravar tela" (start recording) / "Parar gravação" (stop)
- **Dock:** The 5th dock button (Gallery icon, Dolfi asset) opens the Gallery from Library Home.
- **Settings toggle:** "Salvar capturas na galeria do sistema" (also add to system MediaStore) — default ON.

## Package (`capture/` + `gallery/`)
```
capture/
├── CaptureManager.kt        # Orchestrates PixelCopy (with/without overlay), MediaProjection lifecycle
├── ScreenshotTask.kt        # Async PixelCopy + save to filesDir/captures/ + MediaStore insert
├── ScreenRecorder.kt        # MediaProjection + MediaRecorder wrapper; start/stop; filename by timestamp
└── CapturePermissions.kt    # READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / POST_NOTIFICATIONS checks
gallery/
├── GalleryActivity.kt       # SwitchGridScreen list of captures
├── GalleryAdapter.kt        # RecyclerView adapter (image/video thumbnails via Coil)
├── CaptureViewerActivity.kt # Fullscreen view/play; share + delete actions
└── GalleryRepository.kt     # Scans filesDir/captures/ + MediaStore; models CaptureItem
```

## Permissions
- `android.permission.READ_MEDIA_IMAGES` (API 33+) / `READ_EXTERNAL_STORAGE` (legacy) — browse system gallery
- `android.permission.READ_MEDIA_VIDEO` (API 33+) / `READ_EXTERNAL_STORAGE` (legacy) — browse videos
- `android.permission.POST_NOTIFICATIONS` (API 33+) — capture-complete notification
- `android.permission.FOREGROUND_SERVICE` + `MEDIA_PROJECTION` service type — recording foreground service
- **MediaProjection** consent dialog shown at recording start (system).

## Notes
- No base ROM content is captured in "without overlay" mode beyond the emulated frame (which is user's own ROM + patch) — compliant with Hard Rules 1–2 (no distribution of base ROMs; captures are user-local).
- i18n: all menu labels in `strings.xml` (pt-BR/en/es).
