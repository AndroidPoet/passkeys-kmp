import io.github.androidpoet.passkeys.Configuration
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// Relying party + Apple bundle id are configurable so the sample carries no
// real domain. Override per build, e.g.:
//   ./gradlew :sample:composeApp:run -PpasskeysSampleRpId=your-domain.com
//   ...                              -PpasskeysSampleBundleId=com.your.app
val passkeysSampleRpId: Provider<String> =
    providers.gradleProperty("passkeysSampleRpId").orElse("example.com")
val passkeysSampleBundleId: String =
    providers.gradleProperty("passkeysSampleBundleId").orElse("com.example.passkeys.sample").get()

// Generates SampleConfig.kt (commonMain) so the rpId is injected at build time
// rather than hard-coded in tracked source.
val generateSampleConfig =
    tasks.register("generateSampleConfig") {
        val outputDir = layout.buildDirectory.dir("generated/sampleConfig/kotlin")
        val rpId = passkeysSampleRpId
        outputs.dir(outputDir)
        doLast {
            val pkg = outputDir.get().dir("io/github/androidpoet/passkeys/composeapp").asFile
            pkg.mkdirs()
            pkg.resolve("SampleConfig.kt").writeText(
                """
                package io.github.androidpoet.passkeys.composeapp

                internal object SampleConfig {
                    const val RP_ID: String = "${rpId.get()}"
                }
                """.trimIndent() + "\n",
            )
        }
    }

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSampleConfig)
            dependencies {
                implementation(project(":passkeys"))
                implementation(project(":passkeys-compose"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "io.github.androidpoet.passkeys.composeapp"
    compileSdk = Configuration.COMPILE_SDK

    // Optional dedicated test keystore (shared with :sample:android). Its SHA-256
    // must be published in your relying party's assetlinks.json.
    val testKeystore = rootProject.file("sample/android/passkeys-test.keystore")

    defaultConfig {
        // Reuse the existing sample's package so a single assetlinks.json entry
        // (package + signing SHA-256) covers both samples.
        applicationId = "io.github.androidpoet.passkeys.sample"
        minSdk = Configuration.MIN_SDK
        targetSdk = Configuration.COMPILE_SDK
        versionCode = 1
        versionName = "0.1.0"
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
            getByName("debug") { signingConfig = signingConfigs.getByName("passkeysTest") }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.androidpoet.passkeys.composeapp.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "PasskeysKMP"
            packageVersion = "1.0.0"
            macOS {
                // Must match the appID in your relying party's AASA
                // (/.well-known/apple-app-site-association). Override with
                // -PpasskeysSampleBundleId=com.your.app
                bundleID = passkeysSampleBundleId
                // Associated Domains entitlement is injected at signing time; see
                // sample/composeApp/entitlements.plist.
            }
        }
    }
}
