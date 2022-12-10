/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.preference.fragments

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.background.EventListenerService
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.preference.widgets.ItemUpdatingPreference
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPreference
import org.openhab.habdroid.util.getPrefixForBgTasks
import org.openhab.habdroid.util.isInstalled

class SendDeviceInfoSettingsFragment :
    AbstractSettingsFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override val titleResId: Int @StringRes get() = R.string.send_device_info_to_server_short
    private lateinit var phoneStatePref: ItemUpdatingPreference
    private lateinit var wifiSsidPref: ItemUpdatingPreference
    private lateinit var bluetoothPref: ItemUpdatingPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_device_information)

        val prefixHint = getPreference(PrefKeys.DEV_ID_PREFIX_BG_TASKS)
        val foregroundServicePref = getPreference(PrefKeys.SEND_DEVICE_INFO_FOREGROUND_SERVICE)
        phoneStatePref = getPreference(PrefKeys.SEND_PHONE_STATE) as ItemUpdatingPreference
        wifiSsidPref = getPreference(PrefKeys.SEND_WIFI_SSID) as ItemUpdatingPreference
        bluetoothPref = getPreference(PrefKeys.SEND_BLUETOOTH_DEVICES) as ItemUpdatingPreference

        phoneStatePref.setOnPreferenceChangeListener { _, newValue ->
            requestPermissionIfEnabled(
                newValue,
                BackgroundTasksManager.getRequiredPermissionsForTask(PrefKeys.SEND_PHONE_STATE),
                PERMISSIONS_REQUEST_FOR_CALL_STATE
            )
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wifiSsidPref.setSummaryOn(getString(R.string.settings_wifi_ssid_summary_on_location_on))
        }
        wifiSsidPref.setOnPreferenceChangeListener { _, newValue ->
            requestPermissionIfEnabled(
                newValue,
                BackgroundTasksManager.getRequiredPermissionsForTask(PrefKeys.SEND_WIFI_SSID),
                PERMISSIONS_REQUEST_FOR_WIFI_NAME
            )
            true
        }

        bluetoothPref.setOnPreferenceChangeListener { _, newValue ->
            requestPermissionIfEnabled(
                newValue,
                BackgroundTasksManager.getRequiredPermissionsForTask(PrefKeys.SEND_BLUETOOTH_DEVICES),
                PERMISSIONS_REQUEST_FOR_BLUETOOTH_DEVICES
            )
            true
        }

        if (activity?.packageManager?.isInstalled("nodomain.freeyourgadget.gadgetbridge") == false) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.SEND_GADGETBRIDGE)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.SEND_DND_MODE)
            preferenceScreen.removePreferenceRecursively(PrefKeys.SEND_DEVICE_INFO_FOREGROUND_SERVICE)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            foregroundServicePref.setSummary(R.string.send_device_info_foreground_service_summary_pre_o)
        }

        foregroundServicePref.setOnPreferenceChangeListener { preference, newValue ->
            EventListenerService.startOrStopService(preference.context, newValue as Boolean)
            true
        }

        BackgroundTasksManager.KNOWN_KEYS.forEach { key ->
            findPreference<ItemUpdatingPreference>(key)?.startObserving(this)
        }

        prefs.registerOnSharedPreferenceChangeListener(this)

        val prefix = prefs.getPrefixForBgTasks()
        prefixHint.summary = if (prefix.isEmpty()) {
            prefixHint.context.getString(R.string.send_device_info_item_prefix_summary_not_set)
        } else {
            prefixHint.context.getString(R.string.send_device_info_item_prefix_summary, prefix)
        }
    }

    override fun onDetach() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDetach()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in BackgroundTasksManager.KNOWN_PERIODIC_KEYS) {
            EventListenerService.startOrStopService(requireContext())
        }
    }

    private fun requestPermissionIfEnabled(
        newValue: Any?,
        permissions: Array<String>?,
        requestCode: Int
    ) {
        @Suppress("UNCHECKED_CAST")
        if ((newValue as Pair<Boolean, String>).first) {
            parentActivity.requestPermissionsIfRequired(permissions, requestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val context = phoneStatePref.context

        when (requestCode) {
            PERMISSIONS_REQUEST_FOR_CALL_STATE -> {
                if (grantResults.firstOrNull { it != PackageManager.PERMISSION_GRANTED } != null) {
                    parentActivity.showSnackbar(
                        PreferencesActivity.SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_PHONE,
                        R.string.settings_phone_state_permission_denied
                    )
                    phoneStatePref.setValue(checked = false)
                } else {
                    BackgroundTasksManager.scheduleWorker(context, PrefKeys.SEND_PHONE_STATE, true)
                }
            }
            PERMISSIONS_REQUEST_FOR_WIFI_NAME -> {
                if (grantResults.firstOrNull { it != PackageManager.PERMISSION_GRANTED } != null) {
                    parentActivity.showSnackbar(
                        PreferencesActivity.SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_WIFI,
                        R.string.settings_wifi_ssid_permission_denied
                    )
                    wifiSsidPref.setValue(checked = false)
                } else {
                    BackgroundTasksManager.scheduleWorker(context, PrefKeys.SEND_WIFI_SSID, true)
                }
            }
            PERMISSIONS_REQUEST_FOR_BLUETOOTH_DEVICES -> {
                if (grantResults.firstOrNull { it != PackageManager.PERMISSION_GRANTED } != null) {
                    parentActivity.showSnackbar(
                        PreferencesActivity.SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_BLUETOOTH,
                        R.string.settings_bluetooth_devices_permission_denied
                    )
                    bluetoothPref.setValue(checked = false)
                } else {
                    BackgroundTasksManager.scheduleWorker(context, PrefKeys.SEND_BLUETOOTH_DEVICES, true)
                }
            }
        }
    }

    companion object {
        private const val PERMISSIONS_REQUEST_FOR_CALL_STATE = 0
        private const val PERMISSIONS_REQUEST_FOR_WIFI_NAME = 1
        private const val PERMISSIONS_REQUEST_FOR_BLUETOOTH_DEVICES = 2
    }
}
