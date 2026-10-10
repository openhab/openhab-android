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

import android.content.Context
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.parcelable

class ConnectionSettingsFragment : AbstractSettingsFragment() {
    override val titleResId: Int @StringRes get() = requireArguments().getInt("title")

    private lateinit var urlPreference: EditTextPreference
    private lateinit var authMethodPreference: ListPreference
    private lateinit var userNamePreference: EditTextPreference
    private lateinit var passwordPreference: EditTextPreference
    private lateinit var apiTokenPreference: EditTextPreference
    private lateinit var apiTokenInfoPreference: Preference
    private lateinit var basicAuthInfoPreference: Preference
    private lateinit var parent: ServerEditorFragment
    private lateinit var path: ServerPath

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parent = parentFragmentManager.getFragment(requireArguments(), "parent") as ServerEditorFragment
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(requireArguments().getInt("prefs"))

        path = requireArguments().parcelable<ServerPath>("path")
            ?: ServerPath("", null, null)

        urlPreference = initEditor("url", path.url, R.drawable.ic_earth_grey_24dp) { value ->
            val actualValue = if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
            getString(requireArguments().getInt("urlsummary"), actualValue)
        }

        val initialAuthMethod = when {
            path.isApiToken() -> AuthMethod.TOKEN

            path.url.isNotEmpty() && path.userName.isNullOrEmpty() && path.password.isNullOrEmpty() ->
                AuthMethod.NONE

            else -> AuthMethod.BASIC
        }
        authMethodPreference = preferenceScreen.findPreference("auth_method")!!
        authMethodPreference.value = initialAuthMethod.value
        authMethodPreference.setOnPreferenceChangeListener { pref, newValue ->
            onValuesChanged(pref, newValue as String)
            true
        }

        val isApiToken = initialAuthMethod == AuthMethod.TOKEN
        userNamePreference = initEditor(
            "username",
            path.userName.takeUnless { isApiToken },
            R.drawable.ic_person_outline_grey_24dp
        ) { value ->
            if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
        }
        passwordPreference = initEditor(
            "password",
            path.password.takeUnless { isApiToken },
            R.drawable.ic_shield_key_outline_grey_24dp
        ) { value ->
            getString(
                when {
                    value.isNullOrEmpty() -> R.string.info_not_set
                    isWeakPassword(value) -> R.string.settings_openhab_password_summary_weak
                    else -> R.string.settings_openhab_password_summary_strong
                }
            )
        }
        apiTokenPreference = initEditor(
            "api_token",
            path.userName.takeIf { isApiToken },
            R.drawable.ic_shield_key_outline_grey_24dp
        ) { value ->
            getString(
                if (value.isNullOrEmpty()) R.string.info_not_set else R.string.settings_openhab_api_token_summary_set
            )
        }
        apiTokenInfoPreference = preferenceScreen.findPreference("api_token_hint")!!
        basicAuthInfoPreference = preferenceScreen.findPreference("basic_auth_hint")!!

