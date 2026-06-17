package io.github.androidpoet.passkeys.composeapp

import android.os.Build

actual fun platformName(): String = Build.MODEL
