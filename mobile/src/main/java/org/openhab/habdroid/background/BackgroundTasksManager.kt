/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Parcelable
import android.telephony.TelephonyManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.android.parcel.Parcelize
import org.openhab.habdroid.model.NfcTag
import org.openhab.habdroid.ui.TaskerItemPickerActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.toItemUpdatePrefValue
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getString
import org.openhab.habdroid.util.isDemoModeEnabled
import org.openhab.habdroid.util.isTaskerPluginEnabled
import java.util.HashMap
import java.util.concurrent.TimeUnit

class BackgroundTasksManager : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() with intent ${intent.action}")

        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                Log.d(TAG, "Alarm clock changed")
                scheduleWorker(context, Constants.PREFERENCE_ALARM_CLOCK)
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                Log.d(TAG, "Phone state changed")
                scheduleWorker(context, Constants.PREFERENCE_PHONE_STATE)
            }
            Intent.ACTION_LOCALE_CHANGED -> {
                Log.d(TAG, "Locale changed, recreate notification channels")
                NotificationUpdateObserver.createNotificationChannels(context)
            }
            ACTION_RETRY_UPLOAD -> {
                intent.getParcelableArrayListExtra<RetryInfo>(EXTRA_RETRY_INFO_LIST)?.forEach { info ->
                    enqueueItemUpload(
                        context,
                        info.tag,
                        info.itemName,
                        info.label,
                        info.value,
                        info.mappedValue,
                        BackoffPolicy.EXPONENTIAL,
                        info.showToast
                    )
                }
            }
            TaskerIntent.ACTION_QUERY_CONDITION, TaskerIntent.ACTION_FIRE_SETTING -> {
                if (!context.getPrefs().isTaskerPluginEnabled()) {
                    Log.d(TAG, "Tasker plugin is disabled")
                    return
                }
                val bundle = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE) ?: return
                val itemName = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_NAME)
                val label = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_LABEL)
                val state = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_STATE)
                val mappedState = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_MAPPED_STATE)
                if (itemName.isNullOrEmpty() || state.isNullOrEmpty()) {
                    return
                }
                enqueueItemUpload(
                    context,
                    WORKER_TAG_PREFIX_TASKER + itemName,
                    itemName,
                    label,
                    state,
                    mappedState,
                    BackoffPolicy.EXPONENTIAL,
                    false
                )
            }
        }
    }

    @Parcelize
    internal data class RetryInfo(
        val tag: String,
        val itemName: String,
        val label: String?,
        val value: String,
        val mappedValue: String?,
        val showToast: Boolean
    ) : Parcelable

    private class PrefsListener constructor(private val context: Context) :
        SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
            when {
                key == Constants.PREFERENCE_DEMO_MODE && prefs.isDemoModeEnabled() -> {
                    // Demo mode was enabled -> cancel all uploads and clear DB
                    // to clear out notifications
                    with(WorkManager.getInstance(context)) {
                        cancelAllWorkByTag(WORKER_TAG_ITEM_UPLOADS)
                        pruneWork()
                    }
                }
                // Demo mode was disabled -> reschedule uploads
                (key == Constants.PREFERENCE_DEMO_MODE && !prefs.isDemoModeEnabled()) ||
                    // Prefix has been changed -> reschedule uploads
                    key == Constants.PREFERENCE_SEND_DEVICE_INFO_PREFIX -> {
                    KNOWN_KEYS.forEach { knowKey -> scheduleWorker(context, knowKey) }
                }
                key in KNOWN_KEYS -> scheduleWorker(context, key)
            }
        }
    }

    companion object {
        private val TAG = BackgroundTasksManager::class.java.simpleName

        internal const val ACTION_RETRY_UPLOAD = "org.openhab.habdroid.background.action.RETRY_UPLOAD"
        internal const val EXTRA_RETRY_INFO_LIST = "retryInfoList"

        private const val WORKER_TAG_ITEM_UPLOADS = "itemUploads"
        const val WORKER_TAG_PREFIX_NFC = "nfc-"
        const val WORKER_TAG_PREFIX_TASKER = "tasker-"
        const val WORKER_TAG_PREFIX_WIDGET = "widget-"

        internal val KNOWN_KEYS = listOf(
            Constants.PREFERENCE_ALARM_CLOCK,
            Constants.PREFERENCE_PHONE_STATE
        )
        private val IGNORED_PACKAGES_FOR_ALARM = listOf(
            "net.dinglisch.android.taskerm",
            "com.android.providers.calendar",
            "com.android.calendar"
        )
        private val VALUE_GETTER_MAP = HashMap<String, (Context) -> String?>()

        // need to keep a ref for this to avoid it being GC'ed
        // (SharedPreferences only keeps a WeakReference)
        @SuppressLint("StaticFieldLeak")
        private lateinit var prefsListener: PrefsListener

        fun initialize(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val infoLiveData = workManager.getWorkInfosByTagLiveData(WORKER_TAG_ITEM_UPLOADS)
            infoLiveData.observeForever(NotificationUpdateObserver(context))

            prefsListener = PrefsListener(context.applicationContext)
            context.getPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
        }

        fun enqueueNfcUpdateIfNeeded(context: Context, tag: NfcTag?) {
            if (tag != null && tag.sitemap == null && tag.item != null && tag.state != null) {
                enqueueItemUpload(
                    context,
                    WORKER_TAG_PREFIX_NFC + tag.item,
                    tag.item,
                    tag.label,
                    tag.state,
                    tag.mappedState,
                    BackoffPolicy.LINEAR,
                    true
                )
            }
        }

        fun enqueueWidgetItemUpdateIfNeeded(context: Context, data: ItemUpdateWidget.ItemUpdateWidgetData) {
            if (data.item.isNotEmpty() && data.state.isNotEmpty()) {
                enqueueItemUpload(
                    context,
                    WORKER_TAG_PREFIX_WIDGET + data.item,
                    data.item,
                    data.label,
                    data.state,
                    data.mappedState,
                    BackoffPolicy.LINEAR,
                    true
                )
            }
        }

        private fun scheduleWorker(context: Context, key: String) {
            val prefs = context.getPrefs()
            val setting = if (prefs.isDemoModeEnabled()) {
                Pair(false, "") // Don't attempt any uploads in demo mode
            } else {
                prefs.getString(key, null).toItemUpdatePrefValue()
            }

            if (!setting.first) {
                with(WorkManager.getInstance(context)) {
                    cancelAllWorkByTag(key)
                    pruneWork()
                }
                return
            }

            val getter = VALUE_GETTER_MAP[key] ?: return

            val prefix = prefs.getString(Constants.PREFERENCE_SEND_DEVICE_INFO_PREFIX)
            enqueueItemUpload(
                context,
                key,
                prefix + setting.second,
                null,
                getter(context) ?: return,
                null,
                BackoffPolicy.EXPONENTIAL,
                false
            )
        }

        private fun enqueueItemUpload(
            context: Context,
            tag: String,
            itemName: String,
            label: String?,
            value: String,
            mappedValue: String?,
            backoffPolicy: BackoffPolicy,
            showToast: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequest.Builder(ItemUpdateWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(backoffPolicy, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .addTag(tag)
                .addTag(WORKER_TAG_ITEM_UPLOADS)
                .setInputData(ItemUpdateWorker.buildData(itemName, label, value, mappedValue, showToast))
                .build()

            val workManager = WorkManager.getInstance(context)
            Log.d(TAG, "Scheduling work for tag $tag")
            workManager.cancelAllWorkByTag(tag)
            workManager.enqueue(workRequest)
        }

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                VALUE_GETTER_MAP[Constants.PREFERENCE_ALARM_CLOCK] = { context ->
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val info = alarmManager.nextAlarmClock
                    val sender = info?.showIntent?.creatorPackage
                    if (sender in IGNORED_PACKAGES_FOR_ALARM) {
                        Log.d(TAG, "Alarm sent by $sender, ignoring")
                        null
                    } else {
                        Log.d(TAG, "Alarm sent by $sender")
                        (info?.triggerTime ?: 0).toString()
                    }
                }
            }
            VALUE_GETTER_MAP[Constants.PREFERENCE_PHONE_STATE] = { context ->
                val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                when (manager.callState) {
                    TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                    TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                    else -> "UNDEF"
                }
            }
        }
    }
}
