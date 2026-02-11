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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.openhab.habdroid.R
import org.openhab.habdroid.databinding.TextInputPrefDialogBinding

class UrlInputPreference(context: Context, attrs: AttributeSet) : CustomInputTypePreference(context, attrs) {
    private val isForRemoteServer: Boolean

    init {
        context.obtainStyledAttributes(attrs, R.styleable.UrlInputPreference).apply {
            isForRemoteServer = getBoolean(R.styleable.UrlInputPreference_isForRemoteServer, false)
            recycle()
        }
    }

    override fun createDialog(): DialogFragment = PrefFragment.newInstance(key, title, isForRemoteServer)

    override fun setText(text: String?) {
        super.setText(text?.trim())
    }

    class PrefFragment :
        EditTextPreferenceDialogFragmentCompat(),
        TextWatcher {
        private lateinit var binding: TextInputPrefDialogBinding
        private var urlIsValid: Boolean = false

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            binding = TextInputPrefDialogBinding.bind(view)

            arguments?.getCharSequence(KEY_TITLE)?.let { title ->
                binding.inputWrapper.hint = title
            }
            binding.edit.apply {
                addTextChangedListener(this@PrefFragment)
                inputType = InputType.TYPE_TEXT_VARIATION_URI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                }

                val suggestions = if (requireArguments().getBoolean(IS_FOR_REMOTE_SERVER, false)) {
                    listOf("https://myopenhab.org", "https://")
                } else {
                    listOf("https://", "http://")
                }
                val adapter = ArrayAdapter(
                    binding.root.context,
                    android.R.layout.simple_dropdown_item_1line,
                    suggestions
                )
                setAdapter(adapter)
            }
        }

        override fun onStart() {
            super.onStart()
            updateOkButtonState()
            afterTextChanged(binding.edit.text)
        }

        override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
            // no-op
        }

        override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
            // no-op
        }

        override fun afterTextChanged(editable: Editable) {
            var portSeemsInvalid = false
            val isForRemoteServer = requireArguments().getBoolean(IS_FOR_REMOTE_SERVER, false)
            var url: HttpUrl? = null
            if (editable.isEmpty()) {
                urlIsValid = true
            } else {
                val value = editable.toString()
                if (value.contains("\n")) {
                    urlIsValid = false
                } else {
                    try {
                        url = value.toHttpUrl()
                        urlIsValid = true
                        portSeemsInvalid = when {
                            url.isHttps -> url.port == 80 || url.port == 8080

                            isForRemoteServer -> {
                                urlIsValid = false
                                false
                            }

                            else -> url.port == 443 || url.port == 8443
                        }
                    } catch (e: IllegalArgumentException) {
                        urlIsValid = false
                    }
                }
            }
            val errorRes = when {
                !urlIsValid -> if (isForRemoteServer) R.string.error_invalid_https_url else R.string.error_invalid_url
                portSeemsInvalid -> R.string.error_port_seems_invalid
                url?.host == "home.myopenhab.org" -> R.string.error_home_myopenhab_org_no_notifications
                else -> 0
            }

            binding.inputWrapper.error = if (errorRes == 0) null else binding.root.resources.getString(errorRes)
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
            private const val IS_FOR_REMOTE_SERVER = "isForRemoteServer"

            fun newInstance(key: String, title: CharSequence?, isForRemoteServer: Boolean): PrefFragment {
                val f = PrefFragment()
                f.arguments = bundleOf(
                    ARG_KEY to key,
                    KEY_TITLE to title,
                    IS_FOR_REMOTE_SERVER to isForRemoteServer
                )
                return f
            }
        }
    }
}
