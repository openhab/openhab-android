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
import android.content.Intent
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.core.connection.NotACloudServerException
import org.openhab.habdroid.ui.PushNotificationStatus
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getRemoteUrl

object CloudMessagingHelper {
    @Suppress("UNUSED_PARAMETER")
    fun onConnectionUpdated(context: Context, connection: CloudConnection?) {}

    @Suppress("UNUSED_PARAMETER")
    fun onNotificationSelected(context: Context, intent: Intent) {}

    fun needsPollingForNotifications(context: Context) =
        context.getPrefs().getBoolean(PrefKeys.FOSS_NOTIFICATIONS_ENABLED, false)

    suspend fun pollForNotifications(context: Context) {
        NotificationPoller.checkForNewNotifications(context)
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
