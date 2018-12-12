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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.MainActivity;

import static org.openhab.habdroid.background.BackgroundTaskRetryBroadcastReceiver.OH_EXTRA_NOTIFICATION_ID;

public class BackgroundUtils {
    private static final String TAG = BackgroundUtils.class.getSimpleName();
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
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID_BACKGROUND, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);

            name = context.getString(R.string.notification_channel_background_error);
            description =
                    context.getString(R.string.notification_channel_background_error_description);
            importance = NotificationManager.IMPORTANCE_DEFAULT;
            channel = new NotificationChannel(CHANNEL_ID_BACKGROUND_ERROR, name, importance);
            channel.setDescription(description);
            channel.setLightColor(ContextCompat.getColor(context, R.color.openhab_orange));
            channel.enableLights(true);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static Notification makeBackgroundNotification(Context context,
            @StringRes int msgId, @DrawableRes int iconId, boolean isError,
            NotificationCompat.Action action) {
        return makeBackgroundNotification(context, context.getString(msgId), iconId, isError,
                action);
    }

    /**
     * Makes notification for background tasks. Sets notification channel, importance and more
     * depending on the parameters.
     *
     * @param context
     * @param msg Message to show.
     * @param iconId Icon to show.
     * @param isError true if it's an error.
     * @param action Action to show as a button, e.g. "Retry"
     * @return
     */
    public static Notification makeBackgroundNotification(Context context, String msg,
            @DrawableRes int iconId, boolean isError, NotificationCompat.Action action) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(context, 0,
                notificationIntent, 0);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context,
                isError ? CHANNEL_ID_BACKGROUND_ERROR : CHANNEL_ID_BACKGROUND)
                .setLargeIcon(drawableToBitmap(context.getResources().getDrawable(iconId)))
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
                .setOngoing(!isError)
                .setGroup(isError ? CHANNEL_ID_BACKGROUND_ERROR : CHANNEL_ID_BACKGROUND)
                .setPriority(isError ? NotificationCompat.PRIORITY_DEFAULT
                        : NotificationCompat.PRIORITY_LOW);

        if (isError) {
            nb.setLights(ContextCompat.getColor(context, R.color.openhab_orange),
                    3000, 3000);
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
                context.getString(R.string.error_sending_alarm_clock_action_retry),
                retryPendingIntent);
    }

    /**
     * @author https://stackoverflow.com/a/10600736
     */
    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        Bitmap bitmap;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            // Single color bitmap will be created of 1x1 pixel
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
