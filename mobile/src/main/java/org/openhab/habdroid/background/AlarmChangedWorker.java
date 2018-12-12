package org.openhab.habdroid.background;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.work.Result;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.SyncHttpClient;

import java.util.Locale;

import static android.content.Context.NOTIFICATION_SERVICE;
import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_ID_SEND_ALARM_CLOCK;
import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_TAG_BACKGROUND;
import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_TAG_BACKGROUND_ERROR;
import static org.openhab.habdroid.background.BackgroundUtils.makeBackgroundNotificationBuilder;
import static org.openhab.habdroid.util.Constants.PREFERENCE_ALARM_CLOCK_ITEM;

public class AlarmChangedWorker extends Worker {
    private final static String TAG = AlarmChangedWorker.class.getSimpleName();
    private final static int MAX_RETRY = 3;

    public AlarmChangedWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NotificationManager nm =
                (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        Connection connection;
        ConnectionException failureReason;
        try {
            Log.d(TAG, "Trying to get connection");
            connection = ConnectionFactory.getUsableConnection();
            failureReason = null;
        } catch (ConnectionException e) {
            Log.e(TAG, "ConnectionException", e);
            connection = null;
            failureReason = e;
        }

        if (connection == null) {
            Log.e(TAG, "Got no connection "
                    + (failureReason != null ? failureReason.getMessage() : null));
            if (getRunAttemptCount() > MAX_RETRY) {
                Notification notification = makeBackgroundNotificationBuilder(context,
                        context.getString(R.string.error_sending_alarm_clock_no_connection),
                        R.drawable.ic_alarm_grey_24dp, true,
                        getAlarmClockRetryAction(context));
                return fail(notification, nm);
            }
            return retry(context, nm);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        String nextAlarm;
        if (alarmManager.getNextAlarmClock() == null) {
            nextAlarm = "0";
        } else {
            nextAlarm = String.valueOf(alarmManager.getNextAlarmClock().getTriggerTime());
        }
        String item = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREFERENCE_ALARM_CLOCK_ITEM, "");
        if (TextUtils.isEmpty(item.trim())) {
            Notification notification = makeBackgroundNotificationBuilder(context,
                    R.string.error_sending_alarm_clock_item_empty,
                    R.drawable.ic_alarm_grey_24dp, true, null);
            return fail(notification, nm);
        }

        String url = String.format(Locale.US, "rest/items/%s", item);
        SyncHttpClient.HttpResult result = connection.getSyncHttpClient()
                .post(url, nextAlarm, "text/plain;charset=UTF-8");

        if (result.isSuccessful()) {
            Log.d(TAG, "Alarm clock successfully sent: " + nextAlarm);
            nm.cancel(NOTIFICATION_TAG_BACKGROUND, NOTIFICATION_ID_SEND_ALARM_CLOCK);
            return Result.success();
        } else {
            Log.e(TAG, "Error sending alarm clock. Got HTTP error "
                    + result.statusCode, result.error);

            if (getRunAttemptCount() > MAX_RETRY) {
                Notification notification = makeBackgroundNotificationBuilder(context,
                        context.getString(R.string.error_sending_alarm_clock, result.statusCode),
                        R.drawable.ic_alarm_grey_24dp, true,
                        getAlarmClockRetryAction(context));
                return fail(notification, nm);
            }
            return retry(context, nm);
        }
    }

    @CheckResult
    private Result fail(Notification notification, NotificationManager nm) {
        Log.e(TAG, "Don't retry again");
        nm.cancel(NOTIFICATION_TAG_BACKGROUND, NOTIFICATION_ID_SEND_ALARM_CLOCK);
        nm.notify(NOTIFICATION_TAG_BACKGROUND_ERROR, NOTIFICATION_ID_SEND_ALARM_CLOCK,
                notification);
        return Result.failure();
    }

    @CheckResult
    private Result retry(Context context, NotificationManager nm) {
        Log.d(TAG, "Retry");
        Notification notification = makeBackgroundNotificationBuilder(context,
                context.getString(R.string.error_sending_alarm_clock_retry),
                R.drawable.ic_alarm_grey_24dp, false,
                getAlarmClockRetryAction(context));
        nm.notify(NOTIFICATION_TAG_BACKGROUND, NOTIFICATION_ID_SEND_ALARM_CLOCK,
                notification);
        return Result.retry();
    }

    private NotificationCompat.Action getAlarmClockRetryAction(Context context) {
        return BackgroundUtils.getRetryAction(context, NOTIFICATION_ID_SEND_ALARM_CLOCK,
                NOTIFICATION_TAG_BACKGROUND_ERROR);
    }
}