        updateAuthMethodVisibility(initialAuthMethod, urlPreference.text)
        updateIconColors(
            urlPreference.text,
            userNamePreference.text,
            passwordPreference.text,
            apiTokenPreference.text
        )
    }

    private fun initEditor(
        key: String,
        initialValue: String?,
        @DrawableRes iconResId: Int,
        summaryGenerator: (value: String?) -> CharSequence
    ): EditTextPreference {
        val preference = preferenceScreen.findPreference<EditTextPreference>(key)!!
        preference.icon = DrawableCompat.wrap(ContextCompat.getDrawable(preference.context, iconResId)!!)
        preference.text = initialValue
        preference.setOnPreferenceChangeListener { pref, newValue ->
            pref.summary = summaryGenerator(newValue as String)
            onValuesChanged(pref, newValue)
            true
        }
        preference.summary = summaryGenerator(initialValue)
        return preference
    }

    private fun updateAuthMethodVisibility(authMethod: AuthMethod, url: String?) {
        // myopenHAB uses the credentials of the myopenHAB account, no server setting is required
        val isMyOpenhab = url?.toHttpUrlOrNull()?.host?.let { HttpClient.isMyOpenhab(it) } == true
        userNamePreference.isVisible = authMethod == AuthMethod.BASIC
        passwordPreference.isVisible = authMethod == AuthMethod.BASIC
        basicAuthInfoPreference.isVisible = authMethod == AuthMethod.BASIC && !isMyOpenhab
        apiTokenPreference.isVisible = authMethod == AuthMethod.TOKEN
        apiTokenInfoPreference.isVisible = authMethod == AuthMethod.TOKEN
    }

    /**
     * Called from the preference change listeners, i.e. before the new value is stored in the changed preference.
     */
    private fun onValuesChanged(changedPref: Preference, newValue: String) {
        fun valueOf(pref: Preference, currentValue: String?) = if (pref === changedPref) newValue else currentValue

        val url = valueOf(urlPreference, urlPreference.text)
        val authMethod = AuthMethod.fromValue(valueOf(authMethodPreference, authMethodPreference.value))
        val userName = valueOf(userNamePreference, userNamePreference.text)
        val password = valueOf(passwordPreference, passwordPreference.text)
        val apiToken = valueOf(apiTokenPreference, apiTokenPreference.text)

        updateAuthMethodVisibility(authMethod, url)
        updateIconColors(url, userName, password, apiToken)

        if (url != null) {
            // Values of hidden preferences are kept to allow switching back, but they aren't part of the path
            val path = when (authMethod) {
                AuthMethod.NONE -> ServerPath(url, null, null)
                AuthMethod.BASIC -> ServerPath(url, userName, password)
                AuthMethod.TOKEN -> ServerPath(url, apiToken, null)
            }
            parent.onPathChanged(requireArguments().getString("key", ""), path)
        }
    }

    private fun updateIconColors(url: String?, userName: String?, password: String?, apiToken: String?) {
        updateIconColor(urlPreference) {
            when {
                url?.toHttpUrlOrNull()?.isHttps == true -> R.color.pref_icon_green
                !url.isNullOrEmpty() -> R.color.pref_icon_red
                else -> null
            }
        }
        updateIconColor(userNamePreference) {
            when {
                url.isNullOrEmpty() -> null
                userName.isNullOrEmpty() -> R.color.pref_icon_red
                else -> R.color.pref_icon_green
            }
        }
        updateIconColor(passwordPreference) {
            when {
                url.isNullOrEmpty() -> null
                password.isNullOrEmpty() -> R.color.pref_icon_red
                isWeakPassword(password) -> R.color.pref_icon_orange
                else -> R.color.pref_icon_green
            }
        }
        updateIconColor(apiTokenPreference) {
            when {
                url.isNullOrEmpty() -> null
                apiToken.isNullOrEmpty() -> R.color.pref_icon_red
                else -> R.color.pref_icon_green
            }
        }
    }

    private fun updateIconColor(pref: Preference, colorGenerator: () -> Int?) {
        pref.icon?.let { icon ->
            val colorResId = colorGenerator()
            if (colorResId != null) {
                DrawableCompat.setTint(icon, ContextCompat.getColor(pref.context, colorResId))
            } else {
                DrawableCompat.setTintList(icon, null)
            }
        }
    }

    companion object {
        fun newInstance(
            key: String,
            serverPath: ServerPath?,
            prefsResId: Int,
            titleResId: Int,
            urlSummaryResId: Int,
            parent: ServerEditorFragment
        ): ConnectionSettingsFragment {
            val f = ConnectionSettingsFragment()
            val args = Bundle().apply {
                putString("key", key)
                putParcelable("path", serverPath)
                putInt("prefs", prefsResId)
                putInt("title", titleResId)
                putInt("urlsummary", urlSummaryResId)
            }
            parent.parentFragmentManager.putFragment(args, "parent", parent)
            f.arguments = args
            return f
        }
    }

    private enum class AuthMethod(val value: String) {
        NONE("none"),
        BASIC("basic"),
        TOKEN("token");

        companion object {
            fun fromValue(value: String?) = entries.firstOrNull { it.value == value } ?: BASIC
        }
    }
}
