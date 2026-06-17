package io.github.androidpoet.passkeys.sample

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.androidpoet.passkeys.AndroidPasskeyClient
import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var passkeys: AndroidPasskeyClient
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passkeys = AndroidPasskeyClient(this)
        status =
            TextView(this).apply {
                text =
                    if (BuildConfig.REAL_PASSKEYS_ENABLED) {
                        "Ready on ${android.os.Build.MODEL} for ${BuildConfig.PASSKEYS_SAMPLE_RP_ID}."
                    } else {
                        setupMessage()
                    }
                textSize = 15f
                setTextColor(0xFF334155.toInt())
            }

        setContentView(contentView())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun contentView(): ScrollView =
        ScrollView(this).apply {
            setBackgroundColor(0xFFF8FAFC.toInt())
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(40, 56, 40, 56)
                    addView(title())
                    addView(status, blockParams())
                    addView(actionButton("Create Passkey") { createPasskey() }, blockParams())
                    addView(actionButton("Authenticate") { authenticate() }, blockParams())
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

    private fun title(): TextView =
        TextView(this).apply {
            text = "Passkeys KMP"
            textSize = 28f
            setTextColor(0xFF0F172A.toInt())
        }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun blockParams(): LinearLayout.LayoutParams =
        LinearLayout
            .LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 24
            }

    private fun createPasskey() {
        if (!BuildConfig.REAL_PASSKEYS_ENABLED) {
            status.text = setupMessage()
            return
        }
        status.text = "Calling Credential Manager create..."
        scope.launch {
            when (val result = passkeys.create(PasskeyCreationOptions(sampleRegistrationOptions))) {
                is PasskeyResult.Success -> status.text = "✅ Passkey created successfully\n\n${result.value.rawJson}"
                is PasskeyResult.Failure -> status.text = "❌ ${result.error.message} (${result.error.code})"
            }
        }
    }

    private fun authenticate() {
        if (!BuildConfig.REAL_PASSKEYS_ENABLED) {
            status.text = setupMessage()
            return
        }
        status.text = "Calling Credential Manager authenticate..."
        scope.launch {
            when (val result = passkeys.authenticate(PasskeyAuthenticationOptions(sampleAuthenticationOptions))) {
                is PasskeyResult.Success -> status.text = "✅ Login successful\n\n${result.value.rawJson}"
                is PasskeyResult.Failure -> status.text = "❌ ${result.error.message} (${result.error.code})"
            }
        }
    }

    private fun setupMessage(): String =
        """
        Android passkeys need a real RP domain.

        Rebuild with:
        ./gradlew :sample:android:installDebug -PpasskeysSampleRpId=your-domain.com

        Publish a Digital Asset Links entry on that domain for your build:
        package: io.github.androidpoet.passkeys.sample
        sha256: <your signing cert SHA-256 — run `./gradlew :sample:android:signingReport`>
        """.trimIndent()

    private companion object {
        private val sampleRegistrationOptions: String
            get() =
                """
                {
                  "challenge": "c2FtcGxlLWNoYWxsZW5nZQ",
                  "rp": { "id": "${BuildConfig.PASSKEYS_SAMPLE_RP_ID}", "name": "Passkeys KMP Sample" },
                  "user": {
                    "id": "c2FtcGxlLXVzZXI",
                    "name": "user@example.com",
                    "displayName": "Sample User"
                  },
                  "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
                  "authenticatorSelection": {
                    "residentKey": "required",
                    "requireResidentKey": true,
                    "userVerification": "required"
                  }
                }
                """.trimIndent()

        private val sampleAuthenticationOptions: String
            get() =
                """
                {
                  "challenge": "c2FtcGxlLWNoYWxsZW5nZQ",
                  "rpId": "${BuildConfig.PASSKEYS_SAMPLE_RP_ID}",
                  "allowCredentials": [],
                  "userVerification": "preferred"
                }
                """.trimIndent()
    }
}
