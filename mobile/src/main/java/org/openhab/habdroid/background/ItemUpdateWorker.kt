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

import android.content.Context
import android.util.Log
import androidx.core.os.bundleOf
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.toItem
import org.openhab.habdroid.ui.TaskerItemPickerActivity
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.showErrorToast
import org.openhab.habdroid.util.showToast
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class ItemUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runBlocking {
            ConnectionFactory.waitForInitialization()
        }

        Log.d(TAG, "Trying to get connection")
        val connection = ConnectionFactory.usableConnectionOrNull

        val showToast = inputData.getBoolean(INPUT_DATA_SHOW_TOAST, false)
        val taskerIntent = inputData.getString(INPUT_DATA_TASKER_INTENT)

        if (connection == null) {
            Log.e(TAG, "Got no connection")
            return if (runAttemptCount <= MAX_RETRIES) {
                if (showToast) {
                    applicationContext.showErrorToast(
                        applicationContext.getString(R.string.item_update_error_no_connection_retry))
                }
                Result.retry()
            } else {
                sendTaskerSignalIfNeeded(
                    taskerIntent,
                    false,
                    500,
                    applicationContext.getString(R.string.item_update_error_no_connection)
                )
                Result.failure(buildOutputData(false, 500))
            }
        }

        val itemName = inputData.getString(INPUT_DATA_ITEM_NAME)!!
        var value = inputData.getString(INPUT_DATA_VALUE)!!

        var label = inputData.getString(INPUT_DATA_LABEL)
        if (label.isNullOrEmpty()) label = itemName

        var mappedValue = inputData.getString(INPUT_DATA_MAPPED_VALUE)
        if (mappedValue.isNullOrEmpty()) mappedValue = value

        return runBlocking {
            try {
                val item = loadItem(connection, itemName)
                if (item == null) {
                    sendTaskerSignalIfNeeded(
                        taskerIntent,
                        true,
                        500,
                        applicationContext.getString(R.string.item_update_error_couldnt_get_item_type)
                    )
                    return@runBlocking Result.failure(buildOutputData(true, 500))
                }

                val modifiedValue = getModifiedValueOrNull(item)
                if (modifiedValue != null) {
                    value = modifiedValue
                    mappedValue = modifiedValue
                }
                val result = if (inputData.getBoolean(INPUT_DATA_AS_COMMAND, false)) {
                    connection.httpClient
                        .post("rest/items/$itemName", value, "text/plain;charset=UTF-8")
                        .asStatus()
                } else {
                    connection.httpClient
                        .put("rest/items/$itemName/state", value, "text/plain;charset=UTF-8")
                        .asStatus()
                }
                Log.d(TAG, "Item '$itemName' successfully updated to value $value")
                if (showToast) {
                    applicationContext.showToast(
                        getItemUpdateSuccessMessage(applicationContext, label, value, mappedValue!!))
                }
                sendTaskerSignalIfNeeded(taskerIntent, true, result.statusCode, null)
                Result.success(buildOutputData(true, result.statusCode))
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "Error updating item '$itemName' to value $value. Got HTTP error ${e.statusCode}", e)
                sendTaskerSignalIfNeeded(taskerIntent, true, e.statusCode, e.localizedMessage)
                Result.failure(buildOutputData(true, e.statusCode))
            }
        }
    }

    private fun getModifiedValueOrNull(item: Item): String? {
        val value = inputData.getString(INPUT_DATA_VALUE)!!
        if (value == "TOGGLE") {
            return determineOppositeState(item)
        }
        if (tags.contains(Constants.PREFERENCE_ALARM_CLOCK) && item.type == Item.Type.DateTime) {
            val datePattern = "yyyy-mm-dd HH-MM-SS"
            val formatter = SimpleDateFormat(datePattern, Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            return formatter.format(value)
        }
        return null
    }

    private fun sendTaskerSignalIfNeeded(
        taskerIntent: String?,
        hadConnection: Boolean,
        httpCode: Int,
        errorMessage: String?
    ) {
        if (taskerIntent == null) {
            return
        }
        val resultCode = when {
            errorMessage == null -> TaskerPlugin.Setting.RESULT_CODE_OK
            hadConnection -> TaskerItemPickerActivity.getResultCodeForHttpFailure(httpCode)
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

    private suspend fun loadItem(connection: Connection, itemName: String): Item? {
        val response = connection.httpClient.get("rest/items/$itemName")
        val contentType = response.response.contentType()
        val content = response.asText().response

        if (contentType?.type() == "application" && contentType.subtype() == "json") {
            // JSON
            return try {
                JSONObject(content).toItem()
            } catch (e: JSONException) {
                Log.e(TAG, "Failed parsing JSON result for item $itemName", e)
                null
            }
        } else {
            // XML
            return try {
                val dbf = DocumentBuilderFactory.newInstance()
                val builder = dbf.newDocumentBuilder()
                val document = builder.parse(InputSource(StringReader(content)))
                document.toItem()
            } catch (e: ParserConfigurationException) {
                Log.e(TAG, "Failed parsing XML result for item $itemName", e)
                null
            } catch (e: SAXException) {
                Log.e(TAG, "Failed parsing XML result for item $itemName", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Failed parsing XML result for item $itemName", e)
                null
            }
        }
    }

    private fun determineOppositeState(item: Item): String {
        return if (item.isOfTypeOrGroupType(Item.Type.Rollershutter) || item.isOfTypeOrGroupType(Item.Type.Dimmer)) {
            // If shutter is (partially) closed, open it, else close it
            if (item.state?.asNumber?.value == 0F) "100" else "0"
        } else if (item.state?.asBoolean == true) {
            "OFF"
        } else {
            "ON"
        }
    }

    private fun buildOutputData(hasConnection: Boolean, httpStatus: Int): Data {
        return Data.Builder()
            .putBoolean(OUTPUT_DATA_HAS_CONNECTION, hasConnection)
            .putInt(OUTPUT_DATA_HTTP_STATUS, httpStatus)
            .putString(OUTPUT_DATA_ITEM_NAME, inputData.getString(INPUT_DATA_ITEM_NAME))
            .putString(OUTPUT_DATA_LABEL, inputData.getString(INPUT_DATA_LABEL))
            .putString(OUTPUT_DATA_VALUE, inputData.getString(INPUT_DATA_VALUE))
            .putString(OUTPUT_DATA_MAPPED_VALUE, inputData.getString(INPUT_DATA_MAPPED_VALUE))
            .putBoolean(OUTPUT_DATA_SHOW_TOAST, inputData.getBoolean(INPUT_DATA_SHOW_TOAST, false))
            .putString(OUTPUT_DATA_TASKER_INTENT, inputData.getString(INPUT_DATA_TASKER_INTENT))
            .putString(OUTPUT_DATA_AS_COMMAND, inputData.getString(INPUT_DATA_AS_COMMAND))
            .putLong(OUTPUT_DATA_TIMESTAMP, System.currentTimeMillis())
            .build()
    }

    private fun getItemUpdateSuccessMessage(
        context: Context,
        label: String,
        value: String,
        mappedValue: String
    ): String {
        return when (value) {
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
    }

    companion object {
        private val TAG = ItemUpdateWorker::class.java.simpleName
        private const val MAX_RETRIES = 10

        private const val INPUT_DATA_ITEM_NAME = "item"
        private const val INPUT_DATA_LABEL = "label"
        private const val INPUT_DATA_VALUE = "value"
        private const val INPUT_DATA_MAPPED_VALUE = "mappedValue"
        private const val INPUT_DATA_SHOW_TOAST = "showToast"
        private const val INPUT_DATA_TASKER_INTENT = "taskerIntent"
        private const val INPUT_DATA_AS_COMMAND = "command"

        const val OUTPUT_DATA_HAS_CONNECTION = "hasConnection"
        const val OUTPUT_DATA_HTTP_STATUS = "httpStatus"
        const val OUTPUT_DATA_ITEM_NAME = "item"
        const val OUTPUT_DATA_LABEL = "label"
        const val OUTPUT_DATA_VALUE = "value"
        const val OUTPUT_DATA_MAPPED_VALUE = "mappedValue"
        const val OUTPUT_DATA_SHOW_TOAST = "showToast"
        const val OUTPUT_DATA_TASKER_INTENT = "taskerIntent"
        const val OUTPUT_DATA_AS_COMMAND = "command"
        const val OUTPUT_DATA_TIMESTAMP = "timestamp"

        fun buildData(
            itemName: String,
            label: String?,
            value: String,
            mappedValue: String?,
            showToast: Boolean,
            taskerIntent: String?,
            asCommand: Boolean
        ): Data {
            return Data.Builder()
                .putString(INPUT_DATA_ITEM_NAME, itemName)
                .putString(INPUT_DATA_LABEL, label)
                .putString(INPUT_DATA_VALUE, value)
                .putString(INPUT_DATA_MAPPED_VALUE, mappedValue)
                .putBoolean(INPUT_DATA_SHOW_TOAST, showToast)
                .putString(INPUT_DATA_TASKER_INTENT, taskerIntent)
                .putBoolean(INPUT_DATA_AS_COMMAND, asCommand)
                .build()
        }
    }
}
