package org.openhab.habdroid.core;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.ui.OpenHABBleAdapter;
import org.openhab.habdroid.util.bleBeaconUtil.BleBeaconConnector;

import java.util.ArrayList;
import java.util.List;

public class OpenHABBleService extends IntentService{
    private static final String TAG = OpenHABBleService.class.getSimpleName();
    private IBinder mBinder;
    private BleBeaconConnector mBleBeaconConnector;
    private List<OpenHABBeacon> mBeaconList;
    private OpenHABBleAdapter mOpenHABBleAdapter;
    private OpenHABBeacon mMinBeacon;

    public class LocalBinder extends Binder {
        public OpenHABBleService getService(){
            return OpenHABBleService.this;
        }
    }

    public OpenHABBleService(){
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        while (true){
            try {
                mBleBeaconConnector.startLeScan();
                Thread.sleep(BleBeaconConnector.SCAN_PERIOD);
            } catch (InterruptedException e) {
                Log.d(TAG, "Sleep failed!");
                break;
            }
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        mBinder = new LocalBinder();
        mBleBeaconConnector = BleBeaconConnector.getInstance();
        mBeaconList = new ArrayList<>();
        mBleBeaconConnector.bindLeScanCallback(this);
        Toast.makeText(this, R.string.ble_service_start, Toast.LENGTH_SHORT).show();
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void addBeacon(OpenHABBeacon beacon){
        int index;

        //Update if already exists. Avoid duplicate.
        if ((index = findBeaconByAddress(beacon.address())) >= 0){
            mBeaconList.set(index, beacon);

            if (mOpenHABBleAdapter != null) {
                mOpenHABBleAdapter.notifyItemChanged(index);
            }
        } else {
            mBeaconList.add(beacon);

            if (mOpenHABBleAdapter != null) {
                mOpenHABBleAdapter.notifyItemInserted(mBeaconList.size() - 1);
            }
        }
        updateMin(beacon);
    }

    private int findBeaconByAddress(String address){
        for (int i = 0; i < mBeaconList.size(); i++){
            if (address.equals(mBeaconList.get(i).address())){
                return i;
            }
        }
        return -1;
    }

    public void bindOpenHABBleAdapter(OpenHABBleAdapter openHABBleAdapter){
        mOpenHABBleAdapter = openHABBleAdapter;
        mOpenHABBleAdapter.bindData(mBeaconList);
    }

    public void unBindOpenHABBleAdapter(){
        mOpenHABBleAdapter = null;
    }

    private void updateMin(OpenHABBeacon beacon){
        if (mMinBeacon == null || mMinBeacon.address().equals(beacon.address())
                || beacon.distance() < mMinBeacon.distance()){
            mMinBeacon = beacon;
            Log.d(TAG, "Min beacon changed to: " + mMinBeacon);
        }
    }
}
