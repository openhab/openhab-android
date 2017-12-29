package org.openhab.habdroid.core.connection;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

public interface Connection {
    /**
     * Returns any openHAB connection that is most likely to work on the current network. The
     * connections available will be tried in the following order:
     *  - TYPE_LOCAL
     *  - TYPE_REMOTE
     *  - TYPE_CLOUD
     *  - DISCOVERY
     *
     * The connection type TYPE_ANY guarantees to return a Connection object, however, it may throw an
     * exception of type ConnectionException
     */
    int TYPE_ANY = 0;
    /**
     * Returns a connection to a locally hosted openHAB server, which is most likely the instance
     * configured in the settings. May or may not work on the network the device is currently
     * connected to.
     *
     * This method can return null, if no Connection for a local openHAB server could be
     * constructed. In this case, no exception with more information will be thrown.
     */
    int TYPE_LOCAL = 1;
    /**
     * Returns a connection to an openHAB instance, which may or may not be available from the
     * public internet. This is most likely the instance configured in the "remote" settings of
     * the device. The connection may or may not be available in the network the device is
     * currently connected to.
     *
     * This method can return null, if no Connection for a remote openHAB server could be
     * constructed. In this case, no exception with more information will be thrown.
     */
    int TYPE_REMOTE = 2;
    /**
     * Returns a connection that is guaranteed to provide functionality implemented in the
     * openHAB cloud product (like notifications).
     *
     * This method can return null, if no Connection for an openHAB cloud server could be
     * constructed. In this case, no exception with more information will be thrown.
     */
    int TYPE_CLOUD = 3;

    /**
     * @return A fully setup asynchronous http client for requesting resources from the
     * server represented in this Connection object.
     */
    MyAsyncHttpClient getAsyncHttpClient();

    /**
     * @return A fully setup synchronous http client for requesting resources from the server
     * represented in this Connection object.
     */
    MySyncHttpClient getSyncHttpClient();

    void setUsername(String username);
    void setPassword(String password);

    /**
     * @return The username used for this connection.
     */
    @Nullable String getUsername();
    @Nullable String getPassword();

    void setOpenHABUrl(String openHABUrl);
    @NonNull String getOpenHABUrl();

    void setConnectionType(int connectionType);

    /**
     * @return The type of this connection represented by one of the TYPE_* constants of the
     * Connection interface.
     */
    int getConnectionType();

    boolean isReachable();
}
