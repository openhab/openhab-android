package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.support.annotation.NonNull;

public class DemoConnection extends AbstractConnection {
    DemoConnection(Context ctx) {
        super(ctx);
    }

    @Override
    public void setUsername(String username) {
        throw new RuntimeException("This method is not supported for a connection to the demo " +
                "server.");
    }

    @Override
    public void setPassword(String password) {
        throw new RuntimeException("This method is not supported for a connection to the demo " +
                "server.");
    }

    @Override
    public String getUsername() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public void setOpenHABUrl(String openHABUrl) {
        throw new RuntimeException("This method is not supported for a connection to the demo " +
                "server.");
    }

    @Override
    public void setConnectionType(int connectionType) {
        throw new RuntimeException("This method is not supported for a connection to the demo " +
                "server.");
    }

    @Override
    @NonNull
    public String getOpenHABUrl() {
        return "https://demo.openhab.org:8443/";
    }

    @Override
    public int getConnectionType() {
        return Connection.TYPE_REMOTE;
    }
}
