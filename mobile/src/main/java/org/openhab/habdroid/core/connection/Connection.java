package org.openhab.habdroid.core.connection;

import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

public interface Connection {
    MyAsyncHttpClient getAsyncHttpClient();
    MySyncHttpClient getSyncHttpClient();

    void setUsername(String username);
    void setPassword(String password);
    String getUsername();
    String getPassword();

    void setOpenHABUrl(String openHABUrl);
    String getOpenHABUrl();

    void setConnectionType(int connectionType);
    int getConnectionType();

    boolean isReachable();
}
