package org.openhab.habdroid.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.Bundle;
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
    public final static String CUSTOM_BROADCAST_RECEIVER_INTENT = "org.openhab.habdroid.cbr";
    public static String item;
    public static String intent_extra;

    private static void updateNotification(String text, Context context) {
        Intent notificationIntent = new Intent(context, OpenHABMainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Notification notification;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            notification = new NotificationCompat.Builder(context)
                    .setContentTitle(context.getString(R.string.settings_custom_broadcast_listening))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.icon_blank)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setColor(ResourcesCompat.getColor(context.getResources(), R.color.openhab_orange, null))
                    .setOngoing(true).build();
        } else {
            notification = new NotificationCompat.Builder(context)
                    .setContentTitle(context.getString(R.string.settings_custom_broadcast_listening))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.icon_blank)
                    .setColor(ResourcesCompat.getColor(context.getResources(), R.color.openhab_orange, null))
                    .setOngoing(true).build();
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "onReceive()");
        try {

            if (intent.hasExtra(intent_extra)) {
                final String state;
                try {
                    state = intent.getExtras().get(intent_extra).toString();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    return;
                }

                Log.d(TAG, "Value: " + state);

                final String currentTime;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
                } else {
                    currentTime = Calendar.getInstance().getTime().toString();
                }

                try {
                    mAsyncHttpClient.post(OpenHABMainActivity.openHABBaseUrl + "rest/items/" + item, state, "text/plain;charset=UTF-8", new MyHttpClient.TextResponseHandler() {
                        @Override
                        public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                            Log.e(TAG, "Got command error " + error.getMessage());
                            String message = String.format(context.getString(R.string.notification_last_broadcast_failed), currentTime, state);
                            if (statusCode == 404) {
                                message = context.getString(R.string.error_custom_broadcast_item_not_found);
                            }
                            updateNotification(message, context);
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
            } else {
                String message = context.getString(R.string.error_custom_broadcast_extra_not_found);
                updateNotification(message, context);
                Log.d(TAG, "Extra not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
