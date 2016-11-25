package org.openhab.habdroid.util;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.model.OpenHABBeacons;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Observable;

public class BeaconHandler extends Observable{

    private static final String TAG = BeaconHandler.class.getSimpleName();

    //Singelton
    private static BeaconHandler ourInstance;

    public static BeaconHandler getInstance(Context calling) {
        if(ourInstance == null){
            ourInstance = new BeaconHandler(calling);
        }
        return ourInstance;
    }

    public static BeaconHandler getInstance(){
        if(ourInstance == null){
            return null;
        }
        return ourInstance;
    }

    private BeaconHandler(Context c) {
        nearRooms = new ArrayList<>();
        knownBeacons = readBeacons(c);
        nearest = null;
        haveToSwitch = false;
    }

    private List<OpenHABBeacons> nearRooms;
    private List<OpenHABBeacons> knownBeacons;
    private OpenHABBeacons nearest;
    private boolean haveToSwitch;


    public boolean writeBeacons(Context context){
        try {
            String beaconString = "";
            for(OpenHABBeacons beacon : knownBeacons){
                beaconString += beacon.toJSONString();
            }
            FileOutputStream fos = context.openFileOutput("hab_beacons", Context.MODE_PRIVATE);
            fos.write(beaconString.getBytes());
            fos.close();
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private List<OpenHABBeacons> readBeacons(Context context){
        List<OpenHABBeacons> beacons = new ArrayList<>();
        try {
            FileInputStream fis = context.openFileInput("hab_beacons");
            int i;
            String beacon = "";
            while((i = fis.read())>=0){
                beacon += (char)i;
                if (beacon.endsWith("}")){
                    beacons.add(new OpenHABBeacons(new JSONObject(beacon)));
                    beacon = "";
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            beacons.clear();
        } catch (IOException e) {
            e.printStackTrace();
            beacons.clear();
        } catch (JSONException e) {
            e.printStackTrace();
            beacons.clear();
        }
        Log.d(TAG, "readBeacons: " + beacons.size());
        return beacons;
    }

    public void addNewBeacon(OpenHABBeacons newBeacon){
        if(knownBeacons.contains(newBeacon)){
            knownBeacons.remove(knownBeacons.indexOf(newBeacon));
        }
        knownBeacons.add(newBeacon);
    }

    public OpenHABBeacons getNearest(){
        return nearest;
    }

    private void addBeaconInfos(){
        if(knownBeacons.contains(nearest)){
            nearest.addHABInfos(knownBeacons.get(knownBeacons.indexOf(nearest)));
        }
        else {
            nearest = null;
        }
    }

    public List<OpenHABBeacons> getKnownBeacons(){
        return knownBeacons;
    }

    public void refreshNearRooms(List<OpenHABBeacons> nearRooms){
        this.nearRooms.clear();
        if (nearRooms != null && !nearRooms.isEmpty()){
            Collections.sort(nearRooms);
            this.nearRooms.addAll(nearRooms);
            initSwitch();
        }
        else {
            nearest = null;
        }
        setChanged();
        notifyObservers();
    }

    private void initSwitch(){
        if(nearRooms != null && !nearRooms.isEmpty()) {
            OpenHABBeacons newNearest = nearRooms.get(0);
            if(newNearest != null && !newNearest.equals(nearest)) {
                Log.d(TAG, "initSwitch: ");
                nearest = newNearest;
                addBeaconInfos();
                haveToSwitch = true;
            }
        }
        else {
            nearest = null;
        }
    }

    public List<OpenHABBeacons> getNearRooms() {
        return nearRooms;
    }

    public void hasSwitched(){
        haveToSwitch = false;
    }

    public boolean doSwitch(){
        return haveToSwitch;
    }

    public void clearNearRooms(){
        nearRooms.clear();
        nearest = null;
    }

    public void removeKnownBeaocn(OpenHABBeacons toRemove, Context context){
        if(knownBeacons.contains(toRemove)){
            knownBeacons.remove(knownBeacons.indexOf(toRemove));
            writeBeacons(context);
        }
    }
}
