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

package org.openhab.habdroid.core

import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import androidx.annotation.DrawableRes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory

object CloudMessagingHelper {
    internal var registrationDone: Boolean = false
    internal var registrationFailureReason: Throwable? = null

    val pushNotificationIconResId: Int @DrawableRes get() = when {
        ConnectionFactory.cloudConnection == null -> R.drawable.ic_bell_off_outline_grey_24dp
        !registrationDone -> R.drawable.ic_bell_outline_grey_24dp
        registrationFailureReason != null -> R.drawable.ic_bell_off_outline_grey_24dp
        else -> R.drawable.ic_bell_ring_outline_grey_24dp
    }

    val isSupported get() = true

    fun onConnectionUpdated(context: Context, connection: CloudConnection?) {
        registrationDone = false
        if (connection != null) {
            FcmRegistrationService.scheduleRegistration(context)
        }
    }

    fun onNotificationSelected(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
                FcmMessageListenerService.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            FcmRegistrationService.scheduleHideNotification(context, notificationId)
        }
    }

    fun getPushNotificationStatus(context: Context): String = when {
        ConnectionFactory.cloudConnection == null -> {
            if (ConnectionFactory.remoteConnection == null) {
                context.getString(R.string.info_openhab_gcm_no_remote)
            } else {
                context.getString(R.string.info_openhab_gcm_unsupported)
            }
        }
        !registrationDone -> context.getString(R.string.info_openhab_gcm_in_progress)
        registrationFailureReason != null -> {
            val gaa = GoogleApiAvailability.getInstance()
            val errorCode = gaa.isGooglePlayServicesAvailable(context)
            if (errorCode != ConnectionResult.SUCCESS) {
                context.getString(R.string.info_openhab_gcm_failed_with_reason, gaa.getErrorString(errorCode))
            } else {
                context.getString(R.string.info_openhab_gcm_failed)
            }
        }
        else -> context.getString(R.string.info_openhab_gcm_connected)
    }

    // Used to clear all notifications from statusbar
    fun clearAllNotifications(context: Context?) {
        val nm = context?.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        // FcmRegistrationService.scheduleHideNotification(context, 0)
    }
}
