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

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.MyHttpClient;

import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Headers;

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
            sendVoiceCommand(connection, voiceCommand);
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

    private void sendVoiceCommand(final Connection conn, final String command) {
        final String commandJson;
        try {
            JSONObject commandJsonObject = new JSONObject();
            commandJsonObject.put("body", command);
            commandJsonObject.put("Accept-Language", Locale.getDefault().getLanguage());
            commandJson = commandJsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Could not prepare voice command JSON", e);
            return;
        }

        conn.getSyncHttpClient().post("/voice/interpreters", commandJson,
                "application/json", new MyHttpClient.ResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] body, Throwable error) {
                if (statusCode == 404) {
                    Log.d(TAG, "Voice interpreter endpoint returned 404, falling back to item");
                    sendRawVoiceCommand(conn, command);
                } else {
                    Log.e(TAG, "Sending voice command to new endpoint failed: " + statusCode, error);
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] body) {
                Log.d(TAG, "Command was sent successfully");
            }
        });
    }

    private void sendRawVoiceCommand(final Connection conn, final String command) {
        conn.getSyncHttpClient().post("/rest/items/VoiceCommand", command,
                "text/plain;charset=UTF-8", new MyHttpClient.ResponseHandler() {
            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] body) {
                Log.d(TAG, "Command was sent successfully");
            }

            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] body, Throwable error) {
                Log.e(TAG, "Sending voice command to item failed: " + statusCode, error);
            }
        });
    }
}
