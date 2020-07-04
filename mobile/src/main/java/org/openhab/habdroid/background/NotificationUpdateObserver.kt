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

package org.openhab.habdroid.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getNotificationTone
import org.openhab.habdroid.util.getNotificationVibrationPattern
import org.openhab.habdroid.util.getPrefs
import java.util.ArrayList
import java.util.HashMap

internal class NotificationUpdateObserver(context: Context) : Observer<List<WorkInfo>> {
    private val context: Context = context.applicationContext

    override fun onChanged(workInfos: List<WorkInfo>) {
        // Find latest state for each tag
        val latestInfoByTag = HashMap<String, WorkInfo>()
        for (info in workInfos) {
            for (tag in info.tags) {
                if (tag in BackgroundTasksManager.KNOWN_KEYS ||
                    tag.startsWith(BackgroundTasksManager.WORKER_TAG_PREFIX_NFC) ||
                    tag.startsWith(BackgroundTasksManager.WORKER_TAG_PREFIX_TASKER) ||
                    tag.startsWith(BackgroundTasksManager.WORKER_TAG_PREFIX_WIDGET) ||
                    tag.startsWith(BackgroundTasksManager.WORKER_TAG_PREFIX_TILE)
                ) {
                    val state = info.state
                    if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING) {
                        // Always treat a running job as the 'current' one
                        latestInfoByTag[tag] = info
                    } else if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED) {
                        // Succeeded and failed tasks have their timestamp in output data, so
                        // we can use that one to determine the newest one
                        val existing = latestInfoByTag[tag]
                        when (existing?.state) {
                            null -> latestInfoByTag[tag] = info
                            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> {
                                val ts = info.outputData.getLong(ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0)
                                val existingTs = existing.outputData.getLong(ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0)
                                if (ts > existingTs) {
                                    latestInfoByTag[tag] = info
                                }
                            }
                            else -> {}
                        }
                    }
                    // Stop evaluating tags and advance to next info
                    break
                }
            }
        }
        // Now, from the map create a list of work items that are either
        // - enqueued (not yet running or retrying)
        // - running
        // - failed
        val hasEnqueuedWork = latestInfoByTag.any { (_, info) -> info.state == WorkInfo.State.ENQUEUED }
        val hasRunningWork = latestInfoByTag.any { (_, info) -> info.state == WorkInfo.State.FAILED }
        val failedInfoList = latestInfoByTag.filter { (_, info) -> info.state == WorkInfo.State.FAILED }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (failedInfoList.isNotEmpty()) {
            // show error notification
            val errors = ArrayList<CharSequence>()
            val retryInfoList = ArrayList<BackgroundTasksManager.RetryInfo>()
            for ((tag, info) in failedInfoList) {
                val data = info.outputData
                val itemName = data.getString(ItemUpdateWorker.OUTPUT_DATA_ITEM_NAME)
                val label = data.getString(ItemUpdateWorker.OUTPUT_DATA_LABEL)
                val value = data.getValueWithInfo(ItemUpdateWorker.OUTPUT_DATA_VALUE)
                val isImportant = data.getBoolean(ItemUpdateWorker.OUTPUT_DATA_IS_IMPORTANT, false)
                val showToast = data.getBoolean(ItemUpdateWorker.OUTPUT_DATA_SHOW_TOAST, false)
                val taskerIntent = data.getString(ItemUpdateWorker.OUTPUT_DATA_TASKER_INTENT)
                val asCommand = data.getBoolean(ItemUpdateWorker.OUTPUT_DATA_AS_COMMAND, false)
                val hadConnection = data.getBoolean(ItemUpdateWorker.OUTPUT_DATA_HAS_CONNECTION, false)
                val httpStatus = data.getInt(ItemUpdateWorker.OUTPUT_DATA_HTTP_STATUS, 0)

                if (itemName != null && value != null) {
                    retryInfoList.add(
                        BackgroundTasksManager.RetryInfo(
                            tag,
                            itemName,
                            label,
                            value,
                            isImportant,
                            showToast,
                            taskerIntent,
                            asCommand
                        )
                    )
                }
                errors.add(if (hadConnection) {
                    if (label.isNullOrEmpty()) {
                        context.getString(R.string.item_update_http_error, itemName,
                            context.getHumanReadableErrorMessage("", httpStatus, null, true))
                    } else {
                        context.getString(R.string.item_update_http_error_label, label,
                            context.getHumanReadableErrorMessage("", httpStatus, null, true))
                    }
                } else {
                    if (label.isNullOrEmpty()) {
                        context.getString(R.string.item_update_connection_error, itemName)
                    } else {
                        context.getString(R.string.item_update_connection_error_label, label)
                    }
                })
            }
            val n = createErrorNotification(context, errors, retryInfoList)
            createNotificationChannels(context)
            nm.notify(NOTIFICATION_ID_BACKGROUND_WORK, n)
        } else if (hasRunningWork || hasEnqueuedWork) {
            // show waiting notification
            @StringRes val messageResId = if (hasRunningWork)
                R.string.item_upload_in_progress else R.string.waiting_for_item_upload
            val n = createProgressNotification(context, messageResId)
            createNotificationChannels(context)
            nm.notify(NOTIFICATION_ID_BACKGROUND_WORK, n)
        } else {
            // clear notification
            nm.cancel(NOTIFICATION_ID_BACKGROUND_WORK)
        }
    }

    companion object {
        private const val NOTIFICATION_ID_BACKGROUND_WORK = 1000
        const val NOTIFICATION_ID_BACKGROUND_WORK_RUNNING = 1001
        const val NOTIFICATION_ID_BROADCAST_RECEIVER = 1002
        const val CHANNEL_ID_BACKGROUND = "background"
        const val CHANNEL_ID_BACKGROUND_ERROR = "backgroundError"
        const val CHANNEL_ID_BACKGROUND_FOREGROUND_SERVICE = "backgroundBroadcastReceiver"
        const val CHANNEL_ID_MESSAGE_DEFAULT = "default"
        const val CHANNEL_GROUP_MESSAGES = "messages"
        private const val CHANNEL_GROUP_OTHER = "other"

        /**
         * Creates notification channels for background tasks.
         * @param context
         */
        @JvmStatic
        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }

            val nm = context.getSystemService(NotificationManager::class.java)!!

            // Channel groups
            nm.createNotificationChannelGroup(
                    NotificationChannelGroup(
                    CHANNEL_GROUP_MESSAGES,
                    context.getString(R.string.notification_channel_group_messages)
                )
            )

            nm.createNotificationChannelGroup(
                    NotificationChannelGroup(
                    CHANNEL_GROUP_OTHER,
                    context.getString(R.string.notification_channel_group_other)
                )
            )

            // Default notification channel
            with(
                NotificationChannel(
                    CHANNEL_ID_MESSAGE_DEFAULT,
                    context.getString(R.string.notification_channel_messages_default_severity),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            ) {
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lightColor = ContextCompat.getColor(context, R.color.openhab_orange)
                group = CHANNEL_GROUP_MESSAGES
                description = context.getString(R.string.notification_channel_messages_default_severity_description)
                nm.createNotificationChannel(this)
            }

            // Background task channels
            with(
                NotificationChannel(
                    CHANNEL_ID_BACKGROUND,
                    context.getString(R.string.notification_channel_background),
                    NotificationManager.IMPORTANCE_MIN
                )
            ) {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                group = CHANNEL_GROUP_OTHER
                description = context.getString(R.string.notification_channel_background_description)
                nm.createNotificationChannel(this)
            }

            with(
                NotificationChannel(
                    CHANNEL_ID_BACKGROUND_FOREGROUND_SERVICE,
                    context.getString(R.string.notification_channel_background_foreground_service),
                    NotificationManager.IMPORTANCE_NONE
                )
            ) {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                group = CHANNEL_GROUP_OTHER
                description = context.getString(R.string.notification_channel_background_foreground_service_description)
                nm.createNotificationChannel(this)
            }

            with(
                NotificationChannel(
                    CHANNEL_ID_BACKGROUND_ERROR,
                    context.getString(R.string.notification_channel_background_error),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            ) {
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lightColor = ContextCompat.getColor(context, R.color.openhab_orange)
                group = CHANNEL_GROUP_OTHER
                description = context.getString(R.string.notification_channel_background_error_description)
                nm.createNotificationChannel(this)
            }
        }

        private fun createProgressNotification(context: Context, @StringRes messageResId: Int): Notification {
            return createBaseBuilder(context, CHANNEL_ID_BACKGROUND)
                .setContentTitle(context.getString(messageResId))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }

        private fun createErrorNotification(
            context: Context,
            errors: ArrayList<CharSequence>,
            retryInfoList: ArrayList<BackgroundTasksManager.RetryInfo>
        ): Notification {
            val text = context.resources.getQuantityString(R.plurals.item_update_error_title,
                errors.size, errors.size)
            val prefs = context.getPrefs()
            val nb = createBaseBuilder(context, CHANNEL_ID_BACKGROUND_ERROR)
                .setContentTitle(text)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setLights(ContextCompat.getColor(context, R.color.openhab_orange), 3000, 3000)
                .setSound(prefs.getNotificationTone())
                .setVibrate(prefs.getNotificationVibrationPattern(context))

            if (errors.size > 1) {
                val style = NotificationCompat.InboxStyle()
                for (error in errors) {
                    style.addLine(error)
                }
                nb.setStyle(style)
            } else {
                nb.setStyle(NotificationCompat.BigTextStyle()
                    .bigText(errors[0])
                    .setBigContentTitle(text))
            }

            if (retryInfoList.isNotEmpty()) {
                val retryIntent = Intent(context, BackgroundTasksManager::class.java)
                    .setAction(BackgroundTasksManager.ACTION_RETRY_UPLOAD)
                    .putExtra(BackgroundTasksManager.EXTRA_RETRY_INFO_LIST, retryInfoList)
                val retryPendingIntent = PendingIntent.getBroadcast(context, 0,
                    retryIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                nb.addAction(NotificationCompat.Action(R.drawable.ic_refresh_grey_24dp,
                    context.getString(R.string.retry), retryPendingIntent))

                val clearIntent = Intent(context, BackgroundTasksManager::class.java)
                    .setAction(BackgroundTasksManager.ACTION_CLEAR_UPLOAD)
                val clearPendingIntent = PendingIntent.getBroadcast(context, 0,
                    clearIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                nb.addAction(NotificationCompat.Action(R.drawable.ic_clear_grey_24dp,
                    context.getString(R.string.ignore), clearPendingIntent))
            }

            return nb.build()
        }

        private fun createBaseBuilder(context: Context, channelId: String): NotificationCompat.Builder {
            val notificationIntent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val contentIntent = PendingIntent.getActivity(context, 0,
                notificationIntent, 0)

            return NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setColor(ContextCompat.getColor(context, R.color.openhab_orange))
        }
    }
}
