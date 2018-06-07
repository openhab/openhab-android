package org.openhab.habdroid.util.bleBeaconUtil;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.openhab.habdroid.core.OpenHABBleService;
import org.openhab.habdroid.model.OpenHABBeacon;

@TargetApi(18)
public class BleBeaconConnector {
    private static final String TAG = BleBeaconConnector.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 0;
    public static final int SCAN_PERIOD = 5000;//Scan for 5s

    private static BleBeaconConnector INSTANCE;

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private boolean notSupport;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

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

        //Create a worker background thread
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    private void checkAndRequestPosPermission(AppCompatActivity activity){
        String[] posPermission = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        if(ContextCompat.checkSelfPermission(activity, posPermission[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, posPermission, REQUEST_ENABLE_BT);
        }
    }

    @SuppressWarnings("deprecation")
    public void startPeriodLeScan(){
        mBluetoothAdapter.startLeScan(mLeScanCallback);
        mHandler.postDelayed(() -> mBluetoothAdapter.stopLeScan(mLeScanCallback), SCAN_PERIOD);
    }

    public static BleBeaconConnector initializeAndGetInstance(AppCompatActivity activity) {
        if (INSTANCE == null){
            INSTANCE = new BleBeaconConnector(activity);
        }
        return INSTANCE;
    }

    public static BleBeaconConnector getInstance(){
        return INSTANCE;
    }

    public boolean isNotSupport(){
        return notSupport;
    }

    public void bindLeScanCallback(OpenHABBleService bleService){
        mLeScanCallback = (bluetoothDevice, i, bytes) -> mHandler.post(() -> {
            OpenHABBeacon.Builder builder = BeaconParser.parseToBeacon(bytes);
            if (builder == null) {//Not a beacon
                return;
            }
            OpenHABBeacon beacon = builder
                    .setName(bluetoothDevice.getName())
                    .setAddress(bluetoothDevice.getAddress())
                    .setRssi(i)
                    .build();

            bleService.addBeacon(beacon);
            Log.d(TAG, beacon.toString());
        });
    }

    public Handler getHandler(){
        return mHandler;
    }
}
