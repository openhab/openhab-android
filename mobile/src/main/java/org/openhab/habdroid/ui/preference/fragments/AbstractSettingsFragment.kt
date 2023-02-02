/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.util.BitSet
import org.openhab.habdroid.ui.preference.CustomDialogPreference
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.util.getSecretPrefs

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

    override fun onDisplayPreferenceDialog(preference: Preference) {
        val showDialog: (DialogFragment) -> Unit = { fragment ->
            @Suppress("DEPRECATION") // TODO: Replace deprecated function
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
        fun isWeakPassword(password: String?): Boolean {
            if (password == null || password.length < 8) {
                return true
            }
            val groups = BitSet()
            password.forEach { c ->
                groups.set(
                    when {
                        Character.isLetter(c) && Character.isLowerCase(c) -> 0
                        Character.isLetter(c) && Character.isUpperCase(c) -> 1
                        Character.isDigit(c) -> 2
                        else -> 3
                    }
                )
            }
            return groups.cardinality() < 3
        }
    }
}
