/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.IntentService;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.SyncHttpClient;

import java.util.List;

/**
 * This service handles voice commands and sends them to OpenHAB.
 * It will use the openHAB base URL if passed in the intent's extra.
 */
public class OpenHABVoiceService extends IntentService {
    private static final String TAG = OpenHABVoiceService.class.getSimpleName();

    public OpenHABVoiceService() {
        super("OpenHABVoiceService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        final String voiceCommand = extractVoiceCommand(intent);
        if (voiceCommand.isEmpty()) {
            return;
        }

        ConnectionFactory.waitForInitialization();
        Connection connection = null;

        try {
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            Log.w(TAG, "Couldn't determine OpenHAB URL", e);
        }

        if (connection != null) {
            sendItemCommand("VoiceCommand", voiceCommand, connection);
        } else {
            Toast.makeText(this,
                    R.string.error_couldnt_determine_openhab_url, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private String extractVoiceCommand(Intent data) {
        String voiceCommand = "";
        List<String> textMatchList = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (!textMatchList.isEmpty()) {
            voiceCommand = textMatchList.get(0);
        }
        Log.i(TAG, "Recognized text: " + voiceCommand);
        final String message = getString(R.string.info_voice_recognized_text, voiceCommand);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        return voiceCommand;
    }

    private void sendItemCommand(final String itemName, final String command, final Connection conn) {
        Log.d(TAG, "sendItemCommand(): itemName=" + itemName + ", command=" + command);
        SyncHttpClient.HttpStatusResult result = conn.getSyncHttpClient().post(
                "/rest/items/" + itemName, command, "text/plain;charset=UTF-8").asStatus();

        if (result.error != null) {
            Log.e(TAG, "Got command error " + result.statusCode, result.error);
        } else {
            Log.d(TAG, "Command was sent successfully");
        }
    }
}
