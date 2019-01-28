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
import android.util.Log;
import android.util.Pair;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.widget.ItemUpdatingPreference;
import org.openhab.habdroid.util.Constants;

import static org.openhab.habdroid.util.Constants.PREFERENCE_ALARM_CLOCK;

public class BackgroundTasksBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = BackgroundTasksBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive() with intent " + intent.getAction());

        if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(intent.getAction())) {
            Log.d(TAG, "Alarm clock changed");
            startAlarmChangedWorker(context);
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            Log.d(TAG, "Locale changed, recreate notification channels");
            BackgroundUtils.createNotificationChannels(context);
        }
    }

    public static void startAlarmChangedWorker(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Pair<Boolean, String> setting =
                ItemUpdatingPreference.parseValue(prefs.getString(PREFERENCE_ALARM_CLOCK, null));
        String prefix = prefs.getString(Constants.PREFERENCE_SEND_DEVICE_INFO_PREFIX, "");
        startAlarmChangedWorker(context, prefix, setting);
    }

    public static void startAlarmChangedWorker(Context context, String prefix,
                Pair<Boolean, String> settings) {
        Log.d(TAG, "startAlarmChangedWorker()");
        if (settings == null || !settings.first
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        String nextAlarm;
        AlarmManager.AlarmClockInfo alarmClockInfo = alarmManager.getNextAlarmClock();
        if (alarmClockInfo == null) {
            nextAlarm = "0";
        } else {
            nextAlarm = String.valueOf(alarmClockInfo.getTriggerTime());
        }

        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        final Data data = ItemUpdateWorker.buildData(prefix + settings.second, nextAlarm,
                BackgroundUtils.NOTIFICATION_ID_SEND_ALARM_CLOCK,
                R.string.error_sending_alarm_clock_retry,
                R.string.error_sending_alarm_clock_no_connection,
                R.string.error_sending_alarm_clock);
        final OneTimeWorkRequest sendAlarmClockWorker =
                new OneTimeWorkRequest.Builder(ItemUpdateWorker.class)
                .setConstraints(constraints)
                .addTag(BackgroundUtils.WORKER_TAG_SEND_ALARM_CLOCK)
                .setInputData(data)
                .build();

        final WorkManager workManager = WorkManager.getInstance();
        final NotificationManager nm =
                (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

        Log.d(TAG, "Cancel previous worker");
        workManager.cancelAllWorkByTag(BackgroundUtils.WORKER_TAG_SEND_ALARM_CLOCK);
        nm.cancel(BackgroundUtils.NOTIFICATION_TAG_BACKGROUND_ERROR,
                BackgroundUtils.NOTIFICATION_ID_SEND_ALARM_CLOCK);

        Log.d(TAG, "Schedule worker");
        workManager.enqueue(sendAlarmClockWorker);

        BackgroundUtils.createNotificationChannels(context);
        Notification notification = BackgroundUtils.makeBackgroundNotification(context,
                context.getString(R.string.waiting_for_network_to_send_alarm_clock),
                true,
                false,
                false, null);
        nm.notify(BackgroundUtils.NOTIFICATION_TAG_BACKGROUND,
                BackgroundUtils.NOTIFICATION_ID_SEND_ALARM_CLOCK, notification);
    }
}
