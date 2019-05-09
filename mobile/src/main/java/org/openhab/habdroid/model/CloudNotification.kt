/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.json.JSONException
import org.json.JSONObject

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.TimeZone

@Parcelize
data class CloudNotification(val id: String, val message: String, val createdTimestamp: Long,
                             val icon: String?, val severity: String?): Parcelable {
    companion object {
        @Throws(JSONException::class)
        fun fromJson(jsonObject: JSONObject): CloudNotification {
            var created: Long = 0
            if (jsonObject.has("created")) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'")
                format.timeZone = TimeZone.getTimeZone("UTC")
                try {
                    created = format.parse(jsonObject.getString("created")).time
                } catch (e: ParseException) {
                    // keep created at 0
                }

            }

            return CloudNotification(jsonObject.getString("_id"),
                    jsonObject.getString("message"),
                    created,
                    jsonObject.optString("icon", null),
                    jsonObject.optString("severity", null));
        }
    }
}

