/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.preference.widgets.ItemUpdatingPreference
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPreference
import org.openhab.habdroid.util.getPrefixForBgTasks
import org.openhab.habdroid.util.isInstalled

class SendDeviceInfoSettingsFragment : AbstractSettingsFragment() {
    override val titleResId: Int @StringRes get() = R.string.send_device_info_to_server_short
    private lateinit var phoneStatePref: ItemUpdatingPreference
    private lateinit var wifiSsidPref: ItemUpdatingPreference
    private lateinit var bluetoothPref: ItemUpdatingPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_device_information)

        val prefixHint = getPreference(PrefKeys.DEV_ID_PREFIX_BG_TASKS)
        phoneStatePref = getAndInitPreferenceForPermissionRequest(
            PrefKeys.SEND_PHONE_STATE,
            PreferencesActivity.SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_PHONE,
            R.string.settings_phone_state_permission_denied
        )
        wifiSsidPref = getAndInitPreferenceForPermissionRequest(
            PrefKeys.SEND_WIFI_SSID,
            PreferencesActivity.SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_WIFI,
            R.string.settings_wifi_ssid_permission_denied
        )
        bluetoothPref = getAndInitPreferenceForPermissionRequest(
            PrefKeys.SEND_BLUETOOTH_DEVICES,
            PreferencesActivity.SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_BLUETOOTH,
            R.string.settings_bluetooth_devices_permission_denied
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wifiSsidPref.setSummaryOnAndUpdate(getString(R.string.settings_wifi_ssid_summary_on_location_on))
        }

        if (activity?.packageManager?.isInstalled("nodomain.freeyourgadget.gadgetbridge") == false) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.SEND_GADGETBRIDGE)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.SEND_DND_MODE)
        }

        BackgroundTasksManager.KNOWN_KEYS.forEach { key ->
            findPreference<ItemUpdatingPreference>(key)?.startObserving(this)
        }

        val prefix = prefs.getPrefixForBgTasks()
        prefixHint.summary = if (prefix.isEmpty()) {
            prefixHint.context.getString(R.string.send_device_info_item_prefix_summary_not_set)
        } else {
            prefixHint.context.getString(R.string.send_device_info_item_prefix_summary, prefix)
        }
    }

    private fun getAndInitPreferenceForPermissionRequest(
        prefKey: String,
        permDeniedSnackbarTag: String,
        @StringRes permDeniedSnackbarTextResId: Int
    ): ItemUpdatingPreference {
        val pref = getPreference(prefKey) as ItemUpdatingPreference
        val requiredPerms = BackgroundTasksManager.getRequiredPermissionsForTask(prefKey) ?: return pref

        val handleResult = { anyPermDenied: Boolean ->
            if (anyPermDenied) {
                parentActivity.showSnackbar(permDeniedSnackbarTag, permDeniedSnackbarTextResId)
                pref.setValue(checked = false)
            } else {
                BackgroundTasksManager.scheduleWorker(parentActivity, prefKey, true)
            }
        }
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results -> handleResult(results.values.any { v -> !v }) }

        pref.setOnPreferenceChangeListener { _, newValue ->
            @Suppress("UNCHECKED_CAST")
            if ((newValue as Pair<Boolean, String>).first) {
                parentActivity.requestPermissionsIfRequired(requiredPerms, launcher) { handleResult(false) }
            }
            true
        }
        return pref
    }
}
