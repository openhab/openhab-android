package org.openhab.habdroid.core;

import android.app.Application;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;

import org.openhab.habdroid.core.connection.ConnectionFactory;

public class OpenHABApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ConnectionFactory factory = ConnectionFactory.getInstance();
        factory.setContext(this);
        factory.setSettings(PreferenceManager.getDefaultSharedPreferences(this));

        registerReceiver(factory,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(ConnectionFactory.getInstance());
    }
}
