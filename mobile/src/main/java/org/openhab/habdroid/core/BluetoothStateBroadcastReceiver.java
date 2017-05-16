package org.openhab.habdroid.core;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.openhab.habdroid.ui.OpenHABMainActivity;

public class BluetoothStateBroadcastReceiver extends BroadcastReceiver{
    private static final String TAG = BluetoothStateBroadcastReceiver.class.getSimpleName();
    OpenHABMainActivity main;

    public BluetoothStateBroadcastReceiver(OpenHABMainActivity main){
        this.main = main;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            Log.d(TAG, "onReceive: " + state);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    main.setBluetoothActivated(false);
                    main.checkBluetooth();
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    if(main.isLocate()){
                        main.setBluetoothActivated(false);
                        main.bluetoothIsTurningOff();
                    }
                    break;
                case BluetoothAdapter.STATE_ON:
                    main.setBluetoothActivated(true);
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    //Do nothing
                    break;
            }
        }
    }
}
