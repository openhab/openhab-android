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

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.model.toOH2IconResource
import org.openhab.habdroid.ui.BasicItemPickerActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.ui.preference.widgets.CustomInputTypePreference
import org.openhab.habdroid.ui.preference.widgets.ItemAndStatePreference
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.PrefKeys

class WidgetSettingsFragment :
    PreferencesActivity.AbstractSettingsFragment(),
    PreferencesActivity.ConfirmLeaveDialogFragment.Callback {
    override val titleResId: Int @StringRes get() = R.string.item_update_widget
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var itemAndStatePref: ItemAndStatePreference
    private lateinit var namePref: CustomInputTypePreference
    private lateinit var showStatePref: SwitchPreferenceCompat
    private lateinit var themePref: ListPreference
    private var itemAndStatePrefCallback = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "itemAndStatePrefCallback: $result")
        val data = result.data ?: return@registerForActivityResult
        itemAndStatePref.item = data.getStringExtra("item")
        val label = if (data.getStringExtra("label").isNullOrEmpty()) {
            itemAndStatePref.item
        } else {
            data.getStringExtra("label")
        }
        itemAndStatePref.label = label
        itemAndStatePref.state = data.getStringExtra("state")
        itemAndStatePref.mappedState = data.getStringExtra("mappedState")
        itemAndStatePref.icon = data.getStringExtra("icon")
        updateItemAndStatePrefSummary()

        if (namePref.text.isNullOrEmpty()) {
            namePref.text = preferenceManager.context.getString(
                R.string.item_update_widget_text,
                itemAndStatePref.label,
                itemAndStatePref.mappedState
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        activity?.setResult(AppCompatActivity.RESULT_CANCELED)

        widgetId = requireArguments().getInt("id", AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            throw IllegalArgumentException("$TAG called with INVALID_APPWIDGET_ID")
        }

        setDataFromPrefs()

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val oldData = ItemUpdateWidget.getInfoForWidget(requireContext(), widgetId)
                val newData = getCurrentPrefsAsWidgetData()
                if (oldData != newData) {
                    PreferencesActivity.ConfirmLeaveDialogFragment().show(childFragmentManager, "dialog_confirm_leave")
                } else {
                    isEnabled = false
                    parentActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        parentActivity.onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.prefs_save, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                onLeaveAndSave()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_homescreen_widget)
        itemAndStatePref = findPreference("widget_item_and_action")!!
        namePref = findPreference("widget_name")!!
        showStatePref = findPreference("show_state")!!
        themePref = findPreference("widget_theme")!!

        namePref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        itemAndStatePref.setOnPreferenceClickListener {
            val intent = Intent(it.context, BasicItemPickerActivity::class.java)
            intent.putExtra("item", itemAndStatePref.item)
            intent.putExtra("show_no_command", true)
            itemAndStatePrefCallback.launch(intent)
            true
        }
    }

    private fun updateItemAndStatePrefSummary() {
        itemAndStatePref.summary = if (itemAndStatePref.item.isNullOrEmpty()) {
            itemAndStatePref.context.getString(R.string.info_not_set)
        } else {
            "${itemAndStatePref.label} (${itemAndStatePref.item}): ${itemAndStatePref.mappedState}"
        }
    }

    private fun getCurrentPrefsAsWidgetData(): ItemUpdateWidget.ItemUpdateWidgetData {
        return ItemUpdateWidget.ItemUpdateWidgetData(
            item = itemAndStatePref.item.orEmpty(),
            command = itemAndStatePref.state,
            label = itemAndStatePref.label.orEmpty(),
            widgetLabel = namePref.text.orEmpty(),
            mappedState = itemAndStatePref.mappedState.orEmpty(),
            icon = itemAndStatePref.icon.toOH2IconResource(),
            theme = themePref.value,
            showState = showStatePref.isChecked
        )
    }

    private fun setDataFromPrefs() {
        val data = ItemUpdateWidget.getInfoForWidget(requireContext(), widgetId)

        itemAndStatePref.item = data.item
        itemAndStatePref.label = data.label
        itemAndStatePref.state = data.command
        itemAndStatePref.mappedState = data.mappedState
        itemAndStatePref.icon = data.icon?.icon
        namePref.text = data.widgetLabel
        showStatePref.isChecked = data.showState
        themePref.value = data.theme

        updateItemAndStatePrefSummary()
    }

    override fun onLeaveAndSave() {
        Log.d(TAG, "Save widget $widgetId")
        val context = preferenceManager.context
        val newData = getCurrentPrefsAsWidgetData()
        if (!newData.isValid()) {
            parentActivity.showSnackbar(
                PreferencesActivity.SNACKBAR_TAG_MISSING_PREFS,
                R.string.error_missing_prefs,
                Snackbar.LENGTH_LONG
            )
            return
        }

        val oldData = ItemUpdateWidget.getInfoForWidget(context, widgetId)
        if (oldData.icon != newData.icon) {
            CacheManager.getInstance(context).removeWidgetIcon(widgetId)
        }

        ItemUpdateWidget.saveInfoForWidget(context, newData, widgetId)
        prefs.edit {
            putString(PrefKeys.LAST_WIDGET_THEME, newData.theme)
        }

        BackgroundTasksManager.schedulePeriodicTrigger(context, false)

        val updateIntent = Intent(context, ItemUpdateWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        context.sendBroadcast(updateIntent)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        activity?.setResult(AppCompatActivity.RESULT_OK, resultValue)

        parentActivity.onBackPressedDispatcher.onBackPressed()
    }

    override fun onLeaveAndDiscard() {
        setDataFromPrefs()
        parentActivity.onBackPressedDispatcher.onBackPressed()
    }

    companion object {
        private val TAG = WidgetSettingsFragment::class.java.simpleName

        fun newInstance(id: Int): WidgetSettingsFragment {
            val f = WidgetSettingsFragment()
            val args = bundleOf("id" to id)
            f.arguments = args
            return f
        }
    }
}
