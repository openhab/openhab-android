package org.openhab.habdroid.background;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Result;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.DemoConnection;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.SyncHttpClient;

import java.util.Locale;

public class ItemUpdateWorker extends Worker {
    private static final String TAG = ItemUpdateWorker.class.getSimpleName();
    private static final int MAX_RETRIES = 3;

    private static final String INPUT_DATA_ITEM = "item";
    private static final String INPUT_DATA_VALUE = "value";

    public static final String OUTPUT_DATA_HAS_CONNECTION = "hasConnection";
    public static final String OUTPUT_DATA_IS_DEMO = "isDemo";
    public static final String OUTPUT_DATA_HTTP_STATUS = "httpStatus";
    public static final String OUTPUT_DATA_ITEM = "item";
    public static final String OUTPUT_DATA_VALUE = "value";
    public static final String OUTPUT_DATA_TIMESTAMP = "timestamp";

    public static Data buildData(String item, String value) {
        return new Data.Builder()
                .putString(INPUT_DATA_ITEM, item)
                .putString(INPUT_DATA_VALUE, value)
                .build();
    }

    public ItemUpdateWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        ConnectionFactory.waitForInitialization();

        final Data data = getInputData();
        Connection connection;

        try {
            Log.d(TAG, "Trying to get connection");
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            Log.e(TAG, "Got no connection " + e);
            return getRunAttemptCount() <= MAX_RETRIES
                    ? Result.retry() : Result.failure(buildOutputData(false,
                    false,0));
        }

        if (connection instanceof DemoConnection) {
            return Result.failure(buildOutputData(false, true, 0));
        }

        final String item = data.getString(INPUT_DATA_ITEM);
        final String value = getInputData().getString(INPUT_DATA_VALUE);
        final String url = String.format(Locale.US, "rest/items/%s", item);
        final SyncHttpClient.HttpResult result = connection.getSyncHttpClient()
                .post(url, value, "text/plain;charset=UTF-8");
        final Data outputData = buildOutputData(true, false, result.statusCode);

        if (result.isSuccessful()) {
            Log.d(TAG, "Item '" + item + "' successfully updated to value " + value);
            return Result.success(outputData);
        } else {
            Log.e(TAG, "Error sending alarm clock. Got HTTP error "
                    + result.statusCode, result.error);
            return Result.failure(outputData);
        }
    }

    private Data buildOutputData(boolean hasConnection, boolean isDemo, int httpStatus) {
        Data inputData = getInputData();
        return new Data.Builder()
                .putBoolean(OUTPUT_DATA_HAS_CONNECTION, hasConnection)
                .putBoolean(OUTPUT_DATA_IS_DEMO, isDemo)
                .putInt(OUTPUT_DATA_HTTP_STATUS, httpStatus)
                .putString(OUTPUT_DATA_ITEM, inputData.getString(INPUT_DATA_ITEM))
                .putString(OUTPUT_DATA_VALUE, inputData.getString(INPUT_DATA_VALUE))
                .putLong(OUTPUT_DATA_TIMESTAMP, System.currentTimeMillis())
                .build();
    }
}
