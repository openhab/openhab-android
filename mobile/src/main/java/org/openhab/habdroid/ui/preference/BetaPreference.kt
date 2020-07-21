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
import android.widget.TextView
import androidx.core.view.isGone
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.openhab.habdroid.R

class BetaPreference constructor(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private var betaTag: TextView? = null
    private var showBetaTag: Boolean = true

    init {
        widgetLayoutResource = R.layout.pref_beta
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        if (holder != null) {
            betaTag = holder.itemView.findViewById(R.id.beta_tag)
            betaTag?.isGone = !showBetaTag
        }
    }

    fun changeBetaTagVisibility(show: Boolean) {
        showBetaTag = show
        betaTag?.isGone = !showBetaTag
    }
}
