package org.openhab.habdroid.nonfree;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.openhab.habdroid.ui.OpenHABMainActivity;

public class NonfreeMainActivity extends OpenHABMainActivity {
    private static final String TAG = NonfreeMainActivity.class.getSimpleName();

    public void gcmRegisterBackground() {
        Log.e(TAG, "Running foss build: don't register gcm");
    }

    public void processIntent(Intent intent) {
        Log.e(TAG, "Running foss build: don't handle intent");
    }
}
