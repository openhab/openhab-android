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
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.ui.CustomDialogPreference
import org.openhab.habdroid.ui.setupHelpIcon
import org.openhab.habdroid.ui.updateHelpIconAlpha
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrFallbackIfEmpty

class NotificationPollingPreference constructor(context: Context, attrs: AttributeSet?) :
    DialogPreference(context, attrs), CustomDialogPreference {
    private var value: Boolean? = null

    init {
        dialogTitle = null
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedBoolean(false)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getBoolean(index, false)
    }

    override fun createDialog(): DialogFragment {
        return PrefDialogFragment.newInstance(key)
    }

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

    class PrefDialogFragment : PreferenceDialogFragmentCompat(), CompoundButton.OnCheckedChangeListener {
        private lateinit var helpIcon: ImageView
        private lateinit var switch: SwitchCompat
        private lateinit var spinner: AppCompatSpinner
        private lateinit var spinnerValues: Array<String>
        private lateinit var prefs: SharedPreferences

        override fun onCreateDialogView(context: Context?): View {
            val inflater = LayoutInflater.from(activity)
            val v = inflater.inflate(R.layout.pref_dialog_notification_polling, null)
            val pref = preference as NotificationPollingPreference

            switch = v.findViewById(R.id.enabled)
            switch.setOnCheckedChangeListener(this)
            helpIcon = v.findViewById(R.id.help_icon)
            helpIcon.setupHelpIcon(
                "https://www.openhab.org/docs/apps/android.html#notifications-in-foss-version",
                R.string.push_notification_help_icon_description
            )
            spinner = v.findViewById(R.id.spinner)
            ArrayAdapter.createFromResource(
                requireContext(),
                R.array.send_device_info_schedule,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                adapter.setDropDownViewResource(R.layout.select_dialog_singlechoice)
                spinner.adapter = adapter
            }

            val label = v.findViewById<TextView>(R.id.enabledLabel)
            label.text = getString(R.string.app_notifications)

            val value = pref.value
            if (value != null) {
                switch.isChecked = value
            }

            spinnerValues = requireContext().resources.getStringArray(R.array.send_device_info_schedule_values)
            prefs = requireContext().getPrefs()

            val spinnerValue = prefs.getStringOrFallbackIfEmpty(PrefKeys.SEND_DEVICE_INFO_SCHEDULE, "360")
            spinner.setSelection(spinnerValues.indexOf(spinnerValue), false)

            onCheckedChanged(switch, switch.isChecked)

            return v
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val pref = preference as NotificationPollingPreference
                pref.setValue(switch.isChecked)
                prefs.edit {
                    putString(PrefKeys.SEND_DEVICE_INFO_SCHEDULE, spinnerValues[spinner.selectedItemPosition])
                }
            }
        }

        override fun onCheckedChanged(button: CompoundButton, checked: Boolean) {
            helpIcon.updateHelpIconAlpha(checked)
            spinner.isEnabled = checked
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
