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
import android.util.Log;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Result;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.MainActivity;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import static org.openhab.habdroid.background.BackgroundTaskRetryBroadcastReceiver.OH_EXTRA_NOTIFICATION_ID;

public class BackgroundUtils {
    private static final String TAG = BackgroundUtils.class.getSimpleName();
    private static final int MAX_RETRY = 3;
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
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID_BACKGROUND, name,
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
     *               Also see {@link #makeRetryAction(Context, int)}
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
            nb.setLights(ContextCompat.getColor(context, R.color.openhab_orange),
                    3000, 3000)
                    .setSound(Uri.parse(prefs.getString(Constants.PREFERENCE_TONE, "")))
                    .setVibrate(Util.getNotificationVibrationPattern(context));
        }

        if (action != null) {
            nb.addAction(action);
        }

        return nb.build();
    }

    /**
     * Makes a "Retry" action.
     * @param context
     * @param notificationId Id of the current notification
     * @return
     */
    public static NotificationCompat.Action makeRetryAction(Context context, int notificationId) {
        Intent retryIntent = new Intent(context, BackgroundTaskRetryBroadcastReceiver.class);
        retryIntent.putExtra(OH_EXTRA_NOTIFICATION_ID, notificationId);
        PendingIntent retryPendingIntent =
                PendingIntent.getBroadcast(context, 0, retryIntent, 0);
        return new NotificationCompat.Action(
                R.drawable.ic_refresh_grey_24dp,
                context.getString(R.string.retry),
                retryPendingIntent);
    }

    /**
     * Retry worker or fail if max retry is exceeded. Also shows a notification and returns correct
     * {@link Result}.
     * @param errorMessage Message shown in a notification if worker failed.
     * @param retryMessage Message shown in a notification if worker is going to retry.
     *                     If it's null, no notification will be shown.
     * @param runAttemptCount Current attempt count.
     * @param action Action added to the notification.
     * @param notificationId Notification ID
     * @param context
     * @param nm NotificationManager instance
     * @return Worker result.
     */
    @CheckResult
    public static Result retryOrFail(@NonNull String errorMessage, @Nullable String retryMessage,
            int runAttemptCount, @Nullable NotificationCompat.Action action, int notificationId,
            @NonNull Context context, @NonNull NotificationManager nm) {
        if (runAttemptCount > MAX_RETRY) {
            Log.e(TAG, "Don't retry again. Error: " + errorMessage);
            Notification notification = makeBackgroundNotification(context,
                    errorMessage,
                    false,
                    false,
                    true,
                    action);
            nm.cancel(NOTIFICATION_TAG_BACKGROUND, notificationId);
            nm.notify(NOTIFICATION_TAG_BACKGROUND_ERROR, notificationId,
                    notification);
            return Result.failure();
        }
        Log.d(TAG, "Retry: " +  retryMessage);
        if (retryMessage != null) {
            Notification notification = makeBackgroundNotification(context,
                    retryMessage,
                    true,
                    false,
                    false,
                    action);
            nm.notify(NOTIFICATION_TAG_BACKGROUND, notificationId,
                    notification);
        }
        return Result.retry();
    }
}
