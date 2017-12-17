package org.openhab.habdroid.core;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;

public class CustomBroadcastListenerService extends Service {
    private static final String TAG = "CBLService";
    private static boolean cbrRunning = false;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        CustomBroadcastReceiver cbr = new CustomBroadcastReceiver();
        if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION)) {
            Log.i(TAG, "Received Start Foreground Intent ");
            SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(this);
            String broadcast = mSettings.getString(Constants.PREFERENCE_CUSTOM_BROADCAST_BROADCAST, "");
            String item = mSettings.getString(Constants.PREFERENCE_CUSTOM_BROADCAST_ITEM, "");
            String intent_extra = mSettings.getString(Constants.PREFERENCE_CUSTOM_BROADCAST_EXTRA, "button_id");
            CustomBroadcastReceiver.item = item;
            CustomBroadcastReceiver.intent_extra = intent_extra;

            IntentFilter cbr_intent = new IntentFilter();
            cbr_intent.addAction(broadcast);
            registerReceiver(cbr, cbr_intent);

            Intent notificationIntent = new Intent(this, OpenHABMainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.settings_custom_broadcast_listening))
                    .setContentText(getString(R.string.notification_last_broadcast_none))
                    .setSmallIcon(R.drawable.icon_blank)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setColor(ResourcesCompat.getColor(getResources(), R.color.openhab_orange, null))
                    .setOngoing(true).build();

            startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);

        } else if (intent.getAction().equals(Constants.ACTION.STOPFOREGROUND_ACTION)) {
            // No API call available to check if a broadcast receiver is running: https://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android/3568906#3568906
            try {
                Log.e(TAG, "stop cbr");
                unregisterReceiver(cbr);
            } catch (IllegalArgumentException ignored) {}
            Log.i(TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case of bound services.
        return null;
    }
}