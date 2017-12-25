package org.openhab.habdroid.core.connection.exception;

public class NoUrlInformationException extends ConnectionException {
    public NoUrlInformationException(String reason) {
        super(reason);
    }

    public NoUrlInformationException() {
        super("");
    }
}
