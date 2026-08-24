// Zelda 64 Player — app module build file.
//
// Selective migration from Ludere (br.com.redclaw.ootdx). The emulation core
// (.so) files are fetched at build time by the `prepareCore` task (ported from
// Ludere) into app/src/main/jniLibs for every ABI.
import de.undercouch.gradle.tasks.download.DownloadSpec
import org.gradle.api.Action
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download")
}

android {
    namespace = "br.com.redclaw.zelda64player"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.redclaw.zelda64player"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        // RetroAchievements native runtime: same ABI set as the prebuilt cores.
        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
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
        val keystoreProps = Properties().apply {
            keystorePropsFile.inputStream().use { load(it) }
        }
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

    kotlinOptions {
        jvmTarget = "1.8"
    }

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
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
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

val prepareCore by tasks.registering {
    doLast {
        val abis = listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

        // coreName -> target (output .so name + ordered candidate URL list).
        // The first candidate that downloads successfully wins; failures fall
        // through to the next candidate.
        val cores = mapOf(
            "mupen64plus_next_gles3" to CoreTarget(
                outputName = "libcore_mupen_gles3.so",
                candidates = listOf(
                    CoreCandidate(
                        url = "https://buildbot.libretro.com/nightly/android/latest/{abi}/{core}_libretro_android.so.zip",
                        isZip = true,
                        label = "buildbot nightly zip"
                    )
                )
            ),
            "parallel_n64" to CoreTarget(
                outputName = "libcore_parallel.so",
                candidates = listOf(
                    CoreCandidate(
                        url = "https://github.com/zonaro/zelda64player/releases/download/parallel-n64-latest/parallel_n64_libretro_android_{abi}.so",
                        isZip = false,
                        label = "self-built rolling release"
                    ),
                    CoreCandidate(
                        url = "https://buildbot.libretro.com/nightly/android/latest/{abi}/{core}_libretro_android.so.zip",
                        isZip = true,
                        label = "buildbot nightly zip"
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
                    val url = candidate.url
                        .replace("{abi}", abi)
                        .replace("{core}", coreName)
                    val zipFile = file("$jniAbiFolder/${coreName}_libretro_android.so.zip")
                    try {
                        if (candidate.isZip) {
                            project.download.run(object : Action<DownloadSpec> {
                                override fun execute(spec: DownloadSpec) {
                                    spec.src(url)
                                    spec.dest(zipFile)
                                    spec.overwrite(true)
                                }
                            })
                            project.copy {
                                from(project.zipTree(zipFile))
                                into(jniAbiFolder)
                                rename("${coreName}_libretro_android.so", target.outputName)
                            }
                            project.delete(zipFile)
                        } else {
                            project.download.run(object : Action<DownloadSpec> {
                                override fun execute(spec: DownloadSpec) {
                                    spec.src(url)
                                    spec.dest(outputFile)
                                    spec.overwrite(true)
                                }
                            })
                        }
                        println("Fetched $coreName for $abi (${candidate.label})")
                        break
                    } catch (e: Exception) {
                        println("Candidate failed for $coreName/$abi (${candidate.label}): ${e.message}")
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

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
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

    // OoTR Randomizer: encrypted storage of the user's API key (AES256 master key).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
