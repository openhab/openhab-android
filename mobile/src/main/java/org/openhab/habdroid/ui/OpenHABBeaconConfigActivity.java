package org.openhab.habdroid.ui;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.util.Util;

public class OpenHABBeaconConfigActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_open_habbeacon_config);

        EditText beaconName = findViewById(R.id.beacon_name_edit_text);
        EditText beaconUrl = findViewById(R.id.beacon_url_edit_text);

        OpenHABBeacon beacon = (OpenHABBeacon) getIntent().
                getSerializableExtra(OpenHABBeaconConfigListActivity.BEACON_KEY);

        beaconName.setText(beacon.name());
        if (beacon.type() != OpenHABBeacon.Type.EddystoneUrl) {
            beaconUrl.setEnabled(false);
            beaconUrl.setHint(R.string.beacon_edit_url_unavailable_hint);
        } else {
            beaconUrl.setText(beacon.url());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
