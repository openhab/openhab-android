/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.getPreference

class DrawerEntriesMenuFragment : AbstractSettingsFragment() {
    override val titleResId: Int @StringRes get() = R.string.drawer_entries

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_drawer_entries)

        val showSitemapInDrawerPref = getPreference(PrefKeys.SHOW_SITEMAPS_IN_DRAWER)

        showSitemapInDrawerPref.setOnPreferenceChangeListener { _, _ ->
            parentActivity.addResultFlag(PreferencesActivity.RESULT_EXTRA_SITEMAP_DRAWER_CHANGED)
            true
        }

        if (NfcAdapter.getDefaultAdapter(requireContext()) == null && !Util.isEmulator()) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.DRAWER_ENTRY_NFC)
        }
    }
}
