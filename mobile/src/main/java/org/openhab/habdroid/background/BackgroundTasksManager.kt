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

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Parcelable
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.android.parcel.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.model.NfcTag
import org.openhab.habdroid.ui.TaskerItemPickerActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.toItemUpdatePrefValue
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getString
import org.openhab.habdroid.util.isDemoModeEnabled
import org.openhab.habdroid.util.isTaskerPluginEnabled
import java.util.HashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

class BackgroundTasksManager : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() with intent ${intent.action}")

        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                Log.d(TAG, "Alarm clock changed")
                scheduleWorker(context, PrefKeys.SEND_ALARM_CLOCK)
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                Log.d(TAG, "Phone state changed")
                scheduleWorker(context, PrefKeys.SEND_PHONE_STATE)
            }
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY -> {
                Log.d(TAG, "Battery or charging state changed: ${intent.action}")
                scheduleWorker(context, PrefKeys.SEND_BATTERY_LEVEL)
                scheduleWorker(context, PrefKeys.SEND_CHARGING_STATE)
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                Log.d(TAG, "Wifi state has changed")
                scheduleWorker(context, PrefKeys.SEND_WIFI_SSID)
            }
            Intent.ACTION_LOCALE_CHANGED -> {
                Log.d(TAG, "Locale changed, recreate notification channels")
                NotificationUpdateObserver.createNotificationChannels(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed")
                KNOWN_KEYS.forEach { key -> scheduleWorker(context, key) }
            }
            ACTION_RETRY_UPLOAD -> {
                intent.getParcelableArrayListExtra<RetryInfo>(EXTRA_RETRY_INFO_LIST)?.forEach { info ->
                    enqueueItemUpload(
                        context,
                        info.tag,
                        info.itemName,
                        info.label,
                        info.value,
                        BackoffPolicy.EXPONENTIAL,
                        info.showToast,
                        info.taskerIntent,
                        info.asCommand
                    )
                }
            }
            ACTION_CLEAR_UPLOAD -> WorkManager.getInstance(context).pruneWork()
            TaskerIntent.ACTION_QUERY_CONDITION, TaskerIntent.ACTION_FIRE_SETTING -> {
                if (!context.getPrefs().isTaskerPluginEnabled()) {
                    Log.d(TAG, "Tasker plugin is disabled")
                    if (isOrderedBroadcast) {
                        Log.d(TAG, "Send failure to Tasker")
                        resultCode = TaskerItemPickerActivity.RESULT_CODE_PLUGIN_DISABLED
                        TaskerPlugin.addVariableBundle(
                            getResultExtras(true),
                            bundleOf(
                                TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE to
                                    context.getString(R.string.tasker_plugin_disabled)
                            )
                        )
                    }
                    return
                }
                val bundle = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE) ?: return
                val itemName = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_NAME)
                val label = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_LABEL)
                val state = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_STATE)
                val mappedState = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_MAPPED_STATE)
                val asCommand = bundle.getBoolean(TaskerItemPickerActivity.EXTRA_ITEM_AS_COMMAND, true)
                if (itemName.isNullOrEmpty() || state.isNullOrEmpty()) {
                    return
                }
                enqueueItemUpload(
                    context,
                    WORKER_TAG_PREFIX_TASKER + itemName,
                    itemName,
                    label,
                    ItemUpdateWorker.ValueWithInfo(state, mappedState),
                    BackoffPolicy.EXPONENTIAL,
                    false,
                    intent.getStringExtra(TaskerPlugin.Setting.EXTRA_PLUGIN_COMPLETION_INTENT),
                    asCommand
                )
                if (isOrderedBroadcast) {
                    resultCode = TaskerPlugin.Setting.RESULT_CODE_PENDING
                }
            }
        }
    }

    @Parcelize
    internal data class RetryInfo(
        val tag: String,
        val itemName: String,
        val label: String?,
        val value: ItemUpdateWorker.ValueWithInfo,
        val showToast: Boolean,
        val taskerIntent: String?,
        val asCommand: Boolean
    ) : Parcelable

    private class PrefsListener constructor(private val context: Context) :
        SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
            when {
                key == PrefKeys.DEMO_MODE && prefs.isDemoModeEnabled() -> {
                    // Demo mode was enabled -> cancel all uploads and clear DB
                    // to clear out notifications
                    with(WorkManager.getInstance(context)) {
                        cancelAllWorkByTag(WORKER_TAG_ITEM_UPLOADS)
                        cancelAllWorkByTag(WORKER_TAG_PERIODIC_TRIGGER)
                        pruneWork()
                    }
                }
                // Demo mode was disabled -> reschedule uploads
                (key == PrefKeys.DEMO_MODE && !prefs.isDemoModeEnabled()) ||
                    // Prefix has been changed -> reschedule uploads
                    key == PrefKeys.SEND_DEVICE_INFO_PREFIX -> {
                    KNOWN_KEYS.forEach { knowKey -> scheduleWorker(context, knowKey) }
                }
                key in KNOWN_KEYS -> scheduleWorker(context, key)
            }
        }
    }

    companion object {
        private val TAG = BackgroundTasksManager::class.java.simpleName

        internal const val ACTION_RETRY_UPLOAD = "org.openhab.habdroid.background.action.RETRY_UPLOAD"
        internal const val ACTION_CLEAR_UPLOAD = "org.openhab.habdroid.background.action.CLEAR_UPLOAD"
        internal const val EXTRA_RETRY_INFO_LIST = "retryInfoList"

        private const val WORKER_TAG_ITEM_UPLOADS = "itemUploads"
        private const val WORKER_TAG_PERIODIC_TRIGGER = "periodicTrigger"
        private const val WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING = "periodicTriggerNotCharging"
        private const val WORKER_TAG_PERIODIC_TRIGGER_CHARGING = "periodicTriggerCharging"
        const val WORKER_TAG_PREFIX_NFC = "nfc-"
        const val WORKER_TAG_PREFIX_TASKER = "tasker-"
        const val WORKER_TAG_PREFIX_WIDGET = "widget-"

        internal val KNOWN_KEYS = listOf(
            PrefKeys.SEND_ALARM_CLOCK,
            PrefKeys.SEND_PHONE_STATE,
            PrefKeys.SEND_BATTERY_LEVEL,
            PrefKeys.SEND_CHARGING_STATE,
            PrefKeys.SEND_WIFI_SSID
        )
        private val KNOWN_PERIODIC_KEYS = listOf(
            PrefKeys.SEND_BATTERY_LEVEL,
            PrefKeys.SEND_CHARGING_STATE,
            PrefKeys.SEND_WIFI_SSID
        )
        private val IGNORED_PACKAGES_FOR_ALARM = listOf(
            "net.dinglisch.android.taskerm",
            "com.android.providers.calendar",
            "com.android.calendar"
        )
        private val VALUE_GETTER_MAP = HashMap<String, (Context) -> ItemUpdateWorker.ValueWithInfo?>()

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
                    ItemUpdateWorker.ValueWithInfo(tag.state, tag.mappedState),
                    BackoffPolicy.LINEAR,
                    showToast = true,
                    asCommand = true
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
                    ItemUpdateWorker.ValueWithInfo(data.state, data.mappedState),
                    BackoffPolicy.LINEAR,
                    showToast = true,
                    asCommand = true
                )
            }
        }

        fun triggerPeriodicWork(context: Context) {
            Log.d(TAG, "triggerPeriodicWork()")
            KNOWN_PERIODIC_KEYS.forEach { key -> scheduleWorker(context, key) }
        }

        fun schedulePeriodicTrigger(context: Context, force: Boolean = false) {
            val workManager = WorkManager.getInstance(context)
            val prefs = context.getPrefs()
            val periodicWorkIsNeeded = KNOWN_PERIODIC_KEYS
                .map { key -> prefs.getString(key, null).toItemUpdatePrefValue() }
                .any { value -> value.first }

            if (!periodicWorkIsNeeded) {
                Log.d(TAG, "Periodic workers are not needed, canceling...")
                workManager.cancelAllWorkByTag(WORKER_TAG_PERIODIC_TRIGGER)
                return
            }

            fun isWorkerRunning(tag: String): Boolean = workManager
                .getWorkInfosForUniqueWork(tag)
                .get()
                ?.filter { workInfo -> !workInfo.state.isFinished }
                ?.size == 1

            val isChargingWorkerRunning = isWorkerRunning(WORKER_TAG_PERIODIC_TRIGGER_CHARGING)
            val isNotChargingWorkerRunning = isWorkerRunning(WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING)

            if (isChargingWorkerRunning && isNotChargingWorkerRunning && !force) {
                Log.d(TAG, "Both periodic workers are running")
                return
            }

            Log.d(TAG, "Scheduling periodic workers. Currently running:" +
                " notCharging $isNotChargingWorkerRunning, charging $isChargingWorkerRunning")

            val notChargingConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Value is stored in minutes, but we need millis to compare it
            val repeatInterval = max(
                prefs.getString(PrefKeys.SEND_DEVICE_INFO_SCHEDULE).toInt() * 60 * 1000L,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
            )
            val flexInterval = max(
                (repeatInterval * 0.75).toLong(),
                PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS
            )

            val notChargingWorkRequest = PeriodicWorkRequest.Builder(PeriodicItemUpdateWorker::class.java,
                repeatInterval, TimeUnit.MILLISECONDS,
                flexInterval, TimeUnit.MILLISECONDS)
                .setConstraints(notChargingConstraints)
                .addTag(WORKER_TAG_PERIODIC_TRIGGER)
                .addTag(WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING)
                .build()

            val chargingConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .build()
            val chargingWorkRequest = PeriodicWorkRequest.Builder(PeriodicItemUpdateWorker::class.java,
                    PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS,
                    PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(chargingConstraints)
                .addTag(WORKER_TAG_PERIODIC_TRIGGER)
                .addTag(WORKER_TAG_PERIODIC_TRIGGER_CHARGING)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING,
                ExistingPeriodicWorkPolicy.REPLACE,
                notChargingWorkRequest
            )
            workManager.enqueueUniquePeriodicWork(
                WORKER_TAG_PERIODIC_TRIGGER_CHARGING,
                ExistingPeriodicWorkPolicy.REPLACE,
                chargingWorkRequest
            )
        }

        private fun scheduleWorker(context: Context, key: String) {
            val prefs = context.getPrefs()
            val setting = if (prefs.isDemoModeEnabled()) {
                Pair(false, "") // Don't attempt any uploads in demo mode
            } else {
                prefs.getString(key, null).toItemUpdatePrefValue()
            }

            if (key in KNOWN_PERIODIC_KEYS) {
                schedulePeriodicTrigger(context)
            }

            if (!setting.first) {
                with(WorkManager.getInstance(context)) {
                    cancelAllWorkByTag(key)
                    pruneWork()
                }
                return
            }

            val value = VALUE_GETTER_MAP[key]?.invoke(context) ?: return
            val prefix = prefs.getString(PrefKeys.SEND_DEVICE_INFO_PREFIX)

            enqueueItemUpload(
                context,
                key,
                prefix + setting.second,
                null,
                value,
                BackoffPolicy.EXPONENTIAL,
                showToast = false,
                asCommand = true
            )
        }

        private fun enqueueItemUpload(
            context: Context,
            tag: String,
            itemName: String,
            label: String?,
            value: ItemUpdateWorker.ValueWithInfo,
            backoffPolicy: BackoffPolicy,
            showToast: Boolean,
            taskerIntent: String? = null,
            asCommand: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val inputData = ItemUpdateWorker.buildData(itemName, label, value, showToast, taskerIntent, asCommand)
            val workRequest = OneTimeWorkRequest.Builder(ItemUpdateWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(backoffPolicy, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .addTag(tag)
                .addTag(WORKER_TAG_ITEM_UPLOADS)
                .setInputData(inputData)
                .build()

            val workManager = WorkManager.getInstance(context)
            Log.d(TAG, "Scheduling work for tag $tag")
            workManager.enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, workRequest)
        }

        init {
            VALUE_GETTER_MAP[PrefKeys.SEND_ALARM_CLOCK] = { context ->
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val info = alarmManager.nextAlarmClock
                val sender = info?.showIntent?.creatorPackage
                Log.d(TAG, "Alarm sent by $sender")
                var time: String? = if (sender in IGNORED_PACKAGES_FOR_ALARM) {
                    "UNDEF"
                } else {
                    info?.triggerTime?.toString() ?: "UNDEF"
                }

                val prefs = context.getPrefs()

                if (time == "UNDEF" && prefs.getBoolean(PrefKeys.ALARM_CLOCK_LAST_VALUE_WAS_UNDEF, false)) {
                    time = null
                }

                prefs.edit {
                    putBoolean(PrefKeys.ALARM_CLOCK_LAST_VALUE_WAS_UNDEF, time == "UNDEF" || time == null)
                }

                time?.let { ItemUpdateWorker.ValueWithInfo(it, type = ItemUpdateWorker.ValueType.Timestamp) }
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_PHONE_STATE] = { context ->
                val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val state = when (manager.callState) {
                    TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                    TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                    else -> "UNDEF"
                }
                ItemUpdateWorker.ValueWithInfo(state)
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_BATTERY_LEVEL] = { context ->
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ItemUpdateWorker.ValueWithInfo(batteryLevel.toString())
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_CHARGING_STATE] = { context ->
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context.registerReceiver(null, ifilter)
                }
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val state = if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL) {
                    when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                        else -> "UNKNOWN_CHARGER"
                    }
                } else {
                    "UNDEF"
                }
                ItemUpdateWorker.ValueWithInfo(state)
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_WIFI_SSID] = { context ->
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ssidToSend = wifiManager.connectionInfo.let { info ->
                    if (info.networkId == -1) {
                        "UNDEF"
                    } else {
                        // WifiInfo#getSSID() may surround the SSID with double quote marks
                        info.ssid.removeSurrounding("\"")
                    }
                }
                ItemUpdateWorker.ValueWithInfo(ssidToSend)
            }
        }
    }
}
