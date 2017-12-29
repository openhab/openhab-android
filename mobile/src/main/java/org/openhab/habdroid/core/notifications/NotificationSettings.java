package org.openhab.habdroid.core.notifications;

import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;

import okhttp3.Call;
import okhttp3.Headers;

public class NotificationSettings {
    private static final String TAG = NotificationSettings.class.getSimpleName();

    private static final String SETTINGS_ROUTE = "/api/v1/settings/notifications";
    private static final String GCM_OBJECT_KEY = "gcm";
    private static final String GCM_SENDER_ID_KEY = "senderId";

    private JSONObject settings = new JSONObject();
    private boolean isLoaded = false;
    private Connection conn;

    public NotificationSettings(@NonNull Connection conn) {
        this.conn = conn;
    }

    private void loadSettings() {
        if (isLoaded) {
            Log.d(TAG, "Requested to load notifications settings, but it is loaded already.");
            return;
        }

        Log.d(TAG, "Request notification settings from: " + SETTINGS_ROUTE);
        conn.getSyncHttpClient().get(SETTINGS_ROUTE, new SettingsAsyncHttpResponseHandler());
    }

    /**
     * Returns the configured sender ID of the openHBA-cloud instance passed to the constructor. If
     * no sender ID was configured, null is returned.
     *
     * @return
     */
    public String getSenderId() {
        if (!isLoaded) {
            loadSettings();
        }

        try {
            return settings.getString(GCM_SENDER_ID_KEY);
        } catch(JSONException ex) {
            Log.d(TAG, "The settings does not contain a senderId, return the default one.");
            return Constants.DEFAULT_GCM_SENDER_ID;
        }
    }

    public Connection getConnection(){
        return conn;
    }

    private class SettingsAsyncHttpResponseHandler implements MyHttpClient.ResponseHandler {
        @Override
        public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
            Log.e(TAG, "Error loading notification settings: " + error.getMessage());
        }

        @Override
        public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
            Log.d(TAG, "Successfully requested notification settings, parsing it now.");

            JSONObject notifySettings;
            try {
                notifySettings = new JSONObject(new String(responseBody));
            } catch (JSONException e) {
                Log.d(TAG, "Unable to parse returned body as JSON: " + e.getMessage(), e);
                return;
            }

            isLoaded = true;
            try {
                settings = notifySettings.getJSONObject(GCM_OBJECT_KEY);
            } catch(JSONException ex) {
                Log.d(TAG, "Returned notification JSON settings does not contain a GCM key. Error: " + ex.getMessage());
            }
        }
    }
}
