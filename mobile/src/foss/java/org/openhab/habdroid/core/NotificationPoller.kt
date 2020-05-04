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

        val lastShownMessage = prefs.getString(PrefKeys.FOSS_LAST_SHOWN_MESSAGE, "-1")!!.toBigInteger(16)
        if (lastShownMessage == (-1).toBigInteger()) {
            Log.d(TAG, "First message check")
            prefs.edit {
                putString(PrefKeys.FOSS_LAST_SHOWN_MESSAGE, messages.firstOrNull()?.id ?: "0")
            }
            return
        }

        messages = messages.filter {
            it.id.toBigInteger(16) > lastShownMessage
        }
        if (messages.isEmpty()) {
            Log.d(TAG, "No new messages")
            return
        }

        prefs.edit {
            putString(PrefKeys.FOSS_LAST_SHOWN_MESSAGE, messages.first().id)
        }

        val notifHelper = NotificationHelper(context)
        messages.forEach { message ->
            // The notification id can only be int, while the message id is way larger
            val notificationId = message.id.substring(message.id.length - 5).toInt(16)
            notifHelper.showNotification(
                notificationId,
                message,
                null,
                null
            )
        }
    }
}
