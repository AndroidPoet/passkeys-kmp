package io.github.androidpoet.passkeys

public sealed class PasskeyResult<out T> {
    public data class Success<T>(public val value: T) : PasskeyResult<T>()
    public data class Failure(public val error: PasskeyException) : PasskeyResult<Nothing>()
}
