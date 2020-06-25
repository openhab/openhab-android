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

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.model.CloudNotification
import org.openhab.habdroid.model.toOH2IconResource

class FcmMessageListenerService : FirebaseMessagingService() {
    private lateinit var notifHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notifHelper = NotificationHelper(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmRegistrationService.scheduleRegistration(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val messageType = data["type"] ?: return
        val notificationId = data["notificationId"]?.toInt() ?: 1

        when (messageType) {
            "notification" -> {
                val cloudNotification = CloudNotification(
                    data["persistedId"].orEmpty(),
                    data["message"].orEmpty(),
                    // Older versions of openhab-cloud didn't send the notification generation
                    // timestamp, so use the (undocumented) google.sent_time as a time reference
                    // in that case. If that also isn't present, don't show time at all.
                    data["timestamp"]?.toLong() ?: message.sentTime,
                    data["icon"].toOH2IconResource(),
                    data["severity"]
                )

                runBlocking {
                    val context = this@FcmMessageListenerService
                    notifHelper.showNotification(
                        notificationId,
                        cloudNotification,
                        FcmRegistrationService.createHideNotificationIntent(context, notificationId),
                        FcmRegistrationService.createHideNotificationIntent(context,
                            NotificationHelper.SUMMARY_NOTIFICATION_ID)
                    )
                }
            }
            "hideNotification" -> {
                notifHelper.cancelNotification(notificationId)
            }
        }
    }
}
