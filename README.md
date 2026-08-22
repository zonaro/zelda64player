# Zelda 64 Player

A native Android (Kotlin) emulator frontend for Nintendo 64 Zelda hacks
(Ocarina of Time, Majora's Mask) with on-the-fly BPS patching of
user-provided base ROMs.

This project is a derivative of **Ludere** (a fork of Swordfish90/Ludere,
GPL-3.0) and is licensed GPL-3.0 in its entirety.

## Philosophy

The app **never** ships, downloads, or includes base ROMs. Users must legally
import their own Ocarina of Time / Majora's Mask ROMs. The app downloads only
BPS patches from a GitHub-hosted JSON catalog and applies them in cache before
emulation.

## Tech Stack

- Kotlin 1.9 + Jetpack / AndroidX
- Gradle 8.14 + Android Gradle Plugin 8.11.1 (Kotlin DSL)
- LibretroDroid 0.6.2 (emulation core frontend)
- RadialGamePad 0.6.0 (touch controls)
- minSdk 24, targetSdk / compileSdk 34

## Build

```bash
./gradlew assembleDebug
```

The build fetches the LibRetro cores (mupen64plus_next GLES3/GLES2 and
parallel_n64) for every ABI from the LibRetro buildbot into
`app/src/main/jniLibs` at build time.

## Status

Phase 0 (foundation + selective migration from Ludere) is complete. See
`plano.md` for the full roadmap (Phases 1-4: BPS patcher, Hack Store,
Settings, polish).

## License

GPL-3.0. See `LICENSE`.
