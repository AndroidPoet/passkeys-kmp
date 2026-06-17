@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "passkeys-web-sample.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":passkeys"))
            implementation(libs.kotlinx.browser)
        }
    }
}
