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
        status = TextView(this).apply {
            text = "Ready on ${android.os.Build.MODEL}. Replace the sample RP/backend payloads before real E2E."
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
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = 24
        }

    private fun createPasskey() {
        status.text = "Calling Credential Manager create..."
        scope.launch {
            when (val result = passkeys.create(PasskeyCreationOptions(sampleRegistrationOptions))) {
                is PasskeyResult.Success -> status.text = result.value.rawJson
                is PasskeyResult.Failure -> status.text = "${result.error.message} (${result.error.code})"
            }
        }
    }

    private fun authenticate() {
        status.text = "Calling Credential Manager authenticate..."
        scope.launch {
            when (val result = passkeys.authenticate(PasskeyAuthenticationOptions(sampleAuthenticationOptions))) {
                is PasskeyResult.Success -> status.text = result.value.rawJson
                is PasskeyResult.Failure -> status.text = "${result.error.message} (${result.error.code})"
            }
        }
    }

    private companion object {
        private val sampleRegistrationOptions = """
            {
              "challenge": "c2FtcGxlLWNoYWxsZW5nZQ",
              "rp": { "id": "example.com", "name": "Example" },
              "user": {
                "id": "c2FtcGxlLXVzZXI",
                "name": "user@example.com",
                "displayName": "Sample User"
              },
              "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
              "authenticatorSelection": { "userVerification": "preferred" }
            }
        """.trimIndent()

        private val sampleAuthenticationOptions = """
            {
              "challenge": "c2FtcGxlLWNoYWxsZW5nZQ",
              "rpId": "example.com",
              "allowCredentials": [],
              "userVerification": "preferred"
            }
        """.trimIndent()
    }
}
