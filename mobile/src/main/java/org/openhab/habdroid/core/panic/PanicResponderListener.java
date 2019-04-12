package org.openhab.habdroid.core.panic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openhab.habdroid.util.Constants;

/**
 * Respond to a PanicKit trigger intent by locking the app.  PanicKit provides a
 * common framework for creating "panic button" apps that can trigger actions
 * in "panic responder" apps.
 */
public class PanicResponderListener extends BroadcastReceiver {
    private final static String TAG = PanicResponderListener.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null
                && "info.guardianproject.panic.action.TRIGGER".equals(intent.getAction())) {
            Log.d(TAG, "Got panic intent");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            if (prefs.getBoolean(Constants.PREFERENCE_SCREENLOCK, false)) {
                Log.d(TAG, "Close app");
                ExitActivity.exitAndRemoveFromRecentApps(context);
            }
        }
    }
}