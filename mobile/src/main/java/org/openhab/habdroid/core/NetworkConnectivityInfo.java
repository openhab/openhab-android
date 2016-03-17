/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by belovictor on 21/10/14.
 */
public class NetworkConnectivityInfo implements Parcelable {
    private String mSsid = ""; // Wireless network SSID, if available
    private int mNetworkType = -1;

    public NetworkConnectivityInfo() {

    }

    private NetworkConnectivityInfo(Parcel source) {
        this.setNetworkType(source.readInt());
        this.setSsid(source.readString());
    }

    public static final Parcelable.Creator<NetworkConnectivityInfo> CREATOR = new Parcelable.Creator<NetworkConnectivityInfo>() {
        public NetworkConnectivityInfo createFromParcel(Parcel in) {
            return new NetworkConnectivityInfo(in);
        }

        public NetworkConnectivityInfo[] newArray(int size) {
            return new NetworkConnectivityInfo[size];
        }
    };

    public static NetworkConnectivityInfo currentNetworkConnectivityInfo(Context ctx) {
        ConnectivityManager connectivityManager = (ConnectivityManager)ctx.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkConnectivityInfo connectivityInfo = new NetworkConnectivityInfo();
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
            connectivityInfo.setNetworkType(activeNetworkInfo.getType());
            if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                // get ssid here
                WifiManager wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiConnectionInfo = wifiManager.getConnectionInfo();
                if (wifiConnectionInfo != null) {
                    connectivityInfo.setSsid(wifiConnectionInfo.getSSID());
                }
            }
        }
        return connectivityInfo;
    }

    public boolean equals(NetworkConnectivityInfo connectivityInfo) {
        // If network is WiFi
        if (connectivityInfo.getNetworkType() == ConnectivityManager.TYPE_WIFI) {
            // And ssids are the same
            if (connectivityInfo.getSsid().equals(this.getSsid())) {
                // Networks are equal
                return true;
            }
        // If not wifi
        } else {
            // If network type is the same
            if (connectivityInfo.getNetworkType() == this.getNetworkType()) {
                // Networks are equal
                return true;
            }
        }
        // Networks are not equal
        return false;
    }

    public String getSsid() {
        return mSsid;
    }

    public void setSsid(String mSsid) {
        this.mSsid = mSsid;
    }

    public int getNetworkType() {
        return mNetworkType;
    }

    public void setNetworkType(int mNetworkType) {
        this.mNetworkType = mNetworkType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.getNetworkType());
        dest.writeString(this.getSsid());
    }
}
