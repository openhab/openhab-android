package org.openhab.habdroid.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.util.Constants;

public class OnUpdateBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = OpenHABMainActivity.class.getSimpleName();

    private static final String UPDATE_LOCAL_CREDENTIALS = "1.8.0.8";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (getVersionNumber(context.getPackageManager(), context.getPackageName())
                .equals(UPDATE_LOCAL_CREDENTIALS)) {
            Log.d(TAG, "Checking for putting username/password to local username/password.");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor edit = prefs.edit();
            if (prefs.getString(Constants.PREFERENCE_LOCAL_USERNAME, null) == null) {
                edit.putString(Constants.PREFERENCE_LOCAL_USERNAME, prefs.getString(Constants
                        .PREFERENCE_USERNAME, null));
            }
            if (prefs.getString(Constants.PREFERENCE_LOCAL_PASSWORD, null) == null) {
                edit.putString(Constants.PREFERENCE_LOCAL_PASSWORD, prefs.getString(Constants
                        .PREFERENCE_PASSWORD, null));
            }
            edit.apply();
        }
    }

    private String getVersionNumber(PackageManager pm, String packageName) {
        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
            Log.d(TAG, packageInfo.versionName);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Could not get package version information.", e);
            return "0";
        }
    }
}
