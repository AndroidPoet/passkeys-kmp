package io.github.androidpoet.passkeys.composeapp

actual fun platformName(): String = System.getProperty("os.name") ?: "Desktop"
