package org.openhab.habdroid.ui.widget

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.preference.EditTextPreference
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.EditText

import androidx.annotation.StringRes

import org.openhab.habdroid.R

import java.net.MalformedURLException
import java.net.URL

class UrlInputPreference : EditTextPreference, TextWatcher {
    private lateinit var editor: EditText
    private var urlIsValid: Boolean = false

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun onAddEditTextToDialogView(dialogView: View, editText: EditText) {
        super.onAddEditTextToDialogView(dialogView, editText)
        editor = editText
        editText.addTextChangedListener(this)
    }

    override fun showDialog(state: Bundle) {
        super.showDialog(state)
        updateOkButtonState()
    }

    override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {}

    override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(editable: Editable) {
        var portSeemsInvalid = false
        if (TextUtils.isEmpty(editable)) {
            urlIsValid = true
        } else {
            val value = editable.toString()
            if (value.contains("\n") || value.contains(" ")) {
                urlIsValid = false
            } else {
                try {
                    val url = URL(value)
                    urlIsValid = true
                    if (url.protocol == "http") {
                        portSeemsInvalid = url.port == 443 || url.port == 8443
                    }
                    if (url.protocol == "https") {
                        portSeemsInvalid = url.port == 80 || url.port == 8080
                    }
                } catch (e: MalformedURLException) {
                    urlIsValid = false
                }

            }
        }
        @StringRes var error = 0
        if (!urlIsValid) {
            error = R.string.error_invalid_url
        } else if (portSeemsInvalid) {
            error = R.string.error_port_seems_invalid
        }
        editor.error = if (error == 0) null else editor.resources.getString(error)
        updateOkButtonState()
    }

    private fun updateOkButtonState() {
        if (dialog is AlertDialog) {
            val dialog = dialog as AlertDialog
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            if (okButton != null) {
                okButton.isEnabled = urlIsValid
            }
        }
    }
}
