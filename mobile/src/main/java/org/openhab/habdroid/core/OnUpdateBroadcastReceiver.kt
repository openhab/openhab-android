package org.openhab.habdroid.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log

import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.util.Constants

import org.openhab.habdroid.util.Constants.PREFERENCE_COMPAREABLEVERSION

class OnUpdateBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val edit = prefs.edit()

        if (prefs.getInt(PREFERENCE_COMPAREABLEVERSION, 0) <= UPDATE_LOCAL_CREDENTIALS) {
            Log.d(TAG, "Checking for putting local username/password to remote username/password.")
            if (prefs.getString(Constants.PREFERENCE_REMOTE_USERNAME, null) == null) {
                edit.putString(Constants.PREFERENCE_REMOTE_USERNAME,
                        prefs.getString(Constants.PREFERENCE_LOCAL_USERNAME, null))
            }
            if (prefs.getString(Constants.PREFERENCE_REMOTE_PASSWORD, null) == null) {
                edit.putString(Constants.PREFERENCE_REMOTE_PASSWORD,
                        prefs.getString(Constants.PREFERENCE_LOCAL_PASSWORD, null))
            }
        }

        updateComparableVersion(edit)
        edit.apply()
    }

    companion object {
        private val TAG = OnUpdateBroadcastReceiver::class.java.simpleName

        private val UPDATE_LOCAL_CREDENTIALS = 26

        fun updateComparableVersion(prefsEdit: SharedPreferences.Editor) {
            prefsEdit.putInt(PREFERENCE_COMPAREABLEVERSION, BuildConfig.VERSION_CODE)
        }
    }
}
