/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

public class GcmBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = GcmBroadcastReceiver.class.getSimpleName();
    private Context mContext;
    private NotificationManager mNotificationManager;
    private static int mNotificationId = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Explicitly specify that GcmIntentService will handle the intent.
        ComponentName comp = new ComponentName(context.getPackageName(),
                GcmIntentService.class.getName());
        // Start the service, keeping the device awake while it is launching.
        startWakefulService(context, (intent.setComponent(comp)));
        setResultCode(Activity.RESULT_OK);
    }

/*    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        String messageType = gcm.getMessageType(intent);
        Log.d(TAG, "Message type = " + messageType);
        Log.d(TAG, intent.getExtras().keySet().toString());
        if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
            Log.d(TAG, "Send error: " + intent.getExtras().toString());
        } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
            Log.d(TAG, "Deleted messages on server: " +
                    intent.getExtras().toString());
        } else {
            Log.d(TAG, "Type: " + intent.getExtras().getString("type"));
            Log.d(TAG, "From: " + intent.getExtras().getString("from"));
            Log.d(TAG, "Collapse key: " + intent.getExtras().getString("collapse_key"));
            Log.d(TAG, "Message: " + intent.getExtras().getString("message"));
            if (intent.getExtras() != null)
                if (intent.getExtras().getString("type").equals("notification")) {
                    sendNotification(intent.getExtras().getString("message"));
                }
        }
        setResultCode(Activity.RESULT_OK);
    }

    private void sendNotification(String msg) {
        mNotificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0,
                new Intent(mContext, OpenHABMainActivity.class), 0);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mNotificationId++;
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(mContext)
                        .setSmallIcon(R.drawable.openhabicon)
                        .setContentTitle("openHAB")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setAutoCancel(true)
                        .setSound(alarmSound)
                        .setContentText(msg);
        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(mNotificationId, mBuilder.build());
    }
*/

}
