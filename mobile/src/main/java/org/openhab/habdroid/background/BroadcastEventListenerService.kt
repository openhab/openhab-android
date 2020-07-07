/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isEventListenerEnabled
import org.openhab.habdroid.util.isItemUpdatePrefEnabled

class BroadcastEventListenerService : Service() {
    private var backgroundTasksManager = BackgroundTasksManager()
    private var isRegistered = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand())")

        if (isRegistered) {
            unregisterReceiver(backgroundTasksManager)
            isRegistered = false
        }
        val intentFilter = BackgroundTasksManager.getIntentFilterForForeground(this)
        if (intentFilter.countActions() == 0) {
            stopSelf(startId)
        } else {
            registerReceiver(backgroundTasksManager, intentFilter)
            isRegistered = true
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        NotificationUpdateObserver.createNotificationChannels(this)

        val titlesOfItems = BackgroundTasksManager.KNOWN_PERIODIC_KEYS
            .filter { key -> getPrefs().isItemUpdatePrefEnabled(key) }
            .map { key -> getString(getTitleResForDeviceInfo(key)) }
        val summary = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O &&
                getPrefs().isItemUpdatePrefEnabled(PrefKeys.SEND_DND_MODE) -> {
                getString(R.string.send_device_info_foreground_service_running_summary_dnd)
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> null
            titlesOfItems.size == 1 -> {
                getString(R.string.send_device_info_foreground_service_running_summary_one, titlesOfItems[0])
            }
            titlesOfItems.size == 2 -> {
                getString(R.string.send_device_info_foreground_service_running_summary_two,
                    titlesOfItems[0], titlesOfItems[1])
            }
            else -> {
                getString(R.string.send_device_info_foreground_service_running_summary_more,
                    titlesOfItems[0], titlesOfItems[1])
            }
        }
        val title = getString(R.string.send_device_info_foreground_service_title)
        val notificationBuilder = NotificationCompat.Builder(
            this,
            NotificationUpdateObserver.CHANNEL_ID_BACKGROUND_FOREGROUND_SERVICE
        )
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(summary)
            .setSmallIcon(R.drawable.ic_openhab_appicon_24dp)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setColor(ContextCompat.getColor(applicationContext, R.color.openhab_orange))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NotificationUpdateObserver.NOTIFICATION_ID_BROADCAST_RECEIVER, notificationBuilder)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")

        if (isRegistered) {
            unregisterReceiver(backgroundTasksManager)
            isRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val TAG = BroadcastEventListenerService::class.java.simpleName

        fun startOrStopService(context: Context, start: Boolean = context.getPrefs().isEventListenerEnabled()) {
            val intent = Intent(context, BroadcastEventListenerService::class.java)
            if (start) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }

        @VisibleForTesting fun getTitleResForDeviceInfo(key: String) = when (key) {
            PrefKeys.SEND_BATTERY_LEVEL -> R.string.settings_battery_level
            PrefKeys.SEND_CHARGING_STATE -> R.string.settings_charging_state
            PrefKeys.SEND_WIFI_SSID -> R.string.settings_wifi_ssid
            PrefKeys.SEND_DND_MODE -> R.string.settings_dnd_mode
            else -> throw IllegalArgumentException("No summary for $key")
        }
    }
}
