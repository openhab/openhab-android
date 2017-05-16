package org.openhab.habdroid.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OpenHABBeacons implements Comparable<OpenHABBeacons>, Parcelable {

    private String name;

    private String address;

    //iBeacon: UUID:MajorID:MinorID
    //Eddystone: URL
    private String beaconMessage;

    private String sitemap;

    private String group;

    private int notSeen;

    private double away;

    public OpenHABBeacons(JSONObject jsonObject) throws JSONException{
        if(jsonObject.has("name"))
            this.name = jsonObject.getString("name");
        if(jsonObject.has("address"))
            this.address = jsonObject.getString("address");
        if(jsonObject.has("message"))
            this.beaconMessage = jsonObject.getString("message");
        if(jsonObject.has("sitemap"))
            this.sitemap = jsonObject.getString("sitemap");
        if(jsonObject.has("group"))
            this.group = jsonObject.getString("group");
        this.away = -1;
        this.notSeen = -1;
    }

    public OpenHABBeacons(String name, String address, String beaconMessage){
        this.name = name;
        this.address = address;
        this.beaconMessage = beaconMessage;
        this.away = -1;
        this.notSeen = 0;
        this.sitemap = null;
        this.group = null;
    }

    public OpenHABBeacons(String name, String address, String beaconMessage, double away){
        this.name = name;
        this.address = address;
        this.beaconMessage = beaconMessage;
        this.away = away;
        this.notSeen = 0;
        this.sitemap = null;
        this.group = null;
    }

    public OpenHABBeacons(){
        this.name = null;
        this.address = null;
        this.beaconMessage = null;
        this.away = -1;
        this.notSeen = 0;
        this.sitemap = null;
        this.group = null;
    }

    protected OpenHABBeacons(Parcel in) {
        name = in.readString();
        address = in.readString();
        beaconMessage = in.readString();
        sitemap = in.readString();
        group = in.readString();
        notSeen = in.readInt();
        away = in.readDouble();
    }

    public static final Creator<OpenHABBeacons> CREATOR = new Creator<OpenHABBeacons>() {
        @Override
        public OpenHABBeacons createFromParcel(Parcel in) {
            return new OpenHABBeacons(in);
        }

        @Override
        public OpenHABBeacons[] newArray(int size) {
            return new OpenHABBeacons[size];
        }
    };

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBeaconMessage() {
        return beaconMessage;
    }

    public void setBeaconMessage(String beaconMessage) {
        this.beaconMessage = beaconMessage;
    }

    public String getSitemap() {
        return sitemap;
    }

    public void setSitemap(String sitemap) {
        this.sitemap = sitemap;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public double getAway() {
        return away;
    }

    public void setAway(double away) {
        this.away = away;
    }

    public void resetNotSeen(){
        this.notSeen = -1;
    }

    public void incrementNotSeen(){
        this.notSeen++;
    }

    public int getNotSeen(){
        return this.notSeen;
    }

    @Override
    public int compareTo(OpenHABBeacons bi) {
        Double extern = bi.getAway();
        Double intern = this.getAway();
        return ((extern.compareTo(intern))*(-1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenHABBeacons that = (OpenHABBeacons) o;
        //away is completly variable it must not be compared
        //notSeen ist not relevant for equals, because it only count how ofen this Beacon was not seen
        if (address != null ? !address.equals(that.address) : that.address != null) return false;
        if (beaconMessage != null ? !beaconMessage.equals(that.beaconMessage) : that.beaconMessage != null) return false;

        return true;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = name != null ? name.hashCode() : 0;
        result = 31 * result + (address != null ? address.hashCode() : 0);
        result = 31 * result + (beaconMessage != null ? beaconMessage.hashCode() : 0);
        temp = Double.doubleToLongBits(away);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public String getNameForView(){
        return name + "(" + address + ")";
    }

    public String getRoomForView(){
        if(away == -1.0 && notSeen != -1)
            return "";
        else if(sitemap == null && group == null){
            return null;
        }
        else
            return "Sitemap: " + sitemap + ", Group: " + group + "\n";
    }

    public String getInfoForView(){
        if(notSeen == -1 && away == -1.0)
            return "";
        else if(away == -1.0)
            return "No Beacons seen for " + notSeen*2 + "sec.";
        else
            return "Beacon is " + away + "m away and was not seen for " + notSeen*2 + "sec.";
    }

    public boolean hasSeen(){
        if(notSeen == -1)
            return false;
        else
            return true;
    }

    public OpenHABBeacons addHABInfos(OpenHABBeacons beacon){
        this.sitemap = beacon.sitemap;
        this.group = beacon.group;
        this.name = beacon.name;
        return this;
    }

    public List<OpenHABBeacons> asList(){
        List<OpenHABBeacons> l = new ArrayList<>();
        l.add(this);
        return l;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(name);
        out.writeString(address);
        out.writeString(beaconMessage);
        out.writeString(sitemap);
        out.writeString(group);
        out.writeInt(notSeen);
        out.writeDouble(away);
    }

    public String toJSONString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("address", address);
        json.put("sitemap", sitemap);
        json.put("group", group);
        json.put("message", beaconMessage);
        return json.toString();
    }

    @Override
    public String toString(){
        return "This is the Beacon " + name + " with the MAC-Address: " + address + ". It sends out the Message: " + beaconMessage;
    }

    /**
     * isCorrect checks if all necessary attributes are set before saving a Beacon
     * @return true if Beacon is correct
     */
    public boolean isCorrect() {
        if(name == null || "".equals(name))
            return false;
        if(address == null || "".equals(address))
            return false;
        if(beaconMessage == null || "".equals(beaconMessage))
            return false;
        if(sitemap == null || "".equals(sitemap))
            return false;
        if(group == null || "".equals(group))
            return false;
        return true;
    }
}
