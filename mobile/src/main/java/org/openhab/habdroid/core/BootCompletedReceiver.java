package org.openhab.habdroid.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.openhab.habdroid.util.Constants;

import static org.openhab.habdroid.core.CustomBroadcastReceiver.CUSTOM_BROADCAST_RECEIVER_INTENT;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                CUSTOM_BROADCAST_RECEIVER_INTENT.equals(intent.getAction())) {
            try {
                SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
                Intent i = new Intent(context, CustomBroadcastListenerService.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (mSettings.getBoolean(Constants.PREFERENCE_CUSTOM_BROADCAST, false)) {
                    Log.d(TAG, "start cbr");
                    i.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
                } else {
                    Log.d(TAG, "stop cbr");
                    i.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);
                }
                context.startService(i);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }
}