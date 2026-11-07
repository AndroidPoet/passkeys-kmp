@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import io.github.androidpoet.passkeys.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    androidTarget { publishLibraryVariants("release") }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":passkeys"))
            implementation(compose.runtime)
        }
        androidMain.dependencies {
            // LocalContext, to resolve the hosting Activity.
            implementation(compose.ui)
        }
        iosMain.dependencies {
            // LocalUIViewController, to resolve the presenting window at ceremony time.
            implementation(compose.ui)
        }
        jvmMain.dependencies {
            // ComposeWindow, to anchor the native sheet to the hosting Compose window.
            implementation(compose.ui)
        }
    }
}

android {
    namespace = "io.github.androidpoet.passkeys.compose"
    compileSdk = Configuration.COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "passkeys-compose", Configuration.VERSION)
}
