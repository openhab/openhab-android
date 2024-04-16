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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.work.WorkManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.model.toWifiSsids
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.preference.widgets.SslClientCertificatePreference
import org.openhab.habdroid.ui.preference.widgets.WifiSsidInputPreference
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPreference
import org.openhab.habdroid.util.getPrimaryServerId
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

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
            config = ServerConfiguration(
                config.id,
                newValue as String,
                config.localPath,
                config.remotePath,
                config.sslClientCert,
                config.defaultSitemap,
                config.wifiSsids,
                config.restrictToWifiSsids,
                config.frontailUrl
            )
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
            config = ServerConfiguration(
                config.id,
                config.name,
                config.localPath,
                config.remotePath,
                newValue as String?,
                config.defaultSitemap,
                config.wifiSsids,
                config.restrictToWifiSsids,
                config.frontailUrl
            )
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
                config = ServerConfiguration(
                    config.id,
                    config.name,
                    config.localPath,
                    config.remotePath,
                    config.sslClientCert,
                    config.defaultSitemap,
                    ssids,
                    restrictToSsids,
                    config.frontailUrl
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
            config = ServerConfiguration(
                config.id,
                config.name,
                config.localPath,
                config.remotePath,
                config.sslClientCert,
                config.defaultSitemap,
                config.wifiSsids,
                config.restrictToWifiSsids,
                newUrl
            )
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

    private fun handleNoDefaultSitemap(pref: Preference) {
        pref.isEnabled = false
        pref.setSummary(R.string.settings_no_default_sitemap)
    }

    fun onPathChanged(key: String, path: ServerPath) {
        config = if (key == "local") {
            ServerConfiguration(
                config.id,
                config.name,
                path,
                config.remotePath,
                config.sslClientCert,
                config.defaultSitemap,
                config.wifiSsids,
                config.restrictToWifiSsids,
                config.frontailUrl
            )
        } else {
            ServerConfiguration(
                config.id,
                config.name,
                config.localPath,
                path,
                config.sslClientCert,
                config.defaultSitemap,
                config.wifiSsids,
                config.restrictToWifiSsids,
                config.frontailUrl
            )
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
        fun newInstance(config: ServerConfiguration): ServerEditorFragment {
            val f = ServerEditorFragment()
            f.arguments = bundleOf("config" to config)
            return f
        }

        @VisibleForTesting
        fun beautifyUrl(url: String): String {
            val host = url.toHttpUrlOrNull()?.host ?: url
            return if (HttpClient.isMyOpenhab(host)) "myopenHAB" else host
        }
    }
}
