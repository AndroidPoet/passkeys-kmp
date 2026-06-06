package io.github.androidpoet.passkeys.internal

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal fun String.normalizedBase64Url(): String {
    return decodeBase64Url().let(Base64.UrlSafe::encode).trimEnd('=')
}

@OptIn(ExperimentalEncodingApi::class)
internal fun String.decodeBase64Url(): ByteArray {
    val padded = replace('-', '+')
        .replace('_', '/')
        .let { value -> value + "=".repeat((4 - value.length % 4) % 4) }
    return Base64.Default.decode(padded)
}
