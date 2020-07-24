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
import android.util.AttributeSet
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.setupHelpIcon
import org.openhab.habdroid.ui.updateHelpIconAlpha

class PrimaryServerPreference constructor(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private var helpIcon: ImageView? = null

    init {
        widgetLayoutResource = R.layout.help_icon_pref
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        if (holder != null) {
            helpIcon = holder.itemView.findViewById(R.id.help_icon)
            helpIcon?.setupHelpIcon(context.getString(R.string.settings_server_primary_url),
                R.string.click_here_for_more_information)
            helpIcon?.updateHelpIconAlpha(isEnabled)
        }
    }

    override fun onDependencyChanged(dependency: Preference, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        helpIcon?.updateHelpIconAlpha(isEnabled)
    }
}
