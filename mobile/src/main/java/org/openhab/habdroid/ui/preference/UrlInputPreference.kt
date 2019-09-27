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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.google.android.material.textfield.TextInputLayout
import org.openhab.habdroid.R
import java.net.MalformedURLException
import java.net.URL

class UrlInputPreference constructor(context: Context, attrs: AttributeSet) :
    CustomInputTypePreference(context, attrs) {

    override fun createDialog(): DialogFragment {
        return PrefFragment.newInstance(key, title)
    }

    override fun setText(text: String?) {
        super.setText(text?.trim())
    }

    class PrefFragment : EditTextPreferenceDialogFragmentCompat(), TextWatcher {
        private lateinit var wrapper: TextInputLayout
        private lateinit var editor: EditText
        private var urlIsValid: Boolean = false

        override fun onBindDialogView(view: View?) {
            super.onBindDialogView(view)
            if (view != null) {
                wrapper = view.findViewById<TextInputLayout>(R.id.input_wrapper)
                arguments?.getCharSequence(KEY_TITLE)?.let { title ->
                    wrapper.hint = title
                }
                editor = view.findViewById(android.R.id.edit)
                editor.addTextChangedListener(this)
                editor.inputType = InputType.TYPE_TEXT_VARIATION_URI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    editor.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                }
            }
        }

        override fun onStart() {
            super.onStart()
            updateOkButtonState()
            afterTextChanged(editor.text)
        }

        override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
            // no-op
        }

        override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
            // no-op
        }

        override fun afterTextChanged(editable: Editable) {
            var portSeemsInvalid = false
            if (editable.isEmpty()) {
                urlIsValid = true
            } else {
                val value = editable.toString()
                if (value.contains("\n")) {
                    urlIsValid = false
                } else {
                    try {
                        val url = URL(value)
                        urlIsValid = true
                        when (url.protocol) {
                            "http" -> portSeemsInvalid = url.port == 443 || url.port == 8443
                            "https" -> portSeemsInvalid = url.port == 80 || url.port == 8080
                        }
                    } catch (e: MalformedURLException) {
                        urlIsValid = false
                    }
                }
            }
            val res = editor.resources
            wrapper.error = when {
                !urlIsValid -> res.getString(R.string.error_invalid_url)
                portSeemsInvalid -> res.getString(R.string.error_port_seems_invalid)
                else -> null
            }
            updateOkButtonState()
        }

        private fun updateOkButtonState() {
            val dialog = this.dialog
            if (dialog is AlertDialog) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = urlIsValid
            }
        }

        companion object {
            private const val KEY_TITLE = "title"

            fun newInstance(key: String, title: CharSequence): PrefFragment {
                val f = PrefFragment()
                f.arguments = bundleOf(ARG_KEY to key, KEY_TITLE to title)
                return f
            }
        }
    }
}
