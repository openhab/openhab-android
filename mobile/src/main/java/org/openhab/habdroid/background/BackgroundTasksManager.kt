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

package org.openhab.habdroid.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Parcelable
import android.speech.RecognizerIntent
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import androidx.core.os.bundleOf
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.parcelize.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.background.tiles.TileData
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.model.NfcTag
import org.openhab.habdroid.ui.TaskerItemPickerActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.ui.preference.widgets.toItemUpdatePrefValue
import org.openhab.habdroid.util.PendingIntent_Immutable
import org.openhab.habdroid.util.PendingIntent_Mutable
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getBackgroundTaskScheduleInMillis
import org.openhab.habdroid.util.getPrefixForBgTasks
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getStringOrEmpty
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.getWifiManager
import org.openhab.habdroid.util.hasPermissions
import org.openhab.habdroid.util.isDebugModeEnabled
import org.openhab.habdroid.util.isDemoModeEnabled
import org.openhab.habdroid.util.isItemUpdatePrefEnabled
import org.openhab.habdroid.util.isTaskerPluginEnabled
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelableArrayList
import org.openhab.habdroid.util.withAttribution

class BackgroundTasksManager : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() with intent ${intent.action}")

        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                Log.d(TAG, "Alarm clock changed")
                scheduleWorker(context, PrefKeys.SEND_ALARM_CLOCK, true)
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                Log.d(TAG, "Phone state changed")
                scheduleWorker(context, PrefKeys.SEND_PHONE_STATE, true)
            }
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY -> {
                Log.d(TAG, "Battery or charging state changed: ${intent.action}")
                scheduleWorker(context, PrefKeys.SEND_BATTERY_LEVEL, true)
                scheduleWorker(context, PrefKeys.SEND_CHARGING_STATE, true)
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                Log.d(TAG, "Wifi state changed")
                scheduleWorker(context, PrefKeys.SEND_WIFI_SSID, true)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.d(TAG, "Bluetooth device connected")
                scheduleWorker(context, PrefKeys.SEND_BLUETOOTH_DEVICES, true)
            }
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                Log.d(TAG, "DND mode changed")
                scheduleWorker(context, PrefKeys.SEND_DND_MODE, true)
            }
            in GADGETBRIDGE_ACTIONS -> {
                Log.d(TAG, "Gadgetbridge intent received")
                scheduleWorker(context, PrefKeys.SEND_GADGETBRIDGE, true, intent)
            }
            Intent.ACTION_LOCALE_CHANGED -> {
                Log.d(TAG, "Locale changed, recreate notification channels")
                NotificationUpdateObserver.createNotificationChannels(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed")
                KNOWN_KEYS.forEach { key -> scheduleWorker(context, key, true) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    for (tileId in 1..AbstractTileService.TILE_COUNT) {
                        AbstractTileService.requestTileUpdate(context, tileId)
                    }
                }
                EventListenerService.startOrStopService(context)
            }
            ACTION_RETRY_UPLOAD -> {
                intent.parcelableArrayList<RetryInfo>(EXTRA_RETRY_INFO_LIST)?.forEach { info ->
                    enqueueItemUpload(
                        context,
                        info.tag,
                        info.itemName,
                        info.label,
                        info.value,
                        info.isImportant,
                        info.showToast,
                        info.taskerIntent,
                        info.asCommand,
                        forceUpdate = true
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
                    asCommand = asCommand,
                    forceUpdate = true
                )
                if (isOrderedBroadcast) {
                    resultCode = TaskerPlugin.Setting.RESULT_CODE_PENDING
                }
            }
            ACTION_VOICE_RESULT -> {
                val voiceCommand = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.elementAtOrNull(0)
                    ?: return
                Log.i(TAG, "Recognized text: $voiceCommand")

                enqueueItemUpload(
                    context = context,
                    primaryTag = WORKER_TAG_VOICE_COMMAND,
                    itemName = "VoiceCommand",
                    label = context.getString(R.string.voice_command),
                    value = ItemUpdateWorker.ValueWithInfo(
                        voiceCommand,
                        type = ItemUpdateWorker.ValueType.VoiceCommand
                    ),
                    isImportant = true,
                    showToast = true,
                    asCommand = true,
                    forceUpdate = true,
                    primaryServer = intent.getBooleanExtra(EXTRA_FROM_BACKGROUND, false)
                )
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
        val asCommand: Boolean,
        val primaryServer: Boolean
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
                    key == PrefKeys.DEV_ID || key == PrefKeys.DEV_ID_PREFIX_BG_TASKS ||
                    key == PrefKeys.PRIMARY_SERVER_ID -> {
                    KNOWN_KEYS.forEach { knowKey -> scheduleWorker(context, knowKey, true) }
                }
                key in KNOWN_KEYS -> scheduleWorker(context, key, true)
                key == PrefKeys.SEND_DEVICE_INFO_SCHEDULE -> schedulePeriodicTrigger(context, true)
                key == PrefKeys.FOSS_NOTIFICATIONS_ENABLED -> schedulePeriodicTrigger(context, false)
            }
        }
    }

    companion object {
        private val TAG = BackgroundTasksManager::class.java.simpleName

        internal const val ACTION_RETRY_UPLOAD = "org.openhab.habdroid.background.action.RETRY_UPLOAD"
        internal const val ACTION_CLEAR_UPLOAD = "org.openhab.habdroid.background.action.CLEAR_UPLOAD"
        private const val ACTION_VOICE_RESULT = "org.openhab.habdroid.background.action.VOICE_RESULT"
        internal const val EXTRA_RETRY_INFO_LIST = "retryInfoList"
        private const val EXTRA_FROM_BACKGROUND = "fromBackground"

        private const val WORKER_TAG_ITEM_UPLOADS = "itemUploads"
        private const val WORKER_TAG_PERIODIC_TRIGGER = "periodicTrigger"
        private const val WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING = "periodicTriggerNotCharging"
        private const val WORKER_TAG_PERIODIC_TRIGGER_CHARGING = "periodicTriggerCharging"
        const val WORKER_TAG_PREFIX_NFC = "nfc-"
        const val WORKER_TAG_PREFIX_TASKER = "tasker-"
        const val WORKER_TAG_PREFIX_WIDGET = "widget-"
        const val WORKER_TAG_PREFIX_TILE = "tile-"
        const val WORKER_TAG_PREFIX_TILE_ID = "tile_id-"
        const val WORKER_TAG_VOICE_COMMAND = "voiceCommand"
        fun buildWorkerTagForServer(id: Int) = "server-id-$id"

        private const val GADGETBRIDGE_ACTION_PREFIX = "nodomain.freeyourgadget.gadgetbridge."
        private val GADGETBRIDGE_ACTIONS = listOf(
            "${GADGETBRIDGE_ACTION_PREFIX}FellAsleep",
            "${GADGETBRIDGE_ACTION_PREFIX}WokeUp",
            "${GADGETBRIDGE_ACTION_PREFIX}StartNonWear"
        )

        internal val KNOWN_KEYS = listOf(
            PrefKeys.SEND_ALARM_CLOCK,
            PrefKeys.SEND_PHONE_STATE,
            PrefKeys.SEND_BATTERY_LEVEL,
            PrefKeys.SEND_CHARGING_STATE,
            PrefKeys.SEND_WIFI_SSID,
            PrefKeys.SEND_BLUETOOTH_DEVICES,
            PrefKeys.SEND_DND_MODE,
            PrefKeys.SEND_GADGETBRIDGE
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
            "com.android.calendar",
            "com.samsung.android.calendar",
            "com.miui.securitycenter",
            "org.thoughtcrime.securesms"
        )
        private val VALUE_GETTER_MAP = HashMap<String, (Context, Intent?) -> ItemUpdateWorker.ValueWithInfo?>()

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
                    if (prefs.isItemUpdatePrefEnabled(PrefKeys.SEND_GADGETBRIDGE)) {
                        GADGETBRIDGE_ACTIONS.forEach { action ->
                            addAction(action)
                        }
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
            task == PrefKeys.SEND_BLUETOOTH_DEVICES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH)
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
                    asCommand = true,
                    forceUpdate = true
                )
            }
        }

        fun enqueueWidgetItemUpdateIfNeeded(context: Context, data: ItemUpdateWidget.ItemUpdateWidgetData) {
            if (data.item.isNotEmpty() && !data.command.isNullOrEmpty()) {
                enqueueItemUpload(
                    context,
                    WORKER_TAG_PREFIX_WIDGET + data.item,
                    data.item,
                    data.label,
                    ItemUpdateWorker.ValueWithInfo(data.command, data.mappedState),
                    isImportant = true,
                    showToast = true,
                    asCommand = true,
                    forceUpdate = true
                )
            }
        }

        fun enqueueTileUpdate(context: Context, data: TileData, tileId: Int) {
            enqueueItemUpload(
                context,
                WORKER_TAG_PREFIX_TILE + data.item,
                data.item,
                data.label,
                ItemUpdateWorker.ValueWithInfo(data.state, data.mappedState),
                isImportant = true,
                showToast = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q,
                asCommand = true,
                forceUpdate = true,
                secondaryTags = listOf(WORKER_TAG_PREFIX_TILE_ID + tileId)
            )
        }

        fun buildVoiceRecognitionIntent(context: Context, fromBackground: Boolean): Intent {
            val callbackIntent = Intent(context, BackgroundTasksManager::class.java).apply {
                action = ACTION_VOICE_RESULT
                putExtra(EXTRA_FROM_BACKGROUND, fromBackground)
            }
            val callbackPendingIntent = PendingIntent.getBroadcast(
                context,
                if (fromBackground) 1 else 0,
                callbackIntent,
                PendingIntent_Mutable
            )

            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // Display an hint to the user about what he should say.
                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.info_voice_input))
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, callbackPendingIntent)
            }
        }

        fun scheduleUpdatesForAllKeys(context: Context) {
            Log.d(TAG, "scheduleUpdatesForAllKeys()")
            KNOWN_KEYS.forEach { key -> scheduleWorker(context, key, false) }
        }

        fun schedulePeriodicTrigger(context: Context, force: Boolean = false) {
            val workManager = WorkManager.getInstance(context)
            val prefs = context.getPrefs()
            val periodicWorkIsNeeded = KNOWN_PERIODIC_KEYS
                .map { key -> prefs.getStringOrNull(key).toItemUpdatePrefValue() }
                .any { value -> value.first }
            val widgetShowsState = AppWidgetManager.getInstance(context)
                ?.getAppWidgetIds(ComponentName(context, ItemUpdateWidget::class.java))
                ?.map { id -> ItemUpdateWidget.getInfoForWidget(context, id) }
                ?.any { info -> info.showState } ?: false

            if (!periodicWorkIsNeeded &&
                !CloudMessagingHelper.needsPollingForNotifications(context) &&
                !widgetShowsState
            ) {
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
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .addTag(WORKER_TAG_PERIODIC_TRIGGER)
                .addTag(WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING)
                .build()

            val chargingConstraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
                requiresBatteryNotLow = true,
                requiresCharging = true
            )

            val chargingWorkRequest = PeriodicWorkRequest.Builder(PeriodicItemUpdateWorker::class.java,
                    PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS,
                    PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(chargingConstraints)
                .addTag(WORKER_TAG_PERIODIC_TRIGGER)
                .addTag(WORKER_TAG_PERIODIC_TRIGGER_CHARGING)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING,
                ExistingPeriodicWorkPolicy.UPDATE,
                notChargingWorkRequest
            )
            workManager.enqueueUniquePeriodicWork(
                WORKER_TAG_PERIODIC_TRIGGER_CHARGING,
                ExistingPeriodicWorkPolicy.UPDATE,
                chargingWorkRequest
            )
        }

        fun scheduleWorker(context: Context, key: String, isImportant: Boolean, intent: Intent? = null) {
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
                if (setting.second.isNotEmpty()) {
                    getLastUpdateCache(context).edit {
                        Log.d(TAG, "Remove ${setting.second} from last update cache")
                        remove(setting.second)
                    }
                }
                return
            }

            val attributionContext = context.withAttribution(OpenHabApplication.DATA_ACCESS_TAG_SEND_DEV_INFO)
            val value = VALUE_GETTER_MAP[key]?.invoke(attributionContext, intent)
            Log.d(TAG, "Got value '$value' for $key")

            showDebugNotificationIfRequired(context, value?.debugInfo)

            if (value == null) {
                return
            }
            val prefix = prefs.getPrefixForBgTasks()

            enqueueItemUpload(
                context,
                key,
                prefix + setting.second,
                null,
                value,
                isImportant,
                showToast = false,
                asCommand = true,
                forceUpdate = false
            )
        }

        private fun showDebugNotificationIfRequired(context: Context, debugInfo: String?) {
            if (debugInfo == null || !context.getPrefs().isDebugModeEnabled()) {
                return
            }

            val copyIntent = Intent(context, CopyToClipboardReceiver::class.java)
            copyIntent.putExtra(CopyToClipboardReceiver.EXTRA_TO_COPY, debugInfo)
            val copyPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                copyIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent_Immutable
            )

            val copyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_outline_format_align_left_grey_24dp,
                context.getString(R.string.copy_debug_info),
                copyPendingIntent
            )

            val notification = NotificationCompat.Builder(context, NotificationUpdateObserver.CHANNEL_ID_BACKGROUND)
                .setSmallIcon(R.drawable.ic_openhab_appicon_white_24dp)
                .setContentTitle(context.getString(R.string.send_device_info_to_server_short))
                .setContentText(debugInfo)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(debugInfo)
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setColor(ContextCompat.getColor(context, R.color.openhab_orange))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setAutoCancel(true)
                .setGroup("debug")
                .addAction(copyAction.build())
                .build()

            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify("debug", System.currentTimeMillis().toInt(), notification)
        }

        private fun enqueueItemUpload(
            context: Context,
            primaryTag: String,
            itemName: String,
            label: String?,
            value: ItemUpdateWorker.ValueWithInfo,
            isImportant: Boolean,
            showToast: Boolean,
            taskerIntent: String? = null,
            asCommand: Boolean,
            forceUpdate: Boolean,
            primaryServer: Boolean = true,
            secondaryTags: List<String>? = null
        ) {
            val workManager = WorkManager.getInstance(context)

            if (!forceUpdate && getLastUpdateCache(context).getStringOrNull(itemName) == value.value) {
                Log.i(TAG, "Don't send update for item $itemName with value $value")
                workManager.cancelUniqueWork(primaryTag)
                workManager.pruneWork()
                return
            }

            val prefs = context.getPrefs()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val inputData = ItemUpdateWorker.buildData(
                itemName,
                label,
                value,
                showToast,
                taskerIntent,
                asCommand,
                isImportant,
                primaryServer
            )
            val workRequest = OneTimeWorkRequest.Builder(ItemUpdateWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(if (isImportant) BackoffPolicy.LINEAR else BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .addTag(primaryTag)
                .addTag(WORKER_TAG_ITEM_UPLOADS)
                .addTag(
                    buildWorkerTagForServer(
                        if (primaryServer) prefs.getPrimaryServerId() else prefs.getActiveServerId()
                    )
                )
                .setInputData(inputData)

            secondaryTags?.forEach {
                workRequest.addTag(it)
            }

            if (isImportant) {
                workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            Log.d(TAG, "Scheduling work for tag $primaryTag")
            workManager.enqueueUniqueWork(primaryTag, ExistingWorkPolicy.REPLACE, workRequest.build())
        }

        fun getLastUpdateCache(context: Context): SharedPreferences {
            return context.getSharedPreferences("background-tasks-cache", Context.MODE_PRIVATE)
        }

        init {
            VALUE_GETTER_MAP[PrefKeys.SEND_ALARM_CLOCK] = { context, _ ->
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val info: AlarmManager.AlarmClockInfo? = alarmManager.nextAlarmClock
                val sender = info?.showIntent?.creatorPackage
                Log.d(TAG, "Alarm sent by $sender")
                val timeStamp = info?.triggerTime?.let { time ->
                    SimpleDateFormat("HH:mm yyyy-MM-dd", Locale.US).format(time)
                }

                val ignoreSender = when {
                    sender in IGNORED_PACKAGES_FOR_ALARM -> true
                    sender == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> true
                    else -> false
                }

                @StringRes val debugInfoRes: Int
                val time: String = if (ignoreSender || info == null) {
                    debugInfoRes = R.string.settings_alarm_clock_debug_ignored
                    "UNDEF"
                } else {
                    debugInfoRes = R.string.settings_alarm_clock_debug
                    info.triggerTime.toString()
                }

                ItemUpdateWorker.ValueWithInfo(
                    value = time,
                    type = ItemUpdateWorker.ValueType.Timestamp,
                    debugInfo = context.getString(debugInfoRes, timeStamp, sender)
                )
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_PHONE_STATE] = { context, _ ->
                val requiredPermissions = getRequiredPermissionsForTask(PrefKeys.SEND_PHONE_STATE)

                val itemState = if (requiredPermissions != null && !context.hasPermissions(requiredPermissions)) {
                    "NO_PERMISSION"
                } else {
                    val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                    val callState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        manager.callStateForSubscription
                    } else {
                        @Suppress("DEPRECATION")
                        manager.callState
                    }

                    when (callState) {
                        TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                        TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                        TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                        else -> "UNDEF"
                    }
                }

                ItemUpdateWorker.ValueWithInfo(itemState)
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_BATTERY_LEVEL] = { context, _ ->
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ItemUpdateWorker.ValueWithInfo(batteryLevel.toString())
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_CHARGING_STATE] = { context, _ ->
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context.registerReceiver(null, ifilter)
                }
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                Log.d(TAG, "EXTRA_STATUS is $status, EXTRA_PLUGGED is $plugged")
                val state = if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL) {
                    when (plugged) {
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                        else -> "UNKNOWN_CHARGER"
                    }
                } else {
                    "UNDEF"
                }
                ItemUpdateWorker.ValueWithInfo(state, type = ItemUpdateWorker.ValueType.MapUndefToOffForSwitchItems)
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_WIFI_SSID] = { context, _ ->
                val wifiManager = context.getWifiManager(OpenHabApplication.DATA_ACCESS_TAG_SEND_DEV_INFO)
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val requiredPermissions = getRequiredPermissionsForTask(PrefKeys.SEND_WIFI_SSID)
                // TODO: Replace deprecated function
                @Suppress("DEPRECATION") val ssidToSend = wifiManager.connectionInfo.let { info ->
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
            @RequiresApi(Build.VERSION_CODES.M)
            VALUE_GETTER_MAP[PrefKeys.SEND_DND_MODE] = { context, _ ->
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
            VALUE_GETTER_MAP[PrefKeys.SEND_BLUETOOTH_DEVICES] = { context, _ ->
                fun BluetoothDevice.isConnected(): Boolean {
                    return try {
                        val m = javaClass.getMethod("isConnected")
                        m.invoke(this) as Boolean
                    } catch (e: Exception) {
                        throw IllegalStateException(e)
                    }
                }

                val requiredPermissions = getRequiredPermissionsForTask(PrefKeys.SEND_BLUETOOTH_DEVICES)
                val state = if (requiredPermissions != null && !context.hasPermissions(requiredPermissions)) {
                    "NO_PERMISSION"
                } else {
                    val bm = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                    bm.adapter.bondedDevices
                        .filter { device -> device.isConnected() }
                        .joinToString("|") { device -> device.address }
                        .orDefaultIfEmpty("UNDEF")
                }

                ItemUpdateWorker.ValueWithInfo(state)
            }
            VALUE_GETTER_MAP[PrefKeys.SEND_GADGETBRIDGE] = { _, intent ->
                if (intent == null) {
                    Log.d(TAG, "VALUE_GETTER_MAP called without intent for key SEND_GADGETBRIDGE")
                    null
                } else {
                    val state = intent.action?.removePrefix(GADGETBRIDGE_ACTION_PREFIX) ?: "UNDEF"
                    ItemUpdateWorker.ValueWithInfo(state)
                }
            }
        }
    }
}
