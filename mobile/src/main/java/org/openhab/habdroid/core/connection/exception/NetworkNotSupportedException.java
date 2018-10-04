package org.openhab.habdroid.core.connection.exception;

import android.net.NetworkInfo;

public class NetworkNotSupportedException extends ConnectionException {
    private final NetworkInfo mNetworkInfo;

    public NetworkNotSupportedException(NetworkInfo info) {
        super();
        mNetworkInfo = info;
    }

    public NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }
}
