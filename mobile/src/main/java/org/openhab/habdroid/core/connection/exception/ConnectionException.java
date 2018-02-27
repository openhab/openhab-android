package org.openhab.habdroid.core.connection.exception;

/**
 * A general ConnectionException is thrown whenever the reason, why a
 * {@link org.openhab.habdroid.core.connection.Connection} was not able to be obtained, is known.
 * Otherwise one of the subclass exceptions may be thrown.
 */
public class ConnectionException extends Exception {
    public ConnectionException() {
        super();
    }
}
