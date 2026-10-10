/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.wear.voice

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import java.util.Locale
import kotlinx.coroutines.launch
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.ui.WearTheme
import org.openhab.habdroid.wear.wearApplication

/**
 * Asks for a voice command and sends it to the openHAB voice interpreter
 */
class VoiceCommandActivity : ComponentActivity() {
    private sealed interface State {
        data object Listening : State

        data object Sending : State

        data class Sent(val text: String) : State

        data object Failed : State
    }

    private val state = mutableStateOf<State>(State.Listening)

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (text.isNullOrEmpty()) {
            finish()
        } else {
            send(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearTheme {
                AppScaffold {
                    Content(state.value)
                }
            }
        }
        if (savedInstanceState == null) {
            startListening()
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No speech recognizer available", e)
            state.value = State.Failed
        }
    }

    private fun send(text: String) {
        state.value = State.Sending
        lifecycleScope.launch {
            val connection = wearApplication.connectionManager.awaitConnection()?.connection
            state.value = if (connection != null && connection.sendVoiceCommand(text)) {
                State.Sent(text)
            } else {
                State.Failed
            }
        }
    }

    private suspend fun Connection.sendVoiceCommand(text: String): Boolean {
        val headers = mapOf("Accept-Language" to Locale.getDefault().language)
        return try {
            httpClient.post("rest/voice/interpreters", text, headers = headers).asStatus()
            true
        } catch (e: HttpClient.HttpException) {
            if (e.statusCode != 404) {
                Log.w(TAG, "Sending voice command failed", e)
                return false
            }
            // Older servers don't have the voice interpreter endpoint
            try {
                httpClient.post("rest/items/VoiceCommand", text).asStatus()
                true
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Sending voice command to item failed", e)
                false
            }
        }
    }

    @Composable
    private fun Content(current: State) {
        ScreenScaffold {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (current is State.Sending || current is State.Listening) {
                    CircularProgressIndicator()
                }
            }
        }
        ConfirmationDialog(
            visible = current is State.Sent,
            onDismissRequest = { finish() },
            text = { Text((current as? State.Sent)?.text.orEmpty(), textAlign = TextAlign.Center) }
        ) {
            ConfirmationDialogDefaults.SuccessIcon()
        }
        val failedText = stringResource(R.string.voice_failed)
        val curvedTextStyle = ConfirmationDialogDefaults.curvedTextStyle
        FailureConfirmationDialog(
            visible = current is State.Failed,
            onDismissRequest = { finish() },
            curvedText = { confirmationDialogCurvedText(failedText, curvedTextStyle) }
        )
    }

    companion object {
        private val TAG = VoiceCommandActivity::class.java.simpleName
    }
}
