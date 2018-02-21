/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.IntentService;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.model.NotificationSettings;
import org.openhab.habdroid.util.MyHttpClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Headers;

public class GcmRegistrationService extends IntentService {
    private static final String TAG = GcmRegistrationService.class.getSimpleName();

    static final String ACTION_REGISTER = "org.openhab.habdroid.action.REGISTER_GCM";
    static final String ACTION_HIDE_NOTIFICATION = "org.openhab.habdroid.action.HIDE_NOTIFICATION";
    static final String EXTRA_NOTIFICATION_ID = "notificationId";

    public GcmRegistrationService() {
        super("GcmRegistrationService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Connection connection = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
        if (connection == null) {
            return;
        }
        NotificationSettings ns = NotificationSettings.forConnection(connection);
        String senderId = ns.senderId();

        if (ACTION_REGISTER.equals(intent.getAction())) {
            try {
                registerGcm(connection, senderId);
                CloudMessagingHelper.sLastSenderId = senderId;
            } catch (IOException e) {
                Log.e(TAG, "GCM registration failed", e);
                CloudMessagingHelper.sLastSenderId = null;
            }
        } else if (ACTION_HIDE_NOTIFICATION.equals(intent.getAction())) {
            int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
            if (notificationId >= 0) {
                GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
                Bundle sendBundle = new Bundle();
                sendBundle.putString("type", "hideNotification");
                sendBundle.putString("notificationId", String.valueOf(notificationId));
                try {
                    gcm.send(senderId + "@gcm.googleapis.com", "1", sendBundle);
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending notification hide message", e);
                }
            }
        }
    }

    private void registerGcm(Connection connection, String senderId) throws IOException {
        InstanceID instanceID = InstanceID.getInstance(this);
        String token = instanceID.getToken(senderId,
                GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
        String deviceModel = URLEncoder.encode(Build.MODEL, "UTF-8");
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        String regUrl = String.format(Locale.US,
                "/addAndroidRegistration?deviceId=%s&deviceModel=%s&regId=%s",
                deviceId, deviceModel, token);

        Log.d(TAG, "Register device at openHAB-cloud with URL: " + regUrl);
        connection.getSyncHttpClient().get(regUrl, new MyHttpClient.ResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                Log.e(TAG, "GCM reg id error: " + error.getMessage());
                if (responseBody != null)
                    Log.e(TAG, "Error response = " + responseBody);
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                Log.d(TAG, "GCM reg id success");
            }
        });
    }
}
