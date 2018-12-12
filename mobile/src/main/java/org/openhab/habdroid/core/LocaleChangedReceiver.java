package org.openhab.habdroid.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.openhab.habdroid.background.BackgroundUtils;

public class LocaleChangedReceiver extends BroadcastReceiver {
    private static final String TAG = LocaleChangedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Locale changed, recreate notification channels");
        BackgroundUtils.createNotificationChannels(context);
    }
}
