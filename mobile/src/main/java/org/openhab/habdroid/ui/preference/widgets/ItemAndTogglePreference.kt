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
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.work.WorkManager
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.databinding.ItemUpdatingPrefDialogBinding
import org.openhab.habdroid.ui.preference.CustomDialogPreference
import org.openhab.habdroid.ui.setupHelpIcon
import org.openhab.habdroid.util.hasPermissions

open class ItemAndTogglePreference(context: Context, attrs: AttributeSet?) :
    DialogPreference(context, attrs),
    CustomDialogPreference {
    private val howtoUrl: String?
    protected var summaryOn: String?
    protected val summaryOff: String?
    protected val iconOn: Drawable?
    protected val iconOff: Drawable?
    protected var value: Pair<Boolean, String>? = null
    protected val workManager = WorkManager.getInstance(context)

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ItemAndTogglePreference).apply {
            howtoUrl = getString(R.styleable.ItemAndTogglePreference_helpUrl)
            summaryOn = getString(R.styleable.ItemAndTogglePreference_summaryEnabled)
            summaryOff = getString(R.styleable.ItemAndTogglePreference_summaryDisabled)
            iconOn = getDrawable(R.styleable.ItemAndTogglePreference_iconEnabled)
            iconOff = getDrawable(R.styleable.ItemAndTogglePreference_iconDisabled)
            recycle()
        }

        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val setValue = getPersistedString(null)
        value = if (setValue != null) {
            setValue.toItemUpdatePrefValue()
        } else {
            // We ensure the default value is of correct type in onGetDefaultValue()
            @Suppress("UNCHECKED_CAST")
            defaultValue as Pair<Boolean, String>?
        }
        // XXX: persist if not yet present
        updateSummaryAndIcon()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? = a.getString(index).toItemUpdatePrefValue()

    override fun createDialog(): DialogFragment = PrefDialogFragment.newInstance(key)

    fun setValue(checked: Boolean = this.value?.first ?: false, value: String = this.value?.second.orEmpty()) {
        val newValue = Pair(checked, value)
        if (callChangeListener(newValue)) {
            if (shouldPersist()) {
                persistString("${newValue.first}|${newValue.second}")
            }
            this.value = newValue
            updateSummaryAndIcon()
        }
    }

    fun setSummaryOnAndUpdate(summary: String) {
        summaryOn = summary
        updateSummaryAndIcon()
    }

    open fun updateSummaryAndIcon() {
        val value = value ?: return
        val summary = if (value.first) summaryOn else summaryOff
        setSummary(summary.orEmpty().format(value.second))
    }

    class PrefDialogFragment :
        PreferenceDialogFragmentCompat(),
        CompoundButton.OnCheckedChangeListener,
        TextWatcher {
        private lateinit var binding: ItemUpdatingPrefDialogBinding

        override fun onCreateDialogView(context: Context): View {
            val inflater = LayoutInflater.from(activity)
            val pref = preference as ItemAndTogglePreference
            val binding = ItemUpdatingPrefDialogBinding.inflate(inflater)

            binding.enabled.setOnCheckedChangeListener(this)
            binding.itemName.addTextChangedListener(this)
            binding.helpIcon.setupHelpIcon(pref.howtoUrl.orEmpty(), R.string.settings_item_update_pref_howto_summary)
            binding.permissionHint.setText(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    R.string.settings_background_tasks_permission_hint
                } else {
                    R.string.settings_background_tasks_permission_hint_pre_r
                }
            )

            val requiredPermissions = BackgroundTasksManager.getRequiredPermissionsForTask(pref.key)
            binding.permissionHint.isVisible =
                requiredPermissions != null &&
                context.hasPermissions(requiredPermissions) == false

            binding.enabledLabel.text = pref.title

            pref.value?.let { (enabled, itemName) ->
                binding.enabled.isChecked = enabled
                binding.itemName.setText(itemName)
            }

            onCheckedChanged(binding.enabled, binding.enabled.isChecked)

            return binding.root
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val pref = preference as ItemAndTogglePreference
                pref.setValue(binding.enabled.isChecked, binding.itemName.text.toString())
            }
        }

        override fun onStart() {
            super.onStart()
            updateOkButtonState()
        }

        override fun onCheckedChanged(button: CompoundButton, checked: Boolean) {
            binding.itemNameWrapper.isEnabled = checked
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
                binding.itemNameWrapper.error = context?.getString(R.string.error_no_valid_item_name)
            } else {
                binding.itemNameWrapper.error = null
            }
            updateOkButtonState()
        }

        private fun updateOkButtonState() {
            val dialog = this.dialog
            if (dialog is AlertDialog) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    !binding.itemName.isEnabled ||
                    binding.itemNameWrapper.error == null
            }
        }

        companion object {
            fun newInstance(key: String): PrefDialogFragment {
                val f = PrefDialogFragment()
                f.arguments = bundleOf(ARG_KEY to key)
                return f
            }
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
