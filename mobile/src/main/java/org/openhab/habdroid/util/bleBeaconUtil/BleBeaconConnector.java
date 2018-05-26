package org.openhab.habdroid.util.bleBeaconUtil;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABBeacon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TargetApi(18)
public class BleBeaconConnector {
    private static final String TAG = BleBeaconConnector.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int SCAN_PERIOD = 10000;//Scan for 10s

    private static BleBeaconConnector INSTANCE;

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    private boolean notSupport;
    private BluetoothAdapter.LeScanCallback leScanCallback = (bluetoothDevice, i, bytes) -> {
        OpenHABBeacon.Builder builder = BeaconParser.parseToBeacon(bytes);
        if (builder == null){//Not a beacon
            return;
        }
        OpenHABBeacon beacon = builder
                .setName(bluetoothDevice.getName())
                .setAddress(bluetoothDevice.getAddress())
                .setRssi(i)
                .build();

        Log.i(TAG, beacon.toString());
    };

    //Enforce singleton by using private constructors
    private BleBeaconConnector(){}

    private BleBeaconConnector(AppCompatActivity activity){
        checkAndRequestPosPermission(activity);
        handler = new Handler();
        final BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        //Request to open bluetooth it's not enabled.
        if (bluetoothAdapter == null){
            notSupport = true;
        }
        if (!bluetoothAdapter.isEnabled()){//TODO - Crash on the devices don't have Bluetooth. Change this logic later.
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }
    }

    private void checkAndRequestPosPermission(AppCompatActivity activity){
        String[] posPermission = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        if(ContextCompat.checkSelfPermission(activity, posPermission[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, posPermission, REQUEST_ENABLE_BT);
        }
    }
    @SuppressWarnings("deprecation")
    public void scanLeServiceCompact(){
        handler.postDelayed(() -> bluetoothAdapter.stopLeScan(leScanCallback), SCAN_PERIOD);
        bluetoothAdapter.startLeScan(leScanCallback);
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
}
