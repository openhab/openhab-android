package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.model.OpenHABFrameLabelList;
import org.openhab.habdroid.util.Util;

public class OpenHABBeaconConfigActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_beacon_config);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        EditText beaconName = findViewById(R.id.beacon_name_edit_text);
        EditText beaconUrl = findViewById(R.id.beacon_url_edit_text);
        Spinner framesSelect = findViewById(R.id.beacon_frames_spinner);

        OpenHABBeacon beacon = (OpenHABBeacon) getIntent().
                getSerializableExtra(OpenHABBeaconConfigListActivity.BEACON_KEY);

        beaconName.setText(beacon.name());
        if (beacon.type() != OpenHABBeacon.Type.EddystoneUrl) {
            beaconUrl.setEnabled(false);
            beaconUrl.setHint(R.string.beacon_url_unavailable_hint);
        } else {
            beaconUrl.setText(beacon.url());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this
                , R.layout.spinner_dropdown_item, OpenHABFrameLabelList.getInstance());
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        framesSelect.setAdapter(adapter);
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
