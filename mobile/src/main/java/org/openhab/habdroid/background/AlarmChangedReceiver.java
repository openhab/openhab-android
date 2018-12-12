package org.openhab.habdroid.background;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.openhab.habdroid.R;

import static android.content.Context.NOTIFICATION_SERVICE;
import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_ID_SEND_ALARM_CLOCK;
import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_TAG_BACKGROUND;
import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_TAG_BACKGROUND_ERROR;
import static org.openhab.habdroid.background.BackgroundUtils.WORKER_TAG_SEND_ALARM_CLOCK;
import static org.openhab.habdroid.background.BackgroundUtils.createNotificationChannel;
import static org.openhab.habdroid.background.BackgroundUtils.makeBackgroundNotificationBuilder;
import static org.openhab.habdroid.util.Constants.PREFERENCE_ALARM_CLOCK_ENABLED;

public class AlarmChangedReceiver extends BroadcastReceiver {
    private final static String TAG = AlarmChangedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive() with intent " + intent.getAction());
        if (!intent.getAction().equals("android.app.action.NEXT_ALARM_CLOCK_CHANGED")
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                || ! PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREFERENCE_ALARM_CLOCK_ENABLED, true)) {
            Log.d(TAG, "Don't send data");
            return;
        }

        startAlarmChangedWorker(context);
    }

    public static void startAlarmChangedWorker(Context context) {
        Log.d(TAG, "startAlarmChangedWorker()");
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest sendAlarmClockWorker =
                new OneTimeWorkRequest.Builder(AlarmChangedWorker.class)
                        .setConstraints(constraints)
                        .addTag(WORKER_TAG_SEND_ALARM_CLOCK)
                        .build();

        WorkManager workManager = WorkManager.getInstance();
        NotificationManager nm =
                (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);

        Log.d(TAG, "Cancel previous worker");
        workManager.cancelAllWorkByTag(WORKER_TAG_SEND_ALARM_CLOCK);
        nm.cancel(NOTIFICATION_TAG_BACKGROUND_ERROR, NOTIFICATION_ID_SEND_ALARM_CLOCK);

        Log.d(TAG, "Schedule worker");
        workManager.enqueue(sendAlarmClockWorker);

        createNotificationChannel(context);
        Notification notification = makeBackgroundNotificationBuilder(context,
                R.string.waiting_for_network_to_send_alarm_clock, R.drawable.ic_alarm_grey_24dp,
                false, null);
        nm.notify(NOTIFICATION_TAG_BACKGROUND, NOTIFICATION_ID_SEND_ALARM_CLOCK, notification);
    }
}
