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

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.parcelize.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.background.NotificationUpdateObserver.Companion.NOTIFICATION_ID_BACKGROUND_WORK_RUNNING
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.ui.TaskerItemPickerActivity
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getPrefixForVoice
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.hasCause
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.showToast

class ItemUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val isImportant = inputData.getBoolean(INPUT_DATA_IS_IMPORTANT, false)
        if (isImportant) {
            setForegroundAsync(getForegroundInfo())
        }
        ConnectionFactory.waitForInitialization()

        Log.d(TAG, "Trying to get connection")
        val connection = if (inputData.getBoolean(INPUT_DATA_PRIMARY_SERVER, false)) {
            ConnectionFactory.primaryUsableConnection?.connection
        } else {
            ConnectionFactory.activeUsableConnection?.connection
        }

        val showToast = inputData.getBoolean(INPUT_DATA_SHOW_TOAST, false)
        val taskerIntent = inputData.getString(INPUT_DATA_TASKER_INTENT)

        if (connection == null) {
            Log.e(TAG, "Got no connection")
            return retryOrFail(isImportant, showToast, taskerIntent, false, 500)
        }

        val itemName = inputData.getString(INPUT_DATA_ITEM_NAME)!!
        val value = inputData.getValueWithInfo(INPUT_DATA_VALUE)!!

        if (value.type == ValueType.VoiceCommand) {
            return handleVoiceCommand(applicationContext, connection, value)
        }

        return try {
            val item = ItemClient.loadItem(connection, itemName)
            if (item == null) {
                sendTaskerSignalIfNeeded(
                    taskerIntent,
                    true,
                    500,
                    applicationContext.getString(R.string.item_update_error_couldnt_get_item_type)
                )
                return Result.failure(buildOutputData(true, 500))
            }

            val valueToBeSent = mapValueAccordingToItemTypeAndValue(value, item)
            Log.d(TAG, "Trying to update Item '$itemName' to value $valueToBeSent, was ${value.value}")
            val actualMappedValue = if (value.value != valueToBeSent) {
                valueToBeSent
            } else {
                value.mappedValue.orDefaultIfEmpty(value.value)
            }

            val result = if (inputData.getBoolean(INPUT_DATA_AS_COMMAND, false) && valueToBeSent != "UNDEF") {
                connection.httpClient
                    .post("rest/items/$itemName", valueToBeSent)
                    .asStatus()
            } else {
                connection.httpClient
                    .put("rest/items/$itemName/state", valueToBeSent)
                    .asStatus()
            }
            Log.d(TAG, "Item '$itemName' successfully updated to value $valueToBeSent")
            if (showToast) {
                val label = inputData.getString(INPUT_DATA_LABEL).orDefaultIfEmpty(itemName)
                applicationContext.showToast(
                    getItemUpdateSuccessMessage(applicationContext, label, valueToBeSent, actualMappedValue)
                )
            }
            sendTaskerSignalIfNeeded(taskerIntent, true, result.statusCode, null)
            BackgroundTasksManager.getLastUpdateCache(applicationContext).edit {
                putString(itemName, value.value)
            }
            Result.success(buildOutputData(true, result.statusCode, valueToBeSent))
        } catch (e: HttpClient.HttpException) {
            Log.e(TAG, "Error updating item '$itemName' to '$value'. Got HTTP error ${e.statusCode}", e)
            if (e.hasCause(SocketTimeoutException::class.java) || e.statusCode in RETRY_HTTP_ERROR_CODES) {
                retryOrFail(isImportant, showToast, taskerIntent, true, e.statusCode)
            } else {
                sendTaskerSignalIfNeeded(taskerIntent, true, e.statusCode, e.localizedMessage)
                Result.failure(buildOutputData(true, e.statusCode))
            }
        }
    }

    private fun mapValueAccordingToItemTypeAndValue(value: ValueWithInfo, item: Item) = when {
        value.value == "TOGGLE" && item.canBeToggled() -> determineOppositeState(item)
        value.type == ValueType.Timestamp && item.isOfTypeOrGroupType(Item.Type.DateTime) && value.value != "UNDEF" ->
            convertToTimestamp(value)
        value.type == ValueType.MapUndefToOffForSwitchItems && item.isOfTypeOrGroupType(Item.Type.Switch) ->
            if (value.value == "UNDEF") "OFF" else "ON"
        else -> value.value
    }

    private fun determineOppositeState(item: Item) = when {
        item.isOfTypeOrGroupType(Item.Type.Rollershutter) || item.isOfTypeOrGroupType(Item.Type.Dimmer) -> {
            // If shutter is (partially) closed, open it, else close it
            if (item.state?.asNumber?.value == 0F) "100" else "0"
        }
        item.isOfTypeOrGroupType(Item.Type.Contact) -> {
            if (item.state?.asString == "OPEN") "CLOSED" else "OPEN"
        }
        item.isOfTypeOrGroupType(Item.Type.Player) -> {
            if (item.state?.asString == "PAUSE") "PLAY" else "PAUSE"
        }
        item.state?.asBoolean == true -> {
            "OFF"
        }
        else -> {
            "ON"
        }
    }

    private fun convertToTimestamp(value: ValueWithInfo): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+0000", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(value.value.toLong())
    }

    private fun retryOrFail(
        isImportant: Boolean,
        showToast: Boolean,
        taskerIntent: String?,
        hasConnection: Boolean,
        httpCode: Int
    ): Result {
        val maxRunCount = if (isImportant) 3 else 10
        return if (runAttemptCount <= maxRunCount) {
            if (showToast) {
                applicationContext.showToast(R.string.item_update_error_no_connection_retry, Toast.LENGTH_LONG)
            }
            Result.retry()
        } else {
            val message = if (hasConnection) {
                applicationContext.getHumanReadableErrorMessage("", httpCode, null, true)
            } else {
                applicationContext.getString(R.string.item_update_error_no_connection)
            }

            sendTaskerSignalIfNeeded(taskerIntent, hasConnection, httpCode, message)
            Result.failure(buildOutputData(hasConnection, httpCode))
        }
    }

    private fun sendTaskerSignalIfNeeded(
        taskerIntent: String?,
        hasConnection: Boolean,
        httpCode: Int,
        errorMessage: CharSequence?
    ) {
        if (taskerIntent == null) {
            return
        }
        val resultCode = when {
            errorMessage == null -> TaskerPlugin.Setting.RESULT_CODE_OK
            hasConnection -> TaskerItemPickerActivity.getResultCodeForHttpFailure(httpCode)
            else -> TaskerItemPickerActivity.RESULT_CODE_NO_CONNECTION
        }
        Log.d(TAG, "Tasker result code: $resultCode, HTTP code: $httpCode")
        TaskerPlugin.Setting.signalFinish(
            applicationContext,
            taskerIntent,
            resultCode,
            bundleOf(
                TaskerItemPickerActivity.VAR_HTTP_CODE to httpCode,
                TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE to errorMessage
            )
        )
    }

    private suspend fun handleVoiceCommand(context: Context, connection: Connection, value: ValueWithInfo): Result {
        Log.d(TAG, "handleVoiceCommand(value = $value")
        val headers = mapOf("Accept-Language" to Locale.getDefault().language)
        var voiceCommand = value.value
        context.getPrefs().getPrefixForVoice()?.let { prefix ->
            voiceCommand = "$prefix|$voiceCommand"
            Log.d(TAG, "Prefix voice command: $voiceCommand")
        }
        val result = try {
            Log.d(TAG, "Try to send update to voice interpreters endpoint")
            connection.httpClient
                .post("rest/voice/interpreters", voiceCommand, headers = headers)
                .asStatus()
        } catch (e: HttpClient.HttpException) {
            Log.d(TAG, "Error sending update to voice interpreters endpoint", e)
            if (e.statusCode == 404) {
                try {
                    Log.d(TAG, "Voice interpreter endpoint returned 404, falling back to item")
                    connection.httpClient
                        .post("rest/items/VoiceCommand", voiceCommand)
                        .asStatus()
                } catch (e: HttpClient.HttpException) {
                    Log.d(TAG, "Error sending update to voice item", e)
                    return Result.failure(buildOutputData(true, e.statusCode))
                }
            } else {
                return Result.failure(buildOutputData(true, e.statusCode))
            }
        }
        applicationContext.showToast(applicationContext.getString(R.string.info_voice_recognized_text, value.value))
        Log.d(TAG, "Successfully sent update to voice endpoint or item")
        return Result.success(buildOutputData(true, result.statusCode, voiceCommand))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val title = context.getString(R.string.item_upload_in_progress)
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(context, NotificationUpdateObserver.CHANNEL_ID_BACKGROUND)
            .setProgress(0, 0, true)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.ic_openhab_appicon_24dp)
            .setOngoing(true)
            .setWhen(System.currentTimeMillis())
            .setColor(ContextCompat.getColor(context, R.color.openhab_orange))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(R.drawable.ic_clear_grey_24dp, context.getString(android.R.string.cancel), cancelIntent)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID_BACKGROUND_WORK_RUNNING,
            notification,
            FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        )
    }

    private fun buildOutputData(hasConnection: Boolean, httpStatus: Int, sentValue: String? = null): Data =
        Data.Builder()
            .putBoolean(OUTPUT_DATA_HAS_CONNECTION, hasConnection)
            .putInt(OUTPUT_DATA_HTTP_STATUS, httpStatus)
            .putString(OUTPUT_DATA_ITEM_NAME, inputData.getString(INPUT_DATA_ITEM_NAME))
            .putString(OUTPUT_DATA_LABEL, inputData.getString(INPUT_DATA_LABEL))
            .putValueWithInfo(OUTPUT_DATA_VALUE, inputData.getValueWithInfo(INPUT_DATA_VALUE))
            .putString(OUTPUT_DATA_SENT_VALUE, sentValue)
            .putBoolean(OUTPUT_DATA_SHOW_TOAST, inputData.getBoolean(INPUT_DATA_SHOW_TOAST, false))
            .putString(OUTPUT_DATA_TASKER_INTENT, inputData.getString(INPUT_DATA_TASKER_INTENT))
            .putBoolean(OUTPUT_DATA_AS_COMMAND, inputData.getBoolean(INPUT_DATA_AS_COMMAND, false))
            .putBoolean(OUTPUT_DATA_IS_IMPORTANT, inputData.getBoolean(INPUT_DATA_IS_IMPORTANT, false))
            .putBoolean(OUTPUT_DATA_PRIMARY_SERVER, inputData.getBoolean(INPUT_DATA_PRIMARY_SERVER, false))
            .putLong(OUTPUT_DATA_TIMESTAMP, System.currentTimeMillis())
            .build()

    private fun getItemUpdateSuccessMessage(
        context: Context,
        label: String,
        value: String,
        mappedValue: String
    ): String = when (value) {
        "ON" -> context.getString(R.string.item_update_success_message_on, label)
        "OFF" -> context.getString(R.string.item_update_success_message_off, label)
        "UP" -> context.getString(R.string.item_update_success_message_up, label)
        "DOWN" -> context.getString(R.string.item_update_success_message_down, label)
        "MOVE" -> context.getString(R.string.item_update_success_message_move, label)
        "STOP" -> context.getString(R.string.item_update_success_message_stop, label)
        "INCREASE" -> context.getString(R.string.item_update_success_message_increase, label)
        "DECREASE" -> context.getString(R.string.item_update_success_message_decrease, label)
        "UNDEF" -> context.getString(R.string.item_update_success_message_undefined, label)
        "" -> context.getString(R.string.item_update_success_message_empty_string, label)
        "PLAY" -> context.getString(R.string.item_update_success_message_play, label)
        "PAUSE" -> context.getString(R.string.item_update_success_message_pause, label)
        "NEXT" -> context.getString(R.string.item_update_success_message_next, label)
        "PREVIOUS" -> context.getString(R.string.item_update_success_message_previous, label)
        "REWIND" -> context.getString(R.string.item_update_success_message_rewind, label)
        "FASTFORWARD" -> context.getString(R.string.item_update_success_message_fastforward, label)
        else -> context.getString(R.string.item_update_success_message_generic, label, mappedValue)
    }

    companion object {
        private val TAG = ItemUpdateWorker::class.java.simpleName
        private val RETRY_HTTP_ERROR_CODES = listOf(408, 425, 500, 502, 503, 504)

        private const val INPUT_DATA_ITEM_NAME = "item"
        private const val INPUT_DATA_LABEL = "label"
        private const val INPUT_DATA_VALUE = "value"
        private const val INPUT_DATA_SHOW_TOAST = "showToast"
        private const val INPUT_DATA_TASKER_INTENT = "taskerIntent"
        private const val INPUT_DATA_AS_COMMAND = "command"
        private const val INPUT_DATA_IS_IMPORTANT = "is_important"
        private const val INPUT_DATA_PRIMARY_SERVER = "primary_server"

        const val OUTPUT_DATA_HAS_CONNECTION = "hasConnection"
        const val OUTPUT_DATA_HTTP_STATUS = "httpStatus"
        const val OUTPUT_DATA_ITEM_NAME = "item"
        const val OUTPUT_DATA_LABEL = "label"
        const val OUTPUT_DATA_VALUE = "value"
        const val OUTPUT_DATA_SENT_VALUE = "sentValue"
        const val OUTPUT_DATA_SHOW_TOAST = "showToast"
        const val OUTPUT_DATA_TASKER_INTENT = "taskerIntent"
        const val OUTPUT_DATA_AS_COMMAND = "command"
        const val OUTPUT_DATA_IS_IMPORTANT = "is_important"
        const val OUTPUT_DATA_PRIMARY_SERVER = "primary_server"
        const val OUTPUT_DATA_TIMESTAMP = "timestamp"

        fun buildData(
            itemName: String,
            label: String?,
            value: ValueWithInfo,
            showToast: Boolean,
            taskerIntent: String?,
            asCommand: Boolean,
            isImportant: Boolean,
            primaryServer: Boolean
        ) = Data.Builder()
            .putString(INPUT_DATA_ITEM_NAME, itemName)
            .putString(INPUT_DATA_LABEL, label)
            .putValueWithInfo(INPUT_DATA_VALUE, value)
            .putBoolean(INPUT_DATA_SHOW_TOAST, showToast)
            .putString(INPUT_DATA_TASKER_INTENT, taskerIntent)
            .putBoolean(INPUT_DATA_AS_COMMAND, asCommand)
            .putBoolean(INPUT_DATA_IS_IMPORTANT, isImportant)
            .putBoolean(INPUT_DATA_PRIMARY_SERVER, primaryServer)
            .build()

        fun getShortItemUpdateSuccessMessage(context: Context, value: String): String = when (value) {
            "ON" -> context.getString(R.string.item_update_short_success_message_on)
            "OFF" -> context.getString(R.string.item_update_short_success_message_off)
            "UP" -> context.getString(R.string.item_update_short_success_message_up)
            "DOWN" -> context.getString(R.string.item_update_short_success_message_down)
            "MOVE" -> context.getString(R.string.item_update_short_success_message_move)
            "STOP" -> context.getString(R.string.item_update_short_success_message_stop)
            "INCREASE" -> context.getString(R.string.item_update_short_success_message_increase)
            "DECREASE" -> context.getString(R.string.item_update_short_success_message_decrease)
            "UNDEF" -> context.getString(R.string.item_update_short_success_message_undefined)
            "" -> context.getString(R.string.item_update_short_success_message_empty_string)
            "PLAY" -> context.getString(R.string.item_update_short_success_message_play)
            "PAUSE" -> context.getString(R.string.item_update_short_success_message_pause)
            "NEXT" -> context.getString(R.string.item_update_short_success_message_next)
            "PREVIOUS" -> context.getString(R.string.item_update_short_success_message_previous)
            "REWIND" -> context.getString(R.string.item_update_short_success_message_rewind)
            "FASTFORWARD" -> context.getString(R.string.item_update_short_success_message_fastforward)
            else -> context.getString(R.string.item_update_short_success_message_generic, value)
        }
    }

    enum class ValueType {
        Raw,
        Timestamp,
        VoiceCommand,
        MapUndefToOffForSwitchItems
    }

    @Parcelize
    data class ValueWithInfo(
        val value: String,
        val mappedValue: String? = null,
        val type: ValueType = ValueType.Raw,
        val debugInfo: String? = null
    ) : Parcelable
}

fun Data.Builder.putValueWithInfo(key: String, value: ItemUpdateWorker.ValueWithInfo?): Data.Builder {
    if (value != null) {
        putStringArray(key, arrayOf(value.value, value.mappedValue, value.type.name))
    }
    return this
}

fun Data.getValueWithInfo(key: String): ItemUpdateWorker.ValueWithInfo? {
    @Suppress("UNCHECKED_CAST")
    val array = this.keyValueMap[key] as? Array<String?> ?: return null
    val value = array[0] ?: return null
    val type = array[2] ?: return null
    return ItemUpdateWorker.ValueWithInfo(value, array[1], ItemUpdateWorker.ValueType.valueOf(type))
}
