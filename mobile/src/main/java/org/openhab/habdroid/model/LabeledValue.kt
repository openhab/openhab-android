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

package org.openhab.habdroid.model

import android.os.Parcelable

import kotlinx.android.parcel.Parcelize
import org.json.JSONException
import org.json.JSONObject

@Parcelize
data class LabeledValue internal constructor(val value: String, val label: String) : Parcelable

@Throws(JSONException::class)
fun JSONObject.toLabeledValue(keyName: String, valueName: String): LabeledValue {
    return LabeledValue(getString(keyName), getString(valueName))
}
