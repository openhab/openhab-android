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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import org.openhab.habdroid.R
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.background.tiles.TileData
import org.openhab.habdroid.background.tiles.getTileData
import org.openhab.habdroid.background.tiles.putTileData
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.ui.BasicItemPickerActivity
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.preference.widgets.CustomInputTypePreference
import org.openhab.habdroid.ui.preference.widgets.ItemAndStatePreference
import org.openhab.habdroid.util.parcelableArrayList

@RequiresApi(Build.VERSION_CODES.N)
class TileSettingsFragment :
    AbstractSettingsFragment(),
    PreferencesActivity.ConfirmLeaveDialogFragment.Callback,
    MenuProvider {
    override val titleResId: Int @StringRes get() = R.string.tile
    private var tileId = 0

    private lateinit var enabledPref: SwitchPreferenceCompat
    private lateinit var itemAndStatePref: ItemAndStatePreference
    private lateinit var namePref: CustomInputTypePreference
    private lateinit var iconPref: ListPreference
    private lateinit var requireUnlockPref: SwitchPreferenceCompat
    private var itemAndStatePrefCallback = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "itemAndStatePrefCallback: $result")
        val data = result.data ?: return@registerForActivityResult
        itemAndStatePref.item = data.getStringExtra("item")
        itemAndStatePref.label = data.getStringExtra("label")
        itemAndStatePref.state = data.getStringExtra("state")
        itemAndStatePref.mappedState = data.getStringExtra("mappedState")
        val itemTags = data.extras?.parcelableArrayList<Item.Tag>("tags")
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
                    "fan", "fan_box", "fan_ceiling" -> R.string.tile_icon_fan_value
                    "bedroom", "bedroom_blue", "bedroom_orange", "bedroom_red" -> R.string.tile_icon_bed_value
                    "settings" -> R.string.tile_icon_settings_value
                    "bath", "toilet" -> R.string.tile_icon_bath_value
                    "blinds", "rollershutter" -> R.string.tile_icon_roller_shutter_value
                    "camera" -> R.string.tile_icon_camera_value
                    "wallswitch" -> R.string.tile_icon_light_switch_value
                    "garage", "garagedoor", "garage_detached", "garage_detached_selected" ->
                        R.string.tile_icon_garage_value
                    "switch" -> R.string.tile_icon_switch_value
                    "text" -> R.string.tile_icon_text_value
                    "sofa" -> R.string.tile_icon_sofa_value
                    else -> when {
                        itemTags?.contains(Item.Tag.Light) == true -> R.string.tile_icon_bulb_value
                        itemTags?.contains(Item.Tag.Blinds) == true -> R.string.tile_icon_roller_shutter_value
                        itemTags?.contains(Item.Tag.Switch) == true -> R.string.tile_icon_switch_value
                        else -> R.string.tile_icon_openhab_value
                    }
                }
            }
            iconPref.value = getString(preSelectIcon)
            updateIconPrefIcon()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tileId = arguments?.getInt("id") ?: throw AssertionError("No tile id specified")

        setDataFromPrefs()

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (prefs.getTileData(tileId) != getCurrentPrefsAsTileData()) {
                    PreferencesActivity.ConfirmLeaveDialogFragment().show(childFragmentManager, "dialog_confirm_leave")
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
        inflater.inflate(R.menu.prefs_save, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                onLeaveAndSave()
                true
            }
            else -> false
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
            val intent = Intent(it.context, BasicItemPickerActivity::class.java)
            intent.putExtra("item", itemAndStatePref.item)
            itemAndStatePrefCallback.launch(intent)
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

    private fun getCurrentPrefsAsTileData(): TileData? {
        return if (enabledPref.isChecked) {
            TileData(
                item = itemAndStatePref.item.orEmpty(),
                state = itemAndStatePref.state.orEmpty(),
                label = itemAndStatePref.label.orEmpty(),
                tileLabel = namePref.text.orEmpty(),
                mappedState = itemAndStatePref.mappedState.orEmpty(),
                icon = iconPref.value.orEmpty(),
                requireUnlock = requireUnlockPref.isChecked
            )
        } else {
            null
        }
    }

    private fun setDataFromPrefs() {
        val data = prefs.getTileData(tileId)
        enabledPref.isChecked = data != null
        @Suppress("SpellCheckingInspection")
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

    override fun onLeaveAndSave() {
        Log.d(TAG, "Save tile $tileId")
        val context = preferenceManager.context
        val currentData = getCurrentPrefsAsTileData()
        if (currentData != null && !currentData.isValid()) {
            parentActivity.showSnackbar(
                PreferencesActivity.SNACKBAR_TAG_MISSING_PREFS,
                R.string.error_missing_prefs,
                Snackbar.LENGTH_LONG
            )
            return
        }

        prefs.edit {
            putTileData(tileId, currentData)
        }
        AbstractTileService.requestTileUpdate(context, tileId)

        parentActivity.onBackPressedDispatcher.onBackPressed()
    }

    override fun onLeaveAndDiscard() {
        setDataFromPrefs()
        parentActivity.onBackPressedDispatcher.onBackPressed()
    }

    companion object {
        private val TAG = TileSettingsFragment::class.java.simpleName

        fun newInstance(id: Int): TileSettingsFragment {
            val f = TileSettingsFragment()
            val args = bundleOf("id" to id)
            f.arguments = args
            return f
        }
    }
}
