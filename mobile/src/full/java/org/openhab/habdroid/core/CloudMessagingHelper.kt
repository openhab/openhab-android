/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory

object CloudMessagingHelper {
    internal var registrationDone: Boolean = false
    internal var registrationFailureReason: Throwable? = null

    val pushNotificationIconResId: Int
        @DrawableRes get() {
            val cloudConnection = ConnectionFactory.getConnection(Connection.TYPE_CLOUD) as CloudConnection?
            return when {
                cloudConnection == null -> R.drawable.ic_bell_off_outline_grey_24dp
                !registrationDone -> R.drawable.ic_bell_outline_grey_24dp
                registrationFailureReason != null -> R.drawable.ic_bell_off_outline_grey_24dp
                else -> R.drawable.ic_bell_ring_outline_grey_24dp
            }
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

    fun getPushNotificationStatus(context: Context): String {
        val cloudConnection = ConnectionFactory.getConnection(Connection.TYPE_CLOUD) as CloudConnection?
        return when {
            cloudConnection == null -> {
                if (ConnectionFactory.getConnection(Connection.TYPE_REMOTE) == null) {
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
                    context.getString(R.string.info_openhab_gcm_failed_with_reason,
                            gaa.getErrorString(errorCode))
                } else {
                    context.getString(R.string.info_openhab_gcm_failed)
                }
            }
            else -> context.getString(R.string.info_openhab_gcm_connected)
        }
    }
}
