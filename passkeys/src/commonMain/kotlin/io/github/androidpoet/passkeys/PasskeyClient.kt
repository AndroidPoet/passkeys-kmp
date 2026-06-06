package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse

public interface PasskeyClient {
    public suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse>

    public suspend fun create(requestJson: String): PasskeyResult<PasskeyCreationResponse> =
        create(PasskeyCreationOptions(requestJson))

    public suspend fun authenticate(options: PasskeyAuthenticationOptions): PasskeyResult<PasskeyAuthenticationResponse>

    public suspend fun authenticate(requestJson: String): PasskeyResult<PasskeyAuthenticationResponse> =
        authenticate(PasskeyAuthenticationOptions(requestJson))
}
