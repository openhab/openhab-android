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
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.NotificationHelper.Companion.NOTIFICATION_ACTION_EXTRA
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
        val cna = IntentCompat.getParcelableExtra(
            intent,
            NOTIFICATION_ACTION_EXTRA,
            CloudNotificationAction::class.java
        ) ?: return
        Log.d(TAG, "Received action from $notificationId: $cna")

        when (val action = cna.action) {
            is CloudNotificationAction.Action.ItemCommandAction ->
                BackgroundTasksManager.enqueueNotificationAction(context, action)
            is CloudNotificationAction.Action.UrlAction ->
                action.url.toUri().openInBrowser(context)
            else -> {
                // TODO
                Log.e(TAG, "Not yet implemented")
            }
        }
    }

    companion object {
        private val TAG = NotificationActionReceiver::class.java.simpleName
    }
}
