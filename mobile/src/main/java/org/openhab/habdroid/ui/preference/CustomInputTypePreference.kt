/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.PreferenceDataStore
import com.google.android.material.textfield.TextInputLayout
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.CustomDialogPreference

open class CustomInputTypePreference constructor(context: Context, attrs: AttributeSet) :
    EditTextPreference(context, attrs), CustomDialogPreference {
    private val inputType: Int
    private var autofillHints: Array<String>? = null
    private var defValue: Any? = null

    init {
        val attrArray = intArrayOf(android.R.attr.inputType, android.R.attr.autofillHints)
        context.obtainStyledAttributes(attrs, attrArray).apply {
            inputType = getInt(0, 0)
            autofillHints = getString(1)?.split(',')?.toTypedArray()
            recycle()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        defValue = defaultValue
        super.onSetInitialValue(defaultValue)
    }

    override fun setPreferenceDataStore(dataStore: PreferenceDataStore?) {
        super.setPreferenceDataStore(dataStore)
        // The initial onSetInitialValue call, which initializes the editor content, is called before
        // setPreferenceDataStore can possibly be called, so re-do that initialization here to get the content
        // populated from the data store that was just set
        super.onSetInitialValue(defValue)
    }

    override fun getDialogLayoutResource(): Int {
        return R.layout.text_input_pref_dialog
    }

    override fun getDialogTitle(): CharSequence? {
        return null
    }

    override fun createDialog(): DialogFragment {
        return PrefFragment.newInstance(key, title, inputType, autofillHints)
    }

    class PrefFragment : EditTextPreferenceDialogFragmentCompat(), TextWatcher {
        private lateinit var wrapper: TextInputLayout
        private lateinit var editor: EditText

        override fun onBindDialogView(view: View?) {
            super.onBindDialogView(view)
            if (view == null) {
                return
            }
            wrapper = view.findViewById(R.id.input_wrapper)
            editor = view.findViewById(android.R.id.edit)
            editor.addTextChangedListener(this)
            arguments?.getInt(KEY_INPUT_TYPE)?.let { type ->
                editor.inputType = type
            }
            arguments?.getCharSequence(KEY_TITLE)?.let { title ->
                wrapper.hint = title
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hints = arguments?.getStringArray(KEY_AUTOFILL_HINTS)
                if (hints == null) {
                    editor.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                } else {
                    editor.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    editor.setAutofillHints(*hints)
                }
            }
        }

        override fun onStart() {
            super.onStart()
            afterTextChanged(editor.text)
        }

        override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
            // no-op
        }

        override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
            // no-op
        }

        override fun afterTextChanged(editable: Editable) {
            val value = editable.toString()

            val isLastCharWhitespace = value.lastOrNull()?.isWhitespace() ?: false
            val isFirstCharWhitespace = value.firstOrNull()?.isWhitespace() ?: false

            val res = wrapper.resources
            wrapper.error = when {
                isFirstCharWhitespace -> res.getString(R.string.error_first_char_is_whitespace)
                isLastCharWhitespace -> res.getString(R.string.error_last_char_is_whitespace)
                else -> null
            }
        }

        companion object {
            private const val KEY_INPUT_TYPE = "inputType"
            private const val KEY_TITLE = "title"
            private const val KEY_AUTOFILL_HINTS = "autofillHint"

            fun newInstance(
                key: String,
                title: CharSequence,
                inputType: Int,
                autofillHints: Array<String>?
            ): PrefFragment {
                val f = PrefFragment()
                f.arguments = bundleOf(
                    ARG_KEY to key,
                    KEY_TITLE to title,
                    KEY_INPUT_TYPE to inputType,
                    KEY_AUTOFILL_HINTS to autofillHints
                )
                return f
            }
        }
    }
}

