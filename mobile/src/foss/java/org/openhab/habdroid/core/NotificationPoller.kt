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

package org.openhab.habdroid.core

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.toCloudNotification
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.map

object NotificationPoller {
    private val TAG = NotificationPoller::class.java.simpleName
    private const val LAST_SEEN_MESSAGE_KEY = "foss_last_seen_message"

    suspend fun checkForNewNotifications(context: Context) {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.cloudConnectionOrNull
        if (connection == null) {
            Log.d(TAG, "Got no connection")
            return
        }
        val url = "api/v1/notifications?limit=20"
        var messages = try {
            val response = connection.httpClient.get(url).asText().response
            Log.d(TAG, "Notifications request success")
            JSONArray(response).map { obj -> obj.toCloudNotification() }
        } catch (e: JSONException) {
            Log.d(TAG, "Notification response could not be parsed", e)
            null
        } catch (e: HttpClient.HttpException) {
            Log.e(TAG, "Notifications request failure", e)
            null
        } ?: return

        val prefs = context.getPrefs()

        val lastSeenMessageId = prefs.getString(LAST_SEEN_MESSAGE_KEY, null)
        prefs.edit {
            val newestSeenId = messages.firstOrNull()?.id ?: lastSeenMessageId
            putString(LAST_SEEN_MESSAGE_KEY, newestSeenId)
        }
        if (lastSeenMessageId == null) {
            // Never checked for notifications before
            Log.d(TAG, "First message check")
            return
        }

        val lastSeenIndex = messages.map { msg -> msg.id }.indexOf(lastSeenMessageId)
        val newMessages = if (lastSeenIndex >= 0) messages.subList(0, lastSeenIndex) else messages
        val notifHelper = NotificationHelper(context)

        newMessages.forEach { message ->
            notifHelper.showNotification(
                message.id.hashCode(),
                message,
                null,
                null
            )
        }
    }
}
