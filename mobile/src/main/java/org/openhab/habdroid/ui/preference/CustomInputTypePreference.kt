/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.preference

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.getString

class CustomInputTypePreference constructor(context: Context, attrs: AttributeSet) :
    EditTextPreference(context, attrs) {
    private val inputType: Int
    private var autofillHints: Array<String>? = null
    private var hintPrefKey: String? = null
    private val isPrefSecret: Boolean

    init {
        context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.inputType, android.R.attr.autofillHints,
            R.attr.hintPrefKey, R.attr.isPrefSecret))
            .apply {
                inputType = getInt(0, 0)
                autofillHints = getString(1)?.split(',')?.toTypedArray()
                hintPrefKey = getString(2)
                isPrefSecret = getBoolean(3, false)
                recycle()
        }
    }

    fun createDialog(): DialogFragment {
        return PrefFragment.newInstance(key, inputType, autofillHints, hintPrefKey, isPrefSecret)
    }

    class PrefFragment : EditTextPreferenceDialogFragmentCompat() {
        override fun onBindDialogView(view: View?) {
            val editor = view?.findViewById<EditText>(android.R.id.edit)
            arguments?.getInt(KEY_INPUT_TYPE)?.let { type ->
                editor?.inputType = type
            }
            arguments?.getString(KEY_HINT_PREF_KEY)?.let { key ->
                editor?.hint = context?.getPrefs()?.getString(key)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hints = arguments?.getStringArray(KEY_AUTOFILL_HINTS)
                if (hints == null) {
                    editor?.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                } else {
                    editor?.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    editor?.setAutofillHints(*hints)
                }
            }
            super.onBindDialogView(view)
            if (arguments?.getBoolean(KEY_IS_PREF_SECRET) == true) {
                editor?.setText(context?.getSecretPrefs()?.getString(this.preference.key))
            }
        }

        companion object {
            private const val KEY_INPUT_TYPE = "inputType"
            private const val KEY_AUTOFILL_HINTS = "autofillHint"
            private const val KEY_HINT_PREF_KEY = "hintPrefKey"
            private const val KEY_IS_PREF_SECRET = "isPrefSecret"

            fun newInstance(key: String, inputType: Int, autofillHints: Array<String>?, hintPrefKey: String?,
                isPrefSecret: Boolean):
                PrefFragment {
                val f = PrefFragment()
                f.arguments = bundleOf(ARG_KEY to key, KEY_INPUT_TYPE to inputType,
                    KEY_AUTOFILL_HINTS to autofillHints, KEY_HINT_PREF_KEY to hintPrefKey,
                    KEY_IS_PREF_SECRET to isPrefSecret)
                return f
            }
        }
    }
}

