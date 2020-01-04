/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

package org.openhab.habdroid.core

import android.app.IntentService
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.exception.ConnectionException
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.showToast
import java.util.Locale

/**
 * This service handles voice commands and sends them to openHAB.
 */
class VoiceService : IntentService("VoiceService") {
    override fun onHandleIntent(intent: Intent?) {
        val voiceCommand = intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.elementAtOrNull(0)
            ?: return

        Log.i(TAG, "Recognized text: $voiceCommand")
        showToast(getString(R.string.info_voice_recognized_text, voiceCommand))

        runBlocking {
            ConnectionFactory.waitForInitialization()
            try {
                val client = ConnectionFactory.usableConnection.httpClient
                val headers = mapOf("Accept-Language" to Locale.getDefault().language)
                sendVoiceCommand(client, voiceCommand, headers)
                Log.d(TAG, "Voice command was sent successfully")
            } catch (e: ConnectionException) {
                Log.w(TAG, "Couldn't determine openHAB URL", e)
                showToast(R.string.error_couldnt_determine_openhab_url, ToastType.ERROR)
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "Sending voice command failed", e)
            }
        }
    }

    private suspend fun sendVoiceCommand(client: HttpClient, command: String, headers: Map<String, String>) {
        try {
            client.post("rest/voice/interpreters", command, "text/plain", headers).close()
        } catch (e: HttpClient.HttpException) {
            if (e.statusCode == 404) {
                Log.d(TAG, "Voice interpreter endpoint returned 404, falling back to item")
                client.post("rest/items/VoiceCommand", command, "text/plain").close()
            } else {
                throw e
            }
        }
    }

    companion object {
        private val TAG = VoiceService::class.java.simpleName
    }
}
