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
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.ui.PushNotificationStatus
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getRemoteUrl

object CloudMessagingHelper {
    internal var registrationDone: Boolean = false
    internal var registrationFailureReason: Throwable? = null
    private val TAG = CloudMessagingHelper::class.java.simpleName

    fun onConnectionUpdated(context: Context, connection: CloudConnection?) {
        registrationDone = false
        if (connection != null) {
            FcmRegistrationService.scheduleRegistration(context)
        }
    }

    fun onNotificationSelected(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
                NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            FcmRegistrationService.scheduleHideNotification(context, notificationId)
        }
    }

    fun needsPollingForNotifications(@Suppress("UNUSED_PARAMETER") context: Context) = false

    fun pollForNotifications(@Suppress("UNUSED_PARAMETER") context: Context) {
        // Used in foss flavor
    }

    suspend fun getPushNotificationStatus(context: Context): PushNotificationStatus {
        ConnectionFactory.waitForInitialization()
        val prefs = context.getPrefs()
        return when {
            // No remote server is configured
            prefs.getRemoteUrl(prefs.getPrimaryServerId()).isEmpty() ->
                PushNotificationStatus(
                    context.getString(R.string.push_notification_status_no_remote_configured),
                    R.drawable.ic_bell_off_outline_grey_24dp
                )
            // Cloud connection failed
            ConnectionFactory.primaryCloudConnection?.failureReason != null -> {
                val cloudFailure = ConnectionFactory.primaryCloudConnection?.failureReason
                val message = context.getString(R.string.push_notification_status_http_error,
                    context.getHumanReadableErrorMessage(
                        if (cloudFailure is HttpClient.HttpException) cloudFailure.originalUrl else "",
                        if (cloudFailure is HttpClient.HttpException) cloudFailure.statusCode else 0,
                        cloudFailure,
                        true
                    )
                )
                PushNotificationStatus(message, R.drawable.ic_bell_off_outline_grey_24dp)
            }
            // Remote server is configured, but it's not a cloud instance
            ConnectionFactory.primaryCloudConnection?.connection == null && ConnectionFactory.primaryRemoteConnection != null ->
                PushNotificationStatus(
                    context.getString(R.string.push_notification_status_remote_no_cloud),
                    R.drawable.ic_bell_off_outline_grey_24dp
                )
            // Registration isn't done yet
            !registrationDone ->
                PushNotificationStatus(
                    context.getString(R.string.info_openhab_gcm_in_progress),
                    R.drawable.ic_bell_outline_grey_24dp
                )
            // Registration failed
            registrationFailureReason != null -> {
                val gaa = GoogleApiAvailability.getInstance()
                val errorCode = gaa.isGooglePlayServicesAvailable(context)
                if (errorCode != ConnectionResult.SUCCESS) {
                    val message = context.getString(
                        R.string.info_openhab_gcm_failed_play_services,
                        gaa.getErrorString(errorCode)
                    )
                    PushNotificationStatus(message, R.drawable.ic_bell_off_outline_grey_24dp)
                } else {
                    PushNotificationStatus(
                        context.getString(R.string.info_openhab_gcm_failed),
                        R.drawable.ic_bell_off_outline_grey_24dp
                    )
                }
            }
            // Push notifications are working
            else ->
                PushNotificationStatus(
                    context.getString(R.string.info_openhab_gcm_connected),
                    R.drawable.ic_bell_ring_outline_grey_24dp
                )
        }
    }
}
