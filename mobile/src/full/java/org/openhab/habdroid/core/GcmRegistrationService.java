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

import org.openhab.habdroid.core.connection.CloudConnection;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.util.MySyncHttpClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Locale;

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
        ConnectionFactory.waitForInitialization();
        CloudConnection connection =
                (CloudConnection) ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
        if (connection == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            return;
        }

        switch (action) {
            case ACTION_REGISTER:
                try {
                    registerGcm(connection);
                } catch (IOException e) {
                    CloudMessagingHelper.sRegistrationFailureReason = e;
                    Log.e(TAG, "GCM registration failed", e);
                }
                CloudMessagingHelper.sRegistrationDone = true;
                break;
            case ACTION_HIDE_NOTIFICATION:
                int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
                if (notificationId >= 0) {
                    try {
                        sendHideNotificationRequest(notificationId, connection.getMessagingSenderId());
                    } catch (IOException e) {
                        Log.e(TAG, "Failed sending notification hide message", e);
                    }
                }
                break;
        }
    }

    private void registerGcm(CloudConnection connection) throws IOException {
        InstanceID instanceID = InstanceID.getInstance(this);
        String token = instanceID.getToken(connection.getMessagingSenderId(),
                GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
        String deviceModel = URLEncoder.encode(Build.MODEL, "UTF-8");
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        String regUrl = String.format(Locale.US,
                "/addAndroidRegistration?deviceId=%s&deviceModel=%s&regId=%s",
                deviceId, deviceModel, token);

        Log.d(TAG, "Register device at openHAB-cloud with URL: " + regUrl);
        MySyncHttpClient.HttpResult result = connection.getSyncHttpClient().get(regUrl);
        if (result.error == null) {
            Log.d(TAG, "GCM reg id success");
        } else {
            Log.e(TAG, "GCM reg id error: " + result.error.getMessage());
        }
        CloudMessagingHelper.sRegistrationFailureReason = result.error;
    }

    private void sendHideNotificationRequest(int notificationId, String senderId) throws IOException {
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        Bundle sendBundle = new Bundle();
        sendBundle.putString("type", "hideNotification");
        sendBundle.putString("notificationId", String.valueOf(notificationId));
        gcm.send(senderId + "@gcm.googleapis.com", "1", sendBundle);
    }
}
