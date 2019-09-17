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
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.showToast

class ItemUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runBlocking {
            ConnectionFactory.waitForInitialization()
        }

        Log.d(TAG, "Trying to get connection")
        val connection = ConnectionFactory.usableConnectionOrNull

        if (connection == null) {
            Log.e(TAG, "Got no connection")
            return if (runAttemptCount <= MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(buildOutputData(false, 0))
            }
        }

        val item = inputData.getString(INPUT_DATA_ITEM)
        val value = inputData.getString(INPUT_DATA_VALUE) as String
        val successToastMessage = inputData.getString(INPUT_DATA_SUCCESS_TOAST_MESSAGE)

        return runBlocking {
            try {
                val result = connection.httpClient
                    .post("rest/items/$item", value, "text/plain;charset=UTF-8")
                    .asStatus()
                Log.d(TAG, "Item '$item' successfully updated to value $value")
                if (successToastMessage != null) {
                    applicationContext.showToast(successToastMessage)
                }
                Result.success(buildOutputData(true, result.statusCode))
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "Error updating item '$item' to value $value. Got HTTP error ${e.statusCode}", e)
                Result.failure(buildOutputData(true, e.statusCode))
            }
        }
    }

    private fun buildOutputData(hasConnection: Boolean, httpStatus: Int): Data {
        return Data.Builder()
            .putBoolean(OUTPUT_DATA_HAS_CONNECTION, hasConnection)
            .putInt(OUTPUT_DATA_HTTP_STATUS, httpStatus)
            .putString(OUTPUT_DATA_ITEM, inputData.getString(INPUT_DATA_ITEM))
            .putString(OUTPUT_DATA_VALUE, inputData.getString(INPUT_DATA_VALUE))
            .putLong(OUTPUT_DATA_TIMESTAMP, System.currentTimeMillis())
            .build()
    }

    companion object {
        private val TAG = ItemUpdateWorker::class.java.simpleName
        private const val MAX_RETRIES = 10

        private const val INPUT_DATA_ITEM = "item"
        private const val INPUT_DATA_VALUE = "value"
        private const val INPUT_DATA_SUCCESS_TOAST_MESSAGE = "successToast"

        const val OUTPUT_DATA_HAS_CONNECTION = "hasConnection"
        const val OUTPUT_DATA_HTTP_STATUS = "httpStatus"
        const val OUTPUT_DATA_ITEM = "item"
        const val OUTPUT_DATA_VALUE = "value"
        const val OUTPUT_DATA_TIMESTAMP = "timestamp"

        fun buildData(item: String, value: String, successToast: String?): Data {
            return Data.Builder()
                .putString(INPUT_DATA_ITEM, item)
                .putString(INPUT_DATA_VALUE, value)
                .putString(INPUT_DATA_SUCCESS_TOAST_MESSAGE, successToast)
                .build()
        }
    }
}
