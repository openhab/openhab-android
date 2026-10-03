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

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.BasicItemPickerActivity
import org.openhab.habdroid.ui.preference.widgets.ItemPreference
import org.openhab.habdroid.util.PrefKeys

class DayDreamFragment : AbstractSettingsFragment() {
    override val titleResId: Int @StringRes get() = R.string.screensaver

    private lateinit var itemPref: ItemPreference
    private val itemPickerCallback = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val itemName = result.data?.getStringExtra("item") ?: return@registerForActivityResult
        itemPref.setItem(itemName)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_day_dream)
        itemPref = findPreference(PrefKeys.DAY_DREAM_ITEM)!!
        itemPref.setOnPreferenceClickListener {
            val intent = Intent(it.context, BasicItemPickerActivity::class.java).apply {
                putExtra("item", itemPref.itemName)
                putExtra("select_item_only", true)
                putExtra("hide_read_only", false)
            }
            itemPickerCallback.launch(intent)
            true
        }
    }
}
