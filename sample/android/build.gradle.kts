import io.github.androidpoet.passkeys.Configuration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.androidpoet.passkeys.sample"
    compileSdk = Configuration.COMPILE_SDK

    val sampleRpId = providers.gradleProperty("passkeysSampleRpId").orElse("example.com").get()

    // Optional dedicated test keystore. Its SHA-256 must be published in the
    // relying party's /.well-known/assetlinks.json for passkeys to verify.
    // Generate with: keytool -genkeypair -keystore passkeys-test.keystore
    //   -alias passkeys-test -keyalg RSA -keysize 2048 -validity 10000
    //   -storepass passkeystest -keypass passkeystest -dname "CN=Passkeys KMP Test"
    val testKeystore = file("passkeys-test.keystore")

    defaultConfig {
        applicationId = "io.github.androidpoet.passkeys.sample"
        minSdk = Configuration.MIN_SDK
        targetSdk = Configuration.COMPILE_SDK
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PASSKEYS_SAMPLE_RP_ID", "\"$sampleRpId\"")
        buildConfigField("Boolean", "REAL_PASSKEYS_ENABLED", "${sampleRpId != "example.com"}")
    }

    if (testKeystore.exists()) {
        signingConfigs {
            create("passkeysTest") {
                storeFile = testKeystore
                storePassword = "passkeystest"
                keyAlias = "passkeys-test"
                keyPassword = "passkeystest"
            }
        }
        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("passkeysTest")
            }
        }
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
