package org.openhab.habdroid.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.model.OpenHABFrameLabelList;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

public class OpenHABBeaconConfigActivity extends AppCompatActivity
        implements AdapterView.OnItemSelectedListener{

    private OpenHABBeacon mBeacon;
    private SharedPreferences mSharedPreferences;

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
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_BEACON_FRAME_PAIR_FILE, 0);

        mBeacon = (OpenHABBeacon) getIntent()
                .getSerializableExtra(OpenHABBeaconConfigListActivity.BEACON_KEY);

        beaconName.setText(mBeacon.name());
        if (mBeacon.type() != OpenHABBeacon.Type.EddystoneUrl) {
            beaconUrl.setEnabled(false);
            beaconUrl.setHint(R.string.beacon_url_unavailable_hint);
        } else {
            beaconUrl.setText(mBeacon.url());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this
                , R.layout.spinner_dropdown_frame_item, OpenHABFrameLabelList.getInstance());
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_frame_item);
        framesSelect.setAdapter(adapter);
        framesSelect.setOnItemSelectedListener(this);
        setDefaultSelection(framesSelect);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String frameLabel = parent.getItemAtPosition(position).toString();
        if (frameLabel == null || frameLabel.isEmpty()) {
            frameLabel = getString(R.string.frame_label_not_available);
        }
        if (frameLabel.equals(OpenHABFrameLabelList.NONE)) {
            mSharedPreferences.edit().remove(mBeacon.address()).commit();
        } else {
            mSharedPreferences.edit().putString(mBeacon.address(), frameLabel).apply();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        //Nothing happened
    }

    private void setDefaultSelection(Spinner spinner) {
        String frameLabel = mSharedPreferences.getString(mBeacon.address(), null);
        if (frameLabel != null) {
            int index = OpenHABFrameLabelList.getInstance().indexOf(frameLabel);
            if (index >= 0) {
                spinner.setSelection(index);
            }
        }

    }
}
