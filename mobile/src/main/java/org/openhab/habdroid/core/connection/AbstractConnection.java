package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public abstract class AbstractConnection implements Connection {
    private static final String TAG = AbstractConnection.class.getSimpleName();
    private SharedPreferences settings;
    private int connectionType;
    private String username;
    private String password;
    private String baseUrl;

    private final MyAsyncHttpClient asyncHttpClient;
    private final MySyncHttpClient syncHttpClient;

    AbstractConnection(Context ctx, SharedPreferences settings, int connectionType, String baseUrl,
            String username, String password) {
        this.settings = settings;
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.connectionType = connectionType;

        asyncHttpClient = new MyAsyncHttpClient(ctx, ignoreSslHostname(), ignoreCertTrust());
        asyncHttpClient.setTimeout(30000);

        syncHttpClient = new MySyncHttpClient(ctx, ignoreSslHostname(), ignoreCertTrust());

        updateHttpClientAuth(asyncHttpClient);
        updateHttpClientAuth(syncHttpClient);
    }

    private void updateHttpClientAuth(MyHttpClient httpClient) {
        if (hasUsernameAndPassword()) {
            httpClient.setBasicAuth(getUsername(), getPassword());
        }
    }

    private boolean hasUsernameAndPassword() {
        return getUsername() != null && !getUsername().isEmpty() && getPassword() != null &&
                !getPassword().isEmpty();
    }

    public MyAsyncHttpClient getAsyncHttpClient() {
        asyncHttpClient.setBaseUrl(getOpenHABUrl());

        return asyncHttpClient;
    }

    private Boolean ignoreCertTrust() {
        return settings.getBoolean(Constants.PREFERENCE_SSLCERT, false);
    }

    private Boolean ignoreSslHostname() {
        return settings.getBoolean(Constants.PREFERENCE_SSLHOST, false);
    }

    public MySyncHttpClient getSyncHttpClient() {
        syncHttpClient.setBaseUrl(getOpenHABUrl());

        return syncHttpClient;
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
    public int getConnectionType() {
        return connectionType;
    }

    @Override
    @NonNull
    public String getOpenHABUrl() {
        return baseUrl;
    }

    @Override
    public boolean checkReachability() {
        Log.d(TAG, "Checking reachability of " + getOpenHABUrl());

        Socket s = createConnectedSocket();

        if (s == null) {
            return false;
        }

        Log.d(TAG, "Socket connected");
        try {
            s.close();
        } catch (IOException ignored) {
        }
        return true;
    }

    private Socket createConnectedSocket() {
        for (int retries = 0; retries < 10; retries++) {
            Socket s = new Socket();
            InetSocketAddress socketAddress = getReachabilitySocketAddress();
            try {
                s.connect(socketAddress, 1000);
            } catch (SocketTimeoutException e) {
                Log.d(TAG, "Socket timeout at the " + retries + ". try.", e);
                return null;
            } catch (IOException e) {
                Log.d(TAG, "Socket connection failed at the " + retries + ". try.", e);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            return s;
        }
        return null;
    }

    @NonNull
    private InetSocketAddress getReachabilitySocketAddress() {
        Uri openHABUri = Uri.parse(getOpenHABUrl());
        int checkPort = openHABUri.getPort();
        if (checkPort < 0) {
            checkPort = openHABUri.getScheme().equals("https") ? 443 : 80;
        }

        return new InetSocketAddress(openHABUri.getHost(), checkPort);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + connectionType;
        result = 31 * result + baseUrl.hashCode();
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }
}
