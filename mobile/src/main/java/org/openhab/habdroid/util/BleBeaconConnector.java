package org.openhab.habdroid.util;

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
    private static final ParcelUuid BASE_UUID =
            ParcelUuid.fromString("00000000-0000-1000-8000-00805F9B34FB");
    private static final ParcelUuid EDDYSTONE_URL_SERVICE_UUID =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private static final int UUID_BYTES_16_BIT = 2;
    private static final int UUID_BYTES_32_BIT = 4;
    private static final int UUID_BYTES_128_BIT = 16;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;

    private static BleBeaconConnector INSTANCE;

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    private BluetoothAdapter.LeScanCallback leScanCallback = (bluetoothDevice, i, bytes) -> {
        if (!isEddyStoneBeacon(bytes)){//TODO add support for iBeacons
            return;
        }
        Log.d(TAG, "Device: " + bluetoothDevice.getName() + " address: " + bluetoothDevice.getAddress());
    };

    //Enforce singleton by using private constructors
    private BleBeaconConnector(){}

    private BleBeaconConnector(AppCompatActivity activity){
        checkAndRequestPosPermission(activity);
        handler = new Handler();
        final BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        //Request to open bluetooth ff it's not enabled.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()){//TODO - Crash on the devices don't have Bluetooth. Change this logic later.
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

    private boolean isEddyStoneBeacon(byte[] bytes){
        List<ParcelUuid> serviceUuids = parseFromBytes(bytes);
        return serviceUuids.contains(EDDYSTONE_URL_SERVICE_UUID);
    }

    private List<ParcelUuid> parseFromBytes(byte[] bytes){
        int length;
        int fieldType;
        int currentPos = 0;
        List<ParcelUuid> serviceUuids = new ArrayList<>();
        while (currentPos < bytes.length) {
            length = bytes[currentPos++] & 0xFF;
            if (length == 0){
                break;
            }
            fieldType = bytes[currentPos++] & 0xFF;
            switch (fieldType) {
                case DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE:
                    parseServiceUuid(bytes, currentPos, length, UUID_BYTES_16_BIT, serviceUuids);
                case DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE:
                    parseServiceUuid(bytes, currentPos, length, UUID_BYTES_32_BIT, serviceUuids);
                case DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE:
                    parseServiceUuid(bytes, currentPos, length, UUID_BYTES_128_BIT, serviceUuids);
            }
            currentPos += length - 1;
        }
        return serviceUuids;
    }

    private void parseServiceUuid(byte[] scanRecord, int currentPos, int dataLength,
                                  int uuidLength, List<ParcelUuid> serviceUuids) {
        while (dataLength > 0) {
            byte[] uuidBytes = new byte[uuidLength];
            System.arraycopy(scanRecord, currentPos, uuidBytes, 0, uuidLength);
            serviceUuids.add(parseUuidFrom(uuidBytes));
            dataLength -= uuidLength;
            currentPos += uuidLength;
        }
    }

    private ParcelUuid parseUuidFrom(byte[] uuidBytes) {
        if (uuidBytes == null) {
            throw new IllegalArgumentException("uuidBytes cannot be null");
        }
        int length = uuidBytes.length;
        if (length != UUID_BYTES_16_BIT && length != UUID_BYTES_32_BIT &&
                length != UUID_BYTES_128_BIT) {
            throw new IllegalArgumentException("uuidBytes length invalid - " + length);
        }
        // Construct a 128 bit UUID.
        if (length == UUID_BYTES_128_BIT) {
            ByteBuffer buf = ByteBuffer.wrap(uuidBytes).order(ByteOrder.LITTLE_ENDIAN);
            long msb = buf.getLong(8);
            long lsb = buf.getLong(0);
            return new ParcelUuid(new UUID(msb, lsb));
        }
        // For 16 bit and 32 bit UUID we need to convert them to 128 bit value.
        // 128_bit_value = uuid * 2^96 + BASE_UUID
        long shortUuid;
        if (length == UUID_BYTES_16_BIT) {
            shortUuid = uuidBytes[0] & 0xFF;
            shortUuid += (uuidBytes[1] & 0xFF) << 8;
        } else {
            shortUuid = uuidBytes[0] & 0xFF;
            shortUuid += (uuidBytes[1] & 0xFF) << 8;
            shortUuid += (uuidBytes[2] & 0xFF) << 16;
            shortUuid += (uuidBytes[3] & 0xFF) << 24;
        }
        long msb = BASE_UUID.getUuid().getMostSignificantBits() + (shortUuid << 32);
        long lsb = BASE_UUID.getUuid().getLeastSignificantBits();
        return new ParcelUuid(new UUID(msb, lsb));
    }
}
