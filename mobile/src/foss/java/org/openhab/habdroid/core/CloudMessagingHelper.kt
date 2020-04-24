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
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.ui.AboutActivity
import org.openhab.habdroid.ui.PushNotificationStatus
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getString

object CloudMessagingHelper {
    private val TAG = CloudMessagingHelper::class.java.simpleName

    // FIXME: This isn't called if remote user name is change to invalid an username
    fun onConnectionUpdated(context: Context, @Suppress("UNUSED_PARAMETER") connection: CloudConnection?) {
        BackgroundTasksManager.managePeriodicTrigger(context)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onNotificationSelected(context: Context, intent: Intent) {}

    suspend fun getPushNotificationStatus(context: Context): PushNotificationStatus {
        ConnectionFactory.waitForInitialization()
        val cloudFailure = try {
            ConnectionFactory.cloudConnection
            null
        } catch (e: Exception) {
            Log.d(TAG, "Got exception: $e")
            e
        }

        return when {
            context.getPrefs().getString(PrefKeys.REMOTE_URL).isEmpty() -> PushNotificationStatus(
                context.getString(R.string.push_notification_status_no_remote_configured),
                R.drawable.ic_bell_off_outline_grey_24dp
            )
            ConnectionFactory.cloudConnectionOrNull != null -> PushNotificationStatus(
                context.getString(R.string.push_notification_status_impaired),
                R.drawable.ic_bell_ring_outline_grey_24dp,
                AboutActivity.AboutMainFragment.makeClickRedirect(
                    context,
                    "https://www.openhab.org/docs/apps/android.html#notifications-in-foss-version"
                )
            )
            cloudFailure != null -> {
                val message = context.getString(
                    R.string.push_notification_status_http_error,
                    context.getHumanReadableErrorMessage(
                        if (cloudFailure is HttpClient.HttpException) cloudFailure.originalUrl else "",
                        if (cloudFailure is HttpClient.HttpException) cloudFailure.statusCode else 0,
                        cloudFailure,
                        true
                    )
                )
                PushNotificationStatus(message, R.drawable.ic_bell_off_outline_grey_24dp)
            }
            else -> PushNotificationStatus(
                context.getString(R.string.push_notification_status_remote_no_cloud),
                R.drawable.ic_bell_off_outline_grey_24dp
            )
        }
    }
}
