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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.wear.WearHelper
import org.openhab.habdroid.model.DefaultSitemap
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.sortedWithDefaultName
import org.openhab.habdroid.model.toWifiSsids
import org.openhab.habdroid.ui.WebViewManager
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.preference.widgets.SslClientCertificatePreference
import org.openhab.habdroid.ui.preference.widgets.WifiSsidInputPreference
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getPreference
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getWebViewManager
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.optStringOrNull
import org.openhab.habdroid.util.parcelable
import org.openhab.habdroid.util.putPrimaryServerId
import org.openhab.habdroid.util.updateDefaultSitemap

class ServerEditorFragment :
    AbstractSettingsFragment(),
    PreferencesActivity.ConfirmationDialogFragment.Callback,
    PreferencesActivity.ConfirmLeaveDialogFragment.Callback,
    MenuProvider {
    private lateinit var config: ServerConfiguration
    private lateinit var initialConfig: ServerConfiguration
    private var markAsPrimary = false

    override val titleResId: Int get() = R.string.settings_edit_server

    override fun onCreate(savedInstanceState: Bundle?) {
        config = requireArguments().parcelable<ServerConfiguration>("config")!!
        initialConfig = config
        super.onCreate(savedInstanceState)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (initialConfig != config) {
                    PreferencesActivity.ConfirmLeaveDialogFragment().show(
                        childFragmentManager,
                        "dialog_confirm_leave"
                    )
                } else {
                    isEnabled = false
                    parentActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        parentActivity.onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.server_editor, menu)
        val deleteItem = menu.findItem(R.id.delete)
        deleteItem.isVisible = prefs.getConfiguredServerIds().contains(config.id)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.save -> {
            saveAndQuit()
            true
        }

        R.id.delete -> {
            PreferencesActivity.ConfirmationDialogFragment.show(
                childFragmentManager,
                R.string.settings_server_confirm_deletion,
                R.string.delete,
                "delete_server_confirmation"
            )
            true
        }

        else -> false
    }

    private fun saveAndQuit() {
        if (config.name.isEmpty() || (config.localPath == null && config.remotePath == null)) {
            parentActivity.showSnackbar(
                PreferencesActivity.SNACKBAR_TAG_MISSING_PREFS,
                R.string.settings_server_at_least_name_and_connection
            )
            return
        }
        config.saveToPrefs(prefs, secretPrefs)
        if (markAsPrimary) {
            prefs.edit {
                putPrimaryServerId(config.id)
            }
        }
        parentActivity.invalidateOptionsMenu()
        parentFragmentManager.popBackStack() // close ourself
    }

    override fun onConfirmed(tag: String?) = when (tag) {
        "delete_server_confirmation" -> {
            config.removeFromPrefs(prefs, secretPrefs)
            preferenceManager.context.getWebViewManager().deleteDataForServer(config.id)
            WorkManager.getInstance(preferenceManager.context).apply {
                cancelAllWorkByTag(BackgroundTasksManager.buildWorkerTagForServer(config.id))
                pruneWork()
            }
            parentFragmentManager.popBackStack() // close ourself
        }

        else -> {}
    }

    override fun onLeaveAndSave() {
        saveAndQuit()
    }

    override fun onLeaveAndDiscard() {
        parentActivity.invalidateOptionsMenu()
        parentFragmentManager.popBackStack() // close ourself
    }

    override fun onStart() {
        super.onStart()
        updateConnectionSummary("local", config.localPath)
        updateConnectionSummary("remote", config.remotePath)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_server)

        val serverNamePref = getPreference("name") as EditTextPreference
        serverNamePref.text = config.name
        serverNamePref.setOnPreferenceChangeListener { _, newValue ->
            config = ServerConfiguration.createFrom(config, name = newValue as String)
            parentActivity.invalidateOptionsMenu()
            true
        }

        val localConnPref = getPreference("local")
        localConnPref.setOnPreferenceClickListener {
            parentActivity.openSubScreen(
                ConnectionSettingsFragment.newInstance(
                    localConnPref.key,
                    config.localPath,
                    R.xml.preferences_local_connection,
                    R.string.settings_openhab_connection,
                    R.string.settings_openhab_url_summary,
                    this
                )
            )
            false
        }

        val remoteConnPref = getPreference("remote")
        remoteConnPref.setOnPreferenceClickListener {
            parentActivity.openSubScreen(
                ConnectionSettingsFragment.newInstance(
                    remoteConnPref.key,
                    config.remotePath,
                    R.xml.preferences_remote_connection,
                    R.string.settings_openhab_alt_connection,
                    R.string.settings_openhab_alturl_summary,
                    this
                )
            )
            false
        }

        val clientCertPref = getPreference("clientcert") as SslClientCertificatePreference
        clientCertPref.setOnPreferenceChangeListener { _, newValue ->
            config = ServerConfiguration.createFrom(config, sslClientCert = newValue as String?)
            true
        }
        clientCertPref.setValue(config.sslClientCert)

        val clearDefaultSitemapPref = getPreference(PrefKeys.CLEAR_DEFAULT_SITEMAP)
        if (config.defaultSitemap?.name.isNullOrEmpty()) {
            handleNoDefaultSitemap(clearDefaultSitemapPref)
        } else {
            clearDefaultSitemapPref.summary = getString(
                R.string.settings_current_default_sitemap,
                config.defaultSitemap?.label.orEmpty()
            )
        }
        clearDefaultSitemapPref.setOnPreferenceClickListener { preference ->
            preference.sharedPreferences!!.updateDefaultSitemap(null, null, config.id)
            handleNoDefaultSitemap(preference)
            parentActivity.addResultFlag(PreferencesActivity.RESULT_EXTRA_SITEMAP_CLEARED)
            true
        }

        setupWearPreferences()

        val wifiSsidPref = getPreference("wifi_ssid") as WifiSsidInputPreference
        if (prefs.getConfiguredServerIds().isEmpty()) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.PRIMARY_SERVER_PREF)
            preferenceScreen.removePreferenceRecursively("wifi_ssid")
        } else {
            val primaryServerPref = getPreference(PrefKeys.PRIMARY_SERVER_PREF)
            updatePrimaryServerPrefState(primaryServerPref, config.id == prefs.getPrimaryServerId())
            primaryServerPref.setOnPreferenceClickListener {
                if (prefs.getConfiguredServerIds().contains(config.id)) {
                    prefs.edit {
                        putPrimaryServerId(config.id)
                    }
                } else {
                    markAsPrimary = true
                }
                updatePrimaryServerPrefState(primaryServerPref, true)
                true
            }

            wifiSsidPref.setValue(
                Pair(
                    config.wifiSsids?.joinToString("\n").orEmpty(),
                    config.restrictToWifiSsids
                )
            )
            wifiSsidPref.setOnPreferenceChangeListener { _, newValue ->
                @Suppress("UNCHECKED_CAST")
                val value = newValue as Pair<String, Boolean>
                val ssids = value.first.toWifiSsids()
                // Don't restrict if no SSID is set
                val restrictToSsids = if (ssids.isEmpty()) false else value.second
                config = ServerConfiguration.createFrom(
                    config,
                    wifiSsids = ssids,
                    restrictToWifiSsids = restrictToSsids
                )
                true
            }
        }

        val frontailUrlPref = getPreference("frontail_url") as EditTextPreference
        val summaryGenerator = { value: String? ->
            val actualValue = if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
            getString(R.string.frontail_url_summary, actualValue)
        }
        frontailUrlPref.summary = summaryGenerator(config.frontailUrl)
        frontailUrlPref.text = config.frontailUrl
        frontailUrlPref.setOnPreferenceChangeListener { _, newValue ->
            val newUrl = newValue as String?
            frontailUrlPref.summary = summaryGenerator(newUrl)
            config = ServerConfiguration.createFrom(config, frontailUrl = newUrl)
            true
        }

        val mainUiStartPagePref = getPreference("main_ui_start_page") as EditTextPreference
        mainUiStartPagePref.text = config.mainUiStartPage
        mainUiStartPagePref.setOnPreferenceChangeListener { _, newValue ->
            var newPage = newValue as String?
            if (!newPage.isNullOrEmpty() && !newPage.startsWith("/")) {
                newPage = "/$newPage"
                mainUiStartPagePref.text = newPage
            }
            config = ServerConfiguration.createFrom(config, mainUiStartPage = newPage)
            true
        }

        val advancedPrefs = getPreference("advanced_prefs")
        advancedPrefs.setOnPreferenceClickListener {
            advancedPrefs.isVisible = false
            clientCertPref.isVisible = true
            wifiSsidPref.isVisible = true
            frontailUrlPref.isVisible = true
            false
        }
    }

    private fun updatePrimaryServerPrefState(pref: Preference, isPrimary: Boolean) {
        pref.summary = if (isPrimary) {
            getString(R.string.settings_server_primary_summary_is_primary)
        } else {
            val nameOfPrimary = ServerConfiguration.load(prefs, secretPrefs, prefs.getPrimaryServerId())?.name
            getString(R.string.settings_server_primary_summary_is_not_primary, nameOfPrimary)
        }
    }

    /**
     * Only the primary server is used on the watch
     */
    private fun setupWearPreferences() {
        val wearCategory = getPreference(PREF_WEAR)
        val sitemapPref = getPreference(PREF_WEAR_SITEMAP)
        val groupPref = getPreference(PREF_WEAR_COMPLICATION_GROUP)
        if (WearHelper.IS_SUPPORTED && config.id == prefs.getPrimaryServerId()) {
            lifecycleScope.launch {
                wearCategory.isVisible = WearHelper.hasWatchWithApp(preferenceManager.context)
            }
        }
        updateWearSummaries()

        sitemapPref.setOnPreferenceClickListener {
            loadFromPrimaryServer { connection ->
                val props = ServerProperties.fetch(connection) as? ServerProperties.Companion.PropsSuccess
                    ?: return@loadFromPrimaryServer null
                val sitemaps = props.props.sitemaps.sortedWithDefaultName(config.defaultSitemap?.name.orEmpty())
                val options = listOf(null to getString(R.string.settings_wear_sitemap_default)) +
                    sitemaps.map { sitemap -> DefaultSitemap(sitemap.name, sitemap.label) to sitemap.label }
                showWearChoiceDialog(R.string.settings_wear_sitemap, options, config.wearSitemap) { selected ->
                    config = ServerConfiguration.createFrom(config, wearSitemap = selected)
                }
            }
            true
        }

        groupPref.setOnPreferenceClickListener {
            loadFromPrimaryServer { connection ->
                val response = connection.httpClient
                    .get("rest/items?type=Group&fields=name,label")
                    .asText()
                    .response
                val groups = JSONArray(response)
                    .map { group -> group.getString("name") to group.optStringOrNull("label") }
                    .sortedBy { (name, label) -> (label ?: name).lowercase() }
                val options = listOf(null to getString(R.string.settings_wear_complication_group_all)) +
                    groups.map { (name, label) -> name to (label?.let { "$it ($name)" } ?: name) }
                showWearChoiceDialog(
                    R.string.settings_wear_complication_group,
                    options,
                    config.wearComplicationGroup
                ) { selected ->
                    config = ServerConfiguration.createFrom(config, wearComplicationGroup = selected)
                }
            }
            true
        }
    }

    private fun updateWearSummaries() {
        getPreference(PREF_WEAR_SITEMAP).summary =
            config.wearSitemap?.label ?: getString(R.string.settings_wear_sitemap_default)
        getPreference(PREF_WEAR_COMPLICATION_GROUP).summary = config.wearComplicationGroup
            ?.let { group -> getString(R.string.settings_wear_complication_group_summary, group) }
            ?: getString(R.string.settings_wear_complication_group_all)
    }

    /**
     * Loads data from the primary server, which is the server shown on the watch
     *
     * @param block Loads the data and shows it, returns null if loading failed
     */
    private fun loadFromPrimaryServer(block: suspend (Connection) -> Unit?) {
        val connection = preferenceManager.context.getConnectionFactory().currentPrimary?.conn?.connection
        lifecycleScope.launch {
            val result = try {
                connection?.let { block(it) }
            } catch (e: HttpClient.HttpException) {
                Log.d(TAG, "Loading data for Wear OS failed", e)
                null
            } catch (e: JSONException) {
                Log.d(TAG, "Parsing data for Wear OS failed", e)
                null
            }
            if (result == null) {
                parentActivity.showSnackbar(SNACKBAR_TAG_WEAR_LOAD_FAILED, R.string.settings_wear_load_failed)
            }
        }
    }

    private fun <T> showWearChoiceDialog(
        @StringRes titleResId: Int,
        options: List<Pair<T?, String>>,
        selected: T?,
        onSelected: (T?) -> Unit
    ) {
        val selectedIndex = options.indexOfFirst { (value, _) -> value == selected }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(preferenceManager.context)
            .setTitle(titleResId)
            .setSingleChoiceItems(options.map { (_, label) -> label }.toTypedArray(), selectedIndex) { dialog, which ->
                onSelected(options[which].first)
                updateWearSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleNoDefaultSitemap(pref: Preference) {
        pref.isEnabled = false
        pref.setSummary(R.string.settings_no_default_sitemap)
    }

    fun onPathChanged(key: String, path: ServerPath) {
        config = if (key == "local") {
            ServerConfiguration.createFrom(config, localPath = path)
        } else {
            ServerConfiguration.createFrom(config, remotePath = path)
        }
        parentActivity.invalidateOptionsMenu()
    }

    private fun updateConnectionSummary(key: String, path: ServerPath?) {
        fun insecureMessage(beautyUrl: String, reason: Int) =
            getString(R.string.settings_insecure_connection_summary, beautyUrl, getString(reason))

        val pref = getPreference(key)
        val beautyUrl = beautifyUrl(path?.url.orEmpty())
        pref.summary = when {
            path == null || path.url.isEmpty() ->
                getString(R.string.info_not_set)

            path.url.toHttpUrlOrNull()?.isHttps == false ->
                insecureMessage(beautyUrl, R.string.settings_insecure_connection_no_https)

            !path.hasAuthentication() && config.sslClientCert == null ->
                insecureMessage(beautyUrl, R.string.settings_insecure_connection_no_auth)

            isWeakPassword(path.password) ->
                insecureMessage(beautyUrl, R.string.settings_openhab_password_summary_weak)

            else ->
                getString(R.string.settings_connection_summary, beautyUrl)
        }
    }

    companion object {
        private val TAG = ServerEditorFragment::class.java.simpleName
        private const val PREF_WEAR = "wear"
        private const val PREF_WEAR_SITEMAP = "wear_sitemap"
        private const val PREF_WEAR_COMPLICATION_GROUP = "wear_complication_group"
        private const val SNACKBAR_TAG_WEAR_LOAD_FAILED = "wear_load_failed"

        fun newInstance(config: ServerConfiguration): ServerEditorFragment {
            val f = ServerEditorFragment()
            f.arguments = Bundle().apply {
                putParcelable("config", config)
            }
            return f
        }

        @VisibleForTesting
        fun beautifyUrl(url: String): String {
            val host = url.toHttpUrlOrNull()?.host ?: url
            return if (HttpClient.isMyOpenhab(host)) "myopenHAB" else host
        }
    }
}
