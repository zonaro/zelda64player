// Zelda 64 Player — app module build file.
//
// Selective migration from Ludere (br.com.redclaw.ootdx). The emulation core
// (.so) files are fetched at build time by the `prepareCore` task (ported from
// Ludere) into app/src/main/jniLibs for every ABI.
import de.undercouch.gradle.tasks.download.DownloadSpec
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import org.gradle.api.Action

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("de.undercouch.download")
}

android {
    namespace = "br.com.redclaw.zelda64player"
    compileSdk = 35

    // Generate version at build time: {yy}.{dayOfYear}.{hhmm}
    // Example: 26.238.1430 = 2026, day 238 (Aug 26), 14:30
    val buildTime = LocalDateTime.now()
    val generatedVersionName = buildTime.format(DateTimeFormatter.ofPattern("yy.DDD.HHmm"))
    // versionCode: yydddHHmm as integer (e.g., 262381430)
    val generatedVersionCode = buildTime.format(DateTimeFormatter.ofPattern("yyDDDHHmm")).toInt()

    defaultConfig {
        applicationId = "br.com.redclaw.zelda64player"
        minSdk = 24
        targetSdk = 35
        versionCode = generatedVersionCode
        versionName = generatedVersionName

        // RetroAchievements native runtime: same ABI set as the prebuilt cores.
        ndk { abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }

    // Release signing — only active when a local keystore.properties exists
    // (generated for the first release; gitignored, never committed). Builds
    // without it fall back to an unsigned release artifact.
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        val keystoreProps = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile")!!)
                storePassword = keystoreProps.getProperty("storePassword")!!
                keyAlias = keystoreProps.getProperty("keyAlias")!!
                keyPassword = keystoreProps.getProperty("keyPassword")!!
            }
        }
        buildTypes["release"].signingConfig = signingConfigs["release"]
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // RetroAchievements runtime: rcheevos (vendored, MIT) + app JNI bridge,
    // compiled for the same ABI set as the prebuilt libretro cores.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Keep .so cores as real files inside the APK so LibretroDroid can dlopen
    // them from the native library directory. Without this, AGP extracts the
    // libraries to a location the dynamic linker cannot execute from on modern
    // Android, and the core fails to load ("Core library missing from
    // nativeLibraryDir").
    packaging { jniLibs { useLegacyPackaging = true } }
}

// Ported from Ludere: downloads the LibRetro cores (mupen64plus_next GLES3 and
// parallel_n64) for every ABI into jniLibs. mupen64plus_next is fetched as a zip
// from the LibRetro buildbot. parallel_n64 is fetched from our self-built rolling
// release (updated dynarec binaries) first, and falls back to the buildbot
// nightly zip if that is unavailable. If a specific core+ABI combo is unavailable
// (404 on every candidate), it is skipped gracefully, exactly like the original
// implementation. Cores already present in jniLibs are not re-downloaded.

// A download candidate for a core. `url` may contain the placeholders {abi} and
// {core}, which are substituted at runtime. `isZip` candidates are downloaded as
// an archive and extracted (renamed to the output .so); non-zip candidates are
// downloaded directly to the output .so.
data class CoreCandidate(val url: String, val isZip: Boolean, val label: String)

data class CoreTarget(val outputName: String, val candidates: List<CoreCandidate>)

