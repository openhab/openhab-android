package org.openhab.habdroid.nonfree;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Log;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.GcmIntentService;
import org.openhab.habdroid.core.NotificationDeletedBroadcastReceiver;
import org.openhab.habdroid.core.notifications.GoogleCloudMessageConnector;
import org.openhab.habdroid.ui.OpenHABMainActivity;

public class NonfreeMainActivity extends OpenHABMainActivity {
    public void gcmRegisterBackground() {
        OpenHABMainActivity.GCM_SENDER_ID = null;
        // if no notification settings can be constructed, no GCM registration can be made.
        if (getNotificationSettings() == null)
            return;

        if (mGcm == null)
            mGcm = (myGoogleCloudMessaging) myGoogleCloudMessaging.getInstance(getApplicationContext());

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                GoogleCloudMessageConnector connector =
                        new GoogleCloudMessageConnector(getNotificationSettings(), deviceId, mGcm);

                if (connector.register()) {
                    OpenHABMainActivity.GCM_SENDER_ID = getNotificationSettings().getSenderId();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String regId) {}
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void processIntent(Intent intent) {
        Log.d(TAG, "Intent != null");
        if (intent.getAction() != null) {
            Log.d(TAG, "Intent action = " + intent.getAction());
            if (intent.getAction().equals("android.nfc.action.NDEF_DISCOVERED")) {
                Log.d(TAG, "This is NFC action");
                if (intent.getDataString() != null) {
                    Log.d(TAG, "NFC data = " + intent.getDataString());
                    mNfcData = intent.getDataString();
                }
            } else if (intent.getAction().equals(GcmIntentService.ACTION_NOTIFICATION_SELECTED)) {
                onNotificationSelected(intent);
            } else if (intent.getAction().equals("android.intent.action.VIEW")) {
                Log.d(TAG, "This is URL Action");
                mNfcData = intent.getDataString();
            }
        }
    }

    private void onNotificationSelected(Intent intent) {
        Log.d(TAG, "Notification was selected");
        if (intent.hasExtra(GcmIntentService.EXTRA_NOTIFICATION_ID)) {
            Log.d(TAG, String.format("Notification id = %d",
                    intent.getExtras().getInt(GcmIntentService.EXTRA_NOTIFICATION_ID)));
            // Make a fake broadcast intent to hide intent on other devices
            Intent deleteIntent = new Intent(this, NotificationDeletedBroadcastReceiver.class);
            deleteIntent.setAction(GcmIntentService.ACTION_NOTIFICATION_DELETED);
            deleteIntent.putExtra(GcmIntentService.EXTRA_NOTIFICATION_ID, intent.getExtras().getInt(GcmIntentService.EXTRA_NOTIFICATION_ID));
            sendBroadcast(deleteIntent);
        }

        if (getNotificationSettings() != null) {
            openNotifications();
            mNotificationPosition = pagerAdapter.getCount() - 1;
        }

        if (intent.hasExtra(GcmIntentService.EXTRA_MSG)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.dlg_notification_title));
            builder.setMessage(intent.getExtras().getString(GcmIntentService.EXTRA_MSG));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            AlertDialog dialog = builder.create();
            dialog.show();

        }
    }
}
