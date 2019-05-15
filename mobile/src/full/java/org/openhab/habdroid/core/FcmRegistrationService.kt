/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import androidx.core.app.JobIntentService

import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.Util

import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

class FcmRegistrationService : JobIntentService() {

    /**
     * @author https://stackoverflow.com/a/12707479
     */
    private val deviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
                capitalize(model)
            } else {
                capitalize(manufacturer) + " " + model
            }
        }

    override fun onHandleWork(intent: Intent) {
        ConnectionFactory.waitForInitialization()
        val connection = ConnectionFactory.getConnection(Connection.TYPE_CLOUD) as CloudConnection?
                ?: return

        when (intent.action) {
            ACTION_REGISTER -> {
                try {
                    registerFcm(connection)
                } catch (e: IOException) {
                    CloudMessagingHelper.registrationFailureReason = e
                    Log.e(TAG, "FCM registration failed", e)
                }

                CloudMessagingHelper.registrationDone = true
            }
            ACTION_HIDE_NOTIFICATION -> {
                val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (id >= 0) {
                    sendHideNotificationRequest(id, connection.messagingSenderId)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun registerFcm(connection: CloudConnection) {
        val token = FirebaseInstanceId.getInstance().getToken(connection.messagingSenderId,
                FirebaseMessaging.INSTANCE_ID_SCOPE)
        val deviceName = deviceName + if (Util.isFlavorBeta) " (" + getString(R.string.beta) + ")" else ""
        val deviceId = Settings.Secure.getString(contentResolver,
                Settings.Secure.ANDROID_ID) + if (Util.isFlavorBeta) "-beta" else ""

        val regUrl = String.format(Locale.US,
                "addAndroidRegistration?deviceId=%s&deviceModel=%s&regId=%s",
                deviceId, URLEncoder.encode(deviceName, "UTF-8"), token)

        Log.d(TAG, "Register device at openHAB-cloud with URL: $regUrl")
        val result = connection.syncHttpClient[regUrl].asStatus()
        if (result.isSuccessful) {
            Log.d(TAG, "FCM reg id success")
        } else {
            Log.e(TAG, "FCM reg id error: " + result.error)
        }
        CloudMessagingHelper.registrationFailureReason = result.error
    }

    /**
     * @author https://stackoverflow.com/a/12707479
     */
    private fun capitalize(s: String): String {
        val first = s.elementAtOrNull(0) ?: return ""
        return if (Character.isUpperCase(first)) {
            s
        } else {
            Character.toUpperCase(first) + s.substring(1)
        }
    }

    private fun sendHideNotificationRequest(notificationId: Int, senderId: String) {
        val fcm = FirebaseMessaging.getInstance()
        val message = RemoteMessage.Builder("$senderId@gcm.googleapis.com")
                .addData("type", "hideNotification")
                .addData("notificationId", notificationId.toString())
                .build()
        fcm.send(message)
    }

    class ProxyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val actual = intent.getParcelableExtra<Intent>("intent")
            enqueueWork(context, FcmRegistrationService::class.java, JOB_ID, actual)
        }

        companion object {
            internal fun wrap(context: Context, intent: Intent, id: Int): PendingIntent {
                val wrapped = Intent(context, ProxyReceiver::class.java)
                        .putExtra("intent", intent)
                return PendingIntent.getBroadcast(context, id,
                        wrapped, PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }
    }

    companion object {
        private val TAG = FcmRegistrationService::class.java.simpleName

        private val JOB_ID = 1000

        private val ACTION_REGISTER = "org.openhab.habdroid.action.REGISTER_GCM"
        private val ACTION_HIDE_NOTIFICATION = "org.openhab.habdroid.action.HIDE_NOTIFICATION"
        private val EXTRA_NOTIFICATION_ID = "notificationId"

        internal fun scheduleRegistration(context: Context) {
            val intent = Intent(context, FcmRegistrationService::class.java)
                    .setAction(ACTION_REGISTER)
            enqueueWork(context, FcmRegistrationService::class.java, JOB_ID, intent)
        }

        internal fun scheduleHideNotification(context: Context, notificationId: Int) {
            enqueueWork(context, FcmRegistrationService::class.java, JOB_ID,
                    makeHideNotificationIntent(context, notificationId))
        }

        internal fun createHideNotificationIntent(context: Context, notificationId: Int): PendingIntent {
            return ProxyReceiver.wrap(context, makeHideNotificationIntent(context, notificationId),
                    notificationId)
        }

        private fun makeHideNotificationIntent(context: Context, notificationId: Int): Intent {
            return Intent(context, FcmRegistrationService::class.java)
                    .setAction(ACTION_HIDE_NOTIFICATION)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
    }
}
