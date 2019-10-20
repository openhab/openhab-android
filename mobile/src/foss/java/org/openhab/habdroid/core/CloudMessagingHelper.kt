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

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes

import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection

@Suppress("UNUSED_PARAMETER")
object CloudMessagingHelper {

    val pushNotificationIconResId: Int
        @DrawableRes get() = R.drawable.ic_bell_off_outline_grey_24dp

    val isSupported: Boolean
        get() = false

    fun onConnectionUpdated(context: Context, connection: CloudConnection?) {}

    fun onNotificationSelected(context: Context, intent: Intent) {}

    fun getPushNotificationStatus(context: Context): String {
        return context.getString(R.string.info_openhab_notification_status_unavailable)
    }

    fun clearAllNotifications(context: Context) {}
}
