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

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.background.NotificationUpdateObserver
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.CloudNotification
import org.openhab.habdroid.model.CloudNotificationId
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.PendingIntent_Immutable
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.getNotificationTone
import org.openhab.habdroid.util.getNotificationVibrationPattern
import org.openhab.habdroid.util.getPrefs

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    suspend fun showNotification(message: CloudNotification) {
        createChannelForTag(message.tag)
        val n = makeNotification(message)
        notificationManager.notify(message.id.notificationId, n)
        updateGroupNotification()
    }

    fun cancelNotificationById(id: CloudNotificationId) {
        notificationManager.cancel(id.notificationId)
        if (HAS_GROUPING_SUPPORT) {
            val active = notificationManager.activeNotifications
            if (countCloudNotifications(active) == 0) {
                // Cancel summary when removing the last sub-notification
                notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            } else {
                updateGroupNotification()
            }
        }
    }

    fun cancelNotificationsByTag(tag: String) {
        val channelId = getChannelId(tag)
        NotificationManagerCompat.from(context)
            .activeNotifications
            .filter { sbn -> NotificationCompat.getChannelId(sbn.notification) == channelId }
            .forEach { sbn -> notificationManager.cancel(sbn.id) }
    }

    fun handleNotificationDismissed(notificationId: Int) {
        if (!HAS_GROUPING_SUPPORT) {
            return
        }
        if (notificationId == SUMMARY_NOTIFICATION_ID) {
            // Cancel all sub-notifications when removing the summary
            notificationManager.activeNotifications.forEach { notificationManager.cancel(it.id) }
        } else {
            updateGroupNotification()
        }
    }

    private fun updateGroupNotification() {
        if (!HAS_GROUPING_SUPPORT) {
            return
        }
        val count = countCloudNotifications(notificationManager.activeNotifications)
        if (count > 1) {
            val deleteIntent = NotificationHandlingReceiver.createDismissedPendingIntent(
                context,
                SUMMARY_NOTIFICATION_ID
            )
            notificationManager.notify(
                SUMMARY_NOTIFICATION_ID,
                makeSummaryNotification(count, System.currentTimeMillis(), deleteIntent)
            )
        }
    }

    private fun createChannelForTag(tag: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        NotificationUpdateObserver.createNotificationChannels(context)
        if (!tag.isNullOrEmpty()) {
            with(
                NotificationChannel(
                    getChannelId(tag),
                    context.getString(R.string.notification_channel_severity_value, tag),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            ) {
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lightColor = ContextCompat.getColor(context, R.color.openhab_orange)
                group = NotificationUpdateObserver.CHANNEL_GROUP_MESSAGES
                description = context.getString(R.string.notification_channel_severity_value_description, tag)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    @TargetApi(23)
    private fun countCloudNotifications(active: Array<StatusBarNotification>): Int {
        return active.count { n -> n.id != 0 && (n.groupKey?.endsWith("gcm") == true) }
    }

    private suspend fun makeNotification(message: CloudNotification): Notification {
        val iconBitmap = getNotificationIcon(message.icon)

        val contentIntent = if (message.onClickAction == null) {
            makeNotificationClickIntent(message.id, message.id.notificationId)
        } else {
            NotificationHandlingReceiver.createActionPendingIntent(context, message.id, message.onClickAction)
        }
        val deleteIntent = NotificationHandlingReceiver.createDismissedPendingIntent(
            context,
            message.id.notificationId
        )
        val channelId = getChannelId(message.tag)

        val publicText = context.resources.getQuantityString(R.plurals.summary_notification_text, 1, 1)
        val publicVersion = makeNotificationBuilder(channelId, message.createdTimestamp)
            .setContentText(publicText)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .build()

        val builder = makeNotificationBuilder(channelId, message.createdTimestamp)
            .setLargeIcon(iconBitmap)
            .setSound(context.getPrefs().getNotificationTone())
            .setContentTitle(message.title)
            .setContentText(message.message)
            .setSubText(message.tag)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)

        val messageImage = if (message.mediaAttachmentUrl != null) {
            ConnectionFactory.waitForInitialization()
            ConnectionFactory.primaryUsableConnection?.connection?.let {
                message.loadImage(it, context, context.resources.displayMetrics.widthPixels)
            }
        } else {
            null
        }
        if (messageImage != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(messageImage))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(message.message))
        }

        message.actions?.forEach {
            val pi = NotificationHandlingReceiver.createActionPendingIntent(context, message.id, it)
            val action = NotificationCompat.Action(null, it.label, pi)
            builder.addAction(action)
        }

        return builder.build()
    }

    private suspend fun getNotificationIcon(icon: IconResource?): Bitmap? {
        val connection = ConnectionFactory.primaryCloudConnection?.connection

        return when {
            icon == null -> null
            connection == null -> {
                Log.d(TAG, "Got no connection to load icon")
                null
            }
            !context.determineDataUsagePolicy(connection).canDoLargeTransfers -> {
                Log.d(TAG, "Don't load icon: Data usage policy doesn't allow large transfers")
                null
            }
            else -> {
                Log.d(TAG, "Load icon from server")
                try {
                    val targetSize = context.resources.getDimensionPixelSize(R.dimen.notificationlist_icon_size)
                    val iconUrlPath = icon.toUrl(context, true)
                    val bitmap = connection.httpClient
                        .get(
                            iconUrlPath,
                            timeoutMillis = 1000,
                            caching = HttpClient.CachingMode.FORCE_CACHE_IF_POSSIBLE
                        )
                        .asBitmap(
                            targetSize,
                            context.getIconFallbackColor(IconBackground.OS_THEME),
                            ImageConversionPolicy.PreferTargetSize
                        )
                        .response
                    bitmap
                } catch (e: HttpClient.HttpException) {
                    Log.e(TAG, "Error getting icon", e)
                    null
                }
            }
        }
    }

    @TargetApi(24)
    fun makeSummaryNotification(subNotificationCount: Int, timestamp: Long, deleteIntent: PendingIntent): Notification {
        val text = context.resources.getQuantityString(
            R.plurals.summary_notification_text,
            subNotificationCount,
            subNotificationCount
        )
        val clickIntent = makeNotificationClickIntent(null, SUMMARY_NOTIFICATION_ID)
        val publicVersion = makeNotificationBuilder(
            NotificationUpdateObserver.CHANNEL_ID_MESSAGE_DEFAULT,
            timestamp
        )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentText(text)
            .setContentIntent(clickIntent)
            .build()

        return makeNotificationBuilder(NotificationUpdateObserver.CHANNEL_ID_MESSAGE_DEFAULT, timestamp)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setGroupSummary(true)
            .setGroup("gcm")
            .setContentText(text)
            .setPublicVersion(publicVersion)
            .setContentIntent(clickIntent)
            .setDeleteIntent(deleteIntent)
            .build()
    }

    private fun makeNotificationClickIntent(id: CloudNotificationId?, notificationId: Int): PendingIntent {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NOTIFICATION_SELECTED
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PERSISTED_NOTIFICATION_ID, id?.persistedId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent_Immutable
        )
    }

    private fun makeNotificationBuilder(channelId: String, timestamp: Long) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
            .setWhen(timestamp)
            .setShowWhen(timestamp != 0L)
            .setColor(ContextCompat.getColor(context, R.color.openhab_orange))
            .setAutoCancel(true)
            .setLights(ContextCompat.getColor(context, R.color.openhab_orange), 3000, 3000)
            .setVibrate(context.getPrefs().getNotificationVibrationPattern(context))
            .setGroup("gcm")

    companion object {
        private val TAG = NotificationHelper::class.java.simpleName

        private fun getChannelId(tag: String?) = if (tag.isNullOrEmpty()) {
            NotificationUpdateObserver.CHANNEL_ID_MESSAGE_DEFAULT
        } else {
            "severity-$tag"
        }

        internal const val SUMMARY_NOTIFICATION_ID = 0

        // Notification grouping is only available on N or higher, as mentioned in
        // https://developer.android.com/guide/topics/ui/notifiers/notifications#bundle
        private val HAS_GROUPING_SUPPORT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
}
