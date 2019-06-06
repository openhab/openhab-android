package org.openhab.habdroid.background

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.core.connection.ConnectionFactory

class ItemUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runBlocking {
            ConnectionFactory.waitForInitialization()
        }

        val data = inputData

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

        val item = data.getString(INPUT_DATA_ITEM)
        val value = inputData.getString(INPUT_DATA_VALUE) as String
        val result = connection.syncHttpClient.post("rest/items/$item", value, "text/plain;charset=UTF-8")
        val outputData = buildOutputData(true, result.statusCode)

        return if (result.isSuccessful) {
            Log.d(TAG, "Item '$item' successfully updated to value $value")
            Result.success(outputData)
        } else {
            Log.e(TAG, "Error sending alarm clock. Got HTTP error ${result.statusCode}", result.error)
            Result.failure(outputData)
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
        private const val MAX_RETRIES = 3

        private const val INPUT_DATA_ITEM = "item"
        private const val INPUT_DATA_VALUE = "value"

        const val OUTPUT_DATA_HAS_CONNECTION = "hasConnection"
        const val OUTPUT_DATA_HTTP_STATUS = "httpStatus"
        const val OUTPUT_DATA_ITEM = "item"
        const val OUTPUT_DATA_VALUE = "value"
        const val OUTPUT_DATA_TIMESTAMP = "timestamp"

        fun buildData(item: String, value: String): Data {
            return Data.Builder()
                    .putString(INPUT_DATA_ITEM, item)
                    .putString(INPUT_DATA_VALUE, value)
                    .build()
        }
    }
}
