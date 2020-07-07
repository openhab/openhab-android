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

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Parcelable
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
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
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.background.tiles.TileData
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.model.NfcTag
import org.openhab.habdroid.ui.TaskerItemPickerActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.toItemUpdatePrefValue
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getBackgroundTaskScheduleInMillis
import org.openhab.habdroid.util.getPrefixForBgTasks
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getStringOrEmpty
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.hasPermissions
import org.openhab.habdroid.util.isDemoModeEnabled
import org.openhab.habdroid.util.isItemUpdatePrefEnabled
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
                Log.d(TAG, "Wifi state changed")
                scheduleWorker(context, PrefKeys.SEND_WIFI_SSID)
            }
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                Log.d(TAG, "DND mode changed")
                scheduleWorker(context, PrefKeys.SEND_DND_MODE)
            }
            Intent.ACTION_LOCALE_CHANGED -> {
                Log.d(TAG, "Locale changed, recreate notification channels")
                NotificationUpdateObserver.createNotificationChannels(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed")
                KNOWN_KEYS.forEach { key -> scheduleWorker(context, key) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    for (tileId in 1..AbstractTileService.TILE_COUNT) {
                        AbstractTileService.updateTile(context, tileId)
                    }
                }
                BroadcastEventListenerService.startOrStopService(context)
            }
            ACTION_RETRY_UPLOAD -> {
                intent.getParcelableArrayListExtra<RetryInfo>(EXTRA_RETRY_INFO_LIST)?.forEach { info ->
                    enqueueItemUpload(
                        context,
                        info.tag,
                        info.itemName,
                        info.label,
                        info.value,
                        info.isImportant,
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
                    isImportant = false,
                    showToast = false,
                    taskerIntent = intent.getStringExtra(TaskerPlugin.Setting.EXTRA_PLUGIN_COMPLETION_INTENT),
                    asCommand = asCommand
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
        val isImportant: Boolean,
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
                    key == PrefKeys.DEV_ID || key == PrefKeys.DEV_ID_PREFIX_BG_TASKS -> {
                    KNOWN_KEYS.forEach { knowKey -> scheduleWorker(context, knowKey) }
                }
                key in KNOWN_KEYS -> scheduleWorker(context, key)
                key == PrefKeys.SEND_DEVICE_INFO_SCHEDULE -> schedulePeriodicTrigger(context, true)
                key == PrefKeys.FOSS_NOTIFICATIONS_ENABLED -> schedulePeriodicTrigger(context, false)
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
        const val WORKER_TAG_PREFIX_TILE = "tile-"

        internal val KNOWN_KEYS = listOf(
            PrefKeys.SEND_ALARM_CLOCK,
            PrefKeys.SEND_PHONE_STATE,
            PrefKeys.SEND_BATTERY_LEVEL,
            PrefKeys.SEND_CHARGING_STATE,
            PrefKeys.SEND_WIFI_SSID,
            PrefKeys.SEND_DND_MODE
        )
        internal val KNOWN_PERIODIC_KEYS = listOf(
            PrefKeys.SEND_BATTERY_LEVEL,
            PrefKeys.SEND_CHARGING_STATE,
            PrefKeys.SEND_WIFI_SSID,
            PrefKeys.SEND_DND_MODE
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

        fun getIntentFilterForForeground(context: Context): IntentFilter {
            val prefs = context.getPrefs()
            return IntentFilter().apply {
                // These broadcasts are already defined in the manifest, so we only need them on Android 8+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_BATTERY_LEVEL) ||
                        prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_CHARGING_STATE)) {
                        addAction(Intent.ACTION_POWER_CONNECTED)
                        addAction(Intent.ACTION_POWER_DISCONNECTED)
                        addAction(Intent.ACTION_BATTERY_LOW)
                        addAction(Intent.ACTION_BATTERY_OKAY)
                    }
                    if (prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_WIFI_SSID)) {
                        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    }
                }
                // This broadcast is only sent to registered receivers, so we need that in any case
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_DND_MODE)) {
                    addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                }
            }
        }

        fun getRequiredPermissionsForTask(task: String): Array<String>? = when {
            task == PrefKeys.SEND_PHONE_STATE -> arrayOf(Manifest.permission.READ_PHONE_STATE)
            task == PrefKeys.SEND_WIFI_SSID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            task == PrefKeys.SEND_WIFI_SSID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            else -> null
        }

        fun enqueueNfcUpdateIfNeeded(context: Context, tag: NfcTag?) {
            if (tag?.item != null && tag.state != null && tag.sitemap == null) {
                val value = if (tag.deviceId) {
                    val deviceId = context.getPrefs().getStringOrEmpty(PrefKeys.DEV_ID)
                    ItemUpdateWorker.ValueWithInfo(deviceId, deviceId)
                } else {
                    ItemUpdateWorker.ValueWithInfo(tag.state, tag.mappedState)
                }
                enqueueItemUpload(
                    context,
                    WORKER_TAG_PREFIX_NFC + tag.item,
                    tag.item,
                    tag.label,
                    value,
                    isImportant = true,
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
                    isImportant = true,
                    showToast = true,
                    asCommand = true
                )
            }
        }

        fun enqueueTileUpdate(context: Context, data: TileData) {
            enqueueItemUpload(
                context,
                WORKER_TAG_PREFIX_TILE + data.item,
                data.item,
                data.label,
                ItemUpdateWorker.ValueWithInfo(data.state, data.mappedState),
                isImportant = true,
                showToast = true,
                asCommand = true
            )
        }

        fun triggerPeriodicWork(context: Context) {
            Log.d(TAG, "triggerPeriodicWork()")
            KNOWN_PERIODIC_KEYS.forEach { key -> scheduleWorker(context, key) }
        }

        fun schedulePeriodicTrigger(context: Context, force: Boolean = false) {
            val workManager = WorkManager.getInstance(context)
            val prefs = context.getPrefs()
            val periodicWorkIsNeeded = KNOWN_PERIODIC_KEYS
                .map { key -> prefs.getStringOrNull(key).toItemUpdatePrefValue() }
                .any { value -> value.first }

            if (!periodicWorkIsNeeded && !CloudMessagingHelper.needsPollingForNotifications(context)) {
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

            val notChargingConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val repeatInterval = max(
                prefs.getBackgroundTaskScheduleInMillis(),
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
            )
            val flexInterval = max(
                (repeatInterval * 0.75).toLong(),
                PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS
            )

            Log.d(TAG, "Scheduling periodic workers with $repeatInterval repeat interval. Currently running:" +
                " notCharging $isNotChargingWorkerRunning, charging $isChargingWorkerRunning")

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

        fun scheduleWorker(context: Context, key: String) {
            val prefs = context.getPrefs()
            val setting = if (prefs.isDemoModeEnabled()) {
                Pair(false, "") // Don't attempt any uploads in demo mode
            } else {
                prefs.getStringOrNull(key).toItemUpdatePrefValue()
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
            val prefix = prefs.getPrefixForBgTasks()

            enqueueItemUpload(
                context,
                key,
                prefix + setting.second,
                null,
                value,
                isImportant = false,
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
            isImportant: Boolean,
            showToast: Boolean,
            taskerIntent: String? = null,
            asCommand: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val inputData =
                ItemUpdateWorker.buildData(itemName, label, value, showToast, taskerIntent, asCommand, isImportant)
            val workRequest = OneTimeWorkRequest.Builder(ItemUpdateWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(if (isImportant) BackoffPolicy.LINEAR else BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
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
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val requiredPermissions = getRequiredPermissionsForTask(PrefKeys.SEND_WIFI_SSID)
                val ssidToSend = wifiManager.connectionInfo.let { info ->
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !LocationManagerCompat.isLocationEnabled(locationManager) -> {
                            "LOCATION_OFF"
                        }
                        requiredPermissions != null && !context.hasPermissions(requiredPermissions) -> "NO_PERMISSION"
                        info.networkId == -1 -> "UNDEF"
                        else -> {
                            // WifiInfo#getSSID() may surround the SSID with double quote marks
                            info.ssid.removeSurrounding("\"")
                        }
                    }
                }
                ItemUpdateWorker.ValueWithInfo(ssidToSend)
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_DND_MODE] = { context ->
                val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val mode = when (nm.currentInterruptionFilter) {
                    NotificationManager.INTERRUPTION_FILTER_NONE -> "TOTAL_SILENCE"
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
                    NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS"
                    NotificationManager.INTERRUPTION_FILTER_ALL -> "OFF"
                    else -> "UNDEF"
                }
                ItemUpdateWorker.ValueWithInfo(mode)
            }
        }
    }
}
