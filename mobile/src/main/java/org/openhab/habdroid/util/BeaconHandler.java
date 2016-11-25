package org.openhab.habdroid.util;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.model.OpenHABBeacons;
import org.openhab.habdroid.ui.OpenHABMainActivity;

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

    public static BeaconHandler getInstance() throws NullPointerException{
        if(ourInstance == null){
            throw new NullPointerException("Need a Context to instantiat the BeaconHandler at first");
        }
        return ourInstance;
    }

    private BeaconHandler(Context c) {
        //addTestBeacons(c);

        noBeacon = new OpenHABBeacons("No Beacon Seen", "Serching for more", "Dummy", -1.0);
        nearRooms = new ArrayList<>();
        knownBeacons = readBeacons(c);
        oldNearest = null;
        haveToSwitch = false;
    }

    private List<OpenHABBeacons> nearRooms;
    private List<OpenHABBeacons> knownBeacons;
    private OpenHABBeacons oldNearest;
    private boolean haveToSwitch;
    private OpenHABBeacons noBeacon;


    private boolean writeBeacons(Context context){
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

    public boolean addBeacon(OpenHABBeacons newBeacon, Context context){
        if(knownBeacons.contains(newBeacon)){
            knownBeacons.remove(knownBeacons.indexOf(newBeacon));
        }
        knownBeacons.add(newBeacon);
        if(writeBeacons(context)){
            return true;
        }
        else{
            knownBeacons.remove(knownBeacons.indexOf(newBeacon));
            return false;
        }
    }

    public OpenHABBeacons getNearest(){
        if (nearRooms.isEmpty())
            return null;
        else
            return nearRooms.get(0);
    }

    private void addBeaconInfos(){
        if(knownBeacons.contains(nearRooms.get(0))){
            nearRooms.get(0).addHABInfos(knownBeacons.get(knownBeacons.indexOf(nearRooms.get(0))));
        }
        /*else {
            oldNearest = null;
        }*/
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
            oldNearest = null;
        }
        setChanged();
        notifyObservers();
    }

    private void initSwitch(){
        if(nearRooms != null && !nearRooms.isEmpty()) {
            OpenHABBeacons newNearest = nearRooms.get(0);
            if(newNearest != null && !newNearest.equals(oldNearest)) {
                Log.d(TAG, "initSwitch: ");
                oldNearest = newNearest;
                addBeaconInfos();
                haveToSwitch = true;
            }
        }
        else {
            oldNearest = null;
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
        oldNearest = null;
    }

    public void removeKnownBeaocn(OpenHABBeacons toRemove, Context context){
        if(knownBeacons.contains(toRemove)){
            knownBeacons.remove(knownBeacons.indexOf(toRemove));
            writeBeacons(context);
        }
    }

    public void reloadBeacon(Context context){
        knownBeacons = readBeacons(context);
    }

    public List<OpenHABBeacons> getShownBeacons(){
        List<OpenHABBeacons> shownBeacons = new ArrayList<>();
        if(OpenHABMainActivity.isBLEDevice()<0 || !OpenHABMainActivity.bluetoothActivated || !OpenHABMainActivity.isLocate()){
            shownBeacons.addAll(knownBeacons);
            noBeacon.resetNotSeen();
        }
        else{
            if(nearRooms.isEmpty()){
                noBeacon.incrementNotSeen();
                shownBeacons.add(noBeacon);
            }
            else{
                noBeacon.resetNotSeen();
                for(OpenHABBeacons nearBeacon : nearRooms) {
                    if (knownBeacons.contains(nearBeacon)){
                        shownBeacons.add(nearBeacon.addHABInfos(knownBeacons.get(knownBeacons.indexOf(nearBeacon))));
                    }
                    else{
                        nearBeacon.setName("<UNKNOWN>");
                        shownBeacons.add(nearBeacon);
                    }
                }
            }
        }
        Log.d(TAG, "handleShownBeacons: " + shownBeacons.size());
        return shownBeacons;
    }

    public OpenHABBeacons getNoBeacon(){
        return noBeacon;
    }

    /*public void addTestBeacons(Context context){
        try {
            String write = (new OpenHABBeacons("Beacon1", "ab:cd:ef:fe:dc:ba", "123456789")).toJSONString() + (new OpenHABBeacons("Beacon2", "12:34:56:78:90:ab", "987654321")).toJSONString();
            FileOutputStream fos = context.openFileOutput("hab_beacons", Context.MODE_PRIVATE);
            fos.write(write.getBytes());
            fos.close();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/
}
