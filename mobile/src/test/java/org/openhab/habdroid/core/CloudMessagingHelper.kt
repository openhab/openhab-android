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
import androidx.annotation.StringRes

import org.openhab.habdroid.core.connection.CloudConnection

object CloudMessagingHelper {
    val pushNotificationStatusResId: Int
        @StringRes get() = 0

    val isSupported: Boolean
        get() = false

    fun onConnectionUpdated(context: Context, connection: CloudConnection?) {}

    fun onNotificationSelected(context: Context, intent: Intent) {}
}
