// LibretroDroid 0.13.2 — vendored local module.
//
// Vendored from https://github.com/Swordfish90/LibretroDroid (tag 0.13.2,
// GPL-3.0, Copyright (C) Filippo Scognamiglio) so the app can access core
// memory for the RetroAchievements runtime (rcheevos). The only functional
// changes against upstream are the getMemoryData/getMemorySize JNI
// passthroughs; everything else is kept verbatim (package name included).
//
// Submodules vendored in-tree: oboe (1.5-stable, b15f5e39) and
// libretro/libretro-common (public domain / MIT per-file headers).

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.swordfish.libretrodroid"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                // Available arguments are inside ${SDK}/cmake/.../android.toolchain.cmake file
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
