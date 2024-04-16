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

package org.openhab.habdroid.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.optStringOrNull

@Parcelize
data class LabeledValue internal constructor(
    val value: String,
    val label: String,
    val icon: IconResource?,
    val row: Int,
    val column: Int
) : Parcelable

@Throws(JSONException::class)
fun JSONObject.toLabeledValue(valueKey: String, labelKey: String): LabeledValue {
    val value = getString(valueKey)
    val label = optString(labelKey, value)
    val icon = optStringOrNull("icon")?.toOH2IconResource()
    return LabeledValue(value, label, icon, optInt("row"), optInt("column"))
}
