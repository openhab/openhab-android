package org.openhab.habdroid.util.Bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.model.OpenHABBeacons;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.BeaconHandler;
import org.openhab.habdroid.util.Constants;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;

public abstract class AbstractLocateBeacons extends AsyncTask<BluetoothAdapter, List<OpenHABBeacons>, List<OpenHABBeacons>> implements Parcelable {

    private static final String TAG = AbstractLocateBeacons.class.getSimpleName();

    protected BeaconHandler beaconHandler;

    protected List<OpenHABBeacons> beaconList;

    protected boolean locate;

    protected List<OpenHABBeacons> founded;

    protected BluetoothAdapter ba;

    protected boolean restartScan;

    protected int noBeaconsSinceRestart = 0;

    public AbstractLocateBeacons(){
        beaconHandler = BeaconHandler.getInstance(); //TODO: Null handling
        beaconList = new ArrayList<>();
        this.locate = true;
        this.founded = new ArrayList<>();
    }

    public AbstractLocateBeacons(Parcel in){
        in.readValue(ClassLoader.getSystemClassLoader());
    }

    protected List<OpenHABBeacons> doInBackground(BluetoothAdapter... ba){
        while (locate){
            try {
                Thread.sleep(2000);
                listHandling();
                if(founded.size()==0)noBeaconsSinceRestart++;
                if(restartScan || noBeaconsSinceRestart>=5)restartScan();
                publishProgress(beaconList);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return beaconList;
    }

    protected void onProgressUpdate(List<OpenHABBeacons>... beacons){
        beaconHandler.refreshNearRooms(beacons[0]);
        Log.d(TAG, "onProgressUpdate: ");
    }

    protected void onPostExecute(List<OpenHABBeacons> beacons){
        if(!OpenHABMainActivity.bluetoothActivated)ba.disable();
        beaconHandler.clearNearRooms();
    }

    public void stop(){
        this.locate = false;
    }

    protected void listHandling(){
        List<OpenHABBeacons> toRemove = new ArrayList<>();
        for(OpenHABBeacons bi : beaconList){
            if(founded.contains(bi)){
                toRemove.add(bi);
            }
            else{
                if(bi.getNotSeen() <= Constants.BEACON_TIMES_OF_NOT_SEEN){
                    bi.incrementNotSeen();
                }
                else{
                    toRemove.add(bi);
                }
            }
        }
        beaconList.removeAll(toRemove);
        beaconList.addAll(founded);
        founded.clear();
    }

    protected static String bytesToHex(byte[] bytes) {
        char[] hexArray = Constants.HEX_ARRAY;
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    protected static String getUUID(String message, int leading){
        message = message.substring(leading, leading+32);
        return formatUUID(message);
    }

    protected static int getMajorID(String message, int leading){
        return Integer.parseInt(message.substring(leading+32, leading+32+4), 16);
    }

    protected static int getMinorID(String message, int leading){
        return Integer.parseInt(message.substring(leading+32+4, leading+32+8), 16);
    }

    protected static String formatUUID(String uuid){
        uuid =    uuid.substring(0,8) + "-"
                + uuid.substring(8,12) + "-"
                + uuid.substring(12,16) + "-"
                + uuid.substring(16,20) + "-"
                + uuid.substring(20,uuid.length());
        return uuid;
    }

    protected static double howFar(int txPower, int rssi) {
        if (rssi == 0) {
            return -1.0; // if we cannot determine accuracy, return -1.
        }
        double accuracy;
        double ratio = rssi*1.0/txPower;
        if (ratio < 1.0) {
            accuracy = Math.pow(ratio,10);
        }
        else {
            accuracy =  (0.89976)*Math.pow(ratio,7.7095) + 0.111;
        }
        return roundTwoDecimals(accuracy);
    }

    protected static double roundTwoDecimals(Double d){
        return (((double)(Math.round(d * 100)))/100);
    }

    public void restartScan(){
        restartScan = false;
        noBeaconsSinceRestart = 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int i) {
        out.writeBooleanArray(new boolean[]{OpenHABMainActivity.isBLEDevice()>0});
    }


    public static final Creator CREATOR = new Creator() {
        public AbstractLocateBeacons createFromParcel(Parcel in) {
            boolean[] b = new boolean[1];
            in.readBooleanArray(b);
            if (b[0])
                return new LocateBeaconsTaskNew();
            else
                return new LocateBeaconsTaskOld();
        }

        public AbstractLocateBeacons[] newArray(int size) {
            return new AbstractLocateBeacons[size];
        }
    };

}
