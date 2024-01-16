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

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.ImageView
import androidx.appcompat.widget.TooltipCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.openhab.habdroid.R

class NotificationChannelPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private var helpIcon: ImageView? = null

    init {
        widgetLayoutResource = R.layout.help_icon_pref
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        helpIcon = holder.itemView.findViewById(R.id.help_icon)
        helpIcon?.apply {
            val contentDescription = context.getString(R.string.click_here_for_more_information)
            this.contentDescription = contentDescription
            TooltipCompat.setTooltipText(this, contentDescription)

            setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setMessage(R.string.settings_notification_hint)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(R.string.settings_notification_reset_settings) { _, _ ->
                        MaterialAlertDialogBuilder(context)
                            .setMessage(R.string.settings_notification_reset_settings_confirm)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                nm.notificationChannels
                                    .filter { it.id.startsWith("severity-") }
                                    .forEach {
                                        nm.deleteNotificationChannel(it.id)
                                    }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                    .show()
            }
        }
    }
}
