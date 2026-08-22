// Zelda 64 Player — root build file.
//
// Toolchain: Gradle 8.11.1 + AGP 8.11.1 + Kotlin 1.9.22 (modern stack, Kotlin DSL).
// AGP 8 requires the `namespace` declared in the android block of the app module
// (the legacy `package` attribute in AndroidManifest.xml is no longer used) and
// JDK 17, which is available on this machine.
//
// Note on the fallback: a known-good combo (Gradle 7.6 + AGP 7.4.2 + Kotlin 1.8.x
// with de.undercouch.download 4.1.1) was considered, but the modern stack is
// preferred per plano.md and all of its artifacts (AGP 8.11.1, Kotlin 1.9.22,
// Gradle 8.11.1) are already present in the local Gradle cache, so it builds
// reproducibly without extra downloads.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("de.undercouch.download") version "5.6.0" apply false
}

tasks.register("clean") {
    delete(rootProject.layout.buildDirectory)
    delete(file("${rootProject.projectDir}/app/src/main/jniLibs"))
}
