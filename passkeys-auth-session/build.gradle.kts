import io.github.androidpoet.passkeys.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    androidTarget { publishLibraryVariants("release") }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // PasskeyResult / PasskeyException: one result vocabulary across modules.
            api(project(":passkeys"))
        }
        androidMain.dependencies {
            // Auth Tab (AuthTabIntent); its API exposes androidx.activity types.
            api(libs.androidx.browser)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "io.github.androidpoet.passkeys.authsession"
    // androidx.browser 1.9.0 (the first release with Auth Tab) requires compileSdk 36.
    compileSdk = Configuration.AUTH_SESSION_COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "passkeys-auth-session", Configuration.VERSION)
}
