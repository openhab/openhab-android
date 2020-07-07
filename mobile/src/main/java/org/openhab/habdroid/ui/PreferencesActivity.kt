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

import android.app.Activity
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
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.commit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.forEachIndexed
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.background.BroadcastEventListenerService
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.background.tiles.TileData
import org.openhab.habdroid.background.tiles.getTileData
import org.openhab.habdroid.background.tiles.putTileData
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.CustomInputTypePreference
import org.openhab.habdroid.ui.preference.ItemUpdatingPreference
import org.openhab.habdroid.ui.preference.NotificationPollingPreference
import org.openhab.habdroid.ui.preference.TileItemAndStatePreference
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getNotificationTone
import org.openhab.habdroid.util.getPreference
import org.openhab.habdroid.util.getPrefixForBgTasks
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.getStringOrEmpty
import org.openhab.habdroid.util.getStringOrFallbackIfEmpty
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.hasPermissions
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
            val fragment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                intent.action == TileService.ACTION_QS_TILE_PREFERENCES) {
                TileOverviewFragment()
            } else {
                MainSettingsFragment()
            }
            supportFragmentManager.commit {
                add(R.id.activity_content, fragment)
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
            return prefs.getStringOrEmpty(PrefKeys.SSL_CLIENT_CERT).isNotEmpty()
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
            if (preference is CustomDialogPreference) {
                showDialog(preference.createDialog())
            } else {
                super.onDisplayPreferenceDialog(preference)
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

    class MainSettingsFragment : AbstractSettingsFragment(), ConnectionFactory.UpdateListener {
        override val titleResId: Int @StringRes get() = R.string.action_settings
        @ColorInt var previousColor: Int = 0

        private var notificationPollingPref: NotificationPollingPreference? = null
        private var notificationStatusHint: Preference? = null

        override fun onStart() {
            super.onStart()
            updateConnectionSummary(PrefKeys.SUBSCREEN_LOCAL_CONNECTION,
                PrefKeys.LOCAL_URL, PrefKeys.LOCAL_USERNAME,
                PrefKeys.LOCAL_PASSWORD)
            updateConnectionSummary(PrefKeys.SUBSCREEN_REMOTE_CONNECTION,
                PrefKeys.REMOTE_URL, PrefKeys.REMOTE_USERNAME,
                PrefKeys.REMOTE_PASSWORD)
            updateScreenLockStateAndSummary(prefs.getStringOrFallbackIfEmpty(PrefKeys.SCREEN_LOCK,
                getString(R.string.settings_screen_lock_off_value)))
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

            val localConnPref = getPreference(PrefKeys.SUBSCREEN_LOCAL_CONNECTION)
            val remoteConnPref = getPreference(PrefKeys.SUBSCREEN_REMOTE_CONNECTION)
            val sendDeviceInfoPref = getPreference(PrefKeys.SUBSCREEN_SEND_DEVICE_INFO)
            notificationPollingPref =
                getPreference(PrefKeys.FOSS_NOTIFICATIONS_ENABLED) as NotificationPollingPreference
            notificationStatusHint = getPreference(PrefKeys.NOTIFICATION_STATUS_HINT)
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
            val tilePref = getPreference(PrefKeys.SUBSCREEN_TILE)
            val prefs = preferenceScreen.sharedPreferences

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                val dataSaverPref = getPreference(PrefKeys.DATA_SAVER) as SwitchPreference
                dataSaverPref.setSwitchTextOff(R.string.data_saver_off_pre_n)
            }

            val currentDefaultSitemap = prefs.getStringOrNull(PrefKeys.SITEMAP_NAME)
            val currentDefaultSitemapLabel = prefs.getStringOrEmpty(PrefKeys.SITEMAP_LABEL)
            if (currentDefaultSitemap.isNullOrEmpty()) {
                onNoDefaultSitemap(clearDefaultSitemapPref)
            } else {
                clearDefaultSitemapPref.summary = getString(
                    R.string.settings_current_default_sitemap, currentDefaultSitemapLabel)
            }

            updateRingtonePreferenceSummary(ringtonePref, prefs.getNotificationTone())
            updateVibrationPreferenceIcon(vibrationPref,
                prefs.getStringOrNull(PrefKeys.NOTIFICATION_VIBRATION))

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

            if (Util.isFlavorFoss) {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tilePref.setOnPreferenceClickListener {
                    parentActivity.openSubScreen(TileOverviewFragment())
                    false
                }
                updateTileSummary()
            } else {
                preferenceScreen.removePreferenceRecursively(PrefKeys.SUBSCREEN_TILE)
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

        private fun updateNotificationStatusSummaries() {
            parentActivity.launch {
                notificationPollingPref?.updateSummary()
                notificationStatusHint?.apply {
                    summary = CloudMessagingHelper.getPushNotificationStatus(this.context).message
                }
            }
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
            val url = prefs.getStringOrEmpty(urlPrefKey)
            val beautyUrl = beautifyUrl(url)
            val userName = secretPrefs.getStringOrNull(userPrefKey)
            val password = secretPrefs.getStringOrNull(passwordPrefKey)
            val summary = when {
                url.isEmpty() -> getString(R.string.info_not_set)
                isConnectionSecure(url, userName, password) ->
                    getString(R.string.settings_connection_summary, beautyUrl)
                else -> getString(R.string.settings_insecure_connection_summary, beautyUrl)
            }
            pref.summary = summary
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

        override fun onAvailableConnectionChanged() {
            updateNotificationStatusSummaries()
        }

        override fun onCloudConnectionChanged(connection: CloudConnection?) {
            updateNotificationStatusSummaries()
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
            preference.summary = summaryGenerator(prefsForValue.getStringOrEmpty(key))
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

            val prefixHint = getPreference(PrefKeys.DEV_ID_PREFIX_BG_TASKS)
            val foregroundServicePref = getPreference(PrefKeys.SEND_DEVICE_INFO_FOREGROUND_SERVICE)
            phoneStatePref = getPreference(PrefKeys.SEND_PHONE_STATE) as ItemUpdatingPreference
            wifiSsidPref = getPreference(PrefKeys.SEND_WIFI_SSID) as ItemUpdatingPreference

            phoneStatePref.setOnPreferenceChangeListener { preference, newValue ->
                requestPermissionIfRequired(
                    preference.context,
                    newValue,
                    BackgroundTasksManager.getRequiredPermissionsForTask(PrefKeys.SEND_PHONE_STATE),
                    PERMISSIONS_REQUEST_FOR_CALL_STATE
                )
                true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wifiSsidPref.setSummaryOn(getString(R.string.settings_wifi_ssid_summary_on_location_on))
            }
            wifiSsidPref.setOnPreferenceChangeListener { preference, newValue ->
                requestPermissionIfRequired(
                    preference.context,
                    newValue,
                    BackgroundTasksManager.getRequiredPermissionsForTask(PrefKeys.SEND_WIFI_SSID),
                    PERMISSIONS_REQUEST_FOR_WIFI_NAME
                )

                true
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                preferenceScreen.removePreferenceRecursively(PrefKeys.SEND_DND_MODE)
                preferenceScreen.removePreferenceRecursively(PrefKeys.SEND_DEVICE_INFO_FOREGROUND_SERVICE)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                foregroundServicePref.setSummary(R.string.send_device_info_foreground_service_summary_pre_o)
            }

            foregroundServicePref.setOnPreferenceChangeListener { preference, newValue ->
                BroadcastEventListenerService.startOrStopService(preference.context, newValue as Boolean)
                true
            }

            BackgroundTasksManager.KNOWN_PERIODIC_KEYS.forEach { key ->
                findPreference<Preference>(key)?.setOnPreferenceChangeListener { preference, _ ->
                    BroadcastEventListenerService.startOrStopService(preference.context)
                    true
                }
            }

            val prefix = prefs.getPrefixForBgTasks()
            prefixHint.summary = if (prefix.isEmpty()) {
                prefixHint.context.getString(R.string.send_device_info_item_prefix_summary_not_set)
            } else {
                prefixHint.context.getString(R.string.send_device_info_item_prefix_summary, prefix)
            }
        }

        private fun requestPermissionIfRequired(
            context: Context,
            newValue: Any?,
            permissions: Array<String>?,
            requestCode: Int
        ) {
            @Suppress("UNCHECKED_CAST")
            val value = newValue as Pair<Boolean, String>
            if (value.first && permissions != null && !context.hasPermissions(permissions)) {
                Log.d(TAG, "Request $permissions permission")
                requestPermissions(permissions, requestCode)
            }
        }

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
            val context = phoneStatePref.context

            when (requestCode) {
                PERMISSIONS_REQUEST_FOR_CALL_STATE -> {
                    if (grantResults.firstOrNull { it != PackageManager.PERMISSION_GRANTED } != null) {
                        context.showToast(R.string.settings_phone_state_permission_denied, ToastType.ERROR)
                        phoneStatePref.setValue(checked = false)
                    } else {
                        BackgroundTasksManager.scheduleWorker(context, PrefKeys.SEND_PHONE_STATE)
                    }
                }
                PERMISSIONS_REQUEST_FOR_WIFI_NAME -> {
                    if (grantResults.firstOrNull { it != PackageManager.PERMISSION_GRANTED } != null) {
                        context.showToast(R.string.settings_wifi_ssid_permission_denied, ToastType.ERROR)
                        wifiSsidPref.setValue(checked = false)
                    } else {
                        BackgroundTasksManager.scheduleWorker(context, PrefKeys.SEND_WIFI_SSID)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    internal class TileOverviewFragment : AbstractSettingsFragment() {
        override val titleResId: Int @StringRes get() = R.string.tiles_for_quick_settings

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences_tile_overview)
            for (tileId in 1..AbstractTileService.TILE_COUNT) {
                val tilePref = Preference(context).apply {
                    key = "tile_$tileId"
                    title = getString(R.string.tile_number, tileId)
                    isPersistent = false
                }
                tilePref.setOnPreferenceClickListener {
                    parentActivity.openSubScreen(TileSettingsFragment.newInstance(tileId))
                    false
                }
                preferenceScreen.addPreference(tilePref)
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.forEachIndexed { index, preference ->
                // Index 0 is the hint
                if (index != 0) {
                    val data = prefs.getTileData(index)
                    val context = preference.context
                    preference.summary = data?.tileLabel ?: getString(R.string.tile_disabled)
                    preference.icon = if (data == null) {
                        null
                    } else {
                        ContextCompat.getDrawable(context, AbstractTileService.getIconRes(context, data.icon))?.apply {
                            mutate()
                            setTint(context.getColor(R.color.pref_icon_grey))
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    internal class TileSettingsFragment : AbstractSettingsFragment() {
        override val titleResId: Int @StringRes get() = R.string.tile
        private var tileId = 0

        private lateinit var enabledPref: SwitchPreferenceCompat
        private lateinit var itemAndStatePref: TileItemAndStatePreference
        private lateinit var namePref: CustomInputTypePreference
        private lateinit var iconPref: ListPreference
        private lateinit var requireUnlockPref: SwitchPreferenceCompat

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            tileId = arguments?.getInt("id") ?: throw AssertionError("No tile id specified")
            setHasOptionsMenu(true)

            val data = prefs.getTileData(tileId)
            enabledPref.isChecked = data != null
            if (data != null) {
                itemAndStatePref.item = data.item
                itemAndStatePref.label = data.label
                itemAndStatePref.state = data.state
                itemAndStatePref.mappedState = data.mappedState
                namePref.text = data.tileLabel
                iconPref.value = data.icon
                requireUnlockPref.isChecked = data.requireUnlock
            }
            iconPref.setOnPreferenceChangeListener { _, newValue ->
                updateIconPrefIcon(newValue as String)
                true
            }
            updateIconPrefIcon()
            updateItemAndStatePrefSummary()
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            super.onCreateOptionsMenu(menu, inflater)
            inflater.inflate(R.menu.tile_prefs, menu)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val context = preferenceManager.context
            when (item.itemId) {
                R.id.save -> {
                    Log.d(TAG, "Save tile $tileId")
                    val data: TileData? = if (enabledPref.isChecked) {
                        val itemName = itemAndStatePref.item
                        val label = itemAndStatePref.label
                        val state = itemAndStatePref.state
                        val mappedState = itemAndStatePref.mappedState
                        val tileLabel = namePref.text
                        val icon = iconPref.value
                        val requireUnlock = requireUnlockPref.isChecked
                        if (itemName.isNullOrEmpty() || state.isNullOrEmpty() || label.isNullOrEmpty() ||
                            tileLabel.isNullOrEmpty() || mappedState.isNullOrEmpty() || icon.isNullOrEmpty()) {
                            context.showToast(R.string.tile_error_saving, ToastType.ERROR)
                            return true
                        }
                        TileData(itemName, state, label, tileLabel, mappedState, icon, requireUnlock)
                    } else {
                        null
                    }

                    prefs.edit {
                        putTileData(tileId, data)
                    }
                    AbstractTileService.updateTile(context, tileId)
                    parentActivity.invalidateOptionsMenu()
                    parentFragmentManager.popBackStack() // close ourself
                    return true
                }
                else -> return super.onOptionsItemSelected(item)
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences_tile)
            enabledPref = findPreference("tile_show")!!
            itemAndStatePref = findPreference("tile_item_and_action")!!
            namePref = findPreference("tile_name")!!
            iconPref = findPreference("tile_icon")!!
            requireUnlockPref = findPreference("tile_require_unlock")!!

            namePref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            itemAndStatePref.setOnPreferenceClickListener {
                val intent = Intent(it.context, TileItemPickerActivity::class.java)
                intent.putExtra("item", itemAndStatePref.item)
                startActivityForResult(intent, RESULT_TILE_ITEM_PICKER)
                true
            }
        }

        private fun updateItemAndStatePrefSummary() {
            itemAndStatePref.summary = if (itemAndStatePref.label == null) {
                itemAndStatePref.context.getString(R.string.info_not_set)
            } else {
                "${itemAndStatePref.label} (${itemAndStatePref.item}): ${itemAndStatePref.mappedState}"
            }
        }

        private fun updateIconPrefIcon(newIcon: String = iconPref.value) {
            val context = iconPref.context
            iconPref.icon =
                ContextCompat.getDrawable(context, AbstractTileService.getIconRes(context, newIcon))?.apply {
                    mutate()
                    setTint(context.getColor(R.color.pref_icon_grey))
                }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            Log.d(TAG, "onActivityResult() requestCode = $requestCode, resultCode = $resultCode")
            if (requestCode == RESULT_TILE_ITEM_PICKER && resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "Setting itemAndStatePref data")
                itemAndStatePref.item = data.getStringExtra("item")
                itemAndStatePref.label = data.getStringExtra("label")
                itemAndStatePref.state = data.getStringExtra("state")
                itemAndStatePref.mappedState = data.getStringExtra("mappedState")
                updateItemAndStatePrefSummary()

                if (namePref.text.isNullOrEmpty()) {
                    namePref.text = itemAndStatePref.label
                }
                if (iconPref.value == null || iconPref.value == getString(R.string.tile_icon_openhab_value)) {
                    val selectedIcon = data.getStringExtra("icon") ?: "openhab_icon"
                    val preSelectIcon = if (selectedIcon.startsWith("parents")) {
                        R.string.tile_icon_people_value
                    } else if (selectedIcon.startsWith("boy") || selectedIcon.startsWith("girl")) {
                        R.string.tile_icon_child_value
                    } else if (selectedIcon.startsWith("baby")) {
                        R.string.tile_icon_baby_value
                    } else if (selectedIcon.startsWith("man")) {
                        R.string.tile_icon_man_value
                    } else if (selectedIcon.startsWith("women")) {
                        R.string.tile_icon_woman_value
                    } else {
                        when (selectedIcon) {
                            "screen" -> R.string.tile_icon_tv_value
                            "lightbulb", "light", "slider" -> R.string.tile_icon_bulb_value
                            "lock" -> R.string.tile_icon_lock_value
                            "time" -> R.string.tile_icon_clock_value
                            "house", "presence", "group" -> R.string.tile_icon_house_value
                            "microphone", "recorder" -> R.string.tile_icon_microphone_value
                            "colorpicker", "colorlight", "colorwheel", "rbg" -> R.string.tile_icon_color_palette_value
                            "battery", "batterylevel", "lowbattery" -> R.string.tile_icon_battery_value
                            "zoom" -> R.string.tile_icon_magnifier_value
                            "garden" -> R.string.tile_icon_tree_value
                            "network" -> R.string.tile_icon_wifi_value
                            "shield" -> R.string.tile_icon_shield_value
                            "bedroom", "bedroom_blue", "bedroom_orange", "bedroom_red" -> R.string.tile_icon_bed_value
                            "settings" -> R.string.tile_icon_settings_value
                            "bath", "toilet" -> R.string.tile_icon_bath_value
                            "blinds", "rollershutter" -> R.string.tile_icon_roller_shutter_value
                            "camera" -> R.string.tile_icon_camera_value
                            "wallswitch" -> R.string.tile_icon_light_switch_value
                            "garage", "garagedoor", "garage_detached", "garage_detached_selected" ->
                                R.string.tile_icon_garage_value
                            "switch" -> R.string.tile_icon_switch_value
                            "sofa" -> R.string.tile_icon_sofa_value
                            else -> R.string.tile_icon_openhab_value
                        }
                    }
                    iconPref.value = getString(preSelectIcon)
                    updateIconPrefIcon()
                }
            }
        }

        companion object {
            fun newInstance(id: Int): TileSettingsFragment {
                val f = TileSettingsFragment()
                val args = bundleOf("id" to id)
                f.arguments = args
                return f
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
        private const val PERMISSIONS_REQUEST_FOR_CALL_STATE = 0
        private const val PERMISSIONS_REQUEST_FOR_WIFI_NAME = 1
        private const val RESULT_TILE_ITEM_PICKER = 0

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

interface CustomDialogPreference {
    fun createDialog(): DialogFragment
}
