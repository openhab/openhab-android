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

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.background.tiles.getTileData
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.ui.AbstractBaseActivity
import org.openhab.habdroid.ui.LogActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.preference.widgets.NotificationPollingPreference
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.CrashReportingHelper
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getNextAvailableServerId
import org.openhab.habdroid.util.getNotificationTone
import org.openhab.habdroid.util.getPreference
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getStringOrFallbackIfEmpty
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.isInstalled
import org.openhab.habdroid.util.isTaskerPluginEnabled
import org.openhab.habdroid.util.parcelable

class MainSettingsFragment : AbstractSettingsFragment(), ConnectionFactory.UpdateListener {
    override val titleResId: Int @StringRes get() = R.string.action_settings

    private var notificationPollingPref: NotificationPollingPreference? = null
    private var notificationStatusHint: Preference? = null
    private var selectRingToneCallback = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "selectRingToneCallback: $result")
        val data = result.data ?: return@registerForActivityResult
        val ringtoneUri = data.parcelable<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val ringtonePref = getPreference(PrefKeys.NOTIFICATION_TONE)
        updateRingtonePreferenceSummary(ringtonePref, ringtoneUri)
        prefs.edit {
            putString(PrefKeys.NOTIFICATION_TONE, ringtoneUri?.toString() ?: "")
        }
    }

    override fun onStart() {
        super.onStart()
        updateScreenLockStateAndSummary(
            prefs.getStringOrFallbackIfEmpty(PrefKeys.SCREEN_LOCK, getString(R.string.settings_screen_lock_off_value))
        )
        populateServerPrefs()
        ConnectionFactory.addListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateTileSummary()
        }
    }

    override fun onStop() {
        super.onStop()
        ConnectionFactory.removeListener(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val addServerPref = getPreference("add_server")
        val sendDeviceInfoPref = getPreference(PrefKeys.SUBSCREEN_SEND_DEVICE_INFO)
        notificationPollingPref =
            getPreference(PrefKeys.FOSS_NOTIFICATIONS_ENABLED) as NotificationPollingPreference
        notificationStatusHint = getPreference(PrefKeys.NOTIFICATION_STATUS_HINT)
        val drawerEntriesPrefs = getPreference(PrefKeys.DRAWER_ENTRIES)
        val themePref = getPreference(PrefKeys.THEME)
        val colorSchemePref = getPreference(PrefKeys.COLOR_SCHEME) as ListPreference
        val fullscreenPref = getPreference(PrefKeys.FULLSCREEN)
        val launcherPref = getPreference(PrefKeys.LAUNCHER)
        val iconFormatPref = getPreference(PrefKeys.ICON_FORMAT)
        val ringtonePref = getPreference(PrefKeys.NOTIFICATION_TONE)
        val vibrationPref = getPreference(PrefKeys.NOTIFICATION_VIBRATION)
        val viewLogPref = getPreference(PrefKeys.LOG)
        val screenLockPref = getPreference(PrefKeys.SCREEN_LOCK)
        val tilePref = getPreference(PrefKeys.SUBSCREEN_TILE)
        val deviceControlPref = getPreference(PrefKeys.SUBSCREEN_DEVICE_CONTROL)
        val crashReporting = getPreference(PrefKeys.CRASH_REPORTING)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val dataSaverPref = getPreference(PrefKeys.DATA_SAVER) as SwitchPreferenceCompat
            dataSaverPref.setSwitchTextOff(R.string.data_saver_off_pre_n)
        }

        updateRingtonePreferenceSummary(ringtonePref, prefs.getNotificationTone())
        updateVibrationPreferenceIcon(vibrationPref, prefs.getStringOrNull(PrefKeys.NOTIFICATION_VIBRATION))

        addServerPref.setOnPreferenceClickListener {
            val nextServerId = prefs.getNextAvailableServerId()
            val nextName = if (prefs.getConfiguredServerIds().isEmpty()) {
                getString(R.string.openhab)
            } else {
                getString(R.string.settings_server_default_name, nextServerId)
            }
            val f = ServerEditorFragment.newInstance(
                ServerConfiguration(nextServerId, nextName, null, null, null, null, null, false, null)
            )
            parentActivity.openSubScreen(f)
            true
        }

        sendDeviceInfoPref.setOnPreferenceClickListener {
            parentActivity.openSubScreen(SendDeviceInfoSettingsFragment())
            false
        }

        if (CloudMessagingHelper.isPollingBuild()) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.NOTIFICATION_STATUS_HINT)
        } else {
            preferenceScreen.removePreferenceRecursively(PrefKeys.FOSS_NOTIFICATIONS_ENABLED)
        }
        updateNotificationStatusSummaries()
        notificationPollingPref?.setOnPreferenceChangeListener { _, _ ->
            parentActivity.launch(Dispatchers.Main) {
                updateNotificationStatusSummaries()
            }
            true
        }

        drawerEntriesPrefs.setOnPreferenceClickListener {
            parentActivity.openSubScreen(DrawerEntriesMenuFragment())
            false
        }

        themePref.setOnPreferenceChangeListener { _, _ ->
            // getDayNightMode() needs the new preference value, so delay the call until
            // after this listener has returned
            parentActivity.launch(Dispatchers.Main) {
                val mode = parentActivity.getPrefs().getDayNightMode(parentActivity)
                AppCompatDelegate.setDefaultNightMode(mode)
                parentActivity.handleThemeChange()
            }
            true
        }

        if (DynamicColors.isDynamicColorAvailable()) {
            colorSchemePref.entries = resources.getStringArray(R.array.colorSchemeNamesDynamic)
            colorSchemePref.entryValues = resources.getStringArray(R.array.colorSchemeValuesDynamic)
        }
        colorSchemePref.setOnPreferenceChangeListener { _, _ ->
            parentActivity.handleThemeChange()
            true
        }

        getPreference(PrefKeys.SHOW_ICONS).setOnPreferenceChangeListener { _, _ ->
            parentActivity.addResultFlag(PreferencesActivity.RESULT_EXTRA_SHOW_ICONS_CHANGED)
            true
        }

        getPreference(PrefKeys.CLEAR_CACHE).setOnPreferenceClickListener { pref ->
            clearCaches(pref.context)
            true
        }

        if (!prefs.isTaskerPluginEnabled() && !isAutomationAppInstalled()) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.TASKER_PLUGIN_ENABLED)
        }

        if (CrashReportingHelper.canBeDisabledByUser()) {
            crashReporting.setOnPreferenceClickListener {
                CrashReportingHelper.initialize(requireActivity().application)
                true
            }
        } else {
            preferenceScreen.removePreferenceRecursively(PrefKeys.CRASH_REPORTING)
        }

        viewLogPref.setOnPreferenceClickListener { preference ->
            val logIntent = Intent(preference.context, LogActivity::class.java)
            startActivity(logIntent)
            true
        }

        fullscreenPref.setOnPreferenceChangeListener { _, newValue ->
            (activity as AbstractBaseActivity).setFullscreen(newValue as Boolean)
            true
        }

        launcherPref.setOnPreferenceChangeListener { pref, newValue ->
            val context = pref.context
            val launcherAlias = ComponentName(context, "${context.packageName}.ui.LauncherActivityAlias")
            val newState = if (newValue as Boolean) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(launcherAlias, newState, PackageManager.DONT_KILL_APP)
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Removing notification prefs for < 25")
            preferenceScreen.removePreferenceRecursively(PrefKeys.NOTIFICATION_TONE)
            preferenceScreen.removePreferenceRecursively(PrefKeys.NOTIFICATION_VIBRATION)

            getPreference(PrefKeys.NOTIFICATION_TONE_VIBRATION).setOnPreferenceClickListener { pref ->
                val i = Intent(Settings.ACTION_SETTINGS).apply {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, pref.context.packageName)
                }
                startActivity(i)
                true
            }
        } else {
            Log.d(TAG, "Removing notification prefs for >= 25")
            preferenceScreen.removePreferenceRecursively(PrefKeys.NOTIFICATION_TONE_VIBRATION)

            ringtonePref.setOnPreferenceClickListener { pref ->
                val currentTone = prefs.getNotificationTone()
                val chooserIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pref.title)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentTone)
                }
                selectRingToneCallback.launch(chooserIntent)
                true
            }

            vibrationPref.setOnPreferenceChangeListener { pref, newValue ->
                updateVibrationPreferenceIcon(pref, newValue as String?)
                true
            }
        }

        screenLockPref.setOnPreferenceChangeListener { _, newValue ->
            updateScreenLockStateAndSummary(newValue as String)
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tilePref.setOnPreferenceClickListener {
                parentActivity.openSubScreen(TileOverviewFragment())
                false
            }
            updateTileSummary()
        } else {
            preferenceScreen.removePreferenceRecursively(PrefKeys.SUBSCREEN_TILE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            deviceControlPref.setOnPreferenceClickListener {
                parentActivity.openSubScreen(DeviceControlFragment())
                false
            }
        } else {
            preferenceScreen.removePreferenceRecursively(PrefKeys.SUBSCREEN_DEVICE_CONTROL)
        }

        val flags = activity
            ?.intent
            ?.parcelable<ServerProperties>(PreferencesActivity.START_EXTRA_SERVER_PROPERTIES)
            ?.flags
            ?: prefs.getInt(PrefKeys.PREV_SERVER_FLAGS, 0)

        if (flags and ServerProperties.SERVER_FLAG_ICON_FORMAT_SUPPORT == 0 ||
            flags and ServerProperties.SERVER_FLAG_SUPPORTS_ANY_FORMAT_ICON != 0
        ) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.ICON_FORMAT)
        } else {
            iconFormatPref.setOnPreferenceChangeListener { pref, _ ->
                val context = pref.context
                clearCaches(context)
                ItemUpdateWidget.updateAllWidgets(context)
                true
            }
        }
        if (flags and ServerProperties.SERVER_FLAG_CHART_SCALING_SUPPORT == 0) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.CHART_SCALING)
        }
    }

    private fun populateServerPrefs() {
        val context = preferenceManager.context
        val connCategory = getPreference("connection") as PreferenceCategory
        (0 until connCategory.preferenceCount)
            .map { index -> connCategory.getPreference(index) }
            .filter { pref -> pref.key?.startsWith("server_") == true }
            .forEach { pref -> connCategory.removePreference(pref) }

        prefs.getConfiguredServerIds().forEach { serverId ->
            val config = ServerConfiguration.load(prefs, secretPrefs, serverId)
            if (config != null) {
                val pref = Preference(context)
                pref.title = context.getString(R.string.server_with_name, config.name)
                pref.key = "server_$serverId"
                pref.order = 10 * serverId
                pref.setOnPreferenceClickListener {
                    parentActivity.openSubScreen(ServerEditorFragment.newInstance(config))
                    true
                }
                pref.icon = if (serverId == prefs.getPrimaryServerId() && prefs.getConfiguredServerIds().size > 1) {
                    ContextCompat.getDrawable(context, R.drawable.ic_star_border_grey_24dp)
                } else {
                    null
                }
                connCategory.addPreference(pref)
                // The pref needs to be attached for doing this
                pref.dependency = PrefKeys.DEMO_MODE
            }
        }
    }

    private fun clearCaches(context: Context) {
        WebView(context).clearCache(true)
        // Get launch intent for application
        val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Finish current activity
        activity?.finish()
        CacheManager.getInstance(context).clearCache(true)
        // Start launch activity
        restartIntent?.let { startActivity(it) }
    }

    private fun updateNotificationStatusSummaries() {
        parentActivity.launch {
            notificationPollingPref?.updateSummaryAndIcon()
            notificationStatusHint?.apply {
                val data = CloudMessagingHelper.getPushNotificationStatus(this.context)
                summary = data.message
                setIcon(data.icon)
            }
        }
    }

    private fun updateScreenLockStateAndSummary(value: String?) {
        val pref = findPreference<Preference>(PrefKeys.SCREEN_LOCK) ?: return
        val km = ContextCompat.getSystemService(pref.context, KeyguardManager::class.java)!!
        val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) km.isDeviceSecure else km.isKeyguardSecure
        pref.isEnabled = locked
        pref.summary = getString(
            when {
                !locked -> R.string.settings_screen_lock_nolock_summary
                value == getString(R.string.settings_screen_lock_on_value) -> R.string.settings_screen_lock_on_summary
                value == getString(R.string.settings_screen_lock_kiosk_value) ->
                    R.string.settings_screen_lock_kiosk_summary
                else -> R.string.settings_screen_lock_off_summary
            }
        )
    }

    private fun updateRingtonePreferenceSummary(pref: Preference, newValue: Uri?) {
        if (newValue == null) {
            pref.setIcon(R.drawable.ic_bell_off_outline_grey_24dp)
            pref.setSummary(R.string.settings_ringtone_none)
        } else {
            pref.setIcon(R.drawable.ic_bell_ring_outline_grey_24dp)
            val ringtone = RingtoneManager.getRingtone(activity, newValue)
            pref.summary = try {
                ringtone?.getTitle(activity)
            } catch (e: SecurityException) {
                getString(R.string.settings_ringtone_on_external)
            }
        }
    }

    private fun updateVibrationPreferenceIcon(pref: Preference, newValue: String?) {
        val noVibration = newValue == getString(R.string.settings_notification_vibration_value_off)
        pref.setIcon(if (noVibration) R.drawable.ic_vibrate_off_grey_24dp else R.drawable.ic_vibration_grey_24dp)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun updateTileSummary() {
        val activeTileCount = (1..AbstractTileService.TILE_COUNT)
            .mapNotNull { id -> prefs.getTileData(id) }
            .size
        val pref = getPreference(PrefKeys.SUBSCREEN_TILE)
        pref.summary = resources.getQuantityString(R.plurals.tile_active_number, activeTileCount, activeTileCount)
    }

    private fun isAutomationAppInstalled(): Boolean {
        val pm = activity?.packageManager ?: return false
        // These package names must be added to the manifest as well
        return listOf("net.dinglisch.android.taskerm", "com.twofortyfouram.locale").any { pkg ->
            pm.isInstalled(pkg)
        }
    }

    override fun onActiveConnectionChanged() {
        // no-op
    }

    override fun onPrimaryConnectionChanged() {
        updateNotificationStatusSummaries()
    }

    override fun onActiveCloudConnectionChanged(connection: CloudConnection?) {
        // no-op
    }

    override fun onPrimaryCloudConnectionChanged(connection: CloudConnection?) {
        updateNotificationStatusSummaries()
    }

    companion object {
        private val TAG = MainSettingsFragment::class.java.simpleName
    }
}
