package org.openhab.habdroid.core.connection;

import android.support.annotation.Nullable;

import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.SyncHttpClient;

public interface Connection {
    /**
     * Represents a connection to a locally hosted openHAB server, which is most likely the instance
     * configured in the settings. May or may not work on the network the device is currently
     * connected to.
     */
    int TYPE_LOCAL = 0;
    /**
     * Representsa connection to an openHAB instance, which may or may not be available from the
     * public internet. This is most likely the instance configured in the "remote" settings of
     * the device. The connection may or may not be available in the network the device is
     * currently connected to.
     */
    int TYPE_REMOTE = 1;
    /**
     * Represents a connection that is guaranteed to provide functionality implemented in the
     * openHAB cloud product (like notifications).
     */
    int TYPE_CLOUD = 2;

    /**
     * @return A fully setup asynchronous http client for requesting resources from the
     * server represented in this Connection object.
     */
    AsyncHttpClient getAsyncHttpClient();

    /**
     * @return A fully setup synchronous http client for requesting resources from the server
     * represented in this Connection object.
     */
    SyncHttpClient getSyncHttpClient();

    /**
     * @return The username used for this connection.
     */
    @Nullable String getUsername();
    @Nullable String getPassword();

    /**
     * @return The type of this connection represented by one of the TYPE_* constants of the
     * Connection interface.
     */
    int getConnectionType();

    /**
     * @return Whether the this connection is currently reachable.
     */
    boolean checkReachabilityInBackground();
}
