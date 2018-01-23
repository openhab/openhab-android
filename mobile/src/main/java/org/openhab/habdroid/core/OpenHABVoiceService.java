/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.AlertDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.message.MessageHandler;
import org.openhab.habdroid.util.MyHttpClient;

import java.util.List;

import okhttp3.Call;
import okhttp3.Headers;

/**
 * This service handles voice commands and sends them to OpenHAB.
 * It will use the openHAB base URL if passed in the intent's extra.
 */
public class OpenHABVoiceService extends Service {
    private static final String TAG = OpenHABVoiceService.class.getSimpleName();

    public OpenHABVoiceService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String voiceCommand = extractVoiceCommand(intent);
        if (!voiceCommand.isEmpty()) {
            try {
                Connection conn = ConnectionFactory.getConnection(Connection.TYPE_ANY);
                sendItemCommand("VoiceCommand", voiceCommand, conn, startId);
            } catch (ConnectionException e) {
                Log.w(TAG, "Couldn't determine OpenHAB URL", e);
                showToast(getString(R.string.error_couldnt_determine_openhab_url));
                stopSelf(startId);
            }
        } else {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * @param message     message to show
     * @param messageType must be MessageHandler.TYPE_DIALOG or MessageHandler.TYPE_TOAST
     * @param logLevel    not implemented
     */
    public void showMessageToUser(String message, int messageType, int logLevel) {
        if (message == null) {
            return;
        }
        switch (messageType) {
            case MessageHandler.TYPE_DIALOG:
                AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABVoiceService.this);
                builder.setMessage(message)
                        .setPositiveButton(getString(android.R.string.ok), new DialogInterface
                                .OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
                break;
            default:
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                break;
        }
    }

    private String extractVoiceCommand(Intent data) {
        String voiceCommand = "";
        List<String> textMatchList = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (!textMatchList.isEmpty()) {
            voiceCommand = textMatchList.get(0);
        }
        Log.i(TAG, "Recognized text: " + voiceCommand);
        showToast(getString(R.string.info_voice_recognized_text, voiceCommand));
        return voiceCommand;
    }


    private void sendItemCommand(final String itemName, final String command,
                                 final Connection conn, final int startId) {
        Log.d(TAG, "sendItemCommand(): itemName=" + itemName + ", command=" + command);
        try {
            performHttpPost(itemName, command, conn, startId);
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to encode command " + command, e);
            stopSelf(startId);
        }
    }

    private void performHttpPost(final String itemName, final String command,
                                 final Connection conn, final int startId) {
        conn.getAsyncHttpClient().post("/rest/items/" + itemName,
                command, "text/plain;charset=UTF-8", new MyHttpClient.ResponseHandler() {
                    @Override
                    public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                        Log.d(TAG, "Command was sent successfully");
                        stopSelf(startId);
                    }

                    @Override
                    public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                        Log.e(TAG, "Got command error " + statusCode, error);
                        stopSelf(startId);
                    }
                });
    }

    /**
     * Displays the given message as a toast
     *
     * @param message The message to be displayed.
     */
    private void showToast(final String message) {
        // Display toast on main looper because OpenHABVoiceService might be destroyed
        // before to toast has finished displaying
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                showMessageToUser(message, MessageHandler.TYPE_TOAST, MessageHandler.LOGLEVEL_ALWAYS);
            }
        });
    }
}
