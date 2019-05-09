package org.openhab.habdroid.core

import androidx.multidex.MultiDexApplication

import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory

class OpenHabApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        ConnectionFactory.initialize(this)
        BackgroundTasksManager.initialize(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        ConnectionFactory.shutdown()
    }
}
