package org.openhab.habdroid.util.Bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Parcel;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABBeacons;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class LocateBeaconsTaskOld extends AbstractLocateBeacons {

    private static final String TAG = LocateBeaconsTaskOld.class.getSimpleName();

    private BluetoothAdapter.LeScanCallback scanCallback;

    /*public LocateBeaconsTaskOld(Parcel in){
        super(in);
    }*/

    public LocateBeaconsTaskOld(){
        super();

        scanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi,
                                 byte[] scanRecord) {
                if(Arrays.equals(Constants.I_BEACON_PREFIX, Arrays.copyOfRange(scanRecord, 0, 9))){
                    String beaconMessage = bytesToHex(scanRecord);
                    String uUID = getUUID(beaconMessage, Constants.I_BEACON_PREFIX.length*2);
                    int majorID = getMajorID(beaconMessage, Constants.I_BEACON_PREFIX.length*2);
                    int minorId = getMinorID(beaconMessage, Constants.I_BEACON_PREFIX.length*2);
                    double away = howFar((new Byte(scanRecord[29])).intValue(), rssi);
                    OpenHABBeacons newBeacon = new OpenHABBeacons(device.getName(), device.getAddress(), uUID + ":" + majorID + ":" + minorId);
                    if(founded.contains(newBeacon)){
                        founded.get(founded.indexOf(newBeacon)).setAway(away);
                    }
                    else {
                        newBeacon.setAway(away);
                        founded.add(newBeacon);
                    }
                    Log.d(TAG, "ScanResult: " + device.getAddress());
                    restartScan = true;
                }
            }
        };
    }

    @Override
    protected List<OpenHABBeacons> doInBackground(BluetoothAdapter... ba) {
        this.ba = ba[0];
        this.ba.startLeScan(scanCallback);
        return super.doInBackground(ba);
    }

    @Override
    protected void onPostExecute(List<OpenHABBeacons> beacons) {
        ba.stopLeScan(scanCallback);
        super.onPostExecute(beacons);
    }

    public void restartScan(){
        super.restartScan();
        try {
            ba.stopLeScan(scanCallback);
            Thread.sleep(10);
            ba.startLeScan(scanCallback);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        catch (NullPointerException e){
            e.printStackTrace();
            Log.e(TAG, "restartScan: NullPointer fliegt!!!!!", e);
        }
    }
}