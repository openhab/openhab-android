package org.openhab.habdroid.core;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.util.bleBeaconUtil.BleBeaconConnector;

import java.util.ArrayList;
import java.util.List;

public class OpenHABBleService extends Service implements Runnable {
    private static final String TAG = OpenHABBleService.class.getSimpleName();

    private IBinder mBinder;
    private Handler mMainHandler;
    private Handler mScanHandler;
    private ConfigUiUpdateListener mConfigUiUpdateListener;
    private MinBeaconUiUpdateListener mMinBeaconUiUpdateListener;

    private BleBeaconConnector mBleBeaconConnector;
    private List<OpenHABBeacon> mBeaconList;
    private OpenHABBeacon mMinBeacon;

    public class LocalBinder extends Binder {
        public OpenHABBleService getService(){
            return OpenHABBleService.this;
        }
    }

    public interface ConfigUiUpdateListener {
        void itemChange(int position);
        void itemInsert(int position);
        void bindItemList(List<OpenHABBeacon> beaconList);
    }

    public interface MinBeaconUiUpdateListener {
        void itemChange(OpenHABBeacon beacon);
    }

    @Override
    public void onCreate() {
        mBinder = new LocalBinder();
        mBeaconList = new ArrayList<>();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        mBleBeaconConnector = BleBeaconConnector.getInstance();
        mScanHandler = mBleBeaconConnector.getHandler();
        mMainHandler = new Handler(getMainLooper());
        mBleBeaconConnector.bindLeScanCallback(this);
        mScanHandler.post(this);//Start scanning

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

            if (mConfigUiUpdateListener != null) {
                mMainHandler.post(() -> mConfigUiUpdateListener.itemChange(index));
            }
        } else {
            mBeaconList.add(beacon);

            if (mConfigUiUpdateListener != null) {
                mMainHandler.post(() -> mConfigUiUpdateListener
                        .itemInsert(mBeaconList.size() - 1));
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

    private void updateMin(OpenHABBeacon beacon){
        if (mMinBeacon == null || mMinBeacon.address().equals(beacon.address())
                || beacon.distance() < mMinBeacon.distance()){
            mMinBeacon = beacon;

            if (mMinBeaconUiUpdateListener != null) {
                mMainHandler.post(() -> mMinBeaconUiUpdateListener.itemChange(beacon));
            }

            Log.d(TAG, "Min beacon changed to: " + mMinBeacon);
        }
    }

    //Scan for a period, wait for the same period and rescan.
    @Override
    public void run() {
        mBleBeaconConnector.startLeScan();
        mScanHandler.postDelayed(() -> mBleBeaconConnector.stopLeScan(), BleBeaconConnector.SCAN_PERIOD);
        mScanHandler.postDelayed(this, BleBeaconConnector.SCAN_PERIOD  << 1);
    }

    public void setConfigUiUpdateListener(ConfigUiUpdateListener listener){
        mConfigUiUpdateListener = listener;
        mConfigUiUpdateListener.bindItemList(mBeaconList);
    }

    public void setMinBeaconUiUpdateListener(MinBeaconUiUpdateListener listener){
        mMinBeaconUiUpdateListener = listener;
    }

    public OpenHABBeacon get(int index){
        return mBeaconList.get(index);
    }
}
