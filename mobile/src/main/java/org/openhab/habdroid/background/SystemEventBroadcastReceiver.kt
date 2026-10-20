/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.TaskerItemPickerActivity
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getBackgroundTasksManager
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isItemUpdatePrefEnabled
import org.openhab.habdroid.util.isTaskerPluginEnabled

class SystemEventBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(BackgroundTasksManager.TAG, "External onReceive() with intent ${intent.action}")
        val backgroundTasksManager = context.getBackgroundTasksManager()

        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ->
                backgroundTasksManager.scheduleWorker(PrefKeys.SEND_ALARM_CLOCK, true)

            TelephonyManager.ACTION_PHONE_STATE_CHANGED ->
                backgroundTasksManager.scheduleWorker(PrefKeys.SEND_PHONE_STATE, true)

            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY -> {
                backgroundTasksManager.scheduleWorker(PrefKeys.SEND_BATTERY_LEVEL, true)
                backgroundTasksManager.scheduleWorker(PrefKeys.SEND_CHARGING_STATE, true)
            }

            WifiManager.NETWORK_STATE_CHANGED_ACTION ->
                backgroundTasksManager.scheduleWorker(PrefKeys.SEND_WIFI_SSID, true)

            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                backgroundTasksManager.scheduleWorker(PrefKeys.SEND_BLUETOOTH_DEVICES, true)

            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ->
                backgroundTasksManager.scheduleWorker(PrefKeys.SEND_DND_MODE, true)

            Intent.ACTION_LOCALE_CHANGED -> {
                Log.d(BackgroundTasksManager.TAG, "Locale changed, recreate notification channels")
                NotificationUpdateObserver.createNotificationChannels(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                backgroundTasksManager.handleBootCompletedIntent()
            }

            TaskerIntent.ACTION_QUERY_CONDITION, TaskerIntent.ACTION_FIRE_SETTING -> {
                if (!context.getPrefs().isTaskerPluginEnabled()) {
                    Log.d(BackgroundTasksManager.TAG, "Tasker plugin is disabled")
                    if (isOrderedBroadcast) {
                        Log.d(BackgroundTasksManager.TAG, "Send failure to Tasker")
                        resultCode = TaskerItemPickerActivity.RESULT_CODE_PLUGIN_DISABLED
                        TaskerPlugin.addVariableBundle(
                            getResultExtras(true),
                            Bundle().apply {
                                putString(
                                    TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE,
                                    context.getString(R.string.tasker_plugin_disabled)
                                )
                            }
                        )
                    }
                } else if (backgroundTasksManager.handleTaskerIntent(intent) && isOrderedBroadcast) {
                    resultCode = TaskerPlugin.Setting.RESULT_CODE_PENDING
                }
            }
        }
    }

    companion object {
        fun getIntentFilterForForeground(context: Context) = IntentFilter().apply {
            val prefs = context.getPrefs()
            // These broadcasts are already defined in the manifest, so we only need them on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_BATTERY_LEVEL) ||
                    prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_CHARGING_STATE)
                ) {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                    addAction(Intent.ACTION_BATTERY_LOW)
                    addAction(Intent.ACTION_BATTERY_OKAY)
                }
                if (prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_WIFI_SSID)) {
                    addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                }
            }
            // This broadcast is only sent to registered receivers, so we need that in any case
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_DND_MODE)
            ) {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
        }
    }
}
