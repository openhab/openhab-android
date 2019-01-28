package org.openhab.habdroid.background;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.Result;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.SyncHttpClient;

import java.util.Locale;

public class ItemUpdateWorker extends Worker {
    private static final String TAG = ItemUpdateWorker.class.getSimpleName();
    private static final int MAX_RETRIES = 3;

    private static final String DATA_ITEM = "item";
    private static final String DATA_VALUE = "value";
    private static final String DATA_NOTIFICATION_ID = "notificationId";
    private static final String DATA_RETRY_TEXT = "retryText";
    private static final String DATA_NO_CONNECTION_TEXT = "noConnectionText";
    private static final String DATA_FAILURE_TEXT = "failureText";

    public static Data buildData(String item, String value, int notificationId,
            @StringRes int retryTextResId,
            @StringRes int connectionErrorTextResId,
            @StringRes int httpFailureTextResId) {
        return new Data.Builder()
                .putString(DATA_ITEM, item)
                .putString(DATA_VALUE, value)
                .putInt(DATA_NOTIFICATION_ID, notificationId)
                .putInt(DATA_RETRY_TEXT, retryTextResId)
                .putInt(DATA_NO_CONNECTION_TEXT, connectionErrorTextResId)
                .putInt(DATA_FAILURE_TEXT, httpFailureTextResId)
                .build();
    }

    public ItemUpdateWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        ConnectionFactory.waitForInitialization();

        final Data data = getInputData();
        final int notificationId = data.getInt(DATA_NOTIFICATION_ID, 0);
        Connection connection;

        try {
            Log.d(TAG, "Trying to get connection");
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            Log.e(TAG, "Got no connection " + e);
            return retryOrFail(
                    context.getString(data.getInt(DATA_NO_CONNECTION_TEXT, 0)),
                    context.getString(data.getInt(DATA_RETRY_TEXT, 0)),
                    notificationId, nm);
        }

        final String item = data.getString(DATA_ITEM);
        final String value = getInputData().getString(DATA_VALUE);
        final String url = String.format(Locale.US, "rest/items/%s", item);
        final SyncHttpClient.HttpResult result = connection.getSyncHttpClient()
                .post(url, value, "text/plain;charset=UTF-8");

        if (result.isSuccessful()) {
            Log.d(TAG, "Item '" + item + "' successfully updated to value " + value);
            nm.cancel(BackgroundUtils.NOTIFICATION_TAG_BACKGROUND, notificationId);
            return Result.success();
        } else {
            Log.e(TAG, "Error sending alarm clock. Got HTTP error "
                    + result.statusCode, result.error);

            return retryOrFail(
                    context.getString(data.getInt(DATA_FAILURE_TEXT, 0), result.statusCode),
                    context.getString(data.getInt(DATA_RETRY_TEXT, 0)),
                    notificationId, nm);
        }
    }

    /**
     * Makes a "Retry" action.
     * @param context
     * @param notificationId Id of the current notification
     * @return
     */
    private static NotificationCompat.Action makeRetryAction(Context context, int notificationId) {
        Intent retryIntent = new Intent(context, BackgroundTaskRetryBroadcastReceiver.class);
        retryIntent.putExtra(
                BackgroundTaskRetryBroadcastReceiver.OH_EXTRA_NOTIFICATION_ID, notificationId);
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
     * @param notificationId Notification ID
     * @param nm NotificationManager instance
     * @return Worker result.
     */
    @CheckResult
    private Result retryOrFail(@NonNull String errorMessage,
            @Nullable String retryMessage, int notificationId, @NonNull NotificationManager nm) {
        final Context context = getApplicationContext();
        final NotificationCompat.Action retryAction = makeRetryAction(context, notificationId);

        if (getRunAttemptCount() > MAX_RETRIES) {
            Log.e(TAG, "Don't retry again. Error: " + errorMessage);
            Notification notification = BackgroundUtils.makeBackgroundNotification(context,
                    errorMessage,
                    false,
                    false,
                    true,
                    retryAction);
            nm.cancel(BackgroundUtils.NOTIFICATION_TAG_BACKGROUND, notificationId);
            nm.notify(BackgroundUtils.NOTIFICATION_TAG_BACKGROUND_ERROR, notificationId,
                    notification);
            return Result.failure();
        }
        Log.d(TAG, "Retry: " +  retryMessage);
        if (retryMessage != null) {
            Notification notification = BackgroundUtils.makeBackgroundNotification(context,
                    retryMessage,
                    true,
                    false,
                    false,
                    retryAction);
            nm.notify(BackgroundUtils.NOTIFICATION_TAG_BACKGROUND, notificationId,
                    notification);
        }
        return Result.retry();
    }
}
