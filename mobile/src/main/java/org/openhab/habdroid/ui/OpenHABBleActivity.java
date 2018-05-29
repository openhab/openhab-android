package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.util.Util;
import org.openhab.habdroid.util.bleBeaconUtil.BleBeaconConnector;

public class OpenHABBleActivity extends AppCompatActivity implements
        SwipeRefreshLayout.OnRefreshListener{
    private BleBeaconConnector mBleBeaconConnector;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ble_beacons);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mSwipeRefreshLayout = findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mRecyclerView = findViewById(R.id.ble_recyclerview);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        OpenHABBleAdapter openHABBleAdapter = new OpenHABBleAdapter();
        mRecyclerView.setAdapter(openHABBleAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this));

        mBleBeaconConnector = BleBeaconConnector.getInstance(this);
        if (mBleBeaconConnector.isNotSupport()){
            return;
        }
        mBleBeaconConnector.bindLeScanCallback(openHABBleAdapter);

        mSwipeRefreshLayout.setRefreshing(true);
        onRefresh();
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
    public void onRefresh() {
        if (mSwipeRefreshLayout.isRefreshing()) {
            ((OpenHABBleAdapter)mRecyclerView.getAdapter()).clearList();
            mBleBeaconConnector.stopLeScan();
            mBleBeaconConnector.startLeScan();
            mSwipeRefreshLayout.postDelayed(() -> mSwipeRefreshLayout.setRefreshing(false)
                    , BleBeaconConnector.SCAN_PERIOD);
        }
    }
}
