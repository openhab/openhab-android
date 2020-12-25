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

import android.app.AppOpsManager
import android.app.AsyncNotedAppOp
import android.app.SyncNotedAppOp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.InvalidKeyException
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.DataUsagePolicy
import org.openhab.habdroid.util.RemoteLog
import org.openhab.habdroid.util.determineDataUsagePolicy
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getPrefs

class OpenHabApplication : MultiDexApplication() {
    interface OnDataUsagePolicyChangedListener {
        fun onDataUsagePolicyChanged(newPolicy: DataUsagePolicy)
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

    var systemDataSaverStatus: Int = ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
        private set
    var batterySaverActive: Boolean = false
        private set

    private val dataSaverChangeListener = SystemDataSaverStateChangeReceiver()
    private val dataUsagePolicyListeners = mutableSetOf<OnDataUsagePolicyChangedListener>()

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
        RemoteLog.initialize()
        AppCompatDelegate.setDefaultNightMode(getPrefs().getDayNightMode(this))
        ConnectionFactory.initialize(this)
        BackgroundTasksManager.initialize(this)

        dataSaverChangeListener.let { listener ->
            registerReceiver(
                listener,
                IntentFilter().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
                    }
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                }
            )
            systemDataSaverStatus = listener.getSystemDataSaverStatus(this)
            batterySaverActive = listener.isBatterySaverActive(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            registerDataAccessAudit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun registerDataAccessAudit() {
        class DataAccessException(message: String) : Exception(message)

        val appOpsCallback = object : AppOpsManager.OnOpNotedCallback() {
            private fun logPrivateDataAccess(opCode: String, trace: String) {
                Log.e("DataAudit", "Operation: $opCode\nStacktrace: $trace")
                RemoteLog.nonFatal(DataAccessException("Operation: $opCode\nStacktrace: $trace"))
            }

            override fun onNoted(syncNotedAppOp: SyncNotedAppOp) {
                logPrivateDataAccess(
                    syncNotedAppOp.op,
                    Throwable().stackTrace.toString()
                )
            }

            override fun onSelfNoted(syncNotedAppOp: SyncNotedAppOp) {
                logPrivateDataAccess(
                    syncNotedAppOp.op,
                    Throwable().stackTrace.toString()
                )
            }

            override fun onAsyncNoted(asyncNotedAppOp: AsyncNotedAppOp) {
                logPrivateDataAccess(
                    asyncNotedAppOp.op,
                    asyncNotedAppOp.message
                )
            }
        }

        val appOpsManager = getSystemService(AppOpsManager::class.java) as AppOpsManager
        appOpsManager.setOnOpNotedCallback(mainExecutor, appOpsCallback)
    }

    override fun onTerminate() {
        super.onTerminate()
        ConnectionFactory.shutdown()
    }

    fun registerSystemDataSaverStateChangedListener(l: OnDataUsagePolicyChangedListener) {
        dataUsagePolicyListeners.add(l)
    }

    fun unregisterSystemDataSaverStateChangedListener(l: OnDataUsagePolicyChangedListener) {
        dataUsagePolicyListeners.remove(l)
    }

    inner class SystemDataSaverStateChangeReceiver : BroadcastReceiver() {
        fun getSystemDataSaverStatus(context: Context): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
            }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.restrictBackgroundStatus
        }

        fun isBatterySaverActive(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isPowerSaveMode
        }

        override fun onReceive(context: Context, intent: Intent?) {
            val oldPolicy = determineDataUsagePolicy()
            when (intent?.action) {
                ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED -> {
                    systemDataSaverStatus = getSystemDataSaverStatus(context)
                    Log.d(TAG, "Data saver changed to $systemDataSaverStatus")
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    batterySaverActive = isBatterySaverActive(context)
                    Log.d(TAG, "Battery saver changed to $batterySaverActive")
                }
                else -> return
            }
            val newPolicy = determineDataUsagePolicy()
            if (oldPolicy != newPolicy) {
                dataUsagePolicyListeners.forEach { l -> l.onDataUsagePolicyChanged(newPolicy) }
            }
        }
    }

    companion object {
        private val TAG = OpenHabApplication::class.java.simpleName
    }
}
