import io.github.androidpoet.passkeys.Configuration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.androidpoet.passkeys.sample"
    compileSdk = Configuration.COMPILE_SDK

    defaultConfig {
        applicationId = "io.github.androidpoet.passkeys.sample"
        minSdk = Configuration.MIN_SDK
        targetSdk = Configuration.COMPILE_SDK
        versionCode = 1
        versionName = "0.1.0"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":passkeys"))
    implementation(libs.kotlinx.coroutines.android)
}
