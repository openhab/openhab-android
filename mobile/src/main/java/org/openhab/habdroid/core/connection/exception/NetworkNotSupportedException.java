package org.openhab.habdroid.core.connection.exception;

import android.net.NetworkInfo;

public class NetworkNotSupportedException extends ConnectionException {
    private NetworkInfo networkInfo;

    public NetworkNotSupportedException(String message) {
        super(message);
    }

    public void setNetworkInfo(NetworkInfo networkInfo) {
        this.networkInfo = networkInfo;
    }

    public NetworkInfo getNetworkInfo() {
        return networkInfo;
    }
}
