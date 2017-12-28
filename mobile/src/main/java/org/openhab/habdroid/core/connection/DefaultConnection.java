package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

public class DefaultConnection extends AbstractConnection {
    private String username;
    private String password;
    private String openHABUrl;

    DefaultConnection(Context ctx, SharedPreferences settings) {
        super(ctx, settings);
    }

    DefaultConnection(Context ctx) {
        super(ctx);
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
        updateHttpClientAuth(getAsyncHttpClient());
        updateHttpClientAuth(getSyncHttpClient());
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
        updateHttpClientAuth(getAsyncHttpClient());
        updateHttpClientAuth(getSyncHttpClient());
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setOpenHABUrl(String openHABUrl) {
        this.openHABUrl = openHABUrl;
    }

    @Override
    public String getOpenHABUrl() {
        return openHABUrl;
    }
}
