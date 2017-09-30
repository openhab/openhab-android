package org.openhab.habdroid.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.widget.Toast;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;

import java.util.Calendar;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Headers;

import static org.openhab.habdroid.ui.OpenHABMainActivity.mAsyncHttpClient;

public class CustomBroadcastReceiver extends BroadcastReceiver {
    private final static String TAG = CustomBroadcastReceiver.class.getSimpleName();

    private static void updateNotification(String text, Context context) {
        Intent notificationIntent = new Intent(context, OpenHABMainActivity.class);
        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.settings_custom_broadcast_listening))
                .setContentText(text)
                .setSmallIcon(R.drawable.openhabicon_light)
                .setPriority(Notification.PRIORITY_LOW)
                .setColor(ResourcesCompat.getColor(context.getResources(), R.color.openhab_orange, null))
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        try {
            Log.d(TAG, "onReceive()");
            if (intent.hasExtra("button_id")) {
                Toast.makeText(context, "Got broadcast "+intent.getExtras().get("button_id"), Toast.LENGTH_SHORT).show();
                //Log.d(TAG, "Button id " + intent.toString());
                Log.d(TAG, "Button: " + intent.getExtras().get("button_id"));

                final String state;
                try {
                    state = intent.getExtras().get("button_id").toString();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    return;
                }
                SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
                String item = mSettings.getString(Constants.PREFERENCE_CUSTOM_BROADCAST_ITEM, "");

                // todo needs api 24
                final String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());

                try {
                    mAsyncHttpClient.post(OpenHABMainActivity.openHABBaseUrl + "rest/items/" + item, state, "text/plain;charset=UTF-8", new MyHttpClient.TextResponseHandler() {
                        @Override
                        public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                            Log.e(TAG, "Got command error " + error.getMessage());
                            String message = String.format(context.getString(R.string.notification_last_broadcast_failed), currentTime, state);
                            updateNotification(message, context);
                            if (statusCode == 404) {
                                String toastMessage = context.getString(R.string.error_custom_broadcast_item_not_found);
                                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
                            }
                            if (responseString != null)
                                Log.e(TAG, "Error response = " + responseString);
                        }

                        @Override
                        public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                            String message = String.format(context.getString(R.string.notification_last_broadcast_success), currentTime, state);
                            updateNotification(message, context);
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
