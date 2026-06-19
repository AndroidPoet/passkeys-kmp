plugins {
    // Version comes from the Kotlin Gradle plugin already on the build classpath
    // (declared at the root via the multiplatform alias); applying it with a
    // version here would clash.
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":passkeys-server"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
}

application {
    mainClass.set("io.github.androidpoet.passkeys.sample.server.MainKt")
}
