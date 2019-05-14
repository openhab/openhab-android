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
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri

import org.openhab.habdroid.R
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.ui.widget.ItemUpdatingPreference
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.Constants

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
            fragmentManager
                    .beginTransaction()
                    .add(R.id.prefs_container, MainSettingsFragment())
                    .commit()
        } else {
            resultIntent = savedInstanceState.getParcelable(STATE_KEY_RESULT)!!
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
        when (item.itemId) {
            android.R.id.home -> {
                val fm = fragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                } else {
                    NavUtils.navigateUpFromSameTask(this)
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
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

        protected val parentActivity: PreferencesActivity
            get() = activity as PreferencesActivity

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            updateAndInitPreferences()
        }

        override fun onStart() {
            super.onStart()
            parentActivity.supportActionBar?.setTitle(titleResId)
        }

        protected abstract fun updateAndInitPreferences()

        protected fun getPreferenceString(preference: Preference?, defValue: String?): String? {
            if (preference == null) {
                return null
            }
            return getPreferenceString(preference.key, defValue)
        }

        protected fun getPreferenceString(prefKey: String, defValue: String?): String? {
            return preferenceScreen.sharedPreferences.getString(prefKey, defValue)
        }

        protected fun getPreferenceInt(preference: Preference, defValue: Int): Int {
            return getPreferenceInt(preference.key, defValue)
        }

        protected fun getPreferenceInt(prefKey: String, defValue: Int): Int {
            return preferenceScreen.sharedPreferences.getInt(prefKey, defValue)
        }

        protected fun getPreferenceBool(preference: Preference, defValue: Boolean): Boolean {
            return getPreferenceBool(preference.key, defValue)
        }

        protected fun getPreferenceBool(prefKey: String, defValue: Boolean): Boolean {
            return preferenceScreen.sharedPreferences.getBoolean(prefKey, defValue)
        }

        protected fun isConnectionHttps(url: String?): Boolean {
            return url != null && url.startsWith("https://")
        }

        protected fun hasConnectionBasicAuthentication(user: String?, password: String?): Boolean {
            return !user.isNullOrEmpty() && !password.isNullOrEmpty()
        }

        protected fun hasClientCertificate(): Boolean {
            return !getPreferenceString(Constants.PREFERENCE_SSLCLIENTCERT, "").isNullOrEmpty()
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
                for (i in 0 until password.length) {
                    val c = password[i]
                    if (Character.isLetter(c) && Character.isLowerCase(c)) {
                        groups.set(0)
                    } else if (Character.isLetter(c) && Character.isUpperCase(c)) {
                        groups.set(1)
                    } else if (Character.isDigit(c)) {
                        groups.set(2)
                    } else {
                        groups.set(3)
                    }
                }

                return groups.cardinality() < 3
            }
        }
    }

    class MainSettingsFragment : AbstractSettingsFragment() {
        override val titleResId: Int
            @StringRes
            get() = R.string.action_settings

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
            val vibrationPref = findPreference(Constants.PREFERENCE_NOTIFICATION_VIBRATION)
            val ringtoneVibrationPref = findPreference(Constants.PREFERENCE_NOTIFICATION_TONE_VIBRATION)
            val viewLogPref = findPreference(Constants.PREFERENCE_LOG)
            val prefs = preferenceScreen.sharedPreferences

            val currentDefaultSitemap = prefs.getString(Constants.PREFERENCE_SITEMAP_NAME, "") as String
            val currentDefaultSitemapLabel = prefs.getString(Constants.PREFERENCE_SITEMAP_LABEL, "") as String
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
                    prefs.getString(Constants.PREFERENCE_TONE, ""))
            updateVibrationPreferenceIcon(vibrationPref,
                    prefs.getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION, "")!!)

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
                val edit = preference.sharedPreferences.edit()
                edit.putString(Constants.PREFERENCE_SITEMAP_NAME, "")
                edit.putString(Constants.PREFERENCE_SITEMAP_LABEL, "")
                edit.apply()

                onNoDefaultSitemap(preference)
                parentActivity.resultIntent.putExtra(RESULT_EXTRA_SITEMAP_CLEARED, true)
                true
            }

            ringtonePref.setOnPreferenceChangeListener { pref, newValue ->
                updateRingtonePreferenceSummary(pref, newValue as String?)
                true
            }

            vibrationPref.setOnPreferenceChangeListener { pref, newValue ->
                updateVibrationPreferenceIcon(pref, newValue as String?)
                true
            }

            ringtoneVibrationPref.setOnPreferenceClickListener { preference ->
                val i = Intent(android.provider.Settings.ACTION_SETTINGS)
                i.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                i.putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                startActivity(i)
                true
            }

            viewLogPref.setOnPreferenceClickListener { preference ->
                val logIntent = Intent(preference.context, LogActivity::class.java)
                startActivity(logIntent)
                true
            }

            val ps = preferenceScreen

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                Log.d(TAG, "Removing fullscreen pref as device isn't running Kitkat or higher")
                getParent(fullscreenPreference)!!.removePreference(fullscreenPreference)
            } else {
                fullscreenPreference.setOnPreferenceChangeListener { _, newValue ->
                    (activity as AbstractBaseActivity).checkFullscreen(newValue as Boolean)
                    true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Removing notification prefs for < 25")
                getParent(ringtonePref)!!.removePreference(ringtonePref)
                getParent(vibrationPref)!!.removePreference(vibrationPref)
            } else {
                Log.d(TAG, "Removing notification prefs for >= 25")
                getParent(ringtoneVibrationPref)!!.removePreference(ringtoneVibrationPref)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, "Removing alarm clock pref")
                preferenceScreen.removePreference(alarmClockPref)
            } else {
                updateAlarmClockPreferenceIcon(alarmClockPref,
                        ItemUpdatingPreference.parseValue(
                                getPreferenceString(alarmClockPref, null)))
                updateAlarmClockPreferenceSummary(alarmClockPref,
                        getPreferenceString(sendDeviceInfoPrefixPref, ""),
                        ItemUpdatingPreference.parseValue(
                                getPreferenceString(alarmClockPref, null)))
                alarmClockPref.setOnPreferenceChangeListener { preference, newValue ->
                    val prefix = getPreferenceString(sendDeviceInfoPrefixPref, "")
                    updateAlarmClockPreferenceIcon(preference, newValue)
                    val value = newValue as Pair<Boolean, String>
                    updateAlarmClockPreferenceSummary(preference, prefix, value)
                    true
                }
            }

            sendDeviceInfoPrefixPref.setOnPreferenceChangeListener { _, newValue ->
                val item = ItemUpdatingPreference.parseValue(getPreferenceString(alarmClockPref, null))
                updateAlarmClockPreferenceSummary(alarmClockPref, newValue as String, item)
                true
            }

            val flags = activity.intent.getParcelableExtra<ServerProperties>(START_EXTRA_SERVER_PROPERTIES)?.flags
                    ?: preferenceScreen.sharedPreferences.getInt(Constants.PREV_SERVER_FLAGS, 0)

            if (flags and ServerProperties.SERVER_FLAG_ICON_FORMAT_SUPPORT == 0) {
                val iconFormatPreference = ps.findPreference(Constants.PREFERENCE_ICON_FORMAT)
                getParent(iconFormatPreference)!!.removePreference(iconFormatPreference)
            }
            if (flags and ServerProperties.SERVER_FLAG_CHART_SCALING_SUPPORT == 0) {
                val chartScalingPreference = ps.findPreference(Constants.PREFERENCE_CHART_SCALING)
                getParent(chartScalingPreference)!!.removePreference(chartScalingPreference)
            }
        }

        /**
         * @author https://stackoverflow.com/a/17633389
         */
        private fun getParent(preference: Preference): PreferenceGroup? {
            return getParent(preferenceScreen, preference)
        }

        /**
         * @author https://stackoverflow.com/a/17633389
         */
        private fun getParent(root: PreferenceGroup, preference: Preference): PreferenceGroup? {
            for (i in 0 until root.preferenceCount) {
                val p = root.getPreference(i)
                if (p === preference) {
                    return root
                }
                if (p is PreferenceGroup) {
                    val parent = getParent(p, preference)
                    if (parent != null) {
                        return parent
                    }
                }
            }
            return null
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

        private fun updateAlarmClockPreferenceSummary(pref: Preference, prefix: String?, item: Pair<*, *>?) {
            val value = item as Pair<Boolean, String>?
            pref.summary = if (value != null && value.first)
                getString(R.string.settings_alarm_clock_summary_on,
                        (prefix ?: "") + value.second)
            else
                getString(R.string.settings_alarm_clock_summary_off)
        }

        private fun updateAlarmClockPreferenceIcon(pref: Preference, newValue: Any?) {
            val value = newValue as Pair<Boolean, String>?
            pref.setIcon(if (value != null && value.first)
                R.drawable.ic_alarm_grey_24dp
            else
                R.drawable.ic_alarm_off_grey_24dp)
        }

        private fun updateConnectionSummary(subscreenPrefKey: String, urlPrefKey: String,
                                            userPrefKey: String, passwordPrefKey: String) {
            val pref = findPreference(subscreenPrefKey)
            val url = getPreferenceString(urlPrefKey, "") as String
            val host = url.toUri().host?.replace("myopenhab.org", "myopenHAB") ?: ""
            val summary: String
            if (host.isEmpty()) {
                summary = getString(R.string.info_not_set)
            } else if (isConnectionSecure(url, getPreferenceString(userPrefKey, ""),
                            getPreferenceString(passwordPrefKey, ""))) {
                summary = getString(R.string.settings_connection_summary, host)
            } else {
                summary = getString(R.string.settings_insecure_connection_summary, host)
            }
            pref.summary = summary
        }
    }

    internal abstract class ConnectionSettingsFragment : AbstractSettingsFragment() {
        private lateinit var urlPreference: Preference
        private lateinit var userNamePreference: Preference
        private lateinit var passwordPreference: Preference

        protected fun initPreferences(urlPrefKey: String, userNamePrefKey: String,
                                      passwordPrefKey: String, @StringRes urlSummaryFormatResId: Int) {
            urlPreference = initEditor(urlPrefKey, R.drawable.ic_earth_grey_24dp, { value ->
                val actualValue = if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
                getString(urlSummaryFormatResId, actualValue)
            })
            userNamePreference = initEditor(userNamePrefKey, R.drawable.ic_person_outline_grey_24dp,
                    { value -> if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set) })
            passwordPreference = initEditor(passwordPrefKey, R.drawable.ic_shield_key_outline_grey_24dp, { value ->
                @StringRes val resId = if (value.isNullOrEmpty())
                    R.string.info_not_set
                else if (PreferencesActivity.AbstractSettingsFragment.isWeakPassword(value))
                    R.string.settings_openhab_password_summary_weak
                else
                    R.string.settings_openhab_password_summary_strong
                getString(resId)
            })

            updateIconColors(getPreferenceString(urlPrefKey, ""),
                    getPreferenceString(userNamePrefKey, ""),
                    getPreferenceString(passwordPrefKey, ""))
        }


        private fun initEditor(key: String, @DrawableRes iconResId: Int,
                               summaryGenerator: (value: String?) -> CharSequence): Preference {
            val preference = preferenceScreen.findPreference(key)
            preference.icon = DrawableCompat.wrap(
                    ContextCompat.getDrawable(activity, iconResId)!!)
            preference.setOnPreferenceChangeListener { pref, newValue ->
                updateIconColors(getActualValue(pref, newValue, urlPreference),
                        getActualValue(pref, newValue, userNamePreference),
                        getActualValue(pref, newValue, passwordPreference))
                pref.summary = summaryGenerator(newValue as String)
                true
            }
            preference.summary = summaryGenerator(getPreferenceString(key, ""))
            return preference
        }

        private fun getActualValue(pref: Preference, newValue: Any, reference: Preference?): String? {
            return if (pref === reference) newValue as String else getPreferenceString(reference, "")
        }

        private fun updateIconColors(url: String?, userName: String?, password: String?) {
            updateIconColor(urlPreference, {
                if (isConnectionHttps(url))
                    R.color.pref_icon_green
                else if (!url.isNullOrEmpty())
                    R.color.pref_icon_red
                else
                null
            })
            updateIconColor(userNamePreference, {
                if (url.isNullOrEmpty())
                    null
                else if (userName.isNullOrEmpty())
                    R.color.pref_icon_red
                else
                    R.color.pref_icon_green
            })
            updateIconColor(passwordPreference, {
                if (url.isNullOrEmpty())
                    null
                else if (password.isNullOrEmpty())
                    R.color.pref_icon_red
                else if (PreferencesActivity.AbstractSettingsFragment.isWeakPassword(password))
                    R.color.pref_icon_orange
                else
                    R.color.pref_icon_green
            })
        }

        private fun updateIconColor(pref: Preference, colorGenerator: () -> Int?) {
            val icon = pref.icon
            val colorResId = colorGenerator()
            if (colorResId != null) {
                DrawableCompat.setTint(icon, ContextCompat.getColor(pref.context, colorResId))
            } else {
                DrawableCompat.setTintList(icon, null)
            }
        }
    }

    internal class LocalConnectionSettingsFragment : ConnectionSettingsFragment() {
        override val titleResId: Int
            @StringRes
            get() = R.string.settings_openhab_connection

        override fun updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.local_connection_preferences)
            initPreferences(Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                    Constants.PREFERENCE_LOCAL_PASSWORD, R.string.settings_openhab_url_summary)
        }
    }

    internal class RemoteConnectionSettingsFragment : ConnectionSettingsFragment() {
        override val titleResId: Int
            @StringRes
            get() = R.string.settings_openhab_alt_connection

        override fun updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.remote_connection_preferences)
            initPreferences(Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                    Constants.PREFERENCE_REMOTE_PASSWORD, R.string.settings_openhab_alturl_summary)
        }
    }

    companion object {
        val RESULT_EXTRA_THEME_CHANGED = "theme_changed"
        val RESULT_EXTRA_SITEMAP_CLEARED = "sitemap_cleared"
        val START_EXTRA_SERVER_PROPERTIES = "server_properties"
        private val STATE_KEY_RESULT = "result"

        private val TAG = PreferencesActivity::class.java.simpleName
    }
}
