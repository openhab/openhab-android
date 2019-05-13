package org.openhab.habdroid.background;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import org.openhab.habdroid.model.NfcTag;
import org.openhab.habdroid.ui.MainActivity;

public class NfcReceiveActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null || intent.getData() == null) {
            finish();
            return;
        }

        if (Intent.ACTION_VIEW.equals(intent.getAction())
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            NfcTag tag = NfcTag.fromTagData(intent.getData());
            BackgroundTasksManager.enqueueNfcUpdateIfNeeded(this, tag);
            if (tag != null && !TextUtils.isEmpty(tag.sitemap())) {
                Intent startMainIntent = new Intent(this, MainActivity.class);
                startMainIntent.setAction(MainActivity.ACTION_SITEMAP_SELECTED);
                startMainIntent.putExtra(MainActivity.EXTRA_SITEMAP_URL, tag.sitemap());
                startActivity(startMainIntent);
            }
        }

        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }
}
