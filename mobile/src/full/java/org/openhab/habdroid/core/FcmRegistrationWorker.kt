/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.PendingIntent_Immutable
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.parcelable

class FcmRegistrationWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /**
     * @author https://stackoverflow.com/a/12707479
     */
    private val deviceName: String get() {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        val actualModel = if (model.lowercase(Locale.ROOT).startsWith(manufacturer.lowercase(Locale.ROOT)))
            model else "$manufacturer $model"

        // Capitalize returned value
        val first = actualModel.elementAtOrNull(0)
        return when {
            first == null -> ""
            Character.isUpperCase(first) -> actualModel
            else -> Character.toUpperCase(first) + actualModel.substring(1)
        }
    }

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION)
        Log.d(TAG, "Run with action $action")

        ConnectionFactory.waitForInitialization()

        val connection = ConnectionFactory.primaryCloudConnection?.connection

        if (connection == null) {
            Log.d(TAG, "Got no connection")
            return retryOrFail()
        }

        when (action) {
            ACTION_REGISTER -> {
                try {
                    registerFcm(connection)
                    CloudMessagingHelper.registrationFailureReason = null
                    CloudMessagingHelper.registrationDone = true
                    return Result.success()
                } catch (e: HttpClient.HttpException) {
                    CloudMessagingHelper.registrationFailureReason = e
                    CloudMessagingHelper.registrationDone = true
                    Log.e(TAG, "FCM registration failed", e)
                    return retryOrFail()
                } catch (e: IOException) {
                    CloudMessagingHelper.registrationFailureReason = e
                    CloudMessagingHelper.registrationDone = true
                    Log.e(TAG, "FCM registration failed", e)
                    return retryOrFail()
                }
            }
            ACTION_HIDE_NOTIFICATION -> {
                val id = inputData.getInt(KEY_NOTIFICATION_ID, -1)
                if (id >= 0) {
                    sendHideNotificationRequest(id, connection.messagingSenderId)
                    return Result.success()
                }

            }
            else -> Log.e(TAG, "Invalid action '$action'")
        }

        return Result.failure()
    }

    private fun retryOrFail(): Result {
        Log.d(TAG, "retryOrFail() on attempt $runAttemptCount")
        return if (runAttemptCount > 3) Result.failure() else Result.retry()
    }

    // HttpException is thrown by our HTTP code, IOException can be thrown by FCM
    @Throws(HttpClient.HttpException::class, IOException::class)
    private suspend fun registerFcm(connection: CloudConnection) {
        val token = FirebaseInstanceId.getInstance().getToken(connection.messagingSenderId,
                FirebaseMessaging.INSTANCE_ID_SCOPE)
        val deviceName = deviceName + if (Util.isFlavorBeta) " (${context.getString(R.string.beta)})" else ""
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) +
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
            val actual = intent.parcelable<Intent>("intent") ?: return

            val data = Data.Builder()
                .putString(KEY_ACTION, actual.action)
                .putInt(KEY_NOTIFICATION_ID, actual.getIntExtra(KEY_NOTIFICATION_ID, -1))
                .build()

            enqueueFcmWorker(context, data)
        }

        companion object {
            internal fun wrap(context: Context, intent: Intent, id: Int): PendingIntent {
                val wrapped = Intent(context, ProxyReceiver::class.java)
                        .putExtra("intent", intent)
                return PendingIntent.getBroadcast(
                    context,
                    id,
                    wrapped,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent_Immutable
                )
            }
        }
    }

    companion object {
        private val TAG = FcmRegistrationWorker::class.java.simpleName

        private const val ACTION_REGISTER = "org.openhab.habdroid.action.REGISTER_GCM"
        private const val ACTION_HIDE_NOTIFICATION = "org.openhab.habdroid.action.HIDE_NOTIFICATION"
        private const val KEY_ACTION = "action"
        private const val KEY_NOTIFICATION_ID = "notificationId"

        internal fun scheduleRegistration(context: Context) {
            val data = Data.Builder()
                .putString(KEY_ACTION, ACTION_REGISTER)
                .build()

            enqueueFcmWorker(context, data)
        }

        internal fun scheduleHideNotification(context: Context, notificationId: Int) {
            val data = Data.Builder()
                .putString(KEY_ACTION, ACTION_HIDE_NOTIFICATION)
                .putInt(KEY_NOTIFICATION_ID, notificationId)
                .build()

            enqueueFcmWorker(context, data)
        }

        internal fun createHideNotificationIntent(context: Context, notificationId: Int): PendingIntent {
            val intent = Intent(context, FcmRegistrationWorker::class.java)
                .setAction(ACTION_HIDE_NOTIFICATION)
                .putExtra(KEY_NOTIFICATION_ID, notificationId)

            return ProxyReceiver.wrap(context, intent, notificationId)
        }

        private fun enqueueFcmWorker(context: Context, data: Data) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequest.Builder(FcmRegistrationWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