val prepareCore by
        tasks.registering {
            doLast {
                val abis = listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

                // coreName -> target (output .so name + ordered candidate URL list).
                // The first candidate that downloads successfully wins; failures fall
                // through to the next candidate.
                val cores =
                        mapOf(
                                "mupen64plus_next_gles3" to
                                        CoreTarget(
                                                outputName = "libcore_mupen_gles3.so",
                                                candidates =
                                                        listOf(
                                                                CoreCandidate(
                                                                        url =
                                                                                "https://buildbot.libretro.com/nightly/android/latest/{abi}/{core}_libretro_android.so.zip",
                                                                        isZip = true,
                                                                        label =
                                                                                "buildbot nightly zip"
                                                                )
                                                        )
                                        ),
                                "parallel_n64" to
                                        CoreTarget(
                                                outputName = "libcore_parallel.so",
                                                candidates =
                                                        listOf(
                                                                CoreCandidate(
                                                                        url =
                                                                                "https://github.com/zonaro/zelda64player/releases/download/parallel-n64-latest/parallel_n64_libretro_android_{abi}.so",
                                                                        isZip = false,
                                                                        label =
                                                                                "self-built rolling release"
                                                                ),
                                                                CoreCandidate(
                                                                        url =
                                                                                "https://buildbot.libretro.com/nightly/android/latest/{abi}/{core}_libretro_android.so.zip",
                                                                        isZip = true,
                                                                        label =
                                                                                "buildbot nightly zip"
                                                                )
                                                        )
                                        )
                        )

                for (abi in abis) {
                    val jniAbiFolder = file("${rootProject.projectDir}/app/src/main/jniLibs/$abi")
                    jniAbiFolder.mkdirs()

                    for ((coreName, target) in cores) {
                        val outputFile = file("$jniAbiFolder/${target.outputName}")
                        if (outputFile.exists()) {
                            println("Skipping $coreName for $abi (already present)")
                            continue
                        }

                        for (candidate in target.candidates) {
                            val url =
                                    candidate.url.replace("{abi}", abi).replace("{core}", coreName)
                            val zipFile = file("$jniAbiFolder/${coreName}_libretro_android.so.zip")
                            try {
                                if (candidate.isZip) {
                                    project.download.run(
                                            object : Action<DownloadSpec> {
                                                override fun execute(spec: DownloadSpec) {
                                                    spec.src(url)
                                                    spec.dest(zipFile)
                                                    spec.overwrite(true)
                                                }
                                            }
                                    )
                                    project.copy {
                                        from(project.zipTree(zipFile))
                                        into(jniAbiFolder)
                                        rename("${coreName}_libretro_android.so", target.outputName)
                                    }
                                    project.delete(zipFile)
                                } else {
                                    project.download.run(
                                            object : Action<DownloadSpec> {
                                                override fun execute(spec: DownloadSpec) {
                                                    spec.src(url)
                                                    spec.dest(outputFile)
                                                    spec.overwrite(true)
                                                }
                                            }
                                    )
                                }
                                println("Fetched $coreName for $abi (${candidate.label})")
                                break
                            } catch (e: Exception) {
                                println(
                                        "Candidate failed for $coreName/$abi (${candidate.label}): ${e.message}"
                                )
                                // Clean up partial artifacts so the next candidate starts fresh.
                                if (zipFile.exists()) project.delete(zipFile)
                                if (outputFile.exists()) project.delete(outputFile)
                            }
                        }
                    }
                }
            }
        }

tasks.named("preBuild") { dependsOn(prepareCore) }

