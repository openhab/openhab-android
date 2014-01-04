/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.core;

import android.app.Activity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;

public class GcmIntentService extends IntentService {

    private static final String TAG = "GcmIntentService";
    private NotificationManager mNotificationManager;
    // Notification delete receiver
    private final NotificationDeletedBroadcastReceiver mNotificationDeletedBroadcastReceiver =
            new NotificationDeletedBroadcastReceiver();;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
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
                if (!intent.hasExtra("notificationId")) {
                    notificationId = 1;
                } else {
                    notificationId = Integer.parseInt(intent.getExtras().getString("notificationId"));
                }
                if (intent.getExtras().getString("type").equals("notification")) {
                    sendNotification(intent.getExtras().getString("message"), notificationId);
                // If this is hideNotification, cancel existing notification with it's id
                } else if (intent.getExtras().getString("type").equals("hideNotification")) {
                    mNotificationManager.cancel(Integer.parseInt(intent.getExtras().getString("notificationId")));
                }
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void sendNotification(String msg, int notificationId) {
//        registerReceiver(mNotificationDeletedBroadcastReceiver,
//                new IntentFilter("org.openhab.notification.deleted"));
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager)
                    this.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(this, OpenHABMainActivity.class);
        notificationIntent.setAction("org.openhab.notification.selected");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra("notificationId", notificationId);
        PendingIntent pendingNotificationIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent deleteIntent = new Intent(getApplicationContext(), NotificationDeletedBroadcastReceiver.class);
        deleteIntent.setAction("org.openhab.notification.deleted");
        deleteIntent.putExtra("notificationId", notificationId);
        PendingIntent pendingDeleteIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.openhabicon)
                        .setContentTitle("openHAB")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setAutoCancel(true)
                        .setSound(alarmSound)
                        .setContentText(msg);
        mBuilder.setContentIntent(pendingNotificationIntent);
        mBuilder.setDeleteIntent(pendingDeleteIntent);
        mNotificationManager.notify(notificationId, mBuilder.build());
    }
}
