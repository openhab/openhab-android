package org.openhab.habdroid.core;

import android.support.multidex.MultiDexApplication;

import org.openhab.habdroid.core.connection.ConnectionFactory;

public class OpenHABApplication extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        ConnectionFactory.initialize(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ConnectionFactory.shutdown();
    }
}
