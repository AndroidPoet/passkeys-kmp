@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import io.github.androidpoet.passkeys.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    // The Apple platform gate (ApplePasskeyPlatform) is an expect/actual object;
    // opt in to the still-Beta multiplatform expect/actual classes feature.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget { publishLibraryVariants("release") }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    linuxX64 {
        compilations.getByName("main").cinterops.create("libfido2") {
            defFile(project.file("src/nativeInterop/cinterop/libfido2.def"))
            packageName("fido2")
        }
    }
    mingwX64 {
        compilations.getByName("main").cinterops.create("webauthn") {
            defFile(project.file("src/nativeInterop/cinterop/webauthn.def"))
            packageName("webauthn")
            // The official MS webauthn.h is vendored next to the .def; add that
            // directory to the include path so the umbrella header resolves it.
            includeDirs(project.file("src/nativeInterop/cinterop"))
        }
    }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            api(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.github.androidpoet.passkeys"
    compileSdk = Configuration.COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "passkeys", Configuration.VERSION)
}
