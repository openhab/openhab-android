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

package org.openhab.habdroid.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import java.util.Locale

object Util {
    val TAG: String = Util::class.java.simpleName

    val isFlavorStable get() = BuildConfig.FLAVOR.toLowerCase(Locale.ROOT).contains("stable")
    val isFlavorBeta get() = !isFlavorStable
    val isFlavorFull get() = BuildConfig.FLAVOR.toLowerCase(Locale.ROOT).contains("full")
    val isFlavorFoss get() = !isFlavorFull

    @StyleRes
    fun getActivityThemeId(context: Context): Int {
        return when (context.getPrefs().getInt(Constants.PREFERENCE_ACCENT_COLOR, 0)) {
            ContextCompat.getColor(context, R.color.indigo_500) -> R.style.openHAB_DayNight_basicui
            ContextCompat.getColor(context, R.color.blue_grey_700) -> R.style.openHAB_DayNight_grey
            else -> R.style.openHAB_DayNight_orange
        }
    }

    fun isEmulator(): Boolean {
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MODEL.contains("sdk_phone_armv7") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT
        Log.d(TAG, "Device is emulator: $isEmulator")
        return isEmulator
    }
}
