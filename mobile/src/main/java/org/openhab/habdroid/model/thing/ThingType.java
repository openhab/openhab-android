/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model.thing;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class ThingType implements Parcelable {
    private ArrayList<ThingTypeChannel> channels;
    private ArrayList<ThingTypeChannelGroup> channelGroups;
    private ArrayList<ThingTypeConfigParameter> configParameters;
//    private ArrayList<ThingTypeProperty> properties;
    private String description;
    private String label;
    private String UID;
    private Boolean bridge;

    public ThingType(Parcel in) {
        description = in.readString();
        label = in.readString();
        UID = in.readString();
        bridge = in.readByte() != 0;
    }

    public ArrayList<ThingTypeChannel> getChannels() {
        return channels;
    }

    public void setChannles(ArrayList<ThingTypeChannel> channels) {
        this.channels = channels;
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(description);
        dest.writeString(label);
        dest.writeString(UID);
        dest.writeByte((byte)(bridge ? 1 : 0));
    }

    public static final Parcelable.Creator<ThingType> CREATOR
            = new Parcelable.Creator<ThingType>() {
        public ThingType createFromParcel(Parcel in) {
            return new ThingType(in);
        }

        public ThingType[] newArray(int size) {
            return new ThingType[size];
        }
    };
}
