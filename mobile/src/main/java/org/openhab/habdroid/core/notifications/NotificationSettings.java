package org.openhab.habdroid.core.notifications;

import android.util.Log;

import com.loopj.android.http.SyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.util.Constants;

import java.net.MalformedURLException;
import java.net.URL;

import cz.msebera.android.httpclient.Header;

public class NotificationSettings {
    private static final String TAG = NotificationSettings.class.getSimpleName();

    private static final String SETTINGS_ROUTE = "/api/v1/settings/notifications";
    private static final String GCM_OBJECT_KEY = "gcm";
    private static final String GCM_SENDER_ID_KEY = "senderId";

    private URL openHABCloudURL;
    private SyncHttpClient httpClient;
    private JSONObject settings = new JSONObject();
    private boolean isLoaded = false;

    /**
     * Constructor
     *
     * @param openHABCloudURL
     * @param httpClient
     * @throws MalformedURLException
     */
    public NotificationSettings(String openHABCloudURL, SyncHttpClient httpClient) throws MalformedURLException {
        this(new URL(openHABCloudURL), httpClient);
    }

    /**
     * Constructor
     *
     * @param openHABCloudURL
     * @param httpClient
     */
    public NotificationSettings(URL openHABCloudURL, SyncHttpClient httpClient) {
        this.openHABCloudURL = openHABCloudURL;
        this.httpClient = httpClient;
    }

    SyncHttpClient getHttpClient () {
        return this.httpClient;
    }

    private void loadSettings() {
        if (isLoaded) {
            Log.d(TAG, "Requested to load notifications settings, but it is loaded already.");
            return;
        }

        String requestUrl = null;
        try {
            requestUrl = new URL(openHABCloudURL, SETTINGS_ROUTE).toString();
        } catch (MalformedURLException ex) {
            Log.d(TAG, "Unable to build request URL, got error: " + ex.getMessage(), ex);
            return;
        }
        Log.d(TAG, "Request notification settings from: " + requestUrl);
        httpClient.get(requestUrl, new SettingsAsyncHttpResponseHandler());
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

    /**
     * Returns the URL object which represents the openHAB-cloud instance.
     *
     * @return
     */
    public URL getOpenHABCloudURL() {
        return this.openHABCloudURL;
    }

    private class SettingsAsyncHttpResponseHandler extends TextHttpResponseHandler {
        @Override
        public void onFailure(int statusCode, Header[] headers, String responseBody, Throwable error) {
            Log.e(TAG, "Error loading notification settings: " + error.getMessage());
        }

        @Override
        public void onSuccess(int statusCode, Header[] headers, String jsonString) {
            Log.d(TAG, "Successfully requested notification settings, parsing it now.");

            JSONObject notifySettings;
            try {
                notifySettings = new JSONObject(jsonString);
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
