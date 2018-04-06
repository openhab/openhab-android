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

    private final int mConnectionType;
    private final String mUserName;
    private final String mPassword;
    private final String mBaseUrl;

    private final AsyncHttpClient mAsyncHttpClient;
    private final SyncHttpClient mSyncHttpClient;

    AbstractConnection(OkHttpClient httpClient, int connectionType,
            String baseUrl, String username, String password) {
        mUserName = username;
        mPassword = password;
        mBaseUrl = baseUrl;
        mConnectionType = connectionType;

        mAsyncHttpClient = new AsyncHttpClient(httpClient, baseUrl, username, password);
        mSyncHttpClient = new SyncHttpClient(httpClient, baseUrl, username, password);
    }

    AbstractConnection(@NonNull AbstractConnection base, int connectionType) {
        mUserName = base.mUserName;
        mPassword = base.mPassword;
        mBaseUrl = base.mBaseUrl;
        mConnectionType = connectionType;

        mAsyncHttpClient = base.getAsyncHttpClient();
        mSyncHttpClient = base.getSyncHttpClient();
    }

    private boolean hasUsernameAndPassword() {
        return getUsername() != null && !getUsername().isEmpty() && getPassword() != null &&
                !getPassword().isEmpty();
    }

    @Override
    public AsyncHttpClient getAsyncHttpClient() {
        return mAsyncHttpClient;
    }

    @Override
    public SyncHttpClient getSyncHttpClient() {
        return mSyncHttpClient;
    }

    @Override
    public String getUsername() {
        return mUserName;
    }

    @Override
    public String getPassword() {
        return mPassword;
    }

    @Override
    public int getConnectionType() {
        return mConnectionType;
    }

    @Override
    public boolean checkReachabilityInBackground() {
        Log.d(TAG, "Checking reachability of " + mBaseUrl);
        try {
            URL url = new URL(mBaseUrl);
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
        result = 31 * result + mConnectionType;
        result = 31 * result + mBaseUrl.hashCode();
        result = 31 * result + (mUserName != null ? mUserName.hashCode() : 0);
        result = 31 * result + (mPassword != null ? mPassword.hashCode() : 0);
        return result;
    }
}
