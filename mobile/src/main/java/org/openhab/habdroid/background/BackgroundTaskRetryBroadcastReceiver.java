package org.openhab.habdroid.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static org.openhab.habdroid.background.BackgroundUtils.NOTIFICATION_ID_SEND_ALARM_CLOCK;

public class BackgroundTaskRetryBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = BackgroundTaskRetryBroadcastReceiver.class.getSimpleName();
    public static final String OH_EXTRA_NOTIFICATION_ID = "org.openhab.habdroid.notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received intent: " + intent);
        int id = intent.getIntExtra(OH_EXTRA_NOTIFICATION_ID, 0);
        switch (id) {
            case NOTIFICATION_ID_SEND_ALARM_CLOCK:
                Log.d(TAG, "Got retry intent for alarm clock");
                BackgroundTasksBroadcastReceiver.startAlarmChangedWorker(context);
                break;
            default:
                Log.e(TAG, "Got intent without valid id: " + id);
        }
    }
}
