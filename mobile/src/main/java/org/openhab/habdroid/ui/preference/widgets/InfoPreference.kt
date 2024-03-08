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
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.setupHelpIcon

class InfoPreference(context: Context, attrs: AttributeSet) :
    Preference(context, attrs) {
    private var helpIcon: ImageView? = null
    private val infoUrl: String?

    init {
        isSelectable = false
        setIcon(R.drawable.ic_info_outline_grey_24dp)

        if (!title.isNullOrEmpty()) {
            throw IllegalArgumentException("InfoPreference must not have a title set, use summary instead")
        }

        widgetLayoutResource = R.layout.help_icon_pref
        context.obtainStyledAttributes(attrs, R.styleable.InfoPreference).apply {
            infoUrl = getString(R.styleable.InfoPreference_infoUrl)
            recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        helpIcon = holder.itemView.findViewById(R.id.help_icon)
        infoUrl?.let {
            helpIcon?.setupHelpIcon(it, R.string.click_here_for_more_information)
        }
        helpIcon?.isVisible = infoUrl != null
    }
}
