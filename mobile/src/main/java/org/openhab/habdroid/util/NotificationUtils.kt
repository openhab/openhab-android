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

package org.openhab.habdroid.util

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.background.NotificationUpdateObserver
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.ui.MainActivity

object NotificationUtils {
    private fun makeNotificationClickIntent(
        context: Context,
        persistedId: String?,
        notificationId: Int
    ): PendingIntent {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NOTIFICATION_SELECTED
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(MainActivity.EXTRA_PERSISTED_NOTIFICATION_ID, persistedId)
        }
        return PendingIntent.getActivity(
            context, notificationId,
            contentIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    suspend fun makeNotification(
        context: Context,
        msg: String?,
        channelId: String,
        icon: IconResource?,
        timestamp: Long,
        persistedId: String?,
        notificationId: Int,
        deleteIntent: PendingIntent?
    ): Notification {
        var iconBitmap: Bitmap? = null

        if (icon != null) {
            val connection = ConnectionFactory.cloudConnectionOrNull
            if (connection != null && !context.isDataSaverActive()) {
                try {
                    iconBitmap = connection.httpClient
                        .get(icon.toUrl(context, true), timeoutMillis = 1000)
                        .asBitmap(context.resources.getDimensionPixelSize(R.dimen.svg_image_default_size), false)
                        .response
                } catch (e: HttpClient.HttpException) {
                    // ignored, keep bitmap null
                }
            }
        }

        val contentIntent = makeNotificationClickIntent(context, persistedId, notificationId)

        val publicText = context.resources.getQuantityString(
            R.plurals.summary_notification_text, 1, 1
        )
        val publicVersion = makeNotificationBuilder(context, channelId, timestamp)
            .setContentText(publicText)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .build()

        return makeNotificationBuilder(context, channelId, timestamp)
            .setLargeIcon(iconBitmap)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setSound(context.getPrefs().getNotificationTone())
            .setContentText(msg)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
    }

    @TargetApi(24)
    fun makeSummaryNotification(
        context: Context,
        subNotificationCount: Int,
        timestamp: Long,
        deleteIntent: PendingIntent?
    ): Notification {
        val text = context.resources.getQuantityString(
            R.plurals.summary_notification_text,
            subNotificationCount, subNotificationCount
        )
        val clickIntent = makeNotificationClickIntent(context, null, SUMMARY_NOTIFICATION_ID)
        val publicVersion = makeNotificationBuilder(
            context,
            NotificationUpdateObserver.CHANNEL_ID_MESSAGE_DEFAULT,
            timestamp
        )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentText(text)
            .setContentIntent(clickIntent)
            .build()
        return makeNotificationBuilder(context, NotificationUpdateObserver.CHANNEL_ID_MESSAGE_DEFAULT, timestamp)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setGroupSummary(true)
            .setGroup("gcm")
            .setContentText(text)
            .setPublicVersion(publicVersion)
            .setContentIntent(clickIntent)
            .setDeleteIntent(deleteIntent)
            .build()
    }

    private fun makeNotificationBuilder(
        context: Context,
        channelId: String,
        timestamp: Long
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
            .setContentTitle(context.getString(R.string.app_name))
            .setWhen(timestamp)
            .setShowWhen(timestamp != 0L)
            .setColor(ContextCompat.getColor(context, R.color.openhab_orange))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setLights(ContextCompat.getColor(context, R.color.openhab_orange), 3000, 3000)
            .setVibrate(context.getPrefs().getNotificationVibrationPattern(context))
            .setGroup("gcm")
    }

    @TargetApi(23)
    fun getGcmNotificationCount(active: Array<StatusBarNotification>): Int {
        return active.count { n -> n.id != 0 && (n.groupKey?.endsWith("gcm") == true) }
    }

    fun getChannelId(severity: String?) =
        if (severity.isNullOrEmpty()) NotificationUpdateObserver.CHANNEL_ID_MESSAGE_DEFAULT else "severity-$severity"

    fun createChannelForSeverity(
        context: Context,
        severity: String?,
        nm: NotificationManager
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUpdateObserver.createNotificationChannels(context)
            if (!severity.isNullOrEmpty()) {
                with(
                    NotificationChannel(
                        getChannelId(severity),
                        context.getString(R.string.notification_channel_severity_value, severity),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                ) {
                    setShowBadge(true)
                    enableVibration(true)
                    enableLights(true)
                    lightColor = ContextCompat.getColor(context, R.color.openhab_orange)
                    group = NotificationUpdateObserver.CHANNEL_GROUP_MESSAGES
                    description = context.getString(R.string.notification_channel_severity_value_description, severity)
                    nm.createNotificationChannel(this)
                }
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate") // Used in full flavor
    internal const val EXTRA_NOTIFICATION_ID = "notificationId"
    const val SUMMARY_NOTIFICATION_ID = 0
    // Notification grouping is only available on N or higher, as mentioned in
    // https://developer.android.com/guide/topics/ui/notifiers/notifications#bundle
    val HAS_GROUPING_SUPPORT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
}
