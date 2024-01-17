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
import androidx.core.os.bundleOf
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.R
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.parcelable

class ConnectionSettingsFragment : AbstractSettingsFragment() {
    override val titleResId: Int @StringRes get() = requireArguments().getInt("title")

    private lateinit var urlPreference: EditTextPreference
    private lateinit var userNamePreference: EditTextPreference
    private lateinit var passwordPreference: EditTextPreference
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

        userNamePreference = initEditor(
            "username",
            path.userName,
            R.drawable.ic_person_outline_grey_24dp
        ) { value ->
            if (!value.isNullOrEmpty()) value else getString(R.string.info_not_set)
        }
        passwordPreference = initEditor(
            "password",
            path.password,
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

        updateIconColors(urlPreference.text, userNamePreference.text, passwordPreference.text)
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
            val url = if (pref === urlPreference) newValue as String else urlPreference.text
            val userName = if (pref === userNamePreference) newValue as String else userNamePreference.text
            val password = if (pref === passwordPreference) newValue as String else passwordPreference.text

            updateIconColors(url, userName, password)
            pref.summary = summaryGenerator(newValue as String)

            if (url != null) {
                val path = ServerPath(url, userName, password)
                parent.onPathChanged(requireArguments().getString("key", ""), path)
            }
            true
        }
        preference.summary = summaryGenerator(initialValue)
        return preference
    }

    private fun updateIconColors(url: String?, userName: String?, password: String?) {
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
            val args = bundleOf(
                "key" to key,
                "path" to serverPath,
                "prefs" to prefsResId,
                "title" to titleResId,
                "urlsummary" to urlSummaryResId
            )
            parent.parentFragmentManager.putFragment(args, "parent", parent)
            f.arguments = args
            return f
        }
    }
}
