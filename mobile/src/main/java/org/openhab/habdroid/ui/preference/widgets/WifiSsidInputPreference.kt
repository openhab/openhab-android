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

package org.openhab.habdroid.ui.preference.widgets

import android.content.Context
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.databinding.PrefDialogWifiSsidBinding
import org.openhab.habdroid.model.toWifiSsids
import org.openhab.habdroid.ui.preference.CustomDialogPreference
import org.openhab.habdroid.util.getCurrentWifiSsid

class WifiSsidInputPreference(context: Context, attrs: AttributeSet) :
    DialogPreference(context, attrs),
    CustomDialogPreference {
    private var value: Pair<String, Boolean>? = null

    init {
        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun getDialogLayoutResource(): Int = R.layout.pref_dialog_wifi_ssid

    private fun updateSummary() {
        val ssids = value?.first?.toWifiSsids()
        summary = if (ssids.isNullOrEmpty()) {
            context.getString(R.string.info_not_set)
        } else {
            ssids.joinToString(", ")
        }
    }

    fun setValue(value: Pair<String, Boolean>? = this.value) {
        if (callChangeListener(value)) {
            this.value = value
            updateSummary()
        }
    }

    override fun createDialog(): DialogFragment = PrefFragment.newInstance(key, title)

    class PrefFragment : PreferenceDialogFragmentCompat() {
        private lateinit var binding: PrefDialogWifiSsidBinding

        override fun onCreateDialogView(context: Context): View {
            val inflater = LayoutInflater.from(activity)
            binding = PrefDialogWifiSsidBinding.inflate(inflater)

            arguments?.getCharSequence(KEY_TITLE)?.let { title ->
                binding.inputWrapper.hint = title
            }

            val currentValue = (preference as WifiSsidInputPreference).value

            val currentSsid =
                preference.context.getCurrentWifiSsid(OpenHabApplication.DATA_ACCESS_TAG_SELECT_SERVER_WIFI)
            val currentSsidAsArray = currentSsid?.let { arrayOf(it) } ?: emptyArray()
            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, currentSsidAsArray)

            binding.edit.apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                }
                setAdapter(adapter)
                setText(currentValue?.first.orEmpty())
                setSelection(text.length)
            }
            binding.restrictSwitch.isChecked = currentValue?.second ?: false

            return binding.root
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val prefs = preference.sharedPreferences!!
                prefs.edit {
                    val pref = preference as WifiSsidInputPreference
                    pref.setValue(Pair(binding.edit.text.toString(), binding.restrictSwitch.isChecked))
                }
            }
        }

        companion object {
            private const val KEY_TITLE = "title"

            fun newInstance(key: String, title: CharSequence?): PrefFragment {
                val f = PrefFragment()
                f.arguments = bundleOf(
                    ARG_KEY to key,
                    KEY_TITLE to title
                )
                return f
            }
        }
    }
}
