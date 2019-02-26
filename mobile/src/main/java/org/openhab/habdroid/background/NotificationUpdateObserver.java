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

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.MainActivity;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.WorkInfo;

class NotificationUpdateObserver implements Observer<List<WorkInfo>> {
    private static final int NOTIFICATION_ID_BACKGROUND_WORK = 1000;
    private static final String CHANNEL_ID_BACKGROUND = "background";
    private static final String CHANNEL_ID_BACKGROUND_ERROR = "backgroundError";

    private final Context mContext;

    private static final List<String> KNOWN_TAGS = Arrays.asList(
        Constants.PREFERENCE_ALARM_CLOCK
    );

    NotificationUpdateObserver(Context context) {
        mContext = context.getApplicationContext();
    }
    @Override
    public void onChanged(List<WorkInfo> workInfos) {
        // Find latest state for each tag
        HashMap<String, WorkInfo> latestInfoByTag = new HashMap<>();
        for (WorkInfo info : workInfos) {
            for (String tag : info.getTags()) {
                if (KNOWN_TAGS.contains(tag)) {
                    WorkInfo.State state = info.getState();
                    if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING) {
                        // Always treat a running job as the 'current' one
                        latestInfoByTag.put(tag, info);
                    } else if (state == WorkInfo.State.SUCCEEDED
                            || state == WorkInfo.State.FAILED) {
                        // Succeeded and failed tasks have their timestamp in output data, so
                        // we can use that one to determine the newest one
                        WorkInfo existing = latestInfoByTag.get(tag);
                        WorkInfo.State existingState =
                                existing == null ? null : existing.getState();
                        if (existingState == null) {
                            latestInfoByTag.put(tag, info);
                        } else if (existingState == WorkInfo.State.SUCCEEDED
                                || existingState == WorkInfo.State.FAILED) {
                            long ts = info.getOutputData().getLong(
                                    ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0);
                            long existingTs = existing.getOutputData().getLong(
                                    ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0);
                            if (ts > existingTs) {
                                latestInfoByTag.put(tag, info);
                            }
                        }
                    }
                    // Stop evaluating tags and advance to next info
                    break;
                }
            }
        }
        // Now, from the map create a list of work items that are either
        // - enqueued (not yet running or retrying)
        // - running
        // - failed
        boolean hasEnqueuedWork = false;
        boolean hasRunningWork = false;
        List<Pair<String, WorkInfo>> failedInfos = new ArrayList<>();

        for (Map.Entry<String, WorkInfo> entry : latestInfoByTag.entrySet()) {
            WorkInfo.State state = entry.getValue().getState();
            if (state == WorkInfo.State.ENQUEUED) {
                hasEnqueuedWork = true;
            } else if (state == WorkInfo.State.RUNNING) {
                hasRunningWork = true;
            } else if (state == WorkInfo.State.FAILED) {
                failedInfos.add(Pair.create(entry.getKey(), entry.getValue()));
            }
        }

        final NotificationManager nm =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (!failedInfos.isEmpty()) {
            // show error notification
            ArrayList<CharSequence> errors = new ArrayList<>();
            ArrayList<BackgroundTasksManager.RetryInfo> retryInfos = new ArrayList<>();
            for (Pair<String, WorkInfo> entry : failedInfos) {
                Data data = entry.second.getOutputData();
                final String itemName = data.getString(ItemUpdateWorker.OUTPUT_DATA_ITEM);
                final String value = data.getString(ItemUpdateWorker.OUTPUT_DATA_VALUE);
                final boolean hadConnection =
                        data.getBoolean(ItemUpdateWorker.OUTPUT_DATA_HAS_CONNECTION, false);
                final int httpStatus =
                        data.getInt(ItemUpdateWorker.OUTPUT_DATA_HTTP_STATUS, 0);

                retryInfos.add(new BackgroundTasksManager.RetryInfo(entry.first, itemName, value));
                if (hadConnection) {
                    errors.add(mContext.getString(
                            R.string.item_update_http_error, itemName, httpStatus));
                } else {
                    errors.add(mContext.getString(
                            R.string.item_update_connection_error, itemName));
                }
            }
            Notification n = createErrorNotification(mContext, errors, retryInfos);
            createNotificationChannels(mContext);
            nm.notify(NOTIFICATION_ID_BACKGROUND_WORK, n);
        } else if (hasRunningWork || hasEnqueuedWork) {
            // show waiting notification
            @StringRes int messageResId = hasRunningWork
                    ? R.string.item_upload_in_progress : R.string.waiting_for_item_upload;
            Notification n = createProgressNotification(mContext, messageResId);
            createNotificationChannels(mContext);
            nm.notify(NOTIFICATION_ID_BACKGROUND_WORK, n);
        } else {
            // clear notification
            nm.cancel(NOTIFICATION_ID_BACKGROUND_WORK);
        }
    }

    /**
     * Creates notification channels for background tasks.
     * @param context
     */
    static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager nm = context.getSystemService(NotificationManager.class);

        String name = context.getString(R.string.notification_channel_background);
        String description =
                context.getString(R.string.notification_channel_background_description);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID_BACKGROUND, name,
                NotificationManager.IMPORTANCE_MIN);
        channel.setDescription(description);
        nm.createNotificationChannel(channel);

        name = context.getString(R.string.notification_channel_background_error);
        description =
                context.getString(R.string.notification_channel_background_error_description);
        channel = new NotificationChannel(CHANNEL_ID_BACKGROUND_ERROR, name,
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        channel.setLightColor(ContextCompat.getColor(context, R.color.openhab_orange));
        channel.enableLights(true);
        nm.createNotificationChannel(channel);
    }

    private static Notification createProgressNotification(Context context,
            @StringRes int messageResId) {
        return createBaseBuilder(context, CHANNEL_ID_BACKGROUND)
                .setContentText(context.getString(messageResId))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    private static Notification createErrorNotification(Context context,
            ArrayList<CharSequence> errors,
            ArrayList<BackgroundTasksManager.RetryInfo> retryInfos) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String text = context.getResources().getQuantityString(R.plurals.item_update_error_title,
                errors.size(), errors.size());

        NotificationCompat.Builder nb = createBaseBuilder(context, CHANNEL_ID_BACKGROUND_ERROR)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setLights(ContextCompat.getColor(context, R.color.openhab_orange), 3000, 3000)
                .setSound(Uri.parse(prefs.getString(Constants.PREFERENCE_TONE, "")))
                .setVibrate(Util.getNotificationVibrationPattern(context));

        if (errors.size() > 1) {
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            for (CharSequence error : errors) {
                style.addLine(error);
            }
            nb.setStyle(style);
        } else {
            nb.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(errors.get(0))
                    .setBigContentTitle(text));
        }

        if (!retryInfos.isEmpty()) {
            Intent retryIntent = new Intent(context, BackgroundTasksManager.class)
                    .setAction(BackgroundTasksManager.ACTION_RETRY_UPLOAD)
                    .putExtra(BackgroundTasksManager.EXTRA_RETRY_INFOS, retryInfos);
            PendingIntent retryPendingIntent = PendingIntent.getBroadcast(context, 0,
                    retryIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            nb.addAction(new NotificationCompat.Action(R.drawable.ic_refresh_grey_24dp,
                    context.getString(R.string.retry), retryPendingIntent));
        }

        return nb.build();
    }

    private static NotificationCompat.Builder createBaseBuilder(Context context,
            String channelId) {
        Intent notificationIntent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                notificationIntent, 0);

        return new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
                .setContentTitle(context.getString(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setColor(ContextCompat.getColor(context, R.color.openhab_orange));
    }
}
