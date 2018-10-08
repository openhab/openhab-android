package org.openhab.habdroid.core;

import org.openhab.habdroid.core.connection.ConnectionFactory;

import androidx.multidex.MultiDexApplication;

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
