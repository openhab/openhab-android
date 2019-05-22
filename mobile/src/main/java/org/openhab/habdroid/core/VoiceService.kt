/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
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
import org.openhab.habdroid.util.SyncHttpClient
import org.openhab.habdroid.util.Util
import java.util.*

/**
 * This service handles voice commands and sends them to openHAB.
 */
class VoiceService : IntentService("VoiceService") {
    override fun onHandleIntent(intent: Intent?) {
        val voiceCommand = intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.elementAtOrNull(0)
                ?: return

        Log.i(TAG, "Recognized text: $voiceCommand")
        Util.showToast(this, getString(R.string.info_voice_recognized_text, voiceCommand))

        runBlocking {
            ConnectionFactory.waitForInitialization()
        }

        try {
            sendVoiceCommand(ConnectionFactory.usableConnection.syncHttpClient, voiceCommand)
        } catch (e: ConnectionException) {
            Log.w(TAG, "Couldn't determine openHAB URL", e)
            Util.showToast(this, getString(R.string.error_couldnt_determine_openhab_url))
        }
    }

    private fun sendVoiceCommand(client: SyncHttpClient, command: String) {
        val headers = mapOf("Accept-Language" to Locale.getDefault().language)
        var result = client.post("rest/voice/interpreters", command, "text/plain", headers).asStatus()

        if (result.statusCode == 404) {
            Log.d(TAG, "Voice interpreter endpoint returned 404, falling back to item")
            result = client.post("rest/items/VoiceCommand", command, "text/plain").asStatus()
        }
        if (result.isSuccessful) {
            Log.d(TAG, "Voice command was sent successfully")
        } else {
            Log.e(TAG, "Sending voice command failed", result.error)
        }
    }

    companion object {
        private val TAG = VoiceService::class.java.simpleName
    }
}
