package org.openhab.habdroid.background;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

public class NfcReceiveActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BackgroundTasksManager.enqueueNfcItemUpload(getIntent());

        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }
}
