package org.openhab.habdroid.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.util.Constants;

import static org.openhab.habdroid.util.Constants.PREFERENCE_COMPAREABLEVERSION;

public class OnUpdateBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = OnUpdateBroadcastReceiver.class.getSimpleName();

    private static final int UPDATE_LOCAL_CREDENTIALS = 26;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();

        if (prefs.getInt(PREFERENCE_COMPAREABLEVERSION, 0) <= UPDATE_LOCAL_CREDENTIALS) {
            Log.d(TAG, "Checking for putting local username/password to remote username/password.");
            if (prefs.getString(Constants.PREFERENCE_REMOTE_USERNAME, null) == null) {
                edit.putString(Constants.PREFERENCE_REMOTE_USERNAME, prefs.getString(Constants
                        .PREFERENCE_LOCAL_USERNAME, null));
            }
            if (prefs.getString(Constants.PREFERENCE_REMOTE_PASSWORD, null) == null) {
                edit.putString(Constants.PREFERENCE_REMOTE_PASSWORD, prefs.getString(Constants
                        .PREFERENCE_LOCAL_PASSWORD, null));
            }
        }

        updateComparableVersion(edit);
        edit.apply();
    }

    public static void updateComparableVersion(SharedPreferences.Editor prefsEdit) {
        prefsEdit.putInt(PREFERENCE_COMPAREABLEVERSION, BuildConfig.VERSION_CODE);
    }
}
