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
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import org.openhab.habdroid.R
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.model.toWifiSsids
import org.openhab.habdroid.ui.preference.CustomDialogPreference
import org.openhab.habdroid.util.getCurrentWifiSsid

class WifiSsidInputPreference constructor(context: Context, attrs: AttributeSet) :
    DialogPreference(context, attrs), CustomDialogPreference {
    private var value: Pair<String, Boolean>? = null

    init {
        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun getDialogLayoutResource(): Int {
        return R.layout.pref_dialog_wifi_ssid
    }

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

    override fun createDialog(): DialogFragment {
        return PrefFragment.newInstance(key, title)
    }

    class PrefFragment : PreferenceDialogFragmentCompat() {
        private lateinit var editorWrapper: TextInputLayout
        private lateinit var editor: MaterialAutoCompleteTextView
        private lateinit var restrictButton: MaterialSwitch

        override fun onCreateDialogView(context: Context): View {
            val inflater = LayoutInflater.from(activity)
            val v = inflater.inflate(R.layout.pref_dialog_wifi_ssid, null)

            editorWrapper = v.findViewById(R.id.input_wrapper)
            editor = v.findViewById(android.R.id.edit)
            restrictButton = v.findViewById(R.id.restrict_switch)

            editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            arguments?.getCharSequence(KEY_TITLE)?.let { title ->
                editorWrapper.hint = title
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                editor.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }

            val currentValue = (preference as WifiSsidInputPreference).value

            val currentSsid =
                preference.context.getCurrentWifiSsid(OpenHabApplication.DATA_ACCESS_TAG_SELECT_SERVER_WIFI)
            val currentSsidAsArray = currentSsid?.let { arrayOf(it) } ?: emptyArray()
            val adapter = ArrayAdapter(editor.context, android.R.layout.simple_dropdown_item_1line, currentSsidAsArray)
            editor.setAdapter(adapter)

            editor.setText(currentValue?.first.orEmpty())
            editor.setSelection(editor.text.length)
            restrictButton.isChecked = currentValue?.second ?: false

            return v
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val prefs = preference.sharedPreferences!!
                prefs.edit {
                    val pref = preference as WifiSsidInputPreference
                    pref.setValue(Pair(editor.text.toString(), restrictButton.isChecked))
                }
            }
        }

        companion object {
            private const val KEY_TITLE = "title"

            fun newInstance(
                key: String,
                title: CharSequence?
            ): PrefFragment {
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

