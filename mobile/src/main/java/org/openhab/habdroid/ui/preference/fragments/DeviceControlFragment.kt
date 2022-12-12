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

package org.openhab.habdroid.ui.preference.fragments

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import org.openhab.habdroid.R
import org.openhab.habdroid.util.PrefKeys

@RequiresApi(Build.VERSION_CODES.R)
class DeviceControlFragment : AbstractSettingsFragment() {
    override val titleResId: Int @StringRes get() = R.string.device_control

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_device_control)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            preferenceScreen.removePreferenceRecursively(PrefKeys.DEVICE_CONTROL_AUTH_REQUIRED)
        }
    }
}
