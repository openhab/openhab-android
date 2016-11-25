package org.openhab.habdroid.util.bluetooth;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABBeacons;
import org.openhab.habdroid.util.Constants;

import java.util.Arrays;
import java.util.List;

@SuppressLint("ParcelCreator")
@TargetApi(21)
public class LocateBeaconsTaskNew extends AbstractLocateBeacons {

    private static final String TAG = LocateBeaconsTaskNew.class.getSimpleName();

    private ScanCallback scanCallback;

    private BluetoothLeScanner leScanner;

    /*public LocateBeaconsTaskNew(Parcel in){
        super(in);
    }*/

    public LocateBeaconsTaskNew(){
        super();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                int rssi = result.getRssi();
                byte[] scanRecord = result.getScanRecord().getBytes();
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
                    else{
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
        leScanner = this.ba.getBluetoothLeScanner();
        leScanner.startScan(scanCallback);
        return super.doInBackground(ba);
    }

    @Override
    protected void onPostExecute(List<OpenHABBeacons> beacons) {
        leScanner.stopScan(scanCallback);
        super.onPostExecute(beacons);
    }

    @Override
    public void restartScan(){
        super.restartScan();
        try {
            leScanner.stopScan(scanCallback);
            Thread.sleep(10);
            leScanner.startScan(scanCallback);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}