package org.openhab.habdroid.util.bleBeaconUtil;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABBeacon;
import org.openhab.habdroid.ui.OpenHABBleAdapter;

@TargetApi(18)
public class BleBeaconConnector {
    private static final String TAG = BleBeaconConnector.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 0;
    public static final int SCAN_PERIOD = 5000;//Scan for 3s

    private static BleBeaconConnector INSTANCE;

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private boolean notSupport;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;
    private Runnable stopLeScan;

    private BleBeaconConnector(AppCompatActivity activity){
        final BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        //If no bluetooth hardware, stopping the initialization
        if (mBluetoothAdapter == null){
            notSupport = true;
            return;
        }

        //Request to open bluetooth it's not enabled.
        if (!mBluetoothAdapter.isEnabled()){
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }
        checkAndRequestPosPermission(activity);
        mHandler = new Handler();
        stopLeScan = () -> mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    private void checkAndRequestPosPermission(AppCompatActivity activity){
        String[] posPermission = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        if(ContextCompat.checkSelfPermission(activity, posPermission[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, posPermission, REQUEST_ENABLE_BT);
        }
    }

    @SuppressWarnings("deprecation")
    public void startLeScan(){
        mHandler.postDelayed(stopLeScan, SCAN_PERIOD);
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    public void stopLeScan(){
        mHandler.removeCallbacks(stopLeScan);
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    public static BleBeaconConnector getInstance(AppCompatActivity activity) {
        if (INSTANCE == null){
            INSTANCE = new BleBeaconConnector(activity);
        }
        return INSTANCE;
    }

    public boolean isNotSupport(){
        return notSupport;
    }

    public void bindLeScanCallback(OpenHABBleAdapter openHABBleAdapter){
        mLeScanCallback = (bluetoothDevice, i, bytes) -> {
            OpenHABBeacon.Builder builder = BeaconParser.parseToBeacon(bytes);
            if (builder == null){//Not a beacon
                return;
            }
            OpenHABBeacon beacon = builder
                    .setName(bluetoothDevice.getName())
                    .setAddress(bluetoothDevice.getAddress())
                    .setRssi(i)
                    .build();

            //Add item to adapter
            openHABBleAdapter.addBeacon(beacon);
            Log.d(TAG, beacon.toString());
        };
    }
}
