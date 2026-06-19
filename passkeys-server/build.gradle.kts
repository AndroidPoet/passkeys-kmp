import io.github.androidpoet.passkeys.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    // JVM-only: the Yubico WebAuthn server library and Ktor server are JVM. Kept
    // a multiplatform module (single jvm target) so it matches the repo's source
    // layout (src/jvmMain) and BCV/detekt/Dokka wiring.
    jvm()

    sourceSets {
        jvmMain.dependencies {
            api(libs.yubico.webauthn.server.core)
            api(libs.ktor.server.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.server.test.host)
            // The software authenticator test helper builds CBOR (COSE key /
            // attestation object); Yubico pulls cbor at runtime only.
            implementation(libs.upokecenter.cbor)
        }
    }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "passkeys-server", Configuration.VERSION)
}
