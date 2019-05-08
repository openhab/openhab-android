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
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import androidx.annotation.Nullable;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.SyncHttpClient;
import org.openhab.habdroid.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * This service handles voice commands and sends them to openHAB.
 */
public class VoiceService extends IntentService {
    private static final String TAG = VoiceService.class.getSimpleName();

    public VoiceService() {
        super("VoiceService");
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
            Log.w(TAG, "Couldn't determine openHAB URL", e);
        }

        if (connection != null) {
            sendVoiceCommand(connection.getSyncHttpClient(), voiceCommand);
        } else {
            Util.showToast(this, getString(R.string.error_couldnt_determine_openhab_url));
        }
    }

    private String extractVoiceCommand(Intent data) {
        String voiceCommand = "";
        List<String> textMatchList = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (!textMatchList.isEmpty()) {
            voiceCommand = textMatchList.get(0);
        }
        Log.i(TAG, "Recognized text: " + voiceCommand);
        Util.showToast(this, getString(R.string.info_voice_recognized_text, voiceCommand));
        return voiceCommand;
    }

    private void sendVoiceCommand(final SyncHttpClient client, final String command) {
        final HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", Locale.getDefault().getLanguage());

        SyncHttpClient.HttpStatusResult result = client.post("rest/voice/interpreters",
                command, "text/plain", headers).asStatus();
        if (result.statusCode == 404) {
            Log.d(TAG, "Voice interpreter endpoint returned 404, falling back to item");
            result = client.post("rest/items/VoiceCommand", command, "text/plain").asStatus();
        }
        if (result.isSuccessful()) {
            Log.d(TAG, "Voice command was sent successfully");
        } else {
            Log.e(TAG, "Sending voice command failed", result.error);
        }
    }
}
