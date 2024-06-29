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
import androidx.core.net.toUri
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.NotificationHelper.Companion.NOTIFICATION_ACTION_ACTION
import org.openhab.habdroid.core.NotificationHelper.Companion.NOTIFICATION_ACTION_LABEL
import org.openhab.habdroid.core.NotificationHelper.Companion.NOTIFICATION_ID_EXTRA
import org.openhab.habdroid.model.CloudNotificationAction
import org.openhab.habdroid.util.openInBrowser

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive(): $intent")
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_EXTRA, 0)
        if (notificationId == 0) {
            return
        }
        val rawAction = intent.getStringExtra(NOTIFICATION_ACTION_ACTION) ?: return
        val label = intent.getStringExtra(NOTIFICATION_ACTION_LABEL) ?: return
        val action = CloudNotificationAction(label, rawAction)
        Log.d(TAG, "Received action from $notificationId: $action")
        executeAction(context, action)
    }

    companion object {
        private val TAG = NotificationActionReceiver::class.java.simpleName

        fun executeAction(context: Context, action: CloudNotificationAction) {
            when {
                action.action.startsWith("command:") -> {
                    BackgroundTasksManager.enqueueNotificationAction(context, action)
                }
                action.action.startsWith("http://") || action.action.startsWith("https://") -> {
                    action.action.toUri().openInBrowser(context)
                } else -> {
                    // TODO
                    Log.e(TAG, "Not yet implemented")
                }
            }
        }
    }
}
