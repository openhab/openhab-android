package org.openhab.habdroid.core.connection.exception;

public class NoUrlInformationException extends ConnectionException {
    private final boolean mLocal;

    public NoUrlInformationException(boolean local) {
        super();
        mLocal = local;
    }

    public boolean wouldHaveUsedLocalConnection() {
        return mLocal;
    }
}
