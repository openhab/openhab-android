package org.openhab.habdroid.core.connection;

import android.support.annotation.NonNull;
import android.util.Log;

import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.SyncHttpClient;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

import okhttp3.OkHttpClient;

public abstract class AbstractConnection implements Connection {
    private static final String TAG = AbstractConnection.class.getSimpleName();

    private int connectionType;
    private String username;
    private String password;
    private String baseUrl;

    private final AsyncHttpClient asyncHttpClient;
    private final SyncHttpClient syncHttpClient;

    AbstractConnection(OkHttpClient httpClient, int connectionType,
            String baseUrl, String username, String password) {
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.connectionType = connectionType;

        asyncHttpClient = new AsyncHttpClient(httpClient, baseUrl, username, password);
        syncHttpClient = new SyncHttpClient(httpClient, baseUrl, username, password);
    }

    AbstractConnection(@NonNull AbstractConnection base, int connectionType) {
        this.username = base.username;
        this.password = base.password;
        this.baseUrl = base.baseUrl;
        this.connectionType = connectionType;

        asyncHttpClient = base.getAsyncHttpClient();
        syncHttpClient = base.getSyncHttpClient();
    }

    private boolean hasUsernameAndPassword() {
        return getUsername() != null && !getUsername().isEmpty() && getPassword() != null &&
                !getPassword().isEmpty();
    }

    @Override
    public AsyncHttpClient getAsyncHttpClient() {
        return asyncHttpClient;
    }

    @Override
    public SyncHttpClient getSyncHttpClient() {
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
    public boolean checkReachabilityInBackground() {
        Log.d(TAG, "Checking reachability of " + baseUrl);
        try {
            URL url = new URL(baseUrl);
            int checkPort = url.getPort();
            if (url.getProtocol().equals("http") && checkPort == -1)
                checkPort = 80;
            if (url.getProtocol().equals("https") && checkPort == -1)
                checkPort = 443;
            Socket s = new Socket();
            s.connect(new InetSocketAddress(url.getHost(), checkPort), 1000);
            Log.d(TAG, "Socket connected");
            s.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
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
