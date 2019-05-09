package org.openhab.habdroid.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.work.WorkInfo

import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.Util

import java.util.ArrayList
import java.util.HashMap

internal class NotificationUpdateObserver(context: Context) : Observer<List<WorkInfo>> {
    private val context: Context

    init {
        this.context = context.applicationContext
    }

    override fun onChanged(workInfos: List<WorkInfo>) {
        // Find latest state for each tag
        val latestInfoByTag = HashMap<String, WorkInfo>()
        for (info in workInfos) {
            for (tag in info.tags) {
                if (BackgroundTasksManager.KNOWN_KEYS.contains(tag)
                        || tag.startsWith(BackgroundTasksManager.WORKER_TAG_PREFIX_NFC)) {
                    val state = info.state
                    if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING) {
                        // Always treat a running job as the 'current' one
                        latestInfoByTag[tag] = info
                    } else if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED) {
                        // Succeeded and failed tasks have their timestamp in output data, so
                        // we can use that one to determine the newest one
                        val existing = latestInfoByTag[tag]
                        val existingState = existing?.state
                        if (existingState == null) {
                            latestInfoByTag[tag] = info
                        } else if (existingState == WorkInfo.State.SUCCEEDED || existingState == WorkInfo.State.FAILED) {
                            val ts = info.outputData.getLong(ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0)
                            val existingTs = existing.outputData.getLong(ItemUpdateWorker.OUTPUT_DATA_TIMESTAMP, 0)
                            if (ts > existingTs) {
                                latestInfoByTag[tag] = info
                            }
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
        var hasEnqueuedWork = false
        var hasRunningWork = false
        val failedInfos = ArrayList<Pair<String, WorkInfo>>()

        for ((tag, info) in latestInfoByTag) {
            when (info.state) {
                WorkInfo.State.ENQUEUED -> hasEnqueuedWork = true
                WorkInfo.State.RUNNING -> hasRunningWork = true
                WorkInfo.State.FAILED -> failedInfos.add(Pair(tag, info))
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!failedInfos.isEmpty()) {
            // show error notification
            val errors = ArrayList<CharSequence>()
            val retryInfos = ArrayList<BackgroundTasksManager.RetryInfo>()
            for ((tag, info) in failedInfos) {
                val data = info.outputData
                val itemName = data.getString(ItemUpdateWorker.OUTPUT_DATA_ITEM)
                val value = data.getString(ItemUpdateWorker.OUTPUT_DATA_VALUE)
                val hadConnection = data.getBoolean(ItemUpdateWorker.OUTPUT_DATA_HAS_CONNECTION, false)
                val httpStatus = data.getInt(ItemUpdateWorker.OUTPUT_DATA_HTTP_STATUS, 0)

                if (itemName != null && value != null) {
                    retryInfos.add(BackgroundTasksManager.RetryInfo(tag, itemName, value))
                }
                if (hadConnection) {
                    errors.add(context.getString(
                            R.string.item_update_http_error, itemName, httpStatus))
                } else {
                    errors.add(context.getString(
                            R.string.item_update_connection_error, itemName))
                }
            }
            val n = createErrorNotification(context, errors, retryInfos)
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
        private val NOTIFICATION_ID_BACKGROUND_WORK = 1000
        private val CHANNEL_ID_BACKGROUND = "background"
        private val CHANNEL_ID_BACKGROUND_ERROR = "backgroundError"

        /**
         * Creates notification channels for background tasks.
         * @param context
         */
        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }

            val nm = context.getSystemService(NotificationManager::class.java)

            var name = context.getString(R.string.notification_channel_background)
            var description = context.getString(R.string.notification_channel_background_description)
            var channel = NotificationChannel(CHANNEL_ID_BACKGROUND, name,
                    NotificationManager.IMPORTANCE_MIN)
            channel.description = description
            nm.createNotificationChannel(channel)

            name = context.getString(R.string.notification_channel_background_error)
            description = context.getString(R.string.notification_channel_background_error_description)
            channel = NotificationChannel(CHANNEL_ID_BACKGROUND_ERROR, name,
                    NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = description
            channel.enableVibration(true)
            channel.lightColor = ContextCompat.getColor(context, R.color.openhab_orange)
            channel.enableLights(true)
            nm.createNotificationChannel(channel)
        }

        private fun createProgressNotification(context: Context,
                                               @StringRes messageResId: Int): Notification {
            return createBaseBuilder(context, CHANNEL_ID_BACKGROUND)
                    .setContentText(context.getString(messageResId))
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build()
        }

        private fun createErrorNotification(context: Context,
                                            errors: ArrayList<CharSequence>,
                                            retryInfos: ArrayList<BackgroundTasksManager.RetryInfo>): Notification {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val text = context.resources.getQuantityString(R.plurals.item_update_error_title,
                    errors.size, errors.size)

            val nb = createBaseBuilder(context, CHANNEL_ID_BACKGROUND_ERROR)
                    .setContentText(text)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setLights(ContextCompat.getColor(context, R.color.openhab_orange), 3000, 3000)
                    .setSound(Uri.parse(prefs.getString(Constants.PREFERENCE_TONE, "")))
                    .setVibrate(Util.getNotificationVibrationPattern(context))

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

            if (!retryInfos.isEmpty()) {
                val retryIntent = Intent(context, BackgroundTasksManager::class.java)
                        .setAction(BackgroundTasksManager.ACTION_RETRY_UPLOAD)
                        .putExtra(BackgroundTasksManager.EXTRA_RETRY_INFOS, retryInfos)
                val retryPendingIntent = PendingIntent.getBroadcast(context, 0,
                        retryIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                nb.addAction(NotificationCompat.Action(R.drawable.ic_refresh_grey_24dp,
                        context.getString(R.string.retry), retryPendingIntent))
            }

            return nb.build()
        }

        private fun createBaseBuilder(context: Context,
                                      channelId: String): NotificationCompat.Builder {
            val notificationIntent = Intent(context, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val contentIntent = PendingIntent.getActivity(context, 0,
                    notificationIntent, 0)

            return NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setWhen(System.currentTimeMillis())
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .setColor(ContextCompat.getColor(context, R.color.openhab_orange))
        }
    }
}
