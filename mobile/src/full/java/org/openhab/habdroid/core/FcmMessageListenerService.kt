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

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.model.CloudMessage
import org.openhab.habdroid.model.CloudNotificationAction
import org.openhab.habdroid.model.CloudNotificationId
import org.openhab.habdroid.model.toCloudNotificationAction
import org.openhab.habdroid.model.toOH2IconResource
import org.openhab.habdroid.util.map
import org.openhab.habdroid.util.toJsonArrayOrNull

class FcmMessageListenerService : FirebaseMessagingService() {
    private lateinit var notifHelper: NotificationHelper

    override fun onCreate() {
        Log.d(TAG, "onCreate()")
        super.onCreate()
        notifHelper = NotificationHelper(this)
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "onNewToken()")
        super.onNewToken(token)
        FcmRegistrationWorker.scheduleRegistration(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.d(TAG, "onMessageReceived with data $data")

        val cloudMessage: CloudMessage? = when (data["type"]) {
            "notification" -> {
                val actions = data["actions"]
                    ?.toJsonArrayOrNull()
                    ?.map { it.toCloudNotificationAction() }
                    ?.filterNotNull()
                CloudMessage.CloudNotification(
                    id = CloudNotificationId(data["persistedId"].orEmpty(), data["reference-id"]),
                    title = data["title"].orEmpty(),
                    message = data["message"].orEmpty(),
                    // Older versions of openhab-cloud didn't send the notification generation
                    // timestamp, so use the (undocumented) google.sent_time as a time reference
                    // in that case. If that also isn't present, don't show time at all.
                    createdTimestamp = data["timestamp"]?.toLong() ?: message.sentTime,
                    icon = data["icon"].toOH2IconResource(),
                    tag = data["tag"],
                    actions = actions,
                    onClickAction = data["on-click"]?.let { CloudNotificationAction("", it) },
                    mediaAttachmentUrl = data["media-attachment-url"]
                )
            }

            "hideNotification" -> {
                CloudMessage.CloudHideNotificationRequest(
                    id = CloudNotificationId(data["persistedId"].orEmpty(), data["reference-id"]),
                    tag = data["tag"]
                )
            }

            else -> null
        }

        // onMessageReceived is called from a background thread, so runBlocking is OK here
        runBlocking {
            cloudMessage?.let { notifHelper.handleNewCloudMessage(it) }
        }
    }

    companion object {
        private val TAG = FcmMessageListenerService::class.java.simpleName
    }
}
