import io.github.androidpoet.passkeys.Configuration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.androidpoet.passkeys.sample"
    compileSdk = Configuration.COMPILE_SDK

    val sampleRpId = providers.gradleProperty("passkeysSampleRpId").orElse("example.com").get()

    defaultConfig {
        applicationId = "io.github.androidpoet.passkeys.sample"
        minSdk = Configuration.MIN_SDK
        targetSdk = Configuration.COMPILE_SDK
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PASSKEYS_SAMPLE_RP_ID", "\"$sampleRpId\"")
        buildConfigField("Boolean", "REAL_PASSKEYS_ENABLED", "${sampleRpId != "example.com"}")
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":passkeys"))
    implementation(libs.kotlinx.coroutines.android)
}
