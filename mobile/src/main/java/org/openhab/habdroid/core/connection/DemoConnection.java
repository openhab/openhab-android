package org.openhab.habdroid.core.connection;

import okhttp3.OkHttpClient;

public class DemoConnection extends AbstractConnection {
    DemoConnection(OkHttpClient httpClient) {
        super(httpClient, Connection.TYPE_REMOTE, "https://demo.openhab.org:8443/", null, null);
    }
}
