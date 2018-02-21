package org.openhab.habdroid.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.auto.value.AutoValue;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;

import okhttp3.Call;
import okhttp3.Headers;

@AutoValue
public abstract class NotificationSettings {
    private static final String TAG = NotificationSettings.class.getSimpleName();

    public abstract String senderId();

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder senderId(@NonNull String senderId);

        abstract NotificationSettings build();
    }

    private static final String SETTINGS_ROUTE = "/api/v1/settings/notifications";
    private static final String GCM_OBJECT_KEY = "gcm";
    private static final String GCM_SENDER_ID_KEY = "senderId";

    public static NotificationSettings forConnection(Connection connection) {
        final NotificationSettings.Builder builder = new AutoValue_NotificationSettings.Builder()
                .senderId(Constants.DEFAULT_GCM_SENDER_ID);

        connection.getSyncHttpClient().get(SETTINGS_ROUTE, new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseBody, Throwable error) {
                Log.e(TAG, "Error loading notification settings: " + error.getMessage());
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseBody) {
                try {
                    JSONObject settings = new JSONObject(responseBody);
                    builder.senderId(settings.getJSONObject(GCM_OBJECT_KEY).getString(GCM_SENDER_ID_KEY));
                } catch (JSONException e) {
                    Log.d(TAG, "Unable to parse notification settings JSON", e);
                }
            }
        });

        return builder.build();
    }
}
