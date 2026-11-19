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

    // Android only: WKWebView on iOS supports passkeys without any switch.
    androidTarget { publishLibraryVariants("release") }

    sourceSets {
        androidMain.dependencies {
            // WebView WebAuthn switch (WebSettingsCompat.setWebAuthenticationSupport).
            implementation(libs.androidx.webkit)
        }
    }
}

android {
    namespace = "io.github.androidpoet.passkeys.webview"
    compileSdk = Configuration.COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "passkeys-webview", Configuration.VERSION)
}
