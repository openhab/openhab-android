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

package org.openhab.habdroid.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.NotACloudServerException
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.model.toCloudMessage
import org.openhab.habdroid.ui.CloudNotificationListFragment
import org.openhab.habdroid.ui.preference.PushNotificationStatus
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getRemoteUrl
import org.openhab.habdroid.util.map

object CloudMessagingHelper {
    private val TAG = CloudMessagingHelper::class.java.simpleName

    @Suppress("UNUSED_PARAMETER")
    fun onConnectionUpdated(context: Context, connection: CloudConnection?) {}

    @Suppress("UNUSED_PARAMETER")
    fun onNotificationSelected(context: Context, intent: Intent) {}

    fun isPollingBuild() = true

    fun needsPollingForNotifications(context: Context) =
        context.getPrefs().getBoolean(PrefKeys.FOSS_NOTIFICATIONS_ENABLED, false)

    suspend fun pollForNotifications(context: Context) {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.primaryCloudConnection?.connection
        if (connection == null) {
            Log.d(TAG, "No connection for loading notifications")
            return
        }

        val notifHelper = NotificationHelper(context)
        loadNewMessages(context, connection)
            // Reverse list, so old notifications are processed first and can be hidden by newer notifications.
            ?.reversed()
            ?.forEach { notifHelper.handleNewCloudMessage(it) }
    }

    private suspend fun loadNewMessages(context: Context, connection: Connection): List<CloudMessage>? {
        val url = "api/v1/notifications?limit=${CloudNotificationListFragment.PAGE_SIZE}"
        val messages = try {
            val response = connection.httpClient.get(url).asText().response
            Log.d(TAG, "Notifications request success")
            JSONArray(response).map { obj -> obj.toCloudMessage() }.filterNotNull()
        } catch (e: JSONException) {
            Log.d(TAG, "Notification response could not be parsed", e)
            return null
        } catch (e: HttpClient.HttpException) {
            Log.e(TAG, "Notifications request failure", e)
            return null
        }

        val prefs = context.getPrefs()
        val lastSeenMessageId = prefs.getString(PrefKeys.FOSS_LAST_SEEN_MESSAGE, null)
        prefs.edit {
            val newestSeenId = messages.firstOrNull()?.id?.persistedId ?: lastSeenMessageId
            putString(PrefKeys.FOSS_LAST_SEEN_MESSAGE, newestSeenId)
        }
        if (lastSeenMessageId == null) {
            // Never checked for notifications before
            Log.d(TAG, "First notification check")
            return null
        }

        val lastSeenIndex = messages.map { msg -> msg.id.persistedId }.indexOf(lastSeenMessageId)
        return if (lastSeenIndex >= 0) messages.subList(0, lastSeenIndex) else messages
    }

    suspend fun getPushNotificationStatus(context: Context): PushNotificationStatus {
        ConnectionFactory.waitForInitialization()
        val cloudFailure = ConnectionFactory.primaryCloudConnection?.failureReason
        val prefs = context.getPrefs()
        return when {
            !prefs.getBoolean(PrefKeys.FOSS_NOTIFICATIONS_ENABLED, false) -> PushNotificationStatus(
                context.getString(R.string.push_notification_status_disabled),
                R.drawable.ic_bell_off_outline_grey_24dp,
                false
            )

            prefs.getRemoteUrl(prefs.getPrimaryServerId()).isEmpty() -> PushNotificationStatus(
                context.getString(R.string.push_notification_status_no_remote_configured),
                R.drawable.ic_bell_off_outline_grey_24dp,
                false
            )

            ConnectionFactory.primaryCloudConnection?.connection != null -> PushNotificationStatus(
                context.getString(R.string.push_notification_status_impaired),
                R.drawable.ic_bell_ring_outline_grey_24dp,
                false
            )

            cloudFailure != null && cloudFailure !is NotACloudServerException -> {
                val message = context.getString(
                    R.string.push_notification_status_http_error,
                    context.getHumanReadableErrorMessage(
                        if (cloudFailure is HttpClient.HttpException) cloudFailure.originalUrl else "",
                        if (cloudFailure is HttpClient.HttpException) cloudFailure.statusCode else 0,
                        cloudFailure,
                        true
                    )
                )
                PushNotificationStatus(message, R.drawable.ic_bell_off_outline_grey_24dp, true)
            }

            else -> PushNotificationStatus(
                context.getString(R.string.push_notification_status_remote_no_cloud),
                R.drawable.ic_bell_off_outline_grey_24dp,
                false
            )
        }
    }
}
