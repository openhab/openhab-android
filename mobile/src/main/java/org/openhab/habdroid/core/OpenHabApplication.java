package org.openhab.habdroid.core;

import androidx.multidex.MultiDexApplication;

import org.openhab.habdroid.background.BackgroundTasksManager;
import org.openhab.habdroid.core.connection.ConnectionFactory;

public class OpenHabApplication extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        ConnectionFactory.Companion.initialize(this);
        BackgroundTasksManager.initialize(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ConnectionFactory.Companion.shutdown();
    }
}
