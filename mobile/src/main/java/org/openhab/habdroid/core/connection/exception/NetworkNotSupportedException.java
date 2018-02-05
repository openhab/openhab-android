package org.openhab.habdroid.core.connection.exception;

import android.net.NetworkInfo;

public class NetworkNotSupportedException extends ConnectionException {
    private final NetworkInfo networkInfo;

    public NetworkNotSupportedException(NetworkInfo info) {
        super();
        networkInfo = info;
    }

    public NetworkInfo getNetworkInfo() {
        return networkInfo;
    }
}
