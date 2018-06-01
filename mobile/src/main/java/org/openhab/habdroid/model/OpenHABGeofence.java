package org.openhab.habdroid.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.util.Util;

public class OpenHABGeofence {

    private String mLabel;
    private String mName;
    double mLatitude;
    double mLongitude;
    float mRadius;//in meters

    private OpenHABItem mSwitchItem;


    public JSONObject toJSON() throws JSONException {
        JSONObject ret = new JSONObject();
        ret.put("label",mLabel);
        ret.put("name",mName);
        ret.put("latitude",mLatitude);
        ret.put("longitude",mLongitude);
        ret.put("radius",mRadius);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return this.toJSON().toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return super.toString();
    }

    public static OpenHABGeofence fromJSON(JSONObject j) throws JSONException {
        String label = j.getString("label");
        String name = j.getString("name");
        double lat= j.getDouble("latitude");
        double lon = j.getDouble("longitude");
        float radius= (float) j.getDouble("radius");
        return new OpenHABGeofence(lat,lon,radius,label,name);
    }

    public OpenHABGeofence(double latitude, double longitude, float radius,String name,String label) {
        set(latitude,longitude,radius);
        this.mName = name;
        this.mLabel = label;

        //TODO create the openHABItem maybe ?
    }

    private void set(double latitude, double longitude, float radius) {
        this.mLatitude = latitude;
        this.mLongitude = longitude;
        this.mRadius = radius;
    }

    public OpenHABItem getOpenHABItem() {
        return mSwitchItem;
    }

    public String getCoordinatesString() {
        return Util.coordinatesStringFromValues(mLatitude,mLongitude);
    }

    public String getLabel() {
        return mLabel;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public float getRadius() {
        return mRadius;
    }

    public String getName() {
        return mName;
    }

    /**
     * To enable easy removal form lists.
     * As names should be unique this is fine.
     * @param obj to compare to
     * @return true if the name is the same
     */
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof OpenHABGeofence)
            return (((OpenHABGeofence) obj).mName.equals(mName));
        return super.equals(obj);
    }
}
