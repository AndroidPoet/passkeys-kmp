pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "passkeys-kmp"

include(":passkeys")
include(":passkeys-compose")
include(":passkeys-server")
include(":sample:web")
include(":sample:composeApp")
include(":sample:server")
