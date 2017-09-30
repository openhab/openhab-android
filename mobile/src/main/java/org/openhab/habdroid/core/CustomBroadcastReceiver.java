package org.openhab.habdroid.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;

import okhttp3.Call;
import okhttp3.Headers;

import static org.openhab.habdroid.ui.OpenHABMainActivity.mAsyncHttpClient;

public class CustomBroadcastReceiver extends BroadcastReceiver {
    private final static String TAG = CustomBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        try {
            Log.d(TAG, "onReceive()");
            if (intent.hasExtra("button_id")) {
                Toast.makeText(context, "Got broadcast "+intent.getExtras().get("button_id"), Toast.LENGTH_SHORT).show();
                //Log.d(TAG, "Button id " + intent.toString());
                Log.d(TAG, "Button: " + intent.getExtras().get("button_id"));

                String state;
                try {
                    state = intent.getExtras().get("button_id").toString();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    return;
                }
                SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
                String item = mSettings.getString(Constants.PREFERENCE_CUSTOM_BROADCAST_ITEM, "");

                try {
                    mAsyncHttpClient.post(OpenHABMainActivity.openHABBaseUrl + "rest/items/" + item, state, "text/plain;charset=UTF-8", new MyHttpClient.TextResponseHandler() {
                        @Override
                        public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                            Log.e(TAG, "Got command error " + error.getMessage());
                            if (statusCode == 404) {
                                String message = context.getString(R.string.error_custom_broadcast_item_not_found);
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                            }
                            if (responseString != null)
                                Log.e(TAG, "Error response = " + responseString);
                        }

                        @Override
                        public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                            Log.d(TAG, "Command was sent successfully");
                        }
                    });
                } catch (RuntimeException e) {
                    if (e.getMessage() != null)
                        Log.e(TAG, e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
