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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;

public class GcmIntentService extends IntentService {

    private static final String TAG = GcmIntentService.class.getSimpleName();

    public static final String EXTRA_MSG = "message";
    public static final String EXTRA_NOTIFICATION_ID = "notificationId";
    public static final String ACTION_NOTIFICATION_SELECTED = "org.openhab.notification.selected";
    public static final String ACTION_NOTIFICATION_DELETED = "org.openhab.notification.deleted";

    private NotificationManager mNotificationManager;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null)
            return;
        int notificationId;
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager)
                    this.getSystemService(Context.NOTIFICATION_SERVICE);
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String messageType = gcm.getMessageType(intent);
        Log.d(TAG, "Message type = " + messageType);
        if (!extras.isEmpty()) {
            if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                // If this is notification, create new one
                if (!intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
                    notificationId = 1;
                } else {
                    notificationId = Integer.parseInt(intent.getExtras().getString(EXTRA_NOTIFICATION_ID));
                }
                if ("notification".equals(intent.getExtras().getString("type"))) {
                    sendNotification(intent.getExtras().getString(EXTRA_MSG), notificationId);
                // If this is hideNotification, cancel existing notification with it's id
                } else if ("hideNotification".equals(intent.getExtras().getString("type"))) {
                    mNotificationManager.cancel(Integer.parseInt(intent.getExtras().getString(EXTRA_NOTIFICATION_ID)));
                }
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void sendNotification(String msg, int notificationId) {
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager)
                    this.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(this, OpenHABMainActivity.class)
            .setAction(ACTION_NOTIFICATION_SELECTED)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(EXTRA_MSG, msg);

        PendingIntent pendingNotificationIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent deleteIntent = new Intent(getApplicationContext(), NotificationDeletedBroadcastReceiver.class)
            .setAction(ACTION_NOTIFICATION_DELETED)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId);

        PendingIntent pendingDeleteIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Uri alarmSound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.PREFERENCE_TONE, ""));
        if (alarmSound.toString().equals("")) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_openhab_appicon_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setColor(ContextCompat.getColor(this, R.color.openhab_orange))
                        .setAutoCancel(true)
                        .setSound(alarmSound)
                        .setContentText(msg)
                        .setContentIntent(pendingNotificationIntent)
                        .setDeleteIntent(pendingDeleteIntent);

        mNotificationManager.notify(notificationId, mBuilder.build());
    }
}
