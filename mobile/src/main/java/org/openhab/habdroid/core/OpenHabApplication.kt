/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.RemoteLog
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getPrefs
import java.security.InvalidKeyException

class OpenHabApplication : MultiDexApplication() {
    interface OnDataSaverActiveStateChangedListener {
        fun onSystemDataSaverActiveStateChanged(active: Boolean)
    }

    val secretPrefs: SharedPreferences by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                getEncryptedSharedPrefs()
            } catch (e: InvalidKeyException) {
                // See https://github.com/openhab/openhab-android/issues/1807
                Log.e(TAG, "Error getting encrypted shared prefs, try again.", e)
                getEncryptedSharedPrefs()
            }
        } else {
            getSharedPreferences("secret_shared_prefs", MODE_PRIVATE)
        }
    }

    var isSystemDataSaverActive: Boolean = false
        private set

    private val dataSaverChangeListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DataSaverStateChangeReceiver()
    } else {
        null
    }

    private val dataSaverListeners = mutableSetOf<OnDataSaverActiveStateChangedListener>()

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getEncryptedSharedPrefs() = EncryptedSharedPreferences.create(
        "secret_shared_prefs_encrypted",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(getPrefs().getDayNightMode(this))
        ConnectionFactory.initialize(this)
        BackgroundTasksManager.initialize(this)
        RemoteLog.initialize(this)

        dataSaverChangeListener?.let { listener ->
            registerReceiver(listener, IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED))
            isSystemDataSaverActive = listener.isSystemDataSaverEnabled(this)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        ConnectionFactory.shutdown()
    }

    fun registerSystemDataSaverStateChangedListener(l: OnDataSaverActiveStateChangedListener) {
        dataSaverListeners.add(l)
    }

    fun unregisterSystemDataSaverStateChangedListener(l: OnDataSaverActiveStateChangedListener) {
        dataSaverListeners.remove(l)
    }

    inner class DataSaverStateChangeReceiver : BroadcastReceiver() {
        fun isSystemDataSaverEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return false
            }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }

        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED) {
                return
            }
            isSystemDataSaverActive = isSystemDataSaverEnabled(context)
            dataSaverListeners.forEach { l -> l.onSystemDataSaverActiveStateChanged(isSystemDataSaverActive) }
        }
    }

    companion object {
        private val TAG = OpenHabApplication::class.java.simpleName
    }
}
