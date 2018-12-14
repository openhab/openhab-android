package org.openhab.habdroid.background;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
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
import static org.openhab.habdroid.util.Constants.PREFERENCE_ALARM_CLOCK_ENABLED;
import static org.openhab.habdroid.util.Constants.PREFERENCE_ALARM_CLOCK_ITEM;

public class BackgroundTasksBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = BackgroundTasksBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive() with intent " + intent.getAction());

        if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(intent.getAction())) {
            Log.d(TAG, "Alarm clock changed");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startAlarmChangedWorker(context);
            }
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            Log.d(TAG, "Locale changed, recreate notification channels");
            BackgroundUtils.createNotificationChannels(context);
        }
    }

    public static void startAlarmChangedWorker(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        startAlarmChangedWorker(context, prefs.getString(PREFERENCE_ALARM_CLOCK_ITEM, ""),
                prefs.getBoolean(PREFERENCE_ALARM_CLOCK_ENABLED, false));
    }

    public static void startAlarmChangedWorker(Context context, String itemName, boolean enabled) {
        Log.d(TAG, "startAlarmChangedWorker()");
        if (!enabled) {
            Log.d(TAG, "Feature is disabled");
            return;
        }
        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        final OneTimeWorkRequest sendAlarmClockWorker =
                new OneTimeWorkRequest.Builder(AlarmChangedWorker.class)
                .setConstraints(constraints)
                .addTag(WORKER_TAG_SEND_ALARM_CLOCK)
                .build();

        final WorkManager workManager = WorkManager.getInstance();
        final NotificationManager nm =
                (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        Log.d(TAG, "Cancel previous worker");
        workManager.cancelAllWorkByTag(WORKER_TAG_SEND_ALARM_CLOCK);

        if (TextUtils.isEmpty(itemName.trim())) {
            Log.d(TAG, "Empty item name");
            Notification notification = BackgroundUtils.makeBackgroundNotification(context,
                    R.string.error_sending_alarm_clock_item_empty,
                    R.drawable.ic_alarm_grey_24dp, true, null);
            nm.cancel(NOTIFICATION_TAG_BACKGROUND, NOTIFICATION_ID_SEND_ALARM_CLOCK);
            nm.notify(NOTIFICATION_TAG_BACKGROUND_ERROR, NOTIFICATION_ID_SEND_ALARM_CLOCK,
                    notification);
            return;
        }

        nm.cancel(NOTIFICATION_TAG_BACKGROUND_ERROR, NOTIFICATION_ID_SEND_ALARM_CLOCK);

        Log.d(TAG, "Schedule worker");
        workManager.enqueue(sendAlarmClockWorker);

        BackgroundUtils.createNotificationChannels(context);
        Notification notification = BackgroundUtils.makeBackgroundNotification(context,
                R.string.waiting_for_network_to_send_alarm_clock, R.drawable.ic_alarm_grey_24dp,
                false, null);
        nm.notify(NOTIFICATION_TAG_BACKGROUND, NOTIFICATION_ID_SEND_ALARM_CLOCK, notification);
    }


}
