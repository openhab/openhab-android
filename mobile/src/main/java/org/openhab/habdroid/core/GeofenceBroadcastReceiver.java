package org.openhab.habdroid.core;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * Created by jjhuff on 8/18/17.
 */

public class GeofenceBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = GeofenceBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        if(intent.getAction()!= null && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            // Did we just boot? Go register.
            Intent serviceIntent = new Intent(context , GeofenceRegistrationService.class);
            startWakefulService(context, serviceIntent);
        } else {
            ComponentName comp = new ComponentName(context.getPackageName(),
                    GeofenceIntentService.class.getName());
            startWakefulService(context, (intent.setComponent(comp)));
        }
        setResultCode(Activity.RESULT_OK);
    }
}
