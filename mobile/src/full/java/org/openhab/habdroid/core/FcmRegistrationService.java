/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.CloudConnection;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.util.SyncHttpClient;
import org.openhab.habdroid.util.Util;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Locale;

public class FcmRegistrationService extends JobIntentService {
    private static final String TAG = FcmRegistrationService.class.getSimpleName();

    private static final int JOB_ID = 1000;

    private static final String ACTION_REGISTER = "org.openhab.habdroid.action.REGISTER_GCM";
    private static final String ACTION_HIDE_NOTIFICATION =
            "org.openhab.habdroid.action.HIDE_NOTIFICATION";
    private static final String EXTRA_NOTIFICATION_ID = "notificationId";

    static void scheduleRegistration(Context context) {
        Intent intent = new Intent(context, FcmRegistrationService.class)
                .setAction(FcmRegistrationService.ACTION_REGISTER);
        JobIntentService.enqueueWork(context, FcmRegistrationService.class, JOB_ID, intent);
    }

    static void scheduleHideNotification(Context context, int notificationId) {
        JobIntentService.enqueueWork(context, FcmRegistrationService.class, JOB_ID,
                makeHideNotificationIntent(context, notificationId));
    }

    static PendingIntent createHideNotificationIntent(Context context, int notificationId) {
        return ProxyReceiver.wrap(context, makeHideNotificationIntent(context, notificationId),
                notificationId);
    }

    private static Intent makeHideNotificationIntent(Context context, int notificationId) {
        return new Intent(context, FcmRegistrationService.class)
                .setAction(FcmRegistrationService.ACTION_HIDE_NOTIFICATION)
                .putExtra(FcmRegistrationService.EXTRA_NOTIFICATION_ID, notificationId);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
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
                    registerFcm(connection);
                } catch (IOException e) {
                    CloudMessagingHelper.sRegistrationFailureReason = e;
                    Log.e(TAG, "GCM registration failed", e);
                }
                CloudMessagingHelper.sRegistrationDone = true;
                break;
            case ACTION_HIDE_NOTIFICATION:
                int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
                if (id >= 0) {
                    sendHideNotificationRequest(id, connection.getMessagingSenderId());
                }
                break;
            default:
                break;
        }
    }

    private void registerFcm(CloudConnection connection) throws IOException {
        String token = FirebaseInstanceId.getInstance().getToken(connection.getMessagingSenderId(),
                FirebaseMessaging.INSTANCE_ID_SCOPE);
        String deviceName = getDeviceName()
                + (Util.isFlavorBeta() ? " (" + getString(R.string.beta) + ")" : "");
        String deviceId = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID) + (Util.isFlavorBeta() ? "-beta" : "");

        String regUrl = String.format(Locale.US,
                "addAndroidRegistration?deviceId=%s&deviceModel=%s&regId=%s",
                deviceId, URLEncoder.encode(deviceName, "UTF-8"), token);

        Log.d(TAG, "Register device at openHAB-cloud with URL: " + regUrl);
        SyncHttpClient.HttpStatusResult result =
                connection.getSyncHttpClient().get(regUrl).asStatus();
        if (result.isSuccessful()) {
            Log.d(TAG, "GCM reg id success");
        } else {
            Log.e(TAG, "GCM reg id error: " + result.error);
        }
        CloudMessagingHelper.sRegistrationFailureReason = result.error;
    }

    /**
     * @author https://stackoverflow.com/a/12707479
     */
    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    /**
     * @author https://stackoverflow.com/a/12707479
     */
    private String capitalize(String s) {
        if (TextUtils.isEmpty(s)) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    private void sendHideNotificationRequest(int notificationId, String senderId) {
        FirebaseMessaging fcm = FirebaseMessaging.getInstance();
        RemoteMessage message = new RemoteMessage.Builder(senderId + "@gcm.googleapis.com")
                .addData("type", "hideNotification")
                .addData("notificationId", String.valueOf(notificationId))
                .build();
        fcm.send(message);
    }

    public static class ProxyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent actual = intent.getParcelableExtra("intent");
            JobIntentService.enqueueWork(context, FcmRegistrationService.class, JOB_ID, actual);
        }

        private static PendingIntent wrap(Context context, Intent intent, int id) {
            Intent wrapped = new Intent(context, ProxyReceiver.class)
                    .putExtra("intent", intent);
            return PendingIntent.getBroadcast(context, id,
                    wrapped, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }
}
