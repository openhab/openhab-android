package org.openhab.habdroid.core.connection.exception;

import android.net.NetworkInfo;

public class NetworkNotSupportedException extends ConnectionException {
    private final NetworkInfo networkInfo;

    public NetworkNotSupportedException(String message, NetworkInfo info) {
        super(message);
        networkInfo = info;
    }

    public NetworkInfo getNetworkInfo() {
        return networkInfo;
    }
}
