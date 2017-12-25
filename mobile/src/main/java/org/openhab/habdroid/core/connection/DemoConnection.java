package org.openhab.habdroid.core.connection;

import android.content.Context;

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
    public String getOpenHABUrl() {
        return "http://demo.openhab.org:8080/";
    }

    @Override
    public int getConnectionType() {
        return Connections.REMOTE;
    }
}
