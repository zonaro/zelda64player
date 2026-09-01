# Build & Native — Zelda 64 Player

## 1. Requirements

- Android Studio (latest)
- JDK 17
- Android SDK 34 (Platform + Build-Tools)
- NDK r26+ (for rcheevos + JNI bridge)
- Internet (first build fetches Libretro cores)

## 2. Build Commands

```bash
git clone https://github.com/zonaro/zelda64player.git
cd zelda64player
./gradlew assembleDebug
```

The first build runs a `prepareCore` Gradle task that downloads the two Libretro cores into `app/src/main/jniLibs`:
- `mupen64plus_next` (GLES3) — zip from buildbot.libretro.com
- `parallel_n64` — self-built rolling release (see `.github/workflows/build-parallel-n64.yml`, builds updated dynarec binaries from libretro/parallel-n64 with current NDK), falls back to buildbot nightly zip if unavailable

Cores already present in `jniLibs` are not re-downloaded; any core unavailable for a given ABI is skipped gracefully.

## 3. Gradle Structure

- Root `build.gradle.kts` (Kotlin DSL) + `settings.gradle.kts`
- Version catalogs: `gradle/libs.versions.toml`
- Modules: `:app`, `:libretrodroid` (vendored)
- `app/build.gradle.kts`: `externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }`

## 4. Native Build (CMake + rcheevos)

```
app/
├── src/main/cpp/
│   ├── CMakeLists.txt              # add_subdirectory(rcheevos), link rcheevos + libretrodroid JNI
│   ├── rcheevos/                   # VENDORED rcheevos sources (git subtree pinned to tag)
│   │   ├── include/rc_client.h, rc_api_*.h
│   │   ├── src/rc_client.c, rc_api_*.c, rc_compat.c, md5.c, sha1.c
│   │   └── LICENSE (MIT)           # MUST be kept
│   ├── ra_jni_bridge.c/.h          # Thin JNI: RcheevosJni + LibretroDroidMemoryJni
└── libretrodroid/                  # Separate Gradle module (vendor)
    ├── build.gradle.kts
    └── src/main/...                # LibretroDroid 0.13.2 + 2 JNI passthroughs
```

**Vendoring strategy:** `git subtree add --prefix=app/src/main/cpp/rcheevos https://github.com/RetroAchievements/rcheevos.git master --squash` (pinned to ~12.x). Updates via `git subtree pull ... --squash`.

**ABI coverage:** x86, x86_64, armeabi-v7a, arm64-v8a (matches `jniLibs`).

**Debug symbols:** `debug` keeps `-g`, disables strip; `release` uses `-O2 -DNDEBUG`, strip enabled (default AGP).

## 5. Testing Commands

```bash
./gradlew :app:testDebugUnitTest                       # JVM unit tests
./gradlew :app:connectedAndroidTest                   # Instrumented tests
./gradlew :app:assembleRelease                        # Release build (signing via keystore.properties)
```

## 6. Release

- `release.sh` script wraps release build + signing.
- `zelda64player-release.keystore` + `keystore.properties` (gitignored) for signing.
- Generate your own keystore before first release (do NOT reuse Ludere's keystore).

## 7. .gitignore Notes

Adapted from Ludere + adds `*.bps`, `catalog.json`, `base_roms/`. See repo `.gitignore`.
