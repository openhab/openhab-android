package org.openhab.habdroid.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABBleService;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.util.Util;
import org.openhab.habdroid.util.bleBeaconUtil.BleBeaconConnector;

public class OpenHABBeaconConfigActivity extends AppCompatActivity
        implements OpenHABBeaconConfigAdapter.ItemClickListener{
    private OpenHABBeaconConfigAdapter mOpenHABBeaconConfigAdapter;
    private OpenHABBleService mBleService;
    private Intent mBleServiceIntent;

    private ServiceConnection mBleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((OpenHABBleService.LocalBinder)service).getService();
            mBleService.setConfigUiUpdateListener(mOpenHABBeaconConfigAdapter);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleService.setConfigUiUpdateListener(null);
            mBleService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_beacon_config);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        RecyclerView recyclerView = findViewById(R.id.ble_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mOpenHABBeaconConfigAdapter = new OpenHABBeaconConfigAdapter();
        mOpenHABBeaconConfigAdapter.setItemClickListener(this);
        recyclerView.setAdapter(mOpenHABBeaconConfigAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new DividerItemDecoration(this));

        boolean bleNotSupport = BleBeaconConnector.getInstance().isNotSupport();
        if (!bleNotSupport){
            mBleServiceIntent = new Intent(this, OpenHABBleService.class);
        }
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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(int position) {
        OpenHABBeacon beaconClicked = mBleService.get(position);
        Toast.makeText(this, position + " was touched", Toast.LENGTH_SHORT).show();
    }
}
