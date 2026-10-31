/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

package org.openhab.habdroid.core.wear

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.duenndns.ssl.MemorizingTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs

/**
 * Publishes the config of the primary server to the Wear OS app via the Wearable Data Layer
 */
object WearHelper {
    private val TAG = WearHelper::class.java.simpleName
    private const val SYNC_DELAY_MILLIS = 1000L

    const val IS_SUPPORTED = true

    private var syncJob: Job? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (isWearConfigPref(key)) {
            scheduleSync()
        }
    }

    private lateinit var appContext: Context

    /**
     * Publish the config now and whenever it changes
     */
    fun start(context: Context) {
        appContext = context.applicationContext
        context.getPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
        context.getSecretPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
        MemorizingTrustManager.keyStoreChangeListener = Runnable { scheduleSync() }
        scheduleSync()
    }

    fun scheduleSync() {
        syncJob?.cancel()
        // Several preferences are changed at once when saving a server, so wait for further changes
        syncJob = GlobalScope.launch(Dispatchers.IO) {
            delay(SYNC_DELAY_MILLIS)
            publishConfig(appContext)
        }
    }

    suspend fun publishConfig(context: Context) {
        val config = context.loadWearConfig()
        val dataClient = Wearable.getDataClient(context)
        try {
            if (config == null) {
                val uri = Uri.Builder().scheme("wear").path(WearConfig.DATA_PATH).build()
                dataClient.deleteDataItems(uri).await()
            } else {
                val request = PutDataMapRequest.create(WearConfig.DATA_PATH).apply {
                    dataMap.putString(WearConfig.DATA_KEY_CONFIG, config.toJson())
                }
                dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
            }
            Log.d(TAG, "Published config: $config")
        } catch (e: Exception) {
            // Thrown if the Wearable API isn't available, e.g. if no Wear OS app is installed
            Log.d(TAG, "Unable to publish config", e)
        }
    }

    /**
     * @return true if a watch with the openHAB app is known or a watch is connected
     */
    suspend fun hasWatch(context: Context): Boolean = try {
        val watchesWithApp = Wearable.getCapabilityClient(context)
            .getCapability(WearConfig.CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL)
            .await()
            .nodes
        // The capability isn't always reported, so also check for connected watches
        watchesWithApp.isNotEmpty() || Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()
    } catch (e: Exception) {
        Log.d(TAG, "Unable to check for watches", e)
        false
    }
}
