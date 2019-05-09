package org.openhab.habdroid.ui.widget

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.os.Bundle
import android.preference.DialogPreference
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat

import com.google.android.material.textfield.TextInputLayout
import org.openhab.habdroid.R

class ItemUpdatingPreference : DialogPreference, TextWatcher, CompoundButton.OnCheckedChangeListener {
    private var howtoHint: String? = null
    private var howtoUrl: String? = null
    private var summaryOn: String? = null
    private var summaryOff: String? = null
    private var value: Pair<Boolean, String>? = null

    private lateinit var helpIcon: ImageView
    private lateinit var switch: SwitchCompat
    private lateinit var editorWrapper: TextInputLayout
    private lateinit var editor: EditText
    private lateinit var okButton: Button

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs,
                    R.styleable.ItemUpdatingPreference)
            initAttributes(a)
            a.recycle()
        }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs,
                    R.styleable.ItemUpdatingPreference)
            initAttributes(a)
            a.recycle()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int,
                defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs,
                    R.styleable.ItemUpdatingPreference, defStyleAttr, defStyleRes)
            initAttributes(a)
            a.recycle()
        }
    }

    private fun initAttributes(attrs: TypedArray) {
        howtoHint = attrs.getString(R.styleable.ItemUpdatingPreference_helpHint)
        howtoUrl = attrs.getString(R.styleable.ItemUpdatingPreference_helpUrl)
        summaryOn = attrs.getString(R.styleable.ItemUpdatingPreference_summaryEnabled)
        summaryOff = attrs.getString(R.styleable.ItemUpdatingPreference_summaryDisabled)

        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
        if (restorePersistedValue) {
            value = parseValue(getPersistedString(null))
        } else {
            value = defaultValue as Pair<Boolean, String>
            // XXX: persist if not yet present
        }
        updateSummary()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return parseValue(a.getString(index))
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
        HelpIconShowingPreferenceUtil.setupHelpIcon(context,
                helpIcon, howtoUrl?: "", howtoHint ?: "")

        onCheckedChanged(switch, switch.isChecked)

        return v
    }

    override fun showDialog(state: Bundle) {
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
                    val persistedValue = (java.lang.Boolean.valueOf(newValue.first).toString()
                            + "|" + newValue.second)
                    persistString(persistedValue)
                }
                value = newValue
                updateSummary()
            }
        }
    }

    override fun onCheckedChanged(button: CompoundButton, checked: Boolean) {
        editorWrapper.isEnabled = checked
        HelpIconShowingPreferenceUtil.updateHelpIconAlpha(helpIcon, checked)
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
        val valid = true
        if (TextUtils.isEmpty(value.trim { it <= ' ' }) || value.contains(" ") || value.contains("\n")) {
            editor.error = context.getString(R.string.error_sending_alarm_clock_item_empty)
        } else {
            editor.error = null
        }
        updateOkButtonState()
    }

    private fun updateOkButtonState() {
        if (editor != null && okButton != null) {
            okButton.isEnabled = !editor.isEnabled || editor.error == null
        }
    }

    private fun updateSummary() {
        val value = value
        if (value == null) {
            return
        }
        val summary = if (value.first) summaryOn else summaryOff
        if (summary != null) {
            setSummary(String.format(summary, value.second))
        }
    }

    companion object {
        fun parseValue(value: String?): Pair<Boolean, String>? {
            if (value == null) {
                return null
            }
            val pos = value.indexOf('|')
            if (pos < 0) {
                return null
            }
            return Pair(value.substring(0, pos).toBoolean(), value.substring(pos + 1))
        }
    }
}
