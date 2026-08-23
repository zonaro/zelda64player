// Zelda 64 Player — app module build file.
//
// Selective migration from Ludere (br.com.redclaw.ootdx). The emulation core
// (.so) files are fetched at build time by the `prepareCore` task (ported from
// Ludere) into app/src/main/jniLibs for every ABI.
import de.undercouch.gradle.tasks.download.DownloadSpec
import org.gradle.api.Action

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download")
}

android {
    namespace = "br.com.redclaw.zelda64player"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.redclaw.zelda64player"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // No signing config: the release keystore is generated before the
            // first release. Do NOT sign here (no keystore yet).
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    }
}

// Ported from Ludere: downloads the LibRetro cores (mupen64plus_next GLES3/GLES2
// and parallel_n64) for every ABI from the LibRetro buildbot into jniLibs. If a
// specific core+ABI combo is unavailable (404), it is skipped gracefully, exactly
// like the original implementation.
val prepareCore by tasks.registering {
    doLast {
        val cores = mapOf(
            "mupen64plus_next_gles3" to "libcore_mupen_gles3.so",
            "mupen64plus_next_gles2" to "libcore_mupen_gles2.so",
            "parallel_n64" to "libcore_parallel.so"
        )
        val abis = listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

        for (abi in abis) {
            val jniAbiFolder = file("${rootProject.projectDir}/app/src/main/jniLibs/$abi")
            if (jniAbiFolder.exists() && jniAbiFolder.list()?.isNotEmpty() == true)
                continue
            jniAbiFolder.mkdirs()

            for ((coreName, outputName) in cores) {
                val zipFile = file("$jniAbiFolder/${coreName}_libretro_android.so.zip")
                val url =
                    "https://buildbot.libretro.com/nightly/android/latest/$abi/${coreName}_libretro_android.so.zip"
                try {
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
                        rename("${coreName}_libretro_android.so", outputName)
                    }
                    project.delete(zipFile)
                } catch (e: Exception) {
                    println("Skipping $coreName for $abi (not available): ${e.message}")
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
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("com.github.swordfish90:radialgamepad:0.6.0")
    implementation("com.github.swordfish90:libretrodroid:0.6.2")

    // Required by the frozen gamepad/ package (CompositeDisposable, pad.events()).
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
}
