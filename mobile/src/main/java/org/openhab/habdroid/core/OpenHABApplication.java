package org.openhab.habdroid.core;

import android.app.Application;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import org.openhab.habdroid.core.connection.ConnectionFactory;

public class OpenHABApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ConnectionFactory.initialize(this);

        registerReceiver(ConnectionFactory.getInstance(),
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(ConnectionFactory.getInstance());
    }
}
