/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
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
import java.util.Locale;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;

public class GcmIntentService extends IntentService implements OnInitListener {

    private static final String TAG = "GcmIntentService";
    private NotificationManager mNotificationManager;
    // Notification delete receiver
    private final NotificationDeletedBroadcastReceiver mNotificationDeletedBroadcastReceiver =
            new NotificationDeletedBroadcastReceiver();

    public static TextToSpeech tts;

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
        Uri alarmSound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.PREFERENCE_TONE, ""));
        if (alarmSound.toString() == "" || alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        // Read aloud what sendNotification sent
        if (msg.startsWith("speak ")) {
            // strip speak-prefix
            msg = msg.substring(6, msg.length()-6);

            if(tts == null) {
                tts = new TextToSpeech(this.getApplicationContext(), this);
            }
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
        }

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

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH);
            }
        }
    }
}
