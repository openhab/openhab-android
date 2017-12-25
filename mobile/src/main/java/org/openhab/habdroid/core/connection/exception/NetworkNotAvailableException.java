package org.openhab.habdroid.core.connection.exception;

public class NetworkNotAvailableException extends ConnectionException {
    public NetworkNotAvailableException(String reason) {
        super(reason);
    }

    public NetworkNotAvailableException() {
        super("");
    }
}
