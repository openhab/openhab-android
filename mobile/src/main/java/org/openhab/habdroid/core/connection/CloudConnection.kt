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

package org.openhab.habdroid.core.connection

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.AbstractConnection.Companion.TAG
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
@Throws(Exception::class)
suspend fun AbstractConnection.toCloudConnection(): CloudConnection {
    val result = try {
        httpClient.get("api/v1/settings/notifications").asText()
    } catch (e: HttpClient.HttpException) {
        throw if (e.statusCode == 404) NotACloudServerException() else e
    }
    val senderId = try {
        val json = JSONObject(result.response)
        json.getJSONObject("gcm").getString("senderId")
    } catch (e: JSONException) {
        Log.e(TAG, "Error parsing notification endpoint response", e)
        throw NotACloudServerException()
    }

    return CloudConnection(this, senderId)
}
