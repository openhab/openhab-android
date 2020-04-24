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

import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.toCloudNotification
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.NotificationUtils.HAS_GROUPING_SUPPORT
import org.openhab.habdroid.util.NotificationUtils.SUMMARY_NOTIFICATION_ID
import org.openhab.habdroid.util.NotificationUtils.createChannelForSeverity
import org.openhab.habdroid.util.NotificationUtils.getChannelId
import org.openhab.habdroid.util.NotificationUtils.getGcmNotificationCount
import org.openhab.habdroid.util.NotificationUtils.makeNotification
import org.openhab.habdroid.util.NotificationUtils.makeSummaryNotification
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.map

class MessageListenerService {
    companion object {
        private val TAG = MessageListenerService::class.java.simpleName

        suspend fun checkForMessages(context: Context) {
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

            val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            messages.forEach { message ->
                // The notification id can only be int, while the message id is way larger
                val notificationId = message.id.substring(message.id.length - 5).toInt(16)

                val severity = message.severity
                val timestamp = message.createdTimestamp
                createChannelForSeverity(context, severity, nm)

                val n = runBlocking {
                    makeNotification(
                        context,
                        message.message,
                        getChannelId(severity),
                        message.icon,
                        timestamp,
                        message.id,
                        notificationId,
                        null
                    )
                }
                nm.notify(notificationId, n)

                if (HAS_GROUPING_SUPPORT) {
                    val count = getGcmNotificationCount(nm.activeNotifications)
                    nm.notify(SUMMARY_NOTIFICATION_ID, makeSummaryNotification(context, count, timestamp, null))
                }
            }
        }

        fun isRequired(): Boolean {
            val required = ConnectionFactory.cloudConnectionOrNull != null
            Log.d(TAG, "isRequired(): $required")
            return required
        }
    }
}
