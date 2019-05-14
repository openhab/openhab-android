package org.openhab.habdroid.core.connection

import android.util.Log

import org.json.JSONException
import org.json.JSONObject

class CloudConnection internal constructor(baseConnection: AbstractConnection, val messagingSenderId: String) :
        DefaultConnection(baseConnection, Connection.TYPE_CLOUD) {
}

/**
 * Creates a [CloudConnection] instance if possible.
 *
 * It does so by checking whether the given connection supports the needed HTTP endpoints.
 * As this means causing network I/O, this method MUST NOT be called from the main thread.
 *
 * @return  A cloud connection instance if the passed in connection supports the needed
 * HTTP endpoints, or null otherwise.
 */
fun AbstractConnection.toCloudConnection(): CloudConnection? {
    val TAG = CloudConnection::class.java.simpleName
    val result = syncHttpClient.get("api/v1/settings/notifications").asText()
    if (!result.isSuccessful) {
        Log.e(TAG, "Error loading notification settings: " + result.error)
        return null
    }

    try {
        val json = JSONObject(result.response)
        val senderId = json.getJSONObject("gcm").getString("senderId")
        return CloudConnection(this, senderId)
    } catch (e: JSONException) {
        Log.d(TAG, "Unable to parse notification settings JSON", e)
        return null
    }
}
