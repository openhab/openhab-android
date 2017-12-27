package org.openhab.habdroid.core.connection;

public final class Connections {
    /**
     * Returns any openHAB connection that is most likely to work on the current network. The
     * connections available will be tried in the following order:
     *  - LOGLEVEL_LOCAL
     *  - LOGLEVEL_REMOTE
     *  - CLOUD
     *  - DISCOVERY
     *
     * The connection type ANY guarantees to return a Connection object, however, it may throw an
     * exception of type ConnectionException
     */
    public static final int ANY = 0;
    /**
     * Returns a connection to a locally hosted openHAB server, which is most likely the instance
     * configured in the settings. May or may not work on the network the device is currently
     * connected to.
     *
     * This method can return null, if no Connection for a local openHAB server could be
     * constructed. In this case, no exception with more information will be thrown.
     */
    public static final int LOCAL = 1;
    /**
     * Returns a connection to an openHAB instance, which may or may not be available from the
     * public internet. This is most likely the instance configured in the "remote" settings of
     * the device. The connection may or may not be available in the network the device is
     * currently connected to.
     *
     * This method can return null, if no Connection for a remote openHAB server could be
     * constructed. In this case, no exception with more information will be thrown.
     */
    public static final int REMOTE = 2;
    /**
     * Returns a connection that is guaranteed to provide functionality implemented in the
     * openHAB cloud product (like notifications).
     *
     * This method can return null, if no Connection for an openHAB cloud server could be
     * constructed. In this case, no exception with more information will be thrown.
     */
    public static final int CLOUD = 3;
}
