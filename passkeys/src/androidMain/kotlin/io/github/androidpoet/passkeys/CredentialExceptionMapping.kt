package io.github.androidpoet.passkeys

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialCustomException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialException

private const val DOM_MESSAGE =
    "DOM error from WebAuthn. Check your Digital Asset Links at /.well-known/assetlinks.json."

internal fun CreateCredentialException.toCreatePasskeyException(): PasskeyException =
    when (this) {
        is CreateCredentialCancellationException -> PasskeyException.UserCanceled(this)
        is CreateCredentialInterruptedException -> PasskeyException.Interrupted(this)
        is CreateCredentialUnsupportedException -> PasskeyException.Unsupported(this)
        is CreatePublicKeyCredentialDomException -> PasskeyException.DomError(DOM_MESSAGE, this)
        is CreateCredentialNoCreateOptionException,
        is CreatePublicKeyCredentialException,
        -> PasskeyException.DomError(DOM_MESSAGE, this)
        is CreateCredentialUnknownException,
        is CreateCredentialCustomException,
        is CreateCredentialProviderConfigurationException,
        -> PasskeyException.Unexpected(this)
        else -> PasskeyException.Unexpected(this)
    }

internal fun GetCredentialException.toGetPasskeyException(): PasskeyException =
    when (this) {
        is GetCredentialCancellationException -> PasskeyException.UserCanceled(this)
        is GetCredentialInterruptedException -> PasskeyException.Interrupted(this)
        is GetCredentialUnsupportedException -> PasskeyException.Unsupported(this)
        is GetPublicKeyCredentialDomException -> PasskeyException.DomError(DOM_MESSAGE, this)
        is NoCredentialException -> PasskeyException.NoCredential(this)
        is GetPublicKeyCredentialException -> PasskeyException.DomError(DOM_MESSAGE, this)
        is GetCredentialUnknownException,
        is GetCredentialCustomException,
        is GetCredentialProviderConfigurationException,
        -> PasskeyException.Unexpected(this)
        else -> PasskeyException.Unexpected(this)
    }
