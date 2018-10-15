/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.gcm.GcmListenerService;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.ui.MainActivity;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.SyncHttpClient;

import java.util.Locale;

public class GcmMessageListenerService extends GcmListenerService {
    static final String EXTRA_NOTIFICATION_ID = "notificationId";

    private static final String CHANNEL_ID_DEFAULT = "default";
    private static final String CHANNEL_ID_FORMAT_SEVERITY = "severity-%s";
    private static final int SUMMARY_NOTIFICATION_ID = 0;

    @Override
    public void onMessageReceived(String from, Bundle data) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String messageType = data.getString("type", "");
        String notificationIdString = data.getString(EXTRA_NOTIFICATION_ID);
        int notificationId = notificationIdString != null
                ? Integer.parseInt(notificationIdString) : 1;

        switch (messageType) {
            case "notification": {
                String message = data.getString("message");
                String severity = data.getString("severity");
                String icon = data.getString("icon");
                String persistedId = data.getString("persistedId");
                // Older versions of openhab-cloud didn't send the notification generation
                // timestamp, so use the (undocumented) google.sent_time as a time reference
                // in that case. If that also isn't present, don't show time at all.
                long timestamp = data.getLong("timestamp", data.getLong("google.sent_time", 0));

                final String channelId = TextUtils.isEmpty(severity)
                        ? CHANNEL_ID_DEFAULT
                        : String.format(Locale.US, CHANNEL_ID_FORMAT_SEVERITY, severity);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    final CharSequence name = TextUtils.isEmpty(severity)
                            ? getString(R.string.notification_channel_default)
                            : getString(R.string.notification_channel_severity_value, severity);
                    NotificationChannel channel = new NotificationChannel(
                            channelId, name, NotificationManager.IMPORTANCE_DEFAULT);
                    channel.setShowBadge(true);
                    nm.createNotificationChannel(channel);
                }

                Notification n = makeNotification(message, channelId,
                        icon, timestamp, persistedId, notificationId);
                nm.notify(notificationId, n);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int count = getGcmNotificationCount(nm.getActiveNotifications());
                    nm.notify(SUMMARY_NOTIFICATION_ID, makeSummaryNotification(count, timestamp));
                }
                break;
            }
            case "hideNotification":
                nm.cancel(notificationId);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StatusBarNotification[] active = nm.getActiveNotifications();
                    if (notificationId != SUMMARY_NOTIFICATION_ID
                            && getGcmNotificationCount(active) == 0) {
                        // Cancel summary when removing the last sub-notification
                        nm.cancel(SUMMARY_NOTIFICATION_ID);
                    } else if (notificationId == SUMMARY_NOTIFICATION_ID) {
                        // Cancel all sub-notifications when removing the summary
                        for (StatusBarNotification n : active) {
                            nm.cancel(n.getId());
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    private PendingIntent makeNotificationClickIntent(String persistedId, int notificationId) {
        Intent contentIntent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_NOTIFICATION_SELECTED)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(MainActivity.EXTRA_PERSISTED_NOTIFICATION_ID, persistedId);
        return PendingIntent.getActivity(this, notificationId,
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification makeNotification(String msg, String channelId, String icon,
            long timestamp, String persistedId, int notificationId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String toneSetting = prefs.getString(Constants.PREFERENCE_TONE, "");
        Bitmap iconBitmap = null;

        if (icon != null) {
            Connection connection = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
            if (connection != null) {
                final String url = String.format(Locale.US, "images/%s.png", icon);
                SyncHttpClient.HttpResult result = connection.getSyncHttpClient().get(url, 1000);
                if (result.response != null) {
                    iconBitmap = BitmapFactory.decodeStream(result.response.byteStream());
                }
            }
        }

        PendingIntent contentIntent = makeNotificationClickIntent(persistedId, notificationId);

        CharSequence publicText = getResources().getQuantityString(
                R.plurals.summary_notification_text, 1, 1);
        Notification publicVersion = makeNotificationBuilder(channelId, timestamp)
                .setContentText(publicText)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent)
                .build();

        return makeNotificationBuilder(channelId, timestamp)
                .setLargeIcon(iconBitmap)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setSound(Uri.parse(toneSetting))
                .setContentText(msg)
                .setContentIntent(contentIntent)
                .setDeleteIntent(GcmRegistrationService.createHideNotificationIntent(this,
                        notificationId))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .build();
    }

    @TargetApi(23)
    private Notification makeSummaryNotification(int subNotificationCount, long timestamp) {
        CharSequence text = getResources().getQuantityString(R.plurals.summary_notification_text,
                subNotificationCount, subNotificationCount);
        PendingIntent clickIntent =
                makeNotificationClickIntent(null, SUMMARY_NOTIFICATION_ID);
        Notification publicVersion = makeNotificationBuilder(CHANNEL_ID_DEFAULT, timestamp)
                .setGroupSummary(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentText(text)
                .setContentIntent(clickIntent)
                .build();
        return makeNotificationBuilder(CHANNEL_ID_DEFAULT, timestamp)
                .setGroupSummary(true)
                .setContentText(text)
                .setPublicVersion(publicVersion)
                .setContentIntent(clickIntent)
                .setDeleteIntent(GcmRegistrationService.createHideNotificationIntent(this,
                        SUMMARY_NOTIFICATION_ID))
                .build();
    }

    private NotificationCompat.Builder makeNotificationBuilder(String channelId, long timestamp) {
        long[] vibrationPattern;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String vibration = prefs.getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION, "");
        if (getString(R.string.settings_notification_vibration_value_short).equals(vibration)) {
            vibrationPattern = new long[] {0, 500, 500};
        } else if (getString(R.string.settings_notification_vibration_value_long)
                .equals(vibration)) {
            vibrationPattern = new long[] {0, 1000, 1000};
        } else if (getString(R.string.settings_notification_vibration_value_twice)
                .equals(vibration)) {
            vibrationPattern = new long[] {0, 1000, 1000, 1000, 1000};
        } else {
            vibrationPattern = new long[] {0};
        }

        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setWhen(timestamp)
                .setShowWhen(timestamp != 0)
                .setColor(ContextCompat.getColor(this, R.color.openhab_orange))
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setLights(ContextCompat.getColor(this, R.color.openhab_orange), 3000, 3000)
                .setVibrate(vibrationPattern)
                .setGroup("gcm");
    }

    @TargetApi(23)
    private int getGcmNotificationCount(StatusBarNotification[] active) {
        int count = 0;
        for (StatusBarNotification n : active) {
            String groupKey = n.getGroupKey();
            if (n.getId() != 0 && groupKey != null && groupKey.endsWith("gcm")) {
                count++;
            }
        }
        return count;
    }
}
