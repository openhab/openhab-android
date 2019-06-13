/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import org.openhab.habdroid.R
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.ui.widget.toItemUpdatePrefValue
import org.openhab.habdroid.util.*
import java.util.*

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
            fragmentManager
                .beginTransaction()
                .add(R.id.prefs_container, MainSettingsFragment())
                .commit()
        } else {
            resultIntent = savedInstanceState.getParcelable(STATE_KEY_RESULT)
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
                val fm = fragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                } else {
                    NavUtils.navigateUpFromSameTask(this)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun handleThemeChange() {
        resultIntent.putExtra(RESULT_EXTRA_THEME_CHANGED, true)
        recreate()
    }

    fun openSubScreen(subScreenFragment: AbstractSettingsFragment) {
        fragmentManager
            .beginTransaction()
            .replace(R.id.prefs_container, subScreenFragment)
            .addToBackStack(null)
            .commit()
    }

    @VisibleForTesting
    abstract class AbstractSettingsFragment : PreferenceFragment() {
        @get:StringRes
        protected abstract val titleResId: Int

        protected val parentActivity get() = activity as PreferencesActivity
        protected val prefs get() = preferenceScreen.sharedPreferences

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            updateAndInitPreferences()
        }

        override fun onStart() {
            super.onStart()
            parentActivity.supportActionBar?.setTitle(titleResId)
        }

        protected abstract fun updateAndInitPreferences()

        protected fun isConnectionHttps(url: String?): Boolean {
            return url != null && url.startsWith("https://")
        }

        private fun hasConnectionBasicAuthentication(user: String?, password: String?): Boolean {
            return !user.isNullOrEmpty() && !password.isNullOrEmpty()
        }

        private fun hasClientCertificate(): Boolean {
            return prefs.getString(Constants.PREFERENCE_SSLCLIENTCERT).isNotEmpty()
        }

        protected fun isConnectionSecure(url: String?, user: String?, password: String?): Boolean {
            if (!isConnectionHttps(url)) {
                return false
            }
            return hasConnectionBasicAuthentication(user, password) || hasClientCertificate()
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

        override fun onStart() {
            super.onStart()
            updateConnectionSummary(Constants.SUBSCREEN_LOCAL_CONNECTION,
                Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                Constants.PREFERENCE_LOCAL_PASSWORD)
            updateConnectionSummary(Constants.SUBSCREEN_REMOTE_CONNECTION,
                Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                Constants.PREFERENCE_REMOTE_PASSWORD)
        }

        override fun updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.preferences)

            val localConnPref = findPreference(Constants.SUBSCREEN_LOCAL_CONNECTION)
            val remoteConnPref = findPreference(Constants.SUBSCREEN_REMOTE_CONNECTION)
            val themePref = findPreference(Constants.PREFERENCE_THEME)
            val clearCachePref = findPreference(Constants.PREFERENCE_CLEAR_CACHE)
            val clearDefaultSitemapPref = findPreference(Constants.PREFERENCE_CLEAR_DEFAULT_SITEMAP)
            val ringtonePref = findPreference(Constants.PREFERENCE_TONE)
            val fullscreenPreference = findPreference(Constants.PREFERENCE_FULLSCREEN)
            val sendDeviceInfoPrefixPref = findPreference(Constants.PREFERENCE_SEND_DEVICE_INFO_PREFIX)
            val alarmClockPref = findPreference(Constants.PREFERENCE_ALARM_CLOCK)
            val taskerPref = findPreference(Constants.PREFERENCE_TASKER_PLUGIN_ENABLED)
            val vibrationPref = findPreference(Constants.PREFERENCE_NOTIFICATION_VIBRATION)
            val ringtoneVibrationPref = findPreference(Constants.PREFERENCE_NOTIFICATION_TONE_VIBRATION)
            val viewLogPref = findPreference(Constants.PREFERENCE_LOG)
            val prefs = preferenceScreen.sharedPreferences

            val currentDefaultSitemap = prefs.getString(Constants.PREFERENCE_SITEMAP_NAME)
            val currentDefaultSitemapLabel = prefs.getString(Constants.PREFERENCE_SITEMAP_LABEL)
            if (currentDefaultSitemap.isEmpty()) {
                onNoDefaultSitemap(clearDefaultSitemapPref)
            } else {
                clearDefaultSitemapPref.summary = getString(
                    R.string.settings_current_default_sitemap, currentDefaultSitemapLabel)
            }

            updateConnectionSummary(Constants.SUBSCREEN_LOCAL_CONNECTION,
                Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                Constants.PREFERENCE_LOCAL_PASSWORD)
            updateConnectionSummary(Constants.SUBSCREEN_REMOTE_CONNECTION,
                Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                Constants.PREFERENCE_REMOTE_PASSWORD)
            updateRingtonePreferenceSummary(ringtonePref,
                prefs.getString(Constants.PREFERENCE_TONE))
            updateVibrationPreferenceIcon(vibrationPref,
                prefs.getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION))

            localConnPref.setOnPreferenceClickListener {
                parentActivity.openSubScreen(LocalConnectionSettingsFragment())
                false
            }

            remoteConnPref.setOnPreferenceClickListener {
                parentActivity.openSubScreen(RemoteConnectionSettingsFragment())
                false
            }

            themePref.setOnPreferenceChangeListener { _, _ ->
                parentActivity.handleThemeChange()
                true
            }

            clearCachePref.setOnPreferenceClickListener {
                // Get launch intent for application
                val restartIntent = activity.packageManager
                    .getLaunchIntentForPackage(activity.baseContext.packageName)
                restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // Finish current activity
                activity.finish()
                CacheManager.getInstance(activity).clearCache()
                // Start launch activity
                startActivity(restartIntent)
                // Start launch activity
                true
            }

            clearDefaultSitemapPref.setOnPreferenceClickListener { preference ->
                preference.sharedPreferences.edit { updateDefaultSitemap(null) }
                onNoDefaultSitemap(preference)
                parentActivity.resultIntent.putExtra(RESULT_EXTRA_SITEMAP_CLEARED, true)
                true
            }

            if (!prefs.isTaskerPluginEnabled() && !isAutomationAppInstalled()) {
                preferenceScreen.removePreferenceFromHierarchy(taskerPref)
            }

            ringtonePref.setOnPreferenceChangeListener { pref, newValue ->
                updateRingtonePreferenceSummary(pref, newValue as String?)
                true
            }

            vibrationPref.setOnPreferenceChangeListener { pref, newValue ->
                updateVibrationPreferenceIcon(pref, newValue as String?)
                true
            }

            ringtoneVibrationPref.setOnPreferenceClickListener {
                val i = Intent(Settings.ACTION_SETTINGS).apply {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
                startActivity(i)
                true
            }

            viewLogPref.setOnPreferenceClickListener { preference ->
                val logIntent = Intent(preference.context, LogActivity::class.java)
                startActivity(logIntent)
                true
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                Log.d(TAG, "Removing fullscreen pref as device isn't running Kitkat or higher")
                preferenceScreen.removePreferenceFromHierarchy(fullscreenPreference)
            } else {
                fullscreenPreference.setOnPreferenceChangeListener { _, newValue ->
                    (activity as AbstractBaseActivity).checkFullscreen(newValue as Boolean)
                    true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Removing notification prefs for < 25")
                preferenceScreen.removePreferenceFromHierarchy(ringtonePref)
                preferenceScreen.removePreferenceFromHierarchy(vibrationPref)
            } else {
                Log.d(TAG, "Removing notification prefs for >= 25")
                preferenceScreen.removePreferenceFromHierarchy(ringtoneVibrationPref)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, "Removing alarm clock pref")
                preferenceScreen.removePreference(alarmClockPref)
            } else {
                updateAlarmClockPreferenceIcon(alarmClockPref,
                    alarmClockPref.getPrefValue().toItemUpdatePrefValue())
                updateAlarmClockPreferenceSummary(alarmClockPref,
                    sendDeviceInfoPrefixPref.getPrefValue(),
                    alarmClockPref.getPrefValue().toItemUpdatePrefValue())
                alarmClockPref.setOnPreferenceChangeListener { preference, newValue ->
                    val prefix = sendDeviceInfoPrefixPref.getPrefValue()
                    val value = newValue as Pair<Boolean, String>
                    updateAlarmClockPreferenceIcon(preference, newValue)
                    updateAlarmClockPreferenceSummary(preference, prefix, value)
                    true
                }
            }

            sendDeviceInfoPrefixPref.setOnPreferenceChangeListener { _, newValue ->
                updateAlarmClockPreferenceSummary(alarmClockPref, newValue as String,
                    alarmClockPref.getPrefValue().toItemUpdatePrefValue())
                true
            }

            val flags = activity.intent.getParcelableExtra<ServerProperties>(START_EXTRA_SERVER_PROPERTIES)?.flags
                ?: preferenceScreen.sharedPreferences.getInt(Constants.PREV_SERVER_FLAGS, 0)

            if (flags and ServerProperties.SERVER_FLAG_ICON_FORMAT_SUPPORT == 0) {
                val iconFormatPreference = preferenceScreen.findPreference(Constants.PREFERENCE_ICON_FORMAT)
                preferenceScreen.removePreferenceFromHierarchy(iconFormatPreference)
            }
            if (flags and ServerProperties.SERVER_FLAG_CHART_SCALING_SUPPORT == 0) {
                val chartScalingPreference = preferenceScreen.findPreference(Constants.PREFERENCE_CHART_SCALING)
                preferenceScreen.removePreferenceFromHierarchy(chartScalingPreference)
            }
        }

        private fun onNoDefaultSitemap(pref: Preference) {
            pref.isEnabled = false
            pref.setSummary(R.string.settings_no_default_sitemap)
        }

        private fun updateRingtonePreferenceSummary(pref: Preference, newValue: String?) {
            if (newValue.isNullOrEmpty()) {
                pref.setIcon(R.drawable.ic_bell_off_outline_grey_24dp)
                pref.setSummary(R.string.settings_ringtone_none)
            } else {
                pref.setIcon(R.drawable.ic_bell_ring_outline_grey_24dp)
                val ringtone = RingtoneManager.getRingtone(activity, newValue.toUri())
                if (ringtone != null) {
                    pref.summary = ringtone.getTitle(activity)
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

        private fun updateAlarmClockPreferenceSummary(pref: Preference, prefix: String?, value: Pair<Boolean, String>) {
            pref.summary = if (value.first)
                getString(R.string.settings_alarm_clock_summary_on, (prefix.orEmpty()) + value.second)
            else
                getString(R.string.settings_alarm_clock_summary_off)
        }

        private fun updateAlarmClockPreferenceIcon(pref: Preference, value: Pair<Boolean, String>) {
            pref.setIcon(if (value.first) R.drawable.ic_alarm_grey_24dp else R.drawable.ic_alarm_off_grey_24dp)
        }

        private fun updateConnectionSummary(
            subscreenPrefKey: String,
            urlPrefKey: String,
            userPrefKey: String,
            passwordPrefKey: String
        ) {
            val pref = findPreference(subscreenPrefKey)
            val url = beautifyUrl(prefs.getString(urlPrefKey))
            val summary = when {
                url.isEmpty() -> getString(R.string.info_not_set)
                isConnectionSecure(url, prefs.getString(userPrefKey), prefs.getString(passwordPrefKey)) -> {
                    getString(R.string.settings_connection_summary, url)
                }
                else -> getString(R.string.settings_insecure_connection_summary, url)
            }
            pref.summary = summary
        }

        private fun isAutomationAppInstalled(): Boolean {
            val pm = activity.packageManager
            return listOf("net.dinglisch.android.taskerm", "com.twofortyfouram.locale").any { pkg ->
                try {
                    pm.getApplicationInfo(pkg, 0).enabled
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
        }

        companion object {
            @VisibleForTesting fun beautifyUrl(url: String?): String {
                val host = url?.toUri()?.host.orEmpty()
                return if (host.contains("myopenhab.org")) "myopenHAB" else url.orEmpty()
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
            urlPreference = initEditor(urlPrefKey, R.drawable.ic_earth_grey_24dp) { value ->
                val actualValue = if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
                getString(urlSummaryFormatResId, actualValue)
            }
            userNamePreference = initEditor(userNamePrefKey, R.drawable.ic_person_outline_grey_24dp) { value ->
                if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
            }
            passwordPreference = initEditor(passwordPrefKey, R.drawable.ic_shield_key_outline_grey_24dp) { value ->
                getString(when {
                    value.isNullOrEmpty() -> R.string.info_not_set
                    isWeakPassword(value) -> R.string.settings_openhab_password_summary_weak
                    else -> R.string.settings_openhab_password_summary_strong
                })
            }

            updateIconColors(prefs.getString(urlPrefKey),
                prefs.getString(userNamePrefKey), prefs.getString(passwordPrefKey))
        }

        private fun initEditor(
            key: String,
            @DrawableRes iconResId: Int,
            summaryGenerator: (value: String?) -> CharSequence
        ): Preference {
            val preference = preferenceScreen.findPreference(key)
            preference.icon = DrawableCompat.wrap(ContextCompat.getDrawable(activity, iconResId)!!)
            preference.setOnPreferenceChangeListener { pref, newValue ->
                updateIconColors(getActualValue(pref, newValue, urlPreference),
                    getActualValue(pref, newValue, userNamePreference),
                    getActualValue(pref, newValue, passwordPreference))
                pref.summary = summaryGenerator(newValue as String)
                true
            }
            preference.summary = summaryGenerator(prefs.getString(key))
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

        override fun updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.local_connection_preferences)
            initPreferences(Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                Constants.PREFERENCE_LOCAL_PASSWORD, R.string.settings_openhab_url_summary)
        }
    }

    internal class RemoteConnectionSettingsFragment : ConnectionSettingsFragment() {
        override val titleResId: Int @StringRes get() = R.string.settings_openhab_alt_connection

        override fun updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.remote_connection_preferences)
            initPreferences(Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                Constants.PREFERENCE_REMOTE_PASSWORD, R.string.settings_openhab_alturl_summary)
        }
    }

    companion object {
        const val RESULT_EXTRA_THEME_CHANGED = "theme_changed"
        const val RESULT_EXTRA_SITEMAP_CLEARED = "sitemap_cleared"
        const val START_EXTRA_SERVER_PROPERTIES = "server_properties"
        private const val STATE_KEY_RESULT = "result"

        private val TAG = PreferencesActivity::class.java.simpleName
    }
}

fun Preference?.getPrefValue(defaultValue: String? = null): String? {
    if (this == null) {
        return defaultValue
    }
    return sharedPreferences?.getString(key, defaultValue)
}

fun PreferenceGroup.removePreferenceFromHierarchy(pref: Preference?) {
    if (pref == null) {
        return
    }

    /**
     * @author https://stackoverflow.com/a/17633389
     */
    fun getParent(pref: Preference, root: PreferenceGroup): PreferenceGroup? {
        for (i in 0 until root.preferenceCount) {
            val p = root.getPreference(i)
            if (p === pref) {
                return root
            }
            if (p is PreferenceGroup) {
                val parent = getParent(pref, p)
                if (parent != null) {
                    return parent
                }
            }
        }
        return null
    }

    val parent = getParent(pref, this)
    parent?.removePreference(pref)
}