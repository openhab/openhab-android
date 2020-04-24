/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.model.toOH2IconResource
import org.openhab.habdroid.util.NotificationUtils
import org.openhab.habdroid.util.NotificationUtils.HAS_GROUPING_SUPPORT
import org.openhab.habdroid.util.NotificationUtils.SUMMARY_NOTIFICATION_ID
import org.openhab.habdroid.util.NotificationUtils.createChannelForSeverity
import org.openhab.habdroid.util.NotificationUtils.getChannelId
import org.openhab.habdroid.util.NotificationUtils.getGcmNotificationCount
import org.openhab.habdroid.util.NotificationUtils.makeNotification
import org.openhab.habdroid.util.NotificationUtils.makeSummaryNotification

class MessageListenerService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmRegistrationService.scheduleRegistration(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val data = message.data
        val messageType = data["type"] ?: return
        val notificationId = data[NotificationUtils.EXTRA_NOTIFICATION_ID]?.toInt() ?: 1

        when (messageType) {
            "notification" -> {
                val messageText = data["message"]
                val severity = data["severity"]
                val icon = data["icon"].toOH2IconResource()
                val persistedId = data["persistedId"]
                // Older versions of openhab-cloud didn't send the notification generation
                // timestamp, so use the (undocumented) google.sent_time as a time reference
                // in that case. If that also isn't present, don't show time at all.
                val timestamp = data["timestamp"]?.toLong() ?: message.sentTime
                createChannelForSeverity(this, severity, nm)

                val n = runBlocking {
                    makeNotification(
                        this@MessageListenerService,
                        messageText,
                        getChannelId(severity),
                        icon,
                        timestamp,
                        persistedId,
                        notificationId,
                        FcmRegistrationService.createHideNotificationIntent(this@MessageListenerService, notificationId)
                    )
                }
                nm.notify(notificationId, n)

                if (HAS_GROUPING_SUPPORT) {
                    val count = getGcmNotificationCount(nm.activeNotifications)
                    nm.notify(
                        SUMMARY_NOTIFICATION_ID,
                        makeSummaryNotification(
                            this,
                            count,
                            timestamp,
                            FcmRegistrationService.createHideNotificationIntent(this, SUMMARY_NOTIFICATION_ID)
                        )
                    )
                }
            }
            "hideNotification" -> {
                nm.cancel(notificationId)
                if (HAS_GROUPING_SUPPORT) {
                    val active = nm.activeNotifications
                    if (notificationId != SUMMARY_NOTIFICATION_ID && getGcmNotificationCount(active) == 0) {
                        // Cancel summary when removing the last sub-notification
                        nm.cancel(SUMMARY_NOTIFICATION_ID)
                    } else if (notificationId == SUMMARY_NOTIFICATION_ID) {
                        // Cancel all sub-notifications when removing the summary
                        for (n in active) {
                            nm.cancel(n.id)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun checkForMessages(@Suppress("UNUSED_PARAMETER") context: Context) {
            // Used in foss flavor
        }

        fun isRequired() = false
    }
}
