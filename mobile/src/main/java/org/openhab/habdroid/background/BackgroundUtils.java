/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available
 * at https://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.MainActivity;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

public class BackgroundUtils {
    public static final String WORKER_TAG_SEND_ALARM_CLOCK = "sendAlarmClock";
    public static final String NOTIFICATION_TAG_BACKGROUND = "background";
    public static final String NOTIFICATION_TAG_BACKGROUND_ERROR = "backgroundError";
    public static final String CHANNEL_ID_BACKGROUND = "background";
    public static final String CHANNEL_ID_BACKGROUND_ERROR = "backgroundError";
    public static final int NOTIFICATION_ID_SEND_ALARM_CLOCK = 1;

    /**
     * Creates notification channels for background tasks.
     * @param context
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);

            String name = context.getString(R.string.notification_channel_background);
            String description =
                    context.getString(R.string.notification_channel_background_description);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_BACKGROUND, name,
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);

            name = context.getString(R.string.notification_channel_background_error);
            description =
                    context.getString(R.string.notification_channel_background_error_description);
            channel = new NotificationChannel(CHANNEL_ID_BACKGROUND_ERROR, name,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(description);
            channel.setLightColor(ContextCompat.getColor(context, R.color.openhab_orange));
            channel.enableLights(true);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Makes notification for background tasks. Sets notification channel, importance and more
     * depending on the parameters.
     *
     * @param context
     * @param msg Message to show.
     * @param isOngoing Whether the notification is ongoing.
     * @param hasSound Whether the notification should make a sound, vibrate and enable lights.
     *                 Sound and vibration can be disabled by the user in the app settings.
     * @param isError Setting isError to true creates a notification with a higher priority and
     *                posts it on the error channel.
     * @param action Action to show as a button, e.g. "Retry".
     * @return
     */
    public static Notification makeBackgroundNotification(Context context, String msg,
            boolean isOngoing, boolean hasSound, boolean isError,
            NotificationCompat.Action action) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(context, 0,
                notificationIntent, 0);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context,
                isError ? CHANNEL_ID_BACKGROUND_ERROR : CHANNEL_ID_BACKGROUND)
                .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
                .setContentTitle(context.getString(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setContentText(msg)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setColor(ContextCompat.getColor(context, R.color.openhab_orange))
                .setCategory(isError ? NotificationCompat.CATEGORY_ERROR
                        : NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(!isOngoing)
                .setPriority(isError ? NotificationCompat.PRIORITY_DEFAULT
                        : NotificationCompat.PRIORITY_MIN);

        if (hasSound) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            nb.setLights(ContextCompat.getColor(context, R.color.openhab_orange), 3000, 3000)
                    .setSound(Uri.parse(prefs.getString(Constants.PREFERENCE_TONE, "")))
                    .setVibrate(Util.getNotificationVibrationPattern(context));
        }

        if (action != null) {
            nb.addAction(action);
        }

        return nb.build();
    }
}
