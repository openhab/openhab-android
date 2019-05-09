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

import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection

object CloudMessagingHelper {

    val pushNotificationIconResId: Int
        @DrawableRes get() = R.drawable.ic_bell_off_outline_grey_24dp

    val isSupported: Boolean
        get() = false

    fun onConnectionUpdated(context: Context, connection: CloudConnection) {}

    fun onNotificationSelected(context: Context, intent: Intent) {}

    fun getPushNotificationStatus(context: Context): String {
        return context.getString(R.string.info_openhab_notification_status_unavailable)
    }
}
