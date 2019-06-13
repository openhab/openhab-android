package org.openhab.habdroid.ui.widget

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.preference.EditTextPreference
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import org.openhab.habdroid.R
import java.net.MalformedURLException
import java.net.URL

class UrlInputPreference constructor(context: Context, attrs: AttributeSet) :
    EditTextPreference(context, attrs), TextWatcher {
    private lateinit var editor: EditText
    private var urlIsValid: Boolean = false

    override fun onAddEditTextToDialogView(dialogView: View, editText: EditText) {
        super.onAddEditTextToDialogView(dialogView, editText)
        editor = editText
        editText.addTextChangedListener(this)
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)
        updateOkButtonState()
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
            if (value.contains("\n") || value.contains(" ")) {
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
        editor.error = when {
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
}
