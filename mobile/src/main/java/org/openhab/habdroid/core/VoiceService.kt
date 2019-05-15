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
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
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
        val voiceCommand = extractVoiceCommand(intent)
        if (voiceCommand.isEmpty()) {
            return
        }

        ConnectionFactory.waitForInitialization()
        var connection: Connection? = null

        try {
            connection = ConnectionFactory.usableConnection
        } catch (e: ConnectionException) {
            Log.w(TAG, "Couldn't determine openHAB URL", e)
        }

        if (connection != null) {
            sendVoiceCommand(connection.syncHttpClient, voiceCommand)
        } else {
            Util.showToast(this, getString(R.string.error_couldnt_determine_openhab_url))
        }
    }

    private fun extractVoiceCommand(data: Intent?): String {
        var voiceCommand = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.elementAtOrNull(0)
        if (voiceCommand != null) {
            Log.i(TAG, "Recognized text: $voiceCommand")
            Util.showToast(this, getString(R.string.info_voice_recognized_text, voiceCommand))
        }
        return voiceCommand.orEmpty()
    }

    private fun sendVoiceCommand(client: SyncHttpClient, command: String) {
        val headers = HashMap<String, String>()
        headers["Accept-Language"] = Locale.getDefault().language

        var result: SyncHttpClient.HttpStatusResult = client.post("rest/voice/interpreters",
                command, "text/plain", headers).asStatus()
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
