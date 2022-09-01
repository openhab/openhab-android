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

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.forEachIndexed
import org.openhab.habdroid.R
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.background.tiles.getTileData
import org.openhab.habdroid.ui.preference.PreferencesActivity

@RequiresApi(Build.VERSION_CODES.N)
class TileOverviewFragment : PreferencesActivity.AbstractSettingsFragment() {
    override val titleResId: Int @StringRes get() = R.string.tiles_for_quick_settings

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_tile_overview)
        for (tileId in 1..AbstractTileService.TILE_COUNT) {
            val tilePref = Preference(preferenceManager.context).apply {
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
