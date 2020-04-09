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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.JobIntentService
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.R
import org.openhab.habdroid.core.CloudMessagingHelper.getPushNotificationStatus
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.showToast
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

class FcmRegistrationService : JobIntentService() {
    /**
     * @author https://stackoverflow.com/a/12707479
     */
    private val deviceName: String get() {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        val actualModel = if (model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT)))
            model else "$manufacturer $model"

        // Capitalize returned value
        val first = actualModel.elementAtOrNull(0)
        return when {
            first == null -> ""
            Character.isUpperCase(first) -> actualModel
            else -> Character.toUpperCase(first) + actualModel.substring(1)
        }
    }

    override fun onHandleWork(intent: Intent) {
        runBlocking {
            ConnectionFactory.waitForInitialization()
        }
        val connection = ConnectionFactory.cloudConnectionOrNull ?: return

        when (intent.action) {
            ACTION_REGISTER -> {
                try {
                    runBlocking { registerFcm(connection) }
                } catch (e: HttpClient.HttpException) {
                    CloudMessagingHelper.registrationFailureReason = e
                    CloudMessagingHelper.registrationDone = true
                    runBlocking {
                        showToast(getPushNotificationStatus(this@FcmRegistrationService).first, ToastType.ERROR)
                    }
                    Log.e(TAG, "FCM registration failed", e)
                } catch (e: IOException) {
                    CloudMessagingHelper.registrationFailureReason = e
                    CloudMessagingHelper.registrationDone = true
                    runBlocking {
                        showToast(getPushNotificationStatus(this@FcmRegistrationService).first, ToastType.ERROR)
                    }
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

    // HttpException is thrown by our HTTP code, IOException can be thrown by FCM
    @Throws(HttpClient.HttpException::class, IOException::class)
    private suspend fun registerFcm(connection: CloudConnection) {
        val token = FirebaseInstanceId.getInstance().getToken(connection.messagingSenderId,
                FirebaseMessaging.INSTANCE_ID_SCOPE)
        val deviceName = deviceName + if (Util.isFlavorBeta) " (${getString(R.string.beta)})" else ""
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) +
                if (Util.isFlavorBeta) "-beta" else ""

        val regUrl = String.format(Locale.US,
                "addAndroidRegistration?deviceId=%s&deviceModel=%s&regId=%s",
                deviceId, URLEncoder.encode(deviceName, "UTF-8"), token)

        Log.d(TAG, "Register device at openHAB-cloud with URL: $regUrl")
        connection.httpClient.get(regUrl).close()
        Log.d(TAG, "FCM reg id success")
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
            val actual = intent.getParcelableExtra<Intent>("intent") ?: return
            enqueueWork(context, FcmRegistrationService::class.java, JOB_ID, actual)
        }

        companion object {
            internal fun wrap(context: Context, intent: Intent, id: Int): PendingIntent {
                val wrapped = Intent(context, ProxyReceiver::class.java)
                        .putExtra("intent", intent)
                return PendingIntent.getBroadcast(context, id, wrapped, PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }
    }

    companion object {
        private val TAG = FcmRegistrationService::class.java.simpleName

        private const val JOB_ID = 1000

        private const val ACTION_REGISTER = "org.openhab.habdroid.action.REGISTER_GCM"
        private const val ACTION_HIDE_NOTIFICATION = "org.openhab.habdroid.action.HIDE_NOTIFICATION"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"

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
