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
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.databinding.PrefDialogNotificationPollingBinding
import org.openhab.habdroid.ui.preference.CustomDialogPreference
import org.openhab.habdroid.ui.setupHelpIcon
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrFallbackIfEmpty

class NotificationPollingPreference(context: Context, attrs: AttributeSet?) :
    DialogPreference(context, attrs),
    CustomDialogPreference {
    private var value: Boolean? = null

    init {
        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedBoolean(false)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? = a.getBoolean(index, false)

    override fun createDialog(): DialogFragment = PrefDialogFragment.newInstance(key)

    fun setValue(enabled: Boolean = value ?: false) {
        if (callChangeListener(enabled)) {
            if (shouldPersist()) {
                persistBoolean(enabled)
            }
            this.value = enabled
        }
    }

    suspend fun updateSummaryAndIcon() {
        val status = CloudMessagingHelper.getPushNotificationStatus(context)
        summary = status.message
        setIcon(status.icon)
    }

    class PrefDialogFragment : PreferenceDialogFragmentCompat() {
        private lateinit var binding: PrefDialogNotificationPollingBinding
        private lateinit var spinnerValues: Array<String>
        private lateinit var prefs: SharedPreferences

        override fun onCreateDialogView(context: Context): View {
            val inflater = LayoutInflater.from(activity)
            val pref = preference as NotificationPollingPreference
            binding = PrefDialogNotificationPollingBinding.inflate(inflater)

            binding.enabled.setOnCheckedChangeListener { _, checked ->
                binding.spinner.isEnabled = checked
            }

            binding.helpIcon.setupHelpIcon(
                "https://www.openhab.org/docs/apps/android.html#notifications-in-foss-version",
                R.string.click_here_for_more_information
            )
            ArrayAdapter.createFromResource(
                context,
                R.array.send_device_info_schedule,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                adapter.setDropDownViewResource(R.layout.select_dialog_singlechoice)
                binding.spinner.adapter = adapter
            }

            binding.enabledLabel.text = getString(R.string.app_notifications)

            pref.value?.let { binding.enabled.isChecked = it }

            spinnerValues = context.resources.getStringArray(R.array.send_device_info_schedule_values)
            prefs = context.getPrefs()

            val spinnerValue = prefs.getStringOrFallbackIfEmpty(PrefKeys.SEND_DEVICE_INFO_SCHEDULE, "360")
            binding.spinner.setSelection(spinnerValues.indexOf(spinnerValue), false)
            binding.spinner.isEnabled = binding.enabled.isChecked

            return binding.root
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val pref = preference as NotificationPollingPreference
                pref.setValue(binding.enabled.isChecked)
                prefs.edit {
                    putString(PrefKeys.SEND_DEVICE_INFO_SCHEDULE, spinnerValues[binding.spinner.selectedItemPosition])
                }
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
