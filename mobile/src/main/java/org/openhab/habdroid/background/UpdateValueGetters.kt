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

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import java.text.SimpleDateFormat
import java.util.Locale
import org.openhab.habdroid.R
import org.openhab.habdroid.util.hasPermissions
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.registerExportedReceiver

internal object UpdateValueGetters {
    // These package names must be added to the manifest as well
    private val IGNORED_PACKAGES_FOR_ALARM = listOf(
        "net.dinglisch.android.taskerm",
        "com.android.providers.calendar",
        "com.android.calendar",
        "com.samsung.android.calendar",
        "com.miui.securitycenter",
        "org.thoughtcrime.securesms",
        "im.molly.app",
        "com.samsung.android.fmm",
        "eu.smartpatient.mytherapy",
        "com.samsung.android.app.routines"
    )

    val SEND_PHONE_STATE_PERMISSIONS = arrayOf(Manifest.permission.READ_PHONE_STATE)

    val SEND_WIFI_SSID_PERMISSIONS = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)

        else -> null
    }

    val SEND_BLUETOOTH_DEVICE_PERMISSIONS = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH)

        else -> null
    }

    fun buildAlarmChangeUpdateInfo(context: Context): ItemUpdateWorker.ValueWithInfo {
        val alarmManager = context.getSystemService<AlarmManager>()
        val info: AlarmManager.AlarmClockInfo? = alarmManager?.nextAlarmClock
        val sender = info?.showIntent?.creatorPackage

        Log.d(BackgroundTasksManager.TAG, "Alarm sent by $sender")
        val timeStamp = info?.triggerTime?.let { time ->
            SimpleDateFormat("HH:mm yyyy-MM-dd", Locale.US).format(time)
        }

        val ignoreSender = when {
            sender in IGNORED_PACKAGES_FOR_ALARM -> true
            sender == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> true
            else -> false
        }

        val (debugInfoRes, time) = if (ignoreSender || info == null) {
            R.string.settings_alarm_clock_debug_ignored to "UNDEF"
        } else {
            R.string.settings_alarm_clock_debug to info.triggerTime.toString()
        }

        return ItemUpdateWorker.ValueWithInfo(
            value = time,
            type = ItemUpdateWorker.ValueType.Timestamp,
            debugInfo = context.getString(debugInfoRes, timeStamp, sender)
        )
    }

    fun buildPhoneStateUpdateInfo(context: Context): ItemUpdateWorker.ValueWithInfo {
        val itemState = if (!context.hasPermissions(SEND_PHONE_STATE_PERMISSIONS)) {
            "NO_PERMISSION"
        } else {
            val manager = context.getSystemService<TelephonyManager>()

            val callState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager?.callStateForSubscription
            } else {
                @Suppress("DEPRECATION")
                manager?.callState
            }

            when (callState) {
                TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                else -> "UNDEF"
            }
        }

        return ItemUpdateWorker.ValueWithInfo(itemState)
    }

    fun buildBatteryLevelUpdateInfo(context: Context): ItemUpdateWorker.ValueWithInfo {
        val bm = context.getSystemService<BatteryManager>()
        val batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.toString()
        return ItemUpdateWorker.ValueWithInfo(batteryLevel ?: "UNDEF")
    }

    fun buildChargingStateUpdateInfo(context: Context): ItemUpdateWorker.ValueWithInfo {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let {
            context.registerExportedReceiver(null, it)
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        Log.d(BackgroundTasksManager.TAG, "EXTRA_STATUS is $status, EXTRA_PLUGGED is $plugged")
        val state = when {
            status != BatteryManager.BATTERY_STATUS_CHARGING && status != BatteryManager.BATTERY_STATUS_FULL ->
                "UNDEF"

            plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB"

            plugged == BatteryManager.BATTERY_PLUGGED_AC -> "AC"

            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"

            else -> "UNKNOWN_CHARGER"
        }
        return ItemUpdateWorker.ValueWithInfo(state, type = ItemUpdateWorker.ValueType.MapUndefToOffForSwitchItems)
    }

    fun buildWifiSsidUpdateInfo(context: Context): ItemUpdateWorker.ValueWithInfo {
        val wifiManager = context.getSystemService<WifiManager>()
        val locationManager = context.getSystemService<LocationManager>()
        val hasRequiredPermissions = SEND_WIFI_SSID_PERMISSIONS.let { it == null || context.hasPermissions(it) }

        // TODO: Replace deprecated function
        @Suppress("DEPRECATION")
        val ssidToSend = wifiManager?.connectionInfo.let { info ->
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    locationManager != null &&
                    !LocationManagerCompat.isLocationEnabled(locationManager) -> {
                    "LOCATION_OFF"
                }

                !hasRequiredPermissions -> "NO_PERMISSION"

                info == null || info.networkId == -1 -> "UNDEF"

                else -> {
                    // WifiInfo#getSSID() may surround the SSID with double quote marks
                    info.ssid.removeSurrounding("\"")
                }
            }
        }
        return ItemUpdateWorker.ValueWithInfo(ssidToSend)
    }

    fun buildDndModeUpdateInfo(context: Context) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val nm = context.getSystemService<NotificationManager>()
        val mode = when (nm?.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> "TOTAL_SILENCE"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS"
            NotificationManager.INTERRUPTION_FILTER_ALL -> "OFF"
            else -> "UNDEF"
        }
        ItemUpdateWorker.ValueWithInfo(mode)
    } else {
        ItemUpdateWorker.ValueWithInfo("UNDEF")
    }

    fun buildBluetoothDevicesUpdateInfo(context: Context): ItemUpdateWorker.ValueWithInfo {
        fun BluetoothDevice.isConnected(): Boolean = try {
            val m = javaClass.getMethod("isConnected")
            m.invoke(this) as Boolean
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        val hasRequiredPermissions = SEND_BLUETOOTH_DEVICE_PERMISSIONS.let {
            it == null || context.hasPermissions(it)
        }
        val state = if (!hasRequiredPermissions) {
            "NO_PERMISSION"
        } else {
            val bm = context.getSystemService<BluetoothManager>()
            bm?.adapter?.bondedDevices
                ?.filter { device -> device.isConnected() }
                ?.joinToString("|") { device -> device.address }
                .orDefaultIfEmpty("UNDEF")
        }

        return ItemUpdateWorker.ValueWithInfo(state)
    }
}
