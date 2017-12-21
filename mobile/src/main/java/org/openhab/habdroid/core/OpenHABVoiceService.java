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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.ContinuingIntentService;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MyHttpClient;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import okhttp3.Call;
import okhttp3.Headers;

/**
 * This service handles voice commands and sends them to OpenHAB.
 * It will use the openHAB base URL if passed in the intent's extra,
 * or else use the {@link OpenHABTracker} to discover openHAB itself.
 */
public class OpenHABVoiceService extends ContinuingIntentService implements OpenHABTrackerReceiver {

    private static final String TAG = OpenHABVoiceService.class.getSimpleName();
    public static final String OPENHAB_BASE_URL_EXTRA = "openHABBaseUrl";

    private String mOpenHABBaseUrl;
    private MyAsyncHttpClient mAsyncHttpClient;

    private OpenHABTracker mOpenHABTracker;
    private Queue<Intent> mBufferedIntents;


    public OpenHABVoiceService() {
        super(TAG);
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        mBufferedIntents = new LinkedList<Intent>();
        initHttpClient();
    }

    private void initHttpClient() {
        SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        String username = mSettings.getString(Constants.PREFERENCE_REMOTE_USERNAME, null);
        String password = mSettings.getString(Constants.PREFERENCE_REMOTE_PASSWORD, null);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAsyncHttpClient = new MyAsyncHttpClient(this, prefs.getBoolean(Constants.PREFERENCE_SSLHOST,
                false), prefs.getBoolean(Constants.PREFERENCE_SSLCERT, false));
        mAsyncHttpClient.setBasicAuth(username, password);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent()");
        bufferIntent(intent);
        if (intent.hasExtra(OPENHAB_BASE_URL_EXTRA)) {
            Log.d(TAG, "openHABBaseUrl passed as Intent");
            onOpenHABTracked(intent.getStringExtra(OPENHAB_BASE_URL_EXTRA));
        } else if (mOpenHABTracker == null) {
            Log.d(TAG, "No openHABBaseUrl passed, starting OpenHABTracker");
            mOpenHABTracker = new OpenHABTracker(OpenHABVoiceService.this, getString(R.string.openhab_service_type));
            mOpenHABTracker.start();
        }
    }

    /**
     * Buffers the {@link Intent} to be processed later when openHABBaseUrl has been determined by {@link OpenHABTracker}.
     *
     * Usually, the discovery of the openHABBaseUrl is fast enough, so there will be only one intent in the buffer
     * when the buffer is processed and this service is stopped.
     * However, it is not guaranteed that this service is only called once before openHABBaseUrl can be discovered.
     * Therefore all intents are buffered and later this buffer will be processed.
     *
     * @param intent The {@link Intent} to be buffered.
     */
    private void bufferIntent(Intent intent) {
        mBufferedIntents.add(intent);
    }

    @Override
    public void onOpenHABTracked(String baseUrl) {
        Log.d(TAG, "onOpenHABTracked(): " + baseUrl);
        mOpenHABBaseUrl = baseUrl;
        while (!mBufferedIntents.isEmpty()) {
            processVoiceIntent(mBufferedIntents.poll());
        }
        Log.d(TAG, "Stopping service for start ID " + getLastStartId());
        stopSelf(getLastStartId());
    }

    /**
     * @param message message to show
     * @param messageType must be Constants.MESSAGES.DIALOG or Constants.MESSAGES.TOAST
     * @param logLevel not implemented
     */
    public void showMessageToUser(String message, int messageType, int logLevel) {
        if(message == null) {
            return;
        }
        switch (messageType) {
            case Constants.MESSAGES.DIALOG:
                AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABVoiceService.this);
                builder.setMessage(message)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
                break;
            default:
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                break;
        }
    }

    @Override
    public void onError(String error) {
        showMessageToUser(error, Constants.MESSAGES.DIALOG, Constants.MESSAGES.LOGLEVEL.ALWAYS);
        Log.d(TAG, "onError(): " + error);
        stopSelf();
    }


    private void processVoiceIntent(Intent data) {
        Log.d(TAG, "processVoiceIntent()");

        String voiceCommand = extractVoiceCommand(data);
        if (!voiceCommand.isEmpty()) {
            if (mOpenHABBaseUrl != null) {
                sendItemCommand("VoiceCommand", voiceCommand);
            } else {
                Log.w(TAG, "Couldn't determine OpenHAB URL");
                showToast(getString(R.string.error_couldnt_determine_openhab_url));
            }
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


    private void sendItemCommand(final String itemName, final String command) {
        Log.d(TAG, "sendItemCommand(): itemName=" + itemName + ", command=" + command);
        try {
            performHttpPost(itemName, command);
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to encode command " + command, e);
        }
    }

    private void performHttpPost(final String itemName, final String command) {
        /* Call MyAsyncHttpClient on the main UI thread in order to retrieve the callbacks correctly.
         * If calling MyAsyncHttpClient directly, the following would happen:
         * (1) MyAsyncHttpClient performs the HTTP post asynchronously
         * (2) OpenHABVoiceService stops because all intents have been handled
         * (3) MyAsyncHttpClient tries to call onSuccess() or onFailure(), which is not possible
         *     anymore because OpenHABVoiceService is already stopped/destroyed.
         */
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mAsyncHttpClient.post(mOpenHABBaseUrl + "rest/items/" + itemName,
                        command, "text/plain;charset=UTF-8", new MyHttpClient.ResponseHandler() {
                            @Override
                            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                                Log.d(TAG, "Command was sent successfully");
                            }

                            @Override
                            public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                                Log.e(TAG, "Got command error " + statusCode, error);
                            }
                        });
            }
        });
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (mOpenHABTracker != null) {
            mOpenHABTracker.stop();
        }
        super.onDestroy();
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
                showMessageToUser(message, Constants.MESSAGES.TOAST, Constants.MESSAGES.LOGLEVEL.ALWAYS);
            }
        });
    }

    @Override
    public void onBonjourDiscoveryStarted() {
        Log.d(TAG, "onBonjourDiscoveryStarted()");
    }

    @Override
    public void onBonjourDiscoveryFinished() {
        Log.d(TAG, "onBonjourDiscoveryFinished()");
    }
}