// Builds a host (linux x86_64) copy of the xdelta3 native decoder + CLI so the
// JVM unit tests can exercise XdeltaApplier end-to-end without the Android
// toolchain. The artifacts live under build/xdeltaHost and are never committed.
// The SIZEOF_* values match a 64-bit linux host (int=4, long/long-long/void*/size_t=8).
val xdeltaHostOut = layout.buildDirectory.dir("xdeltaHost")
val buildXdeltaHost by
        tasks.registering(Exec::class) {
            group = "native"
            description = "Build host xdelta3 .so + CLI for JVM unit tests"
            val srcDir = file("src/main/cpp/xdelta3")
            val jniFile = file("src/main/cpp/xdelta_jni.c")
            val javaHome = System.getProperty("java.home")
            val out = xdeltaHostOut.get().asFile
            inputs.files(jniFile, fileTree(srcDir))
            outputs.files(File(out, "libxdelta_jni.so"), File(out, "xdelta3"))
            commandLine(
                    "bash",
                    "-c",
                    "set -e; " +
                            "mkdir -p ${out.absolutePath}; " +
                            "gcc -shared -fPIC ${jniFile.absolutePath} ${srcDir.absolutePath}/xdelta3.c " +
                            "-DXD3_MAIN=1 -DSIZEOF_INT=4 -DSIZEOF_LONG=8 -DSIZEOF_LONG_LONG=8 " +
                            "-DSIZEOF_VOID_P=8 -DSIZEOF_SIZE_T=8 -DSIZEOF_UNSIGNED_INT=4 " +
                            "-DSIZEOF_UNSIGNED_LONG=8 -DSIZEOF_UNSIGNED_LONG_LONG=8 " +
                            "-DXD3_USE_LARGESIZET=1 -DXD3_USE_LARGEFILE64=0 -D_FILE_OFFSET_BITS=64 " +
                            "-I${javaHome}/include -I${javaHome}/include/linux " +
                            "-o ${out.absolutePath}/libxdelta_jni.so; " +
                            "gcc ${srcDir.absolutePath}/xdelta3.c " +
                            "-DXD3_MAIN=1 -DSIZEOF_INT=4 -DSIZEOF_LONG=8 -DSIZEOF_LONG_LONG=8 " +
                            "-DSIZEOF_VOID_P=8 -DSIZEOF_SIZE_T=8 -DSIZEOF_UNSIGNED_INT=4 " +
                            "-DSIZEOF_UNSIGNED_LONG=8 -DSIZEOF_UNSIGNED_LONG_LONG=8 " +
                            "-DXD3_USE_LARGESIZET=1 -DXD3_USE_LARGEFILE64=0 -D_FILE_OFFSET_BITS=64 " +
                            "-o ${out.absolutePath}/xdelta3"
            )
        }

tasks.withType<Test>().configureEach {
    dependsOn(buildXdeltaHost)
    systemProperty(
            "zelda64.xdelta.jni.path",
            File(xdeltaHostOut.get().asFile, "libxdelta_jni.so").absolutePath
    )
    systemProperty(
            "zelda64.xdelta.cli.path",
            File(xdeltaHostOut.get().asFile, "xdelta3").absolutePath
    )
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("com.github.swordfish90:radialgamepad:0.6.0")
    // Vendored LibretroDroid 0.13.2 (local module) instead of the JitPack AAR:
    // adds getMemoryData/getMemorySize JNI passthroughs so the RetroAchievements
    // runtime (rcheevos) can read emulated memory. See libretrodroid/build.gradle.kts.
    implementation(project(":libretrodroid"))

    // Background periodic catalog refresh (WorkManager).
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hack Store: image loading (covers) and HTTP (catalog fetch + patch download).
    implementation("io.coil-kt:coil:2.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Required by the frozen gamepad/ package (CompositeDisposable, pad.events()).
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    // EncryptedSharedPreferences (AES256 master key) for RetroAchievements credentials.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google Drive cloud backup: account picker + OAuth2 token retrieval. The
    // Drive REST API v3 is called directly over OkHttp (already a dependency) to
    // avoid the heavy, deprecated google-api-services-drive client. Only the
    // drive.file scope is requested, so the app can only see files it created.
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // Self-hosted dashboard (Ktor 3.x embedded server) + WebRTC native streaming.
    // Ktor 3.x requires Kotlin 2.0+; the project is pinned to Kotlin 2.1.20.
    // NOTE: in Ktor 3.x the static-resources routes were merged into
    // ktor-server-core, so there is no separate ktor-server-static-resources artifact.
    val ktorVersion = "3.1.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    // WebRTC native Android SDK for peer-to-peer streaming.
    // Published on JitPack (org.webrtc:google-webrtc is not on Maven Central/Google Maven).
    implementation("com.github.webrtc-sdk:android:v104.5112.10")
}
