/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import android.text.InputType
import android.util.AttributeSet
import androidx.fragment.app.DialogFragment
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.util.getCurrentWifiSsid

class WifiSsidInputPreference constructor(context: Context, attrs: AttributeSet) :
    CustomInputTypePreference(context, attrs) {
    override fun createDialog(): DialogFragment {
        val currentSsid = context.getCurrentWifiSsid(OpenHabApplication.DATA_ACCESS_TAG_SELECT_SERVER_WIFI)
        return PrefFragment.newInstance(
            key,
            title,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            emptyArray(),
            WhitespaceBehavior.TRIM.ordinal,
            currentSsid?.let { arrayOf(it) }
        )
    }
}
