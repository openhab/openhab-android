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

package org.openhab.habdroid.background

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Parcelable
import android.speech.RecognizerIntent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.parcelize.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.background.tiles.TileData
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.model.CloudNotificationAction
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
import org.openhab.habdroid.util.isDebugModeEnabled
import org.openhab.habdroid.util.isDemoModeEnabled
import org.openhab.habdroid.util.parcelableArrayList
import org.openhab.habdroid.util.showToast
import org.openhab.habdroid.util.withAttribution

class BackgroundTasksManager(private val context: Application) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs get() = context.getPrefs()
    private val workManager get() = WorkManager.getInstance(context)

    private val valueGetterMap: Map<String, (Context) -> ItemUpdateWorker.ValueWithInfo> = mapOf(
        PrefKeys.SEND_ALARM_CLOCK to UpdateValueGetters::buildAlarmChangeUpdateInfo,
        PrefKeys.SEND_PHONE_STATE to UpdateValueGetters::buildPhoneStateUpdateInfo,
        PrefKeys.SEND_BATTERY_LEVEL to UpdateValueGetters::buildBatteryLevelUpdateInfo,
        PrefKeys.SEND_CHARGING_STATE to UpdateValueGetters::buildChargingStateUpdateInfo,
        PrefKeys.SEND_WIFI_SSID to UpdateValueGetters::buildWifiSsidUpdateInfo,
        PrefKeys.SEND_DND_MODE to UpdateValueGetters::buildDndModeUpdateInfo,
        PrefKeys.SEND_BLUETOOTH_DEVICES to UpdateValueGetters::buildBluetoothDevicesUpdateInfo
    )

    internal val lastUpdateCachePrefs by lazy {
        context.getSharedPreferences("background-tasks-cache", Context.MODE_PRIVATE)
    }

    fun initialize() {
        val infoLiveData = workManager.getWorkInfosByTagLiveData(WORKER_TAG_ITEM_UPLOADS)
        infoLiveData.observeForever(NotificationUpdateObserver(context))

        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    fun enqueueNfcUpdateIfNeeded(tag: NfcTag?) {
        if (tag?.item != null && tag.state != null && tag.sitemap == null) {
            val value = if (tag.deviceId) {
                val deviceId = prefs.getStringOrEmpty(PrefKeys.DEV_ID)
                ItemUpdateWorker.ValueWithInfo(deviceId, deviceId)
            } else {
                ItemUpdateWorker.ValueWithInfo(tag.state, tag.mappedState)
            }
            enqueueItemUpload(
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

    fun enqueueWidgetItemUpdateIfNeeded(data: ItemUpdateWidget.ItemUpdateWidgetData) {
        if (data.item.isNotEmpty() && !data.command.isNullOrEmpty()) {
            enqueueItemUpload(
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

    fun enqueueTileUpdate(data: TileData, tileId: Int) {
        enqueueItemUpload(
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

    fun enqueueNotificationAction(action: CloudNotificationAction.Action.ItemCommandAction) {
        enqueueItemUpload(
            WORKER_TAG_PREFIX_NOTIFICATION + action.itemName,
            action.itemName,
            null,
            ItemUpdateWorker.ValueWithInfo(action.command),
            isImportant = true,
            showToast = true,
            asCommand = true,
            forceUpdate = true
        )
    }

    fun scheduleUpdatesForAllKeys() {
        Log.d(TAG, "scheduleUpdatesForAllKeys()")
        KNOWN_KEYS.forEach { key -> scheduleWorker(key, false) }
    }

    fun schedulePeriodicTrigger(force: Boolean = false) {
        val periodicWorkIsNeeded = KNOWN_PERIODIC_KEYS
            .map { key -> prefs.getStringOrNull(key).toItemUpdatePrefValue() }
            .any { value -> value.first }
        val widgetShowsState = AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, ItemUpdateWidget::class.java))
            ?.map { id -> ItemUpdateWidget.getInfoForWidget(context, id) }
            ?.any { info -> info.showState }
            ?: false

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

        Log.d(
            TAG,
            "Scheduling periodic workers with $repeatInterval repeat interval. Currently running:" +
                " notCharging $isNotChargingWorkerRunning, charging $isChargingWorkerRunning"
        )

        val notChargingWorkRequest = PeriodicWorkRequest.Builder(
            PeriodicItemUpdateWorker::class.java,
            repeatInterval,
            TimeUnit.MILLISECONDS,
            flexInterval,
            TimeUnit.MILLISECONDS
        )
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .addTag(WORKER_TAG_PERIODIC_TRIGGER)
            .addTag(WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING)
            .build()

        val chargingConstraints = Constraints(
            requiredNetworkType = NetworkType.CONNECTED,
            requiresBatteryNotLow = true,
            requiresCharging = true
        )

        val chargingWorkRequest = PeriodicWorkRequest.Builder(
            PeriodicItemUpdateWorker::class.java,
            PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
            PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
            TimeUnit.MILLISECONDS
        )
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

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        when {
            key == null -> return

            key == PrefKeys.DEMO_MODE && prefs.isDemoModeEnabled() -> {
                // Demo mode was enabled -> cancel all uploads and clear DB to clear out notifications
                with(WorkManager.getInstance(context)) {
                    cancelAllWorkByTag(WORKER_TAG_ITEM_UPLOADS)
                    cancelAllWorkByTag(WORKER_TAG_PERIODIC_TRIGGER)
                    pruneWork()
                }
            }

            // Demo mode was disabled -> reschedule uploads
            (key == PrefKeys.DEMO_MODE && !prefs.isDemoModeEnabled()) ||
                // Prefix has been changed -> reschedule uploads
                key == PrefKeys.DEV_ID ||
                key == PrefKeys.DEV_ID_PREFIX_BG_TASKS ||
                key == PrefKeys.PRIMARY_SERVER_ID -> {
                KNOWN_KEYS.forEach { knowKey -> scheduleWorker(knowKey, true) }
            }

            key in KNOWN_KEYS -> scheduleWorker(key, true)

            key == PrefKeys.SEND_DEVICE_INFO_SCHEDULE -> schedulePeriodicTrigger(true)

            key == PrefKeys.FOSS_NOTIFICATIONS_ENABLED -> schedulePeriodicTrigger(false)
        }
    }

    internal fun handleInternalBroadcast(intent: Intent) {
        when (intent.action) {
            ACTION_RETRY_UPLOAD ->
                intent.parcelableArrayList<RetryInfo>(EXTRA_RETRY_INFO_LIST)?.forEach { info ->
                    enqueueItemUpload(
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

            ACTION_CLEAR_UPLOAD ->
                WorkManager.getInstance(context).pruneWork()

            ACTION_VOICE_RESULT -> {
                val voiceCommand = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.elementAtOrNull(0)
                    ?: return
                Log.i(TAG, "Recognized text: $voiceCommand")

                enqueueItemUpload(
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

            ACTION_COPY_TO_CLIPBOARD -> {
                val toCopy = intent.getStringExtra(EXTRA_TO_COPY) ?: return
                Log.d(TAG, "Copy to clipboard: $toCopy")
                val clipboard = context.getSystemService<ClipboardManager>()
                val clip = ClipData.newPlainText(context.getString(R.string.app_name), toCopy)
                clipboard?.setPrimaryClip(clip)
                context.showToast(context.getString(R.string.copied_item_name, toCopy))
            }
        }
    }

    internal fun handleBootCompletedIntent() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG_ITEM_UPLOADS)
        KNOWN_KEYS.forEach { key -> scheduleWorker(key, true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            for (tileId in 1..AbstractTileService.TILE_COUNT) {
                AbstractTileService.requestTileUpdate(context, tileId)
            }
        }
    }

    internal fun handleTaskerIntent(intent: Intent): Boolean {
        val bundle = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE) ?: return false
        val itemName = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_NAME)
        val label = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_LABEL)
        val state = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_STATE)
        val mappedState = bundle.getString(TaskerItemPickerActivity.EXTRA_ITEM_MAPPED_STATE)
        val asCommand = bundle.getBoolean(TaskerItemPickerActivity.EXTRA_ITEM_AS_COMMAND, true)
        if (itemName.isNullOrEmpty() || state.isNullOrEmpty()) {
            return false
        }
        enqueueItemUpload(
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
        return true
    }

    internal fun scheduleWorker(key: String, isImportant: Boolean) {
        val (enabled, itemName) = if (prefs.isDemoModeEnabled()) {
            false to "" // Don't attempt any uploads in demo mode
        } else {
            prefs.getStringOrNull(key).toItemUpdatePrefValue()
        }

        if (key in KNOWN_PERIODIC_KEYS) {
            schedulePeriodicTrigger()
        }

        if (!enabled) {
            with(WorkManager.getInstance(context)) {
                cancelAllWorkByTag(key)
                pruneWork()
            }
            if (itemName.isNotEmpty()) {
                lastUpdateCachePrefs.edit {
                    Log.d(TAG, "Remove $itemName from last update cache")
                    remove(itemName)
                }
            }
            return
        }

        val attributionContext = context.withAttribution(OpenHabApplication.DATA_ACCESS_TAG_SEND_DEV_INFO)
        val value = valueGetterMap[key]?.invoke(attributionContext)
        Log.d(TAG, "Got value '$value' for $key")

        showDebugNotificationIfRequired(value?.debugInfo)

        if (value != null) {
            enqueueItemUpload(
                key,
                prefs.getPrefixForBgTasks() + itemName,
                null,
                value,
                isImportant,
                showToast = false,
                asCommand = true,
                forceUpdate = false
            )
        }
    }

    private fun showDebugNotificationIfRequired(debugInfo: String?) {
        if (debugInfo == null || !prefs.isDebugModeEnabled()) {
            return
        }

        val copyIntent = Intent(context, InternalEventBroadcastReceiver::class.java)
            .setAction(ACTION_COPY_TO_CLIPBOARD)
            .putExtra(EXTRA_TO_COPY, debugInfo)
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(debugInfo))
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
        if (!forceUpdate && lastUpdateCachePrefs.getStringOrNull(itemName) == value.value) {
            Log.i(TAG, "Don't send update for item $itemName with value $value")
            workManager.cancelUniqueWork(primaryTag)
            workManager.pruneWork()
            return
        }

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
            .setBackoffCriteria(
                if (isImportant) BackoffPolicy.LINEAR else BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
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

    companion object {
        internal val TAG = BackgroundTasksManager::class.java.simpleName

        private const val ACTION_RETRY_UPLOAD = "org.openhab.habdroid.background.action.RETRY_UPLOAD"
        private const val ACTION_CLEAR_UPLOAD = "org.openhab.habdroid.background.action.CLEAR_UPLOAD"
        private const val ACTION_VOICE_RESULT = "org.openhab.habdroid.background.action.VOICE_RESULT"
        private const val ACTION_COPY_TO_CLIPBOARD = "org.openhab.habdroid.background.action.COPY_TO_CLIPBOARD"
        private const val EXTRA_RETRY_INFO_LIST = "retryInfoList"
        private const val EXTRA_FROM_BACKGROUND = "fromBackground"
        private const val EXTRA_TO_COPY = "extra_to_copy"

        private const val WORKER_TAG_ITEM_UPLOADS = "itemUploads"
        private const val WORKER_TAG_PERIODIC_TRIGGER = "periodicTrigger"
        private const val WORKER_TAG_PERIODIC_TRIGGER_NOT_CHARGING = "periodicTriggerNotCharging"
        private const val WORKER_TAG_PERIODIC_TRIGGER_CHARGING = "periodicTriggerCharging"
        const val WORKER_TAG_PREFIX_NFC = "nfc-"
        const val WORKER_TAG_PREFIX_TASKER = "tasker-"
        const val WORKER_TAG_PREFIX_WIDGET = "widget-"
        const val WORKER_TAG_PREFIX_TILE = "tile-"
        const val WORKER_TAG_PREFIX_NOTIFICATION = "notification-"
        const val WORKER_TAG_PREFIX_TILE_ID = "tile_id-"
        const val WORKER_TAG_VOICE_COMMAND = "voiceCommand"

        fun buildWorkerTagForServer(id: Int) = "server-id-$id"

        internal val KNOWN_KEYS = listOf(
            PrefKeys.SEND_ALARM_CLOCK,
            PrefKeys.SEND_PHONE_STATE,
            PrefKeys.SEND_BATTERY_LEVEL,
            PrefKeys.SEND_CHARGING_STATE,
            PrefKeys.SEND_WIFI_SSID,
            PrefKeys.SEND_BLUETOOTH_DEVICES,
            PrefKeys.SEND_DND_MODE
        )
        internal val KNOWN_PERIODIC_KEYS = listOf(
            PrefKeys.SEND_BATTERY_LEVEL,
            PrefKeys.SEND_CHARGING_STATE,
            PrefKeys.SEND_WIFI_SSID,
            PrefKeys.SEND_DND_MODE
        )

        fun getRequiredPermissionsForTask(task: String): Array<String>? = when (task) {
            PrefKeys.SEND_PHONE_STATE -> UpdateValueGetters.SEND_PHONE_STATE_PERMISSIONS
            PrefKeys.SEND_WIFI_SSID -> UpdateValueGetters.SEND_WIFI_SSID_PERMISSIONS
            PrefKeys.SEND_BLUETOOTH_DEVICES -> UpdateValueGetters.SEND_BLUETOOTH_DEVICE_PERMISSIONS
            else -> null
        }

        internal fun buildClearUploadIntent(context: Context) =
            Intent(context, InternalEventBroadcastReceiver::class.java)
                .setAction(ACTION_CLEAR_UPLOAD)

        internal fun buildRetryUploadIntent(context: Context, retryInfoList: ArrayList<RetryInfo>) =
            Intent(context, InternalEventBroadcastReceiver::class.java)
                .setAction(ACTION_RETRY_UPLOAD)
                .putExtra(EXTRA_RETRY_INFO_LIST, retryInfoList)

        fun buildVoiceRecognitionIntent(context: Context, fromBackground: Boolean): Intent {
            val callbackIntent = Intent(context, InternalEventBroadcastReceiver::class.java)
                .setAction(ACTION_VOICE_RESULT)
                .putExtra(EXTRA_FROM_BACKGROUND, fromBackground)
            val callbackPendingIntent = PendingIntent.getBroadcast(
                context,
                if (fromBackground) 1 else 0,
                callbackIntent,
                PendingIntent_Mutable
            )

            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                // Display a hint to the user about what he should say.
                .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.info_voice_input))
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, callbackPendingIntent)
        }
    }
}
