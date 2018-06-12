package org.openhab.habdroid.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABBleService;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.util.Util;
import org.openhab.habdroid.util.bleBeaconUtil.BleBeaconConnector;

public class OpenHABBeaconActivity extends AppCompatActivity
        implements OpenHABBleService.MinBeaconUiUpdateListener{
    private OpenHABBleService mBleService;
    private Intent mBleServiceIntent;
    private TextView mMinBeaconTextView;

    private ServiceConnection mBleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((OpenHABBleService.LocalBinder)service).getService();
            mBleService.setMinBeaconUiUpdateListener(OpenHABBeaconActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleService.setMinBeaconUiUpdateListener(null);
            mBleService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ble_beacons);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mMinBeaconTextView = findViewById(R.id.nearest_beacon_text);

        FloatingActionButton fab = findViewById(R.id.ble_fab);
        fab.setOnClickListener((v) -> {
            Intent bleConfigIntent = new Intent(this, OpenHABBeaconConfigActivity.class);
            startActivity(bleConfigIntent);
        });

        boolean bleNotSupport = BleBeaconConnector.getInstance().isNotSupport();
        if (!bleNotSupport){
            mBleServiceIntent = new Intent(this, OpenHABBleService.class);
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

    @Override
    protected void onStart() {
        super.onStart();
        bindService(mBleServiceIntent, mBleServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        unbindService(mBleServiceConnection);
        super.onStop();
    }

    @Override
    public void itemChange(OpenHABBeacon beacon) {
        mMinBeaconTextView.setText(beacon.name());
    }
}
