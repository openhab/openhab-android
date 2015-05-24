package org.openhab.habdroid.model.thing;

import java.util.ArrayList;
import com.google.gson.annotations.SerializedName;

/**
 * Created by belovictor on 23/05/15.
 */
public class ThingType {
    private ArrayList<ThingTypeChannel> channles;
    private ArrayList<ThingTypeChannelGroup> channelGroups;
    private ArrayList<ThingTypeConfigParameter> configParameters;
//    private ArrayList<ThingTypeProperty> properties;
    private String description;
    private String label;
    private String UID;
    private Boolean bridge;

    public ArrayList<ThingTypeChannel> getChannles() {
        return channles;
    }

    public void setChannles(ArrayList<ThingTypeChannel> channles) {
        this.channles = channles;
    }

    public ArrayList<ThingTypeChannelGroup> getChannelGroups() {
        return channelGroups;
    }

    public void setChannelGroups(ArrayList<ThingTypeChannelGroup> channelGroups) {
        this.channelGroups = channelGroups;
    }

    public ArrayList<ThingTypeConfigParameter> getConfigParameters() {
        return configParameters;
    }

    public void setConfigParameters(ArrayList<ThingTypeConfigParameter> configParameters) {
        this.configParameters = configParameters;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUID() {
        return UID;
    }

    public void setUID(String UID) {
        this.UID = UID;
    }

    public Boolean getBridge() {
        return bridge;
    }

    public void setBridge(Boolean bridge) {
        this.bridge = bridge;
    }
}
