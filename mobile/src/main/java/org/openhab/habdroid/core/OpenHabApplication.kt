/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.GeneralSecurityException
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.CrashReportingHelper
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getPrefs

class OpenHabApplication : MultiDexApplication() {
    interface OnDataUsagePolicyChangedListener {
        fun onDataUsagePolicyChanged()
    }

    val secretPrefs: SharedPreferences by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                getEncryptedSharedPrefs()
            } catch (e: GeneralSecurityException) {
                // See https://github.com/openhab/openhab-android/issues/1807
                CrashReportingHelper.e(TAG, "Error getting encrypted shared prefs, try again.", exception = e)
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
        if (CrashReportingHelper.isCrashReporterProcess()) {
            // No initialization of the app required
            Log.d(TAG, "Skip onCreate()")
            return
        }

        CrashReportingHelper.initialize(this)
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

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Enable WebView debugging")
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun registerDataAccessAudit() {
        class DataAccessException(message: String) : Exception(message)

        val appOpsCallback = object : AppOpsManager.OnOpNotedCallback() {
            private fun logPrivateDataAccess(tag: String?, opCode: String, trace: String) {
                if (tag in DATA_ACCESS_TAGS) {
                    // Known access, don't report it
                    return
                }
                Log.e("DataAudit", "Tag: $tag, Operation: $opCode\nStacktrace: $trace")
                CrashReportingHelper.nonFatal(DataAccessException("Tag: $tag, Operation: $opCode\nStacktrace: $trace"))
            }

            override fun onNoted(syncNotedAppOp: SyncNotedAppOp) {
                logPrivateDataAccess(
                    syncNotedAppOp.attributionTag,
                    syncNotedAppOp.op,
                    Throwable().stackTraceToString()
                )
            }

            override fun onSelfNoted(syncNotedAppOp: SyncNotedAppOp) {
                logPrivateDataAccess(
                    syncNotedAppOp.attributionTag,
                    syncNotedAppOp.op,
                    Throwable().stackTraceToString()
                )
            }

            override fun onAsyncNoted(asyncNotedAppOp: AsyncNotedAppOp) {
                logPrivateDataAccess(
                    asyncNotedAppOp.attributionTag,
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
            val prevSystemDataSaverStatus = systemDataSaverStatus
            val wasBatterySaverActive = batterySaverActive
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
            if (prevSystemDataSaverStatus != systemDataSaverStatus || wasBatterySaverActive != batterySaverActive) {
                dataUsagePolicyListeners.forEach { l -> l.onDataUsagePolicyChanged() }
            }
        }
    }

    companion object {
        private val TAG = OpenHabApplication::class.java.simpleName

        const val DATA_ACCESS_TAG_SEND_DEV_INFO = "SEND_DEV_INFO"
        const val DATA_ACCESS_TAG_SELECT_SERVER_WIFI = "SELECT_SERVER_WIFI"
        const val DATA_ACCESS_TAG_SUGGEST_TURN_ON_WIFI = "SUGGEST_TURN_ON_WIFI"
        const val DATA_ACCESS_TAG_SERVER_DISCOVERY = "SERVER_DISCOVERY"
        val DATA_ACCESS_TAGS = listOf(
            DATA_ACCESS_TAG_SEND_DEV_INFO,
            DATA_ACCESS_TAG_SELECT_SERVER_WIFI,
            DATA_ACCESS_TAG_SUGGEST_TURN_ON_WIFI,
            DATA_ACCESS_TAG_SERVER_DISCOVERY
        )
    }
}
