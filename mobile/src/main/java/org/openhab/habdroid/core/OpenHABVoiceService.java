/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.MyAsyncHttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Headers;

/**
 * This service handles voice commands and sends them to OpenHAB.
 * It will use the openHAB base URL if passed in the intent's extra.
 */
public class OpenHABVoiceService extends Service implements ConnectionFactory.UpdateListener {
    private static final String TAG = OpenHABVoiceService.class.getSimpleName();

    private final List<Pair<String, Integer>> mPendingCommands = new ArrayList<>();

    public OpenHABVoiceService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        final String voiceCommand = extractVoiceCommand(intent);
        boolean hasSentCommand = false;
        if (!voiceCommand.isEmpty()) {
            try {
                Connection conn = ConnectionFactory.getUsableConnection();
                if (conn != null) {
                    sendVoiceCommand(conn, voiceCommand, startId);
                } else {
                    mPendingCommands.add(Pair.create(voiceCommand, startId));
                    ConnectionFactory.addListener(this);
                }
                hasSentCommand = true;
            } catch (ConnectionException e) {
                Log.w(TAG, "Couldn't determine OpenHAB URL", e);
                Toast.makeText(this,
                        R.string.error_couldnt_determine_openhab_url, Toast.LENGTH_SHORT)
                        .show();
            }
        }
        if (!hasSentCommand) {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ConnectionFactory.removeListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConnectionChanged() {
        try {
            Connection conn = ConnectionFactory.getUsableConnection();
            for (Pair<String, Integer> entry : mPendingCommands) {
                sendVoiceCommand(conn, entry.first, entry.second);
            }
        } catch (ConnectionException e) {
            Log.w(TAG, "Couldn't determine OpenHAB URL", e);
            Toast.makeText(OpenHABVoiceService.this,
                    R.string.error_couldnt_determine_openhab_url, Toast.LENGTH_SHORT)
                    .show();
        } finally {
            ConnectionFactory.removeListener(this);
            mPendingCommands.clear();
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


    private void sendVoiceCommand(final Connection conn, final String command, final int startId) {
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

        conn.getAsyncHttpClient().post("/voice/interpreters", commandJson,
                "application/json", new MyAsyncHttpClient.ResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] body, Throwable error) {
                if (statusCode == 404) {
                    Log.d(TAG, "Voice interpreter endpoint returned 404, falling back to item");
                    sendRawVoiceCommand(conn, command, startId);
                } else {
                    Log.e(TAG, "Sending voice command to new endpoint failed: " + statusCode, error);
                    stopSelf(startId);
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] body) {
                Log.d(TAG, "Command was sent successfully");
                stopSelf(startId);
            }
        });
    }

    private void sendRawVoiceCommand(final Connection conn, final String command, final int startId) {
        conn.getAsyncHttpClient().post("/rest/items/VoiceCommand", command,
                "text/plain;charset=UTF-8", new MyAsyncHttpClient.ResponseHandler() {
            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] body) {
                Log.d(TAG, "Command was sent successfully");
                stopSelf(startId);
            }

            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] body, Throwable error) {
                Log.e(TAG, "Sending voice command to item failed: " + statusCode, error);
                stopSelf(startId);
            }
        });
    }
}
