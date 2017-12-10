package org.openhab.habdroid.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.openhab.habdroid.ui.OpenHABMainActivity;

import static org.openhab.habdroid.core.CustomBroadcastReceiver.CUSTOM_BROADCAST_RECEIVER_INTENT;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "fooooo");
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try {
                Intent i = new Intent(context, OpenHABMainActivity.class);
                i.setAction(CUSTOM_BROADCAST_RECEIVER_INTENT);
                context.startActivity(i);

            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }
}