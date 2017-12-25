package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutionException;

public abstract class AbstractConnection implements Connection {
    private static final String TAG = AbstractConnection.class.getSimpleName();

    private Context ctx;
    private SharedPreferences settings;

    private int connectionType;

    private MyAsyncHttpClient asyncHttpClient;
    private MySyncHttpClient syncHttpClient;

    AbstractConnection(Context ctx){
        this(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
    }

    AbstractConnection(Context ctx, SharedPreferences settings) {
        this.ctx = ctx;
        this.settings = settings;
    }

    public MyAsyncHttpClient getAsyncHttpClient() {
        if (asyncHttpClient == null) {
            asyncHttpClient = new MyAsyncHttpClient(ctx, ignoreSslHostname(), ignoreCertTrust());
            asyncHttpClient.setTimeout(30000);
        }
        return asyncHttpClient;
    }

    private Boolean ignoreCertTrust() {
        return settings.getBoolean(Constants.PREFERENCE_SSLCERT, false);
    }

    private Boolean ignoreSslHostname() {
        return settings.getBoolean(Constants.PREFERENCE_SSLHOST, false);
    };

    public MySyncHttpClient getSyncHttpClient() {
        if (syncHttpClient == null) {
            syncHttpClient = new MySyncHttpClient(ctx, ignoreSslHostname(), ignoreCertTrust());
        }
        return syncHttpClient;
    }

    @Override
    public void setConnectionType(int connectionType) {
        this.connectionType = connectionType;
    }

    @Override
    public int getConnectionType() {
        return this.connectionType;
    }

    @Override
    public boolean isReachable() {
        Log.d(TAG, "Checking reachability of " + getOpenHABUrl());
        try {
            AsyncTask task = new AsyncTask<String, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(String... strings) {
                    try {
                        URL url = new URL(strings[0]);
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
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getOpenHABUrl());

            // task can be null, e.g. unit tests, assume the target is reachable in this case
            return task == null || (boolean) task.get();
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            return false;
        } catch (ExecutionException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }
}
