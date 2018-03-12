/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;

public class GcmListenerService extends com.google.android.gms.gcm.GcmListenerService {
    static final String EXTRA_NOTIFICATION_ID = "notificationId";

    @Override
    public void onMessageReceived(String from, Bundle data) {
        String notificationIdString = data.getString(EXTRA_NOTIFICATION_ID);
        int notificationId = notificationIdString != null
                ? Integer.parseInt(notificationIdString) : 1;

        if ("notification".equals(data.getString("type"))) {
            //for now we use google.sent_time as a time reference for our notifications as the gcm
            //message does not contain the actual event time from the openhab instance at the moment,
            //in case gcm time is also missing, we fall back to the reception time which may be delayed (old behaviour)
            long timestamp = data.getLong("google.sent_time", System.currentTimeMillis());
            sendNotification(data.getString("message"), timestamp, notificationId);
            // If this is hideNotification, cancel existing notification with it's id
        } else if ("hideNotification".equals(data.getString("type"))) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(notificationId);
        }
    }

    private void sendNotification(String msg, long timestamp, int notificationId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_openhab_appicon_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(msg))
                .setColor(ContextCompat.getColor(this, R.color.openhab_orange))
                .setAutoCancel(true)
                .setWhen(timestamp)
                .setContentText(msg)
                .setContentIntent(contentPi)
                .setDeleteIntent(deletePi);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String toneSetting = prefs.getString(Constants.PREFERENCE_TONE, "");
        if (toneSetting.isEmpty()) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        } else {
            builder.setSound(Uri.parse(toneSetting));
        }

        nm.notify(notificationId, builder.build());
    }
}
