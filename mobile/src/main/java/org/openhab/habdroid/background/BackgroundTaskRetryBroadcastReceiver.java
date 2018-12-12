package org.openhab.habdroid.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_ID_SEND_ALARM_CLOCK;
import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_TAG_BACKGROUND;

public class BackgroundTaskRetryBroadcastReceiver extends BroadcastReceiver {
    private final static String TAG = BackgroundTaskRetryBroadcastReceiver.class.getSimpleName();
    public final static String OH_EXTRA_NOTIFICATION_ID = "org.openhab.habdroid.notification_id";
    public final static String OH_EXTRA_NOTIFICATION_TAG = "org.openhab.habdroid.notification_tag";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received intent: " + intent);
        if (NOTIFICATION_TAG_BACKGROUND.equals(intent.getStringExtra(OH_EXTRA_NOTIFICATION_TAG))) {
            int id = intent.getIntExtra(OH_EXTRA_NOTIFICATION_ID, 0);
            switch (id) {
                case NOTIFICATION_ID_SEND_ALARM_CLOCK:
                    Log.d(TAG, "Got retry intent for alarm clock");
                    AlarmChangedReceiver.startAlarmChangedWorker(context);
                    break;
                default:
                    Log.e(TAG, "Got intent without valid id: " + id);
            }
        }
    }
}
