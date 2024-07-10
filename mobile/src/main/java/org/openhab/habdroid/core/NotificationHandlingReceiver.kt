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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.model.CloudNotificationAction
import org.openhab.habdroid.model.CloudNotificationId
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.PendingIntent_Immutable
import org.openhab.habdroid.util.openInBrowser

class NotificationHandlingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive(): $intent")
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        when (intent.action) {
            ACTION_DISMISSED -> {
                Log.d(TAG, "Dismissed notification $notificationId")
                NotificationHelper(context).updateGroupNotification()
            }
            ACTION_NOTIF_ACTION -> {
                val cna = IntentCompat.getParcelableExtra(
                    intent,
                    EXTRA_NOTIFICATION_ACTION,
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
        }
    }

    companion object {
        private val TAG = NotificationHandlingReceiver::class.java.simpleName

        const val ACTION_DISMISSED = "${BuildConfig.APPLICATION_ID}.action.NOTIFICATION_DISMISSED"
        const val ACTION_NOTIF_ACTION = "${BuildConfig.APPLICATION_ID}.action.NOTIFICATION_ACTION"

        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_NOTIFICATION_ACTION = "notification_action"

        fun createDismissedPendingIntent(context: Context, notificationId: Int): PendingIntent {
            val intent = Intent(context, NotificationHandlingReceiver::class.java).apply {
                action = ACTION_DISMISSED
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent_Immutable
            )
        }

        fun createActionPendingIntent(
            context: Context,
            notificationId: CloudNotificationId,
            cna: CloudNotificationAction
        ): PendingIntent = when (val cnaAction = cna.action) {
            is CloudNotificationAction.Action.UiCommandAction -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(MainActivity.EXTRA_UI_COMMAND, cnaAction.command)
                }
                PendingIntent.getActivity(
                    context,
                    notificationId.notificationId + cna.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent_Immutable
                )
            }
            else -> {
                val intent = Intent(context, NotificationHandlingReceiver::class.java).apply {
                    action = ACTION_NOTIF_ACTION
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_NOTIFICATION_ACTION, cna)
                }
                PendingIntent.getBroadcast(
                    context,
                    notificationId.notificationId + cna.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent_Immutable
                )
            }
        }
    }
}
