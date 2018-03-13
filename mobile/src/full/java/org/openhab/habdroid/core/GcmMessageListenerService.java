/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.gcm.GcmListenerService;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;

public class GcmMessageListenerService extends GcmListenerService {
    static final String EXTRA_NOTIFICATION_ID = "notificationId";

    @Override
    public void onMessageReceived(String from, Bundle data) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String messageType = data.getString("type", "");
        String notificationIdString = data.getString(EXTRA_NOTIFICATION_ID);
        int notificationId = notificationIdString != null
                ? Integer.parseInt(notificationIdString) : 1;

        switch (messageType) {
            case "notification":
                // As the GCM message payload sent by OH cloud does not include the notification
                // timestamp, use the (undocumented) google.sent_time as a time reference for our
                // notifications. In case GCM stops sending that timestamp, use the current time
                // as fallback.
                long timestamp = data.getLong("google.sent_time", System.currentTimeMillis());
                Notification n =
                        makeNotification(data.getString("message"), timestamp, notificationId);
                nm.notify(notificationId, n);
                break;
            case "hideNotification":
                nm.cancel(notificationId);
                break;
        }
    }

    private Notification makeNotification(String msg, long timestamp, int notificationId) {
        Intent contentIntent = new Intent(this, OpenHABMainActivity.class)
                .setAction(OpenHABMainActivity.ACTION_NOTIFICATION_SELECTED)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(OpenHABMainActivity.EXTRA_MESSAGE, msg);
        PendingIntent contentPi = PendingIntent.getActivity(this, notificationId,
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent deleteIntent = new Intent(this, GcmRegistrationService.class)
                .setAction(GcmRegistrationService.ACTION_HIDE_NOTIFICATION)
                .putExtra(GcmRegistrationService.EXTRA_NOTIFICATION_ID, notificationId);

        PendingIntent deletePi = PendingIntent.getBroadcast(this, notificationId,
                deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String toneSetting = prefs.getString(Constants.PREFERENCE_TONE, "");

        return new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_openhab_appicon_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(msg))
                .setColor(ContextCompat.getColor(this, R.color.openhab_orange))
                .setAutoCancel(true)
                .setWhen(timestamp)
                .setSound(Uri.parse(toneSetting))
                .setContentText(msg)
                .setContentIntent(contentPi)
                .setDeleteIntent(deletePi)
                .build();
    }
}
