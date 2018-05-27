package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.util.bleBeaconUtil.BleBeaconConnector;
import org.openhab.habdroid.util.Util;

public class OpenHABBleActivity extends AppCompatActivity {
    BleBeaconConnector mBleBeaconConnector;
    RecyclerView mRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_openhab_ble_beacons);

        mRecyclerView = findViewById(R.id.ble_recyclerview);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        OpenHABBleAdapter openHABBleAdapter = new OpenHABBleAdapter();
        mRecyclerView.setAdapter(openHABBleAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this));

        mBleBeaconConnector = BleBeaconConnector.getInstance(this);
        if (mBleBeaconConnector.isNotSupport()){
            //TODO add a prompt later
            return;
        }
        mBleBeaconConnector.bindLeScanCallback(openHABBleAdapter);
        mBleBeaconConnector.scanLeServiceCompact();
    }

}
