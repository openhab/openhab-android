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

package org.openhab.habdroid.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.CustomInputTypePreference
import org.openhab.habdroid.ui.preference.ItemUpdatingPreference
import org.openhab.habdroid.ui.preference.UrlInputPreference
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getNotificationTone
import org.openhab.habdroid.util.getPreference
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.getString
import org.openhab.habdroid.util.hasPermission
import org.openhab.habdroid.util.isTaskerPluginEnabled
import org.openhab.habdroid.util.showToast
import org.openhab.habdroid.util.updateDefaultSitemap
import java.util.BitSet

/**
 * This is a class to provide preferences activity for application.
 */
class PreferencesActivity : AbstractBaseActivity() {
    private lateinit var resultIntent: Intent

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_prefs)

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            resultIntent = Intent()
            supportFragmentManager.commit {
                add(R.id.activity_content, MainSettingsFragment())
            }
        } else {
            resultIntent = savedInstanceState.getParcelable(STATE_KEY_RESULT) ?: Intent()
        }
        setResult(RESULT_OK, resultIntent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_KEY_RESULT, resultIntent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (isFinishing) {
            return true
        }
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        with(supportFragmentManager) {
            if (backStackEntryCount > 0) {
                popBackStack()
            } else {
                super.onBackPressed()
            }
        }
    }

    fun handleThemeChange() {
        resultIntent.putExtra(RESULT_EXTRA_THEME_CHANGED, true)
        recreate()
    }

    fun openSubScreen(subScreenFragment: AbstractSettingsFragment) {
        supportFragmentManager.commit {
            replace(R.id.activity_content, subScreenFragment)
            addToBackStack(null)
        }
    }

    @VisibleForTesting
    abstract class AbstractSettingsFragment : PreferenceFragmentCompat() {
        @get:StringRes
        protected abstract val titleResId: Int

        protected val parentActivity get() = activity as PreferencesActivity
        protected val prefs get() = preferenceScreen.sharedPreferences!!
        protected val secretPrefs get() = requireContext().getSecretPrefs()

        override fun onStart() {
            super.onStart()
            parentActivity.supportActionBar?.setTitle(titleResId)
        }

        protected fun isConnectionHttps(url: String?): Boolean {
            return url != null && url.startsWith("https://")
        }

        private fun hasConnectionBasicAuthentication(user: String?, password: String?): Boolean {
            return !user.isNullOrEmpty() && !password.isNullOrEmpty()
        }

        private fun hasClientCertificate(): Boolean {
            return prefs.getString(PrefKeys.SSL_CLIENT_CERT).isNotEmpty()
        }

        protected fun isConnectionSecure(url: String?, user: String?, password: String?): Boolean {
            if (!isConnectionHttps(url)) {
                return false
            }
            return hasConnectionBasicAuthentication(user, password) || hasClientCertificate()
        }

        override fun onDisplayPreferenceDialog(preference: Preference?) {
            if (preference == null) {
                return
            }
            val showDialog: (DialogFragment) -> Unit = { fragment ->
                fragment.setTargetFragment(this, 0)
                fragment.show(parentFragmentManager, "SettingsFragment.DIALOG:${preference.key}")
            }
            when (preference) {
                is UrlInputPreference -> showDialog(preference.createDialog())
                is ItemUpdatingPreference -> showDialog(preference.createDialog())
                is CustomInputTypePreference -> showDialog(preference.createDialog())
                else -> super.onDisplayPreferenceDialog(preference)
            }
        }

        companion object {
            /**
             * Password is considered strong when it is at least 8 chars long and contains 3 from those
             * 4 categories:
             * * lowercase
             * * uppercase
             * * numerics
             * * other
             * @param password
             */
            @VisibleForTesting
            fun isWeakPassword(password: String?): Boolean {
                if (password == null || password.length < 8) {
                    return true
                }
                val groups = BitSet()
                password.forEach { c -> groups.set(when {
                    Character.isLetter(c) && Character.isLowerCase(c) -> 0
                    Character.isLetter(c) && Character.isUpperCase(c) -> 1
                    Character.isDigit(c) -> 2
                    else -> 3
                }) }
                return groups.cardinality() < 3
            }
        }
    }

    class MainSettingsFragment : AbstractSettingsFragment() {
        override val titleResId: Int @StringRes get() = R.string.action_settings
        @ColorInt var previousColor: Int = 0

        override fun onStart() {
            super.onStart()
            updateConnectionSummary(PrefKeys.SUBSCREEN_LOCAL_CONNECTION,
                PrefKeys.LOCAL_URL, PrefKeys.LOCAL_USERNAME,
                PrefKeys.LOCAL_PASSWORD)
            updateConnectionSummary(PrefKeys.SUBSCREEN_REMOTE_CONNECTION,
                PrefKeys.REMOTE_URL, PrefKeys.REMOTE_USERNAME,
                PrefKeys.REMOTE_PASSWORD)
            updateScreenLockStateAndSummary(prefs.getString(PrefKeys.SCREEN_LOCK,
                getString(R.string.settings_screen_lock_off_value)))
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences)

            val localConnPref = getPreference(PrefKeys.SUBSCREEN_LOCAL_CONNECTION)
            val remoteConnPref = getPreference(PrefKeys.SUBSCREEN_REMOTE_CONNECTION)
            val sendDeviceInfoPref = getPreference(PrefKeys.SUBSCREEN_SEND_DEVICE_INFO)
            val themePref = getPreference(PrefKeys.THEME)
            val accentColorPref = getPreference(PrefKeys.ACCENT_COLOR) as ColorPreferenceCompat
            val clearCachePref = getPreference(PrefKeys.CLEAR_CACHE)
            val clearDefaultSitemapPref = getPreference(PrefKeys.CLEAR_DEFAULT_SITEMAP)
            val showSitemapInDrawerPref = getPreference(PrefKeys.SHOW_SITEMAPS_IN_DRAWER)
            val fullscreenPreference = getPreference(PrefKeys.FULLSCREEN)
            val iconFormatPreference = getPreference(PrefKeys.ICON_FORMAT)
            val ringtonePref = getPreference(PrefKeys.NOTIFICATION_TONE)
            val vibrationPref = getPreference(PrefKeys.NOTIFICATION_VIBRATION)
            val ringtoneVibrationPref = getPreference(PrefKeys.NOTIFICATION_TONE_VIBRATION)
            val viewLogPref = getPreference(PrefKeys.LOG)
            val screenLockPref = getPreference(PrefKeys.SCREEN_LOCK)
            val prefs = preferenceScreen.sharedPreferences

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                val dataSaverPref = getPreference(PrefKeys.DATA_SAVER) as SwitchPreference
                dataSaverPref.setSwitchTextOff(R.string.data_saver_off_pre_n)
            }

            val currentDefaultSitemap = prefs.getString(PrefKeys.SITEMAP_NAME)
            val currentDefaultSitemapLabel = prefs.getString(PrefKeys.SITEMAP_LABEL)
            if (currentDefaultSitemap.isEmpty()) {
                onNoDefaultSitemap(clearDefaultSitemapPref)
            } else {
                clearDefaultSitemapPref.summary = getString(
                    R.string.settings_current_default_sitemap, currentDefaultSitemapLabel)
            }

            updateRingtonePreferenceSummary(ringtonePref, prefs.getNotificationTone())
            updateVibrationPreferenceIcon(vibrationPref,
                prefs.getString(PrefKeys.NOTIFICATION_VIBRATION))

            localConnPref.setOnPreferenceClickListener {
                parentActivity.openSubScreen(LocalConnectionSettingsFragment())
                false
            }

            remoteConnPref.setOnPreferenceClickListener {
                parentActivity.openSubScreen(RemoteConnectionSettingsFragment())
                false
            }

            sendDeviceInfoPref.setOnPreferenceClickListener {
                parentActivity.openSubScreen(SendDeviceInfoSettingsFragment())
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

            previousColor = prefs.getInt(accentColorPref.key, 0)
            accentColorPref.setOnPreferenceChangeListener { _, newValue ->
                parentFragmentManager.findFragmentByTag(accentColorPref.fragmentTag)?.let { dialog ->
                    parentFragmentManager.commit(allowStateLoss = true) {
                        remove(dialog)
                    }
                }
                if (previousColor != newValue) {
                    parentActivity.handleThemeChange()
                }
                true
            }

            clearCachePref.setOnPreferenceClickListener { pref ->
                clearImageCache(pref.context)
                true
            }

            showSitemapInDrawerPref.setOnPreferenceChangeListener { _, _ ->
                parentActivity.resultIntent.putExtra(RESULT_EXTRA_SITEMAP_DRAWER_CHANGED, true)
                true
            }

            clearDefaultSitemapPref.setOnPreferenceClickListener { preference ->
                preference.sharedPreferences.edit { updateDefaultSitemap(null, null) }
                onNoDefaultSitemap(preference)
                parentActivity.resultIntent.putExtra(RESULT_EXTRA_SITEMAP_CLEARED, true)
                true
            }

            if (!prefs.isTaskerPluginEnabled() && !isAutomationAppInstalled()) {
                preferenceScreen.removePreferenceRecursively(PrefKeys.TASKER_PLUGIN_ENABLED)
            }

            viewLogPref.setOnPreferenceClickListener { preference ->
                val logIntent = Intent(preference.context, LogActivity::class.java)
                startActivity(logIntent)
                true
            }

            fullscreenPreference.setOnPreferenceChangeListener { _, newValue ->
                (activity as AbstractBaseActivity).checkFullscreen(newValue as Boolean)
                true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Removing notification prefs for < 25")
                preferenceScreen.removePreferenceRecursively(PrefKeys.NOTIFICATION_TONE)
                preferenceScreen.removePreferenceRecursively(PrefKeys.NOTIFICATION_VIBRATION)

                ringtoneVibrationPref.setOnPreferenceClickListener { pref ->
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
                    startActivityForResult(chooserIntent, REQUEST_CODE_RINGTONE)
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

            val flags = activity?.intent?.getParcelableExtra<ServerProperties>(START_EXTRA_SERVER_PROPERTIES)?.flags
                ?: preferenceScreen.sharedPreferences.getInt(PrefKeys.PREV_SERVER_FLAGS, 0)

            if (flags and ServerProperties.SERVER_FLAG_ICON_FORMAT_SUPPORT == 0 ||
                flags and ServerProperties.SERVER_FLAG_SUPPORTS_ANY_FORMAT_ICON != 0) {
                preferenceScreen.removePreferenceRecursively(PrefKeys.ICON_FORMAT)
            } else {
                iconFormatPreference.setOnPreferenceChangeListener { pref, _ ->
                    val context = pref.context
                    clearImageCache(context)
                    ItemUpdateWidget.updateAllWidgets(context)
                    true
                }
            }
            if (flags and ServerProperties.SERVER_FLAG_CHART_SCALING_SUPPORT == 0) {
                preferenceScreen.removePreferenceRecursively(PrefKeys.CHART_SCALING)
            }
        }

        private fun clearImageCache(context: Context) {
            // Get launch intent for application
            val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Finish current activity
            activity?.finish()
            CacheManager.getInstance(context).clearCache()
            // Start launch activity
            startActivity(restartIntent)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_CODE_RINGTONE && data != null) {
                val ringtoneUri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                val ringtonePref = getPreference(PrefKeys.NOTIFICATION_TONE)
                updateRingtonePreferenceSummary(ringtonePref, ringtoneUri)
                prefs.edit {
                    putString(PrefKeys.NOTIFICATION_TONE, ringtoneUri?.toString() ?: "")
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }

        private fun onNoDefaultSitemap(pref: Preference) {
            pref.isEnabled = false
            pref.setSummary(R.string.settings_no_default_sitemap)
        }

        private fun updateScreenLockStateAndSummary(value: String?) {
            val pref = findPreference<Preference>(PrefKeys.SCREEN_LOCK) ?: return
            val km = ContextCompat.getSystemService(pref.context, KeyguardManager::class.java)!!
            val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) km.isDeviceSecure else km.isKeyguardSecure
            pref.isEnabled = locked
            pref.summary = getString(when {
                !locked -> R.string.settings_screen_lock_nolock_summary
                value == getString(R.string.settings_screen_lock_on_value) -> R.string.settings_screen_lock_on_summary
                value == getString(R.string.settings_screen_lock_kiosk_value) ->
                    R.string.settings_screen_lock_kiosk_summary
                else -> R.string.settings_screen_lock_off_summary
            })
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
            pref.setIcon(if (noVibration)
                R.drawable.ic_vibrate_off_grey_24dp
            else
                R.drawable.ic_vibration_grey_24dp)
        }

        private fun updateConnectionSummary(
            subscreenPrefKey: String,
            urlPrefKey: String,
            userPrefKey: String,
            passwordPrefKey: String
        ) {
            val pref = getPreference(subscreenPrefKey)
            val url = prefs.getString(urlPrefKey)
            val beautyUrl = beautifyUrl(url)
            val userName = secretPrefs.getString(userPrefKey, null)
            val password = secretPrefs.getString(passwordPrefKey, null)
            val summary = when {
                url.isEmpty() -> getString(R.string.info_not_set)
                isConnectionSecure(url, userName, password) ->
                    getString(R.string.settings_connection_summary, beautyUrl)
                else -> getString(R.string.settings_insecure_connection_summary, beautyUrl)
            }
            pref.summary = summary
        }

        private fun isAutomationAppInstalled(): Boolean {
            val pm = activity?.packageManager ?: return false
            return listOf("net.dinglisch.android.taskerm", "com.twofortyfouram.locale").any { pkg ->
                try {
                    // Some devices return `null` for getApplicationInfo()
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    pm.getApplicationInfo(pkg, 0)?.enabled == true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
        }

        companion object {
            private const val REQUEST_CODE_RINGTONE = 1000

            @VisibleForTesting fun beautifyUrl(url: String): String {
                val host = url.toHttpUrlOrNull()?.host ?: url
                return if (host.matches("^(home.)?myopenhab.org$".toRegex())) "myopenHAB" else host
            }
        }
    }

    internal abstract class ConnectionSettingsFragment : AbstractSettingsFragment() {
        private lateinit var urlPreference: Preference
        private lateinit var userNamePreference: Preference
        private lateinit var passwordPreference: Preference

        protected fun initPreferences(
            urlPrefKey: String,
            userNamePrefKey: String,
            passwordPrefKey: String,
            @StringRes urlSummaryFormatResId: Int
        ) {
            urlPreference = initEditor(urlPrefKey, prefs, R.drawable.ic_earth_grey_24dp) { value ->
                val actualValue = if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
                getString(urlSummaryFormatResId, actualValue)
            }

            userNamePreference = initEditor(userNamePrefKey, secretPrefs,
                R.drawable.ic_person_outline_grey_24dp) { value ->
                if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
            }
            passwordPreference = initEditor(passwordPrefKey, secretPrefs,
                R.drawable.ic_shield_key_outline_grey_24dp) { value ->
                getString(when {
                    value.isNullOrEmpty() -> R.string.info_not_set
                    isWeakPassword(value) -> R.string.settings_openhab_password_summary_weak
                    else -> R.string.settings_openhab_password_summary_strong
                })
            }

            updateIconColors(urlPreference.getPrefValue(),
                userNamePreference.getPrefValue(), passwordPreference.getPrefValue())
        }

        private fun initEditor(
            key: String,
            prefsForValue: SharedPreferences,
            @DrawableRes iconResId: Int,
            summaryGenerator: (value: String?) -> CharSequence
        ): Preference {
            val preference: Preference = preferenceScreen.findPreference(key)!!
            preference.preferenceDataStore = SharedPrefsDataStore(prefsForValue)
            preference.icon = DrawableCompat.wrap(ContextCompat.getDrawable(preference.context, iconResId)!!)
            preference.setOnPreferenceChangeListener { pref, newValue ->
                updateIconColors(getActualValue(pref, newValue, urlPreference),
                    getActualValue(pref, newValue, userNamePreference),
                    getActualValue(pref, newValue, passwordPreference))
                pref.summary = summaryGenerator(newValue as String)
                true
            }
            preference.summary = summaryGenerator(prefsForValue.getString(key))
            return preference
        }

        private fun getActualValue(pref: Preference, newValue: Any, reference: Preference?): String? {
            return if (pref === reference) newValue as String else reference.getPrefValue()
        }

        private fun updateIconColors(url: String?, userName: String?, password: String?) {
            updateIconColor(urlPreference) { when {
                isConnectionHttps(url) -> R.color.pref_icon_green
                !url.isNullOrEmpty() -> R.color.pref_icon_red
                else -> null
            } }
            updateIconColor(userNamePreference) { when {
                url.isNullOrEmpty() -> null
                userName.isNullOrEmpty() -> R.color.pref_icon_red
                else -> R.color.pref_icon_green
            } }
            updateIconColor(passwordPreference) { when {
                url.isNullOrEmpty() -> null
                password.isNullOrEmpty() -> R.color.pref_icon_red
                isWeakPassword(password) -> R.color.pref_icon_orange
                else -> R.color.pref_icon_green
            } }
        }

        private fun updateIconColor(pref: Preference, colorGenerator: () -> Int?) {
            val colorResId = colorGenerator()
            if (colorResId != null) {
                DrawableCompat.setTint(pref.icon, ContextCompat.getColor(pref.context, colorResId))
            } else {
                DrawableCompat.setTintList(pref.icon, null)
            }
        }
    }

    internal class LocalConnectionSettingsFragment : ConnectionSettingsFragment() {
        override val titleResId: Int @StringRes get() = R.string.settings_openhab_connection

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.local_connection_preferences)
            initPreferences(PrefKeys.LOCAL_URL, PrefKeys.LOCAL_USERNAME,
                PrefKeys.LOCAL_PASSWORD, R.string.settings_openhab_url_summary)
        }
    }

    internal class RemoteConnectionSettingsFragment : ConnectionSettingsFragment() {
        override val titleResId: Int @StringRes get() = R.string.settings_openhab_alt_connection

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.remote_connection_preferences)
            initPreferences(PrefKeys.REMOTE_URL, PrefKeys.REMOTE_USERNAME,
                PrefKeys.REMOTE_PASSWORD, R.string.settings_openhab_alturl_summary)
        }
    }

    internal class SendDeviceInfoSettingsFragment : AbstractSettingsFragment() {
        override val titleResId: Int @StringRes get() = R.string.send_device_info_to_server_short
        private lateinit var phoneStatePref: ItemUpdatingPreference
        private lateinit var wifiSsidPref: ItemUpdatingPreference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences_device_information)

            val prefixPref = getPreference(PrefKeys.SEND_DEVICE_INFO_PREFIX)
            val schedulePref = getPreference(PrefKeys.SEND_DEVICE_INFO_SCHEDULE)
            phoneStatePref = getPreference(PrefKeys.SEND_PHONE_STATE) as ItemUpdatingPreference
            wifiSsidPref = getPreference(PrefKeys.SEND_CHARGING_STATE) as ItemUpdatingPreference

            phoneStatePref.setOnPreferenceChangeListener { preference, newValue ->
                @Suppress("UNCHECKED_CAST")
                val value = newValue as Pair<Boolean, String>
                if (value.first && !preference.context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                    Log.d(TAG, "Request READ_PHONE_STATE permission")
                    requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE),
                        PERMISSIONS_REQUEST_READ_PHONE_STATE)
                }

                true
            }

            wifiSsidPref.setOnPreferenceChangeListener { preference, newValue ->
                @Suppress("UNCHECKED_CAST")
                val value = newValue as Pair<Boolean, String>
                if (value.first &&
                    !preference.context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ) {
                    Log.d(TAG, "Request ACCESS_FINE_LOCATION permission")
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
                }

                true
            }

            updatePrefixSummary(prefixPref, prefs.getString(PrefKeys.SEND_DEVICE_INFO_PREFIX))
            prefixPref.setOnPreferenceChangeListener { _, newValue ->
                val prefix = newValue as String
                updatePrefixSummary(prefixPref, prefix)

                BackgroundTasksManager.KNOWN_KEYS.forEach {
                    (getPreference(it) as ItemUpdatingPreference).updateSummaryAndIcon(prefix)
                }
                true
            }

            schedulePref.setOnPreferenceChangeListener { preference, _ ->
                parentActivity.launch(Dispatchers.Main) {
                    BackgroundTasksManager.schedulePeriodicTrigger(preference.context, true)
                }
                true
            }
        }

        private fun updatePrefixSummary(pref: Preference, newValue: String?) {
            pref.summary = if (newValue.isNullOrEmpty()) {
                pref.context.getString(R.string.send_device_info_item_prefix_summary_not_set)
            } else {
                pref.context.getString(R.string.send_device_info_item_prefix_summary, newValue)
            }
        }

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
            when (requestCode) {
                PERMISSIONS_REQUEST_READ_PHONE_STATE -> {
                    if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        context?.showToast(R.string.settings_phone_state_permission_denied, ToastType.ERROR)
                        phoneStatePref.setValue(checked = false)
                    }
                }
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                    if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        context?.showToast(R.string.settings_wifi_ssid_permission_denied, ToastType.ERROR)
                        wifiSsidPref.setValue(checked = false)
                    }
                }
            }
        }
    }

    companion object {
        const val RESULT_EXTRA_THEME_CHANGED = "theme_changed"
        const val RESULT_EXTRA_SITEMAP_CLEARED = "sitemap_cleared"
        const val RESULT_EXTRA_SITEMAP_DRAWER_CHANGED = "sitemap_drawer_changed"
        const val START_EXTRA_SERVER_PROPERTIES = "server_properties"
        const val ITEM_UPDATE_WIDGET_ITEM = "item"
        const val ITEM_UPDATE_WIDGET_STATE = "state"
        const val ITEM_UPDATE_WIDGET_LABEL = "label"
        const val ITEM_UPDATE_WIDGET_WIDGET_LABEL = "widgetLabel"
        const val ITEM_UPDATE_WIDGET_MAPPED_STATE = "mappedState"
        const val ITEM_UPDATE_WIDGET_ICON = "icon"
        private const val STATE_KEY_RESULT = "result"
        private const val PERMISSIONS_REQUEST_READ_PHONE_STATE = 0
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1

        private val TAG = PreferencesActivity::class.java.simpleName
    }
}

fun Preference?.getPrefValue(defaultValue: String? = null): String? {
    if (this == null) {
        return defaultValue
    }
    preferenceDataStore?.let {
        return it.getString(key, defaultValue)
    }
    return sharedPreferences.getString(key, defaultValue)
}

class SharedPrefsDataStore constructor(val prefs: SharedPreferences) : PreferenceDataStore() {
    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return prefs.getBoolean(key, defValue)
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return prefs.getInt(key, defValue)
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return prefs.getLong(key, defValue)
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return prefs.getFloat(key, defValue)
    }

    override fun getString(key: String?, defValue: String?): String? {
        return prefs.getString(key, defValue)
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> {
        return prefs.getStringSet(key, defValues) ?: mutableSetOf()
    }

    override fun putBoolean(key: String?, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    override fun putInt(key: String?, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    override fun putLong(key: String?, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    override fun putFloat(key: String?, value: Float) {
        prefs.edit { putFloat(key, value) }
    }

    override fun putString(key: String?, value: String?) {
        prefs.edit { putString(key, value) }
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?) {
        prefs.edit { putStringSet(key, values) }
    }
}
