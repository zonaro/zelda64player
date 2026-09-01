# Testing — Zelda 64 Player

## 1. Stack

- **JUnit 5** — unit tests
- **MockK** — mocking
- **Turbine** — Flow testing
- **AndroidX Test** — instrumented tests

## 2. What Is Tested

| Area                              | Tests        | Notes                                          |
| --------------------------------- | ------------ | ---------------------------------------------- |
| Patcher N64 normalization         | JVM unit     | `.z64`/`.v64`/`.n64` → BE z64; magic detection |
| BPS parsing/validation            | JVM unit     | Varint, footer CRC32, command apply            |
| IPS apply                         | JVM unit     | Legacy format                                  |
| Checksums                         | JVM unit     | CRC32/MD5/SHA1 streaming                       |
| PatcherFacade integration         | JVM unit     | End-to-end synthetic ROM+patch                 |
| RetroAchievements identity        | JVM unit     | RA hash + catalog parsing                      |
| Physical-controller → N64 mapping | JVM unit     | Shared gameplay/tester mapping                 |
| BaseRomLibrarySource              | JVM unit     | Vanilla tile generation, family detection      |
| GameRomResolver                   | JVM unit     | `vanilla_*` vs `Storage.rom` resolution        |
| Cross-catalog dedup               | JVM unit     | `canonicalId`, `isSameHack`                    |
| Base ROM import flow              | Instrumented | SAF picker → validate → persist                |
| Catalog fetch/merge               | Instrumented | Conditional GET, multi-URL merge               |
| Download + validation             | Instrumented | Checksum, resume                               |
| RetroView patched ROM load        | Instrumented | ROM bytes reach core via `gameFilePath`        |

## 3. Fixtures

- Synthetic ROMs (valid headers, known checksums) + minimal BPS/IPS patches in `app/src/test/fixtures/`.
- **No real ROMs are committed** (Hard Rule 1/2).

## 4. Commands

```bash
./gradlew :app:testDebugUnitTest                       # JVM unit tests
./gradlew :app:connectedAndroidTest                   # Instrumented tests
./gradlew :app:testDebugUnitTest :app:connectedAndroidTest   # Both
```

## 5. Coverage Expectations

- 100% of `patcher/` module (pure Kotlin, fast, no Android).
- 100% of `public`/`internal` classes, functions, sealed classes in non-UI modules have KDoc (Wally).
- Visual QA (Chululu) on every screen before release candidates — see `.agents/chululu.md` checklist.
