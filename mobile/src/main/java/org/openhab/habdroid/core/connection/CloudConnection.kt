/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

package org.openhab.habdroid.core.connection

import android.util.Log

import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.util.HttpClient

class CloudConnection internal constructor(baseConnection: AbstractConnection, val messagingSenderId: String) :
    DefaultConnection(baseConnection, Connection.TYPE_CLOUD)

/**
 * Creates a [CloudConnection] instance if possible.
 *
 * It does so by checking whether the given connection supports the needed HTTP endpoints.
 * As this means causing network I/O, this method MUST NOT be called from the main thread.
 *
 * @return A cloud connection instance if the passed in connection supports the needed
 * HTTP endpoints, or null otherwise.
 */
suspend fun AbstractConnection.toCloudConnection(): CloudConnection? {
    val TAG = CloudConnection::class.java.simpleName
    return try {
        val result = httpClient.get("api/v1/settings/notifications").asText()
        val json = JSONObject(result.response)
        val senderId = json.getJSONObject("gcm").getString("senderId")
        CloudConnection(this, senderId)
    } catch (e: JSONException) {
        Log.d(TAG, "Unable to parse notification settings JSON", e)
        null
    } catch (e: HttpClient.HttpException) {
        Log.e(TAG, "Error loading notification settings: $e")
        return null
    }
}
