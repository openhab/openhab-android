package org.openhab.habdroid.core.notifications;

import android.os.Build;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.openhab.habdroid.util.MyHttpClient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Headers;

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

        String deviceModel;
        try {
            deviceModel = URLEncoder.encode(Build.MODEL, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Log.d(TAG, "Could not encode device model: " + ex.getMessage());
            return false;
        }
        String regUrl = String.format(Locale.US,
                "/addAndroidRegistration?deviceId=%s&deviceModel=%s&regId=%s",
                mDeviceId, deviceModel, registrationId);

        Log.d(TAG, "Register device at openHAB-cloud with URL: " + regUrl);
        mSettings.getConnection().getSyncHttpClient().get(regUrl, new MyHttpClient.ResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                Log.e(TAG, "GCM reg id error: " + error.getMessage());
                isRegistered = false;
                if (responseBody != null)
                    Log.e(TAG, "Error response = " + responseBody);
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                Log.d(TAG, "GCM reg id success");
                isRegistered = true;
            }
        });

        return isRegistered;
    }
}
