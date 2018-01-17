package org.openhab.habdroid.core;

import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.support.multidex.MultiDexApplication;

import org.openhab.habdroid.core.connection.ConnectionFactory;

public class OpenHABApplication extends MultiDexApplication {
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
