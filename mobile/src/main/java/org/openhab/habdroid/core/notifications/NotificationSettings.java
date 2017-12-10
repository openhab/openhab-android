package org.openhab.habdroid.core.notifications;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

import java.net.MalformedURLException;
import java.net.URL;

import okhttp3.Call;
import okhttp3.Headers;

public class NotificationSettings {
    private static final String TAG = NotificationSettings.class.getSimpleName();

    private static final String SETTINGS_ROUTE = "/api/v1/settings/notifications";
    private static final String GCM_OBJECT_KEY = "gcm";
    private static final String GCM_SENDER_ID_KEY = "senderId";

    private URL openHABCloudURL;
    private String openHABCloudUsername;
    private String openHABCloudPassword;
    private MySyncHttpClient httpClient;
    private JSONObject settings = new JSONObject();
    private boolean isLoaded = false;

    /**
     * Constructor
     *
     * @param openHABCloudURL
     * @param httpClient
     * @throws MalformedURLException
     */
    public NotificationSettings(String openHABCloudURL, MySyncHttpClient httpClient) throws
            MalformedURLException {
        this(new URL(openHABCloudURL), httpClient);
    }

    /**
     * Constructor
     *
     * @param openHABCloudURL
     * @param httpClient
     */
    public NotificationSettings(URL openHABCloudURL, MySyncHttpClient httpClient) {
        this.openHABCloudURL = openHABCloudURL;
        this.httpClient = httpClient;
    }

    public String getOpenHABCloudUsername() {
        return openHABCloudUsername;
    }

    public void setOpenHABCloudUsername(String openHABCloudUsername) {
        this.openHABCloudUsername = openHABCloudUsername;
        updateHttpClientAuth();
    }

    public String getOpenHABCloudPassword() {
        return openHABCloudPassword;
    }

    public void setOpenHABCloudPassword(String openHABCloudPassword) {
        this.openHABCloudPassword = openHABCloudPassword;
        updateHttpClientAuth();
    }

    private void updateHttpClientAuth() {
        this.httpClient.setBasicAuth(this.openHABCloudUsername, this.openHABCloudPassword);
    }

    MyHttpClient getHttpClient () {
        return this.httpClient;
    }

    private void loadSettings() {
        if (isLoaded) {
            Log.d(TAG, "Requested to load notifications settings, but it is loaded already.");
            return;
        }

        String requestUrl;
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
