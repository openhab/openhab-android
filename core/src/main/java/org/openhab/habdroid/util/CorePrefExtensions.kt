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

package org.openhab.habdroid.util

import android.content.SharedPreferences
import org.openhab.habdroid.model.IconFormat
import org.openhab.habdroid.model.ServerProperties

fun SharedPreferences.getIconFormat(): IconFormat {
    val serverProps = getInt(PrefKeys.PREV_SERVER_FLAGS, 0)
    if (serverProps and ServerProperties.SERVER_FLAG_SUPPORTS_ANY_FORMAT_ICON != 0) {
        return IconFormat.Svg
    }
    val formatString = getStringOrFallbackIfEmpty(PrefKeys.ICON_FORMAT, "PNG")
    return if (formatString == "SVG") IconFormat.Svg else IconFormat.Png
}

fun SharedPreferences.getChartScalingFactor(): Float = getFloat(PrefKeys.CHART_SCALING, 1.5F)

fun SharedPreferences.shouldRequestHighResChart(): Boolean = getBoolean(PrefKeys.CHART_HQ, true)

fun SharedPreferences.getStringOrNull(key: String): String? = getString(key, null)
fun SharedPreferences.getStringOrFallbackIfEmpty(key: String, fallback: String): String {
    val value = getStringOrNull(key)
    return if (value.isNullOrEmpty()) fallback else value
}
