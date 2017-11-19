package org.openhab.habdroid.nonfree;

import android.content.Context;
import android.util.Log;

public class myGoogleCloudMessaging {
    private static final String TAG = myGoogleCloudMessaging.class.getSimpleName();

    public static myGoogleCloudMessaging getInstance(Context context) {
        Log.e(TAG, "Running foss build: don't return gcm instance");
        return null;
    }
}
