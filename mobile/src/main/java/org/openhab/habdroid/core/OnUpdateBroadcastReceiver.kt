/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.util.Constants

import org.openhab.habdroid.util.Constants.PREFERENCE_COMPAREABLEVERSION
import org.openhab.habdroid.util.getPrefs

class OnUpdateBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val prefs = context.getPrefs()
        prefs.edit {
            if (prefs.getInt(PREFERENCE_COMPAREABLEVERSION, 0) <= UPDATE_LOCAL_CREDENTIALS) {
                Log.d(TAG, "Checking for putting local username/password to remote username/password.")
                if (prefs.getString(Constants.PREFERENCE_REMOTE_USERNAME, null) == null) {
                    putString(Constants.PREFERENCE_REMOTE_USERNAME,
                        prefs.getString(Constants.PREFERENCE_LOCAL_USERNAME, null))
                }
                if (prefs.getString(Constants.PREFERENCE_REMOTE_PASSWORD, null) == null) {
                    putString(Constants.PREFERENCE_REMOTE_PASSWORD,
                        prefs.getString(Constants.PREFERENCE_LOCAL_PASSWORD, null))
                }
            }
            updateComparableVersion(this)
        }
    }

    companion object {
        private val TAG = OnUpdateBroadcastReceiver::class.java.simpleName

        private const val UPDATE_LOCAL_CREDENTIALS = 26

        fun updateComparableVersion(editor: SharedPreferences.Editor) {
            editor.putInt(PREFERENCE_COMPAREABLEVERSION, BuildConfig.VERSION_CODE)
        }
    }
}
