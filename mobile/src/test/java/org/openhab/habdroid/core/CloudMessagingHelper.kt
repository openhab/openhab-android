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
