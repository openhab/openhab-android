package org.openhab.habdroid.ui.widget

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.os.Bundle
import android.preference.DialogPreference
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.widget.SwitchCompat

import com.google.android.material.textfield.TextInputLayout
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.setupHelpIcon
import org.openhab.habdroid.ui.updateHelpIconAlpha

class ItemUpdatingPreference constructor(context: Context, attrs: AttributeSet?):
        DialogPreference(context, attrs), TextWatcher, CompoundButton.OnCheckedChangeListener {
    private val howtoHint: String?
    private val howtoUrl: String?
    private val summaryOn: String?
    private val summaryOff: String?
    private var value: Pair<Boolean, String>? = null

    private lateinit var helpIcon: ImageView
    private lateinit var switch: SwitchCompat
    private lateinit var editorWrapper: TextInputLayout
    private lateinit var editor: EditText
    private var okButton: Button? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ItemUpdatingPreference).apply {
            howtoHint = getString(R.styleable.ItemUpdatingPreference_helpHint)
            howtoUrl = getString(R.styleable.ItemUpdatingPreference_helpUrl)
            summaryOn = getString(R.styleable.ItemUpdatingPreference_summaryEnabled)
            summaryOff = getString(R.styleable.ItemUpdatingPreference_summaryDisabled)
            recycle()
        }

        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        value = if (restorePersistedValue) {
            getPersistedString(null).toItemUpdatePrefValue()
        } else {
            // We ensure the default value is of correct type in onGetDefaultValue()
            @Suppress("UNCHECKED_CAST")
            defaultValue as Pair<Boolean, String>?
            // XXX: persist if not yet present
        }
        updateSummary()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index).toItemUpdatePrefValue()
    }

    override fun onCreateDialogView(): View {
        val inflater = LayoutInflater.from(context)
        val v = inflater.inflate(R.layout.item_updating_pref_dialog, null)

        switch = v.findViewById(R.id.enabled)
        switch.setOnCheckedChangeListener(this)
        editor = v.findViewById(R.id.itemName)
        editor.addTextChangedListener(this)
        editorWrapper = v.findViewById(R.id.itemNameWrapper)

        val label = v.findViewById<TextView>(R.id.enabledLabel)
        label.text = title

        val value = value
        if (value != null) {
            switch.isChecked = value.first
            editor.setText(value.second)
        }

        helpIcon = v.findViewById(R.id.help_icon)
        helpIcon.setupHelpIcon(howtoUrl.orEmpty(), howtoHint.orEmpty())

        onCheckedChanged(switch, switch.isChecked)

        return v
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)
        if (dialog is AlertDialog) {
            val dialog = dialog as AlertDialog
            okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        }
        updateOkButtonState()
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (positiveResult) {
            val newValue = Pair(switch.isChecked, editor.text.toString())
            if (callChangeListener(newValue)) {
                if (shouldPersist()) {
                    persistString("${newValue.first}|${newValue.second}")
                }
                value = newValue
                updateSummary()
            }
        }
    }

    override fun onCheckedChanged(button: CompoundButton, checked: Boolean) {
        editorWrapper.isEnabled = checked
        helpIcon.updateHelpIconAlpha(checked)
        updateOkButtonState()
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // no-op
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // no-op
    }

    override fun afterTextChanged(s: Editable) {
        val value = s.toString()
        if (value.trim().isEmpty() || value.contains(" ") || value.contains("\n")) {
            editor.error = context.getString(R.string.error_sending_alarm_clock_item_empty)
        } else {
            editor.error = null
        }
        updateOkButtonState()
    }

    private fun updateOkButtonState() {
        okButton?.isEnabled = !editor.isEnabled || editor.error == null
    }

    private fun updateSummary() {
        val value = value ?: return
        val summary = if (value.first) summaryOn else summaryOff
        if (summary != null) {
            setSummary(String.format(summary, value.second))
        }
    }
}

fun String?.toItemUpdatePrefValue(): Pair<Boolean, String> {
    val pos = this?.indexOf('|')
    if (pos == null || pos < 0) {
        return Pair(false, "")
    }
    return Pair(this!!.substring(0, pos).toBoolean(), substring(pos + 1))

}
