package org.openhab.habdroid.core.notifications;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.TextHttpResponseHandler;

import org.openhab.habdroid.util.Constants;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import cz.msebera.android.httpclient.Header;

public class GoogleCloudMessageConnector {
    private static final String TAG = GoogleCloudMessageConnector.class.getSimpleName();

    private NotificationSettings mSettings;
    private String mDeviceId;
    private GoogleCloudMessaging mGcm;
    private boolean isRegistered;

    public GoogleCloudMessageConnector(NotificationSettings settings, String deviceId, GoogleCloudMessaging gcm) {
        this.mSettings = settings;
        this.mDeviceId = deviceId;
        this.mGcm = gcm;
    }

    /**
     * Registers the android device in GCM and in openHAB-cloud with the device ID and the sender
     * ID returned from {@link NotificationSettings#getSenderId()}.
     *
     * @return True, if the registration succeeded, false otherwise.
     */
    public boolean register() {
        String senderId = mSettings.getSenderId();
        if (senderId == null)
            return false;

        String registrationId;
        try {
            registrationId = mGcm.register(senderId);
        } catch (IOException e) {
            Log.e(TAG, "Error getting GCM ID: " + e.getMessage(), e);
            return false;
        }

        String deviceModel = null;
        try {
            deviceModel = URLEncoder.encode(Build.MODEL, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Log.d(TAG, "Could not encode device model: " + ex.getMessage());
            return false;
        }
        String regUrl;

        try {
            regUrl = mSettings.getOpenHABCloudURL().toURI().resolve("/addAndroidRegistration?deviceId=" + mDeviceId +
                    "&deviceModel=" + deviceModel + "&regId=" + registrationId).toString();
        } catch (URISyntaxException ex) {
            Log.d(TAG, "Could not resolve registration path to openHAB URI: " + ex.getMessage());
            return false;
        }

        Log.d(TAG, "Register device at openHAB-cloud with URL: " + regUrl);
        mSettings.getHttpClient().get(regUrl, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseBody, Throwable error) {
                Log.e(TAG, "GCM reg id error: " + error.getMessage());
                isRegistered = false;
                if (responseBody != null)
                    Log.e(TAG, "Error response = " + responseBody);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                Log.d(TAG, "GCM reg id success");
                isRegistered = true;
            }
        });

        return isRegistered;
    }
}
