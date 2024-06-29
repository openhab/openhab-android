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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.openhab.habdroid.core.NotificationHelper.Companion.NOTIFICATION_ID_EXTRA

class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive(): $intent")
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1)
        if (notificationId < 0) {
            return
        }
        Log.d(TAG, "Dismissed notification $notificationId")
        NotificationHelper(context).updateGroupNotification()
    }

    companion object {
        private val TAG = NotificationDismissedReceiver::class.java.simpleName
    }
}
