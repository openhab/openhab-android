package org.openhab.habdroid.core;

import androidx.multidex.MultiDexApplication;

import org.openhab.habdroid.core.connection.ConnectionFactory;

public class HabDroidApplication extends MultiDexApplication {
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
